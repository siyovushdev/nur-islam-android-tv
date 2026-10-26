# Реальные эмуляторные снимки и протоколы Issue #18

Это **настоящий Android emulator**, не физический H728/Xiaomi и не Robolectric.
Контрольные параметры/источники в тестах не являются утверждёнными данными мечети.
PNG сохранены приложением через Compose capture или ADB screencap без ретуширования.

## Android TV API 34, официальный android-tv x86, 1920×1080

Проверенный интегрированный код `2530a71`, [все четыре CI-job PASS](https://github.com/siyovushdev/nur-islam-android-tv/actions/runs/40195504467).
Документация/снимки добавлены следующим коммитом; актуальные проверки — в [PR #21](https://github.com/siyovushdev/nur-islam-android-tv/pull/21/checks).

- [Elegant landscape](tv-elegant-landscape.png).
- [Classic CCW](tv-classic-ccw.png), поворот снят в физических координатах окна 1920×1080.
- [XLSX до подтверждения](tv-xlsx-preview.png): применение заблокировано, таблица прокручивается.
- [Рамадан после пересоздания Activity](tv-ramadan-restored.png).
- [Три instrumentation-теста до обновления](tv-instrumentation-baseline.txt) и [три после](tv-instrumentation-updated.txt).
- [SHA-256 приватных файлов до/после обновления](tv-update-preservation.json).
- [Точный протокол HOME](tv-home-probe.json): роль была принята, но resolver и фактический
  HOME остались Google TV Launcher; результат **UNSUPPORTED**, затем роль/resolver/запуск
  штатного Launcher восстановлены и проверены. Назначение Nur Islam HOME на этом образе не подтверждено.

Все восемь исходных времён × 365 дней сверены внутри настоящего Android с независимым TSV.
Оригинальный нескорректированный файл владельца дополнительно проверен тем же Kotlin-конвертером
вне репозитория: [протокол](original-workbook-audit.txt). Копия в test resources очищена только от приватных метаданных; значения
и сохранённые результаты формул не изменены.

## Phone API 35

Официальный `default` AOSP x86_64, профиль Pixel 5; тот же код и зелёный CI.

- [Настоящий SAF → XLSX preview](phone-xlsx-preview.png), применение до согласия заблокировано.
- [Список выбранной папки с D-pad](phone-native-folder-list.png); разрешение переживает рестарт.
- [Рамадан, оба языка](phone-ramadan-phone-board.png), синтетические подтверждённые UI-fixture данные.
- [Наступление икамата](phone-iqamah-arrival.png) и [его завершение](phone-iqamah-finished.png).
  Икамат на снимке задан тестом, исходное время строки Асра не изменяется.
- [После подписанного обновления](phone-signed-update-kept-data.png).
- [Результат всех шагов](phone-result.txt), [сверка 2920 значений](phone-xlsx-values.txt),
  [гибель процесса во время SAF](phone-saf-process-death.txt), [SHA-256 четырёх хранилищ до/после](phone-update-preservation.json).
- [Положительный HOME-control](api35-home-probe.json): **TV Debug APK** на стандартном Android:
  роль/resolver/фактический запуск Nur Islam, затем точный возврат Launcher3. Это не HOME
  функция PhoneDebug: в телефонном APK HOME/Device Admin/provisioning отсутствуют.

В предыдущих Google API image attempts системный **Pixel Launcher ANR** перекрывал
приложение до ввода; app launch был успешен, app crash не зафиксирован. Это сохраняется
в истории [предыдущего run](https://github.com/siyovushdev/nur-islam-android-tv/actions/runs/40193821738).
На AOSP все проверки сохранены; ошибок приложения/системного диалога не обходили.

Все API 26/29/30/31/34/35 из JVM-матрицы в [QA_MATRIX](../../QA_MATRIX.md) — отдельные
Robolectric-проверки. Ни этот каталог, ни они не доказывают reboot/AC/CEC/72 часа на панели.
