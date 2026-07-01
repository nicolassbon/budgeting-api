# Endurecimiento de `/transactions/interpret`

Este documento resume el endurecimiento aplicado al endpoint `POST /transactions/interpret` para reducir abuso, mejorar el contrato HTTP y hacer más segura la interpretación asistida por IA en el MVP.

## Resumen rápido

- El endpoint sigue siendo autenticado y mantiene compatibilidad con los campos `description`, `amount` y `category`.
- Se agregó un `status` de alto nivel para distinguir respuestas `OK` e `INCOMPLETE`.
- Los prompts inválidos o fuera de alcance ya no llegan al flujo de IA como si fueran casos válidos.
- El rate limit dejó de depender de la sesión HTTP y ahora se ata al usuario autenticado.
- Los logs de interpretación evitan exponer el prompt crudo.

## Qué cambió

| Área | Cambio aplicado |
|---|---|
| Validación de prompt | Se exige mínimo de caracteres no vacíos, se mantiene el máximo, y se bloquean marcadores obvios de prompt injection o instruction override. |
| Contrato de respuesta | Las respuestas exitosas preservan `description`, `amount` y `category`, y agregan `status`. |
| Respuesta incompleta | Si la IA no puede completar todos los datos pero el prompt sigue siendo un gasto personal válido, responde `200 OK` con `status: INCOMPLETE`. |
| Fuera de alcance | Si el prompt no representa un gasto personal, el endpoint responde `422` con `assistant_out_of_scope`. |
| Límite de uso | Se aplica rate limiting en memoria para `/transactions/interpret`, con `429`, `Retry-After` y headers `RateLimit-*`. |
| Identidad del rate limit | La cuota ahora usa la identidad del usuario autenticado (`user:{id}`), no el `sessionId`, para evitar bypass por renovación de sesión. |
| Timeout y fallos de integración | Los timeouts y errores de integración devuelven respuestas sanitizadas, sin filtrar detalles internos al cliente. |
| Telemetría | Se registra longitud del prompt, hash truncado, latencia y outcome, pero nunca el prompt en texto plano. |

## Flujo esperado

1. El usuario autenticado envía `POST /transactions/interpret` con un `prompt`.
2. El backend valida longitud mínima, longitud máxima y marcadores obvios de manipulación.
3. Se verifica la cuota del usuario autenticado.
4. Si el request pasa ambas barreras, se llama al flujo de interpretación.
5. El resultado se mapea a uno de estos escenarios:
   - `200 OK` + `status: OK`
   - `200 OK` + `status: INCOMPLETE`
   - `422 assistant_out_of_scope`
   - `429 assistant_rate_limited`
   - `502 assistant_timeout`
   - `502 assistant_integration_error`

## Detalle del rate limit

### Antes

- La cuota estaba atada a la sesión HTTP.
- Si el cliente conseguía una nueva sesión, podía resetear el límite.

### Ahora

- La cuota se calcula con una clave basada en el usuario autenticado.
- Formato actual: `user:{id}`.
- Cambiar de sesión ya no reinicia la cuota del mismo usuario.

### Limitación aceptada en este MVP

- El rate limiter sigue siendo **in-memory**.
- Eso significa que el límite es por instancia del proceso y se reinicia al reiniciar la aplicación.
- No es todavía una solución distribuida o persistente.

## Señales HTTP nuevas o reforzadas

### Éxito

```json
{
  "description": "Café y pan",
  "amount": 2300,
  "category": "COMIDA",
  "status": "OK"
}
```

### Respuesta incompleta

```json
{
  "description": "Café",
  "amount": null,
  "category": null,
  "status": "INCOMPLETE"
}
```

## Verificación aplicada

- Tests focalizados para validación de prompts.
- Tests del rate limiter y rollover de ventana.
- Tests para demostrar que una nueva sesión no reinicia la cuota del mismo usuario.
- Tests del controller para `422`, `429`, `502` y headers `RateLimit-*`.
- Tests para asegurar que los logs no incluyen el prompt crudo.

## Diferido a futuro

- Rate limiting distribuido o persistente.
- Observabilidad operativa más profunda para timeouts, saturación y fallos del proveedor.
- Endurecimiento adicional de payloads malformados del proveedor.
- Revisión de contratos compartidos en otros endpoints assistant si se decide extender este patrón.

## Archivos clave

- `src/main/java/dio/budgeting/infraestructure/http/TransactionController.java`
- `src/main/java/dio/budgeting/infraestructure/ai/TransactionAssistantFacade.java`
- `src/main/java/dio/budgeting/infraestructure/ai/AssistantInputValidator.java`
- `src/main/java/dio/budgeting/infraestructure/ai/InMemoryAiInterpretRateLimiter.java`
- `src/main/java/dio/budgeting/infraestructure/http/assistant/AssistantExceptionHandler.java`
- `openspec/specs/transaction-api/spec.md`
