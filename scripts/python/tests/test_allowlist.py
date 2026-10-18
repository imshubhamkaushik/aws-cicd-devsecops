import hashlib
import io
import json
import os
import sys
import tempfile
import unittest
from datetime import date
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from aws import allowlist as a  # noqa: E402


class FakeClientError(Exception):
    def __init__(self, code):
        super().__init__(code)
        self.response = {"Error": {"Code": code}}


class FakeS3:
    """Just enough of S3: one object per key, ETags, and IfMatch / IfNoneMatch."""

    def __init__(self):
        self.objects = {}
        self.fail_next_put_with = None  # simulate a concurrent writer once

    def get_object(self, Bucket, Key):
        if Key not in self.objects:
            raise FakeClientError("NoSuchKey")
        body, etag = self.objects[Key]
        return {"Body": io.BytesIO(body), "ETag": etag}

    def put_object(self, Bucket, Key, Body, IfMatch=None, IfNoneMatch=None, **_):
        if self.fail_next_put_with:
            code, self.fail_next_put_with = self.fail_next_put_with, None
            raise FakeClientError(code)
        if IfNoneMatch == "*" and Key in self.objects:
            raise FakeClientError("PreconditionFailed")
        if IfMatch and (Key not in self.objects or self.objects[Key][1] != IfMatch):
            raise FakeClientError("PreconditionFailed")
        self.objects[Key] = (Body, '"' + hashlib.md5(Body).hexdigest() + '"')

    def delete_object(self, Bucket, Key):
        self.objects.pop(Key, None)


class Normalize(unittest.TestCase):
    def test_bare_ip_becomes_slash_32(self):
        self.assertEqual(a.normalize_cidr("203.0.113.7"), "203.0.113.7/32")

    def test_whitespace_and_cidr_kept(self):
        self.assertEqual(a.normalize_cidr(" 203.0.113.0/24 \n"), "203.0.113.0/24")

    def test_rejects_open_to_world_and_wide_ranges(self):
        for bad in ("0.0.0.0/0", "10.0.0.0/8", "8.0.0.0/15"):
            with self.assertRaises(a.AllowlistError):
                a.normalize_cidr(bad)

    def test_rejects_garbage_ipv6_and_host_bits(self):
        for bad in ("", "not-an-ip", "2001:db8::1", "203.0.113.7/24"):
            with self.assertRaises(a.AllowlistError):
                a.normalize_cidr(bad)


class Entries(unittest.TestCase):
    def test_add_appends_and_never_replaces(self):
        entries, changed = a.add_entry([], "1.1.1.1/32")
        self.assertTrue(changed)
        entries, changed = a.add_entry(entries, "2.2.2.2/32")
        self.assertTrue(changed)
        self.assertEqual(a.cidrs_of(entries), ["1.1.1.1/32", "2.2.2.2/32"])

    def test_same_ip_again_is_no_action(self):
        entries, _ = a.add_entry([], "1.1.1.1/32")
        again, changed = a.add_entry(entries, "1.1.1.1/32", seen=True)
        self.assertFalse(changed)   # last_seen is already today
        self.assertEqual(again, entries)

    def test_seen_refreshes_an_old_last_seen(self):
        old = [{"cidr": "1.1.1.1/32", "label": "", "added": "2020-01-01", "last_seen": "2020-01-01"}]
        updated, changed = a.add_entry(old, "1.1.1.1/32", seen=True)
        self.assertTrue(changed)
        self.assertEqual(updated[0]["last_seen"], a.today())
        self.assertEqual(updated[0]["added"], "2020-01-01")

    def test_inputs_are_not_mutated(self):
        old = [{"cidr": "1.1.1.1/32", "label": "", "added": "2020-01-01", "last_seen": "2020-01-01"}]
        a.add_entry(old, "1.1.1.1/32", seen=True)
        self.assertEqual(old[0]["last_seen"], "2020-01-01")

    def test_remove(self):
        entries, _ = a.add_entry([], "1.1.1.1/32")
        entries, changed = a.remove_entry(entries, "1.1.1.1/32")
        self.assertTrue(changed)
        self.assertEqual(entries, [])
        _, changed = a.remove_entry(entries, "1.1.1.1/32")
        self.assertFalse(changed)

    def test_stale_selection_protects_current_ip_and_recent_entries(self):
        entries = [
            {"cidr": "1.1.1.1/32", "last_seen": "2026-01-01", "added": "2026-01-01"},
            {"cidr": "2.2.2.2/32", "last_seen": "2026-09-28", "added": "2026-01-01"},
            {"cidr": "3.3.3.3/32", "last_seen": "2026-01-01", "added": "2026-01-01"},
        ]
        doomed = a.stale_entries(entries, older_than_days=30, keep={"3.3.3.3/32"}, now=date(2026, 10, 10))
        self.assertEqual([e["cidr"] for e in doomed], ["1.1.1.1/32"])
        every = a.stale_entries(entries, older_than_days=None, keep={"3.3.3.3/32"})
        self.assertEqual({e["cidr"] for e in every}, {"1.1.1.1/32", "2.2.2.2/32"})


class StoreTests(unittest.TestCase):
    def setUp(self):
        self.s3 = FakeS3()
        self.store = a.Store(self.s3, bucket="b", key="k")

    def test_missing_object_is_an_empty_list(self):
        self.assertEqual(self.store.load(), ([], None))

    def test_round_trip(self):
        self.store.update(lambda cur: a.add_entry(cur, "1.1.1.1/32", "home"))
        entries, etag = self.store.load()
        self.assertEqual(a.cidrs_of(entries), ["1.1.1.1/32"])
        self.assertTrue(etag)

    def test_second_ip_is_added_not_replaced(self):
        self.store.update(lambda cur: a.add_entry(cur, "1.1.1.1/32"))
        self.store.update(lambda cur: a.add_entry(cur, "2.2.2.2/32"))
        entries, _ = self.store.load()
        self.assertEqual(a.cidrs_of(entries), ["1.1.1.1/32", "2.2.2.2/32"])

    def test_unchanged_update_does_not_write(self):
        self.store.update(lambda cur: a.add_entry(cur, "1.1.1.1/32", seen=True))
        before = self.s3.objects["k"]
        _, changed = self.store.update(lambda cur: a.add_entry(cur, "1.1.1.1/32", seen=True))
        self.assertFalse(changed)
        self.assertEqual(self.s3.objects["k"], before)

    def test_concurrent_writer_is_retried_not_overwritten(self):
        self.store.update(lambda cur: a.add_entry(cur, "1.1.1.1/32"))
        self.s3.fail_next_put_with = "PreconditionFailed"
        self.store.update(lambda cur: a.add_entry(cur, "2.2.2.2/32"))
        entries, _ = self.store.load()
        self.assertEqual(a.cidrs_of(entries), ["1.1.1.1/32", "2.2.2.2/32"])

    def test_first_write_cannot_clobber_a_list_created_meanwhile(self):
        _, etag = self.store.load()
        self.s3.objects["k"] = (b'{"version":1,"entries":[]}', '"x"')
        with self.assertRaises(a.Conflict):
            self.store.save([], etag)

    def test_missing_bucket_message_points_to_backend_step(self):
        class NoBucket(FakeS3):
            def get_object(self, Bucket, Key):
                raise FakeClientError("NoSuchBucket")
        with self.assertRaises(a.AllowlistError) as ctx:
            a.Store(NoBucket()).load()
        self.assertIn("bootstrap.py backend", str(ctx.exception))

    def test_corrupt_object_is_reported(self):
        self.s3.objects["k"] = (b"not json", '"x"')
        with self.assertRaises(a.AllowlistError):
            self.store.load()

    def test_delete(self):
        self.store.update(lambda cur: a.add_entry(cur, "1.1.1.1/32"))
        self.store.delete()
        self.assertEqual(self.store.load(), ([], None))


class SyncTests(unittest.TestCase):
    def setUp(self):
        self.s3 = FakeS3()
        self.store = a.Store(self.s3, bucket="b", key="k")
        self.tmp = tempfile.TemporaryDirectory()
        self.path = Path(self.tmp.name) / "allowed_cidrs.auto.tfvars.json"

    def tearDown(self):
        self.tmp.cleanup()

    def run_sync(self, ip):
        return a.sync(self.store, ip_lookup=lambda: ip, path=self.path)

    def tfvars(self):
        return json.loads(self.path.read_text())["allowed_cidrs"]

    def test_first_run_adds_my_ip(self):
        self.assertEqual(self.run_sync("1.1.1.1/32"), ["1.1.1.1/32"])
        self.assertEqual(self.tfvars(), ["1.1.1.1/32"])

    def test_same_ip_is_no_action(self):
        self.run_sync("1.1.1.1/32")
        before = self.s3.objects["k"]
        self.run_sync("1.1.1.1/32")
        self.assertEqual(self.s3.objects["k"], before)

    def test_isp_change_adds_and_keeps_the_old_ip(self):
        self.run_sync("1.1.1.1/32")
        self.run_sync("9.9.9.9/32")
        self.assertEqual(self.tfvars(), ["1.1.1.1/32", "9.9.9.9/32"])

    def test_another_operator_is_added_alongside(self):
        self.run_sync("1.1.1.1/32")
        other = a.Store(self.s3, bucket="b", key="k")   # a second machine
        a.sync(other, ip_lookup=lambda: "5.5.5.5/32", path=self.path)
        self.assertEqual(self.tfvars(), ["1.1.1.1/32", "5.5.5.5/32"])

    def test_delete_all_removes_list_and_tfvars(self):
        self.run_sync("1.1.1.1/32")
        a.delete_all(self.store, path=self.path)
        self.assertEqual(self.store.load(), ([], None))
        self.assertFalse(self.path.exists())


class CliTests(unittest.TestCase):
    def setUp(self):
        self.s3 = FakeS3()
        self.store = a.Store(self.s3, bucket="b", key="k")
        self.tmp = tempfile.TemporaryDirectory()
        self._orig = (a.TFVARS_FILE, a.current_ip)
        a.TFVARS_FILE = Path(self.tmp.name) / "x.auto.tfvars.json"
        a.current_ip = lambda: "1.1.1.1/32"
        self._stdout = sys.stdout
        sys.stdout = io.StringIO()

    def tearDown(self):
        sys.stdout = self._stdout
        a.TFVARS_FILE, a.current_ip = self._orig
        self.tmp.cleanup()

    def cli(self, *argv):
        return a.main(list(argv), store=self.store)

    def test_add_list_remove(self):
        self.assertEqual(self.cli("add", "2.2.2.2", "--label", "office"), 0)
        entries, _ = self.store.load()
        self.assertEqual(a.cidrs_of(entries), ["2.2.2.2/32"])
        self.assertEqual(self.cli("remove", "2.2.2.2"), 0)
        entries, _ = self.store.load()
        self.assertEqual(entries, [])

    def test_removing_my_own_ip_is_refused_without_force(self):
        self.cli("add", "1.1.1.1")
        self.assertEqual(self.cli("remove", "1.1.1.1"), 1)
        entries, _ = self.store.load()
        self.assertEqual(a.cidrs_of(entries), ["1.1.1.1/32"])
        self.assertEqual(self.cli("remove", "1.1.1.1", "--force"), 0)

    def test_prune_all_but_current(self):
        self.cli("add", "1.1.1.1")
        self.cli("add", "2.2.2.2")
        self.cli("add", "3.3.3.3")
        self.assertEqual(self.cli("prune", "--all-but-current", "--yes"), 0)
        entries, _ = self.store.load()
        self.assertEqual(a.cidrs_of(entries), ["1.1.1.1/32"])

    def test_prune_leaves_recent_entries_alone(self):
        self.cli("add", "2.2.2.2")
        self.assertEqual(self.cli("prune", "--older-than", "30", "--yes"), 0)
        entries, _ = self.store.load()
        self.assertEqual(a.cidrs_of(entries), ["2.2.2.2/32"])

    def test_bad_input_returns_error_code(self):
        self.assertEqual(self.cli("add", "0.0.0.0/0"), 1)


if __name__ == "__main__":
    unittest.main()
