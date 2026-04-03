package com.example.agent;

import com.example.agent.models.ClientEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for managing ClientEntity.
 */
@Repository
public interface ClientRepository extends JpaRepository<ClientEntity, String> {

    /**
     * Find a client by their unique identifier.
     *
     * @param clientId The client identifier
     * @return Optional containing the client entity if found
     */
    Optional<ClientEntity> findByClientId(String clientId);
}
