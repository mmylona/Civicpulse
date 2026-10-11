package io.civicpulse.reports.application;

import io.civicpulse.identity.UserDirectory;
import io.civicpulse.reports.CommentModerated;
import io.civicpulse.reports.domain.Actor;
import io.civicpulse.reports.domain.Comment;
import io.civicpulse.reports.domain.Report;
import io.civicpulse.reports.domain.ReportErrorCode;
import io.civicpulse.reports.domain.Visibility;
import io.civicpulse.reports.infrastructure.CommentRepository;
import io.civicpulse.reports.infrastructure.ReportRepository;
import io.civicpulse.shared.Ids;
import io.civicpulse.shared.error.ApiException;
import io.civicpulse.shared.security.CurrentUser;
import io.civicpulse.shared.security.Role;
import io.civicpulse.shared.security.SecurityErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {

    private final ReportRepository reports;
    private final CommentRepository comments;
    private final UserDirectory directory;
    private final Actors actors;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    CommentService(ReportRepository reports, CommentRepository comments, UserDirectory directory, Actors actors,
            ApplicationEventPublisher events, Clock clock) {
        this.reports = reports;
        this.comments = comments;
        this.directory = directory;
        this.actors = actors;
        this.events = events;
        this.clock = clock;
    }

    // internal comments are filtered out in the query for everyone but staff
    @Transactional(readOnly = true)
    public List<CommentView> list(UUID reportId, CurrentUser user) {
        load(reportId);
        Viewer viewer = actors.viewer(user);
        Set<Visibility> visible = viewer.isStaff() ? EnumSet.allOf(Visibility.class) : EnumSet.of(Visibility.PUBLIC);
        List<Comment> found = comments.findByReportIdAndVisibilityInOrderByCreatedAtAsc(reportId, visible);
        Map<UUID, String> names = viewer.isStaff()
                ? directory.displayNames(found.stream().map(Comment::authorId).collect(Collectors.toSet()))
                : Map.of();
        return found.stream().map(comment -> view(comment, viewer, names)).toList();
    }

    @Transactional
    public CommentView add(UUID reportId, NewComment request, CurrentUser user) {
        Report report = load(reportId);
        Actor actor = actors.of(user);
        Visibility visibility = request.visibility() == null ? Visibility.PUBLIC : request.visibility();
        if (visibility == Visibility.INTERNAL && !actor.isStaff()) {
            throw new ApiException(SecurityErrorCode.ACCESS_DENIED);
        }
        if (report.status().isClosed() && !actor.isStaff()) {
            throw new ApiException(ReportErrorCode.REPORT_CLOSED);
        }
        Comment comment = new Comment(Ids.newId(), reportId, user.id(), report.kindOf(actor), visibility,
                request.body().trim(), clock.instant());
        comments.save(comment);
        Viewer viewer = Viewer.of(actor);
        return view(comment, viewer, viewer.isStaff() ? directory.displayNames(Set.of(user.id())) : Map.of());
    }

    @Transactional
    @PreAuthorize("hasRole('MODERATOR')")
    public CommentView moderate(UUID reportId, UUID commentId, CommentModeration request, CurrentUser user) {
        Actor actor = actors.of(user);
        if (!actor.hasRole(Role.MODERATOR)) {
            throw new ApiException(SecurityErrorCode.ACCESS_DENIED);
        }
        Comment comment = comments.findByIdAndReportId(commentId, reportId)
                .orElseThrow(() -> new ApiException(ReportErrorCode.COMMENT_NOT_FOUND));
        Instant now = clock.instant();
        String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().strip();
        boolean changed = request.hidden() ? comment.hide(user.id(), reason, now) : comment.unhide();
        if (changed) {
            events.publishEvent(new CommentModerated(reportId, commentId, request.hidden(), reason, user.id(), now));
        }
        return view(comment, Viewer.of(actor), directory.displayNames(Set.of(comment.authorId())));
    }

    private Report load(UUID id) {
        return reports.findById(id).orElseThrow(() -> new ApiException(ReportErrorCode.REPORT_NOT_FOUND));
    }

    private static CommentView view(Comment comment, Viewer viewer, Map<UUID, String> names) {
        boolean staff = viewer.isStaff();
        boolean showBody = !comment.isHidden() || staff;
        return new CommentView(
                comment.id(),
                comment.authorKind(),
                staff ? names.get(comment.authorId()) : null,
                viewer.is(comment.authorId()),
                comment.visibility(),
                showBody ? comment.body() : null,
                comment.isHidden(),
                staff ? comment.hiddenReason() : null,
                comment.createdAt());
    }
}
