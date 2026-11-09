# SlotWise — бэкенд

Spring Boot сервис оптимизатора расписания SlotWise: API, безопасность, хранение данных и солверы.

О продукте, архитектуре и правилах разработки — в репозитории [slotwise-doc](https://github.com/vk-ab-schedule/slotwise-doc):
- [ARCHITECTURE.md](https://github.com/vk-ab-schedule/slotwise-doc/blob/main/ARCHITECTURE.md) — модули, авторизация, API;
- [MODEL.md](https://github.com/vk-ab-schedule/slotwise-doc/blob/main/MODEL.md) — модель и солверы;
- [DEVELOPMENT.md](https://github.com/vk-ab-schedule/slotwise-doc/blob/main/DEVELOPMENT.md) — ветки, коммиты, PR, миграции, соглашения по коду.

## Что нужно

- **JDK 25.** Gradle найдёт JDK 25 сам (toolchain), но удобнее, если `JAVA_HOME` указывает на неё.
- **Docker.** Локальная БД и тесты запускают PostgreSQL в контейнерах; Docker Desktop должен быть запущен.

Отдельно ставить Gradle и PostgreSQL не нужно.

## Быстрый старт

```bash
./gradlew build
```

Собирает все модули и запускает тесты. Тесты `app` поднимают PostgreSQL через Testcontainers.

```bash
./gradlew :app:bootRun
```

Запускает приложение на <http://localhost:8080>. При старте Spring сам поднимает PostgreSQL из `app/compose.yaml` и останавливает его при выходе. Миграции Flyway применяются при старте.

Чтобы войти, при первом запуске задайте администратора (подробнее — в разделе «Сборка для деплоя»):

```bash
SLOTWISE_ADMIN_EMAIL=admin@example.org SLOTWISE_ADMIN_PASSWORD=admin-password ./gradlew :app:bootRun
```

После запуска:

| Адрес | Что это |
| --- | --- |
| <http://localhost:8080/swagger-ui.html> | Swagger UI |
| <http://localhost:8080/v3/api-docs> | Спецификация OpenAPI (по ней фронтенд генерирует типы) |
| <http://localhost:8080/actuator/health> | Health-check |

Без входа доступны только эти адреса и `POST /api/auth/login`. Вход — через серверную сессию в cookie, изменяющие запросы требуют CSRF-токен: его значение из cookie `XSRF-TOKEN` передаётся в заголовке `X-XSRF-TOKEN`.

### Запуск из IntelliJ IDEA

- **С БД из compose.** Запустите `SlotwiseBackApplication`. В конфигурации запуска рабочая папка должна быть `app` (`$MODULE_WORKING_DIR$`), иначе Spring не найдёт `compose.yaml`.
- **С одноразовой БД.** Запустите `TestSlotwiseBackApplication` из `app/src/test`: БД поднимается через Testcontainers и после остановки удаляется.

## Модули

```
domain-model      чистая Java: входные данные солвера, назначения, SchedulingSolver, валидатор, метрики
solver-greedy     жадный солвер                         → domain-model
datagen           генератор синтетических наборов (seed) → domain-model
app               Spring Boot приложение                → всё перечисленное
buildSrc          общие настройки сборки (плагины slotwise.*)
```

Позже добавятся `solver-cpsat`, `solver-timefold` и `experiments`.

**Правило:** `domain-model`, солверы и `datagen` не зависят от Spring. Проверка `verifyNoSpring` входит в `./gradlew build` и падает, если Spring попал в их зависимости, даже транзитивно.

### Новый модуль на чистой Java

1. Создайте папку модуля с `build.gradle.kts`:

   ```kotlin
   plugins {
       id("slotwise.pure-java")
   }

   dependencies {
       api(project(":domain-model"))
   }
   ```

2. Добавьте модуль в `settings.gradle.kts`.
3. Код кладите в пакет `ru.vk.itmo.scheduler.slotwise.<модуль>`.

Java 25, Lombok, JUnit 5 и AssertJ подключаются плагином. Версии библиотек берутся из Spring Boot BOM. Версии самого Spring Boot и springdoc — в `gradle/libs.versions.toml`.

## База данных

- **Миграции** Flyway лежат в `app/src/main/resources/db/migration`. Схема создаётся только ими: Hibernate лишь проверяет её (`ddl-auto=validate`).
- **Именование и нумерация** миграций — в [DEVELOPMENT.md](https://github.com/vk-ab-schedule/slotwise-doc/blob/main/DEVELOPMENT.md#база-данных-и-миграции).
- **Версия PostgreSQL** — 18: в `app/compose.yaml`, в Testcontainers и на стенде.
- **Подключиться к локальной БД** клиентом: порт выбирается случайно, его показывает `docker ps` (контейнер `slotwise-postgres-1`). Логин, пароль и база — `slotwise`.

## API занятости, сбора и правил (B5)

Сессия и участники должны быть созданы заранее (B4). Контракт доступен в OpenAPI.

| Метод и путь `/api/sessions/{id}` | Доступ |
| --- | --- |
| `GET`, `PUT /busy-intervals/me` | Студент или преподаватель — участник сессии |
| `POST /busy-intervals/me/submit` | Участник сессии |
| `GET`, `PATCH /collection`, `POST /collection/close` | Организатор — владелец |
| `GET`, `PUT /rules` | Организатор — владелец |

`GET /busy-intervals/me` возвращает `revision`, `timeZone`, `editable`, личные `intervals`,
учебную занятость `knownBusy` (только чтение) и предупреждения `issues`.
При сохранении передаются `expectedRevision` из GET и полный список `intervals`;
при отправке ответа — `expectedRevision`. Пустой список допустим. Сохранение и отправка — разные действия.
Редактирование отправленного календаря до закрытия сбора сохраняет статус `SUBMITTED`.
Ревизия принадлежит участнику и меняется только при изменении его календаря или отправке ответа;
изменения других участников не мешают сохранять и отправлять свой календарь;
при `409` нужно обновить данные и повторить действие с актуальной ревизией.

Интервалы округляются наружу до 5 минут и обрезаются по периоду, рабочим дням и часам сессии
в её часовом поясе. На пересечении действует максимальная важность `CRITICAL > MEDIUM > LOW`.

Правила и состояние сбора возвращают `sessionVersion` и `inputVersion`.
`PUT /rules`, `PATCH /collection` и `POST /collection/close` требуют `expectedVersion`, равный `sessionVersion`.
`PATCH /collection` дополнительно принимает новый `endsAt`: в будущем и строго до начала сессии.
Закрытый сбор не открывается повторно; ответы сохраняются. Автозапуск солвера не выполняется (D-15).
Повторное закрытие с актуальной версией не меняет данные.

В `weights` передаются пять неотрицательных целых весов: `teacherGaps` (S1),
`commissionLoadSpread` (S2), `undesirableTime` (S3), `teacherRoomChanges` (S4), `movedEvents` (S5).
Также обязательны `roomChangeBufferMinutes` (кратно 5), `defaultAlgorithm`,
`timeLimitSeconds` (от 1 до 300 секунд) и `seed`.
Веса по умолчанию берутся из доменной `Weights.DEFAULT`: 1, 1, 1, 15, 30.
Шаг сетки задаёт `TimeGrid.DEFAULT_SLOT_MINUTES`: всегда 5 минут.
Миграция V8 добавляет отдельную ревизию календаря участника. Используется общая миграция `V7__revise_constraint_model.sql`.

## Непрерывная интеграция

GitHub Actions запускает проверку `Backend tests` для каждого PR, после push в `main` и `sprint/*`, а также вручную через Actions. Команда `./gradlew build --no-daemon --stacktrace --console=plain` собирает все модули, запускает тесты и `verifyNoSpring`; PostgreSQL для тестов поднимает Testcontainers на runner. JDK — Temurin 25.

JUnit XML и HTML-отчёты сохраняются в артефакте `test-reports` на 7 дней, в том числе при падении тестов. Сборка Docker-образов и деплой пока не настроены.

Для merge нужен один апрув от `@vk-ab-schedule/dream-team`. Это требование включается в правилах защиты веток GitHub.

## Сборка для деплоя

```bash
./gradlew :app:bootJar
```

Исполняемый jar — `app/build/libs/slotwise-back.jar`; имя не зависит от версии.

Вне локального режима подключение к БД задаётся стандартными переменными Spring:

| Переменная | Пример |
| --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://db:5432/slotwise` |
| `SPRING_DATASOURCE_USERNAME` | `slotwise` |
| `SPRING_DATASOURCE_PASSWORD` | — |

Первый администратор создаётся при старте, если администраторов в базе ещё нет:

| Переменная | Что это |
| --- | --- |
| `SLOTWISE_ADMIN_EMAIL` | Email первого администратора |
| `SLOTWISE_ADMIN_PASSWORD` | Пароль, не короче 8 символов; хэшируется при старте |
| `SLOTWISE_ADMIN_FIRST_NAME`, `SLOTWISE_ADMIN_LAST_NAME` | Необязательно: имя и фамилия, по умолчанию «Администратор SlotWise» |

Если администратор уже есть, переменные ни на что не влияют, их можно не убирать. Пароль потом меняется в интерфейсе.

Reverse proxy должен передавать `X-Forwarded-Proto`: по нему бэкенд понимает, что запрос пришёл по HTTPS, и ставит cookie флаг `Secure`.

## Как вносить изменения

1. Ветка `feature/<issue>-<кратко>` от текущей `sprint/N`.
2. Коммиты в формате Conventional Commits.
3. PR в `sprint/N` с `Closes #N`, зелёный CI, апрув участника Dream team.

Подробности — в [DEVELOPMENT.md](https://github.com/vk-ab-schedule/slotwise-doc/blob/main/DEVELOPMENT.md).
