package com.containerize.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.*;

/**
 * Client for interacting with Jenkins REST API
 * NOT a singleton - create new instance per request
 */
public class JenkinsClientService {

    private static final Logger logger = LoggerFactory.getLogger(JenkinsClientService.class);

    private String baseUrl;
    private String authHeader;
    private boolean verifySsl;
    private Map<String, String> crumb;
    private org.springframework.web.client.RestTemplate restTemplate;

    /**
     * Initialize Jenkins client
     *
     * @param jenkinsUrl Jenkins server URL (e.g., http://jenkins.example.com:8080)
     * @param username Jenkins username
     * @param apiToken Jenkins API token
     * @param verifySsl Whether to verify SSL certificates
     */
    public void initialize(String jenkinsUrl, String username, String apiToken, boolean verifySsl) {
        this.baseUrl = jenkinsUrl.replaceAll("/$", "");
        this.verifySsl = verifySsl;

        // Create Basic Auth header
        String credentials = username + ":" + apiToken;
        String base64Credentials = Base64.getEncoder()
            .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        this.authHeader = "Basic " + base64Credentials;

        if (!verifySsl) {
            logger.warn("SSL certificate verification is disabled. This is insecure in production!");
        }

        // Get Jenkins crumb for CSRF protection
        this.crumb = getCrumb();

        logger.info("JenkinsClientService initialized for: {}", jenkinsUrl);
    }

    /**
     * Initialize with default SSL verification (false)
     */
    public void initialize(String jenkinsUrl, String username, String apiToken) {
        initialize(jenkinsUrl, username, apiToken, false);
    }

    /**
     * Get Jenkins crumb for CSRF protection
     *
     * @return Crumb header map or null if crumb is not required
     */
    public Map<String, String> getCrumb() {
        try {
            String crumbUrl = baseUrl + "/crumbIssuer/api/json";
            logger.info("Attempting to get Jenkins crumb from: {}", crumbUrl);

            // Use a simple HTTP GET with auth
            java.net.HttpURLConnection conn = openConnection(crumbUrl);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", authHeader);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            int statusCode = conn.getResponseCode();

            if (statusCode == 404) {
                logger.info("Jenkins CSRF protection is not enabled (no crumb required)");
                return null;
            }

            if (statusCode == 200) {
                // Parse JSON response
                String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, Object> crumbData = parseJsonResponse(response);

                String crumbHeader = crumbData.getOrDefault("crumbRequestField", "Jenkins-Crumb").toString();
                String crumbValue = crumbData.getOrDefault("crumb", "").toString();

                Map<String, String> crumb = new HashMap<>();
                crumb.put(crumbHeader, crumbValue);

                logger.info("Retrieved Jenkins crumb successfully");
                return crumb;
            }

            logger.warn("Unexpected status code {} getting crumb", statusCode);
            return null;

        } catch (Exception e) {
            logger.warn("Failed to get Jenkins crumb: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Check if Jenkins job exists
     *
     * @param jobName Job name to check
     * @return True if job exists, False otherwise
     */
    public boolean checkJobExists(String jobName) {
        try {
            String url = baseUrl + "/job/" + URLEncoder.encode(jobName, StandardCharsets.UTF_8) + "/api/json";
            java.net.HttpURLConnection conn = openConnection(url);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", authHeader);
            conn.setConnectTimeout(10000);

            int statusCode = conn.getResponseCode();

            if (statusCode == 200) {
                logger.info("Jenkins job '{}' exists", jobName);
                return true;
            } else if (statusCode == 404) {
                logger.info("Jenkins job '{}' does not exist", jobName);
                return false;
            } else {
                logger.warn("Unexpected status {} checking job", statusCode);
                return false;
            }

        } catch (Exception e) {
            logger.error("Failed to check Jenkins job: {}", e.getMessage());
            throw new RuntimeException("Jenkins connection failed: " + e.getMessage());
        }
    }

    /**
     * Create new Pipeline job in Jenkins
     *
     * @param jobName Job name
     * @param description Job description
     * @return Map with job info
     */
    public Map<String, Object> createJob(String jobName, String description) {
        try {
            String emptyPipelineScript = """
                pipeline {
                    agent any
                    stages {
                        stage('Example') {
                            steps {
                                echo 'Pipeline will be auto-generated'
                            }
                        }
                    }
                }
                """;

            String configXml = String.format("""
                <?xml version='1.1' encoding='UTF-8'?>
                <flow-definition plugin="workflow-job@2.40">
                  <description>%s</description>
                  <keepDependencies>false</keepDependencies>
                  <properties>
                    <hudson.model.ParametersDefinitionProperty>
                      <parameterDefinitions>
                        <hudson.model.StringParameterDefinition>
                          <name>IMAGE_NAME</name>
                          <description>Docker image name</description>
                          <defaultValue>myapp</defaultValue>
                          <trim>true</trim>
                        </hudson.model.StringParameterDefinition>
                        <hudson.model.StringParameterDefinition>
                          <name>IMAGE_TAG</name>
                          <description>Docker image tag</description>
                          <defaultValue>latest</defaultValue>
                          <trim>true</trim>
                        </hudson.model.StringParameterDefinition>
                      </parameterDefinitions>
                    </hudson.model.ParametersDefinitionProperty>
                  </properties>
                  <definition class="org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition" plugin="workflow-cps@2.90">
                    <script><![CDATA[%s]]></script>
                    <sandbox>true</sandbox>
                  </definition>
                  <triggers/>
                  <disabled>false</disabled>
                </flow-definition>""", description, emptyPipelineScript);

            String createUrl = baseUrl + "/createItem?name=" + URLEncoder.encode(jobName, StandardCharsets.UTF_8);

            java.net.HttpURLConnection conn = openConnection(createUrl);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", authHeader);
            conn.setRequestProperty("Content-Type", "application/xml");

            if (crumb != null) {
                crumb.forEach(conn::setRequestProperty);
            }

            conn.setDoOutput(true);
            conn.getOutputStream().write(configXml.getBytes(StandardCharsets.UTF_8));

            int statusCode = conn.getResponseCode();

            if (statusCode == 200) {
                logger.info("Jenkins job '{}' created successfully", jobName);
                Map<String, Object> result = new HashMap<>();
                result.put("job_name", jobName);
                result.put("job_url", baseUrl + "/job/" + jobName);
                result.put("status", "created");
                return result;
            } else if (statusCode == 400) {
                throw new RuntimeException("Job '" + jobName + "' already exists");
            } else if (statusCode == 401) {
                throw new RuntimeException("Authentication failed. Check Jenkins credentials.");
            } else if (statusCode == 403) {
                throw new RuntimeException("Permission denied. User needs Job/Create permission.");
            } else {
                throw new RuntimeException("Job creation failed with status " + statusCode);
            }

        } catch (Exception e) {
            logger.error("Failed to create Jenkins job: {}", e.getMessage());
            throw new RuntimeException("Jenkins API error: " + e.getMessage());
        }
    }

    /**
     * Update Jenkins Pipeline Job's script
     *
     * @param jobName Jenkins job name
     * @param pipelineScript Groovy pipeline script content
     * @return Success status
     */
    public boolean updatePipelineScript(String jobName, String pipelineScript) {
        try {
            String configUrl = baseUrl + "/job/" + URLEncoder.encode(jobName, StandardCharsets.UTF_8) + "/config.xml";
            logger.info("Updating job config at: {}", configUrl);

            // Create complete config XML with pipeline script
            String configXml = createPipelineConfigXml(pipelineScript);

            java.net.HttpURLConnection conn = openConnection(configUrl);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", authHeader);
            conn.setRequestProperty("Content-Type", "application/xml");

            if (crumb != null) {
                crumb.forEach(conn::setRequestProperty);
            }

            conn.setDoOutput(true);
            conn.getOutputStream().write(configXml.getBytes(StandardCharsets.UTF_8));

            int statusCode = conn.getResponseCode();

            if (statusCode == 200) {
                logger.info("Successfully updated job config for: {}", jobName);
                return true;
            } else if (statusCode == 404) {
                throw new RuntimeException("Jenkins job '" + jobName + "' not found. Please create the job first.");
            } else if (statusCode == 500) {
                throw new RuntimeException("Jenkins server error (500). Check Jenkins logs for details.");
            } else {
                throw new RuntimeException("Failed to update pipeline with status: " + statusCode);
            }

        } catch (Exception e) {
            logger.error("Failed to update pipeline script: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Trigger a build for the given job
     *
     * @param jobName Jenkins job name
     * @return Build information including queue ID, build number and build URL
     */
    public Map<String, Object> triggerBuild(String jobName) {
        try {
            String buildUrl = baseUrl + "/job/" + URLEncoder.encode(jobName, StandardCharsets.UTF_8) + "/build";
            logger.info("Triggering build for job: {}", jobName);

            java.net.HttpURLConnection conn = openConnection(buildUrl);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", authHeader);

            if (crumb != null) {
                crumb.forEach(conn::setRequestProperty);
            }

            int statusCode = conn.getResponseCode();

            if (statusCode != 201 && statusCode != 200) {
                throw new RuntimeException("Failed to trigger build: " + statusCode);
            }

            // Get queue item location from response header
            String queueLocation = conn.getHeaderField("Location");
            String queueId = null;
            if (queueLocation != null && !queueLocation.isEmpty()) {
                String[] parts = queueLocation.split("/");
                if (parts.length >= 2) {
                    queueId = parts[parts.length - 2];
                }
            }

            // Try to get build number from queue
            Integer buildNumber = null;
            if (queueId != null) {
                buildNumber = getBuildNumberFromQueue(queueId, 15);
            }

            // If still no build number, try getting the latest build number
            if (buildNumber == null) {
                logger.info("Attempting to get latest build number for {}", jobName);
                buildNumber = getLatestBuildNumber(jobName);
            }

            Map<String, Object> buildInfo = new HashMap<>();
            buildInfo.put("job_name", jobName);
            buildInfo.put("queue_id", queueId);
            buildInfo.put("queue_url", queueLocation);
            buildInfo.put("job_url", baseUrl + "/job/" + jobName);
            buildInfo.put("build_number", buildNumber);
            buildInfo.put("build_url", buildNumber != null
                ? baseUrl + "/job/" + jobName + "/" + buildNumber
                : null);
            buildInfo.put("status", buildNumber != null ? "BUILDING" : "QUEUED");

            logger.info("Build triggered successfully. Queue ID: {}, Build Number: {}", queueId, buildNumber);
            return buildInfo;

        } catch (Exception e) {
            logger.error("Failed to trigger build: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Update pipeline script and trigger build in one operation
     *
     * @param jobName Jenkins job name
     * @param pipelineScript Groovy pipeline script content
     * @return Build information
     */
    public Map<String, Object> updateAndBuild(String jobName, String pipelineScript) {
        // Update pipeline script
        updatePipelineScript(jobName, pipelineScript);

        // Trigger build
        return triggerBuild(jobName);
    }

    /**
     * Get build number from queue ID by polling
     *
     * @param queueId Jenkins queue item ID
     * @param timeoutSeconds Maximum seconds to wait for build number
     * @return Build number if found, null otherwise
     */
    public Integer getBuildNumberFromQueue(String queueId, int timeoutSeconds) {
        if (queueId == null || queueId.isEmpty()) {
            return null;
        }

        try {
            String queueApiUrl = baseUrl + "/queue/item/" + queueId + "/api/json";
            logger.info("Polling queue item {} for build number...", queueId);

            long startTime = System.currentTimeMillis();
            long pollInterval = 500; // Poll every 0.5 seconds
            long timeoutMs = timeoutSeconds * 1000L;

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                java.net.HttpURLConnection conn = openConnection(queueApiUrl);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", authHeader);
                conn.setConnectTimeout(5000);

                int statusCode = conn.getResponseCode();

                if (statusCode == 404) {
                    logger.warn("Queue item {} not found (might have completed quickly)", queueId);
                    return null;
                }

                if (statusCode == 200) {
                    String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    Map<String, Object> data = parseJsonResponse(response);

                    if (data.containsKey("executable")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> executable = (Map<String, Object>) data.get("executable");
                        if (executable != null && executable.containsKey("number")) {
                            Integer buildNumber = (Integer) executable.get("number");
                            logger.info("Found build number: {} for queue ID: {}", buildNumber, queueId);
                            return buildNumber;
                        }
                    }
                }

                // Wait before next poll
                Thread.sleep(pollInterval);
            }

            logger.warn("Timeout waiting for build number for queue ID: {}", queueId);
            return null;

        } catch (Exception e) {
            logger.error("Failed to get build number from queue: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get the latest build number for a job
     *
     * @param jobName Jenkins job name
     * @return Latest build number if found, null otherwise
     */
    public Integer getLatestBuildNumber(String jobName) {
        try {
            String jobApiUrl = baseUrl + "/job/" + URLEncoder.encode(jobName, StandardCharsets.UTF_8) + "/api/json";
            java.net.HttpURLConnection conn = openConnection(jobApiUrl);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", authHeader);
            conn.setConnectTimeout(5000);

            if (conn.getResponseCode() == 200) {
                String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, Object> data = parseJsonResponse(response);

                if (data.containsKey("lastBuild")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> lastBuild = (Map<String, Object>) data.get("lastBuild");
                    if (lastBuild != null && lastBuild.containsKey("number")) {
                        Integer buildNumber = (Integer) lastBuild.get("number");
                        logger.info("Latest build number for {}: {}", jobName, buildNumber);
                        return buildNumber;
                    }
                }
            }

            return null;

        } catch (Exception e) {
            logger.error("Failed to get latest build number: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Create a complete Pipeline job config.xml
     *
     * @param pipelineScript Groovy pipeline script content
     * @return Complete Jenkins Pipeline job config XML
     */
    public String createPipelineConfigXml(String pipelineScript) {
        return String.format("""
            <?xml version='1.1' encoding='UTF-8'?>
            <flow-definition plugin="workflow-job@2.40">
              <actions/>
              <description>Auto-generated Pipeline for Docker image build</description>
              <keepDependencies>false</keepDependencies>
              <properties/>
              <definition class="org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition" plugin="workflow-cps@2.90">
                <script><![CDATA[%s]]></script>
                <sandbox>true</sandbox>
              </definition>
              <triggers/>
              <disabled>false</disabled>
            </flow-definition>""", pipelineScript);
    }

    /**
     * Build headers map with Authorization and Crumb
     */
    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", authHeader);
        if (crumb != null) {
            headers.putAll(crumb);
        }
        return headers;
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
     * Simple JSON response parser
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonResponse(String json) {
        Map<String, Object> result = new HashMap<>();

        // Simple regex-based JSON parsing for crumb and build info
        Pattern numberPattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\\d+)");
        Matcher numberMatcher = numberPattern.matcher(json);
        while (numberMatcher.find()) {
            result.put(numberMatcher.group(1), Integer.parseInt(numberMatcher.group(2)));
        }

        Pattern stringPattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");
        Matcher stringMatcher = stringPattern.matcher(json);
        while (stringMatcher.find()) {
            result.put(stringMatcher.group(1), stringMatcher.group(2));
        }

        // Handle nested objects (simplified)
        if (json.contains("\"executable\"")) {
            Map<String, Object> executable = new HashMap<>();
            Pattern execPattern = Pattern.compile("\"executable\"\\s*:\\s*\\{([^}]*)\\}");
            Matcher execMatcher = execPattern.matcher(json);
            if (execMatcher.find()) {
                String execContent = execMatcher.group(1);
                Pattern execNumPattern = Pattern.compile("\"number\"\\s*:\\s*(\\d+)");
                Matcher execNumMatcher = execNumPattern.matcher(execContent);
                if (execNumMatcher.find()) {
                    executable.put("number", Integer.parseInt(execNumMatcher.group(1)));
                }
                result.put("executable", executable);
            }
        }

        if (json.contains("\"lastBuild\"")) {
            Map<String, Object> lastBuild = new HashMap<>();
            Pattern lastPattern = Pattern.compile("\"lastBuild\"\\s*:\\s*\\{([^}]*)\\}");
            Matcher lastMatcher = lastPattern.matcher(json);
            if (lastMatcher.find()) {
                String lastContent = lastMatcher.group(1);
                Pattern lastNumPattern = Pattern.compile("\"number\"\\s*:\\s*(\\d+)");
                Matcher lastNumMatcher = lastNumPattern.matcher(lastContent);
                if (lastNumMatcher.find()) {
                    lastBuild.put("number", Integer.parseInt(lastNumMatcher.group(1)));
                }
                result.put("lastBuild", lastBuild);
            }
        }

        return result;
    }
}
