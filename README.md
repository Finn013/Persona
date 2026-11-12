# Persona 0.2

Android-браузер с изолированными профилями на базе **[Fulguris](https://github.com/Slion/Fulguris)**.

Каждый профиль — полноценный браузер Fulguris (вкладки, закладки, история, блокировщик рекламы,
сессии) в **своём процессе** со своей папкой данных WebView: куки, входы, localStorage, IndexedDB,
кэш и service worker'ы у профилей не пересекаются. Профиль открывается отдельным окном в «Недавних»,
в окне — цветная полоса профиля.

Возможности:
- список профилей: создание, правка, удаление, «стереть данные», цвет, стартовая (домашняя) страница;
- подмена устройства с пресетами (Pixel 8/7a, Galaxy S23/A54, Redmi Note 12, Xiaomi 13, OnePlus 11,
  Galaxy Tab S9): модель/версия Android через Client Hints, экран, DPR, ядра, память, GPU, языки,
  часовой пояс. Пустое поле — без подмены (серым показано реальное значение);
- ярлыки «сайт в профиле» на рабочий стол (меню Fulguris «Добавить на главный экран» внутри профиля
  или «Ярлык на рабочий стол» в списке профилей); ярлыки из Persona 0.1 продолжают работать;
- ссылки из других приложений → выбор профиля;
- Safe Browsing WebView и блокировка рекламы включены по умолчанию.

До 12 профилей одновременно (число слотов-процессов в манифесте).

## Сборка

GitHub Actions: `.github/workflows/build.yml` → `./gradlew assembleSlionsFullFdroidRelease`
(flavor без Firebase/Play), подпись ключом из секретов `KEYSTORE_B64` / `KEYSTORE_PASS`.

## Лицензии и авторство

Persona — модифицированная версия Fulguris © Stéphane Lenclud (CPAL-1.0, см. `LICENSE-CPAL-1.0`),
которая основана на Lightning Browser © Anthony Restaino (MPL-2.0, см. `LICENSE-MPL-2.0`).
Оригинальный README Fulguris — `README-Fulguris.md`. Изменения Persona: пакет `com.finn013.persona`,
`fulguris/activity/ProfileBrowserActivity.kt` и помеченные «Persona» правки в коде Fulguris.
Исходники Persona 0.1 — тег `v0.1`.
