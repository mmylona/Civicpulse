package io.civicpulse.reports.domain;

import io.civicpulse.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "report_comments")
public class Comment extends AssignedIdEntity {

    @Column(name = "report_id", nullable = false, updatable = false)
    private UUID reportId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_kind", nullable = false, updatable = false)
    private ActorKind authorKind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Visibility visibility;

    @Column(nullable = false, updatable = false)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "hidden_at")
    private Instant hiddenAt;

    @Column(name = "hidden_by")
    private UUID hiddenBy;

    @Column(name = "hidden_reason")
    private String hiddenReason;

    protected Comment() {
    }

    public Comment(UUID id, UUID reportId, UUID authorId, ActorKind authorKind, Visibility visibility, String body,
            Instant createdAt) {
        super(id);
        this.reportId = reportId;
        this.authorId = authorId;
        this.authorKind = authorKind;
        this.visibility = visibility;
        this.body = body;
        this.createdAt = createdAt;
    }

    // the text is kept, it is only hidden from citizens
    public boolean hide(UUID moderatorId, String reason, Instant now) {
        if (isHidden()) {
            return false;
        }
        hiddenAt = now;
        hiddenBy = moderatorId;
        hiddenReason = reason;
        return true;
    }

    public boolean unhide() {
        if (!isHidden()) {
            return false;
        }
        hiddenAt = null;
        hiddenBy = null;
        hiddenReason = null;
        return true;
    }

    public boolean isHidden() {
        return hiddenAt != null;
    }

    public UUID id() {
        return getId();
    }

    public UUID reportId() {
        return reportId;
    }

    public UUID authorId() {
        return authorId;
    }

    public ActorKind authorKind() {
        return authorKind;
    }

    public Visibility visibility() {
        return visibility;
    }

    public String body() {
        return body;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant hiddenAt() {
        return hiddenAt;
    }

    public UUID hiddenBy() {
        return hiddenBy;
    }

    public String hiddenReason() {
        return hiddenReason;
    }
}
