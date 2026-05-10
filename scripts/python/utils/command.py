import subprocess
import time
import sys
import os

def info(msg):
    print(f"[INFO] {msg}")
    
def warn(msg):
    print(f"[WARN] {msg}")

def error(msg):
    print(f"[ERROR] {msg}")
    sys.exit(1)
    

def _get_custom_error(stderr: str):
    """Return human-friendly error message if known pattern matches."""
    if not stderr:
        return None

    stderr = stderr.lower()

    # Network / S3 backend issues
    if "unable to list objects in s3 bucket" in stderr:
        return "S3 backend unreachable. Check internet or AWS region."

    if "connection reset by peer" in stderr:
        return "Network instability detected. Please retry."

    # AWS issues
    if "unable to locate credentials" in stderr:
        return "AWS credentials not configured. Run: aws configure"

    if "accessdenied" in stderr:
        return "AWS access denied. Check IAM permissions or credentials."

    # Terraform issues
    if "error: invalid" in stderr:
        return "Terraform configuration error. Check your .tf files."

    # Ansible Vault issues
    if "decryption failed" in stderr:
        return "Invalid Ansible Vault password."

    return None


def _execute_once(cmd, cwd, capture_output, merged_env, check):
    """
    Run a single command attempt.
 
    Returns stdout string when capture_output=True, otherwise None.
    Streams output directly to the terminal when capture_output=False —
    important for long-running commands like terraform apply and ansible-playbook.
    When check=False, returns the integer returncode instead of raising an exception on failure.
    """
    if capture_output:
        result = subprocess.run(
            cmd, cwd=cwd, shell=True,
            text=True, capture_output=True, env=merged_env
        )
        if check and result.returncode != 0:
            raise subprocess.CalledProcessError(result.returncode, cmd, result.stdout, result.stderr)
        return result.stdout if check else result.returncode
    
    result = subprocess.run(cmd, cwd=cwd, shell=True, env=merged_env)
    if check and result.returncode != 0:
        raise subprocess.CalledProcessError(result.returncode, cmd)
    return result.returncode if not check else None 
 
def _handle_failure(e, cmd, attempt, retries, delay, capture_output):
    """Log the failure and either retry (sleep) or raise a final error."""
    stderr = (e.stderr or "") if capture_output else ""
 
    warn(f"Command failed (attempt {attempt + 1}/{retries}): {cmd}")
 
    if stderr.strip():
        print(stderr)
 
    if attempt + 1 == retries:
        custom_msg = _get_custom_error(stderr)
        error(custom_msg or f"Command failed after {retries} attempts: {cmd}")
    else:
        time.sleep(delay * (attempt + 1))
 
 
def run_command(cmd, cwd=None, capture_output=False, retries=3, delay=5, env=None, check=True):
    """Run a shell command with retry logic and friendly error messages.
    
    When check=True (default): raises on non-zero exit, retries on failure,
    returns stdout (capture_output=True) or None.

    When check=False: runs once (no retry — retrying a failed plan is wrong),
    returns the integer exit code. Use this only for commands where non-zero
    exit is meaningful (e.g. terraform plan -detailed-exitcode).
    """
    merged_env = {**os.environ, **(env or {})}
    
    if not check:
        # No retry when check=False — the caller is inspecting the exit code,
        # so retrying on a non-zero exit would mask intentional non-zero results.
        return _execute_once(cmd, cwd, capture_output, merged_env, check=False)
 
    for attempt in range(retries):
        try:
            return _execute_once(cmd, cwd, capture_output, merged_env, check=True)
        except subprocess.CalledProcessError as e:
            _handle_failure(e, cmd, attempt, retries, delay, capture_output)
