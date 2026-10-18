# Ansible

Configures the two EC2 servers created by `infra/terraform/bootstrap-infra`: a Jenkins server and a SonarQube server. Nothing on them is set up by hand. Jenkins is configured with Jenkins Configuration as Code (JCasC) and starts with both pipeline jobs already present.

```text
infra/ansible/
  ansible.cfg            inventory, SSH user and key, pipelining
  aws_ec2.yaml           dynamic inventory: finds the hosts by EC2 tag
  playbook.yaml          the plays
  requirements.yaml      Galaxy collections: community.docker, amazon.aws
  group_vars/
    all/vars.yaml        tool versions (kubectl, Helm, Trivy, Sonar scanner), region, cluster name
    all/vault.yaml.example   the shape of the encrypted vault
    jenkins/vars.yaml    Jenkins admin user name and the Git repository URL
    sonarqube/vars.yaml  SSH hop through the Jenkins host
  roles/
    common/              apt update, base packages (git, unzip, curl, wget, tar, gnupg, jq, ...), timezone
    docker/              Docker CE; adds the ubuntu user to the docker group
    devops-tools/        AWS CLI, kubectl, Helm, Trivy, Terraform
    sonarqube/           the SonarQube container, admin password, token, webhook
    jenkins/             Java 21, Jenkins, plugins, JCasC
```

Run it through `python3 scripts/python/aws/bootstrap.py ansible` (see [../../scripts/README.md](../../scripts/README.md)), which handles the vault, installs the collections and runs the two phases below. Running `ansible-playbook` by hand works too, but then you must do the two phases yourself.

## Inventory

`aws_ec2.yaml` uses the `amazon.aws.aws_ec2` plugin in `ap-south-1` and keeps running instances tagged `Project: catalogix`. The tag `Role` becomes the group name, so there are two groups, `jenkins` and `sonarqube`. Hosts are found by tag, so changing IP addresses does not matter. Change the region and tag here if you change them in Terraform.

Jenkins sits in a public subnet and is reached on its public IP. SonarQube is in a private subnet with no public address, so `group_vars/sonarqube/vars.yaml` makes Ansible reach it through the Jenkins host (an SSH `ProxyCommand`). SSH uses the user `ubuntu` and the key `~/.ssh/catalogix-key` created by `python3 scripts/python/setup/create_ssh_key.py`.

## Two phases

| Phase | Command | What it does |
|---|---|---|
| 1 | `ansible-playbook playbook.yaml --limit 'all:!jenkins'` | Baseline (`common`, `docker`) on the SonarQube host, then the `sonarqube` role |
| 2 | `ansible-playbook playbook.yaml --limit jenkins` | Baseline, `devops-tools` and `jenkins` on the Jenkins host |

They run as separate processes on purpose. The `sonarqube` role generates a SonarQube token and writes it into the encrypted vault file; Ansible reads vault variables once at start-up, so Jenkins has to start in a new process to see the token. All tasks are idempotent: tool installs check the installed version first, and the SonarQube token and admin password steps skip when already done.

### `sonarqube` role

Sets `vm.max_map_count` (needed by its search engine), creates `/opt/sonarqube/{data,logs,extensions}`, runs the container `sonarqube:26.3.0.120487-community` on port 9000 with small JVM heaps, waits until it is healthy, changes the default admin password to `vault_sonar_admin_password`, creates a user token named `jenkins-token` (replacing a stale one that is not in the vault), writes it into `vault_sonar_token`, and creates the `Jenkins` webhook that tells Jenkins when a quality gate finishes.

### `jenkins` role

Installs Java 21 and Jenkins from the official apt repository (the playbook removes a stale signing key first), adds the `jenkins` user to the `docker` group, writes `/etc/default/jenkins` from `jenkins_defaults.j2` (all secrets as environment variables), copies `plugins.yaml` and installs the plugins with `jenkins-plugin-cli`, deploys `jcasc.yaml`, restarts Jenkins with JCasC active, reloads the configuration through the API and checks that the admin can log in.

## JCasC (`roles/jenkins/templates/jcasc.yaml`)

| Section | What it configures |
|---|---|
| Security | Local admin user from `JENKINS_ADMIN_USER` and `JENKINS_ADMIN_PASSWORD`, no sign-up, no anonymous read, CSRF protection |
| Shared library | `catalogix-shared-library`, loaded from the `vars/` directory of `main` in `GIT_REPO_URL` |
| SonarQube | Server `sonarqube` at `http://<private ip>:9000` with the `sonarqube-token` credential |
| Tools | `git`, Maven 3.9.9, Node.js 24.11.0 (keep it in step with the frontend Dockerfile, `.nvmrc` and `engines`) |
| Credentials | `github-token` and `sonarqube-token`, from environment variables |
| Jobs | `platform-infra` (`scriptPath('infra/jenkins/Jenkinsfile.platform-infra')`) and `app-cicd` (`scriptPath('infra/jenkins/Jenkinsfile.app-cicd')`, with a `githubPush()` trigger), both on `*/main` |

`unknown: reject` and `restricted: reject` make JCasC fail loudly on a typo instead of ignoring it. To change the job definitions or the trigger, edit this file and re-run `python3 scripts/python/aws/bootstrap.py ansible` (see [../../docs/github-webhook.md](../../docs/github-webhook.md)). Editing a job in the Jenkins UI is overwritten the next time JCasC is applied.

Plugins are listed in `roles/jenkins/files/plugins.yaml`: Docker Pipeline, Configuration as Code, Matrix Authorization, Credentials and Credentials Binding, Pipeline (workflow-aggregator), Pipeline Graph View, Git, GitHub and GitHub Branch Source, Multibranch, Job DSL, NodeJS, Maven, SonarQube Scanner, AWS Credentials, Timestamper, Workspace Cleanup and the dark theme.

## Variables and the vault

| Where | Variable | Meaning |
|---|---|---|
| `group_vars/all/vars.yaml` | `kubectl_version`, `helm_version`, `trivy_version`, `sonar_scanner_version` | Tool versions. The pipelines and the local scan scripts read `trivy_version` and `sonar_scanner_version` from here, so Jenkins and your laptop run the same scanners |
| `group_vars/all/vars.yaml` | `aws_region`, `cluster_name` | Not referenced by any role at the moment; kept as documentation of the defaults |
| `group_vars/jenkins/vars.yaml` | `jenkins_admin_user`, `git_repo_url` | **Set `git_repo_url` to your own repository.** It is marked `UPDATE THIS` and is where Jenkins clones from and loads the shared library from |
| `group_vars/all/vault.yaml` (encrypted) | `vault_jenkins_admin_password`, `vault_github_token`, `vault_sonar_admin_password`, `vault_sonar_token` | Created interactively by `bootstrap.py ansible`. `vault_sonar_token` starts empty and is filled in by the `sonarqube` role |

The vault password is stored in `~/.vault_pass` (mode 600). `vault.yaml` is git-ignored through the repository's ignore rules for secrets; never commit it unencrypted. The GitHub token needs permission to read the repository.

## Checks

```bash
cd infra/ansible
ansible-inventory --graph           # both groups should list one host each (needs AWS credentials)
ansible-lint playbook.yaml          # also run by Jenkinsfile.lint; there is no ansible-lint config, so default rules apply
```
