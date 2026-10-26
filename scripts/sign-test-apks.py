#!/usr/bin/env python3
"""Sign test APKs with a persistent external key. Never signs a production package."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--input', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--keystore', type=Path, required=True)
parser.add_argument('--password-file', type=Path, required=True)
parser.add_argument('--sdk', type=Path, default=Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Android/Sdk'))))
args = parser.parse_args()
repo = Path(__file__).resolve().parent.parent
for private in (args.keystore, args.password_file):
    if private.resolve().is_relative_to(repo):
        parser.error('Signing key/password must live outside the repository')
    if not private.is_file():
        parser.error('Signing key/password file is missing')
tools = args.sdk / 'build-tools/35.0.0'
env = dict(os.environ, NUR_ISLAM_SIGNING_PASS=args.password_file.read_text().strip())
certificate = None
packages = {'tv': 'dev.nurislam.tv.debug', 'phone': 'dev.nurislam.tv.phone.debug'}
args.output.mkdir(parents=True, exist_ok=True)
for kind, package in packages.items():
    for generation in ('base', 'updated'):
        source = args.input / generation / (kind + '.apk')
        badging = subprocess.check_output([str(tools / 'aapt'), 'dump', 'badging', str(source)], text=True)
        found = re.search(r"package: name='([^']+)' versionCode='(\d+)'", badging)
        assert found and found[1] == package, 'Wrong test applicationId; refusing to sign'
        folder = args.output / (kind if generation == 'updated' else 'support')
        folder.mkdir(exist_ok=True)
        name = ('Nur-Islam-TV-Debug.apk' if kind == 'tv' else 'Nur-Islam-Phone-Debug.apk') if generation == 'updated' else kind + '-baseline.apk'
        target = folder / name
        aligned = target.with_suffix('.aligned.apk')
        subprocess.run([str(tools / 'zipalign'), '-p', '-f', '4', str(source), str(aligned)], check=True, capture_output=True)
        try:
            subprocess.run([str(tools / 'apksigner'), 'sign', '--ks', str(args.keystore), '--ks-key-alias', 'nur-islam-test', '--ks-pass', 'env:NUR_ISLAM_SIGNING_PASS', '--key-pass', 'env:NUR_ISLAM_SIGNING_PASS', '--out', str(target), str(aligned)], env=env, check=True, capture_output=True)
        finally:
            aligned.unlink(missing_ok=True)
        report = subprocess.check_output([str(tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(target)], text=True)
        fingerprint = re.search(r'certificate SHA-256 digest: ([0-9a-f]+)', report)[1]
        assert fingerprint == (repo / 'config/test-signing-cert.sha256').read_text().strip(), 'Unexpected signing certificate; refusing to publish an incompatible update'
        assert certificate is None or certificate == fingerprint, 'Signing certificate changed within distribution'
        certificate = fingerprint
        target.with_suffix('.apk.sha256').write_text(hashlib.sha256(target.read_bytes()).hexdigest() + '  ' + name + '\n')
        target.with_suffix('.signature.txt').write_text(report)
        target.with_suffix('.manifest.txt').write_text(badging)
# The instrumentation APK targets the TV package and needs the same certificate.
source = args.input / 'tv-androidTest.apk'
if source.is_file():
    target = args.output / 'support/tv-androidTest.apk'
    subprocess.run([str(tools / 'apksigner'), 'sign', '--ks', str(args.keystore), '--ks-key-alias', 'nur-islam-test', '--ks-pass', 'env:NUR_ISLAM_SIGNING_PASS', '--key-pass', 'env:NUR_ISLAM_SIGNING_PASS', '--out', str(target), str(source)], env=env, check=True, capture_output=True)
    report = subprocess.check_output([str(tools / 'apksigner'), 'verify', '--print-certs', str(target)], text=True)
    assert certificate in report, 'Instrumentation signer mismatch'
print('PASS: TV/Phone base+update signatures agree; public certificate SHA-256:', certificate)
