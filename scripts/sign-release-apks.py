#!/usr/bin/env python3
"""Offline Release signing, with an explicit external key. Never installs or publishes."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--tv', type=Path, required=True)
parser.add_argument('--phone', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--keystore', type=Path, required=True)
parser.add_argument('--password-file', type=Path, required=True)
parser.add_argument('--sdk', type=Path, default=Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Android/Sdk'))))
args = parser.parse_args()
repo = Path(__file__).resolve().parent.parent
for private in (args.keystore, args.password_file):
    if private.resolve().is_relative_to(repo) or not private.is_file():
        parser.error('Existing key/password files outside the repository are required')
tools = args.sdk / 'build-tools/35.0.0'
subprocess.run(['python3', str(repo / 'scripts/check-apk-manifests.py'), str(args.tv), str(args.phone), '--release', '--sdk', str(args.sdk)], check=True)
pin = (repo / 'config/release-signing-cert.sha256').read_text().strip()
env = dict(os.environ, NUR_ISLAM_RELEASE_PASSWORD=args.password_file.read_text().strip())
args.output.mkdir(parents=True, exist_ok=True)
checksums = []
for kind, source in (('TV', args.tv), ('Phone', args.phone)):
    name = f'Nur-Islam-{kind}-Release.apk'
    target = args.output / name
    aligned = args.output / (name + '.aligned')
    subprocess.run([str(tools / 'zipalign'), '-p', '-f', '4', str(source), str(aligned)], check=True, capture_output=True)
    try:
        subprocess.run([str(tools / 'apksigner'), 'sign', '--ks', str(args.keystore), '--ks-key-alias', 'nur-islam-release', '--ks-pass', 'env:NUR_ISLAM_RELEASE_PASSWORD', '--key-pass', 'env:NUR_ISLAM_RELEASE_PASSWORD', '--out', str(target), str(aligned)], env=env, check=True, capture_output=True)
    finally:
        aligned.unlink(missing_ok=True)
    report = subprocess.check_output([str(tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(target)], text=True)
    assert re.search(r'certificate SHA-256 digest: ([0-9a-f]+)', report)[1] == pin, 'Unexpected Release certificate'
    target.with_suffix('.signature.txt').write_text(report)
    checksums.append(hashlib.sha256(target.read_bytes()).hexdigest() + '  ' + name)
(args.output / 'SHA256SUMS.txt').write_text('\n'.join(checksums) + '\n')
print('PASS: signed Release APKs; pinned public certificate SHA-256:', pin)
