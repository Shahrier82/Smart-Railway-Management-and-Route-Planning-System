package com.railway.exception;

/**
 * Thrown when reading, importing, or exporting railway network or booking
 * data fails. Wraps the underlying cause (typically {@code SQLException}
 * or {@code IOException}) with a message meaningful to the UI layer,
 * rather than letting low-level persistence exceptions leak upward.
 */
public class RailwayDataException extends Exception {

    public RailwayDataException(String message) {
        super(message);
    }

    public RailwayDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
