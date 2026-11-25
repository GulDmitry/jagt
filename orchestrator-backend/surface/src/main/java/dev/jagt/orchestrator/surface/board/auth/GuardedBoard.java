package dev.jagt.orchestrator.surface.board.auth;

import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;

final class GuardedBoard {

  private GuardedBoard() {}

  static HttpSecurity guard(HttpSecurity http, String loginUrl) throws Exception {
    PublicPaths contract = PublicPaths.fromContract();
    return http.csrf(AbstractHttpConfigurer::disable)
        .headers(BoardCsp::apply)
        .authorizeHttpRequests(requests ->
            requests
                .requestMatchers("/error")
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
        .requestCache(cache -> cache.requestCache(pagesOnly()))
        .exceptionHandling(handling ->
            handling
                .defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    PathPatternRequestMatcher.withDefaults().matcher("/api/**"))
                .defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint(loginUrl), AnyRequestMatcher.INSTANCE));
  }

  private static HttpSessionRequestCache pagesOnly() {
    MediaTypeRequestMatcher json = new MediaTypeRequestMatcher(MediaType.APPLICATION_JSON);
    json.setIgnoredMediaTypes(Set.of(MediaType.ALL));
    HttpSessionRequestCache cache = new HttpSessionRequestCache();
    cache.setRequestMatcher(
        new AndRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/**"),
            new NegatedRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher("/api/**")),
            new NegatedRequestMatcher(json)));
    return cache;
  }
}
