package com.bvb.sharedmodule.service;

import com.bvb.sharedmodule.security.CustomUserDetailsService;
import com.bvb.sharedmodule.security.JWTTokenProvider;
import com.bvb.sharedmodule.service.request.LoginRequest;
import com.bvb.sharedmodule.service.response.AuthResponse;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import com.bvb.sharedmodule.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JWTTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;

    // ── LOGIN ─────────────────────────────────────────────────────────────────

    public AuthResponse login(LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        UserPrincipal userDetails = (UserPrincipal) auth.getPrincipal();
        log.info("DEBUG customerId: {}", userDetails.getCustomerId());

        String role = userDetails.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("USER");

        String accessToken = jwtTokenProvider.generateAccessToken(userDetails.getUsername(), role);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails.getUsername(), role);

        log.info("User logged in: {}", userDetails.getUsername());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresIn(900L)
                .username(userDetails.getUsername())
                .role(role)
                .customerId(userDetails.getCustomerId())
                .build();
    }

    // ── LOGOUT ────────────────────────────────────────────────────────────────

    public void logout(String accessToken, String refreshToken) {
        List<String> tokens = List.of(
                accessToken != null ? accessToken : "",
                refreshToken != null ? refreshToken : ""
        ).stream().filter(StringUtils::hasText).toList();

        jwtTokenProvider.blacklistAll(tokens);
        log.info("Tokens blacklisted");
    }

    // ── REFRESH ───────────────────────────────────────────────────────────────

    public AuthResponse refresh(String refreshToken) {
        if (!StringUtils.hasText(refreshToken))
            throw new IllegalArgumentException("Refresh token is required");

        Claims claims = jwtTokenProvider.getClaim(refreshToken);

        if (!"refresh".equals(claims.get("type", String.class)))
            throw new IllegalArgumentException("Token is not a refresh token");

        // Check blacklist
        if (jwtTokenProvider.isBlacklisted(claims.getId()))
            throw new IllegalArgumentException("Refresh token has been revoked");

        String username = claims.getSubject();
        UserPrincipal userDetails = userDetailsService.loadUserByUsername(username);

        String role = userDetails.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("USER");

        // Rotate — blacklist old refresh token
        jwtTokenProvider.blacklistAll(List.of(refreshToken));

        String newAccessToken = jwtTokenProvider.generateAccessToken(username, role);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(username, role);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .accessTokenExpiresIn(900L)
                .username(username)
                .role(role)
                .customerId(userDetails.getCustomerId())
                .build();
    }
}