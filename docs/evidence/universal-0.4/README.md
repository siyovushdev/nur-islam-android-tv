# Проверка универсальной версии 0.4

Это реальные снимки Android TV emulator API 34, не физического телевизора.
Исходная проверка: commit `7fdfaef38d01edd87be92c0e7c3fef5581ce4a8c`,
[CI TV PASS](https://github.com/siyovushdev/nur-islam-android-tv/actions/runs/40347780424).
PhoneDebug API 35 также прошёл: системный выбор файла, ожидание/наступление/завершение
икамата, события, Рамадан, атомарная замена/откат и повторный запуск.
[Календарь](phone-calendar-preview.png), [наступление икамата](phone-iqamah-arrival.png).

Весь этот CI пока не зелёный: задания Release не получили доступ к draft assets;
это исправляется отдельно. Снимки не заменяют проверку итоговых Release APK.

- [Применение Excel одним действием](tv-import-xlsx.png)
- [Elegant, горизонтальный экран](tv-elegant-landscape.png)
- [Classic, программный поворот CCW](tv-classic-ccw.png)

Время, название и даты Рамадана на снимках — искусственные данные проверки.
Реальные USB, overscan, HOME и работа после включения питания требуют приёмки устройства.
