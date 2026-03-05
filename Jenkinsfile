// =============================================================================
// Fuse (Containerize V2) - Monorepo Jenkins CI/CD Pipeline
// =============================================================================
// Stages:
//   1. Checkout          - Clone monorepo from Gitea (develop branch)
//   2. Detect Changes    - Determine which components changed
//   3. Docker Build & Push  (kaniko single session: frontend then backend)
//   4. Update Manifests  - Patch image tags in containerize-v2-deploy repo
//   5. ArgoCD Sync       - Sync fuse-frontend / fuse-backend apps
//
// Required Jenkins Credentials:
//   - harbor-credentials  : Harbor registry username/password
//   - gitea-credentials   : Gitea username/password
//   - argocd-credentials  : ArgoCD username/password
// =============================================================================

def BACKEND_IMAGE  = 'harbor.devops.cicd.test/containerizev1/backend'
def FRONTEND_IMAGE = 'harbor.devops.cicd.test/containerizev1/frontend'
def IMAGE_TAG      = "${env.BUILD_NUMBER}"

pipeline {
    agent {
        kubernetes {
            yaml """
apiVersion: v1
kind: Pod
metadata:
  labels:
    jenkins: agent
spec:
  containers:
  - name: ci-tools
    image: harbor.devops.cicd.test/demo/ci-tools:latest
    command:
    - cat
    tty: true
  - name: kaniko
    image: gcr.io/kaniko-project/executor:debug
    command:
    - cat
    tty: true
    volumeMounts:
    - name: docker-config
      mountPath: /kaniko/.docker
  volumes:
  - name: docker-config
    emptyDir: {}
"""
        }
    }

    environment {
        HARBOR_REGISTRY   = 'harbor.devops.cicd.test'
        HARBOR_CREDENTIAL = 'harbor-credentials'
        GIT_CREDENTIAL    = 'gitea-credentials'
        ARGOCD_SERVER     = 'argocd.devops.cicd.test'
        DEPLOY_REPO       = 'https://gitea.devops.cicd.test/gitea_admin/containerize-v1-deploy.git'
    }

    options {
        skipDefaultCheckout(true)
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    stages {
        // =================================================================
        // Stage 1: Checkout
        // =================================================================
        stage('Checkout') {
            steps {
                container('ci-tools') {
                    withCredentials([usernamePassword(
                        credentialsId: "${GIT_CREDENTIAL}",
                        usernameVariable: 'GIT_USER',
                        passwordVariable: 'GIT_PASS'
                    )]) {
                        sh """
                            git config --global http.sslVerify false
                            git clone --branch main https://\${GIT_USER}:\${GIT_PASS}@gitea.devops.cicd.test/gitea_admin/containerize-v1.git .
                        """
                    }
                }
            }
        }

        // =================================================================
        // Stage 2: Change Detection
        // =================================================================
        stage('Detect Changes') {
            steps {
                container('ci-tools') {
                    script {
                        def changes = sh(
                            script: "git diff --name-only HEAD~1 HEAD 2>/dev/null || echo 'containerize-backend/ containerize-frontend/'",
                            returnStdout: true
                        ).trim()

                        env.BACKEND_CHANGED  = changes.contains('containerize-backend/') ? 'true' : 'false'
                        env.FRONTEND_CHANGED = changes.contains('containerize-frontend/') ? 'true' : 'false'

                        // Always build both on develop/main
                        if (env.GIT_BRANCH in ['origin/develop', 'origin/main'] || !env.GIT_BRANCH) {
                            env.BACKEND_CHANGED  = 'true'
                            env.FRONTEND_CHANGED = 'true'
                        }

                        echo "Backend changed:  ${env.BACKEND_CHANGED}"
                        echo "Frontend changed: ${env.FRONTEND_CHANGED}"
                        echo "Image tag: ${IMAGE_TAG}"
                    }
                }
            }
        }

        // =================================================================
        // Stage 3: Docker Build & Push (kaniko)
        // Both frontend and backend are built in a single kaniko container
        // session to avoid container restart issues between executions.
        // =================================================================
        stage('Docker Build & Push') {
            steps {
                container('kaniko') {
                    withCredentials([usernamePassword(
                        credentialsId: "${HARBOR_CREDENTIAL}",
                        usernameVariable: 'HARBOR_USER',
                        passwordVariable: 'HARBOR_PASS'
                    )]) {
                        sh """
                            AUTH=\$(echo -n "\${HARBOR_USER}:\${HARBOR_PASS}" | base64)
                            echo '{"auths":{"${HARBOR_REGISTRY}":{"auth":"'"\$AUTH"'"}}}' > /kaniko/.docker/config.json

                            if [ "${env.FRONTEND_CHANGED}" = "true" ]; then
                                echo "Building frontend image..."
                                /kaniko/executor \
                                    --dockerfile=\${WORKSPACE}/containerize-frontend/Dockerfile \
                                    --context=dir://\${WORKSPACE}/containerize-frontend \
                                    --destination=${FRONTEND_IMAGE}:${IMAGE_TAG} \
                                    --destination=${FRONTEND_IMAGE}:latest \
                                    --insecure \
                                    --skip-tls-verify
                                echo "Frontend image pushed: ${FRONTEND_IMAGE}:${IMAGE_TAG}"
                            fi

                            if [ "${env.BACKEND_CHANGED}" = "true" ]; then
                                echo "Building backend image..."
                                /kaniko/executor \
                                    --dockerfile=\${WORKSPACE}/containerize-backend/Dockerfile \
                                    --context=dir://\${WORKSPACE}/containerize-backend \
                                    --destination=${BACKEND_IMAGE}:${IMAGE_TAG} \
                                    --destination=${BACKEND_IMAGE}:latest \
                                    --insecure \
                                    --skip-tls-verify
                                echo "Backend image pushed: ${BACKEND_IMAGE}:${IMAGE_TAG}"
                            fi
                        """
                    }
                }
            }
        }

        // =================================================================
        // Stage 5 & 6: Update Manifests + ArgoCD Sync
        // =================================================================
        stage('Update Manifests & Deploy') {
            steps {
                container('ci-tools') {
                    withCredentials([
                        usernamePassword(credentialsId: "${GIT_CREDENTIAL}", usernameVariable: 'GIT_USER', passwordVariable: 'GIT_PASS'),
                        usernamePassword(credentialsId: 'argocd-credentials', usernameVariable: 'ARGOCD_USER', passwordVariable: 'ARGOCD_PASS')
                    ]) {
                        sh """
                            git config --global http.sslVerify false
                            git config --global user.email 'jenkins@devops.cicd.test'
                            git config --global user.name 'Jenkins CI'

                            rm -rf /tmp/deploy-repo
                            git clone --branch main https://\${GIT_USER}:\${GIT_PASS}@gitea.devops.cicd.test/gitea_admin/containerize-v1-deploy.git /tmp/deploy-repo
                            cd /tmp/deploy-repo

                            if [ "${env.FRONTEND_CHANGED}" = "true" ]; then
                                sed -i 's|image: ${FRONTEND_IMAGE}:.*|image: ${FRONTEND_IMAGE}:${IMAGE_TAG}|g' frontend/deployment.yaml
                                echo "Frontend manifest updated: ${FRONTEND_IMAGE}:${IMAGE_TAG}"
                            fi

                            if [ "${env.BACKEND_CHANGED}" = "true" ]; then
                                sed -i 's|image: ${BACKEND_IMAGE}:.*|image: ${BACKEND_IMAGE}:${IMAGE_TAG}|g' backend/deployment.yaml
                                echo "Backend manifest updated: ${BACKEND_IMAGE}:${IMAGE_TAG}"
                            fi

                            git add .
                            git diff --staged --quiet || git commit -m "ci: update image tags to build #${IMAGE_TAG}"
                            for i in 1 2 3; do
                                git pull --rebase origin main && git push origin main && break
                                echo "Retry \$i..."
                                sleep 3
                            done
                        """

                        sh """
                            argocd login ${ARGOCD_SERVER} \
                                --username \${ARGOCD_USER} \
                                --password \${ARGOCD_PASS} \
                                --insecure --grpc-web

                            if [ "${env.FRONTEND_CHANGED}" = "true" ]; then
                                argocd app sync containerize-v1-frontend --force --grpc-web || true
                                echo "ArgoCD sync triggered: containerize-v1-frontend"
                            fi

                            if [ "${env.BACKEND_CHANGED}" = "true" ]; then
                                argocd app sync containerize-v1-backend --force --grpc-web || true
                                echo "ArgoCD sync triggered: containerize-v1-backend"
                            fi
                        """
                    }
                }
            }
        }
    }

    post {
        success {
            echo "Pipeline completed successfully. Image tag: ${IMAGE_TAG}"
        }
        failure {
            echo "Pipeline FAILED. Check logs above."
        }
        cleanup {
            echo "Build #${IMAGE_TAG} finished."
        }
    }
}
