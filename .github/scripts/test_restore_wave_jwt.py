import base64
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("restore_wave_jwt", Path(__file__).with_name("restore-wave-jwt.py"))
repair = importlib.util.module_from_spec(spec)
spec.loader.exec_module(repair)
SOURCE = "test-source-jwt-secret-with-at-least-32-bytes"
ISSUER = "jambaarpay-user-service"


def b64(value):
    return base64.b64encode(value.encode()).decode()


def secret(valid=False):
    data = {"PAYDUNYA_MASTER_KEY": b64("unchanged-paydunya-test-key")}
    data.update({key: b64((ISSUER if valid else "old-issuer") if "ISSUER" in key
                          else SOURCE if valid else "short") for key in repair.JWT_KEYS})
    return json.dumps({"metadata": {"resourceVersion": "123"}, "data": data})


def deployment(overrides=True, version="456"):
    env = [{"name": "EUREKA_REGISTER", "value": "true"}]
    if overrides:
        env += [{"name": "JWT_SECRET", "value": "short-literal"},
                {"name": "SECURITY_JWT_SECRET", "value": "short-literal"}]
    return json.dumps({"metadata": {"name": "wave-service", "resourceVersion": version},
                       "spec": {"template": {"spec": {"containers": [{"name": "wave-service",
                           "image": "example.test/wave-service:paydunya", "env": env,
                           "envFrom": [{"secretRef": {"name": "wave-service-env"}}]}]}}}})


class RestoreWaveJwtTest(unittest.TestCase):
    def run_repair(self, responses, apply=True):
        output = io.StringIO()
        with patch.object(repair, "execute", side_effect=responses) as execute, contextlib.redirect_stdout(output):
            repair.restore("jambarpay", apply)
        return execute, output.getvalue()

    def test_reads_the_future_secret_instead_of_selecting_the_healthy_old_wave_pod(self):
        execute, output = self.run_repair([SOURCE + "\0" + ISSUER + "\0", secret(), deployment()], apply=False)
        self.assertEqual(3, execute.call_count)
        self.assertIn(["get", "secret", "wave-service-env"], [call.args[0][3:6] for call in execute.call_args_list])
        self.assertFalse(any("deployment/wave-service" in call.args[0] for call in execute.call_args_list))
        self.assertIn('"secret_jwt_matches_user": false', output)
        self.assertNotIn(SOURCE, output)

    def test_restores_only_jwt_data_and_removes_overrides_without_changing_paydunya_or_image(self):
        execute, output = self.run_repair([SOURCE + "\0" + ISSUER + "\0", secret(), deployment(), "", ""])
        self.assertEqual(5, execute.call_count)
        secret_call, deployment_call = execute.call_args_list[3:]
        payload = json.loads(secret_call.args[1])
        self.assertEqual(set(repair.JWT_KEYS), set(payload["data"]))
        self.assertEqual(b64(SOURCE), payload["data"]["SECURITY_JWT_SECRET"])
        self.assertEqual("123", payload["metadata"]["resourceVersion"])
        self.assertNotIn("PAYDUNYA", secret_call.args[1])
        for call in (secret_call, deployment_call):
            self.assertIn("--patch-file=/dev/stdin", call.args[0])
            self.assertNotIn(SOURCE, " ".join(call.args[0]))
        cleanup = json.loads(deployment_call.args[1])
        self.assertEqual([{"name": name, "$patch": "delete"} for name in ("JWT_SECRET", "SECURITY_JWT_SECRET")],
                         cleanup["spec"]["template"]["spec"]["containers"][0]["env"])
        self.assertNotIn("image", deployment_call.args[1])
        self.assertNotIn("EUREKA", deployment_call.args[1])
        self.assertNotIn(SOURCE, output)

    def test_restart_is_necessary_when_only_the_secret_changes(self):
        execute, _ = self.run_repair([SOURCE + "\0" + ISSUER + "\0", secret(), deployment(False), "", ""])
        self.assertEqual(["kubectl", "-n", "jambarpay", "rollout", "restart", "deployment/wave-service"],
                         execute.call_args_list[-1].args[0])

    def test_identical_secret_with_no_overrides_is_unchanged(self):
        execute, _ = self.run_repair([SOURCE + "\0" + ISSUER + "\0", secret(True), deployment(False)])
        self.assertEqual(3, execute.call_count)

    def test_invalid_source_stops_before_any_write(self):
        with patch.object(repair, "execute", return_value="short\0issuer\0") as execute:
            with self.assertRaises(ValueError):
                repair.restore("jambarpay", True)
        self.assertEqual(1, execute.call_count)

    def test_missing_secret_import_stops_before_any_write(self):
        invalid = json.loads(deployment())
        invalid["spec"]["template"]["spec"]["containers"][0]["envFrom"] = []
        with patch.object(repair, "execute", side_effect=[SOURCE + "\0" + ISSUER + "\0", secret(), json.dumps(invalid)]) as execute:
            with self.assertRaises(ValueError):
                repair.restore("jambarpay", True)
        self.assertEqual(3, execute.call_count)

    def test_conflict_re_reads_the_deployment_before_retrying(self):
        execute, _ = self.run_repair([SOURCE + "\0" + ISSUER + "\0", secret(), deployment(), "",
                                     subprocess.CalledProcessError(1, [], stderr="Conflict"),
                                     deployment(version="457"), ""])
        self.assertEqual("457", json.loads(execute.call_args_list[-1].args[1])["metadata"]["resourceVersion"])

    def test_real_shell_preserves_security_alias_priority_including_an_empty_override(self):
        for override, expected in ((SOURCE, SOURCE), ("", "")):
            value = subprocess.check_output(["sh", "-c", repair.SOURCE_COMMAND],
                    env={"JWT_SECRET": "legacy-value", "SECURITY_JWT_SECRET": override,
                         "SECURITY_JWT_ISSUER": ISSUER}, text=True)
            self.assertEqual([expected, ISSUER, ""], value.split("\0"))

    def test_failure_messages_do_not_expose_the_raw_kubectl_error(self):
        output = io.StringIO()
        with patch("sys.argv", ["restore-wave-jwt.py", "--apply"]), patch.object(repair, "restore",
                side_effect=subprocess.CalledProcessError(1, [], stderr=SOURCE)), contextlib.redirect_stderr(output):
            self.assertEqual(1, repair.main())
        self.assertNotIn(SOURCE, output.getvalue())
