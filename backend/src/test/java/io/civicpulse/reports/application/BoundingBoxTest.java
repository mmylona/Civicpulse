package io.civicpulse.reports.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.civicpulse.reports.domain.ReportErrorCode;
import io.civicpulse.shared.error.ApiException;
import org.junit.jupiter.api.Test;

class BoundingBoxTest {

    @Test
    void parsesWestSouthEastNorth() {
        BoundingBox box = BoundingBox.parse("7.43, 46.94,7.46,46.955");

        assertThat(box).isEqualTo(new BoundingBox(7.43, 46.94, 7.46, 46.955));
    }

    @Test
    void rejectsBadInput() {
        for (String value : new String[] {"7.43,46.94,7.46", "a,b,c,d", "7.46,46.94,7.43,46.955",
                "7.43,46.955,7.46,46.94", "-200,0,10,10", "0,0,10,95", "NaN,0,1,1"}) {
            assertThatThrownBy(() -> BoundingBox.parse(value))
                    .as(value)
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.errorCode()).isEqualTo(ReportErrorCode.INVALID_BOUNDING_BOX));
        }
    }

    @Test
    void sizeDependsOnZoom() {
        BoundingBox city = BoundingBox.parse("7.28,46.92,7.50,46.99");

        city.checkSize(12);
        assertThatThrownBy(() -> city.checkSize(17)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> BoundingBox.parse("-20,-20,20,20").checkSize(0)).isInstanceOf(ApiException.class);
    }
}
