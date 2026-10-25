import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location("validator", pathlib.Path(__file__).with_name("validate-paydunya-env.py"))
validator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(validator)


class PayDunyaEnvironmentTest(unittest.TestCase):
    def test_accepts_complete_sandbox_configuration(self):
        self.assertEqual([], validator.validate("PAYDUNYA_SANDBOX=true\nPAYDUNYA_MASTER_KEY=actual-master\n"
                                              "PAYDUNYA_PRIVATE_KEY=actual-private\nPAYDUNYA_TOKEN=actual-token\n"
                                              "JWT_SECRET=test-secret-with-at-least-32-bytes\n"))

    def test_rejects_the_short_jwt_that_caused_the_production_crash(self):
        errors = validator.validate("JWT_SECRET=too-short\n")
        self.assertTrue(any("JWT effectif" in error for error in errors))
        self.assertNotIn("too-short", "\n".join(errors))

    def test_empty_security_override_wins_over_a_valid_legacy_jwt(self):
        errors = validator.validate("JWT_SECRET=test-secret-with-at-least-32-bytes\nSECURITY_JWT_SECRET=\n")
        self.assertTrue(any("JWT effectif" in error for error in errors))

    def test_accepts_the_direct_security_alias_and_counts_utf8_bytes(self):
        errors = validator.validate("JWT_SECRET=short\nSECURITY_JWT_SECRET=" + "é" * 16 + "\n")
        self.assertFalse(any("JWT effectif" in error for error in errors))

    def test_refuses_missing_keys_live_mode_placeholders_and_outer_quotes_without_printing_values(self):
        for text in ("", "PAYDUNYA_SANDBOX=false\n", "PAYDUNYA_MASTER_KEY=your-master-key\n",
                     'PAYDUNYA_MASTER_KEY="sensitive-value"\n'):
            errors = validator.validate(text)
            self.assertTrue(errors)
            self.assertNotIn("sensitive-value", "\n".join(errors))
