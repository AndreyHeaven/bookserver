# BookServerFull

Full-stack приложение «Библиотека книг»: Spring Boot 4 (backend) + Vue 3 / Vuetify 3 (frontend) + PostgreSQL.

## Структура

- `backend/` — Spring Boot 4 на Java 25, Gradle Kotlin DSL.
- `frontend/` — Vue 3 + Vuetify 3 + Vite + TypeScript (создаётся в Task 09).
- `sql/` — историческая схема MySQL (используется как референс).
- `.tasks/` — task package и план реализации.

## Быстрый старт

```bash
./gradlew :backend:bootRun
curl http://localhost:8080/health
```

Подробное README с инструкциями появится в финальной задаче плана (Task 10).
