# Budgeting Project

El proyecto **Budgeting** es una API REST diseñada para actuar como un asistente financiero inteligente. Permite registrar, categorizar y listar transacciones, integrando capacidades de inteligencia artificial para interpretar texto o audio en lenguaje natural, comprender el contexto del usuario y ejecutar acciones automáticamente sobre una base de datos PostgreSQL.

## Funcionalidades Principales

*   **Gestión de Transacciones**: Permite registrar gastos con una descripción, monto y categoría predefinida (por ejemplo, `GROCERIES`, `PHARMA`, `AUTO`), así como listar transacciones filtrando por dichas categorías.
*   **Asistente Financiero Inteligente**: Utiliza modelos de procesamiento de lenguaje natural (LLMs) configurados con un prompt de sistema financiero. El asistente recibe órdenes simples del usuario (por ejemplo, "Anota 50 mil pesos en supermercado") y mapea automáticamente el monto, descripción y categoría adecuada.
*   **Invocación de Herramientas (Tool Calling)**: Emplea *Tool Calling* a través de Spring AI para que los LLMs invoquen directamente los casos de uso internos de la aplicación (`PersistTransactionUseCase` y `ListTransactionsByCategoryUseCase`).
*   **Transcripción de Audio**: Ofrece endpoints que aceptan archivos de audio (MP3, etc.), los transcriben utilizando modelos de reconocimiento de voz y, de manera combinada, proceden a analizarlos para ejecutar el registro de transacciones dictadas por voz.

## Tecnologías Clave

*   **Java 25**: Lenguaje moderno base para la aplicación.
*   **Spring Boot 4.0.6**: Framework principal proveiendo infraestructura HTTP, Inyección de Dependencias (IoC), y auto-configuración de Data JPA.
*   **Spring AI (2.0.0-M4)**: Maneja la abstracción para conectarse con OpenAI y/o DeepSeek, procesando *Chat Models* (razonamiento y tool-calling) y *Audio Transcription Models*.
*   **PostgreSQL**: Base de datos relacional para persistir transacciones categorizadas.
*   **Docker Compose**: Facilita la orquestación del entorno de desarrollo (especialmente la base de datos de manera simplificada).
*   **Lombok**: Reduce el código repetitivo en entidades y DTOs (`@Getter`, `@AllArgsConstructor`).

## Arquitectura y Módulos de Código

La aplicación parece seguir un patrón de arquitectura limpia / hexagonal, organizando el código dentro de `dio.budgeting`:
*   `domain/`: Entidades core (`Transaction`, `TransactionId`), enums (`Category`) y abstracciones de repositorios (`TransactionRepository`).
*   `application/`: Casos de uso de negocio que actúan como puente e interactúan con la IA como *Tools*.
*   `infrastructure/http/`: Controladores REST (`TransactionController`, `ChatClientController`, `TranscriptionController`).
*   `infrastructure/persistence/`: Implementación de repositorios apuntando a la base Postgres.

## Endpoints Notables

*   `POST /transactions`: Crea manualmente una transacción pasándole el esquema explícito de JSON.
*   `GET /transactions/{category}`: Lista las transacciones guardadas que pertenezcan a la categoría especificada.
*   `POST /transactions/ai`: Recibe una nota de voz vía `multipart/form-data`, transcribe el audio, lo manda al modelo AI, el cual ejecuta las tools automáticas y persiste la transacción.
*   `POST /api/transcribe`: Endpoint simple de utilidad para testear la funcionalidad pura de transcripción subiendo un archivo.

## Base de datos y migraciones

La evolución del esquema se maneja con **Flyway** usando migraciones SQL versionadas en `src/main/resources/db/migration`.

Si Flyway detecta que tu historial local diverge de los archivos versionados, frená el arranque, corregí la causa y reiniciá la base local con:

```bash
docker compose down -v
docker compose up -d
```

## Entorno local (`.env`)

La app y Docker Compose leen variables de entorno desde un archivo `.env` en la raíz del proyecto. Compose lo carga automáticamente; la app usa los mismos valores para el datasource y la API key de OpenAI.

### Quick path (todo en Docker Compose)

1. Copiá el template y cargá tu API key: `cp .env.example .env`
2. (Opcional) Cambiá `POSTGRES_*` si querés otro usuario/clave/base.
3. Levantá base + backend juntos:
   ```bash
   docker compose up -d --build
   ```
4. La API queda en `http://localhost:8080`. Logs: `docker compose logs -f backend`.
5. Para reiniciar el esquema desde cero (Flyway): `docker compose down -v` y volvé a levantar.

> ¿Preferís correr la app desde tu IDE/JVM en vez de dentro del contenedor? Entonces solo levantá la base con `docker compose up -d database` y corré la app; `application.properties` ya usa `localhost` por default. El contenedor `backend`, en cambio, usa `POSTGRES_HOST=database`.

### Detalles

| Variable | Uso | Default seguro |
|----------|-----|----------------|
| `OPENAI_API_KEY` | `spring.ai.openai.api-key` en la app | sin default — **requerida** |
| `POSTGRES_HOST` | host de la base que usa la app | `localhost` (host-local) / `database` (dentro de Compose) |
| `POSTGRES_USER` | usuario de la base (Compose + app) | `app` |
| `POSTGRES_PASSWORD` | contraseña de la base (Compose + app) | `app` |
| `POSTGRES_DB` | nombre de la base (Compose + app) | `transaction` |

> `.env` está ignorado por git. `.env.example` queda versionado como referencia. Nunca commitees valores reales.
