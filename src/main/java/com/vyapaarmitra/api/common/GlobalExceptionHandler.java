package com.vyapaarmitra.api.common;

import com.vyapaarmitra.api.subscription.PlanLimitException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(errorBody(ex.getCode(), ex.getMessage(), null));
    }

    /** Plan limit: 402 with a {@code reason} the web maps onto its paywall copy. */
    @ExceptionHandler(PlanLimitException.class)
    ResponseEntity<Map<String, Object>> handlePlanLimit(PlanLimitException ex) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", "PLAN_LIMIT");
        error.put("reason", ex.getReason());
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of("error", error));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(errorBody("VALIDATION_ERROR", "Invalid request", fields));
    }

    /** Constraint violations on @RequestParam / @PathVariable method parameters. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<Map<String, Object>> handleParamValidation(HandlerMethodValidationException ex) {
        Map<String, String> params = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result ->
            result.getResolvableErrors().forEach(error ->
                params.putIfAbsent(result.getMethodParameter().getParameterName(),
                    error.getDefaultMessage())));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(errorBody("VALIDATION_ERROR", "Invalid request parameters", params));
    }

    /** Malformed JSON body or wrong field types (e.g. text where a number is expected). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(errorBody("MALFORMED_REQUEST", "Request body is missing or malformed", null));
    }

    /** Bad path/query parameter types (e.g. an invalid UUID). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(errorBody("INVALID_PARAMETER",
                "Invalid value for parameter '" + ex.getName() + "'", null));
    }

    /** A required query/form parameter was omitted — a client error, not a 500. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(errorBody("MISSING_PARAMETER",
                "Missing required parameter '" + ex.getParameterName() + "'", null));
    }

    /** Same idea for a missing multipart part (e.g. no {@code file} on an upload). */
    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<Map<String, Object>> handleMissingPart(MissingServletRequestPartException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(errorBody("MISSING_PARAMETER",
                "Missing required part '" + ex.getRequestPartName() + "'", null));
    }

    /**
     * The multipart resolver rejects an over-limit upload (spring.servlet.multipart
     * max-file/request-size) before the controller runs, so this — not the in-controller
     * check — is what a too-big image actually hits. Map it to a clean 413.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, Object>> handleTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(errorBody("FILE_TOO_LARGE", "Image is too large. Please use one under 10 MB.", null));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    ResponseEntity<Map<String, Object>> handleDenied(AuthorizationDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(errorBody("FORBIDDEN", "You do not have permission for this action", null));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("Unhandled error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(errorBody("INTERNAL_ERROR", "Something went wrong. Please try again.", null));
    }

    private Map<String, Object> errorBody(String code, String message, Object details) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        if (details != null) {
            error.put("details", details);
        }
        return Map.of("error", error);
    }
}
