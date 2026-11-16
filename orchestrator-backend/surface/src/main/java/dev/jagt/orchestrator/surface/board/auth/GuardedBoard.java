package dev.jagt.orchestrator.surface.board.auth;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;

final class GuardedBoard {

  private GuardedBoard() {}

  static HttpSecurity guard(HttpSecurity http, String loginUrl) throws Exception {
    PublicPaths contract = PublicPaths.fromContract();
    return http.csrf(AbstractHttpConfigurer::disable)
        .headers(BoardCsp::apply)
        .authorizeHttpRequests(requests ->
            requests
                .requestMatchers("/mcp", "/api/agent/**", "/error")
                .permitAll()
                .requestMatchers(contract.patterns().toArray(String[]::new))
                .permitAll()
                .anyRequest()
                .authenticated())
        .logout(logout ->
            logout
                .logoutRequestMatcher(
                    PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/logout"))
                .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler())
                .deleteCookies("JSESSIONID"))
        .exceptionHandling(handling ->
            handling
                .defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    PathPatternRequestMatcher.withDefaults().matcher("/api/**"))
                .defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint(loginUrl), AnyRequestMatcher.INSTANCE));
  }
}
