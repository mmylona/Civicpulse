package io.civicpulse.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdsTest {

    @Test
    void versionAndVariant() {
        UUID id = Ids.newId();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void sortedByTime() {
        UUID earlier = Ids.newId(1_759_500_000_000L);
        UUID later = Ids.newId(1_759_500_000_001L);

        assertThat(earlier.toString()).isLessThan(later.toString());
    }

    @Test
    void timestampInFirst48Bits() {
        long millis = 1_759_500_123_456L;

        assertThat(Ids.newId(millis).getMostSignificantBits() >>> 16).isEqualTo(millis);
    }
}
