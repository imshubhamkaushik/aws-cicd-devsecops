# Triggering Jenkins from GitHub

By default a push to `main` does **not** start `app-cicd`, even though the job has a `githubPush()` trigger. The Jenkins security group allows port 8080 only from your operator IPs, and GitHub's servers are not among them. This page shows three ways to deal with that. Pick one.

| Option | Inbound access to Jenkins | Delay | Best for |
|---|---|---|---|
| A. Poll the repository | None | Up to 5 minutes | Default choice for this project |
| B. Real webhook, open port 8080 to GitHub's hook ranges | GitHub hook ranges on 8080 | Seconds | When you want instant builds and accept the trade-offs below |
| C. Start the job by hand | None | Manual | Occasional demos |

## Option A: poll instead of waiting for a webhook (no inbound access)

Jenkins asks GitHub every few minutes whether `main` has a new commit. Nothing has to reach Jenkins from outside.

1. In `infra/ansible/roles/jenkins/templates/jcasc.yaml`, in the `app-cicd` job, replace

   ```groovy
   triggers {
     githubPush()
   }
   ```

   with

   ```groovy
   triggers {
     pollSCM('H/5 * * * *')
   }
   ```

2. Commit and push the change.
3. Re-apply Jenkins configuration: `python3 scripts/python/bootstrap.py ansible`.
4. In Jenkins open `app-cicd`, then **Configure** and check that *Poll SCM* shows `H/5 * * * *`.
5. Push a commit to `main`. Within about five minutes the job starts, and the build cause reads *Started by an SCM change*.

## Option B: a real GitHub webhook

### Understand the trade-offs first

- GitHub sends hooks from a published list of IP ranges that **every** GitHub user's webhooks share. Anyone can create a webhook that points at your Jenkins address, and it will arrive from an allowed range.
- The `/github-webhook/` endpoint accepts requests without a login by design. Jenkins' other pages still ask for a login, but port 8080 is now reachable from those ranges.
- The traffic is plain HTTP, so the payload (repository and commit metadata) is unencrypted. Use TLS in front of Jenkins before using this beyond a lab.
- The job only builds `*/main`, so a forged hook can at worst cause extra builds.
- GitHub changes the ranges from time to time. The rules below are a snapshot, so repeat the steps now and then.

### 1. Allow GitHub's hook ranges on port 8080

Run on a machine with the AWS CLI and `jq`, from the repository root. It adds one rule per IPv4 range, each described `github-webhook` so you can find and remove them later.

```bash
SG=$(cd infra/terraform/bootstrap-infra && terraform output -raw jenkins_sg_id)

# How many ranges are there? A security group allows 60 inbound rules by default,
# and your operator IPs use some of them.
curl -s https://api.github.com/meta | jq -r '.hooks[] | select(contains(":") | not)' | wc -l

for cidr in $(curl -s https://api.github.com/meta | jq -r '.hooks[] | select(contains(":") | not)'); do
  aws ec2 authorize-security-group-ingress --group-id "$SG" \
    --ip-permissions "IpProtocol=tcp,FromPort=8080,ToPort=8080,IpRanges=[{CidrIp=$cidr,Description=github-webhook}]" \
    || true     # an "already exists" error is fine when you re-run this
done
```

These rules are not managed by Terraform. Terraform does not delete rules it does not know about, so they survive a later `terraform apply` of the network layer. They disappear when the security group is destroyed with the rest of `bootstrap-infra`.

### 2. Add the webhook in GitHub

1. Open your repository, then **Settings**, **Webhooks**, **Add webhook**.
2. **Payload URL:** `http://<jenkins-public-ip>:8080/github-webhook/` (keep the trailing slash). The address is the Jenkins Elastic IP: `cd infra/terraform/bootstrap-infra && terraform output -raw public_ip_jenkins`. It does not change while the deployment exists.
3. **Content type:** `application/json`.
4. **Which events:** *Just the push event*.
5. Leave **Active** ticked and save.

### 3. Check it

- GitHub: the webhook page, **Recent Deliveries**, shows a green tick and `200`.
- Jenkins: push a commit to `main`. `app-cicd` starts and says *Started by GitHub push*.
- The `github` plugin and the `githubPush()` trigger are already set up by the Ansible/JCasC step, and Jenkins needs no extra setting for this.

### Remove the rules later

```bash
SG=$(cd infra/terraform/bootstrap-infra && terraform output -raw jenkins_sg_id)
for cidr in $(aws ec2 describe-security-groups --group-ids "$SG" \
    --query "SecurityGroups[0].IpPermissions[?FromPort==\`8080\`].IpRanges[?Description=='github-webhook'].CidrIp[]" \
    --output text); do
  aws ec2 revoke-security-group-ingress --group-id "$SG" --protocol tcp --port 8080 --cidr "$cidr"
done
```

## Option C: start the job by hand

In Jenkins open `app-cicd`, then **Build with Parameters**, and start it. Nothing else is needed.

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Delivery shows a timeout | Port 8080 is not open to GitHub | Do step 1 of Option B, and check the instance's public IP |
| Delivery shows `403` | A reverse proxy or the CSRF setting blocks the request | Check the URL has the trailing slash; do not put a login in front of `/github-webhook/` |
| Delivery shows `200` but no build starts | The push was not to `main`, or the job has not been created | The job builds `*/main` only; confirm the job exists on the dashboard |
| Builds start but cannot clone | The `github-token` credential cannot read the repo | Create a token that can read it and update the vault, then re-run the Ansible step |
| Polling never triggers | JCasC was not re-applied | Re-run `python3 scripts/python/bootstrap.py ansible` and check **Configure** on the job |
