package com.music.aha.config;

import com.music.aha.filter.JwtAuthenticationFilter;
import com.music.aha.service.JwtUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    private final JwtUtils jwtUtils;
    private final UserDetailsService uds;

    @Value("${app.client.origin:http://localhost:4200}")
    private String clientOrigin;

    public SecurityConfig(JwtUtils jwtUtils, UserDetailsService uds) { this.jwtUtils = jwtUtils; this.uds = uds; }

    @Bean
    public SecurityFilterChain filterChain(org.springframework.security.config.annotation.web.builders.HttpSecurity http) throws Exception {
        var jwtFilter = new JwtAuthenticationFilter(jwtUtils, uds);

        http.cors(cors -> cors.configurationSource(request -> {
            var cfg = new org.springframework.web.cors.CorsConfiguration();
            cfg.setAllowedOrigins(java.util.List.of(clientOrigin));
            cfg.setAllowedMethods(java.util.List.of("GET","POST","PUT","DELETE","OPTIONS"));
            cfg.setAllowCredentials(true);
            cfg.setAllowedHeaders(java.util.List.of("*"));
            return cfg;
        }))
        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(ar -> ar
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers("/api/auth/refresh").permitAll()
                .requestMatchers("/api/auth/logout").permitAll()
                .requestMatchers("/api/youtube/login").permitAll()
                .requestMatchers("/api/youtube/oauth2callback").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/youtube/create-playlist").authenticated()
            .anyRequest().authenticated()
        )
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }
}