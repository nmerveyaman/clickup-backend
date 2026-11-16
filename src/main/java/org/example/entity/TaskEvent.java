package org.example.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "task_events")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TaskEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "field_name", length = 100)
    private String fieldName;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "changed_by_user_id")
    private Long changedByUserId;

    @Column(name = "timestamp", length = 100)
    private String timestamp;

    @Column(name = "idempotency_key", length = 255, unique = true)
    private String idempotencyKey;

    @Column(name = "history_item_id", length = 100)
    private String historyItemId;

    @Column(name = "trigger_source", length = 50)
    @Builder.Default
    private String triggerSource = "WEBHOOK";
}
