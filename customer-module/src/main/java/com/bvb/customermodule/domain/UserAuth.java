package com.bvb.customermodule.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnTransformer;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.bvb.sharedmodule.security.Role;
import com.bvb.sharedmodule.security.UserPrincipal;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "user_auth")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserAuth implements UserPrincipal {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false, unique = true, updatable = false)
    private UUID customerId;

    @Column(nullable = false, unique = true, length = 255)
    private String username;

    // Mapped to password_hash column — getPassword() returns this
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @ColumnTransformer(read = "role::text", write = "?::user_role_")
    @Column(nullable = false, columnDefinition = "USER_ROLE_")
    private Role role;

    @Column(nullable = false)
    private boolean active;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_login")
    private Instant lastLogin;

    // ── UserPrincipal ──────────────────────────────────────────────────────────

    @Override
    public UUID getCustomerId() { return customerId; }

    // ── UserDetails ────────────────────────────────────────────────────────────

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return active; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return active; }

    // ── Business methods ───────────────────────────────────────────────────────

    public void recordLogin() {
        this.lastLogin = Instant.now();
    }

    public void deactivate() { this.active = false; }

    public void activate() { this.active = true; }
}