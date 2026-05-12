package com.bvb.sharedmodule.security;

import org.springframework.security.core.userdetails.UserDetails;

import java.util.UUID;

public interface UserPrincipal extends UserDetails {
    UUID getCustomerId();
}
