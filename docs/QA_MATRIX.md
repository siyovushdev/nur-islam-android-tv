# Матрица программной приёмки Issue #18

Интеграционный stack: #19 → #20 → `codex/product-readiness`.
Проверки программные; физические H728/Xiaomi в этой работе не изменяются.

## Локально выполнено

| Сценарий | Проверка | Результат |
|---|---|---|
| Все варианты | Debug / Release / PhoneDebug + androidTest APK | PASS |
| JVM / Robolectric / Compose | Debug 486, Release 443, PhoneDebug 187 = 1116 тестов | PASS, 0 skipped / failures / errors |
| Android Lint | Три варианта | PASS, 0 ошибок; 13 предупреждений о версиях зависимостей/target/стиле KTX |
| Архивы/подпись | 3 Python regression guard tests + реальные manifest checks | PASS; физические serial и production applicationId отклоняются |
| JSON | Полный год/високосный/дубли/типы/ошибки/год/откат/нет данных | PASS |
| Оригинал XLSX | Независимый openpyxl TSV, 365×8 исходных времён | PASS; 143 Иша 18:50, без исправлений |
| XLSX безопасность | Числа/строки/1900/1904, формула cache/без cache/error, ZIP/XML/DTD/external/лимиты, отмена разбора | PASS |
| Импорт UI | Checkbox/disabled apply/cancel/result, TEST PhoneDebug, TV Release guards, D-pad/touch | PASS |
| Папки | Список внутри SAF tree, выбор D-pad/touch, ошибка провайдера/некорректная сохранённая URI, безопасный повторный выбор | PASS |
| Вытеснение года | Понятное предупреждение, когда два новых импорта вытесняют текущий год | PASS |
| Фаджр | UNCONFIRMED не получает вымышленное начало, поведение до/после восхода сохранено | PASS |
| Икамат/Джума | Пять молитв + отдельная Джума, fixed/offset/off, даты/исключения/семантика, переходы/конфликты/подсветка/неизвестные границы | PASS |
| Рамадан | 29/30/первый/последний/Eid/после/нет окончания/год/подтверждение/ошибка часов | PASS |
| Рамадан UI | Полный ввод с D-pad и touch, подтверждение/отмена/ошибка/save/recreate/HOME | PASS |
| Дизайн | Обе темы, 3 масштаба, RU/TT/оба; portrait/landscape/CW/CCW; 720p/1080p/4K/density проверки | PASS по Compose/Robolectric, без заявлений об аппаратном overscan/читаемости с расстояния |
| Геометрия Рамадана | Одинаковые координаты строк до/после, text overflow отсутствует | PASS |
| Сохранение | AtomicFile/framework на API 26/29/30/31/34/35, restart/rollback/corruption/pre/post commit | PASS |
| HOME/kiosk программно | API 26/29/30/31/34/35, отказ/отзыв/Keyguard/resume/ограниченные retries/без screen pinning | PASS (Robolectric) |
| Офлайн | В APK нет INTERNET; данные локальны, нет обязательного сервера | PASS; реальные RTC/питание отдельно |

## CI и настоящие эмуляторы

- #19 HEAD `7c03b42`: [CI PASS](https://github.com/siyovushdev/nur-islam-android-tv/actions/runs/40185119680), build + Phone API 35 install/touch/JSON/икамат/перезапуск.
- #20 HEAD `532c168`: [CI PASS](https://github.com/siyovushdev/nur-islam-android-tv/actions/runs/40186538461), полная сборка/тесты/lint + Phone API 35. Первый phone attempt встретил **Pixel Launcher ANR**, а не app crash (app launch Status: ok, AndroidRuntime без app exception). Повторён только этот job с тем же APK; результат PASS. История попыток не подменяется результатом приложения.
- Итоговый stack: CI и native-проверки ниже **ещё выполняются/будут запущены**. Итоговый статус фиксируется после фактического прохождения, не по наличию скрипта.

| Native сценарий итогового stack | Планируемое доказательство | Статус |
|---|---|---|
| Phone API 35, опубликованный подписанный APK | SAF XLSX, 2920 значений, persisted folder grant + D-pad listing, JSON/rollback, Ramadan touch, настоящий отсчёт/приход/завершение икамата | Ожидает CI |
| Phone upgrade | Более высокий versionCode, один закреплённый сертификат, SHA-256 приватных schedule/iqamah/Ramadan/settings до/после | Ожидает CI |
| Android TV API 34 x86 | Реальные instrumentation MainActivity: XLSX 2920/apply/rollback, D-pad/themes/rotations/scales/recreate, Ramadan confirmation/save | Ожидает CI |
| TV upgrade | Приватные settings/calendar/Ramadan сохранены, native suite на обеих версиях | Ожидает CI |
| HOME на TV emulator | Временное назначение и точный возврат resolver штатного Launcher, без reboot/DO/Lock Task | Ожидает CI |
| Итоговые APK | PIN сертификата, реальные manifests, установка на emulator; Release unsigned | Локальные сборки/manifest PASS; CI APK ожидаются |

Проверки на API 26/29/30/31/34/35 в таблице выше — **Robolectric**, не шесть физических
устройств. Native TV emulator — x86, не ARM64 H728. Native Phone API 35 не заменяет
личную проверку на телефоне владельца. 72 часа не выполнялись.

## Аппаратная и религиозная приёмка

[Безопасный план](HARDWARE_ACCEPTANCE.md), [инструкция администратора](ADMIN_GUIDE.md),
[подпись/обновление](SIGNING_AND_UPDATES.md). Нужны отдельные разрешения владельца на
установку/HOME/reboot/питание, реальная модель Xiaomi, проверка пульта/SAF/CEC/RTC/72 часа.
Мечеть подтверждает исходник, Фаджр/Иша, икамат/Джума и даты Рамадана. Без этого нельзя
объявлять аппаратную/религиозную приёмку или production-готовность.
