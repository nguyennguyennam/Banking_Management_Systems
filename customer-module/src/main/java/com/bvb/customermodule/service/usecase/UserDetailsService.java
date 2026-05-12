package com.bvb.customermodule.service.usecase;

import com.bvb.customermodule.domain.UserAuth;
import com.bvb.customermodule.repository.UserAuthRepository;
import com.bvb.sharedmodule.security.CustomUserDetailsService;
import com.bvb.sharedmodule.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsService implements CustomUserDetailsService {

    private final UserAuthRepository userAuthRepository;

    @Override
    public UserPrincipal loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAuth user = userAuthRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        log.debug("Loaded user: {}, passwordHash: {}",
                user.getUsername(), user.getPasswordHash());
        return user;
    }
}