package com.example.lets_play.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component 
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest req, HttpServletResponse res, AuthenticationException ex) throws IOException {
        ErrorWriter.write(req, res, HttpStatus.UNAUTHORIZED, "Authentication failed: missing or invalid token");
    }
}
