import os
import subprocess
import sys
import unittest
from pathlib import Path
from unittest import mock

REPO_ROOT = Path(__file__).resolve().parents[3]
PYTHON_DIR = REPO_ROOT / "scripts" / "python"
LOCAL_DIR = PYTHON_DIR / "local"
sys.path.insert(0, str(LOCAL_DIR))
import devsecops_check  # noqa: E402


class LocalCliTests(unittest.TestCase):
    def test_mvn_list_services_does_not_require_maven_or_an_action(self):
        result = subprocess.run(
            [sys.executable, str(LOCAL_DIR / "mvn.py"), "--list-services"],
            cwd=REPO_ROOT, capture_output=True, text=True, check=False,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("user-svc", result.stdout)

    def test_skip_trivy_still_builds_images_before_smoke_test(self):
        calls = []

        def record(*args):
            calls.append(args)

        with mock.patch.object(sys, "argv", ["devsecops_check.py", "--skip-trivy", "--skip-gitleaks", "--skip-sonar"]), \
                mock.patch.object(devsecops_check, "run_python", side_effect=record):
            self.assertEqual(devsecops_check.main(), 0)

        build_index = calls.index(("compose.py", "build"))
        start_index = calls.index(("compose.py", "start", "--no-build"))
        smoke_index = calls.index(("smoke_test.py",))
        self.assertLess(build_index, start_index)
        self.assertLess(start_index, smoke_index)
