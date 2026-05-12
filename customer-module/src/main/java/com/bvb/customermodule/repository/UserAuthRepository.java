package com.bvb.customermodule.repository;

import com.bvb.customermodule.domain.UserAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserAuthRepository extends JpaRepository <UserAuth, UUID> {

    //Username = phone number since it is unique
    Optional<UserAuth> findByUsername(String username);

    //Update password and last login time
    @Modifying
    @Query ("UPDATE UserAuth u SET u.passwordHash = :passwordHash, u.lastLogin = current_timestamp WHERE u.id = :id")
    void updatePassword (UUID id, String passWord);

    @Modifying
    @Query ("UPDATE UserAuth u SET u.lastLogin = current_timestamp WHERE u.id = :id")
    void updateLastLogin(UUID id);

    @Modifying
    @Query ("UPDATE UserAuth u SET u.active = :active WHERE u.username = :username")
    void updateActive (String username, boolean active);
}
