#!/usr/bin/env python3
"""Validate PayDunya sandbox variables from stdin without displaying their values."""
import sys


def validate(content):
    values = {}
    for line in content.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        name, separator, value = line.partition("=")
        if separator:
            values[name.strip()] = value.strip()
    errors = []
    if values.get("PAYDUNYA_SANDBOX", "").lower() != "true":
        errors.append("WAVE_SERVICE_ENV : PAYDUNYA_SANDBOX doit être explicitement true pour ce parcours de test.")
    for key in ("PAYDUNYA_MASTER_KEY", "PAYDUNYA_PRIVATE_KEY", "PAYDUNYA_TOKEN"):
        value = values.get(key, "")
        if not value or value.startswith(("your-", "replace-", "valeur-")):
            errors.append("WAVE_SERVICE_ENV : clé " + key + " absente, vide ou laissée en exemple.")
        elif value[:1] in ("'", '"') or value[-1:] in ("'", '"'):
            errors.append("WAVE_SERVICE_ENV : retirer les guillemets extérieurs de " + key + ".")
    return errors


if __name__ == "__main__":
    errors = validate(sys.stdin.read())
    if errors:
        print("\n".join(errors), file=sys.stderr)
        raise SystemExit(1)
    print("Configuration PayDunya sandbox présente (valeurs masquées).")
