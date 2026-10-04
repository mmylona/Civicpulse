package io.civicpulse;

import io.civicpulse.support.TestcontainersConfiguration;
import org.springframework.boot.SpringApplication;

public class TestCivicPulseApplication {

    public static void main(String[] args) {
        SpringApplication.from(CivicPulseApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
