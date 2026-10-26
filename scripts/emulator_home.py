"""Bounded HOME probe + exact rollback, for disposable emulators only."""
import json
import re
import subprocess
import time


def probe_home(adb, package, component, output, allow_unsupported=False):
    assert len(adb) == 3 and adb[0] == 'adb' and adb[1] == '-s' and re.fullmatch(r'emulator-\d+', adb[2]), 'Emulator target required'
    assert package == 'dev.nurislam.tv.debug' and component == package + '/dev.nurislam.tv.MainActivity', 'Only our TV test package may be probed'
    output.mkdir(parents=True, exist_ok=True)
    def run(*args):
        return subprocess.run(adb + list(args), check=True, capture_output=True, text=True, timeout=45).stdout
    assert run('shell', 'getprop', 'ro.kernel.qemu').strip() == '1', 'Never probe a physical device'
    user_id = run('shell', 'am', 'get-current-user').strip()
    assert user_id.isdigit(), 'Cannot determine active Android user'
    def resolve():
        value = run('shell', 'cmd', 'package', 'resolve-activity', '--brief', '--user', user_id, '--query-flags', '0x10000', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME').strip().splitlines()[-1]
        assert re.fullmatch(r'[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+', value), 'Cannot identify HOME safely'
        return value
    def canonical(value):
        pkg, cls = value.split('/', 1)
        return pkg + '/' + (pkg + cls if cls.startswith('.') else cls)
    def roles():
        return run('shell', 'cmd', 'role', 'get-role-holders', '--user', user_id, 'android.app.role.HOME').strip().splitlines()
    def wait(expected):
        deadline = time.monotonic() + 5
        while True:
            value = resolve()
            if canonical(value) == canonical(expected) or time.monotonic() >= deadline:
                return value
            time.sleep(.15)
    def shot(name):
        with (output / (name + '.png')).open('wb') as stream:
            subprocess.run(adb + ['exec-out', 'screencap', '-p'], stdout=stream, check=True, timeout=30)
    stock, stock_roles = resolve(), roles()
    assert package not in stock and 'ResolverActivity' not in stock, 'Known independent stock HOME required'
    assert len(stock_roles) <= 1, 'Unexpected multi-holder HOME role'
    facts = {'before': {'resolver': stock, 'roles': stock_roles}}
    changed = False
    try:
        changed = True  # Mark before the mutation: even a failed transport must attempt rollback.
        facts['setCommand'] = run('shell', 'cmd', 'package', 'set-home-activity', '--user', user_id, component)
        assigned = wait(component)
        launch = run('shell', 'am', 'start', '-W', '--user', user_id, '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME')
        actual = re.search(r'^Activity:\s*(\S+/\S+)', launch, re.MULTILINE)
        assert actual, 'Cannot confirm actual HOME launch: ' + launch
        actual = actual[1]
        facts['assigned'] = {'resolver': assigned, 'roles': roles(), 'launch': actual, 'launchReport': launch}
        shot('assigned-home')
        if canonical(assigned) == canonical(component) and canonical(actual) == canonical(component):
            assert facts['assigned']['roles'] == [package], 'HOME role disagrees with resolver'
            facts['result'] = 'PASS'
        else:
            # Some TV firmware keeps its stock HOME even after accepting the role.
            # This is NOT a successful assignment, never infer success from ROLE_OK.
            assert allow_unsupported and canonical(assigned) == canonical(stock) and canonical(actual) == canonical(stock), 'HOME assignment not confirmed: ' + json.dumps(facts['assigned'])
            facts['result'] = 'UNSUPPORTED: role change did not change the actual stock HOME'
    finally:
        if changed:
            if stock_roles:
                run('shell', 'cmd', 'package', 'set-home-activity', '--user', user_id, stock)
            else:
                run('shell', 'cmd', 'role', 'clear-role-holders', '--user', user_id, 'android.app.role.HOME')
            restored = wait(stock)
            restored_roles = roles()
            facts['restored'] = {'resolver': restored, 'roles': restored_roles}
            (output / 'home-probe.json').write_text(json.dumps(facts, indent=2))
            assert canonical(restored) == canonical(stock) and restored_roles == stock_roles, 'STOP: original HOME resolver/role not restored'
            returned = run('shell', 'am', 'start', '-W', '--user', user_id, '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME')
            returned_activity = re.search(r'^Activity:\s*(\S+/\S+)', returned, re.MULTILINE)
            assert returned_activity and canonical(returned_activity[1]) == canonical(stock), 'STOP: actual stock HOME launch not confirmed'
            facts['restored']['launch'] = returned_activity[1]
            (output / 'home-probe.json').write_text(json.dumps(facts, indent=2))
            shot('restored-stock-home')
    return facts['result']
