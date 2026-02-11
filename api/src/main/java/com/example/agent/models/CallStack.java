package com.example.agent.models;

import jakarta.persistence.*;
import lombok.Data;
import java.util.List;

@Embeddable
@Data
public class CallStack {
    @ElementCollection
    @CollectionTable(name = "call_stack_frames", joinColumns = @JoinColumn(name = "event_id"))
    private List<StackFrame> frames;
}
