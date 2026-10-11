package io.civicpulse.reports.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReportMapServiceTest {

    @Test
    void cellIsAQuarterTile() {
        assertThat(ReportMapService.cellSize(0)).isEqualTo(90.0);
        assertThat(ReportMapService.cellSize(12)).isEqualTo(360.0 / 4096 / 4);
    }
}
