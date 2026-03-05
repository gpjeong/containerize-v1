package com.containerize.util;

import com.containerize.exception.InvalidFileException;
import org.springframework.stereotype.Component;

/**
 * Utility component for file security validation.
 * Provides methods for validating file extensions, content types, magic numbers, and file sizes.
 */
@Component
public class SecurityUtil {

    private static final String JAR_EXTENSION = ".jar";
    private static final String WAR_EXTENSION = ".war";
    private static final String JAR_CONTENT_TYPE = "application/java-archive";
    private static final String JAR_ALT_CONTENT_TYPE = "application/x-java-archive";
    private static final String ZIP_CONTENT_TYPE = "application/zip";
    private static final byte[] ZIP_MAGIC_NUMBER = {0x50, 0x4B, 0x03, 0x04}; // PK\x03\x04

    /**
     * Validates the file extension.
     * Only .jar and .war files are allowed.
     *
     * @param filename the filename to validate
     * @throws InvalidFileException if the file extension is not allowed
     */
    public void validateExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            throw new InvalidFileException("Filename cannot be null or empty");
        }

        String lowerFilename = filename.toLowerCase();
        if (!lowerFilename.endsWith(JAR_EXTENSION) && !lowerFilename.endsWith(WAR_EXTENSION)) {
            throw new InvalidFileException("Invalid file extension. Only .jar and .war files are allowed");
        }
    }

    /**
     * Validates the content type of the file.
     * Checks for valid Java archive content types.
     *
     * @param contentType the content type to validate
     * @throws InvalidFileException if the content type is not valid
     */
    public void validateContentType(String contentType) {
        if (contentType == null || contentType.isEmpty()) {
            throw new InvalidFileException("Content-Type header is missing or empty");
        }

        String normalizedContentType = contentType.toLowerCase().split(";")[0].trim();
        if (!normalizedContentType.equals(JAR_CONTENT_TYPE) &&
            !normalizedContentType.equals(JAR_ALT_CONTENT_TYPE) &&
            !normalizedContentType.equals(ZIP_CONTENT_TYPE)) {
            throw new InvalidFileException("Invalid Content-Type. Expected application/java-archive or application/zip, got: " + contentType);
        }
    }

    /**
     * Validates the magic number of the file.
     * Checks for ZIP magic number (PK\x03\x04) which is used by JAR and WAR files.
     *
     * @param header the file header bytes to validate
     * @throws InvalidFileException if the magic number is not valid
     */
    public void validateMagicNumber(byte[] header) {
        if (header == null || header.length < ZIP_MAGIC_NUMBER.length) {
            throw new InvalidFileException("File header is too short or missing");
        }

        for (int i = 0; i < ZIP_MAGIC_NUMBER.length; i++) {
            if (header[i] != ZIP_MAGIC_NUMBER[i]) {
                throw new InvalidFileException("Invalid file format. File does not appear to be a valid Java archive (JAR/WAR)");
            }
        }
    }

    /**
     * Validates the file size against a maximum limit.
     *
     * @param size the file size in bytes
     * @param maxSize the maximum allowed size in bytes
     * @throws InvalidFileException if the file size exceeds the maximum
     */
    public void validateFileSize(long size, long maxSize) {
        if (size <= 0) {
            throw new InvalidFileException("File size must be greater than zero");
        }

        if (size > maxSize) {
            throw new InvalidFileException("File size exceeds maximum allowed size of " + formatBytes(maxSize) +
                    ". Uploaded file size: " + formatBytes(size));
        }
    }

    /**
     * Sanitizes the filename by removing unsafe characters.
     * This prevents directory traversal and other path-based attacks.
     *
     * @param filename the filename to sanitize
     * @return the sanitized filename
     * @throws InvalidFileException if the filename is invalid after sanitization
     */
    public String sanitizeFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            throw new InvalidFileException("Filename cannot be null or empty");
        }

        // Remove any path separators and special characters that could be used for directory traversal
        String sanitized = filename.replaceAll("[^a-zA-Z0-9._-]", "");

        // Remove leading/trailing dots and spaces
        sanitized = sanitized.replaceAll("^\\.+|\\.+$", "").trim();

        if (sanitized.isEmpty()) {
            throw new InvalidFileException("Filename contains only invalid characters");
        }

        // Prevent null byte injection
        if (sanitized.contains("\0")) {
            throw new InvalidFileException("Filename contains null bytes");
        }

        // Prevent common path traversal attempts
        if (sanitized.contains("..") || sanitized.contains("/") || sanitized.contains("\\")) {
            throw new InvalidFileException("Filename contains invalid path characters");
        }

        return sanitized;
    }

    /**
     * Formats bytes into a human-readable string.
     *
     * @param bytes the number of bytes
     * @return a formatted string (e.g., "500MB", "1GB")
     */
    private String formatBytes(long bytes) {
        if (bytes <= 0) return "0B";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.1f%s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}
