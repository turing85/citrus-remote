/*
 * Copyright 2006-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.citrusframework.remote;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.citrusframework.Citrus;
import org.citrusframework.CitrusInstanceManager;
import org.citrusframework.CitrusInstanceStrategy;
import org.citrusframework.TestClass;
import org.citrusframework.main.CitrusAppConfiguration;
import org.citrusframework.main.TestRunConfiguration;
import org.citrusframework.remote.job.RunJob;
import org.citrusframework.remote.model.RemoteResult;
import org.citrusframework.remote.listener.RemoteTestListener;
import org.citrusframework.remote.transformer.JsonRequestTransformer;
import org.citrusframework.remote.transformer.JsonResponseTransformer;
import org.citrusframework.report.JUnitReporter;
import org.citrusframework.report.LoggingReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Remote application creates routes for this web application.
 *
 * @author Christoph Deppisch
 * @since 2..4
 */
public class CitrusRemoteApplication extends AbstractVerticle {

    /** Logger */
    private static final Logger logger = LoggerFactory.getLogger(CitrusRemoteApplication.class);

    /** Global url encoding */
    private static final String ENCODING = "UTF-8";
    /** Content types */
    private static final String APPLICATION_JSON = "application/json";
    private static final String APPLICATION_XML = "application/xml";
    private static final String APPLICATION_OCTET_STREAM = "application/octet-stream";

    /** Application configuration */
    private final CitrusRemoteConfiguration configuration;

    /** Single thread job scheduler */
    private Future<List<RemoteResult>> remoteResultFuture;

    /** Latest test reports */
    private final RemoteTestListener remoteTestListener =
            new RemoteTestListener();

    /** Router customizations */
    private final List<Consumer<Router>> routerCustomizations;

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    private final JsonRequestTransformer requestTransformer = new JsonRequestTransformer();
    private final JsonResponseTransformer responseTransformer = new JsonResponseTransformer();

    /**
     * Constructor with given application configuration and route customizations.
     * @param configuration
     * @param routerCustomizations
     */
    public CitrusRemoteApplication(
            CitrusRemoteConfiguration configuration,
            List<Consumer<Router>> routerCustomizations) {
        this.configuration = configuration;
        this.routerCustomizations = Optional.ofNullable(routerCustomizations)
                .orElse(Collections.emptyList());
    }

    @Override
    public void start() {
        CitrusInstanceManager.mode(CitrusInstanceStrategy.SINGLETON);
        CitrusInstanceManager
                .addInstanceProcessor(citrus -> citrus.addTestListener(remoteTestListener));

        Router router = Router.router(getVertx());
        router.route().handler(BodyHandler.create());
        router.route().handler(ctx -> {
            logger.info("{} {}", ctx.request().method(), ctx.request().uri());
            ctx.next();
        });
        addHealthEndpoint(router);
        addFilesEndpoint(router);
        addResultsEndpoints(router);
        addRunEndpoints(router);
        addConfigEndpoints(router);
        routerCustomizations.forEach(customization -> customization.accept(router));

        getVertx().createHttpServer()
                .requestHandler(router)
                .listen(configuration.getPort())
                .onSuccess(unused ->
                        logger.info("Server started on port {}", configuration.getPort()));
    }

    private static void addHealthEndpoint(Router router) {
        router.get("/health")
                .handler(wrapThrowingHandler(ctx ->
                        ctx.response()
                                .putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                                .end("{ \"status\": \"UP\" }")));
    }

    private static void addFilesEndpoint(Router router) {
        router.get("/files/:name")
                .handler(wrapThrowingHandler(ctx -> {
                    HttpServerResponse response = ctx.response();
                    String fileName = ctx.pathParam("name");
                    Path file = Path.of(fileName);
                    if (Files.isRegularFile(file)) {
                        response.putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_OCTET_STREAM)
                                .putHeader(
                                        HttpHeaders.CONTENT_DISPOSITION,
                                        "attachment; filename=\"" + file.getFileName() + "\"")
                                .sendFile(fileName);
                    } else {
                        response.setStatusCode(HttpResponseStatus.NOT_FOUND.code()).end();
                    }
                }));
    }

    private void addResultsEndpoints(Router router) {
        router.get("/results")
                .produces(APPLICATION_JSON)
                .handler(wrapThrowingHandler(ctx -> {
                    long timeout = Optional.ofNullable(ctx.request().params().get("timeout"))
                            .map(Long::valueOf)
                            .orElse(10000L);

                    HttpServerResponse response = ctx.response();
                    if (remoteResultFuture != null) {
                        response.putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON);
                        remoteResultFuture.timeout(timeout, TimeUnit.MILLISECONDS)
                                .onSuccess(results ->
                                        response.end(responseTransformer.render(results)))
                                .onFailure(throwable -> response
                                        .setStatusCode(HttpResponseStatus.PARTIAL_CONTENT.code())
                                        .end(responseTransformer
                                                .render(remoteTestListener.toRemoteResults())));
                    } else {
                        final List<RemoteResult> results = remoteTestListener.toRemoteResults();
                        logger.info("results = {}", results.size());
                        response.end(responseTransformer.render(results));
                    }
                }));
        router.get("/results")
                .handler(ctx -> ctx.response()
                        .end(responseTransformer.render(remoteTestListener.generateTestReport())));
        router.get("/results/files")
                .handler(wrapThrowingHandler(ctx -> {
                    File junitReportsFolder = new File(getJUnitReportsFolder());

                    List<String> result = Collections.emptyList();
                    if (junitReportsFolder.exists()) {
                        result = Optional.ofNullable(junitReportsFolder.list())
                                .stream()
                                .flatMap(Stream::of)
                                .toList();
                    }
                    ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                            .end(responseTransformer.render(result));
                }));
        router.get("/results/file/:name")
                .handler(wrapThrowingHandler(ctx -> {
                    HttpServerResponse response = ctx.response();
                    response.putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_XML);
                    String fileName = ctx.pathParam("name");
                    String reportsFolder = getJUnitReportsFolder();
                    Path testResultFile = Path.of(reportsFolder).resolve(fileName);

                    if (Files.exists(testResultFile)) {
                        response.sendFile(testResultFile.toString());
                    } else {
                        response.setStatusCode(HttpResponseStatus.NOT_FOUND.code())
                                .end("Failed to find test result file: %s".formatted(fileName));
                    }
                }));
        router.get("/results/suite")
                .handler(wrapThrowingHandler(ctx -> {
                    HttpServerResponse response = ctx.response();
                    response.putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON);
                    JUnitReporter jUnitReporter = new JUnitReporter();
                    Path suiteResultFile = Path.of(jUnitReporter.getReportDirectory())
                            .resolve(String.format(
                                    jUnitReporter.getReportFileNamePattern(),
                                    jUnitReporter.getSuiteName()));
                    if (Files.exists(suiteResultFile)) {
                        response.sendFile(suiteResultFile.toString());
                    } else {
                        response.setStatusCode(HttpResponseStatus.NOT_FOUND.code())
                                .end("Failed to find suite result file: %s"
                                        .formatted(suiteResultFile));
                    }
                }));
    }

    private void addRunEndpoints(Router router) {
        router.get("/run")
                .handler(wrapThrowingHandler(ctx ->
                        runTestsAsync(constructRunConfig(ctx.request().params()), ctx.response())));
        router.post("/run")
                .handler(wrapThrowingHandler(ctx ->
                        runTestsAsync(constructRunConfig(ctx.body()), ctx.response())));
        router.put("/run")
                .handler(wrapThrowingHandler(ctx -> {
                    remoteTestListener.reset();
                    remoteResultFuture = startTestsAsync(constructRunConfig(ctx.body()));
                    ctx.response().end("");
                }));
    }

    public static Handler<RoutingContext> wrapThrowingHandler(
            ThrowingHandler<RoutingContext> handler) {
        return ctx -> {
            try {
                handler.handle(ctx);
            } catch (Exception e) {
                ctx.response()
                        .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
                        .end(e.getMessage());
            }
        };
    }



    private TestRunConfiguration constructRunConfig(MultiMap queryParams)
            throws UnsupportedEncodingException {
        TestRunConfiguration runConfiguration = new TestRunConfiguration();
        if (queryParams.contains("engine")) {
            String engine = queryParams.get("engine");
            runConfiguration.setEngine(URLDecoder.decode(engine, ENCODING));
        } else {
            runConfiguration.setEngine(configuration.getEngine());
        }

        if (queryParams.contains("includes")) {
            String value = queryParams.get("includes");
            runConfiguration.setIncludes(URLDecoder.decode(value, ENCODING)
                    .split(","));
        }

        if (queryParams.contains("package")) {
            String value = queryParams.get("package");
            runConfiguration.setPackages(Collections.singletonList(
                    URLDecoder.decode(value, ENCODING)));
        }

        if (queryParams.contains("class")) {
            String value = queryParams.get("class");
            runConfiguration.setTestSources(Collections.singletonList(
                    TestClass.fromString(URLDecoder.decode(value, ENCODING))));
        }
        return runConfiguration;
    }

    private TestRunConfiguration constructRunConfig(RequestBody body) {
        return requestTransformer.read(body.asString(), TestRunConfiguration.class);
    }

    private void runTestsAsync(
            TestRunConfiguration runConfiguration,
            HttpServerResponse response) {
        Future
                .fromCompletionStage(CompletableFuture.supplyAsync(
                        new RunJob(configuration, runConfiguration, remoteTestListener),
                        executorService))
                .onSuccess(results ->
                        response.end(responseTransformer.render(results)))
                .onFailure(error -> response
                        .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
                        .end(error.getMessage()));
    }

    private Future<List<RemoteResult>> startTestsAsync(TestRunConfiguration testRunConfiguration) {
        return Future.fromCompletionStage(CompletableFuture.supplyAsync(
                new RunJob(configuration, testRunConfiguration, remoteTestListener),
                executorService));
    }

    private void addConfigEndpoints(Router router) {
        router.get("/configuration")
                .handler(wrapThrowingHandler(ctx ->
                        ctx.response()
                                .putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                                .end(responseTransformer.render(configuration))));
        router.put("/configuration")
                .handler(wrapThrowingHandler(ctx ->
                        configuration.apply(requestTransformer.read(
                                ctx.body().asString(),
                                CitrusAppConfiguration.class))));
    }

    /**
     * Find reports folder based in unit testing framework present on classpath.
     * @return
     */
    private String getJUnitReportsFolder() {
        if (isPresent("org.testng.annotations.Test")) {
            return "test-output" + File.separator + "junitreports";
        } else if (isPresent("org.junit.Test")) {
            JUnitReporter jUnitReporter = new JUnitReporter();
            return jUnitReporter.getReportDirectory() +
                    File.separator +
                    jUnitReporter.getOutputDirectory();
        } else {
            return new LoggingReporter().getReportDirectory();
        }
    }

    @Override
    public void stop() {
        Optional<Citrus> citrus = CitrusInstanceManager.get();
        if (citrus.isPresent()) {
            logger.info("Closing Citrus and its application context");
            citrus.get().close();
        }
        getVertx().close();
    }

    // TODO: Check if this is equivalent to
    // https://github.com/spring-projects/spring-framework/blob/main/spring-core/src/main/java/org/springframework/util/ClassUtils.java
    private boolean isPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
