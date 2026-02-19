# End-to-End DevSecOps CI/CD Pipeline for Microservices

**(Jenkins · Docker · Amazon ECR · Amazon EKS · Helm · SonarQube · Trivy · Prometheus · Grafana )**

---

## Project Overview

This project demonstrates the design and implementation of an **end-to-end DevSecOps CI/CD pipeline** for a containerized, microservices-based application deployed on Amazon EKS, with Jenkins running on Amazon EC2 and conatiner images stored in Amazon ECR.

The primary objective is to showcase secure, automated application delivery using modern DevOps and DevSecOps practices, including:

- CI/CD pipeline automation using Jenkins
- Containerization of microservices using Docker
- Static code analysis and quality enforcement using SonarQube
- Container and configuration security scanning using Trivy
- Deployment and orchestration using Kubernetes and Helm
- Monitoring and alerting using Prometheus and Grafana
- Operational automation using Bash Scripts

The project intentionally focuses on **pipeline design, security integration, deployment automation, and observability**, rather than application business logic.

---

## Why This Project?

This project focuses on CI/CD and DevSecOps practices for containerized applications, complementing infrastructure-focused DevOps projects.

Key focus areas include:

- Secure CI/CD pipeline design using Jenkins
- Shift-left security using automated scanning
- Kubernetes-based application delivery using Helm
- Service-level observability for microservices
- Operational automation for common day-to-day tasks

Infrastructure provisioning (Terraform / Ansible) is intentionally kept out of scope to maintain clear separation between **CI/CD, application delivery, and infrastructure management**.

---

## Tech Stack

- **CI/CD**: Jenkins(Groovy Pipeline)
- **Containerization**: Docker, Amazon ECR
- **Cloud Platform**: AWS (EC2, EKS,ECR, IAM)
- **Orchestration**: Kubernetes (Amazon EKS)
- **Package Management**: Helm
- **Security & Quality**: SonarQube(SAST), Trivy(Container & Config Scan)
- **Backend Services**: Spring Boot (Microservices)
- **Frontend Services**: React
- **Scripting & Automation**: Bash (Linux Only)
- **Container Runtime**: Linux
- **Observability**: Prometheus, Grafana

---

## Cloud Platform and Infrastructure (AWS)

This project is deployed and validated on Amazon Web Services (AWS) to simulate a production-like cloud environment.

**AWS Services Used**

- **Amazon EC2** 
    - Hosts Jenkins and supporting CI/CD tooling
    - Hosts SonarQube on a Separate instance for isolation
    - This separation improves stability, mirrors common enterprise CI/CD layouts, and avoids performance contention between pipeline execution and static analysis.
- **Amazon EKS** 
    - Managed Kubernetes cluster for application runtime
- **Amazon ECR**
    - Private container registry for application images
- **AWS IAM**
    - Fine-grained access control for EKS and ECR integration

**Design Considerations**

- Jenkins runs on EC2 to reflect commonly used self-managed CI/CD setups.
- SonarQube runs on a separate EC2 instance to isolate resource-intensive analysis workloads.
- Kubernetes workloads run on Amazon EKS, leveraging a managed control plane while retaining full Kubernetes primitives.
- Container images are securely pushed to and pulled from Amazon ECR using IAM-based authentication.
- Static cloud credentials are avoided wherever possible in favor of IAM roles and policies. 

This approach balances cloud realism with project scope clarity, keeping the focus on DevSecOps workflows rather than infrastructure automation.

### Identity & Access Management (AWS IAM)

The project uses AWS IAM roles and policies to enforce secure, least-privilege access across CI/CD and Kubernetes components:

- IAM roles and policies are configured for Amazon EKS to allow cluster control plane operations and managed add-ons.
- IAM permissions enable Kubernetes worker nodes and CI/CD tooling to pull container images securely from Amazon ECR.
- Access to AWS services is authenticated using IAM-based mechanisms rather than static credentials wherever possible.

This setup reflects real-world cloud security practices by separating responsibilities between CI/CD tooling, Kubernetes runtime, and AWS-managed services.

---

## Architecture Overview

### High-Level Architecture

- Microservices are developed using Spring Boot
- Each service is containerized using Docker
- Jenkins orchestrates the CI/CD pipeline
- SonarQube performs static code analysis with quality gates
- Trivy scans container images and Kubernetes manifests
- Helm charts manage Kubernetes deployments
- Kubernetes handles service orchestration, scaling, and health checks
- Prometheus and Grafana provide service-level monitoring and alerting

#### High-Level Architecture Diagram
```mermaid
flowchart TD
    Dev[Developer<br/>Git Push] --> Git[Git Repository]

    Git --> Jenkins[Jenkins CI/CD<br/>(EC2)]

    Jenkins -->|Build & Test| Docker[Docker Images]
    Jenkins -->|Scan| Sonar[SonarQube<br/>(EC2)]
    Jenkins -->|Security Scan| Trivy[Trivy]
    Jenkins -->|Push Images| ECR[Amazon ECR]
    Jenkins -->|Deploy via Helm| EKS[Amazon EKS]

    subgraph EKS["Amazon EKS Cluster"]
        subgraph AppNS["Namespace: shopease"]
            FE[Frontend]
            US[User Service]
            PS[Product Service]
            DB[(Postgres<br/>StatefulSet + PVC)]
        end

        subgraph MonNS["Namespace: monitoring"]
            Prom[Prometheus]
            Graf[Grafana]
            Alert[Alertmanager]
        end
    end

    FE --> US
    FE --> PS
    US --> DB
    PS --> DB

    AppNS -->|Metrics| Prom
    Prom --> Graf
    Prom --> Alert
```

The system is intentionally divided into three independent layers, mirroring real-world production architectures:

- CI/CD & Automation
- Application Runtime (Kubernetes)
- Observability & Monitoring

### Application Runtime

Applications run on Kubernetes,packaged and deployed using Helm.

Namespace: ```shopease```

Deployed workloads:

- ``frontend`` – UI service (Deployment)
- ``user-service`` – Backend microservice (Deployment)
- ``product-service`` – Backend microservice (Deployment)
- ``postgres`` – Database (StatefulSet + PVC)

Runtime characteristics

- Containers run as non-root
- Persistent storage managed via PVCs
- Init containers enforce startup ordering
- Internal communication via Kubernetes Services

This setup ensures secure defaults, correct stateful behavior, and production-aligned Kubernetes patterns.

### Observability & Monitoring

Monitoring is deployed as cluster-level infrastructure, decoupled from the CI/CD pipeline.

Namespace: ``monitoring``

Components:

- Prometheus (StatefulSet + PVC)
- Grafana (Dashboards & visualization)
- Alertmanager
- kube-state-metrics
- node-exporter

Monitoring is installed once per cluster and operates independently of application deployments.

This avoids unnecessary redeployments and reflects real-world platform engineering practices.

### CI/CD Pipeline Workflow

The Jenkins pipeline follows a stage-based DevSecOps workflow:

1. Source code checkout from GitHub
2. Parallel unit testing for backend microservices
3. Static code analysis using SonarQube
4. Quality gate enforcement to prevent insecure builds
5. Docker image build for all services
6. Container image vulnerability scanning using Trivy
7. Push images to container registry(Amazon ECR)
8. Kubernetes secret creation using Jenkins credentials
9. Security scanning of Helm/Kubernetes manifests using Trivy
10. Deployment to Kubernetes(Amazon EKS) using Helm
11. Post-deployment observability validation

This workflow ensures **secure, repeatable, and automated deployments**.
Jenkins is **intentionally scoped to applicaion delivery only**.
Cluster-wide infrastructure (e.g., monitoring) is not redeployed on every pipeline execution.

---

## Repository Structure
```
microservice-shopease/
├── frontend/
│   └── src/
|   └── .dockerignore
|   └── Dockerfile
|   └── nginx.conf
|   └── package.json
│
├── user-service/
|   └── src/
|   └── .dockerignore
|   └── Dockerfile
|   └── pom.xml
|   └── sonar-project.properties
|
├── product-service/
|   └── src/
|   └── .dockerignore
|   └── Dockerfile
|   └── pom.xml
|   └── sonar-project.properties
│
├── helm/
│   └── shopease-hc/
│       ├── templates/
│       └── values.yaml
|       └── Chart.yaml
|   └── monitoring/
|       └── Dashboards/
|       └── README.md
|       └── values.yaml
|       └── monitoring/(Kept this folder for manual deployment of Prometheus, Grafana, Alertmanager without Helm)
|           └── prometheus.yaml
|           └── prometheus-alerts.yaml
|           └── prometheus-rbac.yaml
|           └── grafana.yaml
|           └── alertmanager.yaml
|
├── scripts/
│   ├── env.sh
│   ├── check-cluster.sh
│   ├── deploy-monitoring.sh
│   ├── show-monitoring-info.sh
│   ├── rollout-status.sh
│   ├── logs.sh
│   └── cleanup.sh
|
├── .trivyignore
├── docker-compose.yaml
├── Jenkinsfile
├── .gitignore
└── README.md
```

---

## Containerization (Docker)

- Each microservice uses a multi-stage Dockerfile
- Build and runtime stages are separated
- Lightweight runtime images are used to reduce attack surface

This improves **security, portability, and deployment consistency**.

---

## Kubernetes & Helm Deployment

### Kubernetes

- Services are deployed as **Kubernetes Deployments**
- Health probes (startup, readiness, liveness) ensure application reliability
- Resource requests and limits enforce controlled resource usage
- StatefulSet with persistent storage is used for PostgreSQL

### Helm

- Helm charts manage Kubernetes manifests
- Values files enable environment-specific configuration
- Helm enables **versioned and repeatable deployments**

This setup mirrors **real-world Kubernetes deployment patterns**.

---

## DevSecOps Integration

### Static Code Analysis (SonarQube)

- Jenkins integrates SonarQube for code quality checks
- Quality gates enforce minimum standards before deployment

### Container & Configuration Security (Trivy)

- Docker images are scanned for HIGH and CRITICAL vulnerabilities
- Helm and Kubernetes manifests are scanned for misconfigurations
- Pipeline execution fails on critical security findings

Security is treated as a **first-class citizen** throughout the CI/CD lifecycle.

---

## Testing & Quality Assurance

### Testing Strategy

- Unit Tests
  - Validate core service logic using JUnit and Mockito
- Integration Tests
  - Database interactions tested using Testcontainers with PostgreSQL

### CI Integration

- Tests run automatically via:

```bash
mvn clean verify
```

- Integration tests execute as part of the Maven lifecycle
- Test failures immediately fail the pipeline

### Code Coverage

- JaCoCo generates coverage reports for visibility
- Coverage reports are reviewed but strict percentage gates are intentionally not enforced

This avoids artificial test inflation and keeps the focus on meaningful testing and CI stability.

---

## Monitoring & Alerting

This project implements production-style monitoring and alerting for Kubernetes-based microservices using Prometheus, Grafana, and Alertmanager.

### Monitoring Architecture

- **Prometheus** is deployed inside the Kubernetes cluster and uses Kubernetes service discovery to automatically detect and scrape application metrics.
- **Microservices** expose metrics via Spring Boot Actuator (/actuator/prometheus), enabled through service annotations.
- **Grafana** queries Prometheus as a data source to visualize service health and performance.
- **Alertmanager** receives alerts from Prometheus and manages alert grouping, deduplication, and routing.

```
Application → Prometheus → (Metrics) → Grafana
              Prometheus → (Alerts)  → Alertmanager
```

### Metrics Collection

Prometheus dynamically scrapes services annotated with:

```yaml
prometheus.io/scrape: "true"
prometheus.io/path: /actuator/prometheus
prometheus.io/port: "8081" / "8082"
```
- Microservices expose metrics via Spring Boot Actuator (``/actuator/prometheus``)
- Prometheus discovers targets automatically using Kubernetes-native mechanisms

Collected metrics include:

  - Service availability (up)
  - HTTP request rate and error rate
  - Latency (P95) using Prometheus histograms
  - JVM CPU usage
  - JVM heap memory usage

Metrics are labeled by service and namespace, enabling clean dashboards and scalable alerting.

### Grafana Dashboards

Grafana dashboards provide:

- Service selector variable to dynamically switch between services for multi-service monitoring
- Service availability (UP / DOWN)
- Request rate and 5xx error rate
- Latency (P95)
- CPU usage per service
- JVM heap memory usage

Dashboards are designed to be **service-centric**, avoiding pod IPs and low-level noise, making them suitable for both operational monitoring.

Dashboards were created via the Grafana UI and exported as JSON for version control.

### Alerting (Prometheus + Alertmanager)

Prometheus evaluates alert rules defined via ConfigMaps and mounted into the Prometheus container.

Alert rules are defined using PrometheusRule resources via Helm

Key alerts include:

- **ServiceDown** – triggered when a service disappears from Prometheus targets.
- **HighCPUUsage** – CPU usage above 80% for sustained periods.
- **HighJVMMemoryUsage** – JVM heap usage exceeding safe thresholds.

Alerts are forwarded to Alertmanager, which:

- Groups related alerts
- Prevents alert storms
- Supports silencing and future notification integrations

Alerts were validated by intentionally scaling services to zero replicas and observing alert state transitions.

Alerts include service and namespace labels, making correlation with dashboards straightforward.

---

## Monitoring Deployment Model

- Monitoring stack is not deployed by Jenkins
- Deployed separately via Helm
- Jenkins only verifies presence, never mutates monitoring state

This avoids coupling CI/CD with cluster observabiity lifecycle

---

## Operational Automation (Bash Scripts)

Bash scripts are used to **improve operational usability**, without replacing Jenkins or Helm.

Scripts provide:

- Cluster and tool pre-flight validation
- One-command monitoring stack deployment
- Deployment rollout status checks
- Centralized log access for services
- Safe cleanup of non-production monitoring resources

These scripts reduce repetitive manual commands and standardize common operational workflows.

### Script Execution Scope

All operational scripts in this project are written in Bash and are intended to be
executed on Linux environments (Ubuntu preferred).

Windows support is intentionally out of scope to avoid non-production Bash-on-Windows behavior. CI/CD automation remains fully handled by Jenkins.

---

<!-- ## Windows Jenkins + Local Kubernetes: Important Note

When running Jenkins as a Windows service, Kubernetes authentication requires a kubeconfig for the service account

= Kubernetes access requires a valid kubeconfig for the service account
- Docker Desktop Kubernetes is the recommended local cluster
- Jenkins does not manage cluster lifecycle

This avoids state corruption and authentication issues. -->

---

## How to Run the Project

### Prerequisites

- Docker
- Kubernetes cluster (local or managed)
- Helm
- Jenkins
- SonarQube
- Trivy

### High-Level Execution Flow

1. Configure Jenkins with required credentials
2. Push code changes to the Git repository
3. Jenkins pipeline triggers automatically
4. Services are built, scanned, and deployed to Kubernetes

  Detailed environment-specific setup steps are intentionally abstracted to keep the focus on **CI/CD pipeline design and DevSecOps concepts**.

---

## Current Limitations

- Designed and tested in a limited environment
- No external traffic or load testing
- Observability limited to Kubernetes health checks
- Focus remains on CI/CD and security automation

These constraints are intentional to keep the project focused and explainable.

---

## Key Learnings

- Designing secure CI/CD pipelines using Jenkins
- Integrating security scanning into build pipelines
- Containerizing microservices using Docker
- Managing Kubernetes deployments using Helm
- Applying DevSecOps principles in real-world workflows
- Building service-level observability using Prometheus and Grafana
- Cost-conscious cloud experimentation

