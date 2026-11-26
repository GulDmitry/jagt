package dev.jagt.orchestrator.surface.board.filter;

import dev.jagt.orchestrator.service.log.BoardUrl;
import dev.jagt.orchestrator.surface.board.auth.AuthProperties;
import dev.jagt.orchestrator.surface.board.auth.BoardCsp;
import dev.jagt.orchestrator.surface.mcp.refusal.RootToken;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;

/**
 * The doors in front of the board's own chain, first match wins: what a page on another site or a
 * write without the page's Origin is refused at, then {@code /mcp} and {@code /api/agent/}. What
 * remains is the board, whose chain follows the {@code orchestrator.auth} mode.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class Doors {

  @Bean
  @Order(0)
  SecurityFilterChain refusedDoor(HttpSecurity http, AuthProperties auth, BoardUrl boardUrl)
      throws Exception {
    NotThePage refused = new NotThePage(auth, boardUrl);
    http.securityMatcher(refused)
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(requests -> requests.anyRequest().denyAll())
        .exceptionHandling(handling ->
            handling.authenticationEntryPoint(
                (request, response, denied) -> forbid(request, response, refused)));
    return http.build();
  }

  @Bean
  @Order(1)
  SecurityFilterChain agentDoor(
      HttpSecurity http, AuthProperties auth, RootToken rootToken, BoardCsp csp) throws Exception {
    http.securityMatcher(AgentDoor.PATHS)
        .csrf(AbstractHttpConfigurer::disable)
        .headers(csp::apply)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(requests -> {
          if (auth.mode() == AuthProperties.Mode.NONE) {
            requests.anyRequest().permitAll();
          } else {
            requests.anyRequest().access(new LoopbackOrRootToken(rootToken));
          }
        })
        .exceptionHandling(handling ->
            handling.authenticationEntryPoint(
                (request, response, denied) -> demandToken(request, response)));
    return http.build();
  }

  @Bean
  HttpFirewall firewall() {
    return new StrictHttpFirewall();
  }

  private static void forbid(
      HttpServletRequest request, HttpServletResponse response, NotThePage refused) {
    log.atWarn()
        .setMessage("request refused")
        .addKeyValue("url", request.getRequestURI())
        .addKeyValue("cause", refused.cause(request).orElse("refused"))
        .log();
    response.setStatus(HttpStatus.FORBIDDEN.value());
  }

  private static void demandToken(HttpServletRequest request, HttpServletResponse response) {
    log.atWarn()
        .setMessage("request refused")
        .addKeyValue("url", request.getRequestURI())
        .addKeyValue("cause", "no root token from a peer off loopback")
        .log();
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
  }
}
