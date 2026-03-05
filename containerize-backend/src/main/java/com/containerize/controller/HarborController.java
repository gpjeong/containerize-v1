package com.containerize.controller;

import com.containerize.service.HarborClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HarborController {

    private static final Logger logger = LoggerFactory.getLogger(HarborController.class);

    /**
     * Extract base URL from Harbor URL (https://harbor.example.com)
     */
    private String extractBaseUrl(String harborUrl) {
        String[] parts = harborUrl.split("/");
        if (parts.length >= 3) {
            return parts[0] + "//" + parts[2];
        }
        return harborUrl;
    }

    /**
     * Check if Harbor project exists
     */
    @PostMapping("/setup/harbor/check-project")
    public ResponseEntity<Map<String, Object>> checkHarborProject(@RequestBody Map<String, Object> request) {
        try {
            String harborUrl = (String) request.get("harbor_url");
            String harborUsername = (String) request.get("harbor_username");
            String harborPassword = (String) request.get("harbor_password");
            String projectName = (String) request.get("project_name");

            String baseHarborUrl = extractBaseUrl(harborUrl);

            HarborClientService harborClient = new HarborClientService();
            harborClient.initialize(baseHarborUrl, harborUsername, harborPassword);

            boolean exists = harborClient.checkProjectExists(projectName);

            Map<String, Object> response = new HashMap<>();
            response.put("exists", exists);
            response.put("project_name", projectName);

            if (exists) {
                response.put("project_url", baseHarborUrl + "/harbor/projects");
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to check Harbor project: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /**
     * Create new Harbor project
     */
    @PostMapping("/setup/harbor/create-project")
    public ResponseEntity<Map<String, Object>> createHarborProject(@RequestBody Map<String, Object> request) {
        try {
            String harborUrl = (String) request.get("harbor_url");
            String harborUsername = (String) request.get("harbor_username");
            String harborPassword = (String) request.get("harbor_password");
            String projectName = (String) request.get("project_name");
            boolean isPublic = (boolean) request.getOrDefault("public", false);
            boolean enableContentTrust = (boolean) request.getOrDefault("enable_content_trust", false);
            boolean autoScan = (boolean) request.getOrDefault("auto_scan", true);
            String severity = (String) request.getOrDefault("severity", "high");
            boolean preventVul = (boolean) request.getOrDefault("prevent_vul", false);

            String baseHarborUrl = extractBaseUrl(harborUrl);

            HarborClientService harborClient = new HarborClientService();
            harborClient.initialize(baseHarborUrl, harborUsername, harborPassword);

            // Check if already exists
            if (harborClient.checkProjectExists(projectName)) {
                Map<String, Object> response = new HashMap<>();
                response.put("project_name", projectName);
                response.put("project_url", baseHarborUrl + "/harbor/projects");
                response.put("status", "already_exists");
                response.put("message", "Project '" + projectName + "' already exists");
                return ResponseEntity.ok(response);
            }

            // Create project
            harborClient.createProject(
                    projectName,
                    isPublic,
                    enableContentTrust,
                    autoScan,
                    severity,
                    preventVul
            );

            Map<String, Object> response = new HashMap<>();
            response.put("project_name", projectName);
            response.put("project_url", baseHarborUrl + "/harbor/projects");
            response.put("status", "created");
            response.put("message", "Project '" + projectName + "' created successfully");

            Map<String, Object> settings = new HashMap<>();
            settings.put("public", isPublic);
            settings.put("auto_scan", autoScan);
            settings.put("severity", severity);
            settings.put("content_trust", enableContentTrust);
            settings.put("prevent_vulnerable", preventVul);
            response.put("settings", settings);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.error("Project creation failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to create Harbor project: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }
}
