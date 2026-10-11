package io.civicpulse.reports.application;

import io.civicpulse.identity.UserDirectory;
import io.civicpulse.identity.UserSummary;
import io.civicpulse.reports.domain.Actor;
import io.civicpulse.shared.error.ApiException;
import io.civicpulse.shared.security.CurrentUser;
import io.civicpulse.shared.security.SecurityErrorCode;
import org.springframework.stereotype.Component;

// roles and team are read from the database, so a change applies before the token expires
// (reads too, so a removed moderator stops seeing internal notes at once)
@Component
class Actors {

    private final UserDirectory directory;

    Actors(UserDirectory directory) {
        this.directory = directory;
    }

    Actor of(CurrentUser user) {
        UserSummary summary = directory.findUser(user.id())
                .filter(UserSummary::enabled)
                .orElseThrow(() -> new ApiException(SecurityErrorCode.ACCESS_DENIED));
        return new Actor(summary.id(), summary.roles(), summary.teamId());
    }

    // null user, unknown or disabled account: anonymous
    Viewer viewer(CurrentUser user) {
        if (user == null) {
            return Viewer.anonymous();
        }
        return directory.findUser(user.id())
                .filter(UserSummary::enabled)
                .map(summary -> new Viewer(summary.id(), summary.roles()))
                .orElse(Viewer.anonymous());
    }
}
