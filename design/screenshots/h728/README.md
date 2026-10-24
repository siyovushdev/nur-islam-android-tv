# Реальные скриншоты H728 — ADB screencap

Это **не Robolectric и не mockup**: нативное Kotlin/Compose-приложение на Android 14/API 34, физический буфер 1920×1080, density 320. PNG получены непосредственно через ADB screencap, без ретуши, изменения размера или поворота файла. CW/CCW нужно рассматривать с соответствующим поворотом панели.

Все времена **синтетические, не официальные**. Табло подписано «ТЕСТ». Идентификаторы подключения и сырые диагностические дампы не публикуются. Системные HOME/DO/Lock Task не назначались.

[Подробный протокол, ограничения и следующий безопасный этап](../../../docs/H728_HARDWARE_REPORT.md).

## Дефекты до исправления

- [Растянутые строки меню](menu-before.png) → [естественный межстрочный интервал](menu-after.png).
- [Уменьшение шрифтов Classic при живом переключении/масштабировании](classic-text-before.png) → [итоговый Classic 115%](matrix-classic-landscape-1.15.png).

## Итоговая матрица

| Оформление / режим | 100% | 115% | 85% |
| --- | --- | --- | --- |
| Elegant landscape | [PNG](matrix-elegant-landscape-1.0.png) | [PNG](matrix-elegant-landscape-1.15.png) | [PNG](matrix-elegant-landscape-0.85.png) |
| Elegant CW | [PNG](matrix-elegant-cw-1.0.png) | [PNG](matrix-elegant-cw-1.15.png) | [PNG](matrix-elegant-cw-0.85.png) |
| Elegant CCW | [PNG](matrix-elegant-ccw-1.0.png) | [PNG](matrix-elegant-ccw-1.15.png) | [PNG](matrix-elegant-ccw-0.85.png) |
| Classic landscape | [PNG](matrix-classic-landscape-1.0.png) | [PNG](matrix-classic-landscape-1.15.png) | [PNG](matrix-classic-landscape-0.85.png) |
| Classic CW | [PNG](matrix-classic-cw-1.0.png) | [PNG](matrix-classic-cw-1.15.png) | [PNG](matrix-classic-cw-0.85.png) |
| Classic CCW | [PNG](matrix-classic-ccw-1.0.png) | [PNG](matrix-classic-ccw-1.15.png) | [PNG](matrix-classic-ccw-0.85.png) |

## Сценарии

- [Нет календаря](empty-calendar.png).
- [Первый запуск до исправления интервала](first-run-before.png), [мастер после исправления](wizard-after.png).
- [Предпросмотр синтетического файла](synthetic-preview.png), [запрет approved=false](unapproved-preview.png).
- [Ревизия B: Аср 16:02](calendar-replacement-b.png), [откат A: Аср 15:23](calendar-rollback-a.png).
- [Предпросмотр поворота с автоматическим возвратом](orientation-timeout.png).
- [После повторного холодного запуска процесса](persisted-board.png).
