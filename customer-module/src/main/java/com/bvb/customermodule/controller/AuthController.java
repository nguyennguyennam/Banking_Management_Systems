package com.bvb.customermodule.controller;

import com.bvb.sharedmodule.service.request.LoginRequest;
import com.bvb.sharedmodule.service.response.AuthResponse;
import com.bvb.sharedmodule.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    private static final long REFRESH_TOKEN_TTL = 604800L;

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {

        AuthResponse auth = authService.login(request);
        setRefreshTokenCookie(response, auth.getRefreshToken(), REFRESH_TOKEN_TTL);

        return ResponseEntity.ok(AuthResponse.builder()
                .accessToken(auth.getAccessToken())
                .accessTokenExpiresIn(auth.getAccessTokenExpiresIn())
                .username(auth.getUsername())
                .role(auth.getRole())
                .customerId(auth.getCustomerId())
                .build());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {

        if (!StringUtils.hasText(refreshToken))
            return ResponseEntity.status(401).build();

        AuthResponse auth = authService.refresh(refreshToken);
        setRefreshTokenCookie(response, auth.getRefreshToken(), REFRESH_TOKEN_TTL);

        return ResponseEntity.ok(AuthResponse.builder()
                .accessToken(auth.getAccessToken())
                .accessTokenExpiresIn(auth.getAccessTokenExpiresIn())
                .username(auth.getUsername())
                .role(auth.getRole())
                .customerId(auth.getCustomerId())
                .build());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response,
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken) {

        String accessToken = resolveToken(request);
        authService.logout(accessToken, refreshToken);
        setRefreshTokenCookie(response, "", 0);

        return ResponseEntity.noContent().build();
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String value, long maxAge) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}