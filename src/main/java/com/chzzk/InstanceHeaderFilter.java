package com.chzzk;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// 5단계: 어느 인스턴스(파드)가 응답했는지 X-Chzzk-Instance 헤더로 보여준다 (요청 분산 확인용)
@Component
public class InstanceHeaderFilter extends OncePerRequestFilter {
    private final String instance = System.getenv().getOrDefault("HOSTNAME", "local");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Chzzk-Instance", instance);
        chain.doFilter(request, response);
    }
}
