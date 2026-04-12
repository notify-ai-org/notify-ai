package com.notify.agent;

import com.notify.agent.models.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {
    Optional<AdminUser> findByGoogleUserId(String googleUserId);

    Optional<AdminUser> findByEmail(String email);
}
