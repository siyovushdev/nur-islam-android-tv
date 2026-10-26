#!/usr/bin/env python3
"""Disposable Android TV EMULATOR only: install/update, native tests, reversible HOME.
No Device Owner, Device Admin, Lock Task, reboot, or physical device access.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import io
import tarfile
from emulator_home import probe_home

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('apk', type=Path)
parser.add_argument('--baseline', type=Path, required=True)
parser.add_argument('--tests', type=Path, required=True)
parser.add_argument('--serial', default='emulator-5554')
args = parser.parse_args()
if not re.fullmatch(r'emulator-\d+', args.serial):
    parser.error('Only an explicitly selected emulator is permitted')
adb = ['adb', '-s', args.serial]
out = Path('tv-emulator-results'); out.mkdir(exist_ok=True)
package = 'dev.nurislam.tv.debug'
component = package + '/dev.nurislam.tv.MainActivity'

def run(*cmd, timeout=60):
    return subprocess.run(adb + list(cmd), text=True, capture_output=True, check=True, timeout=timeout).stdout

def screenshot(name):
    with (out / (name + '.png')).open('wb') as f:
        subprocess.run(adb + ['exec-out', 'screencap', '-p'], stdout=f, check=True, timeout=30)

def home():
    value = run('shell', 'cmd', 'package', 'resolve-activity', '--brief', '--user', 'current', '--query-flags', '0x10000', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME').strip().splitlines()[-1]
    assert re.fullmatch(r'[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+', value), 'Cannot safely identify stock emulator HOME'
    return value

def collect_images(label):
    images = subprocess.check_output(adb + ['exec-out', 'run-as', package, '--user', user_id, 'tar', '-C', 'files/qa-screenshots', '-cf', '-', '.'], timeout=60)
    folder = out / ('screenshots-' + label); folder.mkdir(exist_ok=True)
    count = 0
    with tarfile.open(fileobj=io.BytesIO(images)) as archive:
        for item in archive.getmembers():
            if item.isfile() and item.name.endswith('.png'):
                (folder / Path(item.name).name).write_bytes(archive.extractfile(item).read()); count += 1
    assert count >= 10, 'Native screenshots were not collected'

stock = None
try:
    assert '1' == run('shell', 'getprop', 'ro.kernel.qemu').strip(), 'The selected target is not an emulator'
    user_id = run('shell', 'am', 'get-current-user').strip()
    assert user_id.isdigit(), 'Cannot determine the active emulator user'
    stock = home()
    assert package not in stock and "ResolverActivity" not in stock, 'Baseline HOME must be a stock emulator launcher'
    result = run('install', '--no-incremental', str(args.baseline)); assert 'Success' in result
    (out / 'baseline-install.txt').write_text(result)
    run('shell', 'am', 'start', '-W', '-n', component)
    screenshot('setup-first-launch')
    run('install', '-r', '--no-incremental', str(args.tests))
    def instrument(label):
        tests = run('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', 'dev.nurislam.tv.NativeIntegrationTest', 'dev.nurislam.tv.debug.test/androidx.test.runner.AndroidJUnitRunner', timeout=600)
        (out / (label + '.txt')).write_text(tests)
        assert 'OK (3 tests)' in tests and 'FAILURES' not in tests and 'INSTRUMENTATION_FAILED' not in tests, tests[-2000:]
    instrument('instrumentation-baseline')
    collect_images('baseline')
    capability = run('exec-out', 'run-as', package, '--user', user_id, 'cat', 'files/qa-home-capability.json')
    (out / 'home-capability.json').write_text(capability)
    home_result = 'NOT RUN: system HOME is unchanged in this acceptance'
    (out / 'home-result.txt').write_text(home_result + '\n')
    def private_hashes():
        result = {}
        for name in ('files/schedule-store.json', 'files/ramadan-store.json', 'shared_prefs/display.xml'):
            contents = run('exec-out', 'run-as', package, '--user', user_id, 'cat', name)
            result[name] = hashlib.sha256(contents.encode()).hexdigest()
        return result
    run('shell', 'am', 'force-stop', '--user', 'current', package)
    before = private_hashes()
    result = run('install', '-r', '--no-incremental', str(args.apk)); assert 'Success' in result
    after = private_hashes()
    assert before == after, 'Package update changed private data'
    (out / 'update-install.txt').write_text(result)
    (out / 'update-preservation.json').write_text(json.dumps({'result': 'PASS', 'before': before, 'after': after}, indent=2))
    instrument('instrumentation')
    assert home() == stock
    collect_images('updated')
    screenshot('final-board')
    (out / 'result.txt').write_text('HOME capability: ' + home_result + '\nPASS: actual Android TV emulator API 34 install/update, native Compose D-pad/settings/themes/CW/CCW/scales, exact XLSX 2920 values/apply/rollback, Ramadan confirmation/persistence; HOME unchanged; no HOME round-trip in this run; no physical TV/reboot/Device Owner/Lock Task\n')
finally:
    (out / 'android-runtime.txt').write_text(run('logcat', '-d', '-t', '200', '-s', 'AndroidRuntime'))
