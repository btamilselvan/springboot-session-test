package com.example.sessionleak.config;

import com.example.sessionleak.security.LoadTestAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Mirrors the default Spring Security behaviour (IF_REQUIRED): a session
     * is created the first time something needs to be stored in it, and the
     * resulting SecurityContext is persisted into it by
     * HttpSessionSecurityContextRepository. This is intentionally the
     * out-of-the-box setting, not a misconfiguration.
     */
    @Value("${loadtest.session-creation-policy:IF_REQUIRED}")
    private SessionCreationPolicy sessionCreationPolicy;

    @Value("${loadtest.authority-count:5}")
    private int authorityCount;

    @Value("${loadtest.authority-payload-bytes:10240}")
    private int authorityPayloadBytes;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(sessionCreationPolicy))
                .addFilterBefore(
                        new LoadTestAuthFilter(authorityCount, authorityPayloadBytes),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}
