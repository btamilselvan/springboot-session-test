package com.example.sessionleak.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Authenticates every request unconditionally (no credential check) so the
 * demo can focus purely on what happens to the resulting SecurityContext
 * once it reaches the HttpSession, not on an auth mechanism.
 */
public class LoadTestAuthFilter extends OncePerRequestFilter {

    private final int authorityCount;
    private final int authorityPayloadBytes;

    public LoadTestAuthFilter(int authorityCount, int authorityPayloadBytes) {
        this.authorityCount = authorityCount;
        this.authorityPayloadBytes = authorityPayloadBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isBlank()) {
            userId = "user-" + System.nanoTime();
        }

        List<GrantedAuthority> authorities = new ArrayList<>(authorityCount);
        for (int i = 0; i < authorityCount; i++) {
            authorities.add(new FatAuthority("ROLE_PERMISSION_" + i, authorityPayloadBytes));
        }

        Authentication authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}
