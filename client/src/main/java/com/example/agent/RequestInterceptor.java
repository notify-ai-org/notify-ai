package com.example.sdk.interceptor;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;

/**
 * Intercepts incoming HTTP requests to attach context and optionally
 * notify the Agent Server for monitoring and traceability.
 */
public class RequestInterceptor implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;

        // Add correlation ID
        String correlationId = UUID.randomUUID().toString();
        request.setAttribute("correlationId", correlationId);

        System.out.println("🛰️ Incoming Request interscepted: " + request.getRequestURI() +
                " | correlationId=" + correlationId);

        chain.doFilter(req, res);
    }
}
