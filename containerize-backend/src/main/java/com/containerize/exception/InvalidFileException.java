package com.containerize.exception;

/**
 * Exception thrown when file validation fails.
 * This includes checks for file extension, content type, magic numbers, and file size.
 */
public class InvalidFileException extends RuntimeException {

    public InvalidFileException(String message) {
        super(message);
    }

    public InvalidFileException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidFileException(Throwable cause) {
        super(cause);
    }
}
