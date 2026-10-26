#!/usr/bin/env python3
"""Positive HOME assignment control on a standard phone emulator, using our TV test package."""
import argparse
from pathlib import Path
import re
import subprocess
from emulator_home import probe_home

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('apk', type=Path)
parser.add_argument('--serial', default='emulator-5554')
args = parser.parse_args()
if not re.fullmatch(r'emulator-\d+', args.serial):
    parser.error('Only an explicitly selected emulator is permitted')
adb = ['adb', '-s', args.serial]
assert subprocess.check_output(adb + ['shell', 'getprop', 'ro.kernel.qemu'], text=True).strip() == '1'
result = subprocess.check_output(adb + ['install', '--no-incremental', str(args.apk)], text=True)
assert 'Success' in result
package = 'dev.nurislam.tv.debug'
assert package in subprocess.check_output(adb + ['shell', 'cmd', 'package', 'list', 'packages', package], text=True)
subprocess.run(adb + ['shell', 'am', 'start', '-W', '--user', 'current', '-n', package + '/dev.nurislam.tv.MainActivity'], check=True, capture_output=True, text=True)
assert probe_home(adb, package, package + '/dev.nurislam.tv.MainActivity', Path('phone-emulator-results/home-api35')) == 'PASS'
print('PASS: positive HOME assignment + exact role/resolver restoration on Android API 35 emulator, no reboot/policies')
