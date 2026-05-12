package com.bvb.sharedmodule.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JWTAuthFilter extends OncePerRequestFilter {

    private final JWTTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService customUserDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        log.debug("Request URI: {}", request.getRequestURI());
        log.debug("Token present: {}", token != null);

        if (StringUtils.hasText(token) && isValidAccessToken(token, response)) {
            String username = jwtTokenProvider.extractUsername(token);
            log.debug("Username from token: {}", username);

            UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);
            log.debug("UserDetails loaded: {}, authorities: {}",
                    userDetails.getUsername(), userDetails.getAuthorities());

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);

            log.debug("Authentication set in SecurityContext");
        }

        filterChain.doFilter(request, response);
    }

    private boolean isValidAccessToken(String token, HttpServletResponse response) throws IOException {
        try {
            if ("refresh".equals(jwtTokenProvider.extractType(token))) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Refresh token not accepted here");
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("Invalid token: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
            return false;
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}