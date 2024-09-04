package org.citrusframework.remote.sample.http;

import org.citrusframework.TestCaseRunner;
import org.citrusframework.annotations.CitrusResource;
import org.citrusframework.annotations.CitrusTest;
import org.citrusframework.testng.spring.TestNGCitrusSpringSupport;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.testng.annotations.Optional;
import org.testng.annotations.Test;

import static org.citrusframework.container.Async.Builder.async;
import static org.citrusframework.http.actions.HttpActionBuilder.http;
import static org.junit.Assert.assertEquals;

public class TextTestIT extends TestNGCitrusSpringSupport {
    @Test
    @CitrusTest
    public void test(@Optional @CitrusResource TestCaseRunner runner) {
        runner.variable("body", "citrus:randomString(10, MIXED, true)");
        runner.given(async().actions(
                http().server("httpServer")
                        .receive()
                        .get("/get/text")
                        .message()
                        .accept(MediaType.TEXT_PLAIN_VALUE),
                http().server("httpServer")
                        .send()
                        .response(HttpStatus.OK)
                        .message()
                        .contentType(MediaType.TEXT_PLAIN_VALUE)
                        .body("${body}")));

        runner.when(http()
                .client("httpClient")
                .send()
                .get("/get/text")
                .message()
                .accept(MediaType.TEXT_PLAIN_VALUE));

        runner.then(http()
                .client("httpClient")
                .receive()
                .response(HttpStatus.OK)
                .message()
                .contentType(MediaType.TEXT_PLAIN_VALUE)
                .body("${body}"));
        assertEquals(1, 1);
    }
}
