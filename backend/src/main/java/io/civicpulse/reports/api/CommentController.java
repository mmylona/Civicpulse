package io.civicpulse.reports.api;

import io.civicpulse.reports.application.CommentModeration;
import io.civicpulse.reports.application.CommentService;
import io.civicpulse.reports.application.CommentView;
import io.civicpulse.reports.application.NewComment;
import io.civicpulse.shared.security.CurrentUser;
import io.civicpulse.shared.web.RateLimiter;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports/{id}/comments")
class CommentController {

    private static final int COMMENTS_PER_HOUR = 60;

    private final CommentService comments;
    private final RateLimiter rateLimiter;

    CommentController(CommentService comments, RateLimiter rateLimiter) {
        this.comments = comments;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping
    List<CommentView> list(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return comments.list(id, ReportController.userOrNull(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CommentView add(@PathVariable UUID id, @Valid @RequestBody NewComment body, @AuthenticationPrincipal Jwt jwt) {
        CurrentUser user = CurrentUser.from(jwt);
        rateLimiter.check("comment", user.id().toString(), COMMENTS_PER_HOUR, Duration.ofHours(1));
        return comments.add(id, body, user);
    }

    @PatchMapping("/{commentId}")
    CommentView moderate(@PathVariable UUID id, @PathVariable UUID commentId,
            @Valid @RequestBody CommentModeration body, @AuthenticationPrincipal Jwt jwt) {
        return comments.moderate(id, commentId, body, CurrentUser.from(jwt));
    }
}
