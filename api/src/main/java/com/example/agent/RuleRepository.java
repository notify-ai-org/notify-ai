package com.example.agent;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.agent.models.Rule;

@Repository
public interface RuleRepository extends JpaRepository<Rule, String> {
    
    @Query("SELECT r FROM Rule r WHERE r.eventName = :eventName")
    List<Rule> findByEventName(@Param("eventName") String eventName);
}
