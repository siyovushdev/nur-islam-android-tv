# Проверка универсальной версии 0.4

Это реальные снимки Android TV emulator API 34, не физического телевизора.
Исходная проверка: commit `7fdfaef38d01edd87be92c0e7c3fef5581ce4a8c`,
[CI TV PASS](https://github.com/siyovushdev/nur-islam-android-tv/actions/runs/40347780424).
PhoneDebug API 35 также прошёл: системный выбор файла, ожидание/наступление/завершение
икамата, события, Рамадан, атомарная замена/откат и повторный запуск.
[Календарь](phone-calendar-preview.png), [наступление икамата](phone-iqamah-arrival.png).

Первоначальная ошибка доступа CI к draft assets исправлена ограниченным правом
contents:write только для задания приёмки Release. Подписанные TV/Phone Release
прошли установку/обновление RC3, JSON/XLSX/календарь и живой икамат в
[следующем запуске](https://github.com/siyovushdev/nur-islam-android-tv/actions/runs/40351493537).
Окончательные снимки Release, source SHA и протоколы установки приложены к
[v0.4.0-rc1](https://github.com/siyovushdev/nur-islam-android-tv/releases/tag/v0.4.0-rc1).
Снимки этого каталога относятся к обозначенной предварительной проверке, не подменяют финальные.

- [Применение Excel одним действием](tv-import-xlsx.png)
- [Elegant, горизонтальный экран](tv-elegant-landscape.png)
- [Classic, программный поворот CCW](tv-classic-ccw.png)

Время, название и даты Рамадана на снимках — искусственные данные проверки.
Реальные USB, overscan, HOME и работа после включения питания требуют приёмки устройства.
