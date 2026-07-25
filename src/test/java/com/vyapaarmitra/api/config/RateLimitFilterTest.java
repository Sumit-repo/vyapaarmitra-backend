package com.vyapaarmitra.api.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    private MockHttpServletResponse run(RateLimitFilter filter, String uri, String ip)
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRequestURI(uri);
        request.addHeader("X-Forwarded-For", ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void blocksAuthRequestsBeyondTheLimit() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(new RateLimitProperties(true, 3));
        for (int i = 0; i < 3; i++) {
            assertNotEquals(429, run(filter, "/api/v1/auth/login", "1.1.1.1").getStatus());
        }
        MockHttpServletResponse blocked = run(filter, "/api/v1/auth/login", "1.1.1.1");
        assertEquals(429, blocked.getStatus());
        assertEquals("60", blocked.getHeader("Retry-After"));
    }

    @Test
    void doesNotLimitNonAuthPaths() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(new RateLimitProperties(true, 1));
        for (int i = 0; i < 5; i++) {
            assertNotEquals(429, run(filter, "/api/v1/customers", "2.2.2.2").getStatus());
        }
    }

    @Test
    void tracksEachClientIpSeparately() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(new RateLimitProperties(true, 1));
        assertNotEquals(429, run(filter, "/api/v1/auth/login", "1.1.1.1").getStatus());
        assertEquals(429, run(filter, "/api/v1/auth/login", "1.1.1.1").getStatus());
        // A different IP still has its full allowance.
        assertNotEquals(429, run(filter, "/api/v1/auth/login", "9.9.9.9").getStatus());
    }

    @Test
    void disabledFilterNeverBlocks() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(new RateLimitProperties(false, 1));
        for (int i = 0; i < 5; i++) {
            assertNotEquals(429, run(filter, "/api/v1/auth/login", "3.3.3.3").getStatus());
        }
    }
}
