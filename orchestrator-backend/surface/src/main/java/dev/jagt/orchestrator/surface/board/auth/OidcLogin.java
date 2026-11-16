package dev.jagt.orchestrator.surface.board.auth;

import dev.jagt.orchestrator.service.Causes;
import dev.jagt.orchestrator.service.log.BoardUrl;
import dev.jagt.orchestrator.surface.board.auth.attempts.LoginAudit;
import java.util.Objects;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "orchestrator.auth", name = "mode", havingValue = "oidc")
class OidcLogin {

  private static final String START = "/oauth2/authorization/" + IssuerRegistrations.ID;

  @Bean
  SecurityFilterChain oidcChain(
      HttpSecurity http,
      ClientRegistrationRepository registrations,
      OidcProperties oidc,
      LoginAudit audit)
      throws Exception {
    SavedRequestAwareAuthenticationSuccessHandler redirect =
        new SavedRequestAwareAuthenticationSuccessHandler();
    GuardedBoard.guard(http, START)
        .oauth2Login(login ->
            login
                .clientRegistrationRepository(registrations)
                .userInfoEndpoint(
                    info -> info.oidcUserService(owner(Objects.requireNonNull(oidc.allowed()))))
                .successHandler((request, response, authentication) -> {
                  audit.succeeded(request.getRemoteAddr(), "oidc");
                  redirect.onAuthenticationSuccess(request, response, authentication);
                })
                .failureHandler((request, response, failure) -> {
                  audit.failed(request.getRemoteAddr(), "oidc", Causes.of(failure));
                  response.sendError(HttpStatus.FORBIDDEN.value());
                }));
    return http.build();
  }

  @Bean
  ClientRegistrationRepository oidcRegistrations(
      OidcProperties oidc, Environment env, BoardUrl boardUrl) {
    return new IssuerRegistrations(
        Objects.requireNonNull(oidc.issuerUri()),
        Objects.requireNonNull(oidc.clientId()),
        Objects.requireNonNull(env.getProperty(Objects.requireNonNull(oidc.clientSecretEnv()))),
        boardUrl.root() + IssuerRegistrations.CALLBACK);
  }

  // Without a user store Boot generates, and logs, a password of its own.
  @Bean
  UserDetailsService noUsers() {
    return new InMemoryUserDetailsManager();
  }

  private static OAuth2UserService<OidcUserRequest, OidcUser> owner(String allowed) {
    return request -> {
      OidcIdToken token = request.getIdToken();
      if (!allowed.equals(token.getSubject()) && !allowed.equals(token.getEmail())) {
        throw new OAuth2AuthenticationException(new OAuth2Error("access_denied"));
      }
      return new DefaultOidcUser(Set.of(new SimpleGrantedAuthority("ROLE_OWNER")), token);
    };
  }
}
