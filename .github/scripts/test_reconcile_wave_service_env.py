import contextlib
import copy
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("reconciler", Path(__file__).with_name("reconcile-wave-service-env.py"))
reconciler = importlib.util.module_from_spec(spec)
spec.loader.exec_module(reconciler)


class ReconcilePaymentEnvironmentTest(unittest.TestCase):
    def deployment(self):
        def reference(name, key=None):
            return {"name": name, "valueFrom": {"secretKeyRef": {"name": "wave-service-env", "key": key or name}}}
        return {"metadata": {"name": "wave-service", "resourceVersion": "123"},
                "spec": {"template": {"spec": {"containers": [{
                    "name": "wave-service", "image": "example.test/wave-service:paydunya",
                    "envFrom": [{"secretRef": {"name": "wave-service-env"}}],
                    "env": [reference("KKIAPAY_SANDBOX"), reference("KKIAPAY_PRIVATE_KEY"),
                            reference("WAVE_CHECKOUT_SIGNING_SECRET"), reference("WAVE_PUBLIC_BASE_URL"),
                            reference("OLD_ALIAS", "KKIAPAY_SECRET_KEY"),
                            {"name": "KKIAPAY_BASE_URL", "value": "private-value"},
                            reference("JWT_SECRET"), reference("SECURITY_JWT_SECRET", "JWT_SECRET"),
                            reference("PAYDUNYA_MASTER_KEY"), {"name": "EUREKA_REGISTER", "value": "true"}],
                }]}}}}

    def test_removes_only_obsolete_entries_and_preserves_jwt_paydunya_eureka_and_image(self):
        deployment = self.deployment()
        before = copy.deepcopy(deployment)
        names, result = reconciler.plan(deployment)
        self.assertEqual(["KKIAPAY_BASE_URL", "KKIAPAY_PRIVATE_KEY", "KKIAPAY_SANDBOX", "OLD_ALIAS",
                          "WAVE_CHECKOUT_SIGNING_SECRET", "WAVE_PUBLIC_BASE_URL"], names)
        self.assertEqual("123", result["metadata"]["resourceVersion"])
        self.assertEqual([{"name": name, "$patch": "delete"} for name in names],
                         result["spec"]["template"]["spec"]["containers"][0]["env"])
        self.assertEqual(before, deployment)
        for retained in ("JWT_SECRET", "PAYDUNYA_MASTER_KEY", "EUREKA_REGISTER", "image", "private-value"):
            self.assertNotIn(retained, json.dumps(result))

    def test_requires_the_correct_secret_import_before_applying_cleanup(self):
        for sources in ([], [{"prefix": "PREFIX_", "secretRef": {"name": "wave-service-env"}}],
                        [{"secretRef": {"name": "other-secret"}}]):
            deployment = self.deployment()
            deployment["spec"]["template"]["spec"]["containers"][0]["envFrom"] = sources
            with self.assertRaises(ValueError):
                reconciler.plan(deployment)

    def test_clean_deployment_is_a_noop(self):
        deployment = self.deployment()
        container = deployment["spec"]["template"]["spec"]["containers"][0]
        container["env"] = [entry for entry in container["env"] if entry["name"] in ("JWT_SECRET", "PAYDUNYA_MASTER_KEY")]
        self.assertEqual(([], None), reconciler.plan(deployment))

    def test_default_cli_is_read_only_and_does_not_display_values(self):
        output = io.StringIO()
        response = subprocess.CompletedProcess([], 0, json.dumps(self.deployment()), "")
        with patch("sys.argv", ["reconcile-wave-service-env.py"]), patch.object(reconciler.subprocess, "run", return_value=response) as run, contextlib.redirect_stdout(output):
            self.assertEqual(0, reconciler.main())
        self.assertEqual(1, run.call_count)
        self.assertFalse(json.loads(output.getvalue())["changed"])
        self.assertNotIn("private-value", output.getvalue())

    def test_apply_patches_only_the_deployment_using_stdin_and_never_the_secret(self):
        output = io.StringIO()
        responses = [subprocess.CompletedProcess([], 0, json.dumps(self.deployment()), ""),
                     subprocess.CompletedProcess([], 0, "patched", "")]
        with patch("sys.argv", ["reconcile-wave-service-env.py", "--apply"]), patch.object(reconciler.subprocess, "run", side_effect=responses) as run, contextlib.redirect_stdout(output):
            self.assertEqual(0, reconciler.main())
        self.assertEqual(["kubectl", "-n", "jambarpay", "patch", "deployment", "wave-service", "--type=strategic", "--patch-file=/dev/stdin"], run.call_args.args[0])
        self.assertNotIn("private-value", run.call_args.kwargs["input"])
        self.assertTrue(json.loads(output.getvalue())["changed"])

    def test_conflict_re_reads_before_retrying(self):
        before = self.deployment()
        after = copy.deepcopy(before)
        after["metadata"]["resourceVersion"] = "124"
        responses = [subprocess.CompletedProcess([], 0, json.dumps(before), ""),
                     subprocess.CalledProcessError(1, [], stderr="Conflict private-value"),
                     subprocess.CompletedProcess([], 0, json.dumps(after), ""),
                     subprocess.CompletedProcess([], 0, "patched", "")]
        output = io.StringIO()
        with patch("sys.argv", ["reconcile-wave-service-env.py", "--apply"]), patch.object(reconciler.subprocess, "run", side_effect=responses) as run, contextlib.redirect_stdout(output):
            self.assertEqual(0, reconciler.main())
        self.assertEqual("124", json.loads(run.call_args.kwargs["input"])["metadata"]["resourceVersion"])
        self.assertNotIn("private-value", output.getvalue())

    def test_failure_does_not_display_raw_errors_or_secret_values(self):
        output = io.StringIO()
        with patch("sys.argv", ["reconcile-wave-service-env.py", "--apply"]), patch.object(reconciler.subprocess, "run", side_effect=subprocess.CalledProcessError(1, [], stderr="private-value")), contextlib.redirect_stderr(output):
            self.assertEqual(1, reconciler.main())
        self.assertNotIn("private-value", output.getvalue())
