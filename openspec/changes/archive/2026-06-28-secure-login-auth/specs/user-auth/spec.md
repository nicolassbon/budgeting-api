# User Auth Specification

## Purpose

Define registration, login, logout, session, and auth migration behavior for budgeting-api.

## Requirements

### Requirement: Register local users

The system MUST allow email/password user registration for the MVP, MUST reject duplicate emails, and SHALL create a least-privileged account usable by the protected budgeting endpoints.

#### Scenario: Register successfully
- GIVEN an unused email and valid password
- WHEN the client submits registration
- THEN the response SHALL indicate account creation
- AND the new account SHALL be able to authenticate

#### Scenario: Reject duplicate email
- GIVEN an existing user email
- WHEN registration is submitted with that email
- THEN the response SHALL reject the request without creating a second account

### Requirement: Authenticate with a server-side session

The system MUST authenticate valid email/password credentials, MUST store passwords only as encoded values, and SHALL establish an httpOnly session cookie for subsequent requests.

#### Scenario: Login creates a session
- GIVEN a registered user with valid credentials
- WHEN the client logs in
- THEN the response SHALL authenticate the user
- AND subsequent protected requests with the session cookie SHALL be treated as authenticated

#### Scenario: Reject invalid credentials
- GIVEN an unknown email or wrong password
- WHEN the client logs in
- THEN the response SHALL deny authentication

### Requirement: Terminate authenticated sessions

The system MUST provide logout and SHALL invalidate the active session so the prior cookie no longer grants access.

#### Scenario: Logout revokes access
- GIVEN an authenticated session
- WHEN the client logs out
- THEN the session SHALL be invalidated
- AND a later protected request with that prior session SHALL be unauthenticated

### Requirement: Preserve auth migration safety

The system MUST define migrations for user records, encoded password storage, transaction ownership backfill or rollout handling, and any role data used for access decisions.

#### Scenario: Startup after migration
- GIVEN the auth schema migration is applied
- WHEN the application starts with JPA validation enabled
- THEN auth and transaction ownership tables/columns SHALL validate successfully

#### Scenario: Least-privileged role data remains compatible
- GIVEN roles are stored for local users
- WHEN a new user is created or migrated
- THEN the assigned role SHALL be least-privileged by default
