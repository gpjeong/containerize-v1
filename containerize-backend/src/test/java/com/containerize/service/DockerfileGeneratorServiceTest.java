package com.containerize.service;

import com.containerize.dto.request.ProjectInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DockerfileGeneratorServiceTest {

    private DockerfileGeneratorService generatorService;

    @BeforeEach
    void setUp() {
        generatorService = new DockerfileGeneratorService();
    }

    @Nested
    @DisplayName("selectTemplate")
    class SelectTemplate {

        @Test
        @DisplayName("should select FastAPI template for Python FastAPI")
        void shouldSelectFastapiTemplate() {
            ProjectInfo info = new ProjectInfo();
            info.setLanguage("python");
            info.setFramework("fastapi");

            String template = generatorService.selectTemplate(info, new HashMap<>());
            assertEquals("python/fastapi.dockerfile.j2", template);
        }

        @Test
        @DisplayName("should select Flask template for Python Flask")
        void shouldSelectFlaskTemplate() {
            ProjectInfo info = new ProjectInfo();
            info.setLanguage("python");
            info.setFramework("flask");

            String template = generatorService.selectTemplate(info, new HashMap<>());
            assertEquals("python/flask.dockerfile.j2", template);
        }

        @Test
        @DisplayName("should select Django template for Python Django")
        void shouldSelectDjangoTemplate() {
            ProjectInfo info = new ProjectInfo();
            info.setLanguage("python");
            info.setFramework("django");

            String template = generatorService.selectTemplate(info, new HashMap<>());
            assertEquals("python/django.dockerfile.j2", template);
        }

        @Test
        @DisplayName("should select Express template for Node.js Express")
        void shouldSelectExpressTemplate() {
            ProjectInfo info = new ProjectInfo();
            info.setLanguage("nodejs");
            info.setFramework("express");

            String template = generatorService.selectTemplate(info, new HashMap<>());
            assertEquals("nodejs/express.dockerfile.j2", template);
        }

        @Test
        @DisplayName("should select NestJS template for Node.js NestJS")
        void shouldSelectNestjsTemplate() {
            ProjectInfo info = new ProjectInfo();
            info.setLanguage("nodejs");
            info.setFramework("nestjs");

            String template = generatorService.selectTemplate(info, new HashMap<>());
            assertEquals("nodejs/nestjs.dockerfile.j2", template);
        }

        @Test
        @DisplayName("should select Next.js template for Node.js Next.js")
        void shouldSelectNextjsTemplate() {
            ProjectInfo info = new ProjectInfo();
            info.setLanguage("nodejs");
            info.setFramework("nextjs");

            String template = generatorService.selectTemplate(info, new HashMap<>());
            assertEquals("nodejs/nextjs.dockerfile.j2", template);
        }

        @Test
        @DisplayName("should select Maven template for Java Maven build tool")
        void shouldSelectMavenTemplate() {
            ProjectInfo info = new ProjectInfo();
            info.setLanguage("java");
            info.setFramework("spring-boot");

            Map<String, Object> config = new HashMap<>();
            config.put("build_tool", "maven");

            String template = generatorService.selectTemplate(info, config);
            assertEquals("java/spring-boot-maven.dockerfile.j2", template);
        }

        @Test
        @DisplayName("should select Gradle template for Java Gradle build tool")
        void shouldSelectGradleTemplate() {
            ProjectInfo info = new ProjectInfo();
            info.setLanguage("java");
            info.setFramework("spring-boot");

            Map<String, Object> config = new HashMap<>();
            config.put("build_tool", "gradle");

            String template = generatorService.selectTemplate(info, config);
            assertEquals("java/spring-boot-gradle.dockerfile.j2", template);
        }

        @Test
        @DisplayName("should select JAR template for Java pre-built artifact")
        void shouldSelectJarTemplate() {
            ProjectInfo info = new ProjectInfo();
            info.setLanguage("java");
            info.setFramework("spring-boot");

            String template = generatorService.selectTemplate(info, new HashMap<>());
            assertEquals("java/spring-boot-jar.dockerfile.j2", template);
        }

        @Test
        @DisplayName("should fall back to generic template for unknown framework")
        void shouldFallbackToGeneric() {
            ProjectInfo info = new ProjectInfo();
            info.setLanguage("python");
            info.setFramework("unknown-framework");

            String template = generatorService.selectTemplate(info, new HashMap<>());
            assertEquals("python/generic.dockerfile.j2", template);
        }
    }

    @Nested
    @DisplayName("buildContext")
    class BuildContext {

        @Test
        @DisplayName("should set security defaults (non-root user)")
        void shouldSetSecurityDefaults() {
            ProjectInfo info = new ProjectInfo();
            info.setLanguage("python");
            info.setFramework("fastapi");
            info.setMetadata(new HashMap<>());

            Map<String, Object> context = generatorService.buildContext(info, new HashMap<>());

            assertEquals("appuser", context.get("user"));
            assertEquals("/health", context.get("health_check_path"));
        }

        @Test
        @DisplayName("should preserve user-provided values over defaults")
        void shouldPreserveUserValues() {
            ProjectInfo info = new ProjectInfo();
            info.setLanguage("python");
            info.setFramework("fastapi");
            info.setMetadata(new HashMap<>());

            Map<String, Object> config = new HashMap<>();
            config.put("user", "myuser");
            config.put("port", 3000);
            config.put("health_check_path", "/api/health");

            Map<String, Object> context = generatorService.buildContext(info, config);

            assertEquals("myuser", context.get("user"));
            assertEquals(3000, context.get("port"));
            assertEquals("/api/health", context.get("health_check_path"));
        }

        @Test
        @DisplayName("should set default port 8000")
        void shouldSetDefaultPort() {
            ProjectInfo info = new ProjectInfo();
            info.setLanguage("python");
            info.setFramework("fastapi");
            info.setMetadata(new HashMap<>());

            Map<String, Object> context = generatorService.buildContext(info, new HashMap<>());

            assertEquals(8000, context.get("port"));
        }

        @Test
        @DisplayName("should select correct base image for Python")
        void shouldSelectPythonBaseImage() {
            ProjectInfo info = new ProjectInfo();
            info.setLanguage("python");
            info.setFramework("fastapi");
            info.setMetadata(new HashMap<>());

            Map<String, Object> context = generatorService.buildContext(info, new HashMap<>());

            String baseImage = (String) context.get("base_image");
            assertTrue(baseImage.startsWith("python:"));
            assertTrue(baseImage.endsWith("-slim"));
        }

        @Test
        @DisplayName("should select correct base image for Node.js")
        void shouldSelectNodejsBaseImage() {
            ProjectInfo info = new ProjectInfo();
            info.setLanguage("nodejs");
            info.setFramework("express");
            info.setMetadata(new HashMap<>());

            Map<String, Object> context = generatorService.buildContext(info, new HashMap<>());

            String baseImage = (String) context.get("base_image");
            assertTrue(baseImage.startsWith("node:"));
            assertTrue(baseImage.endsWith("-alpine"));
        }

        @Test
        @DisplayName("should select correct base image for Java")
        void shouldSelectJavaBaseImage() {
            ProjectInfo info = new ProjectInfo();
            info.setLanguage("java");
            info.setFramework("spring-boot");
            info.setMetadata(new HashMap<>());

            Map<String, Object> context = generatorService.buildContext(info, new HashMap<>());

            String baseImage = (String) context.get("base_image");
            assertTrue(baseImage.startsWith("eclipse-temurin:"));
            assertTrue(baseImage.endsWith("-jre-alpine"));
        }

        @Test
        @DisplayName("should convert string environment_vars to map")
        void shouldConvertStringEnvVars() {
            ProjectInfo info = new ProjectInfo();
            info.setLanguage("python");
            info.setFramework("fastapi");
            info.setMetadata(new HashMap<>());

            Map<String, Object> config = new HashMap<>();
            config.put("environment_vars", "KEY1=value1\nKEY2=value2");

            Map<String, Object> context = generatorService.buildContext(info, config);

            @SuppressWarnings("unchecked")
            Map<String, String> envVars = (Map<String, String>) context.get("environment_vars");
            assertEquals("value1", envVars.get("KEY1"));
            assertEquals("value2", envVars.get("KEY2"));
        }

        @Test
        @DisplayName("should set Python-specific defaults")
        void shouldSetPythonDefaults() {
            ProjectInfo info = new ProjectInfo();
            info.setLanguage("python");
            info.setFramework("fastapi");
            Map<String, String> metadata = new HashMap<>();
            metadata.put("server", "uvicorn");
            info.setMetadata(metadata);

            Map<String, Object> context = generatorService.buildContext(info, new HashMap<>());

            assertEquals("uvicorn", context.get("server"));
            assertEquals("pip", context.get("package_manager"));
            assertEquals("main.py", context.get("entrypoint_file"));
        }

        @Test
        @DisplayName("should set Node.js-specific defaults")
        void shouldSetNodejsDefaults() {
            ProjectInfo info = new ProjectInfo();
            info.setLanguage("nodejs");
            info.setFramework("express");
            Map<String, String> metadata = new HashMap<>();
            metadata.put("package_manager", "npm");
            metadata.put("start_command", "npm start");
            metadata.put("build_command", "");
            info.setMetadata(metadata);

            Map<String, Object> context = generatorService.buildContext(info, new HashMap<>());

            assertEquals("npm", context.get("package_manager"));
            assertEquals("npm start", context.get("start_command"));
        }

        @Test
        @DisplayName("should set Java-specific defaults")
        void shouldSetJavaDefaults() {
            ProjectInfo info = new ProjectInfo();
            info.setLanguage("java");
            info.setFramework("spring-boot");
            Map<String, String> metadata = new HashMap<>();
            metadata.put("jar_filename", "myapp.jar");
            info.setMetadata(metadata);

            Map<String, Object> context = generatorService.buildContext(info, new HashMap<>());

            assertEquals("myapp.jar", context.get("jar_file_name"));
            assertEquals("-Xmx512m", context.get("jvm_options"));
        }
    }

    @Nested
    @DisplayName("selectBaseImage")
    class SelectBaseImage {

        @Test
        @DisplayName("should select Python slim image with version")
        void shouldSelectPythonImage() {
            assertEquals("python:3.11-slim", generatorService.selectBaseImage("python", "3.11"));
        }

        @Test
        @DisplayName("should select Node.js alpine image with version")
        void shouldSelectNodejsImage() {
            assertEquals("node:20-alpine", generatorService.selectBaseImage("nodejs", "20"));
        }

        @Test
        @DisplayName("should select Java Temurin image with version")
        void shouldSelectJavaImage() {
            assertEquals("eclipse-temurin:17-jre-alpine", generatorService.selectBaseImage("java", "17"));
        }

        @Test
        @DisplayName("should fall back to default version when empty")
        void shouldFallbackToDefault() {
            assertEquals("python:3.11-slim", generatorService.selectBaseImage("python", ""));
            assertEquals("node:20-alpine", generatorService.selectBaseImage("nodejs", ""));
            assertEquals("eclipse-temurin:17-jre-alpine", generatorService.selectBaseImage("java", ""));
        }

        @Test
        @DisplayName("should return alpine for unknown language")
        void shouldReturnAlpineForUnknown() {
            assertEquals("alpine:latest", generatorService.selectBaseImage("ruby", "3.2"));
        }
    }
}
