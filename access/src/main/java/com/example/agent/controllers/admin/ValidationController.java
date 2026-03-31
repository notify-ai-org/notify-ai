package com.example.agent.controllers;

import com.example.agent.EventRepository;
import com.example.agent.EventScheduleRepository;
import com.example.agent.MessageTemplateRepository;
import com.example.agent.models.Event;
import com.example.agent.models.EventSchedule;
import com.example.agent.models.MessageTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for managing entity validation in human-in-the-loop
 * workflows.
 * Provides endpoints to validate events, templates, and schedules, as well as
 * list entities pending validation.
 */
@RestController
@RequestMapping("/api/admin/validation")
@RequiredArgsConstructor
public class ValidationController {

    private final EventRepository eventRepository;
    private final MessageTemplateRepository messageTemplateRepository;
    private final EventScheduleRepository eventScheduleRepository;

    /**
     * Validate an event by ID.
     * 
     * @param eventId     The event ID
     * @param validatedBy The username or identifier of the validator
     * @return Response with validation status
     */
    @PutMapping("/events/{eventId}/validate")
    public ResponseEntity<Map<String, Object>> validateEvent(
            @PathVariable String eventId,
            @RequestParam String validatedBy) {

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));

        event.markAsValidated(validatedBy);
        eventRepository.save(event);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Event validated successfully");
        response.put("eventId", eventId);
        response.put("validatedBy", validatedBy);
        response.put("validatedAt", event.getValidatedAt());

        return ResponseEntity.ok(response);
    }

    /**
     * Validate a message template by ID.
     * 
     * @param templateId  The template ID
     * @param validatedBy The username or identifier of the validator
     * @return Response with validation status
     */
    @PutMapping("/templates/{templateId}/validate")
    public ResponseEntity<Map<String, Object>> validateTemplate(
            @PathVariable String templateId,
            @RequestParam String validatedBy) {

        MessageTemplate template = messageTemplateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));

        template.markAsValidated(validatedBy);
        messageTemplateRepository.save(template);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Template validated successfully");
        response.put("templateId", templateId);
        response.put("validatedBy", validatedBy);
        response.put("validatedAt", template.getValidatedAt());

        return ResponseEntity.ok(response);
    }

    /**
     * Validate an event schedule by ID.
     * 
     * @param scheduleId  The schedule ID
     * @param validatedBy The username or identifier of the validator
     * @return Response with validation status
     */
    @PutMapping("/schedules/{scheduleId}/validate")
    public ResponseEntity<Map<String, Object>> validateSchedule(
            @PathVariable String scheduleId,
            @RequestParam String validatedBy) {

        EventSchedule schedule = eventScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleId));

        schedule.markAsValidated(validatedBy);
        eventScheduleRepository.save(schedule);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Schedule validated successfully");
        response.put("scheduleId", scheduleId);
        response.put("validatedBy", validatedBy);
        response.put("validatedAt", schedule.getValidatedAt());

        return ResponseEntity.ok(response);
    }

    /**
     * List all entities pending validation (validated = false).
     * 
     * @return Map containing lists of pending events, templates, and schedules
     */
    @GetMapping("/pending")
    public ResponseEntity<Map<String, Object>> getPendingValidations() {
        List<Event> pendingEvents = eventRepository.findByValidated(false);
        List<MessageTemplate> pendingTemplates = messageTemplateRepository.findByValidated(false);
        List<EventSchedule> pendingSchedules = eventScheduleRepository.findByValidated(false);

        Map<String, Object> response = new HashMap<>();
        response.put("events", pendingEvents);
        response.put("templates", pendingTemplates);
        response.put("schedules", pendingSchedules);
        response.put("totalPending", pendingEvents.size() + pendingTemplates.size() + pendingSchedules.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Revoke validation for an event (e.g., when entity is modified).
     * 
     * @param eventId The event ID
     * @return Response with revocation status
     */
    @DeleteMapping("/events/{eventId}/validate")
    public ResponseEntity<Map<String, Object>> revokeEventValidation(@PathVariable String eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));

        event.revokeValidation();
        eventRepository.save(event);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Event validation revoked");
        response.put("eventId", eventId);

        return ResponseEntity.ok(response);
    }

    /**
     * Revoke validation for a template.
     * 
     * @param templateId The template ID
     * @return Response with revocation status
     */
    @DeleteMapping("/templates/{templateId}/validate")
    public ResponseEntity<Map<String, Object>> revokeTemplateValidation(@PathVariable String templateId) {
        MessageTemplate template = messageTemplateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));

        template.revokeValidation();
        messageTemplateRepository.save(template);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Template validation revoked");
        response.put("templateId", templateId);

        return ResponseEntity.ok(response);
    }

    /**
     * Revoke validation for a schedule.
     * 
     * @param scheduleId The schedule ID
     * @return Response with revocation status
     */
    @DeleteMapping("/schedules/{scheduleId}/validate")
    public ResponseEntity<Map<String, Object>> revokeScheduleValidation(@PathVariable String scheduleId) {
        EventSchedule schedule = eventScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleId));

        schedule.revokeValidation();
        eventScheduleRepository.save(schedule);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Schedule validation revoked");
        response.put("scheduleId", scheduleId);

        return ResponseEntity.ok(response);
    }
}
