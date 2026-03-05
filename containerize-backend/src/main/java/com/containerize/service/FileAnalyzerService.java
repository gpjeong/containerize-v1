package com.containerize.service;

import com.containerize.dto.request.ProjectInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * File and configuration analysis service for detecting language, framework, and build tools
 */
@Service
public class FileAnalyzerService {

    private static final Logger logger = LoggerFactory.getLogger(FileAnalyzerService.class);

    /**
     * Analyze JAR/WAR file
     *
     * Extracts:
     * - MANIFEST.MF for Spring Boot version, main class
     * - Build tool detection
     * - Fat JAR vs Thin JAR
     *
     * @param filePath Path to JAR/WAR file
     * @return ProjectInfo with detected project information
     * @throws IOException if file cannot be read
     */
    public ProjectInfo analyzeJavaArtifact(Path filePath) throws IOException {
        try (ZipFile jar = new ZipFile(filePath.toFile())) {
            Map<String, String> manifestInfo = parseManifest(jar);

            // Detect if it's a Spring Boot application
            String framework = manifestInfo.getOrDefault("spring_boot", "false").equals("true")
                ? "spring-boot"
                : "java";

            // Detect build tool from manifest or JAR structure
            String buildTool = detectJavaBuildTool(jar, manifestInfo);

            Map<String, String> metadata = new HashMap<>();
            metadata.put("spring_boot_version", manifestInfo.getOrDefault("spring_boot_version", ""));
            metadata.put("fat_jar", manifestInfo.getOrDefault("fat_jar", "true"));
            metadata.put("jar_filename", filePath.getFileName().toString());

            ProjectInfo projectInfo = new ProjectInfo();
            projectInfo.setLanguage("java");
            projectInfo.setFramework(framework);
            projectInfo.setDetectedVersion(manifestInfo.getOrDefault("java_version", "17"));
            projectInfo.setBuildTool(buildTool);
            projectInfo.setMainClass(manifestInfo.get("main_class"));
            projectInfo.setMetadata(metadata);

            return projectInfo;

        } catch (Exception e) {
            logger.error("Failed to analyze JAR file: {}", e.getMessage(), e);
            throw new IOException("Failed to analyze JAR file: " + e.getMessage(), e);
        }
    }

    /**
     * Parse MANIFEST.MF from JAR file
     *
     * @param jar ZipFile object
     * @return Map with parsed manifest information
     */
    public Map<String, String> parseManifest(ZipFile jar) {
        try {
            ZipEntry manifestEntry = jar.getEntry("META-INF/MANIFEST.MF");
            if (manifestEntry == null) {
                logger.warn("MANIFEST.MF not found in JAR");
                return createDefaultManifest();
            }

            String manifestData;
            try (java.io.InputStream is = jar.getInputStream(manifestEntry)) {
                manifestData = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            Map<String, String> manifest = new HashMap<>();

            // Parse manifest entries (handle line continuations)
            String[] lines = manifestData.split("\n");
            for (String line : lines) {
                if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    if (parts.length == 2) {
                        manifest.put(parts[0].trim(), parts[1].trim());
                    }
                }
            }

            // Extract relevant information
            Map<String, String> result = new HashMap<>();

            // Main class
            if (manifest.containsKey("Main-Class")) {
                result.put("main_class", manifest.get("Main-Class"));
            } else if (manifest.containsKey("Start-Class")) {
                result.put("main_class", manifest.get("Start-Class"));
            }

            // Spring Boot detection
            if (manifest.containsKey("Spring-Boot-Version")) {
                result.put("spring_boot", "true");
                result.put("spring_boot_version", manifest.get("Spring-Boot-Version"));
            } else {
                result.put("spring_boot", "false");
            }

            // Java version (if specified)
            if (manifest.containsKey("Build-Jdk")) {
                String javaVersion = manifest.get("Build-Jdk");
                String[] versionParts = javaVersion.split("\\.");
                if (versionParts.length > 0) {
                    result.put("java_version", versionParts[0]);
                }
            }

            // Fat JAR detection (Spring Boot apps are typically fat JARs)
            boolean isSpringBoot = result.getOrDefault("spring_boot", "false").equals("true");
            boolean hasBoot = jar.stream().anyMatch(e -> e.getName().startsWith("BOOT-INF/"));
            result.put("fat_jar", String.valueOf(isSpringBoot || hasBoot));

            return result;

        } catch (Exception e) {
            logger.warn("Failed to parse manifest: {}", e.getMessage());
            return createDefaultManifest();
        }
    }

    /**
     * Detect build tool (Maven, Gradle, or just JAR)
     *
     * @param jar ZipFile object
     * @param manifestInfo Parsed manifest information
     * @return Build tool name
     */
    public String detectJavaBuildTool(ZipFile jar, Map<String, String> manifestInfo) {
        List<String> fileNames = new ArrayList<>(jar.stream()
            .map(ZipEntry::getName)
            .toList());

        // Check for Maven
        if (fileNames.stream().anyMatch(f -> f.toLowerCase().contains("maven"))) {
            return "maven";
        }

        // Check for Gradle
        if (fileNames.stream().anyMatch(f -> f.toLowerCase().contains("gradle"))) {
            return "gradle";
        }

        // Default to JAR (pre-built artifact)
        return "jar";
    }

    /**
     * Analyze Python configuration
     *
     * @param requirementsContent Content of requirements.txt
     * @param framework User-specified framework
     * @return ProjectInfo with detected project information
     */
    public ProjectInfo analyzePythonConfig(String requirementsContent, String framework) {
        List<String> dependencies = new ArrayList<>();
        String detectedFramework = framework;
        String server = null;

        if (requirementsContent != null && !requirementsContent.isEmpty()) {
            // Parse requirements.txt
            for (String line : requirementsContent.split("\n")) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    // Extract package name (before ==, >=, etc.)
                    String pkgName = line.split("[=<>!]")[0].trim();
                    dependencies.add(pkgName);
                }
            }

            // Auto-detect framework if not specified
            if (framework == null || framework.isEmpty() || "auto".equalsIgnoreCase(framework)) {
                if (dependencies.contains("fastapi")) {
                    detectedFramework = "fastapi";
                    server = "uvicorn";
                } else if (dependencies.contains("flask")) {
                    detectedFramework = "flask";
                    server = "gunicorn";
                } else if (dependencies.contains("django")) {
                    detectedFramework = "django";
                    server = "gunicorn";
                }
            }

            // Detect server
            if (server == null) {
                if ("fastapi".equalsIgnoreCase(detectedFramework)) {
                    server = "uvicorn";
                } else if ("flask".equalsIgnoreCase(detectedFramework) || "django".equalsIgnoreCase(detectedFramework)) {
                    server = "gunicorn";
                }
            }
        }

        Map<String, String> metadata = new HashMap<>();
        metadata.put("server", server != null ? server : "uvicorn");
        metadata.put("package_count", String.valueOf(dependencies.size()));

        ProjectInfo projectInfo = new ProjectInfo();
        projectInfo.setLanguage("python");
        projectInfo.setFramework(detectedFramework);
        projectInfo.setDependencies(dependencies);
        projectInfo.setMetadata(metadata);

        return projectInfo;
    }

    /**
     * Analyze Node.js configuration
     *
     * @param packageJson Parsed package.json content
     * @param framework User-specified framework
     * @return ProjectInfo with detected project information
     */
    public ProjectInfo analyzeNodeJSConfig(Map<String, Object> packageJson, String framework) {
        List<String> dependencies = new ArrayList<>();
        String detectedFramework = framework;
        String packageManager = "npm";
        String buildCommand = null;
        String startCommand = "npm start";

        if (packageJson != null) {
            // Extract dependencies
            @SuppressWarnings("unchecked")
            Map<String, Object> deps = (Map<String, Object>) packageJson.getOrDefault("dependencies", new HashMap<>());
            @SuppressWarnings("unchecked")
            Map<String, Object> devDeps = (Map<String, Object>) packageJson.getOrDefault("devDependencies", new HashMap<>());

            dependencies.addAll(deps.keySet());
            dependencies.addAll(devDeps.keySet());

            // Auto-detect framework
            if (framework == null || framework.isEmpty() || "auto".equalsIgnoreCase(framework)) {
                if (deps.containsKey("next") || devDeps.containsKey("next")) {
                    detectedFramework = "nextjs";
                    buildCommand = "npm run build";
                    startCommand = "npm start";
                } else if (deps.containsKey("@nestjs/core")) {
                    detectedFramework = "nestjs";
                    buildCommand = "npm run build";
                    startCommand = "npm run start:prod";
                } else if (deps.containsKey("express")) {
                    detectedFramework = "express";
                    startCommand = "node server.js";
                }
            }

            // Detect package manager
            if (packageJson.containsKey("packageManager")) {
                String pm = (String) packageJson.get("packageManager");
                if (pm.contains("yarn")) {
                    packageManager = "yarn";
                } else if (pm.contains("pnpm")) {
                    packageManager = "pnpm";
                }
            }

            // Extract scripts
            @SuppressWarnings("unchecked")
            Map<String, Object> scripts = (Map<String, Object>) packageJson.getOrDefault("scripts", new HashMap<>());
            if (scripts.containsKey("build") && buildCommand == null) {
                buildCommand = packageManager + " run build";
            }
            if (scripts.containsKey("start")) {
                startCommand = packageManager + " start";
            }
        }

        Map<String, String> metadata = new HashMap<>();
        metadata.put("package_manager", packageManager);
        metadata.put("build_command", buildCommand != null ? buildCommand : "");
        metadata.put("start_command", startCommand);
        metadata.put("dependency_count", String.valueOf(dependencies.size()));

        ProjectInfo projectInfo = new ProjectInfo();
        projectInfo.setLanguage("nodejs");
        projectInfo.setFramework(detectedFramework);
        projectInfo.setDependencies(dependencies);
        projectInfo.setMetadata(metadata);

        return projectInfo;
    }

    /**
     * Create default manifest with safe values
     */
    private Map<String, String> createDefaultManifest() {
        Map<String, String> defaultManifest = new HashMap<>();
        defaultManifest.put("spring_boot", "false");
        defaultManifest.put("fat_jar", "true");
        defaultManifest.put("java_version", "17");
        return defaultManifest;
    }
}
