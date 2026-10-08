import importlib.util
from datetime import datetime, timezone
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('agreement', ROOT / 'scripts/clion_ui_agreement.py')
agreement = importlib.util.module_from_spec(spec)
spec.loader.exec_module(agreement)


class AgreementAuthorizationTest(unittest.TestCase):
    def test_only_exact_first_run_label_event_can_authorize(self):
        head = 'a' * 40
        reference = agreement.AUTHORIZATION_PREFIX + head
        event = {'action': 'labeled', 'number': 8,
                 'repository': {'full_name': 'x-ege/ege-clion-plugin'},
                 'sender': {'login': 'wysaid'}, 'label': {'name': reference},
                 'pull_request': {'head': {'ref': 'fix/clion-new-project-wizard', 'sha': head}}}
        now = datetime(2026, 10, 8, tzinfo=timezone.utc)
        with tempfile.TemporaryDirectory(dir=ROOT) as directory:
            event_path = Path(directory) / 'event.json'
            event_path.write_text(json.dumps(event))
            env = {'GITHUB_EVENT_NAME': 'pull_request', 'GITHUB_RUN_ATTEMPT': '1',
                   'GITHUB_EVENT_PATH': str(event_path)}
            with patch.object(agreement.subprocess, 'check_output', return_value=head+'\n'):
                agreement.check_authorization(reference, env, now)
                for bad in ['', agreement.AUTHORIZATION_PREFIX + 'b'*40]:
                    with self.subTest(reference=bad), self.assertRaises(RuntimeError):
                        agreement.check_authorization(bad, env, now)
                for key, value in [('GITHUB_RUN_ATTEMPT', '2'), ('GITHUB_EVENT_NAME', 'push')]:
                    with self.subTest(key=key), self.assertRaises(RuntimeError):
                        agreement.check_authorization(reference, {**env, key: value}, now)
                with self.assertRaises(RuntimeError):
                    agreement.check_authorization(reference, env, agreement.EXPIRES)
                for key, value in [('action', 'synchronize'), ('number', 9), ('sender', {'login': 'other'})]:
                    event_path.write_text(json.dumps({**event, key: value}))
                    with self.subTest(key=key), self.assertRaises(RuntimeError):
                        agreement.check_authorization(reference, env, now)
                event_path.write_text(json.dumps(event))
            with patch.object(agreement.subprocess, 'check_output', return_value='b'*40+'\n'), self.assertRaises(RuntimeError):
                agreement.check_authorization(reference, env, now)

    def test_new_or_unknown_agreement_is_never_accepted(self):
        for text in ['JETBRAINS USER AGREEMENT Version 2.0', 'Privacy Policy', '']:
            with self.subTest(text=text), self.assertRaises(RuntimeError):
                agreement.acceptance_label([{'text': text}])

    @unittest.skipUnless(shutil.which('convert') and shutil.which('tesseract'), 'Needs CI OCR tools')
    def test_actual_checkbox_is_found_from_current_pixels_and_ocr(self):
        original = ROOT / 'tests/fixtures/clion-ui/user-agreement-1.4.png'
        template = agreement.region(agreement.pixels(original), 373, 606)
        self.assertFalse(agreement.checked_box(template))
        with tempfile.TemporaryDirectory(dir=ROOT) as directory:
            image = Path(directory) / 'window.png'
            subprocess.run(['convert', str(original), '-crop', '600x460+340+220', '+repage', str(image)],
                           capture_output=True, check=True)
            label, confirm = agreement.acceptance_label(agreement.words(image))
            self.assertEqual(agreement.unchecked_box(agreement.pixels(image), label, template), (33, 386))
            x, y, w, h = agreement.box(confirm)
            self.assertTrue(x > 33 and 380 < y < 410 and w > 0 and h > 0)
            with self.assertRaises(RuntimeError):
                agreement.unchecked_box(agreement.pixels(image), label, bytes(len(template)))

    @unittest.skipUnless(shutil.which('convert') and shutil.which('tesseract'), 'Needs CI OCR tools')
    def test_only_audited_optional_telemetry_can_be_declined(self):
        original = ROOT / 'tests/fixtures/clion-ui/data-sharing.png'
        image = agreement.pixels(original)
        with tempfile.TemporaryDirectory(dir=ROOT) as directory:
            screenshot = Path(directory) / 'sharing.png'
            shutil.copyfile(original, screenshot)
            ocr = agreement.words(screenshot)
        target = agreement.telemetry_decline_button(image, ocr)
        self.assertEqual(target['text'], "Don't")
        # New text or altered prompt pixels must fail closed before any input.
        width, height, data = image
        changed = bytes([data[0] ^ 1]) + data[1:]
        with self.assertRaises(RuntimeError):
            agreement.telemetry_decline_button((width, height, changed), ocr)
        with self.assertRaises(RuntimeError):
            agreement.telemetry_decline_button(image, [w for w in ocr if w['text'] != "Don't"])


if __name__ == '__main__':
    unittest.main()
