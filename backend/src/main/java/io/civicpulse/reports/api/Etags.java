package io.civicpulse.reports.api;

import io.civicpulse.shared.error.ApiException;
import io.civicpulse.shared.error.CommonErrorCode;
import org.springframework.http.ETag;

// the ETag of a report is its version, e.g. "3"
final class Etags {

    private Etags() {
    }

    static String of(long version) {
        return "\"" + version + "\"";
    }

    static long expectedVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new ApiException(CommonErrorCode.PRECONDITION_REQUIRED);
        }
        for (ETag etag : ETag.parse(ifMatch)) {
            if (!etag.weak() && !etag.isWildcard()) {
                try {
                    return Long.parseLong(etag.tag());
                } catch (NumberFormatException ignored) {
                    // not one of ours
                }
            }
        }
        throw new ApiException(CommonErrorCode.VERSION_CONFLICT);
    }
}
