package io.civicpulse.identity.domain;

import io.civicpulse.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "teams")
public class Team extends AssignedIdEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Team() {
    }

    public Team(UUID id, String name, Instant createdAt) {
        super(id);
        this.name = name;
        this.createdAt = createdAt;
    }

    public String name() {
        return name;
    }
}
