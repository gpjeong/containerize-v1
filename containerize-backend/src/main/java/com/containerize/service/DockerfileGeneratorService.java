package com.containerize.service;

import com.containerize.dto.request.ProjectInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

/**
 * Generates optimized Dockerfiles using Jinja2 templates
 */
@Service
public class DockerfileGeneratorService {

    private static final Logger logger = LoggerFactory.getLogger(DockerfileGeneratorService.class);

    @Autowired
    private TemplateEngineService templateEngine;

    /**
     * Generate Dockerfile from project info and user config
     *
     * @param projectInfo Detected project information
     * @param userConfig User-provided configuration
     * @return Generated Dockerfile content
     * @throws IOException if template rendering fails
     */
    public String generate(ProjectInfo projectInfo, Map<String, Object> userConfig) throws IOException {
        // Select appropriate template
        String templateName = selectTemplate(projectInfo, userConfig);

        // Build context by merging project info and user config
        Map<String, Object> context = buildContext(projectInfo, userConfig);

        // Render Dockerfile
        String dockerfile = templateEngine.render(templateName, context);

        logger.info("Generated Dockerfile for {}/{}", projectInfo.getLanguage(), projectInfo.getFramework());
        return dockerfile;
    }

    /**
     * Select appropriate template based on language and framework
     *
     * @param projectInfo Project information
     * @param config User configuration
     * @return Template path relative to template directory
     */
    public String selectTemplate(ProjectInfo projectInfo, Map<String, Object> config) {
        String language = projectInfo.getLanguage();
        String framework = projectInfo.getFramework();

        // Map to template file
        Map<String, Map<String, String>> templateMap = new HashMap<>();

        Map<String, String> pythonTemplates = new HashMap<>();
        pythonTemplates.put("generic", "python/generic.dockerfile.j2");
        pythonTemplates.put("fastapi", "python/fastapi.dockerfile.j2");
        pythonTemplates.put("flask", "python/flask.dockerfile.j2");
        pythonTemplates.put("django", "python/django.dockerfile.j2");
        templateMap.put("python", pythonTemplates);

        Map<String, String> nodejsTemplates = new HashMap<>();
        nodejsTemplates.put("generic", "nodejs/generic.dockerfile.j2");
        nodejsTemplates.put("express", "nodejs/express.dockerfile.j2");
        nodejsTemplates.put("nestjs", "nodejs/nestjs.dockerfile.j2");
        nodejsTemplates.put("nextjs", "nodejs/nextjs.dockerfile.j2");
        templateMap.put("nodejs", nodejsTemplates);

        Map<String, String> javaTemplates = new HashMap<>();
        javaTemplates.put("spring-boot", "java/spring-boot-jar.dockerfile.j2");
        templateMap.put("java", javaTemplates);

        // For Java, check build tool to select correct template
        if ("java".equals(language)) {
            String buildTool = (String) config.getOrDefault("build_tool", "jar");
            if ("maven".equals(buildTool)) {
                return "java/spring-boot-maven.dockerfile.j2";
            } else if ("gradle".equals(buildTool)) {
                return "java/spring-boot-gradle.dockerfile.j2";
            } else { // jar (pre-built)
                return "java/spring-boot-jar.dockerfile.j2";
            }
        }

        String templatePath = templateMap
            .getOrDefault(language, new HashMap<>())
            .get(framework);

        if (templatePath == null) {
            // Fallback to generic template
            templatePath = language + "/generic.dockerfile.j2";
            logger.warn("No specific template found, using generic: {}", templatePath);
        }

        return templatePath;
    }

    /**
     * Build template context from project info and config
     *
     * Applies security defaults and optimizations
     *
     * @param projectInfo Project information
     * @param config User configuration
     * @return Template context
     */
    public Map<String, Object> buildContext(ProjectInfo projectInfo, Map<String, Object> config) {
        // Start with user config
        Map<String, Object> context = new HashMap<>(config);

        // Add project info
        context.put("detected_framework", projectInfo.getFramework());
        context.put("detected_version", projectInfo.getDetectedVersion());
        context.put("build_tool", projectInfo.getBuildTool());
        context.put("main_class", projectInfo.getMainClass());
        context.put("dependencies", projectInfo.getDependencies());

        // Merge metadata
        context.putAll(projectInfo.getMetadata());

        // Apply security defaults
        if (!context.containsKey("user") || context.get("user") == null || context.get("user").toString().isEmpty()) {
            context.put("user", "appuser");
        }

        if (!context.containsKey("health_check_path")) {
            context.put("health_check_path", "/health");
        }

        // Set default port if not specified
        if (!context.containsKey("port")) {
            context.put("port", 8000);
        }

        // Ensure base_image has sensible default
        if (!context.containsKey("base_image") || context.get("base_image") == null || context.get("base_image").toString().isEmpty()) {
            String baseImage = selectBaseImage(
                projectInfo.getLanguage(),
                context.getOrDefault("runtime_version", "").toString()
            );
            context.put("base_image", baseImage);
        }

        // Ensure environment_vars is a Map (frontend sends Map, but API may receive String)
        Object envVars = context.get("environment_vars");
        if (envVars instanceof String) {
            Map<String, String> envMap = new LinkedHashMap<>();
            String envStr = (String) envVars;
            if (!envStr.isEmpty()) {
                for (String line : envStr.split("\n")) {
                    line = line.trim();
                    if (!line.isEmpty() && line.contains("=")) {
                        String[] parts = line.split("=", 2);
                        envMap.put(parts[0].trim(), parts[1].trim());
                    }
                }
            }
            context.put("environment_vars", envMap);
        } else if (envVars == null) {
            context.put("environment_vars", new LinkedHashMap<>());
        }

        // Ensure system_dependencies is a List
        Object sysDeps = context.get("system_dependencies");
        if (sysDeps instanceof String) {
            String depsStr = (String) sysDeps;
            if (depsStr.isEmpty()) {
                context.put("system_dependencies", new ArrayList<>());
            } else {
                context.put("system_dependencies", Arrays.asList(depsStr.split("[,\\s]+")));
            }
        } else if (sysDeps == null) {
            context.put("system_dependencies", new ArrayList<>());
        }

        // Language-specific context adjustments
        if ("python".equals(projectInfo.getLanguage())) {
            context = adjustPythonContext(context, projectInfo);
        } else if ("nodejs".equals(projectInfo.getLanguage())) {
            context = adjustNodeJSContext(context, projectInfo);
        } else if ("java".equals(projectInfo.getLanguage())) {
            context = adjustJavaContext(context, projectInfo);
        }

        return context;
    }

    /**
     * Select default base image for language and version
     *
     * @param language Programming language
     * @param version Runtime version
     * @return Default base image
     */
    public String selectBaseImage(String language, String version) {
        return switch (language) {
            case "python" -> version != null && !version.isEmpty()
                ? "python:" + version + "-slim"
                : "python:3.11-slim";
            case "nodejs" -> version != null && !version.isEmpty()
                ? "node:" + version + "-alpine"
                : "node:20-alpine";
            case "java" -> version != null && !version.isEmpty()
                ? "eclipse-temurin:" + version + "-jre-alpine"
                : "eclipse-temurin:17-jre-alpine";
            default -> "alpine:latest";
        };
    }

    /**
     * Adjust context for Python projects
     */
    private Map<String, Object> adjustPythonContext(Map<String, Object> context, ProjectInfo projectInfo) {
        // Set default server if not specified
        if (!context.containsKey("server") || context.get("server") == null) {
            String server = projectInfo.getMetadata().getOrDefault("server", "uvicorn");
            context.put("server", server);
        }

        // Set default package manager
        if (!context.containsKey("package_manager") || context.get("package_manager") == null) {
            context.put("package_manager", "pip");
        }

        // Set default entrypoint
        if (!context.containsKey("entrypoint_file") || context.get("entrypoint_file") == null) {
            context.put("entrypoint_file", "main.py");
        }

        return context;
    }

    /**
     * Adjust context for Node.js projects
     */
    private Map<String, Object> adjustNodeJSContext(Map<String, Object> context, ProjectInfo projectInfo) {
        // Set package manager from metadata
        if (!context.containsKey("package_manager") || context.get("package_manager") == null) {
            String packageManager = projectInfo.getMetadata().getOrDefault("package_manager", "npm");
            context.put("package_manager", packageManager);
        }

        // Set build and start commands
        if (!context.containsKey("build_command") || context.get("build_command") == null) {
            String buildCommand = projectInfo.getMetadata().getOrDefault("build_command", "");
            context.put("build_command", buildCommand);
        }

        if (!context.containsKey("start_command") || context.get("start_command") == null) {
            String startCommand = projectInfo.getMetadata().getOrDefault("start_command", "npm start");
            context.put("start_command", startCommand);
        }

        return context;
    }

    /**
     * Adjust context for Java projects
     */
    private Map<String, Object> adjustJavaContext(Map<String, Object> context, ProjectInfo projectInfo) {
        // Set JAR filename from metadata
        if (!context.containsKey("jar_file_name") || context.get("jar_file_name") == null) {
            String jarFileName = projectInfo.getMetadata().getOrDefault("jar_filename", "app.jar");
            context.put("jar_file_name", jarFileName);
        }

        // Set JVM options if not specified
        if (!context.containsKey("jvm_options") || context.get("jvm_options") == null) {
            context.put("jvm_options", "-Xmx512m");
        }

        return context;
    }
}
