#!/usr/bin/env python3
"""Remove obsolete KkiaPay environment entries after switching wave-service to PayDunya."""

import argparse
import json
import re
import subprocess
import sys


def plan(deployment):
    if deployment.get("metadata", {}).get("name") != "wave-service":
        raise ValueError("Unexpected deployment")
    containers = deployment.get("spec", {}).get("template", {}).get("spec", {}).get("containers", [])
    container = next((item for item in containers if item.get("name") == "wave-service"), None)
    sources = container.get("envFrom", []) if container is not None else []
    if (len(sources) != 1 or sources[0].get("secretRef", {}).get("name") != "wave-service-env"
            or sources[0].get("prefix")):
        raise ValueError("Apply the envFrom manifest before reconciling")
    def obsolete(name):
        return (name.startswith(("KKIAPAY_", "WAVE_PROVIDERS_KKIAPAY_", "WAVE_CHECKOUT_"))
                or name == "WAVE_PUBLIC_BASE_URL")

    names = set()
    for entry in container.get("env", []):
        reference = entry.get("valueFrom", {}).get("secretKeyRef", {})
        name = entry.get("name", "")
        old_reference = reference.get("name") == "wave-service-env" and obsolete(reference.get("key", ""))
        if (obsolete(name) or old_reference) and re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", name):
            names.add(name)
    if not names:
        return [], None
    version = deployment.get("metadata", {}).get("resourceVersion")
    if not isinstance(version, str) or not version:
        raise ValueError("Missing resource version")
    patch = {
        "metadata": {"resourceVersion": version},
        "spec": {"template": {"spec": {"containers": [{
            "name": "wave-service", "env": [{"name": name, "$patch": "delete"} for name in sorted(names)]
        }]}}},
    }
    return sorted(names), patch


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--namespace", default="jambarpay")
    parser.add_argument("--apply", action="store_true", help="Remove only obsolete payment entries; otherwise report names without changing anything.")
    args = parser.parse_args()
    if not re.fullmatch(r"[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?", args.namespace):
        print("Namespace invalide.", file=sys.stderr)
        return 1
    command = ["kubectl", "-n", args.namespace]
    try:
        for attempt in range(3):
            result = subprocess.run(command + ["get", "deployment", "wave-service", "-o", "json"],
                                    capture_output=True, text=True, timeout=30, check=True)
            names, patch = plan(json.loads(result.stdout))
            if patch is not None and args.apply:
                try:
                    subprocess.run(command + ["patch", "deployment", "wave-service", "--type=strategic", "--patch-file=/dev/stdin"],
                                   input=json.dumps(patch), capture_output=True, text=True, timeout=30, check=True)
                except subprocess.CalledProcessError as error:
                    # Controllers may update status during rollout. Re-read rather than overwrite a newer version.
                    if attempt < 2 and "Conflict" in (error.stderr or ""):
                        continue
                    raise
            break
    except (OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError):
        print("Réconciliation impossible : vérifier l'accès au Deployment et son envFrom. Aucune valeur secrète affichée.", file=sys.stderr)
        return 1
    action = "supprimées" if args.apply else "à supprimer (lecture seule)"
    print(json.dumps({"obsolete_payment_env": names, "action": action, "changed": args.apply and patch is not None}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
