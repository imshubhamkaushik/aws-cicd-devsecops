import subprocess
import sys
import unittest
from unittest import mock
from pathlib import Path

PYTHON_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PYTHON_DIR))
from common import command  # noqa: E402


class CommandRunnerTests(unittest.TestCase):
    def test_unclassified_failure_is_not_retried(self):
        failure = subprocess.CalledProcessError(1, ["mutating-command"], stderr="unexpected provider error")
        with mock.patch.object(command, "_execute_once", side_effect=failure) as execute:
            with self.assertRaises(SystemExit):
                command.run_command(["mutating-command"], capture_output=True, retries=3)
        self.assertEqual(execute.call_count, 1)

    def test_known_transient_failure_is_retried(self):
        failure = subprocess.CalledProcessError(1, ["read-command"], stderr="connection reset by peer")
        with mock.patch.object(command, "_execute_once", side_effect=[failure, "ok"]) as execute, \
                mock.patch.object(command.time, "sleep") as sleep:
            result = command.run_command(["read-command"], capture_output=True, retries=3)
        self.assertEqual(result, "ok")
        self.assertEqual(execute.call_count, 2)
        sleep.assert_called_once_with(5)

    def test_check_false_executes_once_and_returns_exit_code(self):
        with mock.patch.object(command, "_execute_once", return_value=2) as execute:
            result = command.run_command(["terraform", "plan"], check=False, retries=5)
        self.assertEqual(result, 2)
        execute.assert_called_once()

    def test_string_commands_are_split_without_a_shell(self):
        self.assertEqual(command._normalise_command("terraform plan -destroy"), ["terraform", "plan", "-destroy"])
