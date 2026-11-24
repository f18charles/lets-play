package com.example.lets_play.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.example.lets_play.security.JWTService;
import com.example.lets_play.security.JwtAuthFilter;
import com.example.lets_play.security.RateLimitFilter;
import com.example.lets_play.security.RestAccessDeniedHandler;
import com.example.lets_play.security.RestAuthenticationEntryPoint;
import com.example.lets_play.users.UserRepository;

@Configuration 
@EnableWebSecurity 
public class SecurityConfig {
    private final int authLimit;
    private final int generalLimit;

    public SecurityConfig(@Value("${app.rate-limit.auth-per-minute}") int authLimit, @Value("${app.rate-limit.general-per-minute}") int generalLimit) {
        this.authLimit = authLimit;
        this.generalLimit = generalLimit;
    }

    @Bean 
    public SecurityFilterChain filterChain(HttpSecurity http, JWTService jwtService, UserRepository users, RestAuthenticationEntryPoint entrypoint, RestAccessDeniedHandler deniedHandler) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
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
            .addFilterBefore(new RateLimitFilter(authLimit, generalLimit), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new JwtAuthFilter(jwtService, users), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}