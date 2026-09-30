package com.aigateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_REQUEST_ID = "requestId";
    private static final String MDC_USER_ID = "userId";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        long startTime = System.currentTimeMillis();

        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (!StringUtils.hasText(requestId) || !requestId.matches("^[a-zA-Z0-9\\-]+$")) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = response.getStatus();
            
            String userId = MDC.get(MDC_USER_ID);
            String userContext = (userId != null) ? " userId=" + userId : "";

            if (status >= 400) {
                // Determine if there was an exception passed to request attributes by GlobalExceptionHandler
                String errorType = (String) request.getAttribute("errorType");
                String errorContext = (errorType != null) ? " errorType=" + errorType : "";
                log.error("HTTP {} {}{} -> {} ({}ms){}", 
                        request.getMethod(), request.getRequestURI(), userContext, status, duration, errorContext);
            } else {
                log.info("HTTP {} {}{} -> {} ({}ms)", 
                        request.getMethod(), request.getRequestURI(), userContext, status, duration);
            }

            MDC.clear();
        }
    }
}
