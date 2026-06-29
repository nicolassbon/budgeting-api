# Decisión arquitectónica — MVP Budgeting

## Decisión

Para el MVP de Budgeting vamos a usar una **arquitectura por capas pragmática con límites limpios**.

Esto significa que el proyecto se organiza por responsabilidades claras —HTTP, aplicación, dominio e infraestructura— sin adoptar una arquitectura Hexagonal estricta ni una Clean Architecture completa. La prioridad del MVP es entregar una demo coherente, mantenible y fácil de revisar, sin introducir abstracciones que todavía no justifican su costo.

## Contexto del producto

El PRD define un MVP para un desafío técnico de alcance acotado. El producto debe demostrar, de punta a punta:

- autenticación con email y contraseña;
- captura de gastos por texto y voz;
- interpretación asistida por IA;
- confirmación antes de persistir datos interpretados por IA;
- gestión manual de gastos, con creación disponible y edición prevista como parte del alcance objetivo del MVP;
- historial y dashboard con visibilidad simple.

El objetivo no es construir una plataforma financiera extensible desde el día uno. El objetivo es mostrar un producto creíble, con buen criterio técnico y una experiencia completa.

## Por qué Layered Architecture

La arquitectura por capas encaja bien con el tamaño y la presión del MVP porque mantiene una estructura simple:

| Capa | Responsabilidad |
|------|-----------------|
| HTTP / Controllers | Recibir requests, validar entrada básica, traducir responses. |
| Application Services | Orquestar casos de uso: usuario actual, reglas de flujo, persistencia, IA y dashboard. |
| Domain | Representar conceptos del negocio y reglas importantes. |
| Infrastructure | Implementar detalles técnicos: JPA, Spring Security, Spring AI, Flyway y configuración. |

Esta separación alcanza para que el código sea entendible y testeable sin convertir cada operación en una colección de puertos, adapters, presenters e interactors.

## Por qué no Hexagonal estricta

Hexagonal es útil cuando el dominio necesita aislarse fuertemente de muchas entradas y salidas: colas, CLIs, webhooks, múltiples proveedores, storage externo, APIs de terceros intercambiables, etc.

Este MVP tiene una forma mucho más directa:

- una API REST;
- PostgreSQL;
- integración con Spring AI/OpenAI;
- autenticación básica;
- una experiencia de frontend centrada en dashboard, captura e historial.

Forzar Hexagonal ahora agregaría indirection antes de tener complejidad real. Eso aumentaría el costo de implementación y revisión sin mejorar proporcionalmente el producto.

## Por qué no Clean Architecture completa

Clean Architecture aporta buenas ideas, pero aplicada de forma estricta puede derivar en demasiada ceremonia para este contexto:

- interfaces creadas “por si acaso”;
- DTOs duplicados sin necesidad clara;
- interactors para operaciones simples;
- presenters y boundaries que no aportan al flujo actual;
- más archivos para revisar sin más valor de negocio.

Para este MVP tomamos las ideas útiles —límites claros, controllers delgados, dominio protegido de detalles técnicos— sin adoptar todo el paquete como dogma.

## Reglas prácticas del proyecto

Estas reglas guían el trabajo diario:

1. **Los controllers no contienen lógica de negocio.**
   Deben delegar en servicios de aplicación.

2. **Los services de aplicación orquestan casos de uso.**
   Pueden coordinar repositorios, usuario autenticado, IA y respuestas del flujo.

3. **El dominio conserva conceptos y reglas importantes.**
   Ejemplos: transacciones, categorías, usuario propietario y reglas que no deberían depender de HTTP/JPA.

4. **La infraestructura contiene detalles técnicos.**
   JPA, entidades de persistencia, Spring Security, Spring AI, Flyway y configuración viven fuera del dominio.

5. **La IA no debe debilitar reglas de negocio.**
   El PRD exige que los datos interpretados por IA no se persistan sin confirmación del usuario en el flujo de captura confirmado.

6. **La entrada manual sigue siendo obligatoria.**
   El usuario debe poder registrar gastos aunque la IA falle o no se use. La edición manual pertenece al alcance objetivo del MVP, pero debe agregarse con un cambio explícito de backend y sus pruebas.

7. **No se renombra `infraestructure` dentro de esta decisión.**
   El paquete está mal escrito, pero cambiarlo es un refactor separado porque toca compatibilidad, imports y revisión.

## Consecuencias esperadas

Esta decisión favorece:

- menor carga cognitiva para nuevos contribuidores;
- menor riesgo de sobreingeniería;
- cambios más chicos y revisables;
- buena alineación con Spring Boot;
- espacio para evolucionar si el producto crece.

Si más adelante aparecen múltiples canales de entrada, proveedores intercambiables o reglas de dominio más complejas, se puede reconsiderar una separación más estricta. Por ahora, esa complejidad sería prematura.

## Resumen

La arquitectura elegida no es “menos arquitectura”. Es una decisión proporcional al producto.

Para este MVP, una **Layered Architecture pragmática con límites limpios** nos da suficiente orden sin bloquear velocidad. La disciplina está en respetar responsabilidades, no en multiplicar abstracciones.
