pipeline {
    agent any

    environment {
        // AWS
        AWS_REGION = "ap-south-1"

        AWS_ACCOUNT_ID = sh(
            script: "aws sts get-caller-identity --query Account --output text",
            returnStdout: true
        ).trim()

        ECR_REGISTRY = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
        CLUSTER_NAME = "catalogix-cluster"

        // Image tags
        MAJOR_VERSION = "1.0"
        IMAGE_TAG = "${MAJOR_VERSION}-${BUILD_NUMBER}"

        USER_SVC_IMAGE = "${ECR_REGISTRY}/user-svc:${IMAGE_TAG}"
        PRODUCT_SVC_IMAGE = "${ECR_REGISTRY}/product-svc:${IMAGE_TAG}"
        FRONTEND_SVC_IMAGE = "${ECR_REGISTRY}/frontend-svc:${IMAGE_TAG}"

        // Kubernetes / Helm
        HELM_CHART_DIR = "helm/catalogix-hc"
        K8S_NAMESPACE = "catalogix"

        // Jenkins credentials
        SONARQUBE_SERVER = "sonarqube"
        DB_PASSWORD_CREDENTIAL_ID = "catalogix-db-password"
        
    }

    tools {
        maven 'maven'
        nodejs 'node'
    }

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    stages {

        stage('Unit & Integration Tests (Backend)') {
            parallel {
                stage('User Service') {
                    steps {
                        dir('user-svc') {
                            sh 'mvn -B clean verify'
                        }
                    }
                }
                stage('Product Service') {
                    steps {
                        dir('product-svc') {
                            sh 'mvn -B clean verify'
                        }
                    }
                }
            }
        }

        stage('Build & SonarQube Analysis') {
            parallel {

                stage('User Service') {
                    steps {
                        dir('user-svc') {
                            sh 'mvn -B clean package'
                            withSonarQubeEnv("${SONARQUBE_SERVER}") {
                                sh 'mvn sonar:sonar'
                            }
                        }
                        timeout(time: 5, unit: 'MINUTES') {
                            waitForQualityGate abortPipeline: true
                        }
                    }
                }

                stage('Product Service') {
                    steps {
                        dir('product-svc') {
                            sh 'mvn -B clean package'
                            withSonarQubeEnv("${SONARQUBE_SERVER}") {
                                sh 'mvn sonar:sonar'
                            }
                        }
                        timeout(time: 5, unit: 'MINUTES') {
                            waitForQualityGate abortPipeline: true
                        }
                    }
                }

                stage('Frontend Service Build') {
                    steps {
                        dir('frontend-svc') {
                            sh 'npm install'
                            sh 'npm run build'
                        }
                    }
                }
            }
        }

        stage('Build Docker Images') {
            parallel {
                stage('User Service') {
                    steps {
                        sh "docker build -t ${USER_SVC_IMAGE} user-svc"
                    }
                }
                stage('Product Service') {
                    steps {
                        sh "docker build -t ${PRODUCT_SVC_IMAGE} product-svc"
                    }
                }
                stage('Frontend Service') {
                    steps {
                        sh "docker build -t ${FRONTEND_SVC_IMAGE} frontend-svc"
                    }
                }
            }
        }

        stage('Trivy Image Security Scan') {
            parallel {
                stage('User Service') {
                    steps {
                        sh """
                        docker run --rm \
                          -v /var/run/docker.sock:/var/run/docker.sock \
                          aquasec/trivy:latest image \
                          --severity HIGH,CRITICAL \
                          --exit-code 1 \
                          --ignore-unfixed \
                          ${USER_SVC_IMAGE}
                        """
                    }
                }
                stage('Product Service') {
                    steps {
                        sh """
                        docker run --rm \
                          -v /var/run/docker.sock:/var/run/docker.sock \
                          aquasec/trivy:latest image \
                          --severity HIGH,CRITICAL \
                          --exit-code 1 \
                          --ignore-unfixed \
                          ${PRODUCT_SVC_IMAGE}
                        """
                    }
                }
                stage('Frontend Service') {
                    steps {
                        sh """
                        docker run --rm \
                          -v /var/run/docker.sock:/var/run/docker.sock \
                          aquasec/trivy:latest image \
                          --severity HIGH,CRITICAL \
                          --exit-code 1 \
                          --ignore-unfixed \
                          ${FRONTEND_SVC_IMAGE}
                        """
                    }
                }
            }
        }

        stage('Login to Amazon ECR') {
            steps {
                sh """
                aws ecr get-login-password --region ${AWS_REGION} \
                | docker login --username AWS --password-stdin ${ECR_REGISTRY}
                """
            }
        }

        stage('Push Images to ECR') {
            parallel {
                stage('Push User Service') {
                    steps {
                        sh "docker push ${USER_SVC_IMAGE}"
                    }
                }
                stage('Push Product Service') {
                    steps {
                        sh "docker push ${PRODUCT_SVC_IMAGE}"
                    }
                }
                stage('Push Frontend Service') {
                    steps {
                        sh "docker push ${FRONTEND_SVC_IMAGE}"
                    }
                }
            }
        }

        stage('Validate Kubernetes Access') {
            steps {
                // Fix: Generate the config file for the Jenkins user dynamically
                sh 'aws eks update-kubeconfig --region ap-south-1 --name ${CLUSTER_NAME}'
        
                // Now this will work
                sh 'kubectl get nodes' 
            }
        }

        stage('Create / Update Kubernetes Secrets') {
            steps {
                withCredentials([
                    string(credentialsId: "${DB_PASSWORD_CREDENTIAL_ID}", variable: 'DB_PASSWORD')
                ]) {
                    sh """
                    kubectl create secret generic catalogix-secrets \
                      --from-literal=DB_PASSWORD=${DB_PASSWORD} \
                      --from-literal=DB_USER=postgres \
                      --dry-run=client -o yaml | kubectl apply -f -
                    """
                }
            }
        }

        stage('Trivy Scan Helm Charts') {
            steps {
                sh """
                docker run --rm \
                  -v \$(pwd):/catalogix \
                  aquasec/trivy:latest config \
                  --severity HIGH,CRITICAL \
                  --exit-code 1 \
                  --ignorefile /catalogix/.trivyignore \
                  /catalogix/${HELM_CHART_DIR}
                """
            }
        }

        stage('Deploy to EKS using Helm') {
            steps {
                script {
                    // Define the Helm command as a variable so we can reuse it
                    def helmCmd = """
                        helm upgrade --install catalogix ${HELM_CHART_DIR} \
                          --namespace ${K8S_NAMESPACE} \
                          --create-namespace \
                          --set global.imageRegistry=${ECR_REGISTRY} \
                          --set global.imageTag=${IMAGE_TAG} \
                          --set global.cloudProvider=aws \
                          --set postgres.storageClass=gp3-sc
                    """

                    // Attempt deployment (returnStatus: true prevents pipeline from failing immediately)
                    echo "Attempting Helm deployment..."
                    def exitCode = sh(script: helmCmd, returnStatus: true)

                    if (exitCode != 0) {
                        echo "Helm Upgrade Failed! It might be a StatefulSet immutability issue."
                        echo "Auto-fixing: Deleting old StatefulSet (Data/PVCs will be preserved)..."
                        
                        // Delete the conflicting StatefulSet so Helm can recreate it with new settings
                        sh "kubectl delete statefulset postgres -n ${K8S_NAMESPACE} --ignore-not-found"
                        
                        // Wait a moment for Kubernetes to register the deletion
                        sleep 5
                        
                        echo "Retrying deployment..."
                        // Run the deployment again. If it fails this time, the pipeline will fail for real.
                        sh helmCmd
                    } else {
                        echo "Deployment successful on first try."
                    }
                }
            }
        }

        stage('Post-Deployment Verification') {
            steps {
                sh 'kubectl get pods -n ${K8S_NAMESPACE}'
                sh 'kubectl get svc -n monitoring || true'
            }
        }
    }

    post {
        success {
            echo 'Pipeline executed successfully'
        }
        failure {
            echo 'Pipeline failed — check logs'
        }
    }
}