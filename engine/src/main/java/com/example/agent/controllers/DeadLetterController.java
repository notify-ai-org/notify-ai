package com.example.agent.controllers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.agent.interfaces.DeadLetterManager;
import com.example.agent.models.DeadLetterRecord;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/dlq")
@RequiredArgsConstructor
public class DeadLetterController {

    private final DeadLetterManager manager;

    @GetMapping("/pending")
    public Page<DeadLetterRecord> pending(Pageable pageable) {
        return manager.listPending(pageable);
    }

    @GetMapping("/{id}")
    public DeadLetterRecord get(@PathVariable long id) {
        return manager.get(id);
    }

    @PostMapping("/{id}/replay")
    public void replay(@PathVariable long id, @RequestParam(defaultValue="system") String actor) {
        manager.replay(id, actor);
    }

    @PostMapping("/{id}/discard")
    public void discard(@PathVariable long id,
                        @RequestParam(defaultValue="system") String actor,
                        @RequestParam String reason) {
        manager.discard(id, actor, reason);
    }
}

