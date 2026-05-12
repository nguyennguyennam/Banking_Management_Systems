package com.bvb.sharedmodule.security;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public interface CustomUserDetailsService extends UserDetailsService {

    @Override
    UserPrincipal loadUserByUsername(String username) throws UsernameNotFoundException;
}
