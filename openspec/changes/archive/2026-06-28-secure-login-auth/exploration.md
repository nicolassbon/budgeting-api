## Exploration: secure login auth

### Current State
The app is effectively public today: there is no Spring Security dependency, no authentication/authorization layer, and no user concept in persistence. The exposed surface includes manual transaction CRUD plus AI/audio endpoints that can trigger OpenAI calls and file handling without server-side identity checks.

### Affected Areas
- `src/main/java/dio/budgeting/assistant/*` — public chat/transcription/TTS endpoints and shared AI exception handling.
- `src/main/java/dio/budgeting/infraestructure/http/TransactionController.java` — `/transactions`, `/transactions/ai`, and `/transactions/interpret` currently accept unauthenticated requests.
- `src/main/java/dio/budgeting/application/TransactionService.java` and `src/main/java/dio/budgeting/domain/*` — ownership will need to be enforced at the service/domain boundary.
- `src/main/resources/db/migration/V1__Initial_schema.sql` — schema currently has only transactions; users/roles/ownership require new migrations.
- `build.gradle` — missing Spring Security, auth persistence, and token/session support dependencies.
- `src/test/java/dio/budgeting/*` — current tests only characterize open endpoints; auth-focused slice tests are missing.

### Approaches
1. **Browser-first session auth with httpOnly cookie** — email/password login, server-side session, and CSRF protection for browser use.
   - Pros: simplest secure default for a web app, revocable sessions, no token storage in JS, good fit if the client is browser-based.
   - Cons: less convenient for non-browser clients, adds CSRF/session lifecycle work.
   - Effort: Medium

2. **JWT access token auth** — email/password login issuing short-lived bearer tokens, with refresh token later if needed.
   - Pros: stateless API shape, easier for mobile/third-party clients, cleaner for future Google login/OAuth2.
   - Cons: token revocation and refresh logic add complexity, bearer tokens are easier to misuse in browsers.
   - Effort: Medium/High

3. **Hybrid: session auth now, OAuth2 later** — ship email/password with cookie sessions first, then add Google via OAuth2 login if needed.
   - Pros: matches current MVP scope, minimizes attack surface, keeps optional Google login isolated.
   - Cons: later migration work if the frontend becomes non-browser heavy.
   - Effort: Medium

### Recommendation
Use **email/password first with server-side sessions and httpOnly cookies** for the initial security layer. It best matches the current backend-only project stage, keeps browser security sane, and avoids premature token complexity. Add Google login later via OAuth2/OpenID Connect only after the core user/ownership model is stable.

### Risks
- Every transaction query/mutation must enforce ownership server-side; otherwise IDOR/BOLA appears immediately.
- `POST /transactions/ai`, `/transactions/interpret`, `/api/transcribe`, `/api/sinthesize`, `/api/chat-client`, and `/api/chat-model` are all abuse-prone cost/availability endpoints and should be authenticated or disabled in production.
- Persistence changes will need Flyway migrations for users, roles, and transaction ownership; startup will fail with `ddl-auto=validate` if schema lags.
- Spring Security can change request matching and error handling for multipart and AI endpoints if filters are not ordered carefully.

### Ready for Proposal
Yes — but this should likely be split into at least two implementation slices: **auth foundation** (users, sessions, login, protected CRUD) and **AI hardening** (restrict or scope AI/audio endpoints, add ownership-aware authorization, and optional Google later).
