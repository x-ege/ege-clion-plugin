import importlib.util
from pathlib import Path
import unittest

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


if __name__ == '__main__':
    unittest.main()
