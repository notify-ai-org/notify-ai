package com.example.agent;

import com.example.agent.records.MemoryPage;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MemoryPageRepository extends JpaRepository<MemoryPage, String> {

    Optional<MemoryPage> findByNamespaceAndWindowStart(
            String namespace,
            Instant windowStart);

}
