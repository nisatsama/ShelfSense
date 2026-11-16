package com.nisatsama.shelfsense.common;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
