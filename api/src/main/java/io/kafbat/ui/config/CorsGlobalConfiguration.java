package io.kafbat.ui.config;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Configuration
public class CorsGlobalConfiguration {

  private static List<String> allowedOrigins = List.of();

  public CorsGlobalConfiguration(
      @Value("${web.cors.allowed-origins:}") String allowedOriginsConfig) {
    if (StringUtils.hasText(allowedOriginsConfig)) {
      allowedOrigins = Arrays.stream(allowedOriginsConfig.split(","))
          .map(String::trim)
          .filter(StringUtils::hasText)
          .toList();
    } else {
      allowedOrigins = List.of();
    }
  }

  @Bean
  public WebFilter corsFilter() {
    return (final ServerWebExchange ctx, final WebFilterChain chain) -> {
      final ServerHttpRequest request = ctx.getRequest();
      final ServerHttpResponse response = ctx.getResponse();
      final HttpHeaders headers = response.getHeaders();

      String origin = request.getHeaders().getOrigin();
      boolean isAllowed = isOriginAllowed(origin, request);

      if (isAllowed && origin != null) {
        fillCorsHeader(headers, request);
      }

      if (request.getMethod() == HttpMethod.OPTIONS) {
        if (origin != null && !isAllowed) {
          response.setStatusCode(HttpStatus.FORBIDDEN);
          return Mono.empty();
        }
        response.setStatusCode(HttpStatus.OK);
        return Mono.empty();
      }

      return chain.filter(ctx);
    };
  }

  public static void fillCorsHeader(HttpHeaders responseHeaders, ServerHttpRequest request) {
    String origin = request.getHeaders().getOrigin();
    if (origin == null || origin.isBlank() || !isOriginAllowed(origin, request)) {
      return;
    }
    responseHeaders.set("Access-Control-Allow-Origin", origin);
    responseHeaders.set("Access-Control-Allow-Credentials", "true");
    responseHeaders.set("Access-Control-Allow-Methods", "GET, PUT, POST, DELETE, OPTIONS");
    responseHeaders.set("Access-Control-Max-Age", "3600");
    responseHeaders.set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
  }

  public static boolean isOriginAllowed(String origin, ServerHttpRequest request) {
    if (origin == null || origin.isBlank()) {
      return true;
    }
    if (allowedOrigins.contains("*") || allowedOrigins.contains(origin)) {
      return true;
    }
    try {
      URI originUri = URI.create(origin);
      String originHost = originUri.getHost();
      int originPort = originUri.getPort();
      if (originHost == null) {
        return false;
      }

      URI requestUri = request.getURI();
      String requestHost = requestUri.getHost();
      int requestPort = requestUri.getPort();

      if (requestHost != null && requestHost.equalsIgnoreCase(originHost)) {
        if (originPort == requestPort || (originPort == -1 && (requestPort == 80 || requestPort == 443))) {
          return true;
        }
      }

      var hostHeader = request.getHeaders().getHost();
      if (hostHeader != null && hostHeader.getHostString().equalsIgnoreCase(originHost)) {
        int hostPort = hostHeader.getPort();
        if (originPort == hostPort || (originPort == -1 && (hostPort == 80 || hostPort == 443 || hostPort == -1))) {
          return true;
        }
      }
    } catch (Exception ignored) {
      return false;
    }
    return false;
  }
}
