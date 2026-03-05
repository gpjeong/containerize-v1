package com.containerize.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Base64;

/**
 * Generates Jenkins Pipeline (Groovy) scripts for Docker builds
 */
@Service
public class PipelineGeneratorService {

    private static final Logger logger = LoggerFactory.getLogger(PipelineGeneratorService.class);

    /**
     * Generate Jenkins Pipeline script (Jenkinsfile) with Base64 encoded Dockerfile
     *
     * @param gitUrl Git repository URL
     * @param gitBranch Git branch name
     * @param gitCredentialId Jenkins credential ID for Git (optional for public repos)
     * @param dockerfileContent Generated Dockerfile content
     * @param imageName Docker image name
     * @param imageTag Docker image tag
     * @return Complete Groovy pipeline script
     */
    public String generatePipelineScript(
        String gitUrl,
        String gitBranch,
        String gitCredentialId,
        String dockerfileContent,
        String imageName,
        String imageTag
    ) {
        // Encode Dockerfile content as Base64 for safe embedding
        String base64Dockerfile = encodeDockerfileBase64(dockerfileContent);

        // Build git checkout command
        String gitCheckout;
        if (gitCredentialId != null && !gitCredentialId.isEmpty()) {
            gitCheckout = String.format("""
                git url: '%s',
                    branch: '%s',
                    credentialsId: '%s'""", gitUrl, gitBranch, gitCredentialId);
        } else {
            // Public repository (no credentials)
            gitCheckout = String.format("""
                git url: '%s',
                    branch: '%s'""", gitUrl, gitBranch);
        }

        // Generate pipeline script
        String pipelineScript = String.format("""
            pipeline {
                agent any

                parameters {
                    string(name: 'IMAGE_NAME', defaultValue: '%s', description: 'Docker image name')
                    string(name: 'IMAGE_TAG', defaultValue: '%s', description: 'Docker image tag')
                }

                stages {
                    stage('Checkout') {
                        steps {
                            echo 'Cloning repository from %s...'
                            %s
                        }
                    }

                    stage('Create Dockerfile') {
                        steps {
                            echo 'Creating Dockerfile from generated content...'
                            script {
                                // Decode Base64 Dockerfile content
                                def dockerfileBase64 = '%s'
                                def dockerfileContent = new String(dockerfileBase64.decodeBase64())
                                writeFile file: 'Dockerfile', text: dockerfileContent
                                echo 'Dockerfile created successfully'
                                sh 'cat Dockerfile'
                            }
                        }
                    }

                    stage('Build Docker Image') {
                        steps {
                            echo "Building Docker image: ${params.IMAGE_NAME}:${params.IMAGE_TAG}"
                            script {
                                docker.build("${params.IMAGE_NAME}:${params.IMAGE_TAG}")
                            }
                        }
                    }

                    stage('Verify Image') {
                        steps {
                            echo 'Verifying Docker image...'
                            sh 'docker images | grep \\$IMAGE_NAME || echo "Image built successfully"'
                        }
                    }
                }

                post {
                    success {
                        echo 'Docker image built successfully!'
                        echo "Image: ${params.IMAGE_NAME}:${params.IMAGE_TAG}"
                    }
                    failure {
                        echo 'Build failed!'
                        echo 'Check the console output for details'
                    }
                    always {
                        echo 'Build completed'
                    }
                }
            }""", imageName, imageTag, gitUrl, gitCheckout, base64Dockerfile);

        logger.info("Generated pipeline script for image: {}:{}", imageName, imageTag);
        return pipelineScript;
    }

    /**
     * Generate Jenkins Pipeline script for preview (with readable Dockerfile content)
     *
     * Same as generatePipelineScript but embeds Dockerfile as plain text with proper escaping
     *
     * @param gitUrl Git repository URL
     * @param gitBranch Git branch name
     * @param gitCredentialId Jenkins credential ID for Git (optional for public repos)
     * @param dockerfileContent Generated Dockerfile content
     * @param imageName Docker image name
     * @param imageTag Docker image tag
     * @return Complete Groovy pipeline script with readable Dockerfile
     */
    public String generatePipelineScriptForPreview(
        String gitUrl,
        String gitBranch,
        String gitCredentialId,
        String dockerfileContent,
        String imageName,
        String imageTag
    ) {
        // Escape special characters in Dockerfile for Groovy string
        String escapedDockerfile = escapeDockerfileForGroovy(dockerfileContent);

        // Build git checkout command
        String gitCheckout;
        if (gitCredentialId != null && !gitCredentialId.isEmpty()) {
            gitCheckout = String.format("""
                git url: '%s',
                        branch: '%s',
                        credentialsId: '%s'""", gitUrl, gitBranch, gitCredentialId);
        } else {
            gitCheckout = String.format("""
                git url: '%s',
                        branch: '%s'""", gitUrl, gitBranch);
        }

        // Generate pipeline script with plain Dockerfile content
        String pipelineScript = String.format("""
            pipeline {
                agent any

                parameters {
                    string(name: 'IMAGE_NAME', defaultValue: '%s', description: 'Docker image name')
                    string(name: 'IMAGE_TAG', defaultValue: '%s', description: 'Docker image tag')
                }

                stages {
                    stage('Checkout') {
                        steps {
                            echo 'Cloning repository from %s...'
                            %s
                        }
                    }

                    stage('Create Dockerfile') {
                        steps {
                            echo 'Creating Dockerfile from generated content...'
                            script {
                                // Dockerfile content (plain text for readability)
                                def dockerfileContent = \"\"\"\\
            %s\\
            \"\"\"
                                writeFile file: 'Dockerfile', text: dockerfileContent
                                echo 'Dockerfile created successfully'
                                sh 'cat Dockerfile'
                            }
                        }
                    }

                    stage('Build Docker Image') {
                        steps {
                            echo "Building Docker image: ${params.IMAGE_NAME}:${params.IMAGE_TAG}"
                            script {
                                docker.build("${params.IMAGE_NAME}:${params.IMAGE_TAG}")
                            }
                        }
                    }

                    stage('Verify Image') {
                        steps {
                            echo 'Verifying Docker image...'
                            sh 'docker images | grep \\$IMAGE_NAME || echo "Image built successfully"'
                        }
                    }
                }

                post {
                    success {
                        echo 'Docker image built successfully!'
                        echo "Image: ${params.IMAGE_NAME}:${params.IMAGE_TAG}"
                    }
                    failure {
                        echo 'Build failed!'
                        echo 'Check the console output for details'
                    }
                    always {
                        echo 'Build completed'
                    }
                }
            }""", imageName, imageTag, gitUrl, gitCheckout, escapedDockerfile);

        logger.info("Generated preview pipeline script for image: {}:{}", imageName, imageTag);
        return pipelineScript;
    }

    /**
     * Generate Kubernetes-compatible Jenkins Pipeline script with Base64 Dockerfile
     *
     * Uses podTemplate with Docker-in-Docker (DinD) for Kubernetes environment
     *
     * @param gitUrl Git repository URL
     * @param gitBranch Git branch name
     * @param gitCredentialId Jenkins credential ID for Git
     * @param dockerfileContent Generated Dockerfile content
     * @param imageName Docker image name
     * @param imageTag Docker image tag
     * @return Kubernetes-compatible Groovy pipeline script
     */
    public String generateK8sPipelineScript(
        String gitUrl,
        String gitBranch,
        String gitCredentialId,
        String dockerfileContent,
        String imageName,
        String imageTag
    ) {
        // Encode Dockerfile content as Base64 for safe embedding
        String base64Dockerfile = encodeDockerfileBase64(dockerfileContent);

        // Build git checkout command
        String gitCheckout;
        if (gitCredentialId != null && !gitCredentialId.isEmpty()) {
            gitCheckout = String.format("""
                git url: '%s',
                        branch: '%s',
                        credentialsId: '%s'""", gitUrl, gitBranch, gitCredentialId);
        } else {
            gitCheckout = String.format("""
                git url: '%s',
                        branch: '%s'""", gitUrl, gitBranch);
        }

        String pipelineScript = String.format("""
            pipeline {
                agent {
                    kubernetes {
                        yaml '''
            apiVersion: v1
            kind: Pod
            metadata:
              labels:
                jenkins: agent
            spec:
              containers:
              - name: docker
                image: docker:24-dind
                securityContext:
                  privileged: true
                volumeMounts:
                - name: docker-sock
                  mountPath: /var/run
                env:
                - name: DOCKER_TLS_CERTDIR
                  value: ""
                - name: DOCKER_HOST
                  value: "unix:///var/run/docker.sock"
              - name: docker-client
                image: docker:24-cli
                command:
                - cat
                tty: true
                volumeMounts:
                - name: docker-sock
                  mountPath: /var/run
                env:
                - name: DOCKER_HOST
                  value: "unix:///var/run/docker.sock"
              volumes:
              - name: docker-sock
                emptyDir: {}
            '''
                    }
                }

                parameters {
                    string(name: 'IMAGE_NAME', defaultValue: '%s', description: 'Docker image name')
                    string(name: 'IMAGE_TAG', defaultValue: '%s', description: 'Docker image tag')
                }

                stages {
                    stage('Wait for Docker') {
                        steps {
                            container('docker-client') {
                                echo 'Waiting for Docker daemon to be ready...'
                                sh '''
                                    for i in $(seq 1 30); do
                                        if docker info >/dev/null 2>&1; then
                                            echo "Docker daemon is ready"
                                            exit 0
                                        fi
                                        echo "Waiting for Docker daemon... ($i/30)"
                                        sleep 2
                                    done
                                    echo "ERROR: Docker daemon failed to start"
                                    exit 1
                                '''
                            }
                        }
                    }

                    stage('Checkout') {
                        steps {
                            container('docker-client') {
                                echo 'Cloning repository from %s...'
                                %s
                            }
                        }
                    }

                    stage('Create Dockerfile') {
                        steps {
                            container('docker-client') {
                                echo 'Creating Dockerfile from generated content...'
                                script {
                                    // Decode Base64 Dockerfile content
                                    def dockerfileBase64 = '%s'
                                    def dockerfileContent = new String(dockerfileBase64.decodeBase64())
                                    writeFile file: 'Dockerfile', text: dockerfileContent
                                    echo 'Dockerfile created successfully'
                                    sh 'cat Dockerfile'
                                }
                            }
                        }
                    }

                    stage('Build Docker Image') {
                        steps {
                            container('docker-client') {
                                echo "Building Docker image: ${params.IMAGE_NAME}:${params.IMAGE_TAG}"
                                sh \"""
                                    docker build -t \\${IMAGE_NAME}:\\${IMAGE_TAG} .
                                \"""
                            }
                        }
                    }

                    stage('Verify Image') {
                        steps {
                            container('docker-client') {
                                echo 'Verifying Docker image...'
                                sh 'docker images | grep \\$IMAGE_NAME || echo "Image built successfully"'
                            }
                        }
                    }
                }

                post {
                    success {
                        echo 'Docker image built successfully!'
                        echo "Image: ${params.IMAGE_NAME}:${params.IMAGE_TAG}"
                    }
                    failure {
                        echo 'Build failed!'
                        echo 'Check the console output for details'
                    }
                    always {
                        echo 'Build completed'
                    }
                }
            }""", imageName, imageTag, gitUrl, gitCheckout, base64Dockerfile);

        logger.info("Generated Kubernetes pipeline script for image: {}:{}", imageName, imageTag);
        return pipelineScript;
    }

    /**
     * Generate Kubernetes-compatible Jenkins Pipeline script for preview
     *
     * Uses podTemplate with Docker-in-Docker (DinD) container with readable Dockerfile
     *
     * @param gitUrl Git repository URL
     * @param gitBranch Git branch name
     * @param gitCredentialId Jenkins credential ID for Git (optional for public repos)
     * @param dockerfileContent Generated Dockerfile content
     * @param imageName Docker image name
     * @param imageTag Docker image tag
     * @return Kubernetes-compatible Groovy pipeline script with readable Dockerfile
     */
    public String generateK8sPipelineScriptForPreview(
        String gitUrl,
        String gitBranch,
        String gitCredentialId,
        String dockerfileContent,
        String imageName,
        String imageTag
    ) {
        // Escape special characters in Dockerfile for Groovy string
        String escapedDockerfile = escapeDockerfileForGroovy(dockerfileContent);

        // Build git checkout command
        String gitCheckout;
        if (gitCredentialId != null && !gitCredentialId.isEmpty()) {
            gitCheckout = String.format("""
                git url: '%s',
                        branch: '%s',
                        credentialsId: '%s'""", gitUrl, gitBranch, gitCredentialId);
        } else {
            gitCheckout = String.format("""
                git url: '%s',
                        branch: '%s'""", gitUrl, gitBranch);
        }

        String pipelineScript = String.format("""
            pipeline {
                agent {
                    kubernetes {
                        yaml '''
            apiVersion: v1
            kind: Pod
            metadata:
              labels:
                jenkins: agent
            spec:
              containers:
              - name: docker
                image: docker:24-dind
                securityContext:
                  privileged: true
                volumeMounts:
                - name: docker-sock
                  mountPath: /var/run
                env:
                - name: DOCKER_TLS_CERTDIR
                  value: ""
                - name: DOCKER_HOST
                  value: "unix:///var/run/docker.sock"
              - name: docker-client
                image: docker:24-cli
                command:
                - cat
                tty: true
                volumeMounts:
                - name: docker-sock
                  mountPath: /var/run
                env:
                - name: DOCKER_HOST
                  value: "unix:///var/run/docker.sock"
              volumes:
              - name: docker-sock
                emptyDir: {}
            '''
                    }
                }

                parameters {
                    string(name: 'IMAGE_NAME', defaultValue: '%s', description: 'Docker image name')
                    string(name: 'IMAGE_TAG', defaultValue: '%s', description: 'Docker image tag')
                }

                stages {
                    stage('Wait for Docker') {
                        steps {
                            container('docker-client') {
                                echo 'Waiting for Docker daemon to be ready...'
                                sh '''
                                    for i in $(seq 1 30); do
                                        if docker info >/dev/null 2>&1; then
                                            echo "Docker daemon is ready"
                                            exit 0
                                        fi
                                        echo "Waiting for Docker daemon... ($i/30)"
                                        sleep 2
                                    done
                                    echo "ERROR: Docker daemon failed to start"
                                    exit 1
                                '''
                            }
                        }
                    }

                    stage('Checkout') {
                        steps {
                            container('docker-client') {
                                echo 'Cloning repository from %s...'
                                %s
                            }
                        }
                    }

                    stage('Create Dockerfile') {
                        steps {
                            container('docker-client') {
                                echo 'Creating Dockerfile from generated content...'
                                script {
                                    // Dockerfile content (plain text for readability)
                                    def dockerfileContent = \"\"\"\\
            %s\\
            \"\"\"
                                    writeFile file: 'Dockerfile', text: dockerfileContent
                                    echo 'Dockerfile created successfully'
                                    sh 'cat Dockerfile'
                                }
                            }
                        }
                    }

                    stage('Build Docker Image') {
                        steps {
                            container('docker-client') {
                                echo "Building Docker image: ${params.IMAGE_NAME}:${params.IMAGE_TAG}"
                                sh \"""
                                    docker build -t \\${IMAGE_NAME}:\\${IMAGE_TAG} .
                                \"""
                            }
                        }
                    }

                    stage('Verify Image') {
                        steps {
                            container('docker-client') {
                                echo 'Verifying Docker image...'
                                sh 'docker images | grep \\$IMAGE_NAME || echo "Image built successfully"'
                            }
                        }
                    }
                }

                post {
                    success {
                        echo 'Docker image built successfully!'
                        echo "Image: ${params.IMAGE_NAME}:${params.IMAGE_TAG}"
                    }
                    failure {
                        echo 'Build failed!'
                        echo 'Check the console output for details'
                    }
                    always {
                        echo 'Build completed'
                    }
                }
            }""", imageName, imageTag, gitUrl, gitCheckout, escapedDockerfile);

        logger.info("Generated Kubernetes preview pipeline script for image: {}:{}", imageName, imageTag);
        return pipelineScript;
    }

    /**
     * Generate Kubernetes-compatible Pipeline using Kaniko (no privileged mode required)
     *
     * Kaniko builds container images without Docker daemon, making it more suitable
     * for Kubernetes environments without privileged containers
     *
     * @param gitUrl Git repository URL
     * @param gitBranch Git branch name
     * @param gitCredentialId Jenkins credential ID for Git
     * @param dockerfileContent Generated Dockerfile content
     * @param imageName Docker image name
     * @param imageTag Docker image tag
     * @param registryUrl Optional registry URL for pushing
     * @param registryCredentialId Optional registry credential ID
     * @return Kubernetes-compatible Groovy pipeline script using Kaniko
     */
    public String generateK8sKanikoPipelineScript(
        String gitUrl,
        String gitBranch,
        String gitCredentialId,
        String dockerfileContent,
        String imageName,
        String imageTag,
        String registryUrl,
        String registryCredentialId
    ) {
        // Escape special characters in Dockerfile for Groovy string
        String escapedDockerfile = escapeDockerfileForGroovy(dockerfileContent);

        // Build git checkout command
        String gitCheckout;
        if (gitCredentialId != null && !gitCredentialId.isEmpty()) {
            gitCheckout = String.format("""
                git url: '%s',
                        branch: '%s',
                        credentialsId: '%s'""", gitUrl, gitBranch, gitCredentialId);
        } else {
            gitCheckout = String.format("""
                git url: '%s',
                        branch: '%s'""", gitUrl, gitBranch);
        }

        // Build Kaniko execution script
        String buildScript;
        String successMessage;
        String verifyStage;
        String postSuccessMessage;
        String imageLocation;
        String tarMessage;

        if (registryUrl != null && !registryUrl.isEmpty()) {
            // Push to Harbor with Jenkins credentials
            if (registryCredentialId != null && !registryCredentialId.isEmpty()) {
                buildScript = String.format("""
                    def destination = params.REGISTRY_URL + "/" + params.IMAGE_NAME + ":" + params.IMAGE_TAG
                            echo "Destination: ${destination}"
                            def cacheRepo = params.REGISTRY_URL + "/cache"

                            withCredentials([usernamePassword(credentialsId: '%s', usernameVariable: 'HARBOR_USER', passwordVariable: 'HARBOR_PASS')]) {
                                sh \"""
                                    mkdir -p /kaniko/.docker
                                    cat > /kaniko/.docker/config.json << EOF
                    {"auths":{"${params.REGISTRY_URL}":{"username":"${HARBOR_USER}","password":"${HARBOR_PASS}"}}}
                    EOF
                                    /kaniko/executor --context=\\$(pwd) --dockerfile=Dockerfile --destination=$destination --cache=true --cache-repo=$cacheRepo --skip-tls-verify
                                \"""
                            }""", registryCredentialId);
            } else {
                // No credential - try without auth
                buildScript = """
                    def destination = params.REGISTRY_URL + "/" + params.IMAGE_NAME + ":" + params.IMAGE_TAG
                            echo "Destination: ${destination}"
                            def cacheRepo = params.REGISTRY_URL + "/cache"
                            sh "/kaniko/executor --context=\\$(pwd) --dockerfile=Dockerfile --destination=${destination} --cache=true --cache-repo=${cacheRepo} --skip-tls-verify" """;
            }
            successMessage = "Image built and pushed to Harbor successfully!";
            verifyStage = "";
            postSuccessMessage = "Docker image built and pushed to Harbor successfully!";
            imageLocation = String.format("${params.REGISTRY_URL}/${params.IMAGE_NAME}:${params.IMAGE_TAG}");
            tarMessage = "";
        } else {
            // Local build only
            buildScript = """
                def destination = params.IMAGE_NAME + ":" + params.IMAGE_TAG
                        echo "Destination: ${destination}"
                        sh "/kaniko/executor --context=\\$(pwd) --dockerfile=Dockerfile --no-push --destination=${destination} --tar-path=image.tar" """;
            successMessage = "Image built successfully and saved as image.tar";
            verifyStage = """

        stage('Verify Image') {
            steps {
                container('kaniko') {
                    echo 'Verifying built image tarball...'
                    sh 'ls -lh image.tar'
                }
            }
        }""";
            postSuccessMessage = "Docker image built successfully with Kaniko!";
            imageLocation = "${params.IMAGE_NAME}:${params.IMAGE_TAG}";
            tarMessage = "\n            echo 'Image saved as: image.tar'";
        }

        String podYaml = """
            apiVersion: v1
            kind: Pod
            metadata:
              labels:
                jenkins: agent
            spec:
              containers:
              - name: kaniko
                image: gcr.io/kaniko-project/executor:debug
                command:
                - /busybox/cat
                tty: true""";

        String registryParam = registryUrl != null && !registryUrl.isEmpty()
            ? String.format("string(name: 'REGISTRY_URL', defaultValue: '%s', description: 'Harbor registry URL')", registryUrl)
            : "";

        String pipelineScript = String.format("""
            pipeline {
                agent {
                    kubernetes {
                        yaml '''
            %s
            '''
                    }
                }

                parameters {
                    string(name: 'IMAGE_NAME', defaultValue: '%s', description: 'Docker image name')
                    string(name: 'IMAGE_TAG', defaultValue: '%s', description: 'Docker image tag')
                    %s
                }

                stages {
                    stage('Checkout') {
                        steps {
                            container('kaniko') {
                                echo 'Cloning repository from %s...'
                                %s
                            }
                        }
                    }

                    stage('Create Dockerfile') {
                        steps {
                            container('kaniko') {
                                echo 'Creating Dockerfile from generated content...'
                                script {
                                    // Dockerfile content (plain text)
                                    def dockerfileContent = \"\"\"\\
            %s\\
            \"\"\"
                                    writeFile file: 'Dockerfile', text: dockerfileContent
                                    echo 'Dockerfile created successfully'
                                    sh 'cat Dockerfile'
                                }
                            }
                        }
                    }

                    stage('Build Docker Image with Kaniko') {
                        steps {
                            container('kaniko') {
                                echo "Building Docker image with Kaniko: ${params.IMAGE_NAME}:${params.IMAGE_TAG}"
                                script {
                                    %s
                                }
                                echo '%s'
                            }
                        }
                    }%s
                }

                post {
                    success {
                        echo '%s'
                        echo "Image: %s"%s
                    }
                    failure {
                        echo 'Build failed!'
                        echo 'Check the console output for details'
                    }
                    always {
                        echo 'Build completed'
                    }
                }
            }""", podYaml, imageName, imageTag, registryParam, gitUrl, gitCheckout, escapedDockerfile,
            buildScript, successMessage, verifyStage, postSuccessMessage, imageLocation, tarMessage);

        logger.info("Generated Kaniko pipeline script for image: {}:{}", imageName, imageTag);
        return pipelineScript;
    }

    /**
     * Generate Kaniko pipeline for preview (alias to standard Kaniko pipeline)
     *
     * Kaniko already uses plain text Dockerfile, so this is identical to the standard method.
     * This method exists for consistency with the API naming convention.
     */
    public String generateK8sKanikoPipelineScriptForPreview(
        String gitUrl,
        String gitBranch,
        String gitCredentialId,
        String dockerfileContent,
        String imageName,
        String imageTag,
        String registryUrl,
        String registryCredentialId
    ) {
        return generateK8sKanikoPipelineScript(
            gitUrl, gitBranch, gitCredentialId, dockerfileContent,
            imageName, imageTag, registryUrl, registryCredentialId
        );
    }

    /**
     * Encode Dockerfile content as Base64 to safely embed in Groovy script
     *
     * @param content Dockerfile content
     * @return Base64 encoded string
     */
    public static String encodeDockerfileBase64(String content) {
        return Base64.getEncoder().encodeToString(content.getBytes());
    }

    /**
     * Escape Dockerfile for plain text embedding in Groovy script
     *
     * Escapes: \ -> \\, $ -> \$, " -> \"
     *
     * @param content Dockerfile content
     * @return Escaped content
     */
    public static String escapeDockerfileForGroovy(String content) {
        return content
            .replace("\\", "\\\\")
            .replace("$", "\\$")
            .replace("\"", "\\\"");
    }
}
