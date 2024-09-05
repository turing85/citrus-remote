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

import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import org.citrusframework.remote.controller.RunController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static java.lang.Thread.currentThread;

/**
 * @author Christoph Deppisch
 */
public class CitrusRemoteServer {

    /** Logger */
    private static final Logger logger = LoggerFactory.getLogger(CitrusRemoteServer.class);

    /** Endpoint configuration */
    private final CitrusRemoteConfiguration configuration;

    /** Router customizations */
    private final List<Consumer<Router>> routerCustomizations;

    private CitrusRemoteApplication application;

    /** Completed future marking completed state */
    protected final CompletableFuture<Boolean> completed = new CompletableFuture<>();

    /**
     * Default constructor using controller and configuration.
     * @param configuration
     */
    public CitrusRemoteServer(CitrusRemoteConfiguration configuration, List<Consumer<Router>> routerCustomizations) {
        this.configuration = configuration;
        this.routerCustomizations = routerCustomizations;
    }

    public CitrusRemoteServer(String[] args, List<Consumer<Router>> routerCustomizations) {
        this(new CitrusRemoteOptions().apply(new CitrusRemoteConfiguration(), args), routerCustomizations);
    }

    /**
     * Main method
     * @param args
     */
    public static void main(String[] args) {
        entrypoint(args, Collections.emptyList());
    }

    /**
     * Entrypoint method
     * @param args
     * @param routerCustomizations
     */
    public static void entrypoint(String[] args, List<Consumer<Router>> routerCustomizations) {
        CitrusRemoteServer server = new CitrusRemoteServer(args, routerCustomizations);

        if (server.configuration.getTimeToLive() > 0) {
            CompletableFuture.runAsync(() -> {
                try {
                    new CompletableFuture<Void>().get(server.configuration.getTimeToLive(), TimeUnit.MILLISECONDS);
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    server.stop();
                    currentThread().interrupt();
                }
            });
        }

        server.start();

        if (server.configuration.isSystemExit()) {
            if (server.waitForCompletion()) {
                System.exit(0);
            } else {
                System.exit(-1);
            }
        } else {
            server.waitForCompletion();
        }
    }

    /**
     * Start server instance and listen for incoming requests.
     */
    public void start() {
        application = new CitrusRemoteApplication(configuration, routerCustomizations);
        Vertx.vertx().deployVerticle(application);

        if (!configuration.isSkipTests()) {
            new RunController(configuration).run();
        }

        if (configuration.getTimeToLive() == 0) {
            stop();
        }
    }

    /**
     * Stops the server instance.
     */
    public void stop() {
        application.stop();
        complete();
    }

    /**
     * Completes this application.
     */
    public void complete() {
        completed.complete(true);
    }

    /**
     * Waits for completed state of application.
     * @return
     */
    public boolean waitForCompletion() {
        try {
            return completed.get();
        } catch (InterruptedException | ExecutionException e) {
            logger.warn("Failed to wait for server completion", e);
            currentThread().interrupt();
        }

        return false;
    }
}
