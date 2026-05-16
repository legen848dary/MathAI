package com.insoftu.mathai.admin.security;

import com.insoftu.mathai.admin.model.AdminUser;
import com.insoftu.mathai.admin.repository.AdminUserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Component
public class AdminJwtFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AdminJwtFilter.class);
    private final JwtUtil jwtUtil;
    private final AdminUserRepository adminUserRepository;

    public AdminJwtFilter(JwtUtil jwtUtil, AdminUserRepository adminUserRepository) {
        this.jwtUtil = jwtUtil;
        this.adminUserRepository = adminUserRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // Skip auth for login, 2FA endpoints, and CORS preflight
        if (path.equals("/api/admin/auth/login")
                || path.equals("/api/admin/auth/2fa/verify")
                || path.equals("/api/admin/auth/refresh")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Handle CORS preflight
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendUnauthorized(response, "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(7);
        Claims claims = jwtUtil.validateToken(token);
        if (claims == null) {
            sendUnauthorized(response, "Invalid or expired token");
            return;
        }

        // Verify user still exists in DB
        UUID userId = UUID.fromString(claims.getSubject());
        Optional<AdminUser> user = adminUserRepository.findById(userId);
        if (user.isEmpty()) {
            sendUnauthorized(response, "Admin user no longer exists");
            return;
        }

        // Check if token should be refreshed (extend session on activity)
        if (jwtUtil.shouldRefresh(claims)) {
            String refreshed = jwtUtil.refreshToken(claims);
            response.setHeader("X-Refreshed-Token", refreshed);
        }

        // Set user attributes for controllers
        request.setAttribute("adminUserId", userId);
        request.setAttribute("adminEmail", claims.get("email", String.class));

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/admin/");
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}");
    }
}
