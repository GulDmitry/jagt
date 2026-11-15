package dev.jagt.orchestrator.surface.board.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "orchestrator.auth",
    name = "mode",
    havingValue = "none",
    matchIfMissing = true)
class OpenBoard {

  @Bean
  SecurityFilterChain openChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .headers(headers -> headers.cacheControl(cache -> cache.disable()))
        .authorizeHttpRequests(requests -> requests.anyRequest().permitAll());
    return http.build();
  }

  // Without a user store Boot generates, and logs, a password of its own.
  @Bean
  UserDetailsService noUsers() {
    return new InMemoryUserDetailsManager();
  }
}
