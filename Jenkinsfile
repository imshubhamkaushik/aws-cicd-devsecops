pipeline {
    agent any

    environment {
        // AWS
        AWS_REGION = "ap-south-1"
        AWS_ACCOUNT_ID = ""

        // Cluster Name
        CLUSTER_NAME = "catalogix-cluster"

        ECR_REGISTRY = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

        USER_SVC_IMAGE = "${ECR_REGISTRY}/user-svc:${IMAGE_TAG}"
        PRODUCT_SVC_IMAGE = "${ECR_REGISTRY}/product-svc:${IMAGE_TAG}"
        FRONTEND_SVC_IMAGE = "${ECR_REGISTRY}/frontend-svc:${IMAGE_TAG}"

        // Image tags
        MAJOR_VERSION = "1.0"
        IMAGE_TAG = "${MAJOR_VERSION}-${BUILD_NUMBER}"

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

        stage('Clean Workspace') {
            steps {
                cleanWs()
            }
        }

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
                                timeout(time: 5, unit: 'MINUTES') {
                                    waitForQualityGate abortPipeline: true
                                }
                            }
                        }
                    }
                }

                stage('Product Service') {
                    steps {
                        dir('product-svc') {
                            sh 'mvn -B clean package'
                            withSonarQubeEnv("${SONARQUBE_SERVER}") {
                                sh 'mvn sonar:sonar'
                                timeout(time: 5, unit: 'MINUTES') {
                                    waitForQualityGate abortPipeline: true
                                }
                            }
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

        stage('Terraform Infrastructure') {
            steps {
                dir('terraform/env/dev') {

                    sh 'terraform fmt -recursive'

                    sh 'terraform init'

                    sh 'terraform validate'

                    sh 'terraform plan -out main.tfplan'

                    sh 'terraform apply -auto-approve main.tfplan'
                }
            }
        }

        stage('Verify Infrastructure Readiness') {
            steps {

                sh """
                aws eks describe-cluster \
                --name ${CLUSTER_NAME} \
                --region ${AWS_REGION} \
                --query "cluster.status"
                """

            }
        }

        stage('Fetch RDS Endpoint') {
            steps {
                script {
                    env.RDS_ENDPOINT = sh(
                        script: "terraform -chdir=terraform/env/dev output -raw rds_endpoint",
                        returnStdout: true
                    ).trim()
                }
            }
        }

        stage('Validate Kubernetes Access') {
            steps {
                // Generate the config file for the Jenkins user dynamically
                sh 'aws eks update-kubeconfig --region ${AWS_REGION} --name ${CLUSTER_NAME}'
        
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
                      --from-literal=DB_PASSWORD="\${DB_PASSWORD}" \
                      --from-literal=DB_USER=postgres \
                      --namespace ${K8S_NAMESPACE} \
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
                sh """
                helm upgrade --install catalogix ${HELM_CHART_DIR} \
                    --namespace ${K8S_NAMESPACE} \
                    --create-namespace \
                    --set global.imageRegistry=${ECR_REGISTRY} \
                    --set global.imageTag=${IMAGE_TAG} \
                    --set global.cloudProvider=aws \
                    --set database.host=${RDS_ENDPOINT}
                """
            }
        }

        stage('Wait for Deployment Rollout') {
            steps {

                sh "kubectl rollout status deployment/frontend-svc -n ${K8S_NAMESPACE}"
                sh "kubectl rollout status deployment/user-svc -n ${K8S_NAMESPACE}"
                sh "kubectl rollout status deployment/product-svc -n ${K8S_NAMESPACE}"

            }
        }

        stage('Post-Deployment Verification') {
            steps {
                sh 'kubectl get pods -n ${K8S_NAMESPACE}'
                sh 'kubectl get svc -n ${K8S_NAMESPACE}'
                sh 'kubectl get all -n ${K8S_NAMESPACE}'
                sh 'kubectl get ingress -n ${K8S_NAMESPACE}'
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