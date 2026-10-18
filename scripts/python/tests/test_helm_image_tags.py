import contextlib
import io
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

PYTHON_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PYTHON_DIR))
from ci import check_helm_image_tags  # noqa: E402


class HelmImageTagContractTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.values_file = Path(self.temp_dir.name) / "values.yaml"
        self.values_file.write_text("# test values; Helm rendering is mocked\n", encoding="utf-8")
        self.services = [*check_helm_image_tags.BACKEND_SERVICES, "frontend", "gateway"]
        self.image_lines = "\n".join(
            f'image: "{check_helm_image_tags.REGISTRY}/catalogix-{service}:tag-{service}"'
            for service in self.services
        )

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_passes_when_each_service_uses_its_own_tag(self):
        output = io.StringIO()
        with mock.patch.object(check_helm_image_tags, "render_chart", return_value=self.image_lines), \
             contextlib.redirect_stdout(output):
            result = check_helm_image_tags.main([str(self.values_file)])

        self.assertEqual(result, 0)
        self.assertIn("all 9 images", output.getvalue())

    def test_fails_when_a_service_falls_back_to_global_tag(self):
        rendered = self.image_lines.replace(
            'image: "registry.example.test/catalogix-cart-svc:tag-cart-svc"',
            'image: "registry.example.test/catalogix-cart-svc:GLOBAL-SENTINEL"',
        )
        output = io.StringIO()
        with mock.patch.object(check_helm_image_tags, "render_chart", return_value=rendered), \
             contextlib.redirect_stdout(output):
            result = check_helm_image_tags.main([str(self.values_file)])

        self.assertEqual(result, 1)
        self.assertIn("fell back to global.imageTag", output.getvalue())


if __name__ == "__main__":
    unittest.main()
