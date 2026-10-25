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
        if key in ("PAYDUNYA_MASTER_KEY", "PAYDUNYA_PRIVATE_KEY") and value.startswith(("test_public_", "live_public_")):
            errors.append("WAVE_SERVICE_ENV : " + key + " contient une clé publique ; copier le champ "
                          + ("Master Key" if key == "PAYDUNYA_MASTER_KEY" else "Private Key")
                          + " de l’application PayDunya.")
    # A direct security.jwt.* environment property wins even when it is empty.
    jwt = values.get("SECURITY_JWT_SECRET", values.get("JWT_SECRET", ""))
    issuer = values.get("SECURITY_JWT_ISSUER", values.get("JWT_ISSUER", "jambaarpay-user-service"))
    if len(jwt.encode("utf-8")) < 32 or jwt.startswith(("your-", "replace-", "valeur-")):
        errors.append("WAVE_SERVICE_ENV : JWT effectif absent, trop court (32 octets minimum) ou laissé en exemple ; reprendre celui de user-service.")
    elif jwt[:1] in ("'", '"') or jwt[-1:] in ("'", '"'):
        errors.append("WAVE_SERVICE_ENV : retirer les guillemets extérieurs du JWT effectif.")
    if not issuer:
        errors.append("WAVE_SERVICE_ENV : issuer JWT effectif vide ; reprendre celui de user-service.")
    return errors


if __name__ == "__main__":
    errors = validate(sys.stdin.read())
    if errors:
        print("\n".join(errors), file=sys.stderr)
        raise SystemExit(1)
    print("Configuration PayDunya sandbox présente (valeurs masquées).")
