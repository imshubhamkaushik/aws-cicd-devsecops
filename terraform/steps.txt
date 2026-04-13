echo "Step 1: Creating S3 remote state bucket..."
cd terraform/backend-bootstrap
terraform init
terraform apply -auto-approve

echo "Step 2: Provisioning Jenkins, SonarQube, VPC..."
cd ../bootstrap-infra
terraform init
terraform apply -auto-approve

echo "Step 3: Provisioning EKS, RDS, ECR..."
cd ../../platform-infra/env/dev
terraform init
terraform apply -auto-approve