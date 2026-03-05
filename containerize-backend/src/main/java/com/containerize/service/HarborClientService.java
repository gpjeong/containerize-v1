package com.containerize.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;
import javax.net.ssl.*;

/**
 * Harbor Registry REST API Client
 * NOT a singleton - create new instance per request
 */
public class HarborClientService {

    private static final Logger logger = LoggerFactory.getLogger(HarborClientService.class);

    private String baseUrl;
    private String apiBase;
    private String authHeader;
    private boolean verifySsl;

    private ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Initialize Harbor client
     *
     * @param harborUrl Harbor base URL (e.g., https://harbor.example.com)
     * @param username Harbor admin username
     * @param password Harbor admin password
     * @param verifySsl Verify SSL certificate (default: False for self-signed)
     */
    public void initialize(String harborUrl, String username, String password, boolean verifySsl) {
        // Remove trailing slash and /api/v2.0 if present
        this.baseUrl = harborUrl.replaceAll("/$", "").replace("/api/v2.0", "");
        this.apiBase = baseUrl + "/api/v2.0";
        this.verifySsl = verifySsl;

        // Create Basic Auth header
        String credentials = username + ":" + password;
        String base64Credentials = Base64.getEncoder()
            .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        this.authHeader = "Basic " + base64Credentials;

        logger.info("Initialized Harbor client for {} (cookieless mode)", baseUrl);
    }

    /**
     * Initialize with default SSL verification (false)
     */
    public void initialize(String harborUrl, String username, String password) {
        initialize(harborUrl, username, password, false);
    }

    /**
     * Open an HTTP(S) connection, applying SSL bypass when verifySsl is false.
     */
    private java.net.HttpURLConnection openConnection(String url) throws Exception {
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                new java.net.URL(url).openConnection();
        if (!verifySsl && conn instanceof HttpsURLConnection httpsConn) {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }}, new SecureRandom());
            httpsConn.setSSLSocketFactory(sc.getSocketFactory());
            httpsConn.setHostnameVerifier((hostname, session) -> true);
        }
        return conn;
    }

    /**
     * Make HTTP request without cookies to bypass CSRF protection
     */
    private java.net.HttpURLConnection makeRequest(String method, String url) throws Exception {
        java.net.HttpURLConnection conn = openConnection(url);
        conn.setRequestMethod(method);
        conn.setRequestProperty("Authorization", authHeader);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        return conn;
    }

    /**
     * Check if Harbor project exists
     *
     * @param projectName Project name to check
     * @return True if project exists, False otherwise
     */
    public boolean checkProjectExists(String projectName) {
        try {
            String url = apiBase + "/projects/" + URLEncoder.encode(projectName, StandardCharsets.UTF_8);
            java.net.HttpURLConnection conn = makeRequest("GET", url);

            int statusCode = conn.getResponseCode();

            if (statusCode == 200) {
                logger.info("Harbor project '{}' exists", projectName);
                return true;
            } else if (statusCode == 404) {
                logger.info("Harbor project '{}' does not exist", projectName);
                return false;
            } else {
                logger.warn("Unexpected status {} checking project", statusCode);
                return false;
            }

        } catch (Exception e) {
            logger.error("Failed to check Harbor project: {}", e.getMessage());
            throw new RuntimeException("Harbor connection failed: " + e.getMessage());
        }
    }

    /**
     * Create new Harbor project
     *
     * @param projectName Project name (lowercase, alphanumeric, - _ allowed)
     * @param isPublic Make project public (default: false - private)
     * @param enableContentTrust Enable Docker Content Trust (image signing)
     * @param autoScan Auto scan images on push (default: true)
     * @param severity Vulnerability severity threshold (critical, high, medium, low)
     * @param preventVul Prevent vulnerable images from running
     * @return Map with project info
     */
    public Map<String, Object> createProject(
        String projectName,
        boolean isPublic,
        boolean enableContentTrust,
        boolean autoScan,
        String severity,
        boolean preventVul
    ) {
        try {
            String url = apiBase + "/projects";

            // Build project metadata
            Map<String, String> metadata = new HashMap<>();
            if (enableContentTrust) {
                metadata.put("enable_content_trust", "true");
            }
            if (autoScan) {
                metadata.put("auto_scan", "true");
            }
            if (severity != null && !severity.isEmpty()) {
                metadata.put("severity", severity);
            }
            if (preventVul) {
                metadata.put("prevent_vul", "true");
            }

            // Build request payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("project_name", projectName);
            payload.put("public", isPublic);

            if (!metadata.isEmpty()) {
                payload.put("metadata", metadata);
            }

            // Convert payload to JSON
            String jsonPayload = objectMapper.writeValueAsString(payload);

            // Make POST request
            java.net.HttpURLConnection conn = openConnection(url);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", authHeader);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);

            conn.getOutputStream().write(jsonPayload.getBytes(StandardCharsets.UTF_8));

            int statusCode = conn.getResponseCode();

            // Log response for debugging
            logger.info("Harbor create project response: status={}", statusCode);
            if (statusCode != 201) {
                try {
                    String response = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                    logger.error("Harbor error response: {}", response);
                } catch (Exception e) {
                    // Ignore error reading error stream
                }
            }

            if (statusCode == 201) {
                // Success - get location header
                String location = conn.getHeaderField("Location");
                logger.info("Harbor project '{}' created successfully", projectName);

                Map<String, Object> result = new HashMap<>();
                result.put("project_name", projectName);
                result.put("location", location);
                result.put("public", isPublic);
                result.put("metadata", metadata);
                return result;

            } else if (statusCode == 409) {
                throw new RuntimeException("Project '" + projectName + "' already exists");

            } else if (statusCode == 400) {
                try {
                    String response = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                    Map<String, Object> errorData = objectMapper.readValue(response, Map.class);
                    @SuppressWarnings("unchecked")
                    List<Map<String, String>> errors = (List<Map<String, String>>) errorData.get("errors");
                    String errorMsg = errors != null && !errors.isEmpty()
                        ? errors.get(0).getOrDefault("message", "Invalid request")
                        : "Invalid request";
                    throw new RuntimeException("Invalid project configuration: " + errorMsg);
                } catch (Exception e) {
                    throw new RuntimeException("Invalid project configuration: " + e.getMessage());
                }

            } else if (statusCode == 401) {
                throw new RuntimeException("Authentication failed. Check Harbor credentials.");

            } else if (statusCode == 403) {
                // Try to get more detailed error message
                try {
                    String response = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                    Map<String, Object> errorData = objectMapper.readValue(response, Map.class);
                    @SuppressWarnings("unchecked")
                    List<Map<String, String>> errors = (List<Map<String, String>>) errorData.get("errors");
                    if (errors != null && !errors.isEmpty()) {
                        String errorMsg = errors.get(0).getOrDefault("message", "");
                        if (!errorMsg.isEmpty()) {
                            logger.error("Harbor 403 error details: {}", errorMsg);
                            throw new RuntimeException("Permission denied: " + errorMsg);
                        }
                    }
                } catch (Exception e) {
                    // Ignore error parsing details
                }
                throw new RuntimeException("Permission denied. User must be Harbor admin with 'Project Admin' or 'System Admin' role.");

            } else {
                throw new RuntimeException("Project creation failed with status " + statusCode);
            }

        } catch (Exception e) {
            logger.error("Failed to create Harbor project: {}", e.getMessage());
            throw new RuntimeException("Harbor API error: " + e.getMessage());
        }
    }

    /**
     * Get detailed project information
     *
     * @param projectName Project name
     * @return Project info map or null if not found
     */
    public Map<String, Object> getProjectInfo(String projectName) {
        try {
            String url = apiBase + "/projects/" + URLEncoder.encode(projectName, StandardCharsets.UTF_8);
            java.net.HttpURLConnection conn = makeRequest("GET", url);

            if (conn.getResponseCode() == 200) {
                String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                @SuppressWarnings("unchecked")
                Map<String, Object> projectInfo = objectMapper.readValue(response, Map.class);
                return projectInfo;
            } else {
                return null;
            }

        } catch (Exception e) {
            logger.error("Failed to get project info: {}", e.getMessage());
            return null;
        }
    }

    /**
     * List all Harbor projects
     *
     * @param page Page number (default: 1)
     * @param pageSize Items per page (default: 10)
     * @return List of project maps
     */
    public List<Map<String, Object>> listProjects(int page, int pageSize) {
        try {
            String url = String.format("%s/projects?page=%d&page_size=%d", apiBase, page, pageSize);
            java.net.HttpURLConnection conn = makeRequest("GET", url);

            if (conn.getResponseCode() == 200) {
                String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> projects = objectMapper.readValue(response, List.class);
                return projects;
            } else {
                return new ArrayList<>();
            }

        } catch (Exception e) {
            logger.error("Failed to list projects: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
