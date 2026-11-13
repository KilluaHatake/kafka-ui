package io.kafbat.ui.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

class CorsGlobalConfigurationTest {

  @Test
  void permitsSameOriginHost() {
    new CorsGlobalConfiguration("");
    MockServerHttpRequest req = MockServerHttpRequest.get("http://ec2-internal:8080/api/clusters")
        .header("Origin", "http://ec2-internal:8080")
        .header("Host", "ec2-internal:8080")
        .build();

    assertThat(CorsGlobalConfiguration.isOriginAllowed("http://ec2-internal:8080", req)).isTrue();

    HttpHeaders headers = new HttpHeaders();
    CorsGlobalConfiguration.fillCorsHeader(headers, req);
    assertThat(headers.getFirst("Access-Control-Allow-Origin")).isEqualTo("http://ec2-internal:8080");
    assertThat(headers.getFirst("Access-Control-Allow-Credentials")).isEqualTo("true");
  }

  @Test
  void permitsConfiguredAllowedOrigins() {
    new CorsGlobalConfiguration("https://app.company.internal, http://localhost:3000");
    MockServerHttpRequest req = MockServerHttpRequest.get("http://ec2-internal:8080/api/clusters")
        .header("Origin", "https://app.company.internal")
        .build();

    assertThat(CorsGlobalConfiguration.isOriginAllowed("https://app.company.internal", req)).isTrue();
    assertThat(CorsGlobalConfiguration.isOriginAllowed("http://localhost:3000", req)).isTrue();

    HttpHeaders headers = new HttpHeaders();
    CorsGlobalConfiguration.fillCorsHeader(headers, req);
    assertThat(headers.getFirst("Access-Control-Allow-Origin")).isEqualTo("https://app.company.internal");
  }

  @Test
  void rejectsUntrustedExternalOrigin() {
    new CorsGlobalConfiguration("https://trusted.corp");
    MockServerHttpRequest req = MockServerHttpRequest.get("http://ec2-internal:8080/api/clusters")
        .header("Origin", "https://evil-attacker.com")
        .header("Host", "ec2-internal:8080")
        .build();

    assertThat(CorsGlobalConfiguration.isOriginAllowed("https://evil-attacker.com", req)).isFalse();

    HttpHeaders headers = new HttpHeaders();
    CorsGlobalConfiguration.fillCorsHeader(headers, req);
    assertThat(headers.containsKey("Access-Control-Allow-Origin")).isFalse();
    assertThat(headers.containsKey("Access-Control-Allow-Credentials")).isFalse();
  }

  @Test
  void preflightOptionsForbiddenForDisallowedOrigin() {
    CorsGlobalConfiguration config = new CorsGlobalConfiguration("https://trusted.corp");
    MockServerHttpRequest req = MockServerHttpRequest.options("http://ec2-internal:8080/api/clusters")
        .header("Origin", "https://evil-attacker.com")
        .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(req);
    WebFilterChain chain = ex -> Mono.empty();

    config.corsFilter().filter(exchange, chain).block();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void preflightOptionsOkForAllowedOrigin() {
    CorsGlobalConfiguration config = new CorsGlobalConfiguration("https://trusted.corp");
    MockServerHttpRequest req = MockServerHttpRequest.options("http://ec2-internal:8080/api/clusters")
        .header("Origin", "https://trusted.corp")
        .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(req);
    WebFilterChain chain = ex -> Mono.empty();

    config.corsFilter().filter(exchange, chain).block();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Origin"))
        .isEqualTo("https://trusted.corp");
  }
}
