import io
import json
import os
import sys
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from aws import credentials as c  # noqa: E402


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

    def test_optional_readonly_username_alone_is_invalid(self):
        problems = c.check_values(dict(GOOD, db_readonly_username="catalogix_readonly"))
        self.assertTrue(any("db_readonly_password: required" in problem for problem in problems))

    def test_targeted_update_cannot_create_half_readonly_pair(self):
        problems = c._validate_changes(
            GOOD,
            {"db_readonly_username": "catalogix_readonly"},
            ["db_readonly_username"],
            exists=True,
        )
        self.assertTrue(any("db_readonly_password" in problem for problem in problems))

    def test_targeted_update_to_new_secret_must_supply_all_required_fields(self):
        problems = c._validate_changes(
            {},
            {"seed_admin_password": "Str0ngAdminPw1"},
            ["seed_admin_password"],
            exists=False,
        )
        self.assertIn("db_master_password: missing", problems)
        self.assertIn("rabbitmq_password: missing", problems)

    def test_targeted_unrelated_update_does_not_block_on_unrelated_optional_pair(self):
        problems = c._validate_changes(
            GOOD,
            {"grafana_admin_user": "grafana.admin"},
            ["grafana_admin_user"],
            exists=True,
        )
        self.assertEqual(problems, [])


class CommandLine(unittest.TestCase):
    def setUp(self):
        self.categories = set(c.CATEGORY_SUFFIXES)

    def test_secret_names_are_grouped_by_environment_and_category(self):
        self.assertEqual(c.secret_id("dev", "database"), "catalogix-cluster-dev/credentials/database")
        self.assertEqual(c.secret_id("dev", "application"), "catalogix-cluster-dev/credentials/application")
        self.assertEqual(c.secret_id("dev", "observability"), "catalogix-cluster-dev/credentials/observability")
        self.assertEqual(c.legacy_secret_id("dev"), "catalogix-cluster-dev/operator-credentials")
        self.assertEqual(c.secret_id("staging", "database"), "catalogix-cluster-staging/credentials/database")

    def test_check_fails_when_category_secrets_are_missing(self):
        with mock.patch.object(c, "load_credential_state", side_effect=c.SecretNotFound("x")):
            self.assertEqual(c.cmd_check("dev", "ap-south-1"), 1)

    def test_check_fails_when_legacy_secret_needs_migration(self):
        out = io.StringIO()
        with mock.patch.object(c, "load_credential_state", return_value=(GOOD, set(), True)), mock.patch("sys.stdout", out):
            self.assertEqual(c.cmd_check("dev", "ap-south-1"), 1)
        self.assertIn("--migrate-legacy", out.getvalue())

    def test_check_passes_when_all_category_secrets_exist(self):
        with mock.patch.object(c, "load_credential_state", return_value=(GOOD, self.categories, False)):
            self.assertEqual(c.cmd_check("dev", "ap-south-1"), 0)

    def test_show_never_prints_passwords(self):
        out = io.StringIO()
        with mock.patch.object(c, "load_credential_state", return_value=(GOOD, self.categories, False)), mock.patch("sys.stdout", out):
            c.cmd_show("dev", "ap-south-1")
        text = out.getvalue()
        self.assertNotIn("Str0ng", text)
        self.assertIn("rabbitmq_user", text)
        self.assertIn("credentials/application", text)

    def test_store_writes_only_category_json_via_private_file(self):
        captured = {}

        def fake_aws(args, region):
            path = args[args.index("--secret-string") + 1].replace("file://", "")
            captured["mode"] = os.stat(path).st_mode & 0o777
            with open(path, encoding="utf-8") as fh:
                captured["body"] = json.load(fh)
            captured["args"] = args
            return mock.Mock(returncode=0, stderr="")

        with mock.patch.object(c, "_aws", side_effect=fake_aws):
            c.store_secret("dev", "ap-south-1", "application", GOOD, exists=False)
        self.assertEqual(captured["mode"], 0o600)
        self.assertEqual(set(captured["body"]), {"rabbitmq_user", "rabbitmq_password", "seed_admin_password"})
        self.assertEqual(captured["args"][0], "create-secret")
        self.assertEqual(captured["args"][captured["args"].index("--name") + 1], c.secret_id("dev", "application"))
        self.assertFalse(any("Str0ng" in a for a in captured["args"]))

    def test_store_updates_when_category_secret_exists(self):
        with mock.patch.object(c, "_aws", return_value=mock.Mock(returncode=0, stderr="")) as aws:
            c.store_secret("dev", "ap-south-1", "database", GOOD, exists=True)
        self.assertEqual(aws.call_args[0][0][0], "put-secret-value")
        self.assertEqual(aws.call_args[0][0][2], c.secret_id("dev", "database"))

    def test_migration_splits_legacy_secret_and_does_not_delete_source(self):
        writes = []
        def fake_store(env, region, category, values, exists):
            writes.append((category, values, exists))
        with mock.patch.object(c, "fetch_legacy_secret", return_value=GOOD), \
             mock.patch.object(c, "fetch_category_secret", side_effect=c.SecretNotFound("missing")), \
             mock.patch.object(c, "store_secret", side_effect=fake_store), \
             mock.patch("sys.stdout", io.StringIO()):
            self.assertEqual(c.cmd_migrate_legacy("dev", "ap-south-1"), 0)
        self.assertEqual({row[0] for row in writes}, self.categories)
        self.assertEqual(len(writes), 3)
        self.assertEqual(writes[0][2], False)


class DeleteCommand(unittest.TestCase):
    def _mock_existing_secrets(self):
        existing = {c.secret_id("dev", category) for category in c.CATEGORY_SUFFIXES}
        existing.add(c.legacy_secret_id("dev"))
        def fetch(sid, region):
            if sid not in existing:
                raise c.SecretNotFound(sid)
            return {"present": True}
        return existing, fetch

    def test_delete_is_a_no_op_when_no_secret_exists(self):
        with mock.patch.object(c, "_fetch_secret_id", side_effect=c.SecretNotFound("x")), \
             mock.patch.object(c, "_aws") as aws_mock:
            self.assertEqual(c.cmd_delete("dev", "ap-south-1", assume_yes=True), 0)
        aws_mock.assert_not_called()

    def test_delete_with_yes_deletes_all_category_and_legacy_secrets(self):
        existing, fetch = self._mock_existing_secrets()
        with mock.patch.object(c, "_fetch_secret_id", side_effect=fetch), \
             mock.patch.object(c, "_aws", return_value=mock.Mock(returncode=0, stderr="")) as aws_mock, \
             mock.patch("builtins.input") as input_mock:
            self.assertEqual(c.cmd_delete("dev", "ap-south-1", assume_yes=True), 0)
        input_mock.assert_not_called()
        deleted = {call.args[0][call.args[0].index("--secret-id") + 1] for call in aws_mock.call_args_list}
        self.assertEqual(deleted, existing)
        self.assertTrue(all("--force-delete-without-recovery" in call.args[0] for call in aws_mock.call_args_list))

    def test_delete_requires_exact_yes(self):
        existing, fetch = self._mock_existing_secrets()
        with mock.patch.object(c, "_fetch_secret_id", side_effect=fetch), \
             mock.patch.object(c, "_aws") as aws_mock, \
             mock.patch("builtins.input", return_value="y"):
            self.assertEqual(c.cmd_delete("dev", "ap-south-1", assume_yes=False), 1)
        aws_mock.assert_not_called()

    def test_delete_proceeds_when_confirmed(self):
        _, fetch = self._mock_existing_secrets()
        with mock.patch.object(c, "_fetch_secret_id", side_effect=fetch), \
             mock.patch.object(c, "_aws", return_value=mock.Mock(returncode=0, stderr="")) as aws_mock, \
             mock.patch("builtins.input", return_value="yes"):
            self.assertEqual(c.cmd_delete("dev", "ap-south-1", assume_yes=False), 0)
        self.assertEqual(aws_mock.call_count, 4)

    def test_delete_reports_failures(self):
        _, fetch = self._mock_existing_secrets()
        with mock.patch.object(c, "_fetch_secret_id", side_effect=fetch), \
             mock.patch.object(c, "_aws", return_value=mock.Mock(returncode=1, stderr="AccessDenied")), \
             mock.patch("sys.stdout", io.StringIO()):
            self.assertEqual(c.cmd_delete("dev", "ap-south-1", assume_yes=True), 1)

    def test_main_dispatches_delete_before_check_or_show(self):
        with mock.patch.object(c, "cmd_delete", return_value=0) as delete_mock, \
             mock.patch.object(c, "cmd_check") as check_mock:
            self.assertEqual(c.main(["--env", "dev", "--region", "ap-south-1", "--delete", "--check"]), 0)
        delete_mock.assert_called_once_with("dev", "ap-south-1", False)
        check_mock.assert_not_called()


class InteractiveFlow(unittest.TestCase):
    """Drives the real prompt loop with a mocked, categorized Secrets Manager."""

    def run_flow(self, existing, secret_answers, text_answers, only=None, present=None):
        stored = {}
        if existing is None:
            state = ({}, set(), False)
        else:
            state = (dict(existing), set(present or c.CATEGORY_SUFFIXES), False)

        def fake_store(env, region, category, values, exists):
            stored[category] = dict(values)
            stored["_exists_" + category] = exists

        with mock.patch.object(c, "_load_secret", return_value=state), \
             mock.patch.object(c, "store_secret", side_effect=fake_store), \
             mock.patch.object(c, "cmd_check", return_value=0), \
             mock.patch("getpass.getpass", side_effect=list(secret_answers)), \
             mock.patch("builtins.input", side_effect=list(text_answers)), \
             mock.patch("sys.stdout", io.StringIO()):
            code = c.interactive("dev", "ap-south-1", only=only)
        return code, stored

    def test_first_run_creates_three_category_secrets(self):
        secrets_in = ["Str0ngDbPassw0rd", "Str0ngDbPassw0rd",
                      "Str0ngRabbitPw1", "Str0ngRabbitPw1",
                      "Str0ngAdminPw1", "Str0ngAdminPw1",
                      "Str0ngGrafanaPw1", "Str0ngGrafanaPw1"]
        text_in = ["", "", "n"]  # default RabbitMQ/Grafana usernames; no read-only login
        code, stored = self.run_flow(None, secrets_in, text_in)
        self.assertEqual(code, 0)
        self.assertEqual(set(k for k in stored if not k.startswith("_exists_")), set(c.CREDENTIALS_BY_CATEGORY))
        self.assertEqual(stored["database"]["db_master_password"], "Str0ngDbPassw0rd")
        self.assertEqual(stored["application"]["rabbitmq_user"], "catalogix")
        self.assertEqual(stored["observability"]["grafana_admin_user"], "admin")
        self.assertNotIn("db_readonly_password", stored["database"])
        self.assertFalse(stored["_exists_database"])

    def test_legacy_secret_requires_explicit_migration_before_editing(self):
        out = io.StringIO()
        with mock.patch.object(c, "_load_secret", return_value=(GOOD, set(), True)), mock.patch("sys.stdout", out):
            code = c.interactive("dev", "ap-south-1", only=["seed_admin_password"])
        self.assertEqual(code, 1)
        self.assertIn("--migrate-legacy", out.getvalue())

    def test_weak_and_mismatched_passwords_are_reprompted(self):
        secrets_in = ["weak", "Str0ngDbPassw0rd", "Different1Passw0rd", "Str0ngDbPassw0rd", "Str0ngDbPassw0rd"]
        code, stored = self.run_flow({**GOOD, "db_master_password": None}, secrets_in, [], only=["db_master_password"])
        self.assertEqual(code, 0)
        self.assertEqual(stored["database"]["db_master_password"], "Str0ngDbPassw0rd")

    def test_changing_one_key_writes_only_its_category_and_keeps_other_values(self):
        code, stored = self.run_flow(GOOD, ["NewAdminPassw0rd", "NewAdminPassw0rd"], [], only=["seed_admin_password"])
        self.assertEqual(code, 0)
        self.assertEqual(set(k for k in stored if not k.startswith("_exists_")), {"application"})
        self.assertEqual(stored["application"]["seed_admin_password"], "NewAdminPassw0rd")
        self.assertEqual(stored["application"]["rabbitmq_password"], GOOD["rabbitmq_password"])
        self.assertTrue(stored["_exists_application"])

    def test_enter_keeps_current_value(self):
        code, stored = self.run_flow(GOOD, [""], [], only=["seed_admin_password"])
        self.assertEqual(code, 0)
        self.assertEqual(stored, {})


if __name__ == "__main__":
    unittest.main()
