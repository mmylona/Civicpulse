package io.civicpulse.reports.infrastructure;

import io.civicpulse.reports.domain.Report;
import io.civicpulse.reports.domain.ReportStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, UUID>, JpaSpecificationExecutor<Report> {

    @Query(value = "SELECT nextval('report_number_seq')", nativeQuery = true)
    long nextNumber();

    // used when changing confirmations, so concurrent recounts see each other's rows
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Report r WHERE r.id = :id")
    Optional<Report> lockById(@Param("id") UUID id);

    Optional<Report> findByReporterIdAndSubmissionKey(UUID reporterId, String submissionKey);

    List<Report> findTop100ByStatusAndResolvedAtBeforeOrderByResolvedAtAsc(ReportStatus status, Instant cutoff);

    // keeps duplicate chains one level deep
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Report r SET r.duplicateOfId = :target WHERE r.duplicateOfId = :source")
    int moveDuplicates(@Param("source") UUID source, @Param("target") UUID target);
}
