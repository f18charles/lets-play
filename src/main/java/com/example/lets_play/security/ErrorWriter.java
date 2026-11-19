package com.example.lets_play.security;

import java.io.IOException;
import java.time.Instant;

import org.springframework.http.HttpStatus;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ErrorWriter {
    private ErrorWriter() {}

    static void write(HttpServletRequest req, HttpServletResponse res, HttpStatus status, String message) throws IOException {
        res.setStatus(status.value());
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write(String.format(
            "{\"timestamp\":\"%s\",\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"path\":\"%s\"}"
            , Instant.now(), status.value(), esc(status.getReasonPhrase()), esc(message), esc(req.getRequestURI())
        ));
    }

    private static String esc(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");                    
                default -> sb.append(c < 0x20 ? ' ' : c);
            }
        }
        return sb.toString();
    }
}
