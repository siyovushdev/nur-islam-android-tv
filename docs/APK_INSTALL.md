# Тестовые APK Nur Islam

Это программная сборка для приёмки, не production-релиз и не утверждение расписания мечети.

- Телефон: `Nur-Islam-Phone-Debug.apk`, пакет `dev.nurislam.tv.phone.debug`, обычный список приложений, кнопка «Настройки», без HOME/kiosk.
- TV: `Nur-Islam-TV-Debug.apk`, пакет `dev.nurislam.tv.debug`, пульт: пять OK → настройки. Не устанавливайте на рабочий TV без разрешения владельца.
- Минимум Android 8 / API 26. Распакуйте ZIP, откройте APK штатным файловым менеджером, при необходимости временно разрешите установку из этого источника. Root и ADB не нужны. После установки отзовите разрешение неизвестного источника.

Постоянный тестовый сертификат SHA-256:
`52af1f59b0661ae514cd3b531ebe0ff91fb333a63138612932f84537fd880152`.
Рядом с APK — checksum, manifest и публичная проверка подписи.

**Старые CI-сборки имеют другой сертификат.** Для первого перехода сохраните исходные
JSON/XLSX и запишите все параметры экрана, икамата и Рамадана, затем один раз
переустановите тестовую копию. Не удаляйте без резервирования. Следующие опубликованные
APK с этим постоянным сертификатом устанавливаются поверх, с сохранением данных.

В телефонном ZIP есть `TEST-Kazan-2026.xlsx`: значения исходного Excel, **не официальный
календарь**. Настройки → Расписание → выбор JSON/XLSX или папки → проверка → checkbox
о тестовом статусе → применить. При другом системном годе он не становится календарём
этого года. Не переименовывайте даты и не исправляйте времена ради демонстрации.
Икамат по умолчанию выключен, Рамадан не содержит встроенных дат.

Полные инструкции текущего интеграционного Draft PR (merge не выполнен):

- [Администратору](https://github.com/siyovushdev/nur-islam-android-tv/blob/codex/product-readiness/docs/ADMIN_GUIDE.md).
- [Подпись и обновление](https://github.com/siyovushdev/nur-islam-android-tv/blob/codex/product-readiness/docs/SIGNING_AND_UPDATES.md).
- [XLSX](https://github.com/siyovushdev/nur-islam-android-tv/blob/codex/product-readiness/docs/XLSX_IMPORT.md).
- [Рамадан](https://github.com/siyovushdev/nur-islam-android-tv/blob/codex/product-readiness/docs/RAMADAN.md).
- [Матрица проверок](https://github.com/siyovushdev/nur-islam-android-tv/blob/codex/product-readiness/docs/QA_MATRIX.md).
