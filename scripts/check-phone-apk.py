"""Install/smoke-test the distributed phone APK on an explicitly selected emulator only."""
import argparse
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


def screen_with(label):
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        run("shell", "uiautomator", "dump", "/sdcard/nur-phone-smoke.xml")
        xml = run("exec-out", "cat", "/sdcard/nur-phone-smoke.xml")
        (output / "screen.xml").write_text(xml)
        root = ET.fromstring(xml)
        for node in root.iter("node"):
            if node.get("text") == label:
                return node
        time.sleep(1)
    raise AssertionError("Phone UI did not show: " + label)


def tap(label):
    node = screen_with(label)
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
    (output / "result.txt").write_text("PASS: signed APK install, LAUNCHER, no HOME entry point, touch settings/theme/scale, cold process restart\n")
finally:
    (output / "android-runtime.txt").write_text(run("logcat", "-d", "-t", "150", "-s", "AndroidRuntime"))
