package com.example.gpapi.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** 브라우저 요청을 위한 CORS, Referrer Policy 및 로컬 네트워크 접근 헤더를 설정한다. */
@Configuration
public class CorsConfig {

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> permissiveCorsFilter() {
        OncePerRequestFilter filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    FilterChain filterChain) throws ServletException, IOException {

                response.setHeader("Referrer-Policy", "unsafe-url");

                String origin = request.getHeader("Origin");
                if (origin != null && !origin.isBlank()) {
                    response.setHeader("Access-Control-Allow-Origin", "*");
                    response.setHeader(
                            "Access-Control-Allow-Methods",
                            "GET,POST,PUT,PATCH,DELETE,OPTIONS,HEAD");

                    String requestedHeaders = request.getHeader("Access-Control-Request-Headers");
                    response.setHeader(
                            "Access-Control-Allow-Headers",
                            requestedHeaders == null || requestedHeaders.isBlank()
                                    ? "*"
                                    : requestedHeaders);
                    response.setHeader("Access-Control-Expose-Headers", "*");
                    response.setHeader("Access-Control-Max-Age", "3600");

                    if ("true".equalsIgnoreCase(
                            request.getHeader("Access-Control-Request-Private-Network"))) {
                        response.setHeader("Access-Control-Allow-Private-Network", "true");
                    }
                }

                boolean preflight = "OPTIONS".equalsIgnoreCase(request.getMethod())
                        && origin != null
                        && request.getHeader("Access-Control-Request-Method") != null;
                if (preflight) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    return;
                }

                filterChain.doFilter(request, response);
            }
        };

        FilterRegistrationBean<OncePerRequestFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
