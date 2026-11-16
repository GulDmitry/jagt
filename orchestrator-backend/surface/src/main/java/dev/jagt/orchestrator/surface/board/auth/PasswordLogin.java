package dev.jagt.orchestrator.surface.board.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "orchestrator.auth", name = "mode", havingValue = "password")
class PasswordLogin {

  @Bean
  SecurityFilterChain passwordChain(HttpSecurity http) throws Exception {
    PublicPaths contract = PublicPaths.fromContract();
    String page = contract.loginPage();
    http.csrf(AbstractHttpConfigurer::disable)
        .headers(headers -> headers.cacheControl(cache -> cache.disable()))
        .authorizeHttpRequests(requests ->
            requests
                .requestMatchers("/mcp", "/api/agent/**", "/error")
                .permitAll()
                .requestMatchers(contract.patterns().toArray(String[]::new))
                .permitAll()
                .anyRequest()
                .authenticated())
        .formLogin(form -> form.loginPage(page).loginProcessingUrl("/login").permitAll())
        .exceptionHandling(handling ->
            handling
                .defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    PathPatternRequestMatcher.withDefaults().matcher("/api/**"))
                .defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint(page), AnyRequestMatcher.INSTANCE));
    return http.build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  UserDetailsService owner(AuthProperties auth) {
    return new InMemoryUserDetailsManager(
        User.withUsername("owner").password(auth.passwordHash()).roles("OWNER").build());
  }
}
