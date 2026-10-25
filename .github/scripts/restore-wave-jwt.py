#!/usr/bin/env python3
"""Restore only wave-service JWT settings from the running user-service, without displaying values."""
import argparse
import base64
import json
import re
import subprocess
import sys


JWT_KEYS = ("JWT_SECRET", "JWT_ISSUER", "SECURITY_JWT_SECRET", "SECURITY_JWT_ISSUER")
SOURCE_COMMAND = '''
if [ "${SECURITY_JWT_SECRET+x}" = x ]; then secret="$SECURITY_JWT_SECRET"; else secret="$JWT_SECRET"; fi
if [ "${SECURITY_JWT_ISSUER+x}" = x ]; then issuer="$SECURITY_JWT_ISSUER"; else issuer="${JWT_ISSUER-jambaarpay-user-service}"; fi
printf '%s\\000%s\\000' "$secret" "$issuer"
'''


def execute(arguments, payload=None):
    return subprocess.run(arguments, input=payload, capture_output=True, text=True,
                          timeout=30, check=True).stdout


def deployment_cleanup(deployment):
    if deployment.get("metadata", {}).get("name") != "wave-service":
        raise ValueError("Unexpected deployment")
    containers = deployment.get("spec", {}).get("template", {}).get("spec", {}).get("containers", [])
    container = next((item for item in containers if item.get("name") == "wave-service"), None)
    sources = container.get("envFrom", []) if container is not None else []
    if (len(sources) != 1 or sources[0].get("secretRef", {}).get("name") != "wave-service-env"
            or sources[0].get("prefix")):
        raise ValueError("Expected wave-service-env import")
    names = sorted(entry["name"] for entry in container.get("env", []) if entry.get("name") in JWT_KEYS)
    version = deployment.get("metadata", {}).get("resourceVersion")
    if not version:
        raise ValueError("Missing resource version")
    patch = {"metadata": {"resourceVersion": version}, "spec": {"template": {"spec": {"containers": [{
        "name": "wave-service", "env": [{"name": name, "$patch": "delete"} for name in names]
    }]}}}}
    return names, patch


def restore(namespace, apply=False):
    command = ["kubectl", "-n", namespace]
    source = execute(command + ["exec", "deployment/user-service", "--", "sh", "-c", SOURCE_COMMAND]).split("\0")
    if len(source) != 3 or source[-1] or len(source[0].encode("utf-8")) < 32 or not source[1].strip():
        raise ValueError("Invalid source JWT")
    expected = dict(zip(JWT_KEYS, [source[0], source[1], source[0], source[1]]))
    encoded = {key: base64.b64encode(value.encode("utf-8")).decode("ascii") for key, value in expected.items()}
    # Read the Secret used by FUTURE pods. A healthy old pod can hide a broken new Secret.
    secret = json.loads(execute(command + ["get", "secret", "wave-service-env", "-o", "json"]))
    deployment = json.loads(execute(command + ["get", "deployment", "wave-service", "-o", "json"]))
    names, patch = deployment_cleanup(deployment)
    version = secret.get("metadata", {}).get("resourceVersion")
    if not version:
        raise ValueError("Missing secret version")
    changed = {key: value for key, value in encoded.items() if secret.get("data", {}).get(key) != value}
    print(json.dumps({"secret_jwt_matches_user": not bool(changed), "explicit_jwt_overrides": names,
                      "mode": "apply" if apply else "read-only"}), flush=True)
    if not apply:
        return
    if changed:
        execute(command + ["patch", "secret", "wave-service-env", "--type=merge", "--patch-file=/dev/stdin"],
                json.dumps({"metadata": {"resourceVersion": version}, "data": changed}))
    if names:
        for attempt in range(3):
            try:
                execute(command + ["patch", "deployment", "wave-service", "--type=strategic", "--patch-file=/dev/stdin"],
                        json.dumps(patch))
                break
            except subprocess.CalledProcessError as error:
                if attempt == 2 or "Conflict" not in (error.stderr or ""):
                    raise
                deployment = json.loads(execute(command + ["get", "deployment", "wave-service", "-o", "json"]))
                names, patch = deployment_cleanup(deployment)
                if not names:
                    if changed:
                        execute(command + ["rollout", "restart", "deployment/wave-service"])
                    break
    elif changed:
        execute(command + ["rollout", "restart", "deployment/wave-service"])
    print("JWT de wave-service restauré depuis user-service. Clés PayDunya et autres services conservés."
          if changed or names else "JWT déjà conforme ; aucune modification.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--namespace", default="jambarpay")
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    if not re.fullmatch(r"[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?", args.namespace):
        print("Namespace invalide.", file=sys.stderr)
        return 1
    try:
        restore(args.namespace, args.apply)
    except (OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError):
        print("Restauration interrompue : vérifier l’accès Kubernetes, le JWT source et l’import wave-service-env. Aucune valeur secrète affichée.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
