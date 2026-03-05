package com.containerize.controller;

import com.containerize.dto.request.GenerateRequest;
import com.containerize.dto.request.ProjectInfo;
import com.containerize.dto.request.PythonConfig;
import com.containerize.service.DockerfileGeneratorService;
import com.containerize.service.FileAnalyzerService;
import com.containerize.service.TemplateEngineService;
import com.containerize.util.SessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DockerfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Nested
    @DisplayName("HTTP Method Validation (Issue 1: 405)")
    class MethodNotAllowed {

        @Test
        @DisplayName("GET /api/generate should return 405 Method Not Allowed")
        void getGenerateShouldReturn405() throws Exception {
            mockMvc.perform(get("/api/generate"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.detail").exists());
        }

        @Test
        @DisplayName("GET /api/analyze/python should return 405 Method Not Allowed")
        void getAnalyzePythonShouldReturn405() throws Exception {
            mockMvc.perform(get("/api/analyze/python"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.detail").exists());
        }

        @Test
        @DisplayName("GET /api/analyze/nodejs should return 405 Method Not Allowed")
        void getAnalyzeNodejsShouldReturn405() throws Exception {
            mockMvc.perform(get("/api/analyze/nodejs"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.detail").exists());
        }

        @Test
        @DisplayName("DELETE /api/templates should return 405 Method Not Allowed")
        void deleteTemplatesShouldReturn405() throws Exception {
            mockMvc.perform(delete("/api/templates"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.detail").exists());
        }
    }

    @Nested
    @DisplayName("Not Found (Issue 2: 404)")
    class NotFound {

        @Test
        @DisplayName("GET /api/nonexistent should return 404 Not Found")
        void nonExistentEndpointShouldReturn404() throws Exception {
            mockMvc.perform(get("/api/nonexistent"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").exists());
        }

        @Test
        @DisplayName("POST /api/nonexistent should return 404 Not Found")
        void postNonExistentEndpointShouldReturn404() throws Exception {
            mockMvc.perform(post("/api/nonexistent")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").exists());
        }
    }

    @Nested
    @DisplayName("Analyze Endpoint")
    class AnalyzeEndpoint {

        @Test
        @DisplayName("POST /api/analyze/python should return 200 with valid config")
        void analyzePythonShouldReturn200() throws Exception {
            Map<String, Object> body = new HashMap<>();
            body.put("framework", "fastapi");
            body.put("requirements_content", "fastapi==0.100.0\nuvicorn==0.23.0");

            mockMvc.perform(post("/api/analyze/python")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.project_info").exists())
                    .andExpect(jsonPath("$.project_info.language").value("python"));
        }

        @Test
        @DisplayName("POST /api/analyze/nodejs should return 200 with valid config")
        void analyzeNodejsShouldReturn200() throws Exception {
            Map<String, Object> body = new HashMap<>();
            body.put("framework", "express");
            Map<String, Object> packageJson = new HashMap<>();
            packageJson.put("dependencies", Map.of("express", "^4.18.0"));
            packageJson.put("devDependencies", new HashMap<>());
            body.put("package_json", packageJson);

            mockMvc.perform(post("/api/analyze/nodejs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.project_info").exists())
                    .andExpect(jsonPath("$.project_info.language").value("nodejs"));
        }
    }

    @Nested
    @DisplayName("Generate Endpoint")
    class GenerateEndpoint {

        @Test
        @DisplayName("POST /api/generate should return 200 with valid request")
        void generateShouldReturn200() throws Exception {
            Map<String, Object> body = new HashMap<>();

            Map<String, Object> projectInfo = new HashMap<>();
            projectInfo.put("language", "python");
            projectInfo.put("framework", "fastapi");
            projectInfo.put("metadata", Map.of("server", "uvicorn"));
            body.put("project_info", projectInfo);

            Map<String, Object> config = new HashMap<>();
            config.put("port", 8000);
            config.put("runtime_version", "3.11");
            body.put("config", config);

            mockMvc.perform(post("/api/generate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dockerfile").exists())
                    .andExpect(jsonPath("$.session_id").exists());
        }
    }

    @Nested
    @DisplayName("Templates Endpoint")
    class TemplatesEndpoint {

        @Test
        @DisplayName("GET /api/templates should return 200 with template list")
        void templatesShouldReturn200() throws Exception {
            mockMvc.perform(get("/api/templates"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.templates").exists())
                    .andExpect(jsonPath("$.templates.python").exists())
                    .andExpect(jsonPath("$.templates.nodejs").exists())
                    .andExpect(jsonPath("$.templates.java").exists());
        }
    }
}
