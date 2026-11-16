package community.zomi;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class Security {
  @Bean
  PasswordEncoder passwords() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  SecurityFilterChain chain(HttpSecurity h) throws Exception {
    return h.authorizeHttpRequests(a -> a.anyRequest().permitAll())
        .csrf(
            c ->
                c.csrfTokenRepository(
                    new org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository()))
        .headers(
            x ->
                x.contentSecurityPolicy(
                    c -> c.policyDirectives("default-src 'self'; frame-ancestors 'none'")))
        .build();
  }
}
