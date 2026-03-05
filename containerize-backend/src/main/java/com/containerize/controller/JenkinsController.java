package com.containerize.controller;

import com.containerize.dto.request.JenkinsBuildRequest;
import com.containerize.dto.request.ProjectInfo;
import com.containerize.dto.response.JenkinsBuildResponse;
import com.containerize.service.DockerfileGeneratorService;
import com.containerize.service.JenkinsClientService;
import com.containerize.service.PipelineGeneratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class JenkinsController {

    private static final Logger logger = LoggerFactory.getLogger(JenkinsController.class);

    @Autowired
    private DockerfileGeneratorService dockerfileGeneratorService;

    @Autowired
    private PipelineGeneratorService pipelineGeneratorService;

    /**
     * Preview Jenkins Pipeline script without triggering build
     *
     * Generates the Pipeline script that would be used for Jenkins build
     * Returns the Groovy script for preview/editing
     */
    @PostMapping("/preview/pipeline")
    public ResponseEntity<Map<String, String>> previewPipeline(@RequestBody JenkinsBuildRequest request) {
        try {
            logger.info("Generating pipeline preview for {}", request.getConfig().get("language"));

            // Generate Dockerfile
            ProjectInfo projectInfo = new ProjectInfo();
            projectInfo.setLanguage((String) request.getConfig().get("language"));
            projectInfo.setFramework((String) request.getConfig().getOrDefault("framework", "generic"));
            projectInfo.setDetectedVersion((String) request.getConfig().get("runtime_version"));

            String dockerfileContent = dockerfileGeneratorService.generate(
                    projectInfo,
                    request.getConfig()
            );

            // Generate Pipeline script for preview (with readable Dockerfile)
            String pipelineScript;

            if (request.isUseKubernetes() && request.isUseKaniko()) {
                // Kubernetes with Kaniko (no privileged mode)
                pipelineScript = pipelineGeneratorService.generateK8sKanikoPipelineScriptForPreview(
                        request.getGitUrl(),
                        request.getGitBranch(),
                        request.getGitCredentialId(),
                        dockerfileContent,
                        request.getImageName(),
                        request.getImageTag(),
                        request.getHarborUrl(),
                        request.getHarborCredentialId()
                );
            } else if (request.isUseKubernetes()) {
                // Kubernetes with Docker-in-Docker
                pipelineScript = pipelineGeneratorService.generateK8sPipelineScriptForPreview(
                        request.getGitUrl(),
                        request.getGitBranch(),
                        request.getGitCredentialId(),
                        dockerfileContent,
                        request.getImageName(),
                        request.getImageTag()
                );
            } else {
                // Standard pipeline
                pipelineScript = pipelineGeneratorService.generatePipelineScriptForPreview(
                        request.getGitUrl(),
                        request.getGitBranch(),
                        request.getGitCredentialId(),
                        dockerfileContent,
                        request.getImageName(),
                        request.getImageTag()
                );
            }

            Map<String, String> result = new HashMap<>();
            result.put("pipeline_script", pipelineScript);
            result.put("dockerfile", dockerfileContent);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("Failed to generate pipeline preview: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Preview generation failed: " + e.getMessage());
        }
    }

    /**
     * Trigger Jenkins build with auto-generated Pipeline script
     *
     * Workflow:
     * 1. Generate Dockerfile from config
     * 2. Generate Jenkins Pipeline script
     * 3. Update Jenkins job with new Pipeline script
     * 4. Trigger build
     */
    @PostMapping("/build/jenkins")
    public ResponseEntity<JenkinsBuildResponse> triggerJenkinsBuild(@RequestBody JenkinsBuildRequest request) {
        try {
            logger.info("Jenkins build request for job: {}", request.getJenkinsJob());

            // 1. Generate Dockerfile
            ProjectInfo projectInfo = new ProjectInfo();
            projectInfo.setLanguage((String) request.getConfig().get("language"));
            projectInfo.setFramework((String) request.getConfig().getOrDefault("framework", "generic"));
            projectInfo.setDetectedVersion((String) request.getConfig().get("runtime_version"));

            String dockerfileContent = dockerfileGeneratorService.generate(
                    projectInfo,
                    request.getConfig()
            );

            logger.info("Generated Dockerfile for {}/{}", projectInfo.getLanguage(), projectInfo.getFramework());

            // 2. Generate Pipeline script (Kubernetes or standard)
            String pipelineScript;

            if (request.isUseKubernetes() && request.isUseKaniko()) {
                pipelineScript = pipelineGeneratorService.generateK8sKanikoPipelineScript(
                        request.getGitUrl(),
                        request.getGitBranch(),
                        request.getGitCredentialId(),
                        dockerfileContent,
                        request.getImageName(),
                        request.getImageTag(),
                        request.getHarborUrl(),
                        request.getHarborCredentialId()
                );
            } else if (request.isUseKubernetes()) {
                pipelineScript = pipelineGeneratorService.generateK8sPipelineScript(
                        request.getGitUrl(),
                        request.getGitBranch(),
                        request.getGitCredentialId(),
                        dockerfileContent,
                        request.getImageName(),
                        request.getImageTag()
                );
            } else {
                pipelineScript = pipelineGeneratorService.generatePipelineScript(
                        request.getGitUrl(),
                        request.getGitBranch(),
                        request.getGitCredentialId(),
                        dockerfileContent,
                        request.getImageName(),
                        request.getImageTag()
                );
            }

            logger.info("Generated Pipeline script for image: {}:{}", request.getImageName(), request.getImageTag());

            // 3. Create Jenkins client and update + trigger build
            JenkinsClientService jenkinsClient = new JenkinsClientService();
            jenkinsClient.initialize(
                    request.getJenkinsUrl(),
                    request.getJenkinsUsername(),
                    request.getJenkinsToken()
            );

            // Update Pipeline script and trigger build
            Map<String, Object> buildInfo = jenkinsClient.updateAndBuild(
                    request.getJenkinsJob(),
                    pipelineScript
            );

            logger.info("Jenkins build triggered successfully. Queue ID: {}, Build Number: {}",
                    buildInfo.get("queue_id"), buildInfo.get("build_number"));

            JenkinsBuildResponse response = new JenkinsBuildResponse();
            response.setJobName((String) buildInfo.get("job_name"));
            response.setQueueId(buildInfo.get("queue_id") != null ? String.valueOf(buildInfo.get("queue_id")) : null);
            response.setQueueUrl((String) buildInfo.get("queue_url"));
            response.setJobUrl((String) buildInfo.get("job_url"));
            response.setBuildNumber(buildInfo.get("build_number") != null ? ((Number) buildInfo.get("build_number")).intValue() : null);
            response.setBuildUrl((String) buildInfo.get("build_url"));
            response.setStatus((String) buildInfo.get("status"));
            response.setMessage("Jenkins build triggered successfully");

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid configuration: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to trigger Jenkins build: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Jenkins build failed: " + e.getMessage());
        }
    }

    /**
     * Trigger Jenkins build with custom/edited Pipeline script
     *
     * This endpoint allows users to build with an edited pipeline script
     * from the preview modal
     */
    @PostMapping("/build/jenkins/custom")
    public ResponseEntity<JenkinsBuildResponse> triggerJenkinsBuildCustom(@RequestBody Map<String, Object> request) {
        try {
            String jenkinsUrl = (String) request.get("jenkins_url");
            String jenkinsJob = (String) request.get("jenkins_job");
            String jenkinsToken = (String) request.get("jenkins_token");
            String jenkinsUsername = (String) request.getOrDefault("jenkins_username", "admin");
            String pipelineScript = (String) request.get("pipeline_script");

            logger.info("Custom Jenkins build request for job: {}", jenkinsJob);

            // Create Jenkins client
            JenkinsClientService jenkinsClient = new JenkinsClientService();
            jenkinsClient.initialize(jenkinsUrl, jenkinsUsername, jenkinsToken);

            // Update Pipeline script and trigger build
            Map<String, Object> buildInfo = jenkinsClient.updateAndBuild(jenkinsJob, pipelineScript);

            logger.info("Custom Jenkins build triggered. Build Number: {}", buildInfo.get("build_number"));

            JenkinsBuildResponse response = new JenkinsBuildResponse();
            response.setJobName((String) buildInfo.get("job_name"));
            response.setQueueId(buildInfo.get("queue_id") != null ? String.valueOf(buildInfo.get("queue_id")) : null);
            response.setQueueUrl((String) buildInfo.get("queue_url"));
            response.setJobUrl((String) buildInfo.get("job_url"));
            response.setBuildNumber(buildInfo.get("build_number") != null ? ((Number) buildInfo.get("build_number")).intValue() : null);
            response.setBuildUrl((String) buildInfo.get("build_url"));
            response.setStatus((String) buildInfo.get("status"));
            response.setMessage("Jenkins build triggered with custom pipeline");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to trigger custom Jenkins build: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Jenkins build failed: " + e.getMessage());
        }
    }

    /**
     * Check if Jenkins job exists
     */
    @PostMapping("/setup/jenkins/check-job")
    public ResponseEntity<Map<String, Object>> checkJenkinsJob(@RequestBody Map<String, String> request) {
        try {
            String jenkinsUrl = request.get("jenkins_url");
            String jenkinsUsername = request.get("jenkins_username");
            String jenkinsToken = request.get("jenkins_token");
            String jobName = request.get("job_name");

            JenkinsClientService jenkinsClient = new JenkinsClientService();
            jenkinsClient.initialize(jenkinsUrl, jenkinsUsername, jenkinsToken);

            boolean exists = jenkinsClient.checkJobExists(jobName);

            Map<String, Object> response = new HashMap<>();
            response.put("exists", exists);
            response.put("job_name", jobName);

            if (exists) {
                response.put("job_url", jenkinsUrl + "/job/" + jobName);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to check Jenkins job: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /**
     * Create new Jenkins Pipeline job
     */
    @PostMapping("/setup/jenkins/create-job")
    public ResponseEntity<Map<String, Object>> createJenkinsJob(@RequestBody Map<String, String> request) {
        try {
            String jenkinsUrl = request.get("jenkins_url");
            String jenkinsUsername = request.get("jenkins_username");
            String jenkinsToken = request.get("jenkins_token");
            String jobName = request.get("job_name");
            String description = request.getOrDefault("description", "Auto-generated Pipeline job for containerization");

            JenkinsClientService jenkinsClient = new JenkinsClientService();
            jenkinsClient.initialize(jenkinsUrl, jenkinsUsername, jenkinsToken);

            // Check if already exists
            if (jenkinsClient.checkJobExists(jobName)) {
                Map<String, Object> response = new HashMap<>();
                response.put("job_name", jobName);
                response.put("job_url", jenkinsUrl + "/job/" + jobName);
                response.put("status", "already_exists");
                response.put("message", "Job '" + jobName + "' already exists");
                return ResponseEntity.ok(response);
            }

            // Create job
            Map<String, Object> result = jenkinsClient.createJob(jobName, description);
            result.put("message", "Job '" + jobName + "' created successfully");

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            logger.error("Job creation failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to create Jenkins job: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }
}
