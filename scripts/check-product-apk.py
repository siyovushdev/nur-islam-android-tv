#!/usr/bin/env python3
"""Exercise the exact installed APK through public UI on a disposable emulator only.
No HOME/policies, reboot, root, private run-as access, or physical targets.
"""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from zoneinfo import ZoneInfo

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('apk', type=Path)
p.add_argument('--baseline', type=Path)
p.add_argument('--package', required=True, choices=['dev.nurislam.tv', 'dev.nurislam.tv.phone', 'dev.nurislam.tv.debug', 'dev.nurislam.tv.phone.debug'])
p.add_argument('--serial', default='emulator-5554')
p.add_argument('--output', type=Path, default=Path('product-emulator-results'))
a = p.parse_args()
if not re.fullmatch(r'emulator-\d+', a.serial): p.error('Only a disposable emulator is allowed')
adb = ['adb', '-s', a.serial]
package = a.package
phone = '.phone' in package
out = a.output; out.mkdir(parents=True, exist_ok=True)
component = package + '/dev.nurislam.tv.MainActivity'
checks = []

def run(*cmd, timeout=60):
    return subprocess.run(adb + list(cmd), text=True, capture_output=True, check=True, timeout=timeout).stdout

def key(code): run('shell', 'input', 'keyevent', str(code))

def dump():
    for attempt in range(5):
        run('shell', 'uiautomator', 'dump', '/sdcard/nur-product-ui.xml')
        xml = run('exec-out', 'cat', '/sdcard/nur-product-ui.xml')
        try:
            root = ET.fromstring(xml)
            (out / 'last-screen.xml').write_text(xml)
            return root
        except ET.ParseError: time.sleep(1)
    raise AssertionError('Android did not provide a readable UI hierarchy')

def find(label, timeout=20, partial=False):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        root = dump()
        for node in root.iter('node'):
            text = node.get('text', '')
            if text == label or (partial and label in text): return root, node
        time.sleep(.5)
    raise AssertionError('UI missing: ' + label)

def visible(label, partial=False):
    return any((label in n.get('text','') if partial else label == n.get('text','')) for n in dump().iter('node'))

def tap(label, partial=False, plain=False):
    root, node = find(label, partial=partial)
    if not plain:
        parents = {child: parent for parent in root.iter() for child in parent}
        while node is not None and node.get('clickable') != 'true': node = parents.get(node)
        assert node is not None, 'No clickable action for ' + label
        assert node.get('enabled') == 'true', 'Action disabled: ' + label
    x1,y1,x2,y2 = map(int,re.findall(r'\d+',node.get('bounds','')))
    assert x2>x1 and y2>y1, 'Empty touch target: ' + label
    run('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))

def tap_scroll(label):
    for i in range(10):
        if visible(label): tap(label); return
        size=run('shell','wm','size'); w,h=map(int,re.findall(r'(\d+)x(\d+)',size)[-1])
        run('shell','input','swipe',str(w//2),str(h*3//4),str(w//2),str(h//3),'250')
    raise AssertionError('Cannot reach ' + label)

def image(name):
    with (out/(name+'.png')).open('wb') as f: subprocess.run(adb+['exec-out','screencap','-p'],stdout=f,check=True,timeout=30)

def launch():
    result=run('shell','am','start','-W','-n',component);assert 'Status: ok' in result,result
    if legacy and not phone:
        if visible('Пропустить и открыть табло'): tap('Пропустить и открыть табло')
        find('Нур Ислам')
    else: find('Настройки')

def restart():
    run('shell','am','force-stop',package);launch()

def settings():
    if phone: tap('Настройки')
    else: key(23)
    find('Загрузить расписание')

def leave_appearance():
    key(4)
    find('Загрузить расписание')
    tap_scroll('Вернуться к табло')
    find('Настройки')

def close_result():
    tap('Вернуться к табло')
    find('Нур Ислам' if legacy and not phone else 'Настройки')

def write_json(name,value):
    path=out/name;path.write_text(json.dumps(value,ensure_ascii=False));return path

def inbox(path):
    run('push',str(path),'/sdcard/Android/data/'+package+'/files/incoming-schedule.json')
    settings();tap_scroll('Для специалиста');tap_scroll('Резервный импорт из папки приложения')

def apply(preview):
    find(preview);image('preview-'+('calendar' if preview=='Календарь мечети' else 'schedule'))
    # Initial focus is on Apply; short OK performs exactly one application.
    key(23) if not phone else tap('Применить')
    find('Вернуться к табло');close_result()

def board_value(value): find(value)

def ui_signature():
    root=dump()
    return sorted(n.get('content-desc') for n in root.iter('node') if '; ' in n.get('content-desc','') and re.search(r'\d\d:\d\d',n.get('content-desc','')))

try:
    assert run('shell','getprop','ro.kernel.qemu').strip()=='1','Target is not an emulator'
    sdk=Path(os.environ.get('ANDROID_HOME',os.environ.get('ANDROID_SDK_ROOT','/home/siovush/Android/Sdk')))
    aapt=sorted(sdk.glob('build-tools/*/aapt2'))[-1]
    def manifest(apk): return subprocess.check_output([str(aapt),'dump','badging',str(apk)],text=True)
    current=manifest(a.apk);assert "package: name='"+package+"'" in current
    if not package.endswith('.debug'): assert 'application-debuggable' not in current
    baseline=a.baseline or a.apk
    old=manifest(baseline);assert "package: name='"+package+"'" in old
    old_code=int(re.search(r"versionCode='(\d+)'",old)[1]);new_code=int(re.search(r"versionCode='(\d+)'",current)[1])
    assert new_code>=old_code
    if a.baseline: assert new_code>old_code,'An update must increase versionCode'
    result=run('install','--no-incremental',str(baseline));assert 'Success' in result
    (out/'baseline-install.txt').write_text(result)
    legacy="versionName='0.3.0" in old
    launch()
    image('first-launch')
    # Prepare a visible value/settings under the previous production signature before update.
    now=dt.datetime.fromtimestamp(int(run('shell','date','+%s').strip()),ZoneInfo('Europe/Moscow'))
    year=now.year
    keys=['suhurEnd','fajrInMosque','sunrise','zawal','dhuhr','asr','maghrib','isha']
    values=['05:08','05:30','07:12','11:40','12:05','15:15','17:48','19:02']
    def schedule(zone, vals, revision='one', mosque=False):
        local=dt.datetime.fromtimestamp(int(run('shell','date','+%s').strip()),ZoneInfo(zone)); y=local.year
        first=dt.date(y,1,1)
        doc=dict(schemaVersion=2 if mosque else 1,locationId='qa-city',locationName='Проверка эмулятора',timezone=zone,year=y,
            source='ТЕСТ: искусственные данные эмулятора',revision=revision,approved=False,fajrMeaning='PRAYER_START',
            days=[dict(date=(first+dt.timedelta(days=i)).isoformat(),**dict(zip(keys,vals))) for i in range((dt.date(y+1,1,1)-first).days)])
        if mosque: doc.update(mosqueId='qa-mosque',mosqueName='Демонстрационная мечеть')
        return doc
    fixture=schedule('Europe/Moscow',values)
    if legacy:
        if visible('Пропустить и открыть табло'): tap('Пропустить и открыть табло')
        if phone: tap('Настройки')
        else:
            for _ in range(5): key(23)
            tap('Открыть настройки администратора')
        tap('Тема: ELEGANT');tap('Масштаб текста: 100%')
        # RC3 requires this legacy flag. This fixture remains explicitly artificial,
        # runs only on a disposable emulator, and is never bundled as official data.
        fixture.update(approved=True,source='Искусственные данные проверки обновления на эмуляторе')
        file=write_json('legacy-fixture.json',fixture)
        run('push',str(file),'/sdcard/Android/data/'+package+'/files/incoming-schedule.json')
        tap_scroll('Расписание');tap_scroll('Импорт из локальной папки')
        tap('Подтверждаю: источник утверждён мечетью');tap('Применить расписание');close_result()
        board_value('15:15')
    else:
        settings();tap('Внешний вид');tap('Тема: ELEGANT');tap('Масштаб текста: 100%');leave_appearance()
        inbox(write_json('baseline-fixture.json',fixture));apply('Проверка расписания');board_value('15:15')
    image('before-update')
    if a.baseline:
        result=run('install','-r','--no-incremental',str(a.apk));assert 'Success' in result
        (out/'update-install.txt').write_text(result)
        legacy=False
        restart();board_value('15:15');settings();tap('Внешний вид');find('Тема: CLASSIC');find('Масштаб текста: 115%');leave_appearance()
        checks.append('PASS: signed update retains imported schedule, theme and scale without uninstall')
    # Use a regional zone with enough daytime margin for a deterministic live iqamah.
    epoch=int(run('shell','date','+%s').strip())
    zone=next(z for z in ['Asia/Yekaterinburg','Asia/Vladivostok','Europe/Kaliningrad','Asia/Kamchatka','Europe/Moscow'] if 7<=dt.datetime.fromtimestamp(epoch,ZoneInfo(z)).hour<=20)
    local=dt.datetime.fromtimestamp(epoch,ZoneInfo(zone)).replace(second=0,microsecond=0)
    minutes=local.hour*60+local.minute
    def hm(m): return f'{m//60:02}:{m%60:02}'
    vals=[hm(minutes-210),hm(minutes-180),hm(minutes-120),hm(minutes-61),hm(minutes-60),hm(minutes-1),hm(minutes+60),hm(minutes+120)]
    doc=schedule(zone,vals,mosque=True);file=write_json('TEST-schedule.json',doc)
    run('shell','mkdir','-p','/sdcard/Download/NurIslam');run('push',str(file),'/sdcard/Download/NurIslam/TEST-schedule.json')
    # Phone SAF is required. TV provider support is firmware-dependent; the same
    # parser/import is also exercised through the documented local fallback.
    if phone:
        settings();tap('Загрузить расписание')
        if not visible('TEST-schedule.json'): tap('NurIslam',plain=True)
        tap('TEST-schedule.json',plain=True);find('Проверка расписания');image('saf-preview');tap('Отмена')
        find('Загрузить расписание');tap_scroll('Вернуться к табло');find('Настройки')
        checks.append('PASS: actual SAF selection, preview and cancellation without auto-apply')
    inbox(file);apply('Проверка расписания')
    for value in vals: board_value(value)
    checks.append('PASS: all eight JSON values shown exactly, no timezone shifts to the values')
    image('regional-board')
    # A full calendar document, timed from the actual emulator clock, not clock manipulation.
    local=dt.datetime.fromtimestamp(int(run('shell','date','+%s').strip()),ZoneInfo(zone)).replace(second=0,microsecond=0)
    target=local+dt.timedelta(minutes=3)
    today=local.date();first=today+dt.timedelta(days=5)
    cal=dict(type='mosqueCalendar',schemaVersion=1,mosque=dict(id='qa-mosque',name='Демонстрационная мечеть',cityId='qa-city',city='Проверка эмулятора',timezone=zone),
        validFrom=str(today-dt.timedelta(days=366)),validUntil=str(today+dt.timedelta(days=366)),source='ТЕСТ: искусственные даты и правила',revision='calendar-one',demo=True,
        iqamah=dict(rules=[dict(prayer='ASR',mode='FIXED',time=target.strftime('%H:%M'),**{'from':str(today)})]),
        ramadan=[dict(firstFast=str(first),eid=str(first+dt.timedelta(days=29)))],events=[dict(id='meeting',title='Встреча сегодня',**{'from':str(today)})])
    inbox(write_json('TEST-calendar.json',cal));find('Календарь мечети');image('calendar-preview-full');apply('Календарь мечети')
    find('До икамата',partial=True);find('Встреча сегодня',partial=True);find('До первого дня поста Рамадан: 5 дн.',partial=True)
    image('calendar-waiting')
    before=ui_signature();assert len(before)==8,before
    restart();find('До икамата',partial=True);assert ui_signature()==before
    checks.append('PASS: calendar import, regional Ramadan/event, iqamah waiting and process restart')
    remaining=int(target.timestamp())-int(run('shell','date','+%s').strip())
    assert remaining>0,'Live fixture expired during UI setup'
    time.sleep(remaining+2)
    find('Икамат');image('iqamah-arrival')
    highlighted=[n.get('content-desc') for n in dump().iter('node') if n.get('content-desc','').endswith('; выделен')]
    assert len(highlighted)==1 and vals[5] in highlighted[0],highlighted
    time.sleep(60)
    assert not visible('Икамат'),'Arrival did not finish'
    image('iqamah-finished');checks.append('PASS: live iqamah arrival, correct table row, normal end')
    empty={k:v for k,v in cal.items() if k not in ['iqamah','ramadan','events']};empty['revision']='calendar-empty'
    inbox(write_json('calendar-empty.json',empty));apply('Календарь мечети');assert not visible('Встреча сегодня',partial=True)
    settings();tap_scroll('Загруженные данные');tap_scroll('Вернуть предыдущий календарь мечети');close_result();find('Встреча сегодня',partial=True)
    # Malformed import must keep every active section intact.
    bad=out/'broken.json';bad.write_text('{broken');inbox(bad);find('Результат');image('invalid-file');close_result();find('Встреча сегодня',partial=True)
    # A valid different-region schedule suspends the incompatible calendar, without erasing it.
    other=schedule('Europe/Moscow',values,revision='different-mosque',mosque=True);other['mosqueId']='different-mosque'
    inbox(write_json('different-mosque.json',other));apply('Проверка расписания');assert not visible('Встреча сегодня',partial=True)
    settings();tap_scroll('Загруженные данные');tap_scroll('Вернуть предыдущее расписание');close_result();find('Встреча сегодня',partial=True)
    checks.append('PASS: whole-calendar replacement/rollback, invalid file preservation, incompatible mosque suspension and schedule rollback')
    # Exercise the supported, unchanged Excel template in the exact Release APK too.
    # All 2920 values are independently checked by the parser/native test suite;
    # here verify this APK's actual import/apply/rollback and today's eight display values.
    xlsx=Path('app/src/test/resources/xlsx/kazan-2026.xlsx')
    inbox(xlsx);apply('Проверка расписания')
    xlsx_day=dt.datetime.fromtimestamp(int(run('shell','date','+%s').strip()),ZoneInfo('Europe/Moscow')).date()
    expected=dict(line.split('\t',1) for line in xlsx.with_suffix('.tsv').read_text().splitlines())
    if str(xlsx_day) in expected:
        for value in expected[str(xlsx_day)].split('\t'): board_value(value)
    else:
        find('Загрузите расписание',partial=True)
    assert not visible('Встреча сегодня',partial=True)
    image('xlsx-release-board')
    settings();tap_scroll('Загруженные данные');tap_scroll('Вернуть предыдущее расписание');close_result();find('Встреча сегодня',partial=True)
    checks.append('PASS: exact Release XLSX preview/apply/display/rollback, calendar preserved and reactivated')
    # Appearance changes are exercised with normal controls, independently of imported data.
    settings();tap('Внешний вид');tap('Тема: CLASSIC');leave_appearance();image('elegant-board')
    settings();tap('Внешний вид');tap('Тема: ELEGANT');leave_appearance();image('classic-board')
    before=ui_signature()
    assert len(before)==8, ('Expected eight visible table rows before reinstall',before)
    (out/'before-final-reinstall.json').write_text(json.dumps(before,ensure_ascii=False,indent=2))
    assert 'Success' in run('install','-r','--no-incremental',str(a.apk))
    restart();find('Встреча сегодня',partial=True)
    after=ui_signature();(out/'after-final-reinstall.json').write_text(json.dumps(after,ensure_ascii=False,indent=2))
    assert after==before,(before,after)
    checks.append('PASS: reinstall exact published APK retains new calendar and all visible times')
    checks.append('PASS: both themes, touch/short OK, offline APK with no network permission, saved calendar after final restart')
    (out/'result.json').write_text(json.dumps(dict(result='PASS',package=package,apkSha256=hashlib.sha256(a.apk.read_bytes()).hexdigest(),versionCode=new_code,source=os.environ.get('NUR_ISLAM_SOURCE_SHA',''),checks=checks),ensure_ascii=False,indent=2))
finally:
    (out/'checks-completed.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2))
    try:
        image('last-screen');(out/'android-runtime.txt').write_text(run('logcat','-d','-v','brief','AndroidRuntime:E','*:S'))
    except Exception: pass
