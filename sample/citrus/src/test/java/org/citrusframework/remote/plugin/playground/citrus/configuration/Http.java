package org.citrusframework.remote.plugin.playground.citrus.configuration;

import org.citrusframework.dsl.endpoint.CitrusEndpoints;
import org.citrusframework.http.client.HttpClient;
import org.citrusframework.http.server.HttpServer;
import org.citrusframework.http.server.HttpServerBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class Http {

    public static final String SERVICE_CLIENT_NAME = "serviceClient";
    public static final String HTTP_SERVER_NAME = "httpServer";

    @Bean
    @Qualifier(SERVICE_CLIENT_NAME)
    public HttpClient serviceClient(@Value("${test-config.sut.url}") String sutUrl) {
        return CitrusEndpoints
          .http().client()
            .requestUrl(sutUrl)
        .build();
    }

    @Bean
    @Qualifier(HTTP_SERVER_NAME)
    public HttpServer httpServer(@Value("${test-config.http.server.port}") int port) {
        return new HttpServerBuilder()
            .port(port)
            .timeout(Duration.ofSeconds(10).toMillis())
            .autoStart(true)
            .build();
    }
}
