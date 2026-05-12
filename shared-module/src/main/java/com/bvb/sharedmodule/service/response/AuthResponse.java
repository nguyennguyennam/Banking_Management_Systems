package com.bvb.sharedmodule.service.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private long accessTokenExpiresIn;  // seconds
    private long refreshTokenExpiresIn; // seconds
    private String username;
    private String role;
    private UUID customerId;
}