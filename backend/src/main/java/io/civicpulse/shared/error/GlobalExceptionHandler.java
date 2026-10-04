package io.civicpulse.shared.error;

import io.civicpulse.shared.security.SecurityErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final AuthenticationTrustResolver TRUST_RESOLVER = new AuthenticationTrustResolverImpl();

    private final ApiErrorResponses responses;

    public GlobalExceptionHandler(ApiErrorResponses responses) {
        this.responses = responses;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        return responses.of(ex.errorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ApiError> handleRateLimited(RateLimitExceededException ex, HttpServletRequest request) {
        ResponseEntity<ApiError> response = responses.of(ex.errorCode(), ex.getMessage(), request);
        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()))
                .body(response.getBody());
    }

    // @PreAuthorize: 401 if not signed in, 403 otherwise
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        if (TRUST_RESOLVER.isAuthenticated(SecurityContextHolder.getContext().getAuthentication())) {
            return responses.of(SecurityErrorCode.ACCESS_DENIED, request);
        }
        ResponseEntity<ApiError> response = responses.of(SecurityErrorCode.AUTHENTICATION_REQUIRED, request);
        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(response.getBody());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleInvalidBody(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getAllErrors().stream()
                .map(error -> new ApiError.FieldViolation(
                        error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName(),
                        constraintName(error),
                        error.getDefaultMessage()))
                .sorted(Comparator.comparing(ApiError.FieldViolation::field))
                .toList();
        return validationFailed(violations, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> handleInvalidParameters(HandlerMethodValidationException ex, HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ApiError.FieldViolation(
                                result.getMethodParameter().getParameterName(),
                                constraintName(error),
                                error.getDefaultMessage())))
                .toList();
        return validationFailed(violations, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(violation -> new ApiError.FieldViolation(
                        lastNode(violation.getPropertyPath().toString()),
                        violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
                        violation.getMessage()))
                .sorted(Comparator.comparing(ApiError.FieldViolation::field))
                .toList();
        return validationFailed(violations, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return validationFailed(
                List.of(new ApiError.FieldViolation(ex.getParameterName(), "Required", "This parameter is required")),
                request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> handleUnreadable(Exception ex, HttpServletRequest request) {
        return responses.of(CommonErrorCode.MALFORMED_REQUEST, request);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ResponseEntity<ApiError> handleNotFound(Exception ex, HttpServletRequest request) {
        return responses.of(CommonErrorCode.RESOURCE_NOT_FOUND, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        ResponseEntity<ApiError> response = responses.of(CommonErrorCode.METHOD_NOT_ALLOWED, request);
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(response.getHeaders());
        if (ex.getSupportedHttpMethods() != null) {
            headers.setAllow(ex.getSupportedHttpMethods());
        }
        return new ResponseEntity<>(response.getBody(), headers, response.getStatusCode());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return responses.of(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE, request);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ResponseEntity<ApiError> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex, HttpServletRequest request) {
        return responses.of(CommonErrorCode.NOT_ACCEPTABLE, request);
    }

    @ExceptionHandler(ErrorResponseException.class)
    ResponseEntity<ApiError> handleErrorResponse(ErrorResponseException ex, HttpServletRequest request) {
        int status = ex.getStatusCode().value();
        if (status >= 500) {
            log.error("Request failed with status {}", status, ex);
        }
        ErrorCode code = CommonErrorCode.forStatus(status);
        return responses.of(code, ex.getStatusCode(), code.defaultMessage(), request, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), ex);
        return responses.of(CommonErrorCode.INTERNAL_ERROR, request);
    }

    private ResponseEntity<ApiError> validationFailed(List<ApiError.FieldViolation> violations, HttpServletRequest request) {
        CommonErrorCode code = CommonErrorCode.VALIDATION_FAILED;
        return responses.of(code, HttpStatus.BAD_REQUEST, code.defaultMessage(), request, violations);
    }

    private static String constraintName(MessageSourceResolvable error) {
        String[] codes = error.getCodes();
        if (codes == null || codes.length == 0) {
            return "Invalid";
        }
        return codes[codes.length - 1];
    }

    private static String lastNode(String propertyPath) {
        if (!StringUtils.hasText(propertyPath)) {
            return "";
        }
        int dot = propertyPath.lastIndexOf('.');
        return dot < 0 ? propertyPath : propertyPath.substring(dot + 1);
    }
}
