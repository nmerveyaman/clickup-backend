package org.example.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {
    @Id
    @Column(name = "id")
    private String id;

    private String name;

    @Column(name = "status_name")
    private String statusName;

    @Column(name = "status_type")
    private String statusType;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TaskAssignee> assignees = new ArrayList<>();

    private Boolean archived;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "list_id")
    private Lists list;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Task parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "top_level_parent_id")
    private Task topLevelParent;

    @Column(name = "time_estimate")
    private String timeEstimate;

    private String priority; // DTO'daki priority nesnesinden buraya sadece adı (örn: "high") maplenecek

    @Column(name = "date_created")
    private String dateCreated;

    @Column(name = "date_updated")
    private String dateUpdated;

    @Column(name = "date_closed")
    private String dateClosed;

    @Column(name = "date_done")
    private String dateDone;

    @Column(name = "url")
    private String url; // ClickUp görev linki

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private User creator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_by_task_id")
    private Task blockedByTask;

    @ElementCollection
    @CollectionTable(name = "task_tags", joinColumns = @JoinColumn(name = "task_id"))
    @Column(name = "tag_name")
    @Builder.Default
    private List<String> tags = new ArrayList<>();
}