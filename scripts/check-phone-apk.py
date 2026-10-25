"""Install/smoke-test the distributed phone APK on an explicitly selected emulator only."""
import argparse
import datetime
import json
import hashlib
from zoneinfo import ZoneInfo
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("apk", type=Path)
parser.add_argument("--baseline", type=Path)
parser.add_argument("--serial", default="emulator-5554")
args = parser.parse_args()
if not re.fullmatch(r"emulator-\d+", args.serial):
    parser.error("Only a local Android emulator is allowed; never a physical TV/phone")
adb = ["adb", "-s", args.serial]
package = "dev.nurislam.tv.phone.debug"
component = package + "/dev.nurislam.tv.MainActivity"
output = Path("phone-emulator-results")
output.mkdir(exist_ok=True)


def run(*command):
    return subprocess.run(adb + list(command), check=True, text=True,
                          capture_output=True, timeout=45).stdout


def screen_with(label, control=False, timeout=30, ignore_case=False):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        run("shell", "uiautomator", "dump", "/sdcard/nur-phone-smoke.xml")
        xml = run("exec-out", "cat", "/sdcard/nur-phone-smoke.xml")
        (output / "screen.xml").write_text(xml)
        root = ET.fromstring(xml)
        for node in root.iter("node"):
            if node.get("text") == label or (ignore_case and node.get("text", "").casefold() == label.casefold()):
                if not control:
                    return node
                # Compose can export a TextView child with enabled=true inside a
                # disabled Button/Checkbox. Inspect/tap the interactive ancestor.
                parents = {child: parent for parent in root.iter() for child in parent}
                while node is not None:
                    if node.get("clickable") == "true" or node.get("checkable") == "true":
                        return node
                    node = parents.get(node)
                raise AssertionError("Label has no control: " + label)
        time.sleep(1)
    raise AssertionError("Phone UI did not show: " + label)


def tap(label):
    node = screen_with(label, control=True)
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.attrib["bounds"]))
    run("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


def tap_scrolled(label):
    for attempt in range(8):
        try:
            node = screen_with(label, control=True, timeout=2)
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.attrib["bounds"]))
            run("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
            return
        except AssertionError:
            run("shell", "input", "swipe", "500", "1500", "500", "600", "250")
    raise AssertionError("Cannot reach: " + label)


def screenshot(name):
    with (output / (name + ".png")).open("wb") as file:
        subprocess.run(adb + ["exec-out", "screencap", "-p"], stdout=file,
                       check=True, timeout=30)


try:
    assert run("shell", "getprop", "ro.kernel.qemu").strip() == "1", "Target must be an emulator"
    result = run("install", "--no-incremental", str(args.baseline or args.apk))
    (output / "install.txt").write_text(result)
    assert "Success" in result, result
    launcher = run("shell", "cmd", "package", "resolve-activity", "--brief",
                   "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", "-p", package)
    assert component in launcher, launcher
    home = run("shell", "cmd", "package", "query-activities", "--brief",
               "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME", "-p", package)
    assert "MainActivity" not in home, home
    launch = run("shell", "am", "start", "-W", "-n", component)
    (output / "launch.txt").write_text(launch)
    assert "Status: ok" in launch, launch
    screen_with("Настройки")
    screenshot("first-launch")
    tap("Настройки")
    tap("Тема: ELEGANT")
    screen_with("Тема: CLASSIC")
    tap("Масштаб текста: 100%")
    screen_with("Масштаб текста: 115%")
    screenshot("touch-settings")
    run("shell", "am", "force-stop", package)
    run("shell", "am", "start", "-W", "-n", component)
    tap("Настройки")
    screen_with("Тема: CLASSIC")
    screen_with("Масштаб текста: 115%")
    screenshot("saved-settings")
    # Real XLSX corpus, supplied by the owner but not an approved mosque calendar.
    # Exercise an actual SAF selection on Android rather than injecting a content URI.
    xlsx = Path("app/src/test/resources/xlsx/kazan-2026.xlsx")
    filename = "TEST-Kazan-2026.xlsx"
    run("shell", "mkdir", "-p", "/sdcard/Download/NurIslam")
    run("push", str(xlsx), "/sdcard/Download/NurIslam/" + filename)
    def tap_plain(label):
        node = screen_with(label, ignore_case=True)
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.attrib["bounds"]))
        run("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    tap("Расписание")
    tap("Импорт JSON / XLSX — выбрать файл")
    screen_with("NurIslam")
    screenshot("saf-file-picker")
    run("shell", "input", "keyevent", "4")
    screen_with("Импорт JSON / XLSX — выбрать файл")
    tap("Импорт JSON / XLSX — выбрать файл")
    tap_plain("NurIslam"); tap_plain(filename)
    screen_with("Проверка расписания")
    assert screen_with("Применить расписание", control=True).get("enabled") == "false"
    screenshot("xlsx-preview")
    tap("Понимаю: тестовое расписание не утверждено мечетью")
    tap("Применить расписание")
    screen_with("Тестовое расписание применено")
    envelope = json.loads(run("exec-out", "run-as", package, "cat", "files/schedule-store.json"))
    excel = json.loads(envelope["current"]["json"])
    oracle = [line.split("\t") for line in Path("app/src/test/resources/xlsx/kazan-2026.tsv").read_text().splitlines()]
    keys = ["suhurEnd", "fajrInMosque", "sunrise", "zawal", "dhuhr", "asr", "maghrib", "isha"]
    expected = {line[0]: dict(zip(keys, line[1:])) for line in oracle}
    assert len(excel["days"]) == 365 and excel["fajrMeaning"] == "UNCONFIRMED"
    for item in excel["days"]:
        assert {key: item[key] for key in keys} == expected[item["date"]]
    (output / "xlsx-values.txt").write_text("PASS: 365 x 8 saved native Android XLSX values exactly match independent original-cell TSV; no Fajr inference; saved formula caches only\n")
    screenshot("xlsx-result")
    tap("К расписанию")
    # A persisted tree grant plus our own D-pad listing is the alternative to Recents.
    tap("Выбрать папку с JSON / XLSX")
    # Android may remember the child folder selected by the preceding file picker.
    run("shell", "uiautomator", "dump", "/sdcard/nur-phone-smoke.xml")
    tree_xml = run("exec-out", "cat", "/sdcard/nur-phone-smoke.xml")
    if 'text="NurIslam"' in tree_xml and 'text="' + filename + '"' not in tree_xml:
        tap_plain("NurIslam")
    for label in ("USE THIS FOLDER", "Use this folder"):
        try:
            tap_plain(label); break
        except AssertionError:
            continue
    else:
        raise AssertionError("System tree picker did not offer folder confirmation")
    tap_plain("Allow")
    screen_with("Импорт JSON / XLSX")
    screen_with(filename)
    screenshot("native-folder-list")
    run("shell", "input", "keyevent", "20")
    run("shell", "input", "keyevent", "20")
    run("shell", "input", "keyevent", "23")
    screen_with("Проверка расписания")
    tap("Отменить")
    run("shell", "am", "force-stop", package)
    run("shell", "am", "start", "-W", "-n", component)
    tap("Настройки"); tap("Расписание"); tap("Выбрать папку с JSON / XLSX")
    screen_with("Импорт JSON / XLSX"); screen_with(filename)
    screenshot("persisted-folder-grant")
    run("shell", "input", "keyevent", "4")
    screen_with("Тема: CLASSIC")
    # Synthetic fixtures exist only in the disposable emulator, never inside the APK.
    year = datetime.datetime.now(ZoneInfo("Europe/Moscow")).year
    day = datetime.date(year, 1, 1)
    days = []
    fields = ["suhurEnd", "fajrInMosque", "sunrise", "zawal", "dhuhr", "asr", "maghrib", "isha"]
    values = ["05:08", "05:30", "07:12", "11:40", "12:05", "15:15", "17:48", "19:02"]
    while day.year == year:
        days.append({"date": day.isoformat(), **dict(zip(fields, values))})
        day += datetime.timedelta(days=1)
    fixture = {"schemaVersion": 1, "locationId": "qa-fixture", "locationName": "CI TEST ONLY",
               "year": year, "timezone": "Europe/Moscow", "fajrMeaning": "PRAYER_START", "days": days,
               "revision": "qa-42", "approved": False, "source": "ТЕСТ: конвертация Excel, не официальный календарь"}
    inbox = "/sdcard/Android/data/" + package + "/files/incoming-schedule.json"
    def push_fixture():
        path = output / "synthetic-fixture.json"
        path.write_text(json.dumps(fixture, ensure_ascii=False))
        run("push", str(path), inbox)
    tap("Расписание")
    push_fixture()
    tap("Импорт из локальной папки")
    screen_with("Проверка расписания")
    assert screen_with("Применить расписание", control=True).get("enabled") == "false"
    screenshot("import-test-source")
    tap("Отменить")
    # PhoneDebug alone permits a validated approved=true test source for demonstration.
    fixture.update(approved=True, source="ТЕСТ ДЛЯ ТЕЛЕФОНА")
    push_fixture()
    tap("Импорт из локальной папки")
    screen_with("Проверка расписания")
    assert screen_with("Применить расписание", control=True).get("enabled") == "false"
    screenshot("import-unconfirmed")
    tap("Понимаю: тестовое расписание не утверждено мечетью")
    assert screen_with("Понимаю: тестовое расписание не утверждено мечетью", control=True).get("checked") == "true"
    assert screen_with("Применить расписание", control=True).get("enabled") == "true"
    assert screen_with("Применить расписание", control=True).get("checkable") == "false"
    screenshot("import-confirmed")
    tap("Применить расписание")
    screen_with("Тестовое расписание применено")
    screenshot("import-success")
    tap("К расписанию")
    tap("Откат к предыдущему расписанию")
    tap("Подтвердить откат")
    tap("Понятно")
    restored = json.loads(json.loads(run("exec-out", "run-as", package, "cat", "files/schedule-store.json"))["current"]["json"])
    assert restored == excel, "Rollback did not recover the exact imported XLSX calendar"
    screenshot("xlsx-rollback")
    tap("Импорт из локальной папки")
    tap("Понимаю: тестовое расписание не утверждено мечетью")
    tap("Применить расписание")
    screen_with("Тестовое расписание применено")
    tap("Вернуться к табло")
    screen_with("ТЕСТОВОЕ РАСПИСАНИЕ")
    screen_with("15:15")
    screenshot("import-board-test-fixture")
    run("shell", "am", "force-stop", package)
    run("shell", "am", "start", "-W", "-n", component)
    screen_with("ТЕСТОВОЕ РАСПИСАНИЕ")
    screen_with("15:15")
    screenshot("restored-test-schedule-banner")
    tap("Настройки")
    tap_scrolled("Рамадан")
    tap("Показывать блок Рамадана")
    tap("Добавить даты Рамадана")
    today = datetime.datetime.fromtimestamp(int(run("shell", "date", "+%s").strip()), ZoneInfo("Europe/Moscow")).date()
    first_fast = today + datetime.timedelta(days=5)
    def enter_ramadan_date(label, value):
        tap(label); tap("Цифры без разделителей")
        run("shell", "input", "text", value.strftime("%Y%m%d"))
        run("shell", "input", "keyevent", "4")
        tap("Применить ввод")
    enter_ramadan_date("Первый день поста: не задан", first_fast)
    enter_ramadan_date("Ураза-байрам: не задан", first_fast + datetime.timedelta(days=29))
    tap("Источник: не указан")
    tap("Источник и документ согласования")
    run("shell", "input", "text", "Emulator-QA-fixture")
    run("shell", "input", "keyevent", "4")
    tap("Сохранить")
    tap_scrolled("Даты утверждены мечетью")
    tap_scrolled("Добавить даты в черновик")
    tap("Сохранить"); tap("Подтвердить и сохранить даты")
    screen_with("Даты сохранены"); tap("Вернуться к табло")
    screen_with("ТЕСТОВОЕ РАСПИСАНИЕ")
    def ramadan_caption():
        current = datetime.datetime.fromtimestamp(int(run("shell", "date", "+%s").strip()), ZoneInfo("Europe/Moscow")).date()
        return "До первого дня поста Рамадан: " + str((first_fast - current).days) + " дн."
    screen_with(ramadan_caption())
    screenshot("ramadan-phone-board")
    tap("Настройки")
    tap_scrolled("Икамат")
    screen_with("ТЕСТОВЫЕ ПРАВИЛА · не утверждены мечетью")
    screenshot("iqamah-defaults")
    tap("Функция икамата")
    # Pick a synthetic demonstration prayer whose arrival window is before its
    # next JSON prayer start. Missing iqamah rules must not bypass that boundary.
    device_seconds = int(run("shell", "date", "+%s").strip())
    target_seconds = (device_seconds // 60 + 2) * 60
    target = datetime.datetime.fromtimestamp(target_seconds, ZoneInfo("Europe/Moscow"))
    slots = [("Фаджр", "05:30", "12:05"),
             ("Джума (пятница)" if target.weekday() == 4 else "Зухр", "12:05", "15:15"),
             ("Аср", "15:15", "17:48"), ("Магриб", "17:48", "19:02"), ("Иша", "19:02", None)]
    for prayer_title, row_time, boundary in slots:
        if boundary is None or target + datetime.timedelta(minutes=1) <= datetime.datetime.combine(target.date(), datetime.time.fromisoformat(boundary), ZoneInfo("Europe/Moscow")):
            break
    if prayer_title == "Джума (пятница)":
        row_time = target.strftime("%H:%M")  # Jumuah's separate iqamah row, not JSON Dhuhr.
    tap_scrolled(prayer_title)
    tap("Режим: Отключено")
    tap("Значение: не задано")
    tap("Цифры без разделителей")
    run("shell", "input", "text", target.strftime("%H%M"))
    run("shell", "input", "keyevent", "4")  # Hide only the phone IME.
    tap("Применить ввод")
    tap_scrolled("Добавить в черновик")
    tap("Сохранить")
    screen_with("Сохранить правила?")
    screenshot("iqamah-confirm")
    tap("Подтвердить сохранение")
    screen_with("Правила сохранены")
    tap("Вернуться к табло")
    screen_with("ТЕСТОВОЕ РАСПИСАНИЕ · ТЕСТОВЫЙ ИКАМАТ")
    # A fixed iqamah with unknown start must not show a fabricated waiting countdown.
    screenshot("iqamah-fixed-before")
    run("shell", "am", "force-stop", package)
    run("shell", "am", "start", "-W", "-n", component)
    screen_with("ТЕСТОВОЕ РАСПИСАНИЕ · ТЕСТОВЫЙ ИКАМАТ")
    restored_xml = (output / "screen.xml").read_text()
    assert "До икамата" not in restored_xml, "Unknown start produced a false waiting countdown"
    assert "начало не подтверждено" in restored_xml.lower()
    assert target.strftime("%H:%M") in restored_xml, "Entered iqamah time was changed"
    screenshot("iqamah-restored")
    remaining = target_seconds - int(run("shell", "date", "+%s").strip())
    assert remaining > 0, "Fixture time elapsed before restart checks; timing budget needs adjustment"
    time.sleep(remaining + 2)
    screen_with("Икамат")
    arrival_xml = (output / "screen.xml").read_text()
    screenshot("iqamah-arrival")
    highlighted = [node for node in ET.fromstring(arrival_xml).iter("node") if node.get("content-desc", "").endswith("; выделен")]
    assert len(highlighted) == 1, "Active iqamah must highlight exactly one schedule row"
    description = highlighted[0].get("content-desc")
    assert prayer_title in description and row_time in description, "Arrival must highlight the configured prayer without replacing JSON times"
    assert highlighted[0].get("checkable") == "false", "Read-only row must not be exposed as a checkbox"
    time.sleep(60)
    # The arrival must finish instead of looping indefinitely.
    run("shell", "uiautomator", "dump", "/sdcard/nur-phone-smoke.xml")
    final_xml = run("exec-out", "cat", "/sdcard/nur-phone-smoke.xml")
    assert 'text="Икамат"' not in final_xml, "Arrival did not end"
    # Restore the actual next JSON prayer. It can legitimately still be Asr when
    # this synthetic fixed iqamah was entered before Asr's unconfirmed start.
    highlighted = [node for node in ET.fromstring(final_xml).iter("node") if node.get("content-desc", "").endswith("; выделен")]
    now = datetime.datetime.fromtimestamp(int(run("shell", "date", "+%s").strip()), ZoneInfo("Europe/Moscow"))
    prayer_fields = {"fajrInMosque", "dhuhr", "asr", "maghrib", "isha"}
    upcoming = [value for field, value in zip(fields, values) if field in prayer_fields and value > now.strftime("%H:%M")]
    if upcoming:
        assert len(highlighted) == 1 and ("; " + upcoming[0] + "; выделен") in highlighted[0].get("content-desc", ""), "Next-prayer highlight was not restored"
    else:
        assert not highlighted, "Tomorrow's prayer must not highlight today's row"
    screenshot("iqamah-finished")
    if args.baseline:
        def private_hashes():
            result = {}
            for name in ("files/schedule-store.json", "files/ramadan-store.json", "files/iqamah-store.json", "shared_prefs/display.xml"):
                contents = run("exec-out", "run-as", package, "cat", name)
                result[name] = hashlib.sha256(contents.encode()).hexdigest()
            return result
        before = private_hashes()
        run("shell", "am", "force-stop", package)
        install = run("install", "-r", "--no-incremental", str(args.apk))
        assert "Success" in install
        after = private_hashes()
        assert before == after, "Signed package update changed private data"
        (output / "update-preservation.json").write_text(json.dumps({"result": "PASS", "before": before, "after": after}, indent=2))
        run("shell", "am", "start", "-W", "-n", component)
        screen_with("ТЕСТОВОЕ РАСПИСАНИЕ · ТЕСТОВЫЙ ИКАМАТ"); screen_with(ramadan_caption())
        tap("Настройки"); screen_with("Тема: CLASSIC"); screen_with("Масштаб текста: 115%")
        run("shell", "input", "keyevent", "4")
        screenshot("signed-update-kept-data")
    (output / "result.txt").write_text("PASS: persistent-signer base-to-higher-version update with exact private-file preservation; real SAF XLSX, 2920 values, persistent tree and D-pad listing; Ramadan touch input/confirmation/save/restart; signed APK install, LAUNCHER, no HOME entry point, touch settings/theme/scale, cold process restart; unapproved test-source block/cancel; approved phone-test acknowledge/apply/result/board; persistent banner; iqamah default-off, explicit touch entry/save/confirmation, process restart, real-time arrival and automatic exit; no system settings changed\n")
finally:
    (output / "android-runtime.txt").write_text(run("logcat", "-d", "-t", "150", "-s", "AndroidRuntime"))
