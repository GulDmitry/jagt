package dev.jagt.orchestrator.surface.board.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "orchestrator.auth", name = "mode", havingValue = "password")
class PasswordLogin {

  @Bean
  SecurityFilterChain passwordChain(HttpSecurity http) throws Exception {
    String page = PublicPaths.fromContract().loginPage();
    GuardedBoard.guard(http, page)
        .formLogin(form -> form.loginPage(page).loginProcessingUrl("/login").permitAll());
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
