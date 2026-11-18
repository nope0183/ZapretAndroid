# ZapretDroid Material

Android-приложение на основе Kotlin-порта Flowseal/zapret-discord-youtube. Работает через системный `VpnService` и не требует root. Интерфейс использует динамические цвета Material You; Android TV поддерживается через Leanback-запуск, навигацию D-pad и увеличенные элементы управления.

## Возможности

- 18 встроенных профилей, основанных на стратегиях из `general*.bat`.
- Запуск и остановка VPN с системным запросом разрешения.
- Импорт и экспорт JSON-профилей через системный выбор файлов.
- Проверка профиля: неизвестные стратегии, отсутствующие поля и изменённые значения отклоняются.
- Переключатели Telegram и автозапуска, журнал работы.

JSON-профиль предназначен для этого Android-приложения: это переносимый набор параметров одной встроенной стратегии, а не исполняемый Windows `.bat` и не произвольный скрипт. Импорт профиля не запускает VPN автоматически. Конфигурации и бинарные файлы Windows нельзя напрямую выполнить на Android.

## Сборка

Нужны JDK 17+ и Android SDK с платформой API 36.

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

APK появится в `app/build/outputs/apk/debug/app-debug.apk`.
APK из GitHub Actions собирается по тегу `v*` и прикрепляется к GitHub Release; он подписан стандартным debug-ключом, поскольку для публикации собственного release-ключа требуется отдельная подпись.

## Происхождение и лицензия

Это не официальный выпуск Flowseal или ZapretDroid. Android-основа и Kotlin-реализация стратегий взяты из [ezxidze/ZapretDroid](https://github.com/ezxidze/ZapretDroid) (MIT); файл лицензии сохранён в [LICENSE](./LICENSE). Логика стратегий опирается на [Flowseal/zapret-discord-youtube](https://github.com/Flowseal/zapret-discord-youtube). Material You / Android TV UI, импорт и проверка профилей добавлены в этой производной сборке.
