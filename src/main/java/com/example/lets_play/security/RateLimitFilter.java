package com.example.lets_play.security;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class RateLimitFilter extends OncePerRequestFilter{
    private static final long WINDOW_MS = 60_000;

    private static final class Counter {
        final long windowStart;
        int count;
        Counter(long windowStart) {
            this.windowStart = windowStart;
        }
    }

    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final int authLimit;
    private final int generalLimit;

    public RateLimitFilter(int authLimit, int generalLimit) {
        this.authLimit = authLimit;
        this.generalLimit = generalLimit;
    }

    @Override 
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        boolean auth = req.getRequestURI().startsWith("/auth/");
        String key = (auth ? "auth:" : "gen:") + req.getRemoteAddr();
        int limit = auth ? authLimit : generalLimit;
        long now = System.currentTimeMillis();

        boolean[] allowed = {true};
        long[] retryAfterSeconds = {0};
        counters.compute(key, (k, c) -> {
            if (c == null || now - c.windowStart >= WINDOW_MS) c = new Counter(now);
            c.count++;
            if (c.count > limit) {
                allowed[0] = false;
                retryAfterSeconds[0] = Math.max(1, (c.windowStart + WINDOW_MS - now + 999) / 1000);
            }
            return c;
        });

        if (counters.size() > 10_000) {
            counters.entrySet().removeIf(e -> now - e.getValue().windowStart >= WINDOW_MS);
        }

        if (!allowed[0]) {
            res.setHeader("Retry-After", String.valueOf(retryAfterSeconds[0]));
            ErrorWriter.write(req, res, HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please slow down and try again later.");
            return;
        }
        chain.doFilter(req, res);
    }
}
