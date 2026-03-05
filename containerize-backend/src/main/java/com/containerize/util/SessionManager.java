package com.containerize.util;

import com.containerize.config.AppConfig;
import com.containerize.exception.InvalidFileException;
import com.containerize.exception.ServiceException;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Component for managing upload sessions.
 * Handles session creation, file storage, cleanup scheduling, and session lifecycle.
 */
@Component
public class SessionManager {

    private static final int CHUNK_SIZE = 1024 * 1024; // 1MB chunks
    private static final long SESSION_TIMEOUT_MINUTES = 60;
    private static final String DOCKERFILE_FILENAME = "Dockerfile";

    private final AppConfig appConfig;
    private final SecurityUtil securityUtil;
    private final ScheduledExecutorService scheduledExecutor;

    /**
     * Constructor for SessionManager.
     *
     * @param appConfig    the application configuration
     * @param securityUtil the security utility for file validation
     */
    public SessionManager(AppConfig appConfig, SecurityUtil securityUtil) {
        this.appConfig = appConfig;
        this.securityUtil = securityUtil;
        this.scheduledExecutor = Executors.newScheduledThreadPool(1);
        ensureUploadDirectoryExists();
    }

    /**
     * Creates a new session with a unique UUID.
     *
     * @return the session ID
     */
    public String createSession() {
        return UUID.randomUUID().toString();
    }

    /**
     * Gets the directory path for a session.
     *
     * @param sessionId the session ID
     * @return the path to the session directory
     */
    public Path getSessionDir(String sessionId) {
        Path base = Paths.get(appConfig.getUploadDir()).toAbsolutePath().normalize();
        Path resolved = base.resolve(sessionId).normalize();
        if (!resolved.startsWith(base)) {
            throw new InvalidFileException("Invalid session ID");
        }
        return resolved;
    }

    /**
     * Checks if a session directory exists.
     *
     * @param sessionId the session ID
     * @return true if the session directory exists, false otherwise
     */
    public boolean sessionExists(String sessionId) {
        Path sessionDir = getSessionDir(sessionId);
        return Files.exists(sessionDir) && Files.isDirectory(sessionDir);
    }

    /**
     * Saves an uploaded file to the session directory with streaming (1MB chunks).
     *
     * @param sessionId the session ID
     * @param file the uploaded file
     * @return the path to the saved file
     * @throws ServiceException if an error occurs while saving the file
     */
    public Path saveUploadedFile(String sessionId, MultipartFile file) {
        try {
            // Create session directory if it doesn't exist
            Path sessionDir = getSessionDir(sessionId);
            Files.createDirectories(sessionDir);

            // Sanitize filename and validate path (C-2: prevent path traversal)
            String sanitizedFilename = securityUtil.sanitizeFilename(file.getOriginalFilename());
            Path filePath = sessionDir.resolve(sanitizedFilename).normalize();
            if (!filePath.startsWith(sessionDir)) {
                throw new InvalidFileException("Invalid file path");
            }
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, filePath);
            }

            return filePath;
        } catch (IOException e) {
            throw new ServiceException("Failed to save uploaded file: " + e.getMessage(), e);
        }
    }

    /**
     * Saves the generated Dockerfile to the session directory.
     *
     * @param sessionId the session ID
     * @param content the Dockerfile content
     * @return the path to the saved Dockerfile
     * @throws ServiceException if an error occurs while saving the file
     */
    public Path saveDockerfile(String sessionId, String content) {
        try {
            // Create session directory if it doesn't exist
            Path sessionDir = getSessionDir(sessionId);
            Files.createDirectories(sessionDir);

            // Save Dockerfile
            Path dockerfilePath = sessionDir.resolve(DOCKERFILE_FILENAME);
            Files.write(dockerfilePath, content.getBytes());

            return dockerfilePath;
        } catch (IOException e) {
            throw new ServiceException("Failed to save Dockerfile: " + e.getMessage(), e);
        }
    }

    /**
     * Schedules automatic cleanup of a session after a delay.
     *
     * @param sessionId the session ID to cleanup
     */
    public void scheduleCleanup(String sessionId) {
        scheduledExecutor.schedule(
                () -> cleanupSession(sessionId),
                SESSION_TIMEOUT_MINUTES,
                TimeUnit.MINUTES
        );
    }

    /**
     * Cleans up a session by deleting its directory and all contents.
     *
     * @param sessionId the session ID to cleanup
     * @throws ServiceException if an error occurs while deleting the directory
     */
    public void cleanupSession(String sessionId) {
        try {
            Path sessionDir = getSessionDir(sessionId);
            if (Files.exists(sessionDir)) {
                deleteDirectory(sessionDir);
            }
        } catch (IOException e) {
            throw new ServiceException("Failed to cleanup session " + sessionId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Ensures the upload directory exists, creating it if necessary.
     *
     * @throws ServiceException if the upload directory cannot be created
     */
    private void ensureUploadDirectoryExists() {
        try {
            Path uploadDir = Paths.get(appConfig.getUploadDir());
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new ServiceException("Failed to create upload directory: " + e.getMessage(), e);
        }
    }

    /**
     * Recursively deletes a directory and all its contents.
     *
     * @param path the path to the directory to delete
     * @throws IOException if an error occurs while deleting
     */
    private void deleteDirectory(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            Files.list(path).forEach(childPath -> {
                try {
                    deleteDirectory(childPath);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        Files.delete(path);
    }

    /**
     * Shuts down the scheduled executor service.
     * Called automatically during application shutdown via @PreDestroy.
     */
    @PreDestroy
    public void shutdown() {
        if (!scheduledExecutor.isShutdown()) {
            scheduledExecutor.shutdown();
            try {
                if (!scheduledExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    scheduledExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduledExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
