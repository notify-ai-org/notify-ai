package com.example.agent;

import com.example.agent.models.ConfigEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfigEntryRepository extends JpaRepository<ConfigEntry, String> {
}
