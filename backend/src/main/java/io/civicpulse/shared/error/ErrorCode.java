package io.civicpulse.shared.error;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

    String code();

    HttpStatus status();

    String defaultMessage();
}
