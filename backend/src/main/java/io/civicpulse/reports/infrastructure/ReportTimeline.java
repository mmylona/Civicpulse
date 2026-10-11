package io.civicpulse.reports.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.civicpulse.reports.domain.ActorKind;
import io.civicpulse.reports.domain.TimelineType;
import io.civicpulse.reports.domain.Visibility;
import io.civicpulse.shared.Ids;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

// report_events is append-only, so plain SQL instead of an entity
@Repository
public class ReportTimeline {

    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    ReportTimeline(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void append(UUID reportId, TimelineType type, Visibility visibility, UUID actorId, ActorKind actorKind,
            Map<String, Object> payload, Instant occurredAt) {
        jdbc.sql("""
                        INSERT INTO report_events
                            (id, report_id, type, visibility, actor_id, actor_kind, payload, occurred_at)
                        VALUES
                            (:id, :reportId, :type, :visibility, :actorId, :actorKind, CAST(:payload AS jsonb), :occurredAt)
                        """)
                .param("id", Ids.newId())
                .param("reportId", reportId)
                .param("type", type.name())
                .param("visibility", visibility.name())
                .param("actorId", actorId)
                .param("actorKind", actorKind.name())
                .param("payload", toJson(payload))
                .param("occurredAt", OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
                .update();
    }

    // the visibility filter is part of the query on purpose
    public List<Entry> list(UUID reportId, boolean includeInternal) {
        String visibility = includeInternal ? "" : " AND visibility = 'PUBLIC'";
        return jdbc.sql("""
                        SELECT id, type, visibility, actor_id, actor_kind, payload::text AS payload, occurred_at
                        FROM report_events
                        WHERE report_id = :reportId""" + visibility + """
                         ORDER BY seq
                        """)
                .param("reportId", reportId)
                .query(this::mapRow)
                .list();
    }

    private Entry mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Entry(
                rs.getObject("id", UUID.class),
                TimelineType.valueOf(rs.getString("type")),
                Visibility.valueOf(rs.getString("visibility")),
                rs.getObject("actor_id", UUID.class),
                ActorKind.valueOf(rs.getString("actor_kind")),
                fromJson(rs.getString("payload")),
                rs.getObject("occurred_at", OffsetDateTime.class).toInstant());
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Timeline payload is not serializable", e);
        }
    }

    private Map<String, Object> fromJson(String json) {
        try {
            return objectMapper.readValue(json, PAYLOAD_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Timeline payload is not valid JSON", e);
        }
    }

    public record Entry(
            UUID id,
            TimelineType type,
            Visibility visibility,
            UUID actorId,
            ActorKind actorKind,
            Map<String, Object> payload,
            Instant occurredAt) {
    }
}
