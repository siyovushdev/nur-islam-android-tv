# Мастер первоначальной настройки — нативные Compose-снимки

Снимки сделаны `SetupScreenshotsTest` через Android View.draw + native Skia под Robolectric API 35. Это реальные пиксели Kotlin/Compose UI, **не** макет, HTML или генерация изображения. Факты системы для этих сцен моделируются тестом (`TEST RENDER`): Device Owner / allowlist / Lock Task на изображении **не доказывают** выдачу полномочий на физическом ТВ.

Тест генерирует 48 PNG: шесть страниц × Elegant/Classic × portrait/landscape/CW/CCW. В репозитории 13 выбранных изображений; полный набор — `app/build/screenshots/setup-*.png` и CI artifact `checks-and-screenshots`.

- [Начало, Elegant portrait](setup-elegant-portrait-check.png)
- [HOME, Elegant portrait](setup-elegant-portrait-home.png)
- [Kiosk, Elegant portrait](setup-elegant-portrait-kiosk.png)
- [Проба с таймером](setup-elegant-portrait-trial.png)
- [Отдельное подтверждение kiosk](setup-elegant-portrait-confirm.png)
- [Итоговая проверка](setup-elegant-portrait-summary.png)
- [Elegant landscape](setup-elegant-landscape-check.png)
- [Elegant CW](setup-elegant-cw-check.png), [CCW](setup-elegant-ccw-check.png)
- [Classic portrait](setup-classic-portrait-check.png), [landscape HOME](setup-classic-landscape-home.png)
- [Classic CW kiosk](setup-classic-cw-kiosk.png), [CCW итог](setup-classic-ccw-summary.png)

CW/CCW PNG записаны в физическом системном окне 1920×1080; для чтения их следует рассматривать с соответствующим поворотом панели. Portrait — 1080×1920. Файлы не поворачивались/ретушировались после захвата.

Воспроизведение: JDK 17, `./gradlew :app:testDebugUnitTest --tests '*SetupScreenshotsTest'`. Тест также проверяет D-pad, таймаут пробы, успешный выход и выбор отмены в подтверждении. Системные диалоги HOME, provisioning и реальные reboot/AC loss требуют отдельного тестового ТВ.
