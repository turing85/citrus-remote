package org.citrusframework.remote.sample.entrypoint;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.io.IoBuilder;
import org.citrusframework.remote.CitrusRemoteApplication;
import org.citrusframework.remote.CitrusRemoteServer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class CustomEntrypoint {
    private static final Path CITRUS_LOG_PATH = Path.of("target/logs/citrus.log");
    public static final RollingFileAppender FILE_ROLLER =
            ((LoggerContext) LogManager.getContext())
                    .getConfiguration()
                    .getAppender("FILE_ROLLER");

    public static void main(String... args) {
        System.setOut(IoBuilder
                .forLogger(LogManager.getLogger("system.out"))
                .buildPrintStream());
        CitrusRemoteServer.entrypoint(
                args, List.of(CustomEntrypoint::getLogHandler, CustomEntrypoint::rotateLogHandler));
    }

    private static void getLogHandler(Router router) {
        router.get("/citrus-logs")
                .handler(CitrusRemoteApplication.wrapThrowingHandler(ctx -> {
                    HttpServerResponse response = ctx.response();
                    if (Files.exists(CITRUS_LOG_PATH)) {
                        response.setStatusCode(HttpStatus.OK.value())
                                .putHeader(
                                        HttpHeaders.CONTENT_TYPE,
                                        MediaType.TEXT_PLAIN_VALUE)
                                .putHeader(
                                        HttpHeaders.CONTENT_DISPOSITION,
                                        "attachment; filename=\"citrus.log\"")
                                .end(Files.readString(
                                        CITRUS_LOG_PATH,
                                        Charset.defaultCharset()))
                                .onSuccess(unused -> {
                                    if (Optional.ofNullable(ctx.request().params().get("reset"))
                                            .map(Boolean::parseBoolean)
                                            .orElse(false)) {
                                        rotateCitrusLog();
                                    }
                                });
                    } else {
                        response.setStatusCode(HttpStatus.NOT_FOUND.value()).end();
                    }
                }));
    }

    private static void rotateLogHandler(Router router) {
        router.delete("/citrus-logs")
                .handler(CitrusRemoteApplication.wrapThrowingHandler(ctx -> {
                    HttpServerResponse response = ctx.response();
                    rotateCitrusLog();
                    response.setStatusCode(HttpStatus.NO_CONTENT.value())
                            .end();
                }));
    }

    private static void rotateCitrusLog() {
        if (Files.exists(CITRUS_LOG_PATH)) {
            FILE_ROLLER.getManager().rollover();
        }
    }
}
