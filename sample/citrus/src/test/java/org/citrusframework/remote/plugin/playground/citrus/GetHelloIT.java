package org.citrusframework.remote.plugin.playground.citrus;

import org.citrusframework.TestCaseRunner;
import org.citrusframework.annotations.CitrusResource;
import org.citrusframework.annotations.CitrusTest;
import org.citrusframework.message.MessageType;
import org.citrusframework.testng.spring.TestNGCitrusSpringSupport;
import org.citrusframework.remote.plugin.playground.citrus.configuration.Http;
import org.springframework.http.HttpStatus;
import org.testng.annotations.Optional;
import org.testng.annotations.Test;

import static org.citrusframework.container.Async.Builder.async;
import static org.citrusframework.http.actions.HttpActionBuilder.http;

@SuppressWarnings("unused")
public class GetHelloIT extends TestNGCitrusSpringSupport {
  @Test
  @CitrusTest
  public void getHello(@Optional @CitrusResource TestCaseRunner runner) {
    // GIVEN
    // @formatter:off
    runner.$(async().actions(
        http().server(Http.HTTP_SERVER_NAME)
            .receive().get("/greeting"),
        http().server(Http.HTTP_SERVER_NAME)
            .send()
                .response(HttpStatus.OK)
                .message().body("Hai")));

    // WHEN
    runner.$(http()
      .client(Http.SERVICE_CLIENT_NAME)
      .send()
        .get("/hello"));

    // THEN
    runner.$(http()
      .client(Http.SERVICE_CLIENT_NAME)
      .receive()
        .response(HttpStatus.OK)
        .message()
          .type(MessageType.PLAINTEXT)
          .body("Hai"));
    // @formatter:on
  }
}
