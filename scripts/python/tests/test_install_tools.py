import sys
import unittest
from pathlib import Path
from unittest import mock

PYTHON_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PYTHON_DIR))
import install_tools  # noqa: E402


class InstallerSelectionTests(unittest.TestCase):
    def test_select_all_missing_by_default(self):
        with mock.patch("builtins.input", return_value=""):
            self.assertEqual(install_tools.select_missing(["jq", "aws"]), ["jq", "aws"])

    def test_select_subset_by_name_or_number(self):
        with mock.patch("builtins.input", side_effect=["s", "2,aws"]):
            self.assertEqual(install_tools.select_missing(["jq", "aws", "terraform"]), ["aws"])

    def test_quit_returns_no_tools(self):
        with mock.patch("builtins.input", return_value="q"):
            self.assertEqual(install_tools.select_missing(["jq"]), [])

    def test_unknown_selected_tool_is_rejected(self):
        with mock.patch("builtins.input", side_effect=["s", "unknown"]):
            with self.assertRaises(SystemExit):
                install_tools.select_missing(["jq", "aws"])

    def test_terraform_target_keeps_matching_requested_version(self):
        self.assertIsNone(install_tools.resolve_terraform_target("1.12.2", "1.12.2", False, "1.13.0"))

    def test_terraform_target_replaces_mismatched_requested_version(self):
        self.assertEqual(install_tools.resolve_terraform_target("1.12.1", "1.12.2", False, None), "1.12.2")

    def test_terraform_upgrade_selects_latest_version(self):
        self.assertEqual(install_tools.resolve_terraform_target("1.12.1", None, True, "1.13.0"), "1.13.0")

    def test_terraform_without_requested_upgrade_keeps_existing_version(self):
        self.assertIsNone(install_tools.resolve_terraform_target("1.12.1", None, False, None))
