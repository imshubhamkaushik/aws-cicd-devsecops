import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

PYTHON_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PYTHON_DIR))
from aws import ansible_runner as runner  # noqa: E402


class VaultRecoveryTests(unittest.TestCase):
    def test_failed_recovery_preserves_encrypted_vault_and_existing_password_file(self):
        with tempfile.TemporaryDirectory() as temp:
            temp_path = Path(temp)
            vault = temp_path / "vault.yaml"
            password_file = temp_path / ".vault_pass"
            vault.write_text("$ANSIBLE_VAULT;1.1;AES256\nencrypted-payload\n")
            password_file.write_text("previous-password\n")

            with mock.patch.object(runner, "VAULT_FILE", vault), \
                    mock.patch.object(runner, "VAULT_PASSWORD_FILE", password_file), \
                    mock.patch.object(runner, "_prompt_for_vault_password", return_value="wrong-password"), \
                    mock.patch.object(runner, "_is_vault_password_valid", return_value=False), \
                    self.assertRaises(SystemExit):
                runner._handle_invalid_vault_password()

            self.assertEqual(vault.read_text(), "$ANSIBLE_VAULT;1.1;AES256\nencrypted-payload\n")
            self.assertEqual(password_file.read_text(), "previous-password\n")

    def test_correct_recovery_password_replaces_password_file_only_after_validation(self):
        with tempfile.TemporaryDirectory() as temp:
            password_file = Path(temp) / ".vault_pass"
            password_file.write_text("old-password")
            with mock.patch.object(runner, "VAULT_FILE", Path(temp) / "vault.yaml"), \
                    mock.patch.object(runner, "VAULT_PASSWORD_FILE", password_file), \
                    mock.patch.object(runner, "_prompt_for_vault_password", return_value="correct-password"), \
                    mock.patch.object(runner, "_is_vault_password_valid", return_value=True):
                self.assertTrue(runner._handle_invalid_vault_password())
            self.assertEqual(password_file.read_text(), "correct-password")
            self.assertEqual(password_file.stat().st_mode & 0o777, 0o600)
