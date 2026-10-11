package io.civicpulse.reports.api;

import static org.springframework.http.HttpHeaders.IF_MATCH;

import io.civicpulse.reports.application.ReportCommand;
import io.civicpulse.reports.application.ReportCommandService;
import io.civicpulse.reports.application.ReportDetails;
import io.civicpulse.shared.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports/{id}")
class ReportCommandController {

    private final ReportCommandService commands;

    ReportCommandController(ReportCommandService commands) {
        this.commands = commands;
    }

    @PostMapping("/triage")
    ResponseEntity<ReportDetails> triage(@PathVariable UUID id,
            @RequestHeader(name = IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ReportCommand.Triage body, @AuthenticationPrincipal Jwt jwt) {
        return perform(id, ifMatch, body, jwt);
    }

    @PostMapping("/reject")
    ResponseEntity<ReportDetails> reject(@PathVariable UUID id,
            @RequestHeader(name = IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ReportCommand.Reject body, @AuthenticationPrincipal Jwt jwt) {
        return perform(id, ifMatch, body, jwt);
    }

    @PostMapping("/mark-duplicate")
    ResponseEntity<ReportDetails> markDuplicate(@PathVariable UUID id,
            @RequestHeader(name = IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ReportCommand.MarkDuplicate body, @AuthenticationPrincipal Jwt jwt) {
        return perform(id, ifMatch, body, jwt);
    }

    @PostMapping("/request-information")
    ResponseEntity<ReportDetails> requestInformation(@PathVariable UUID id,
            @RequestHeader(name = IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ReportCommand.RequestInformation body, @AuthenticationPrincipal Jwt jwt) {
        return perform(id, ifMatch, body, jwt);
    }

    @PostMapping("/provide-information")
    ResponseEntity<ReportDetails> provideInformation(@PathVariable UUID id,
            @RequestHeader(name = IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ReportCommand.ProvideInformation body, @AuthenticationPrincipal Jwt jwt) {
        return perform(id, ifMatch, body, jwt);
    }

    @PostMapping("/resume")
    ResponseEntity<ReportDetails> resume(@PathVariable UUID id,
            @RequestHeader(name = IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ReportCommand.Resume body, @AuthenticationPrincipal Jwt jwt) {
        return perform(id, ifMatch, body, jwt);
    }

    @PostMapping("/assign")
    ResponseEntity<ReportDetails> assign(@PathVariable UUID id,
            @RequestHeader(name = IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ReportCommand.Assign body, @AuthenticationPrincipal Jwt jwt) {
        return perform(id, ifMatch, body, jwt);
    }

    @PostMapping("/unassign")
    ResponseEntity<ReportDetails> unassign(@PathVariable UUID id,
            @RequestHeader(name = IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ReportCommand.Unassign body, @AuthenticationPrincipal Jwt jwt) {
        return perform(id, ifMatch, body, jwt);
    }

    @PostMapping("/start")
    ResponseEntity<ReportDetails> start(@PathVariable UUID id,
            @RequestHeader(name = IF_MATCH, required = false) String ifMatch,
            @AuthenticationPrincipal Jwt jwt) {
        return perform(id, ifMatch, new ReportCommand.Start(), jwt);
    }

    @PostMapping("/hand-back")
    ResponseEntity<ReportDetails> handBack(@PathVariable UUID id,
            @RequestHeader(name = IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ReportCommand.HandBack body, @AuthenticationPrincipal Jwt jwt) {
        return perform(id, ifMatch, body, jwt);
    }

    @PostMapping("/resolve")
    ResponseEntity<ReportDetails> resolve(@PathVariable UUID id,
            @RequestHeader(name = IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ReportCommand.Resolve body, @AuthenticationPrincipal Jwt jwt) {
        return perform(id, ifMatch, body, jwt);
    }

    @PostMapping("/reopen")
    ResponseEntity<ReportDetails> reopen(@PathVariable UUID id,
            @RequestHeader(name = IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ReportCommand.Reopen body, @AuthenticationPrincipal Jwt jwt) {
        return perform(id, ifMatch, body, jwt);
    }

    @PostMapping("/close")
    ResponseEntity<ReportDetails> close(@PathVariable UUID id,
            @RequestHeader(name = IF_MATCH, required = false) String ifMatch,
            @AuthenticationPrincipal Jwt jwt) {
        return perform(id, ifMatch, new ReportCommand.Close(), jwt);
    }

    @PostMapping("/restore")
    ResponseEntity<ReportDetails> restore(@PathVariable UUID id,
            @RequestHeader(name = IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ReportCommand.Restore body, @AuthenticationPrincipal Jwt jwt) {
        return perform(id, ifMatch, body, jwt);
    }

    private ResponseEntity<ReportDetails> perform(UUID id, String ifMatch, ReportCommand command, Jwt jwt) {
        long version = Etags.expectedVersion(ifMatch);
        ReportDetails report = commands.perform(id, version, command, CurrentUser.from(jwt));
        return ResponseEntity.ok().eTag(Etags.of(report.version())).body(report);
    }
}
