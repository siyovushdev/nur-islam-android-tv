#!/usr/bin/env python3
"""Verify real APK packages/entry points and ensure fixtures/secrets were not bundled."""
import argparse
import os
from pathlib import Path
import subprocess
from zipfile import ZipFile

parser = argparse.ArgumentParser()
parser.add_argument('tv', type=Path)
parser.add_argument('phone', type=Path)
parser.add_argument('--release', action='store_true', help='Check the non-debuggable TV/Phone Release packages')
parser.add_argument('--sdk', type=Path, default=Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Android/Sdk'))))
args = parser.parse_args()
aapt = args.sdk / 'build-tools/35.0.0/aapt'
suffix = '' if args.release else '.debug'
for apk, package in [(args.tv, 'dev.nurislam.tv' + suffix), (args.phone, 'dev.nurislam.tv.phone' + suffix)]:
    badging = subprocess.check_output([str(aapt), 'dump', 'badging', str(apk)], text=True)
    manifest = subprocess.check_output([str(aapt), 'dump', 'xmltree', str(apk), 'AndroidManifest.xml'], text=True)
    assert "package: name='" + package + "'" in badging
    assert "sdkVersion:'26'" in badging
    if args.release:
        assert 'application-debuggable' not in badging, 'Release must not be debuggable'
        assert 'application-testOnly' not in badging, 'Release must install without adb -t'
    assert 'android.permission.INTERNET' not in badging
    assert 'android.permission.REQUEST_INSTALL_PACKAGES' not in badging
    lines = manifest.splitlines()
    start = next(i for i, line in enumerate(lines) if line.lstrip().startswith('E: application '))
    indent = len(lines[start]) - len(lines[start].lstrip())
    end = next((i for i in range(start + 1, len(lines)) if lines[i].strip() and len(lines[i]) - len(lines[i].lstrip()) <= indent), len(lines))
    application = '\n'.join(lines[start:end])
    if apk == args.phone:
        for forbidden in ('android.intent.category.HOME', 'MosqueDeviceAdmin', 'ProvisioningActivity', 'android.intent.category.LEANBACK_LAUNCHER'):
            assert forbidden not in application, forbidden
        assert 'android.intent.category.LAUNCHER' in application
    else:
        assert 'android.intent.category.HOME' in application
        assert 'android.intent.category.LEANBACK_LAUNCHER' in application
    with ZipFile(apk) as z:
        assert not any(n.lower().endswith(('.jks', '.keystore', '.p12', '.xlsx', '.tsv')) for n in z.namelist())
        assert not any('kazan-2026' in n for n in z.namelist())
    print('PASS manifest/package/no fixture:', apk.name, package)
