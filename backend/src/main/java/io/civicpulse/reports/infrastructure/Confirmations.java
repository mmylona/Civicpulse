package io.civicpulse.reports.infrastructure;

import io.civicpulse.reports.domain.ConfirmationKind;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class Confirmations {

    private final JdbcClient jdbc;

    Confirmations(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void save(UUID reportId, UUID userId, ConfirmationKind kind, Instant now) {
        jdbc.sql("""
                        INSERT INTO confirmations (report_id, user_id, kind, created_at, updated_at)
                        VALUES (:reportId, :userId, :kind, :now, :now)
                        ON CONFLICT (report_id, user_id)
                        DO UPDATE SET kind = excluded.kind, updated_at = excluded.updated_at
                        """)
                .param("reportId", reportId)
                .param("userId", userId)
                .param("kind", kind.name())
                .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .update();
    }

    public boolean delete(UUID reportId, UUID userId) {
        return jdbc.sql("DELETE FROM confirmations WHERE report_id = :reportId AND user_id = :userId")
                .param("reportId", reportId)
                .param("userId", userId)
                .update() > 0;
    }

    public Optional<ConfirmationKind> find(UUID reportId, UUID userId) {
        return jdbc.sql("SELECT kind FROM confirmations WHERE report_id = :reportId AND user_id = :userId")
                .param("reportId", reportId)
                .param("userId", userId)
                .query(String.class)
                .optional()
                .map(ConfirmationKind::valueOf);
    }

    // the counters on reports are not written by JPA, only here
    public Counts recount(UUID reportId) {
        return jdbc.sql("""
                        UPDATE reports SET
                            still_present_count = (SELECT count(*) FROM confirmations
                                                   WHERE report_id = :reportId AND kind = 'STILL_PRESENT'),
                            fixed_count = (SELECT count(*) FROM confirmations
                                           WHERE report_id = :reportId AND kind = 'FIXED')
                        WHERE id = :reportId
                        RETURNING still_present_count, fixed_count
                        """)
                .param("reportId", reportId)
                .query((rs, rowNum) -> new Counts(rs.getInt("still_present_count"), rs.getInt("fixed_count")))
                .single();
    }

    public int stillPresentSince(UUID reportId, Instant since) {
        return jdbc.sql("""
                        SELECT count(*) FROM confirmations
                        WHERE report_id = :reportId AND kind = 'STILL_PRESENT' AND updated_at > :since
                        """)
                .param("reportId", reportId)
                .param("since", OffsetDateTime.ofInstant(since, ZoneOffset.UTC))
                .query(Integer.class)
                .single();
    }

    public record Counts(int stillPresent, int fixed) {
    }
}
