package com.notify.agent;

import com.notify.agent.models.ConfigEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfigEntryRepository extends JpaRepository<ConfigEntry, String> {
}
