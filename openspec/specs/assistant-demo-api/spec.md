# Assistant Demo API Specification

## Purpose

Define the stable HTTP contract for the four root `/api/*` assistant/demo endpoints and the package-boundary expectations for their controller adapters.

## Requirements

### Requirement: Chat Client endpoint compatibility

The system MUST expose `GET /api/chat-client` and bind a `prompt` request value to the endpoint response.

#### Scenario: Chat client returns generated content
- GIVEN the assistant demo API is available
- WHEN a client calls `GET /api/chat-client` with a `prompt` value
- THEN the response SHALL be HTTP 200 with generated text content

#### Scenario: Chat client path remains stable after relocation
- GIVEN the controllers move behind an assistant package boundary
- WHEN a client calls `GET /api/chat-client`
- THEN the route and query-style request contract MUST remain unchanged

### Requirement: Chat Model endpoint compatibility

The system MUST expose `GET /api/chat-model` and bind a `prompt` request value to the endpoint response.

#### Scenario: Chat model returns generated content
- GIVEN the assistant demo API is available
- WHEN a client calls `GET /api/chat-model` with a `prompt` value
- THEN the response SHALL be HTTP 200 with generated text content

#### Scenario: Chat model remains independent from transaction AI flow
- GIVEN `/transactions/ai` exists separately
- WHEN the assistant demo controllers are consolidated
- THEN `GET /api/chat-model` MUST remain a root `/api/*` endpoint and SHALL NOT move under `/transactions`

### Requirement: Audio transcription endpoint compatibility

The system MUST expose `POST /api/transcribe`, consume `multipart/form-data`, and read the uploaded part named `file`.

#### Scenario: Transcription accepts an uploaded file
- GIVEN a multipart request containing a `file` part
- WHEN a client calls `POST /api/transcribe`
- THEN the response SHALL be HTTP 200 with transcribed text content

#### Scenario: Transcription media contract remains stable
- GIVEN the controllers move behind an assistant package boundary
- WHEN a client calls `POST /api/transcribe`
- THEN the endpoint MUST continue consuming `multipart/form-data`

### Requirement: Text-to-speech compatibility endpoint

The system MUST expose `POST /api/sinthesize` as the supported compatibility route, accept a JSON body with `text`, and produce `audio/mp3` with a downloadable `audio.mp3` payload.

#### Scenario: Compatibility text-to-speech returns mp3 audio
- GIVEN a request body containing `text`
- WHEN a client calls `POST /api/sinthesize`
- THEN the response SHALL be HTTP 200, produce `audio/mp3`, and include downloadable audio content

#### Scenario: Misspelled compatibility route is preserved
- GIVEN controller code is relocated behind an assistant boundary
- WHEN clients continue calling `POST /api/sinthesize`
- THEN that exact misspelled path MUST remain available

### Requirement: Assistant package boundary preservation

The system MUST keep these four root `/api/*` controllers inside an assistant-specific package boundary that remains discoverable from the application root package. The consolidation SHALL NOT change `/transactions/ai`, the prompt resource path, or the `infraestructure` package spelling.

#### Scenario: Root assistant endpoints remain discoverable
- GIVEN the application starts after controller relocation
- WHEN Spring scans from the application root package
- THEN all four assistant/demo endpoints MUST still be registered

#### Scenario: Out-of-scope AI orchestration stays unchanged
- GIVEN transaction AI orchestration is outside this slice
- WHEN assistant consolidation is implemented
- THEN `/transactions/ai` and `src/main/resources/prompts/system-message.st` SHALL remain unchanged

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
