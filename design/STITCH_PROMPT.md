# Google Stitch — главный промт дизайна

Сервис: https://stitch.google.com/

**Статус:** дизайн не сгенерирован и не утверждён. Ниже промт для ручного запуска в Stitch. Stitch используется **только для макетов**; реализация — нативная Kotlin/Jetpack Compose, без WebView/HTML.

## PROMPT 1 — основной экран (вставить в Stitch целиком)

Design a premium, calm, exceptionally legible digital prayer-time display for a Sunni Muslim mosque in Kazan, Russia, called "Нур Ислам". This is not a phone app, dashboard, or website. It is a dedicated, always-on WALL-MOUNTED ANDROID TV INFORMATION BOARD, viewed from 3–8 meters away by worshippers.

FIRST create a true 9:16 portrait TV composition at 1080 × 1920. It must be usable on a vertically mounted large television. The aesthetic should evoke the quiet dignity, symmetry, clarity and refined geometry of the great mosques of Madinah and Makkah, WITHOUT imitating their official branding or copying a specific interior. Contemporary Islamic architectural restraint: subtle arches, fine geometric lines, deep emerald / jade / midnight-green field, soft warm ivory typography, muted champagne-gold accents. Avoid excessive ornament, bright neon, hard gradients, glassmorphism, glitter, blinking objects, animations, cartoons, minarets, people, distracting mosque photos, advertisements, QR codes, and unverified Quranic or Arabic calligraphy.

Give the screen impeccable information hierarchy:
1) modest elegant heading "Нур Ислам", secondary "Казан · Казань";
2) very large high-contrast current local time "14:20", Gregorian weekday/date; smaller but readable Hijri area as optional placeholder;
3) a prominent NEXT PRAYER area with the name "Икенде · Аср", the scheduled time "15:15", and a clear label "До следующего намаза" plus countdown "00:55:00"; nobody should confuse the countdown with the clock or the scheduled prayer time;
4) a beautiful, extremely readable daily timetable with bilingual Tatar/Russian labels: "Сәхәр тәмам / Завершение сухура", "Иртәнге / Фаджр (в мечети)", "Кояш чыгу / Восход", "Зәвәл / Зенит", "Өйлә / Зухр", "Икенде / Аср", "Ахшам / Магриб", "Ястү / Иша". Make the FIVE prayers visually primary, and sunrise/zawal/suhur secondary informational items. Highlight the current/upcoming row gently, without blinking;
5) extremely subtle bottom status strip if useful; no unnecessary controls on the public screen.

Use natural, correct Cyrillic characters and maintain a clear typographic distinction between Tatar primary labels and smaller Russian translations. Do not invent Arabic text. Design as if it were used for years: timeless, respectful, serene, non-political, not commercial. Large digits, 5% TV-safe margins, excellent contrast, uncluttered layout, consistent 8px spacing grid. Keep the entire screen usable at 1080p without scrolling. Include full practical spacing, colors, type scale and component hierarchy.

Explore three visual directions on the same theme: A) elegant minimal Medina-inspired, B) refined green-and-warm-gold geometric, C) soft ivory-and-forest-green daylight; recommend the most readable for daily long-term operation. Use realistic but illustrative values only; displayed times are MOCKUP DATA and must not be interpreted as an official prayer schedule.

## PROMPT 2 — после выбора лучшего портретного макета

Adapt the selected design to a TRUE 16:9 1920 × 1080 landscape Android TV display. Do not rotate or stretch the portrait design. Recompose: large time and next prayer on one side, complete prayer timetable on the other, keeping safe margins and long-distance readability. Preserve the same visual identity, bilingual labels, contrast, and component styles. Provide a compact coherent design system (colors, typography, layout, spacing) suitable for implementation in native Jetpack Compose. Then create separate design states: (1) normal, (2) valid optional iqama countdown and prayer-in-progress state, (3) missing/outdated schedule warning, (4) a TV D-pad-friendly administrator settings screen. No flashy animation; no HTML/WebView implementation.

## После Stitch

- Утвердить скриншоты/макеты 9:16 и/или 16:9 после определения реальной ориентации ТВ.
- Сохранить экспортированный Stitch DESIGN.md/описание стилей рядом с этими файлами, если доступно.
- Передать Codex **ссылку на проект Stitch или сохранённые файлы макетов**.
- Никакой сгенерированный в Stitch web-код не переносить в Android-приложение.
