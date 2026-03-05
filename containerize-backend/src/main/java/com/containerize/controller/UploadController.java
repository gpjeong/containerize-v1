package com.containerize.controller;

import com.containerize.dto.request.ProjectInfo;
import com.containerize.dto.response.UploadResponse;
import com.containerize.service.FileAnalyzerService;
import com.containerize.util.SecurityUtil;
import com.containerize.util.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class UploadController {

    private static final Logger logger = LoggerFactory.getLogger(UploadController.class);

    @Autowired
    private SecurityUtil securityUtil;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private FileAnalyzerService fileAnalyzerService;

    @Autowired
    private com.containerize.config.AppConfig appConfig;

    /**
     * Upload JAR/WAR file for analysis
     *
     * - Validates file type and size
     * - Analyzes JAR structure
     * - Returns project info and session ID
     */
    @PostMapping("/upload/java")
    public ResponseEntity<UploadResponse> uploadJavaArtifact(
            @RequestParam("file") MultipartFile file
    ) {
        try {
            // Validate upload (extension, content-type, magic number, size)
            securityUtil.validateExtension(file.getOriginalFilename());
            securityUtil.validateContentType(file.getContentType());
            securityUtil.validateMagicNumber(file.getBytes());
            securityUtil.validateFileSize(file.getSize(), appConfig.getUpload().getMaxSize());

            // Create session and save file
            String sessionId = sessionManager.createSession();
            Path filePath = sessionManager.saveUploadedFile(sessionId, file);

            // Get file size
            long fileSize = filePath.toFile().length();

            // Analyze JAR file
            ProjectInfo projectInfo = fileAnalyzerService.analyzeJavaArtifact(filePath);

            logger.info("Uploaded and analyzed Java artifact: {}", file.getOriginalFilename());

            // Schedule cleanup after 1 hour
            sessionManager.scheduleCleanup(sessionId);

            UploadResponse response = new UploadResponse();
            response.setSessionId(sessionId);
            response.setFilename(file.getOriginalFilename());
            response.setSize(fileSize);
            response.setProjectInfo(projectInfo);

            return ResponseEntity.ok(response);

        } catch (com.containerize.exception.InvalidFileException e) {
            logger.warn("File validation failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to upload Java artifact: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Upload failed: " + e.getMessage());
        }
    }

    /**
     * Download generated Dockerfile
     *
     * - Returns file response with proper headers
     */
    @GetMapping("/download/{sessionId}")
    public ResponseEntity<Resource> downloadDockerfile(@PathVariable String sessionId) {
        try {
            // Check if session exists
            if (!sessionManager.sessionExists(sessionId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found or expired");
            }

            // Get Dockerfile path
            Path dockerfilePath = sessionManager.getSessionDir(sessionId).resolve("Dockerfile");

            if (!dockerfilePath.toFile().exists()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dockerfile not found");
            }

            logger.info("Downloading Dockerfile from session {}", sessionId);

            Resource resource = new FileSystemResource(dockerfilePath);

            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Dockerfile")
                    .body(resource);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to download Dockerfile: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Download failed: " + e.getMessage());
        }
    }
}
