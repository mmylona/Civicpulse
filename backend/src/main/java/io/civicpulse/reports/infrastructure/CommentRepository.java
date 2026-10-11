package io.civicpulse.reports.infrastructure;

import io.civicpulse.reports.domain.Comment;
import io.civicpulse.reports.domain.Visibility;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    List<Comment> findByReportIdAndVisibilityInOrderByCreatedAtAsc(UUID reportId, Collection<Visibility> visibilities);

    Optional<Comment> findByIdAndReportId(UUID id, UUID reportId);
}
