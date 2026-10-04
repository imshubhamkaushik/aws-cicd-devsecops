import io
import json
import os
import sys
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import credentials as c  # noqa: E402


class PasswordPolicy(unittest.TestCase):
    def test_accepts_a_good_password(self):
        self.assertEqual(c.password_problems("Str0ngPassw0rd"), [])

    def test_rejects_short(self):
        self.assertTrue(any("8-64" in p for p in c.password_problems("Ab1")))

    def test_rejects_too_long(self):
        self.assertTrue(c.password_problems("Aa1" + "x" * 70))

    def test_needs_each_character_class(self):
        self.assertTrue(any("lowercase" in p for p in c.password_problems("ABCDEFG1")))
        self.assertTrue(any("uppercase" in p for p in c.password_problems("abcdefg1")))
        self.assertTrue(any("digit" in p for p in c.password_problems("Abcdefgh")))

    def test_is_case_sensitive_not_case_folded(self):
        self.assertEqual(c.password_problems("aBcDeFg1"), [])

    def test_rejects_characters_rds_forbids_and_whitespace(self):
        for bad in ("Passw0rd/xx", 'Passw0rd"xx', "Passw0rd@xx", "Pass w0rdxx"):
            self.assertTrue(c.password_problems(bad), bad)

    def test_rejects_non_ascii(self):
        self.assertTrue(c.password_problems("Passw0rdé!"))

    def test_generated_passwords_always_satisfy_the_policy(self):
        for _ in range(200):
            self.assertEqual(c.password_problems(c.generate_password()), [])


class Usernames(unittest.TestCase):
    def test_service_style_usernames(self):
        self.assertEqual(c.username_problems("username", "catalogix"), [])
        self.assertTrue(c.username_problems("username", "Catalogix"))
        self.assertTrue(c.username_problems("username", "1abc"))
        self.assertTrue(c.username_problems("username", "ab"))

    def test_grafana_username(self):
        self.assertEqual(c.username_problems("grafana_user", "Grafana.Admin-1"), [])
        self.assertTrue(c.username_problems("grafana_user", "a b"))


GOOD = {
    "db_master_password": "Str0ngDbPassw0rd",
    "rabbitmq_user": "catalogix",
    "rabbitmq_password": "Str0ngRabbitPw1",
    "seed_admin_password": "Str0ngAdminPw1",
    "grafana_admin_user": "admin",
    "grafana_admin_password": "Str0ngGrafanaPw1",
}


class CheckValues(unittest.TestCase):
    def test_complete_secret_passes(self):
        self.assertEqual(c.check_values(GOOD), [])

    def test_missing_required_keys_are_reported(self):
        values = dict(GOOD)
        del values["db_master_password"]
        self.assertIn("db_master_password: missing", c.check_values(values))

    def test_weak_password_is_reported_without_echoing_it(self):
        values = dict(GOOD, seed_admin_password="weak")
        problems = c.check_values(values)
        self.assertTrue(any(p.startswith("seed_admin_password:") for p in problems))
        self.assertFalse(any("weak" in p for p in problems))

    def test_readonly_login_is_optional_but_needs_both_halves(self):
        self.assertEqual(c.check_values(GOOD), [])
        half = dict(GOOD, db_readonly_password="Str0ngReadOnly1")
        self.assertTrue(any("db_readonly_username" in p for p in c.check_values(half)))
        both = dict(half, db_readonly_username="catalogix_readonly")
        self.assertEqual(c.check_values(both), [])


class CommandLine(unittest.TestCase):
    def test_secret_name_matches_terraform_env_prefix(self):
        self.assertEqual(c.secret_id("dev"), "catalogix-cluster-dev/operator-credentials")
        self.assertEqual(c.secret_id("staging"), "catalogix-staging/operator-credentials")

    def test_check_fails_when_secret_is_missing(self):
        with mock.patch.object(c, "fetch_secret", side_effect=c.SecretNotFound("x")):
            self.assertEqual(c.cmd_check("dev", "ap-south-1"), 1)

    def test_check_passes_for_a_valid_secret(self):
        with mock.patch.object(c, "fetch_secret", return_value=GOOD):
            self.assertEqual(c.cmd_check("dev", "ap-south-1"), 0)

    def test_show_never_prints_passwords(self):
        out = io.StringIO()
        with mock.patch.object(c, "fetch_secret", return_value=GOOD), mock.patch("sys.stdout", out):
            c.cmd_show("dev", "ap-south-1")
        text = out.getvalue()
        self.assertNotIn("Str0ng", text)
        self.assertIn("rabbitmq_user", text)

    def test_store_writes_json_via_a_private_file_not_the_command_line(self):
        captured = {}

        def fake_aws(args, region):
            path = args[args.index("--secret-string") + 1].replace("file://", "")
            captured["mode"] = os.stat(path).st_mode & 0o777
            with open(path) as fh:
                captured["body"] = json.load(fh)
            captured["args"] = args
            return mock.Mock(returncode=0, stderr="")

        with mock.patch.object(c, "_aws", side_effect=fake_aws):
            c.store_secret("dev", "ap-south-1", GOOD, exists=False)
        self.assertEqual(captured["mode"], 0o600)
        self.assertEqual(captured["body"], GOOD)
        self.assertEqual(captured["args"][0], "create-secret")
        self.assertFalse(any("Str0ng" in a for a in captured["args"]))

    def test_store_updates_when_the_secret_exists(self):
        with mock.patch.object(c, "_aws", return_value=mock.Mock(returncode=0, stderr="")) as aws:
            c.store_secret("dev", "ap-south-1", GOOD, exists=True)
        self.assertEqual(aws.call_args[0][0][0], "put-secret-value")


class DeleteCommand(unittest.TestCase):
    def test_delete_is_a_no_op_when_the_secret_does_not_exist(self):
        with mock.patch.object(c, "fetch_secret", side_effect=c.SecretNotFound("x")), \
             mock.patch.object(c, "_aws") as aws_mock:
            self.assertEqual(c.cmd_delete("dev", "ap-south-1", assume_yes=True), 0)
        aws_mock.assert_not_called()

    def test_delete_with_yes_skips_the_prompt_and_calls_delete_secret(self):
        with mock.patch.object(c, "fetch_secret", return_value=GOOD), \
             mock.patch.object(c, "_aws", return_value=mock.Mock(returncode=0, stderr="")) as aws_mock, \
             mock.patch("builtins.input") as input_mock:
            self.assertEqual(c.cmd_delete("dev", "ap-south-1", assume_yes=True), 0)
        input_mock.assert_not_called()
        args = aws_mock.call_args[0][0]
        self.assertEqual(args[0], "delete-secret")
        self.assertIn("--force-delete-without-recovery", args)
        self.assertIn(c.secret_id("dev"), args)

    def test_delete_without_yes_requires_typing_yes_exactly(self):
        with mock.patch.object(c, "fetch_secret", return_value=GOOD), \
             mock.patch.object(c, "_aws", return_value=mock.Mock(returncode=0, stderr="")) as aws_mock, \
             mock.patch("builtins.input", return_value="y"):
            self.assertEqual(c.cmd_delete("dev", "ap-south-1", assume_yes=False), 1)
        aws_mock.assert_not_called()

    def test_delete_without_yes_proceeds_once_confirmed(self):
        with mock.patch.object(c, "fetch_secret", return_value=GOOD), \
             mock.patch.object(c, "_aws", return_value=mock.Mock(returncode=0, stderr="")) as aws_mock, \
             mock.patch("builtins.input", return_value="yes"):
            self.assertEqual(c.cmd_delete("dev", "ap-south-1", assume_yes=False), 0)
        aws_mock.assert_called_once()

    def test_delete_reports_failure_from_aws(self):
        with mock.patch.object(c, "fetch_secret", return_value=GOOD), \
             mock.patch.object(c, "_aws", return_value=mock.Mock(returncode=1, stderr="AccessDenied")):
            self.assertEqual(c.cmd_delete("dev", "ap-south-1", assume_yes=True), 1)

    def test_main_dispatches_delete_before_check_or_show(self):
        # --delete takes priority even if --check/--show were also (mistakenly) passed.
        with mock.patch.object(c, "cmd_delete", return_value=0) as delete_mock, \
             mock.patch.object(c, "cmd_check") as check_mock:
            self.assertEqual(c.main(["--env", "dev", "--region", "ap-south-1", "--delete", "--check"]), 0)
        delete_mock.assert_called_once_with("dev", "ap-south-1", False)
        check_mock.assert_not_called()


class InteractiveFlow(unittest.TestCase):
    """Drives the real prompt loop with scripted answers and a fake Secrets Manager."""

    def run_flow(self, existing, secret_answers, text_answers, only=None):
        stored = {}

        def fake_fetch(env, region):
            if existing is None:
                raise c.SecretNotFound("x")
            return dict(existing)

        def fake_store(env, region, values, exists):
            stored.update(values)
            stored["_exists"] = exists

        with mock.patch.object(c, "fetch_secret", side_effect=fake_fetch), \
             mock.patch.object(c, "store_secret", side_effect=fake_store), \
             mock.patch("getpass.getpass", side_effect=list(secret_answers)), \
             mock.patch("builtins.input", side_effect=list(text_answers)), \
             mock.patch("sys.stdout", io.StringIO()):
            code = c.interactive("dev", "ap-south-1", only=only)
        return code, stored

    def test_first_run_collects_everything_and_creates_the_secret(self):
        # order: db, (rabbit user text), rabbit pw, admin pw, (grafana user text), grafana pw
        secrets_in = ["Str0ngDbPassw0rd", "Str0ngDbPassw0rd",
                      "Str0ngRabbitPw1", "Str0ngRabbitPw1",
                      "Str0ngAdminPw1", "Str0ngAdminPw1",
                      "Str0ngGrafanaPw1", "Str0ngGrafanaPw1"]
        text_in = ["", "", "n"]   # rabbit user default, grafana user default, no read-only login
        code, stored = self.run_flow(None, secrets_in, text_in)
        self.assertEqual(code, 0)
        self.assertFalse(stored["_exists"])
        self.assertEqual(stored["rabbitmq_user"], "catalogix")
        self.assertEqual(stored["db_master_password"], "Str0ngDbPassw0rd")
        self.assertNotIn("db_readonly_password", stored)

    def test_weak_and_mismatched_entries_are_re_prompted(self):
        secrets_in = ["weak",                                   # rejected by policy
                      "Str0ngDbPassw0rd", "Different1Passw0rd",  # mismatch -> re-prompt
                      "Str0ngDbPassw0rd", "Str0ngDbPassw0rd"]    # accepted
        code, stored = self.run_flow(dict(GOOD, db_master_password=None), secrets_in, [], only=["db_master_password"])
        self.assertEqual(code, 0)
        self.assertEqual(stored["db_master_password"], "Str0ngDbPassw0rd")

    def test_changing_one_key_keeps_the_rest(self):
        code, stored = self.run_flow(GOOD, ["NewAdminPassw0rd", "NewAdminPassw0rd"], [], only=["seed_admin_password"])
        self.assertEqual(code, 0)
        self.assertEqual(stored["seed_admin_password"], "NewAdminPassw0rd")
        self.assertEqual(stored["db_master_password"], GOOD["db_master_password"])
        self.assertTrue(stored["_exists"])

    def test_enter_keeps_the_current_value(self):
        code, stored = self.run_flow(GOOD, [""], [], only=["seed_admin_password"])
        self.assertEqual(stored, {})   # nothing written


if __name__ == "__main__":
    unittest.main()
