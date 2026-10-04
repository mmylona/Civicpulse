package io.civicpulse.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.civicpulse.shared.Ids;
import io.civicpulse.shared.web.PageResponse;
import io.civicpulse.shared.web.RequestIdFilter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class AuditLog {

    private static final TypeReference<Map<String, Object>> DETAILS_TYPE = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    AuditLog(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    void record(Instant occurredAt, UUID actorId, String action, String targetType, String targetId,
            Map<String, Object> details) {
        jdbc.sql("""
                        INSERT INTO audit_entries
                            (id, occurred_at, actor_id, action, target_type, target_id, details, request_id)
                        VALUES
                            (:id, :occurredAt, :actorId, :action, :targetType, :targetId, CAST(:details AS jsonb), :requestId)
                        """)
                .param("id", Ids.newId())
                .param("occurredAt", OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
                .param("actorId", actorId)
                .param("action", action)
                .param("targetType", targetType)
                .param("targetId", targetId)
                .param("details", toJson(details))
                .param("requestId", MDC.get(RequestIdFilter.MDC_KEY))
                .update();
    }

    PageResponse<AuditEntry> search(AuditQuery query, int page, int size) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        if (query.action() != null) {
            conditions.add("action = :action");
            params.put("action", query.action());
        }
        if (query.actorId() != null) {
            conditions.add("actor_id = :actorId");
            params.put("actorId", query.actorId());
        }
        if (query.targetType() != null) {
            conditions.add("target_type = :targetType");
            params.put("targetType", query.targetType());
        }
        if (query.targetId() != null) {
            conditions.add("target_id = :targetId");
            params.put("targetId", query.targetId());
        }
        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);

        long total = jdbc.sql("SELECT count(*) FROM audit_entries" + where)
                .params(params)
                .query(Long.class)
                .single();

        params.put("limit", size);
        params.put("offset", (long) page * size);
        List<AuditEntry> entries = jdbc.sql("""
                        SELECT id, occurred_at, actor_id, action, target_type, target_id, details::text AS details, request_id
                        FROM audit_entries""" + where + """
                         ORDER BY occurred_at DESC, id DESC
                         LIMIT :limit OFFSET :offset
                        """)
                .params(params)
                .query(this::mapRow)
                .list();
        return PageResponse.of(entries, page, size, total);
    }

    private AuditEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AuditEntry(
                rs.getObject("id", UUID.class),
                rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                rs.getObject("actor_id", UUID.class),
                rs.getString("action"),
                rs.getString("target_type"),
                rs.getString("target_id"),
                fromJson(rs.getString("details")),
                rs.getString("request_id"));
    }

    private String toJson(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Audit details are not serializable", e);
        }
    }

    private Map<String, Object> fromJson(String json) {
        try {
            return objectMapper.readValue(json, DETAILS_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Audit details are not valid JSON", e);
        }
    }

    record AuditQuery(String action, UUID actorId, String targetType, String targetId) {
    }
}
