package com.containerize.service;

import com.containerize.dto.request.ProjectInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileAnalyzerServiceTest {

    private FileAnalyzerService fileAnalyzerService;

    @BeforeEach
    void setUp() {
        fileAnalyzerService = new FileAnalyzerService();
    }

    @Nested
    @DisplayName("analyzePythonConfig")
    class AnalyzePythonConfig {

        @Test
        @DisplayName("should detect FastAPI framework from requirements")
        void shouldDetectFastapi() {
            String requirements = "fastapi==0.100.0\nuvicorn==0.23.0\npydantic==2.0";
            ProjectInfo result = fileAnalyzerService.analyzePythonConfig(requirements, "auto");

            assertEquals("python", result.getLanguage());
            assertEquals("fastapi", result.getFramework());
            assertEquals("uvicorn", result.getMetadata().get("server"));
        }

        @Test
        @DisplayName("should detect Flask framework from requirements")
        void shouldDetectFlask() {
            String requirements = "flask==3.0.0\ngunicorn==21.2.0";
            ProjectInfo result = fileAnalyzerService.analyzePythonConfig(requirements, "auto");

            assertEquals("flask", result.getFramework());
            assertEquals("gunicorn", result.getMetadata().get("server"));
        }

        @Test
        @DisplayName("should detect Django framework from requirements")
        void shouldDetectDjango() {
            String requirements = "django==4.2\ngunicorn==21.2.0";
            ProjectInfo result = fileAnalyzerService.analyzePythonConfig(requirements, "auto");

            assertEquals("django", result.getFramework());
            assertEquals("gunicorn", result.getMetadata().get("server"));
        }

        @Test
        @DisplayName("should respect explicitly specified framework")
        void shouldRespectExplicitFramework() {
            String requirements = "fastapi==0.100.0\nuvicorn==0.23.0";
            ProjectInfo result = fileAnalyzerService.analyzePythonConfig(requirements, "flask");

            assertEquals("flask", result.getFramework());
        }

        @Test
        @DisplayName("should handle empty requirements")
        void shouldHandleEmptyRequirements() {
            ProjectInfo result = fileAnalyzerService.analyzePythonConfig("", "fastapi");

            assertEquals("python", result.getLanguage());
            assertEquals("fastapi", result.getFramework());
        }

        @Test
        @DisplayName("should handle null requirements")
        void shouldHandleNullRequirements() {
            ProjectInfo result = fileAnalyzerService.analyzePythonConfig(null, "fastapi");

            assertEquals("python", result.getLanguage());
            assertEquals("fastapi", result.getFramework());
        }

        @Test
        @DisplayName("should skip comment lines in requirements")
        void shouldSkipComments() {
            String requirements = "# This is a comment\nfastapi==0.100.0\n# Another comment\nuvicorn";
            ProjectInfo result = fileAnalyzerService.analyzePythonConfig(requirements, "auto");

            assertEquals("fastapi", result.getFramework());
            assertEquals("2", result.getMetadata().get("package_count"));
        }

        @Test
        @DisplayName("should parse package names with version specifiers")
        void shouldParseVersionSpecifiers() {
            String requirements = "fastapi>=0.100.0\nuvicorn<1.0\npydantic!=1.0";
            ProjectInfo result = fileAnalyzerService.analyzePythonConfig(requirements, "auto");

            assertTrue(result.getDependencies().contains("fastapi"));
            assertTrue(result.getDependencies().contains("uvicorn"));
            assertTrue(result.getDependencies().contains("pydantic"));
        }
    }

    @Nested
    @DisplayName("analyzeNodeJSConfig")
    class AnalyzeNodeJSConfig {

        @Test
        @DisplayName("should detect Express framework")
        void shouldDetectExpress() {
            Map<String, Object> packageJson = new HashMap<>();
            packageJson.put("dependencies", Map.of("express", "^4.18.0"));
            packageJson.put("devDependencies", new HashMap<>());

            ProjectInfo result = fileAnalyzerService.analyzeNodeJSConfig(packageJson, "auto");

            assertEquals("nodejs", result.getLanguage());
            assertEquals("express", result.getFramework());
        }

        @Test
        @DisplayName("should detect Next.js framework")
        void shouldDetectNextjs() {
            Map<String, Object> packageJson = new HashMap<>();
            packageJson.put("dependencies", Map.of("next", "^14.0.0", "react", "^18.0.0"));
            packageJson.put("devDependencies", new HashMap<>());

            ProjectInfo result = fileAnalyzerService.analyzeNodeJSConfig(packageJson, "auto");

            assertEquals("nextjs", result.getFramework());
        }

        @Test
        @DisplayName("should detect NestJS framework")
        void shouldDetectNestjs() {
            Map<String, Object> packageJson = new HashMap<>();
            packageJson.put("dependencies", Map.of("@nestjs/core", "^10.0.0"));
            packageJson.put("devDependencies", new HashMap<>());

            ProjectInfo result = fileAnalyzerService.analyzeNodeJSConfig(packageJson, "auto");

            assertEquals("nestjs", result.getFramework());
        }

        @Test
        @DisplayName("should detect yarn package manager")
        void shouldDetectYarn() {
            Map<String, Object> packageJson = new HashMap<>();
            packageJson.put("dependencies", Map.of("express", "^4.18.0"));
            packageJson.put("devDependencies", new HashMap<>());
            packageJson.put("packageManager", "yarn@3.6.0");

            ProjectInfo result = fileAnalyzerService.analyzeNodeJSConfig(packageJson, "auto");

            assertEquals("yarn", result.getMetadata().get("package_manager"));
        }

        @Test
        @DisplayName("should detect pnpm package manager")
        void shouldDetectPnpm() {
            Map<String, Object> packageJson = new HashMap<>();
            packageJson.put("dependencies", Map.of("express", "^4.18.0"));
            packageJson.put("devDependencies", new HashMap<>());
            packageJson.put("packageManager", "pnpm@8.0.0");

            ProjectInfo result = fileAnalyzerService.analyzeNodeJSConfig(packageJson, "auto");

            assertEquals("pnpm", result.getMetadata().get("package_manager"));
        }

        @Test
        @DisplayName("should default to npm when no packageManager field")
        void shouldDefaultToNpm() {
            Map<String, Object> packageJson = new HashMap<>();
            packageJson.put("dependencies", Map.of("express", "^4.18.0"));
            packageJson.put("devDependencies", new HashMap<>());

            ProjectInfo result = fileAnalyzerService.analyzeNodeJSConfig(packageJson, "auto");

            assertEquals("npm", result.getMetadata().get("package_manager"));
        }

        @Test
        @DisplayName("should handle null packageJson")
        void shouldHandleNullPackageJson() {
            ProjectInfo result = fileAnalyzerService.analyzeNodeJSConfig(null, "express");

            assertEquals("nodejs", result.getLanguage());
            assertEquals("express", result.getFramework());
        }

        @Test
        @DisplayName("should detect build and start commands from scripts")
        void shouldDetectScripts() {
            Map<String, Object> packageJson = new HashMap<>();
            packageJson.put("dependencies", Map.of("express", "^4.18.0"));
            packageJson.put("devDependencies", new HashMap<>());
            packageJson.put("scripts", Map.of("build", "tsc", "start", "node dist/index.js"));

            ProjectInfo result = fileAnalyzerService.analyzeNodeJSConfig(packageJson, "auto");

            assertFalse(result.getMetadata().get("build_command").isEmpty());
            assertEquals("npm start", result.getMetadata().get("start_command"));
        }
    }
}
