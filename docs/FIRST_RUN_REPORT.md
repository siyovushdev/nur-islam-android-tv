# Issue #11 — программный результат первоначальной настройки

Ветка `codex/tv-first-run-kiosk` от `main` после merged PR #10. Один модуль Kotlin/Compose; существующие темы, настройки, часы и расписание сохранены. Код календаря/AtomicFile не изменялся.

## Реализовано

- Первый запуск: четыре шага, проверка возможностей/статуса устройства, HOME, дополнительный kiosk, итог. Можно отказаться и открыть обычное табло. Завершение сохраняется отдельно от настроек табло. Повторный вход из существующего Release-меню HOME.
- API 29+ HOME-role request, fallback к HOME settings / обычному HOME resolver. Результат всегда сверяется по фактическому обработчику HOME, также после resume. `RESULT_OK` не считается доказательством назначения. Видны версия Android, пакет, роль HOME, Device Owner, разрешение и состояние Lock Task, доступность legacy provisioning.
- Защищённый DeviceAdminReceiver, metadata; modern mode/compliance handlers для API 31+. Только fully managed из системного allowed-list, compliance только при настоящем DO. На API 26–30 отдельный legacy flow for-result доступен лишь при положительной системной проверке и наличии handler, с подтверждением и предупреждением о риске системного сброса. Нет wipe/выдачи DO/активации legacy admin под видом DO.
- Настоящий Lock Task только после текущего `isLockTaskPermitted`. Наш DO добавляет только собственный пакет, сохраняя остальных. Внешняя DPC allowlist не меняется. Проверяются `LOCKED` и `NONE`, pinning не принимается за kiosk.
- Перед постоянным kiosk: 20-секундная проба, обязательный успешный выход с D-pad, отдельное подтверждение с отменой по умолчанию. Таймаут/BACK/background отменяют пробу. Дисковый marker позволяет отменить прерванную процессом пробу; сохранённый kiosk восстанавливается только при действующем разрешении.
- Безопасный выход: опт-аут перед остановкой; ошибка записи не препятствует попытке разблокировать задачу. При отказе Android выход не объявляется успешным. Снимаются только созданные нами HOME-policy/allowlist entry; состояние Device Owner остаётся. Без другого Launcher и HOME settings снимается kiosk, но HOME-policy сохраняется с понятной ошибкой.
- Все действия доступны с обычного D-pad, страницы прокручиваются и вращаются вместе с табло в native portrait / landscape / CW / CCW. Текущие настройки темы, масштаба, подписи и JSON доступны из мастера.
- [Инструкция установки без ADB и managed provisioning](TV_SETUP.md), включая подписанный APK, QR template/checksum, отдельную подготовку установщиком и модельные ограничения.

## Автоматические проверки

Команда полного прогона (JDK 17, SDK 35):

```bash
./gradlew :app:assembleDebug :app:assembleRelease :app:test :app:lintDebug :app:lintRelease :app:assembleDebugAndroidTest
```

- Debug/Release APK и instrumentation APK собраны. Release unsigned, требует постоянной подписи владельца до установки.
- 230 Debug + 195 Release = **425 успешных JVM/Robolectric выполнений тестов**, без skipped. Включают Compose UI действительной MainActivity в обоих вариантах, прежние проверки расписания/AtomicFile/тем/настроек и новые проверки устройства.
- Контроллер безопасности и реальный Android adapter проверены в Robolectric на **API 26, 29, 30, 31, 35**. Provisioning hooks проверены на API 29/30 (modern flow отвергается) и 31/35 (fully managed/compliance). Это не аппаратный enrollment.
- Ошибки: неразрешённый kiosk, отзыв разрешения перед запуском/рестартом, pinning вместо LOCKED, отказ allowlist/stop, отсутствие другого Launcher, ошибка записи preferences, некорректный тип preferences, прерывание пробы процессом/background. Проверены реальные вызовы Activity.start/stopLockTask через Robolectric framework shadows, сохранение согласия, повторный запуск и штатный D-pad путь выхода.
- UI: отказ/ложный OK HOME не подделывает назначение, пропуск/повторный вход, завершение/пересоздание, объяснение ограничений provisioning, проба/подтверждение/автовыход, обе темы и четыре ориентации. Новые 48 native Skia renders, [13 выбранных скриншотов](../design/screenshots/setup/README.md).
- Android Lint Debug/Release: 0 errors, 11 warnings: target/dependency updates и стилистическая рекомендация KTX edit в двух preference-хранилищах. Прямой commit позволяет проверить Boolean результата записи. Новая API-граница allowed provisioning modes привязана к API 31; предупреждение о ней устранено.
- CI workflow выполняет ту же команду и публикует APK, отчёты и снимки. Состояние CI окончательного коммита видно в Checks Draft PR; аппаратная проверка не подменяется CI.

## Исправленные при собственной проверке случаи

Ошибка сохранения настроек не должна прерывать выход из Lock Task — системная остановка теперь выполняется в `finally`. Незавершённая проба отмечается на диске до старта, чтобы падение процесса не превращало её в постоянное включение. При отзыве полномочий сохранённое согласие не запускает screen pinning. HOME не объявляется выбранным по одному result code. Учитывается API 31 для allowed provisioning modes; прежние версии используют legacy путь.

## Что остаётся аппаратной приёмкой

Ни на рабочий ТВ, ни на другое физическое устройство APK не устанавливался; provisioning/Device Owner/kiosk на них не включались. Instrumentation APK собран, **connected instrumentation на реальном устройстве не запускался**. Скриншоты — Compose под Robolectric, не системные диалоги физического ТВ.

Нужны модель/firmware и отдельное согласование тестового устройства: реальный HOME-role/chooser и возврат Launcher, доступный с пульта managed enrollment/QR (если есть), собственный/внешний DPC, ввод/выход Lock Task, рестарт/3+ reboot/AC loss, standby/CEC/overscan и 72 часа. Подтверждённой no-ADB managed provisioning модели пока нет; для несовместимых ТВ остаётся обычный HOME или обычное приложение. Не обещаем универсальную выдачу DO после обычной установки. Не выполнялись merge, production release, автоматический factory reset или настройка рабочего телевизора.
