# Этап 1 — результат проверки

Дата: 23 октября 2026. Ветка: `codex/stage-1-native-tv`.

## Изменения

- Gradle Kotlin DSL, один `app`, Kotlin/Jetpack Compose, TV manifest, fullscreen и keep-screen-on, без сети и WebView.
- Нативные шапка, часы, татарская/русская дата, отдельные название/время/countdown следующего намаза, восемь строк. Арка и орнамент — Canvas. Исходный PNG сохранён в `design/reference_original.png`, в APK не включён.
- Пять режимов экрана, отдельный landscape, поворот всего логического layout с перестановкой размеров, сохранение режима. Debug-меню с D-pad, MENU/M, BACK; остановленный визуальный mock отдельно помечен.
- Europe/Moscow, минутный snapshot для крупных часов/таблицы, секундное состояние только для countdown, перечитывание системного времени при resume и TIME_CHANGED.
- `ScheduleRepository` готов к будущему источнику из приватного локального JSON. Полные даты и политика approved/demo исключают перенос времён на другой год. Парсер и импорт относятся к этапу 2.
- Демо из PNG — только debug и дата 2026-10-23. Release использует пустой репозиторий, без демо-класса и времён молитв. Хиджра/икамат не вычисляются. Неоднозначный `fajrInMosque` остаётся в таблице и не участвует в расчёте следующей молитвы.

## Фактически выполнено

Среда: Linux, OpenJDK 17, Android SDK Platform 35. Версии сборки закреплены: AGP 8.9.2, Gradle 8.11.1 с SHA-256 дистрибутива, Kotlin/Compose compiler 2.1.20, Compose BOM 2025.04.01. Совместимость Gradle/AGP сверена с [официальной таблицей AGP 8.9](https://developer.android.com/build/releases/agp-8-9-0-release-notes); настройка компилятора — с [документацией Compose](https://developer.android.com/develop/ui/compose/compiler).

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew \
  :app:assembleDebug :app:assembleRelease :app:test \
  :app:lintDebug :app:assembleDebugAndroidTest

git diff --check
```

**PASS**: debug APK, unsigned release APK, instrumentation APK, 51 выполнения тестов (33 debug + 18 release), `git diff --check`. Lint: **0 ошибок, 10 предупреждений** — преимущественно о более новых зависимостях/target SDK, размере TV banner и рекомендациях backup/KTX; предупреждения не скрыты baseline или suppress.

18 чистых тестов запускаются в обеих конфигурациях: дата/часовой пояс, татарские символы, countdown и скачок времени, исключение служебных строк/неподтверждённого Фаджра, следующая дата после Иша, запрет прошлогодних/демо-данных в production, модели и геометрия AUTO/CW/CCW на 720p/1080p/4K.

15 Compose/Android проверок debug выполняются через Robolectric 4.15.1, Android 15/API 35, native Skia:

- Восемь видимых строк, отдельное время и countdown; отсутствие sample-времён при отсутствии расписания; реакция countdown на скачок Instant.
- **Фактические цвета всех четырёх углов после Compose-поворота** CW/CCW, дополнительно к чистой математике.
- Рендер с проверкой размеров bitmap: 1080×1920, 1920×1080, оба поворота в 1920×1080, 1280×720, 3840×2160, 2160×3840; fontScale 1.3.
- D-pad меняет режим, BACK закрывает меню, пересоздание Activity сохраняет CW, физическая LEFT перемещает выбор вниз в CW, запускается явно помеченное остановленное демо.

`apkanalyzer dex packages --defined-only app/build/outputs/apk/release/app-release-unsigned.apk` дополнительно подтвердил: `DemoScheduleRepository` отсутствует, `UnavailableScheduleRepository` присутствует.

## Снимки

Это рендер **реальных Compose-компонентов через Android View/Skia в Robolectric**, не фотография ТВ и не запуск APK на физическом устройстве. Снимки просмотрены: все восемь строк видны, подписи и таймер не перекрываются. При fontScale 1.3 текст подбирает размер внутри доступного места.

- [Портрет 1080×1920](../design/screenshots/portrait-1080.png)
- [Горизонтальный ТВ 1920×1080](../design/screenshots/landscape-1080.png)
- [Программный CW в 1920×1080](../design/screenshots/rotate-cw-1080.png)
- [Программный CCW в 1920×1080](../design/screenshots/rotate-ccw-1080.png)
- [Нет расписания](../design/screenshots/unavailable.png)

Остальные PNG (720p, 4K, fontScale) воспроизводятся командой тестов в `app/build/screenshots/`; CI сохраняет их вместе с отчётами как artifact. Эталонное автоматическое сравнение пикселей с PNG дизайна не заявляется.

## Ограничения и следующий шаг

- Инструментальные тесты **на эмуляторе/ТВ не запускались**. Локальные AVD API 33 и 36 с KVM завершались `kvm_arch_handle_exit: hardware error`; программный AVD API 33 не дошёл до полной готовности и был остановлен. Instrumentation APK успешно собран. Проверка нативного GPU, overscan и физических клавиш требует целевого ТВ; Robolectric не заменяет эту приёмку.
- Поворот подтверждён математикой, Compose-рендером и управлением меню, но направление монтажа и читаемость с 3–8 м нужно выбрать/проверить на месте. AUTO не распознаёт физический разворот корпуса.
- Выбор режима в первом этапе доступен через debug-диагностику. Защищённое production-меню администратора — отдельный этап. Release пока безопасный каркас без расписания, unsigned.
- Далее: утверждённый календарь/семантика Фаджра и Иша; полный JSON-парсер, валидация 365/366 дат, SAF, подтверждение и атомарная замена с резервной версией без пересборки APK. Работоспособность SAF/USB проверить на конкретной прошивке.
- HOME/kiosk, автозапуск, икамат и 72-часовой прогон не входят в этот PR.
