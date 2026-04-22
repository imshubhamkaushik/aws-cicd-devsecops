import subprocess
import time
import sys
import os

def info(msg):
    print(f"[INFO] {msg}")


def error(msg):
    print(f"[ERROR] {msg}")
    sys.exit(1)
    
def warn(msg):
    print(f"[WARN] {msg}")
    
def _print_error_output(e, capture_output):
    if capture_output and e.stdout:
        print(e.stdout)
    if capture_output and e.stderr:
        print(e.stderr)


def run_command(cmd, cwd=None, capture_output=False, retries=3, delay=5, env=None):
    """
    Run shell command with retry logic (for network issues like S3 backend).
    """
    for attempt in range(retries):
        try:
            result = subprocess.run(
                cmd,
                cwd=cwd,
                shell=True,
                check=True,
                text=True,
                capture_output=capture_output,
                env={**os.environ, **(env or {})}
            )
            return result.stdout if capture_output else None
        except subprocess.CalledProcessError as e:
            warn(f"\nCommand failed (attempt {attempt+1}/{retries}): {cmd}")
            _print_error_output(e, capture_output)
            if attempt < retries - 1:
                time.sleep(delay * (attempt + 1))
            else:
                error(f"Command failed after {retries} attempts: {cmd}")