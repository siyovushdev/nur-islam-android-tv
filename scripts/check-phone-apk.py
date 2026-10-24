"""Install/smoke-test the distributed phone APK on an explicitly selected emulator only."""
import argparse
import datetime
import json
from zoneinfo import ZoneInfo
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("apk", type=Path)
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


def screen_with(label, control=False):
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        run("shell", "uiautomator", "dump", "/sdcard/nur-phone-smoke.xml")
        xml = run("exec-out", "cat", "/sdcard/nur-phone-smoke.xml")
        (output / "screen.xml").write_text(xml)
        root = ET.fromstring(xml)
        for node in root.iter("node"):
            if node.get("text") == label:
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


def screenshot(name):
    with (output / (name + ".png")).open("wb") as file:
        subprocess.run(adb + ["exec-out", "screencap", "-p"], stdout=file,
                       check=True, timeout=30)


try:
    result = run("install", "--no-incremental", str(args.apk))
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
    # Exercise the eligible-source confirmation path with an explicitly fabricated CI fixture.
    fixture.update(approved=True, source="Календарь мечети, протокол 42")
    push_fixture()
    tap("Импорт из локальной папки")
    screen_with("Проверка расписания")
    assert screen_with("Применить расписание", control=True).get("enabled") == "false"
    screenshot("import-unconfirmed")
    tap("Подтверждаю: источник утверждён мечетью")
    assert screen_with("Подтверждаю: источник утверждён мечетью", control=True).get("checked") == "true"
    assert screen_with("Применить расписание", control=True).get("enabled") == "true"
    assert screen_with("Применить расписание", control=True).get("checkable") == "false"
    screenshot("import-confirmed")
    tap("Применить расписание")
    screen_with("Расписание применено")
    screenshot("import-success")
    tap("Вернуться к табло")
    screen_with("15:15")
    screenshot("import-board-test-fixture")
    (output / "result.txt").write_text("PASS: signed APK install, LAUNCHER, no HOME entry point, touch settings/theme/scale, cold process restart; import test-source block/cancel/confirm/apply/result/board\n")
finally:
    (output / "android-runtime.txt").write_text(run("logcat", "-d", "-t", "150", "-s", "AndroidRuntime"))
