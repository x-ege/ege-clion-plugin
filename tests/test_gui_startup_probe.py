import importlib.util
from pathlib import Path
import unittest
import hashlib
import shutil
import tempfile

path = Path(__file__).resolve().parents[1] / 'scripts/clion_ui_startup_probe.py'
spec = importlib.util.spec_from_file_location('startup_probe', path)
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)


class StartupObservationTest(unittest.TestCase):
    def test_agreement_or_activation_stops_even_when_welcome_is_present(self):
        for text in ['JetBrains User Agreement', 'Privacy Policy', 'Activate CLion', 'Start trial',
                     'License server', 'Log in to JetBrains Account', 'Data Sharing']:
            with self.subTest(text=text):
                self.assertEqual(probe.classify('Welcome to CLion ' + text, True), 'blocked_agreement_or_activation')

    def test_welcome_requires_successful_robot_observation(self):
        self.assertEqual(probe.classify('Welcome to CLion', False), 'starting_or_unknown_window')
        self.assertEqual(probe.classify('<div class="FlatWelcomeFrame">New Project</div>', True), 'welcome_observed')

    def test_splash_and_unknown_windows_are_not_gui_success(self):
        for text in ['CLion 2023.3', '', 'Downloading runtime']:
            self.assertEqual(probe.classify(text, True), 'starting_or_unknown_window')

    def test_x11_agreement_title_stops_without_ocr_or_robot(self):
        windows = probe.parse_windows('0x00400007  0 340 220 600 460 runner CLion User Agreement\n')
        self.assertEqual(windows[0]['title'], 'CLion User Agreement')
        self.assertEqual(probe.classify('', False, [windows[0]['title']]), 'blocked_agreement_or_activation')

    def test_unknown_window_stops_even_with_robot_welcome(self):
        self.assertEqual(probe.classify('Welcome to CLion', True, ['Unexpected consent']),
                         'unknown_window_detected')
        self.assertEqual(probe.classify('', True, ['']), 'unknown_window_detected')

    def test_malformed_inventory_cannot_silently_drop_a_window(self):
        for listing in ['garbage', '0x123 0 0 0 0 0 host Dialog', '0x123 0 0 0 100 100 host']:
            with self.subTest(listing=listing), self.assertRaises(ValueError):
                probe.parse_windows(listing)

    @unittest.skipUnless(shutil.which('convert') and shutil.which('tesseract'), 'Needs CI OCR tools')
    def test_original_dark_agreement_screenshot_is_detected(self):
        fixture = path.parents[1] / 'tests/fixtures/clion-ui/user-agreement-1.4.png'
        self.assertEqual(hashlib.sha256(fixture.read_bytes()).hexdigest(),
                         '7975e25994d9974278be94e4dec47c8d22b06fd96ff9eaea15d575d3f3318c19')
        with tempfile.TemporaryDirectory(dir=path.parents[1]) as directory:
            screenshot = Path(directory) / 'agreement.png'
            shutil.copyfile(fixture, screenshot)
            text = probe.ocr_screenshot(screenshot)
        self.assertIn('JETBRAINS USER AGREEMENT', text)
        self.assertIn('Version 1.4', text)
        self.assertEqual(probe.classify(text), 'blocked_agreement_or_activation')


if __name__ == '__main__':
    unittest.main()
