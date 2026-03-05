package com.containerize.controller;

import com.containerize.dto.request.GenerateRequest;
import com.containerize.dto.request.NodeJSConfig;
import com.containerize.dto.request.ProjectInfo;
import com.containerize.dto.request.PythonConfig;
import com.containerize.dto.response.AnalyzeResponse;
import com.containerize.dto.response.GenerateResponse;
import com.containerize.service.DockerfileGeneratorService;
import com.containerize.service.FileAnalyzerService;
import com.containerize.service.TemplateEngineService;
import com.containerize.util.SessionManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DockerfileController {

    private static final Logger logger = LoggerFactory.getLogger(DockerfileController.class);

    private final FileAnalyzerService fileAnalyzerService;
    private final DockerfileGeneratorService dockerfileGeneratorService;
    private final TemplateEngineService templateEngineService;
    private final SessionManager sessionManager;

    /**
     * Analyze Python project from configuration
     *
     * - Validates dependencies format
     * - Detects framework and server
     * - Returns analysis results
     */
    @PostMapping("/analyze/python")
    public ResponseEntity<AnalyzeResponse> analyzePythonConfig(@Valid @RequestBody PythonConfig config) {
        try {
            // Analyze Python configuration
            ProjectInfo projectInfo = fileAnalyzerService.analyzePythonConfig(
                    config.getRequirementsContent(),
                    config.getFramework()
            );

            // Suggestions based on analysis
            Map<String, String> suggestions = new HashMap<>();
            if (projectInfo.getMetadata() != null) {
                String server = (String) projectInfo.getMetadata().get("server");
                if (server != null) {
                    suggestions.put("server", "Recommended server: " + server);
                }
            }

            logger.info("Analyzed Python project: {}", config.getFramework());

            AnalyzeResponse response = new AnalyzeResponse();
            response.setProjectInfo(projectInfo);
            response.setSuggestions(suggestions);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to analyze Python config: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Analysis failed: " + e.getMessage());
        }
    }

    /**
     * Analyze Node.js project from configuration
     *
     * - Parses package.json
     * - Detects framework and package manager
     * - Returns analysis results
     */
    @PostMapping("/analyze/nodejs")
    public ResponseEntity<AnalyzeResponse> analyzeNodeJSConfig(@Valid @RequestBody NodeJSConfig config) {
        try {
            // Analyze Node.js configuration
            ProjectInfo projectInfo = fileAnalyzerService.analyzeNodeJSConfig(
                    config.getPackageJson(),
                    config.getFramework()
            );

            // Suggestions based on analysis
            Map<String, String> suggestions = new HashMap<>();
            if (projectInfo.getMetadata() != null) {
                String packageManager = (String) projectInfo.getMetadata().get("package_manager");
                if (packageManager != null) {
                    suggestions.put("package_manager", "Detected package manager: " + packageManager);
                }

                String buildCmd = (String) projectInfo.getMetadata().get("build_command");
                if (buildCmd != null) {
                    suggestions.put("build_command", "Build command: " + buildCmd);
                }
            }

            logger.info("Analyzed Node.js project: {}", config.getFramework());

            AnalyzeResponse response = new AnalyzeResponse();
            response.setProjectInfo(projectInfo);
            response.setSuggestions(suggestions);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to analyze Node.js config: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Analysis failed: " + e.getMessage());
        }
    }

    /**
     * Generate Dockerfile from project info and user config
     *
     * - Validates all inputs
     * - Calls generator service
     * - Returns Dockerfile content and metadata
     */
    @PostMapping("/generate")
    public ResponseEntity<GenerateResponse> generateDockerfile(@Valid @RequestBody GenerateRequest request) {
        try {
            // Use provided project_info or create minimal one from config
            ProjectInfo projectInfo = request.getProjectInfo();
            if (projectInfo == null) {
                Map<String, Object> config = request.getConfig();
                projectInfo = new ProjectInfo();
                projectInfo.setLanguage((String) config.get("language"));
                projectInfo.setFramework((String) config.get("framework"));
                projectInfo.setDetectedVersion((String) config.get("runtime_version"));
            }

            // Generate Dockerfile
            String dockerfileContent = dockerfileGeneratorService.generate(
                    projectInfo,
                    request.getConfig()
            );

            // Generate session ID (saveDockerfile creates directory automatically)
            String sessionId = UUID.randomUUID().toString();

            // Save Dockerfile to session and schedule cleanup after 1 hour (M-5)
            sessionManager.saveDockerfile(sessionId, dockerfileContent);
            sessionManager.scheduleCleanup(sessionId);

            logger.info("Generated Dockerfile for {}/{}", projectInfo.getLanguage(), projectInfo.getFramework());

            GenerateResponse response = new GenerateResponse();
            response.setDockerfile(dockerfileContent);
            response.setSessionId(sessionId);

            Map<String, String> metadata = new HashMap<>();
            metadata.put("language", projectInfo.getLanguage());
            metadata.put("framework", projectInfo.getFramework());
            metadata.put("template", projectInfo.getLanguage() + "/" + projectInfo.getFramework());
            response.setMetadata(metadata);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to generate Dockerfile: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Generation failed: " + e.getMessage());
        }
    }

    /**
     * List available Dockerfile templates
     *
     * - Returns all supported languages and frameworks
     */
    @GetMapping("/templates")
    public ResponseEntity<Map<String, Object>> listTemplates() {
        Map<String, Object> templates = new HashMap<>();
        Map<String, Object> templateMap = new HashMap<>();
        templateMap.put("python", new String[]{"fastapi", "flask", "django"});
        templateMap.put("nodejs", new String[]{"express", "nestjs", "nextjs"});
        templateMap.put("java", new String[]{"spring-boot"});
        templates.put("templates", templateMap);
        return ResponseEntity.ok(templates);
    }
}
