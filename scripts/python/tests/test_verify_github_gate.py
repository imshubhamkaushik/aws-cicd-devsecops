import sys
import unittest
from pathlib import Path

CI_DIR = Path(__file__).resolve().parents[1] / "ci"
sys.path.insert(0, str(CI_DIR))
import verify_github_gate as gate  # noqa: E402


def run(name="ci-gate", status="completed", conclusion="success", run_id=1):
    return {"id": run_id, "name": name, "status": status, "conclusion": conclusion,
            "html_url": f"https://github.com/o/r/runs/{run_id}"}


class FakeClock:
    def __init__(self):
        self.now = 0.0

    def clock(self):
        return self.now

    def sleep(self, seconds):
        self.now += seconds


class ParseRepoTests(unittest.TestCase):
    def test_https_ssh_and_plain(self):
        self.assertEqual(gate.parse_repo("https://github.com/me/proj.git"), ("me", "proj"))
        self.assertEqual(gate.parse_repo("git@github.com:me/proj.git"), ("me", "proj"))
        self.assertEqual(gate.parse_repo("https://github.com/me/proj"), ("me", "proj"))

    def test_rejects_other_hosts(self):
        with self.assertRaises(gate.GateError):
            gate.parse_repo("https://example.com/me/proj.git")


class ClassifyTests(unittest.TestCase):
    def test_no_check_run_yet_is_pending(self):
        self.assertEqual(gate.classify({"check_runs": []}, "ci-gate")[0], gate.PENDING)
        self.assertEqual(gate.classify({"check_runs": [run(name="other")]}, "ci-gate")[0], gate.PENDING)

    def test_running_is_pending(self):
        payload = {"check_runs": [run(status="in_progress", conclusion=None)]}
        self.assertEqual(gate.classify(payload, "ci-gate")[0], gate.PENDING)

    def test_success_and_failure(self):
        self.assertEqual(gate.classify({"check_runs": [run()]}, "ci-gate")[0], gate.SUCCESS)
        for conclusion in ("failure", "cancelled", "skipped", "timed_out", None):
            payload = {"check_runs": [run(conclusion=conclusion)]}
            self.assertEqual(gate.classify(payload, "ci-gate")[0], gate.FAILURE, conclusion)

    def test_latest_rerun_wins(self):
        payload = {"check_runs": [run(conclusion="failure", run_id=1), run(conclusion="success", run_id=2)]}
        self.assertEqual(gate.classify(payload, "ci-gate")[0], gate.SUCCESS)
        payload = {"check_runs": [run(conclusion="success", run_id=1), run(conclusion="failure", run_id=2)]}
        self.assertEqual(gate.classify(payload, "ci-gate")[0], gate.FAILURE)


class WaitTests(unittest.TestCase):
    def wait(self, responses, timeout=100, interval=10):
        fake = FakeClock()
        calls = iter(responses)

        def fetch():
            item = next(calls)
            if isinstance(item, Exception):
                raise item
            return item

        result = gate.wait_for_gate(fetch, "ci-gate", timeout=timeout, interval=interval,
                                    sleep=fake.sleep, clock=fake.clock, log=lambda _msg: None)
        return result, fake.now

    def test_waits_until_success(self):
        pending = {"check_runs": [run(status="queued", conclusion=None)]}
        (state, _), elapsed = self.wait([{"check_runs": []}, pending, {"check_runs": [run()]}])
        self.assertEqual(state, gate.SUCCESS)
        self.assertEqual(elapsed, 20)

    def test_transient_errors_are_retried(self):
        (state, _), _ = self.wait([gate.TransientError("HTTP 502"), {"check_runs": [run()]}])
        self.assertEqual(state, gate.SUCCESS)

    def test_failure_returns_immediately(self):
        (state, _), elapsed = self.wait([{"check_runs": [run(conclusion="failure")]}])
        self.assertEqual(state, gate.FAILURE)
        self.assertEqual(elapsed, 0)

    def test_timeout_is_a_failure(self):
        never = [{"check_runs": []}] * 50
        (state, message), _ = self.wait(never, timeout=30, interval=10)
        self.assertEqual(state, gate.FAILURE)
        self.assertIn("timed out", message)

    def test_fatal_errors_propagate(self):
        with self.assertRaises(gate.GateError):
            self.wait([gate.GateError("HTTP 401")])


class MainTests(unittest.TestCase):
    def test_missing_token_fails(self):
        import os
        from unittest import mock
        with mock.patch.dict(os.environ, {}, clear=True):
            self.assertEqual(gate.main(["--repo-url", "https://github.com/o/r.git", "--sha", "a" * 40]), 1)

    def test_bad_sha_fails(self):
        import os
        from unittest import mock
        with mock.patch.dict(os.environ, {"GH_TOKEN": "x"}):
            self.assertEqual(gate.main(["--repo-url", "https://github.com/o/r.git", "--sha", "main"]), 1)


if __name__ == "__main__":
    unittest.main()
