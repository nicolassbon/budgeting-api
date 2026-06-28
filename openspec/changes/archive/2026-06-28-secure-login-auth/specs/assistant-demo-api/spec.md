# Delta for Assistant Demo API

## ADDED Requirements

### Requirement: Require authenticated access for assistant demo endpoints

The system MUST require an authenticated session for `GET /api/chat-client`, `GET /api/chat-model`, `POST /api/transcribe`, and `POST /api/sinthesize` while preserving their existing paths and response contracts for authenticated users.

#### Scenario: Anonymous assistant access is rejected
- GIVEN no authenticated session
- WHEN a client calls any protected root `/api/*` assistant endpoint
- THEN the response SHALL be `401 Unauthorized`

#### Scenario: Authenticated assistant access remains compatible
- GIVEN an authenticated session
- WHEN a client calls a protected root `/api/*` assistant endpoint with a valid request
- THEN the response SHALL preserve the existing route, media type, and payload contract

### Requirement: Distinguish authn failure from authz policy

The system MUST fail missing identity as unauthenticated access and SHALL NOT introduce alternate public routes that bypass the protected assistant endpoints.

#### Scenario: Missing session stays unauthenticated
- GIVEN no valid authenticated session
- WHEN a protected root assistant endpoint is called
- THEN the failure SHALL remain `401 Unauthorized`

#### Scenario: Path compatibility is preserved under protection
- GIVEN existing clients use `/api/sinthesize` and other root assistant routes
- WHEN access policy changes to authenticated-only
- THEN those exact paths SHALL remain the supported routes
