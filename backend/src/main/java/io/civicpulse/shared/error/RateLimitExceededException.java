package io.civicpulse.shared.error;

import java.time.Duration;

public class RateLimitExceededException extends ApiException {

    private final transient Duration retryAfter;

    public RateLimitExceededException(Duration retryAfter) {
        super(CommonErrorCode.RATE_LIMITED);
        this.retryAfter = retryAfter;
    }

    public long retryAfterSeconds() {
        long seconds = retryAfter.toSeconds();
        return retryAfter.toNanosPart() > 0 || seconds == 0 ? seconds + 1 : seconds;
    }
}
