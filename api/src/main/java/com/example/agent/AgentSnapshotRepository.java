package com.example.agent;

import com.example.agent.models.AgentSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentSnapshotRepository extends JpaRepository<AgentSnapshot, String> {
}
