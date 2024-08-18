package org.citrusframework.remote.plugin.playground.citrus;

import org.citrusframework.TestCaseRunner;
import org.citrusframework.annotations.CitrusResource;
import org.citrusframework.annotations.CitrusTest;
import org.citrusframework.testng.spring.TestNGCitrusSpringSupport;
import org.citrusframework.remote.plugin.playground.citrus.configuration.Http;
import org.springframework.http.HttpStatus;
import org.testng.annotations.Optional;
import org.testng.annotations.Test;

import static org.citrusframework.http.actions.HttpActionBuilder.http;

@SuppressWarnings("unused")
public class GetMetricsIT extends TestNGCitrusSpringSupport {
  @Test
  @CitrusTest
  public void getMetrics(@Optional @CitrusResource TestCaseRunner runner) {
    // WHEN
    // @formatter:off
    runner.$(http()
      .client(Http.SERVICE_CLIENT_NAME)
      .send()
        .get("/q/metrics"));

    // THEN
    runner.$(http()
      .client(Http.SERVICE_CLIENT_NAME)
      .receive()
        .response(HttpStatus.OK));
    // @formatter:on
  }
}
