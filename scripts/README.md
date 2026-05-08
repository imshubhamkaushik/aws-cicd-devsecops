Bash Scripts
Consider bash scripts only if using linux OS. This doesnt work on Windows OS

Python Scripts
bootstrap.py           → orchestrates backend_bootstrap.py → bootstrap_infra.py → ansible.py
backend_bootstrap.py   → backend terraform only
bootstrap_infra.py     → provisions bootstrap infra only
ansible.py             → ansible configuration only
destroy_infra.py       → destroy bootstrap infra only

Go to root directory of project
Use command "python scripts/python/bootstrap.py" or "python scripts/python/bootstrap.py full" for full automation (backend_bootstrap --> bootstrap_infra --> ansible)
or "python scripts/python/bootstrap.py backend" to execute backend_bootstrap.py only
or "python scripts/python/bootstrap.py infra" to execute bootstrap_infra.py only
or "python scripts/python/bootstrap.py ansible" to execute ansible.py only


HOW TO RUN PROJECT
- Create an AWS Account.
- Create an IAM User (Save the Access key and Secret key. You can go for AdministratorAccess for ease).
- Download and Install AWS CLI
- Configure the AWS CLI using "aws configure"
- Clone the project repo on the local where you will run the scripts. Make sure Python, Terraform and Ansible are installed on the local system to run the scripts.
- Go to the root directory of project repo and run the scripts
    - Use command "python scripts/python/bootstrap.py" or "python scripts/python/bootstrap.py full" for full automation (backend_bootstrap --> bootstrap_infra --> ansible)
    - or "python scripts/python/bootstrap.py backend" to execute backend_bootstrap.py only
    - or "python scripts/python/bootstrap.py infra" to execute bootstrap_infra.py only
    - or "python scripts/python/bootstrap.py ansible" to execute ansible.py only
- While ansible script runs, the script looks for vault.yaml and for injecting
- Wait until evrything is ready.
- Login to Jenkins using http://<jenkins-public-ip>:8080 and the credentials you stored in vault.yaml
- As per the Jenkins Configuration-as-Code, platform-infra and app-cicd pipelines are already on the Jenkins dashboard
- Run platform-infra pipeline. 
    - It will ask for "DEPLOY_APP_AFTER_INFRA". By default, it will run app-cicd pipeline after successful run of platform-infra pipeline. If you don't want to run app-cicd then you can unmark or untick the option. 
    - Also there is choice for "APPLY" or "DESTROY" for the pipeline. By default, "APPLY" will be done which will fully provision the terraform resources. This option acts as safety gate so that accidental 'destroy' wont happen. If you want to destroy the resources after use, you can run the platform-infra pipeline with "DESTROY" option. BE CAREFUL WITH THE "DESTORY" OPTION.
- After platform-infra pipeline run successfully, run app-cicd pipeline if you didnt mark or tick true for "DEPLOY_APP_AFTER_INFRA".

After everything is done, you can run platform-infra pipeline and select "DESTROY" to destroy the terraform resources provisioned using "APPLY" option.
After destroying resources provisioned from platform-infra pipeline, destroy rest of the terraform resources using destroy.py script. This will destroy the resources provisioned at the start using scripts.