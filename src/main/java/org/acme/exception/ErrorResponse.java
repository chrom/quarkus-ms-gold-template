package org.acme.exception;

import java.time.LocalDateTime;

/**
 * DTO (Data Transfer Object) for standardizing error messages.
 */
public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        LocalDateTime timestamp
) {}
