package com.example.lets_play.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.example.lets_play.security.JWTService;
import com.example.lets_play.security.JwtAuthFilter;
import com.example.lets_play.security.RestAccessDeniedHandler;
import com.example.lets_play.security.RestAuthenticationEntryPoint;
import com.example.lets_play.users.UserRepository;

@Configuration 
@EnableWebSecurity 
public class SecurityConfig {
    @Bean 
    public SecurityFilterChain filterChain(HttpSecurity http, JWTService jwtService, UserRepository users, RestAuthenticationEntryPoint entrypoint, RestAccessDeniedHandler deniedHandler) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers(HttpMethod.GET, "/products", "/products/**").permitAll()
                .requestMatchers("/users", "/users/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .exceptionHandling(e -> e
                .authenticationEntryPoint(entrypoint)
                .accessDeniedHandler(deniedHandler)
            )
            .addFilterBefore(new JwtAuthFilter(jwtService, users), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}