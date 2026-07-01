# Budgeting Project

El proyecto **Budgeting** es una API REST diseñada para actuar como un asistente financiero inteligente. Permite registrar, categorizar y listar transacciones, integrando capacidades de inteligencia artificial para interpretar texto o audio en lenguaje natural, comprender el contexto del usuario y ejecutar acciones automáticamente sobre una base de datos PostgreSQL.

## Funcionalidades Principales

*   **Gestión de Transacciones**: Permite registrar gastos con una descripción, monto y categoría predefinida (por ejemplo, `COMIDA`, `FARMACIA`, `ROPA`, `TRANSPORTE`), así como listar transacciones filtrando por dichas categorías.
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

La arquitectura oficial del MVP es **pragmatic Layered Architecture** con límites claros entre transporte, orquestación, reglas de negocio e integraciones.

Como no-objetivo explícito para este desafío: **strict Hexagonal Architecture and full Clean Architecture are out of scope for this MVP**. La prioridad es mantener compatibilidad, claridad y velocidad de entrega sin agregar abstracciones que el MVP todavía no necesita.

### Quick path

*   Los controladores deben seguir siendo adapters finos de HTTP/transporte y no dueños de reglas de negocio.
*   La lógica de casos de uso vive en `application/`.
*   La persistencia, seguridad, IA y wiring de Spring quedan en los bordes de infraestructura.

### Responsabilidades por capa

*   `domain/`: core business models, invariants, and repository contracts.
*   `application/`: use-case orchestration, transaction boundaries, and user-scoped operations.
*   `infraestructure/`: HTTP, persistence, security, and framework adapters.
*   `infraestructure/ai/`: AI-facing orchestration owned by the infrastructure edge.
*   `infraestructure/http/assistant/`: assistant/demo HTTP adapters and AI HTTP error responses.
*   `config/`: configuración de Spring Boot, seguridad y Flyway para sostener los límites anteriores.

### Restricciones del MVP

*   El paquete `infraestructure` mantiene ese spelling por compatibilidad y no se renombra en este cambio.
*   La interpretación asistida por IA produce un borrador; persistir una transacción sigue siendo un paso explícito de confirmación del usuario. Cualquier refactor futuro del flujo IA debe preservar la confirmación antes del guardado como una regla obligatoria.
*   Manual transaction creation remains available even if AI flows fail or are not used; manual editing is part of the target MVP scope but must be introduced through an explicit backend change.

## Endpoints Notables

*   `POST /transactions`: Crea manualmente una transacción pasándole el esquema explícito de JSON.
*   `GET /transactions/{category}`: Lista las transacciones guardadas que pertenezcan a la categoría especificada.
*   `POST /transactions/ai`: conserva el flujo asistido actual de transcripción, tool calling y respuesta de audio; para un borrador con confirmación previa usá `POST /transactions/interpret`.
*   `POST /transactions/interpret`: Interpreta un prompt de texto y devuelve un `TransactionDraft` para revisión/confirmación antes de guardar.
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
