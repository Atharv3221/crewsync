package github.atharv3221.crewsync.auth;

import github.atharv3221.crewsync.common.ApiErrorWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

  private static final String[] STATIC_FILES = {
      "/", "/index.html", "/assets/**", "/favicon.ico", "/favicon.svg"
  };

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
      ApiErrorWriter errorWriter) {
    return http
        .cors(Customizer.withDefaults())
        .csrf(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(requests -> requests
            .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
            .requestMatchers("/ws/**", "/error").permitAll()
            .requestMatchers(HttpMethod.GET, STATIC_FILES).permitAll()
            .anyRequest().authenticated())
        .exceptionHandling(exceptions -> exceptions
            .authenticationEntryPoint((request, response, exception) ->
                errorWriter.write(response, HttpStatus.UNAUTHORIZED, "Authentication required"))
            .accessDeniedHandler((request, response, exception) ->
                errorWriter.write(response, HttpStatus.FORBIDDEN, "Access denied")))
        .addFilterBefore(new JwtAuthenticationFilter(jwtService),
            UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
