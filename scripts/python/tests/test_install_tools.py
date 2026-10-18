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
