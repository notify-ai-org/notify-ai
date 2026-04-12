package com.notify.agent.controllers.admin;

import com.notify.agent.EventScheduleRepository;
import com.notify.agent.MessageTemplateRepository;
import com.notify.agent.models.EventSchedule;
import com.notify.agent.models.MessageTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/templates-schedules")
public class TemplateScheduleController {

    private final MessageTemplateRepository templateRepo;
    private final EventScheduleRepository scheduleRepo;

    public TemplateScheduleController(MessageTemplateRepository templateRepo, EventScheduleRepository scheduleRepo) {
        this.templateRepo = templateRepo;
        this.scheduleRepo = scheduleRepo;
    }

    @GetMapping("/templates")
    public ResponseEntity<List<MessageTemplate>> getAllTemplates() {
        return ResponseEntity.ok(templateRepo.findAll());
    }

    @PostMapping("/templates")
    public ResponseEntity<MessageTemplate> saveTemplate(@RequestBody MessageTemplate template) {
        return ResponseEntity.ok(templateRepo.save(template));
    }

    @GetMapping("/schedules")
    public ResponseEntity<List<EventSchedule>> getAllSchedules() {
        return ResponseEntity.ok(scheduleRepo.findAll());
    }

    @PostMapping("/schedules")
    public ResponseEntity<EventSchedule> saveSchedule(@RequestBody EventSchedule schedule) {
        return ResponseEntity.ok(scheduleRepo.save(schedule));
    }
}
