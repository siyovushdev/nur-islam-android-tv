#!/usr/bin/env python3
"""Safety regression tests; never connects to ADB or signs an actual package."""
import ast
import xml.etree.ElementTree as ET
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parent.parent

class DistributionGuards(unittest.TestCase):
    def invoke(self, script, *args):
        return subprocess.run([sys.executable, str(ROOT / 'scripts' / script), *map(str, args)], capture_output=True, text=True, timeout=15)
    def test_physical_targets_refused_before_any_adb_call(self):
        for script, arguments in [('check-product-apk.py', ['--package', 'dev.nurislam.tv.phone']), ('check-phone-apk.py', []), ('check-emulator-home.py', []), ('check-tv-apk.py', ['--baseline', 'unused.apk', '--tests', 'unused-tests.apk'])]:
            result = self.invoke(script, 'unused.apk', *arguments, '--serial', 'physical-device')
            self.assertEqual(2, result.returncode)
            self.assertIn('Only', result.stderr)
    def test_only_exact_system_launcher_dialog_can_be_waited(self):
        # Extract the pure predicate without executing the ADB script's entry point.
        tree = ast.parse((ROOT / 'scripts/check-product-apk.py').read_text())
        node = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == 'system_launcher_startup_dialog')
        namespace = {}
        exec(compile(ast.Module(body=[node], type_ignores=[]), '<predicate>', 'exec'), namespace)
        recognize = namespace['system_launcher_startup_dialog']
        def dialog(text, package='android', resource='android:id/alertTitle'):
            root = ET.Element('hierarchy')
            ET.SubElement(root, 'node', {'text': text, 'package': package, 'resource-id': resource})
            return root
        self.assertTrue(recognize(dialog("Quickstep isn't responding")))
        for text in ["Нур Ислам isn't responding", "Nur Islam isn't responding", 'Quickstep keeps stopping', 'System UI isn’t responding']:
            self.assertFalse(recognize(dialog(text)))
        self.assertFalse(recognize(dialog("Quickstep isn't responding", package='dev.nurislam.tv.phone')))
        self.assertFalse(recognize(dialog("Quickstep isn't responding", resource='app:title')))

    def test_key_inside_repository_refused_even_if_ignored(self):
        result = self.invoke('sign-test-apks.py', '--input', '/tmp/unused-input', '--output', '/tmp/unused-output', '--keystore', ROOT / 'never-create.jks', '--password-file', ROOT / 'never-create.password')
        self.assertEqual(2, result.returncode)
        self.assertIn('outside the repository', result.stderr)
    def test_production_application_id_never_reaches_signer(self):
        with tempfile.TemporaryDirectory(prefix='nur-release-guard-') as folder:
            root = Path(folder)
            tools = root / 'sdk/build-tools/35.0.0'; tools.mkdir(parents=True)
            aapt = tools / 'aapt'
            aapt.write_text('#!/bin/sh\necho "package: name=\'dev.nurislam.tv\' versionCode=\'3\'"\n')
            aapt.chmod(0o700)
            (root / 'dummy.jks').write_text('not a key, regression fixture only')
            (root / 'dummy.password').write_text('not a secret')
            result = self.invoke('sign-test-apks.py', '--input', root / 'in', '--output', root / 'out', '--keystore', root / 'dummy.jks', '--password-file', root / 'dummy.password', '--sdk', root / 'sdk')
            self.assertNotEqual(0, result.returncode)
            self.assertIn('Wrong test applicationId', result.stderr)
            self.assertEqual([], list((root / 'out').glob('**/*.apk')))

if __name__ == '__main__':
    unittest.main()
