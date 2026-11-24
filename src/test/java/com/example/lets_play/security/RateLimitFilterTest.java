package com.example.lets_play.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    private MockHttpServletResponse call(RateLimitFilter filter, String uri, String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", uri);
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }

    @Test
    void authEndpoint_isBlockedAfterLimit_with429AndRetryAfter() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(2, 100);
        assertEquals(200, call(filter, "/auth/login", "1.1.1.1").getStatus());
        assertEquals(200, call(filter, "/auth/login", "1.1.1.1").getStatus());
        MockHttpServletResponse blocked = call(filter, "/auth/login", "1.1.1.1");
        assertEquals(429, blocked.getStatus());
        assertNotNull(blocked.getHeader("Retry-After"));
        assertTrue(blocked.getContentAsString().contains("\"status\":429"));
    }

    @Test
    void limitsAreTrackedPerClientIp() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, 100);
        assertEquals(200, call(filter, "/auth/login", "1.1.1.1").getStatus());
        assertEquals(429, call(filter, "/auth/login", "1.1.1.1").getStatus());
        assertEquals(200, call(filter, "/auth/login", "2.2.2.2").getStatus());
    }

    @Test
    void generalEndpoints_useTheHigherLimit() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, 3);
        for (int i = 0; i < 3; i++) {
            assertEquals(200, call(filter, "/products", "1.1.1.1").getStatus());
        }
        assertEquals(429, call(filter, "/products", "1.1.1.1").getStatus());
    }
}