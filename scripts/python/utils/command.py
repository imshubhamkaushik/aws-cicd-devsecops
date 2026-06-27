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
    msg = _classify_error(stderr)
    return msg


# Patterns that are worth retrying — genuinely transient, likely to clear up
# on their own (network blips, throttling, eventual-consistency races).
_TRANSIENT_PATTERNS = {
    "unable to list objects in s3 bucket": "S3 backend unreachable. Check internet or AWS region.",
    "connection reset by peer": "Network instability detected. Please retry.",
}

# Patterns that will NOT fix themselves by re-running the exact same command.
# Retrying these just delays the real error message by ~30s for nothing —
# worse, for a mutating command (terraform apply, ansible-playbook) it can
# also waste a partial/duplicate side effect before failing anyway.
_NON_TRANSIENT_PATTERNS = {
    "unable to locate credentials": "AWS credentials not configured. Run: aws configure",
    "accessdenied": "AWS access denied. Check IAM permissions or credentials.",
    "error: invalid": "Terraform configuration error. Check your .tf files.",
    "decryption failed": "Invalid Ansible Vault password.",
}


def _classify_error(stderr: str):
    """Return (message, is_transient) for a known error pattern, or (None, None)."""
    if not stderr:
        return None, None

    lowered = stderr.lower()

    for pattern, msg in _NON_TRANSIENT_PATTERNS.items():
        if pattern in lowered:
            return msg, False

    for pattern, msg in _TRANSIENT_PATTERNS.items():
        if pattern in lowered:
            return msg, True

    return None, None


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
        
    custom_msg, is_transient = _classify_error(stderr)

    if is_transient is False:
        # Known non-transient failure — fail now instead of burning 2 more
        # retries and ~30s on a command that cannot succeed without a change.
        error(custom_msg)
        return
 
    if attempt + 1 == retries:
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
