package com.notify.agent;

import com.notify.agent.models.TenantRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantRegistryRepository extends JpaRepository<TenantRegistry, Long> {
    Optional<TenantRegistry> findByTenantId(String tenantId);
}
