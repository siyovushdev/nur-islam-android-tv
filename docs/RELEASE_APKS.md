# Release APK для телефона и TV

`release`: TV, `dev.nurislam.tv`. `phoneRelease`: телефон, `dev.nurislam.tv.phone`.
Оба без `debuggable`, API 26+, Kotlin/Compose. Телефон использует тот же сенсорный UI,
манифест и тему, что PhoneDebug, без HOME/Device Admin/provisioning. Тестовое расписание
в телефонном режиме по-прежнему помечается явно; Release-правила икамата требуют подтверждения.
TV Release не принимает тестовый источник как рабочий. Календарь в APK не вложен.

Это отдельные пакеты от Debug: данные тестовых пакетов автоматически не переносятся.
Постоянный Release-ключ отличается от тестового. Закрытый ключ/пароль находятся только
в защищённой локальной папке `~/.local/share/nur-islam/signing/release/`, вне репозитория;
сохраните защищённую резервную копию. В GitHub публикуются APK, checksums и публичный сертификат,
никогда ключи и пароли. Публичный PIN: `config/release-signing-cert.sha256`.

Сборка и проверки без установки:

```bash
./gradlew -PnurIslamVersionCode=30001 :app:assembleRelease :app:assemblePhoneRelease \
  :app:testReleaseUnitTest :app:testPhoneReleaseUnitTest :app:lintRelease :app:lintPhoneRelease
python3 scripts/sign-release-apks.py \
  --tv app/build/outputs/apk/release/app-release-unsigned.apk \
  --phone app/build/outputs/apk/phoneRelease/app-phoneRelease-unsigned.apk \
  --output distributions/release \
  --keystore "$HOME/.local/share/nur-islam/signing/release/nur-islam-release.jks" \
  --password-file "$HOME/.local/share/nur-islam/signing/release/release-password"
```

Для следующей поставки повышайте versionCode и сохраняйте тот же ключ. Сначала выполните
`apksigner verify --verbose --print-certs FILE.apk`, затем сверьте SHA256SUMS.txt.
Для установки откройте APK файловым менеджером и при запросе разрешите установку из этого
источника; ADB и root не нужны. На рабочих TV установка требует отдельного разрешения.

Первый выпуск опубликован как GitHub **pre-release v0.3.0-rc1**: это настоящие Release-сборки,
но аппаратная/религиозная приёмка не объявляется завершённой. По просьбе владельца эти APK
не устанавливались ни на устройства, ни на эмуляторы. Merge и изменение системных настроек
не выполнялись. [Инструкция администратора](ADMIN_GUIDE.md).

Проверки этой поставки: Release 448 + PhoneRelease 192 теста PASS, PhoneDebug 192
регрессионных теста PASS; lint обоих Release без ошибок. Проверены реальные манифесты,
отсутствие debuggable/тестового календаря в APK, подписи и SHA-256.
