package com.nisatsama.shelfsense.common;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.http.ResponseEntity;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({BadCredentialsException.class, DisabledException.class})
    public ResponseEntity<ApiError> handleAuthenticationFailure(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError(
                HttpStatus.UNAUTHORIZED.value(), "UNAUTHORIZED", "Invalid login identifier or password."
        ));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleOptimisticLockingFailure(
            OptimisticLockingFailureException ex
    ) {
        return new ApiError(
                HttpStatus.CONFLICT.value(),
                "CONFLICT",
                "The resource was modified by another request. Please retry."
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleIllegalArgumentException(
            IllegalArgumentException ex
    ) {
        return new ApiError(
                400,
                "BAD_REQUEST",
                ex.getMessage()
        );
    }
}
