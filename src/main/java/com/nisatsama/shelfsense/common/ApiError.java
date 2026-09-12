package com.nisatsama.shelfsense.common;

public record ApiError(
        int status,
        String error,
        String message
) {
}
