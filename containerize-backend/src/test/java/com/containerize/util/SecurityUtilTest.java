package com.containerize.util;

import com.containerize.exception.InvalidFileException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecurityUtilTest {

    private SecurityUtil securityUtil;

    @BeforeEach
    void setUp() {
        securityUtil = new SecurityUtil();
    }

    @Nested
    @DisplayName("validateExtension")
    class ValidateExtension {

        @Test
        @DisplayName("should accept .jar files")
        void shouldAcceptJarFiles() {
            assertDoesNotThrow(() -> securityUtil.validateExtension("app.jar"));
        }

        @Test
        @DisplayName("should accept .war files")
        void shouldAcceptWarFiles() {
            assertDoesNotThrow(() -> securityUtil.validateExtension("app.war"));
        }

        @Test
        @DisplayName("should accept uppercase extensions")
        void shouldAcceptUppercaseExtensions() {
            assertDoesNotThrow(() -> securityUtil.validateExtension("app.JAR"));
            assertDoesNotThrow(() -> securityUtil.validateExtension("app.WAR"));
        }

        @Test
        @DisplayName("should reject invalid extensions")
        void shouldRejectInvalidExtensions() {
            assertThrows(InvalidFileException.class, () -> securityUtil.validateExtension("app.zip"));
            assertThrows(InvalidFileException.class, () -> securityUtil.validateExtension("app.exe"));
            assertThrows(InvalidFileException.class, () -> securityUtil.validateExtension("app.txt"));
        }

        @Test
        @DisplayName("should reject null or empty filename")
        void shouldRejectNullOrEmpty() {
            assertThrows(InvalidFileException.class, () -> securityUtil.validateExtension(null));
            assertThrows(InvalidFileException.class, () -> securityUtil.validateExtension(""));
        }
    }

    @Nested
    @DisplayName("validateContentType")
    class ValidateContentType {

        @Test
        @DisplayName("should accept valid content types")
        void shouldAcceptValidContentTypes() {
            assertDoesNotThrow(() -> securityUtil.validateContentType("application/java-archive"));
            assertDoesNotThrow(() -> securityUtil.validateContentType("application/x-java-archive"));
            assertDoesNotThrow(() -> securityUtil.validateContentType("application/zip"));
        }

        @Test
        @DisplayName("should accept content type with charset")
        void shouldAcceptContentTypeWithCharset() {
            assertDoesNotThrow(() -> securityUtil.validateContentType("application/java-archive; charset=utf-8"));
        }

        @Test
        @DisplayName("should reject invalid content types")
        void shouldRejectInvalidContentTypes() {
            assertThrows(InvalidFileException.class, () -> securityUtil.validateContentType("text/plain"));
            assertThrows(InvalidFileException.class, () -> securityUtil.validateContentType("application/pdf"));
        }

        @Test
        @DisplayName("should reject null or empty content type")
        void shouldRejectNullOrEmpty() {
            assertThrows(InvalidFileException.class, () -> securityUtil.validateContentType(null));
            assertThrows(InvalidFileException.class, () -> securityUtil.validateContentType(""));
        }
    }

    @Nested
    @DisplayName("validateMagicNumber")
    class ValidateMagicNumber {

        @Test
        @DisplayName("should accept valid ZIP magic number")
        void shouldAcceptValidMagicNumber() {
            byte[] header = {0x50, 0x4B, 0x03, 0x04, 0x00, 0x00};
            assertDoesNotThrow(() -> securityUtil.validateMagicNumber(header));
        }

        @Test
        @DisplayName("should reject invalid magic number")
        void shouldRejectInvalidMagicNumber() {
            byte[] header = {0x00, 0x00, 0x00, 0x00};
            assertThrows(InvalidFileException.class, () -> securityUtil.validateMagicNumber(header));
        }

        @Test
        @DisplayName("should reject short header")
        void shouldRejectShortHeader() {
            byte[] header = {0x50, 0x4B};
            assertThrows(InvalidFileException.class, () -> securityUtil.validateMagicNumber(header));
        }

        @Test
        @DisplayName("should reject null header")
        void shouldRejectNullHeader() {
            assertThrows(InvalidFileException.class, () -> securityUtil.validateMagicNumber(null));
        }
    }

    @Nested
    @DisplayName("validateFileSize")
    class ValidateFileSize {

        @Test
        @DisplayName("should accept valid file size")
        void shouldAcceptValidFileSize() {
            assertDoesNotThrow(() -> securityUtil.validateFileSize(1024, 536870912L));
        }

        @Test
        @DisplayName("should reject zero file size")
        void shouldRejectZeroSize() {
            assertThrows(InvalidFileException.class, () -> securityUtil.validateFileSize(0, 536870912L));
        }

        @Test
        @DisplayName("should reject file exceeding max size")
        void shouldRejectExceedingSize() {
            assertThrows(InvalidFileException.class, () -> securityUtil.validateFileSize(600_000_000L, 536870912L));
        }
    }

    @Nested
    @DisplayName("sanitizeFilename")
    class SanitizeFilename {

        @Test
        @DisplayName("should keep valid filenames unchanged")
        void shouldKeepValidFilenames() {
            assertEquals("app.jar", securityUtil.sanitizeFilename("app.jar"));
            assertEquals("my-app-1.0.war", securityUtil.sanitizeFilename("my-app-1.0.war"));
        }

        @Test
        @DisplayName("should strip path traversal characters")
        void shouldStripPathTraversal() {
            String sanitized = securityUtil.sanitizeFilename("../../etc/passwd.jar");
            assertFalse(sanitized.contains(".."));
            assertFalse(sanitized.contains("/"));
        }

        @Test
        @DisplayName("should strip special characters")
        void shouldStripSpecialCharacters() {
            String sanitized = securityUtil.sanitizeFilename("app @#$.jar");
            assertFalse(sanitized.contains("@"));
            assertFalse(sanitized.contains("#"));
            assertFalse(sanitized.contains("$"));
            assertFalse(sanitized.contains(" "));
        }

        @Test
        @DisplayName("should reject null or empty filename")
        void shouldRejectNullOrEmpty() {
            assertThrows(InvalidFileException.class, () -> securityUtil.sanitizeFilename(null));
            assertThrows(InvalidFileException.class, () -> securityUtil.sanitizeFilename(""));
        }

        @Test
        @DisplayName("should reject filename with only invalid characters")
        void shouldRejectOnlyInvalidChars() {
            assertThrows(InvalidFileException.class, () -> securityUtil.sanitizeFilename("@#$%^&"));
        }
    }
}
