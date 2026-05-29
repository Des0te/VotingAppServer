# VotingAppServer

Серверная часть приложения для голосований на Ktor.

## Что есть

- регистрация и вход по JWT;
- роли USER и ADMIN;
- создание, редактирование до старта и удаление голосований;
- активная лента, поиск, голосование и просмотр результатов;
- JDBC-слой для PostgreSQL;
- простое логирование запросов.

## База данных

По умолчанию сервер ждёт PostgreSQL:

```text
jdbc:postgresql://localhost:5432/voting_app
user: postgres
password: postgres
```

Можно переопределить настройки через переменные окружения:

```text
DATABASE_URL
DB_USER
DB_PASSWORD
JWT_SECRET
PORT
```

Таблицы создаются автоматически при старте приложения.

## Запуск

```bash
./gradlew run
```

На Windows:

```bat
gradlew.bat run
```

Сервер запускается на `http://localhost:8080`.

## Проверка

```bash
./gradlew build
```

Первый зарегистрированный пользователь получает роль ADMIN, остальные пользователи получают роль USER.
