# Architecture Guidance Specification

## Purpose

Define the official MVP architecture language, layer responsibilities, and scope constraints for budgeting-api.

## Requirements

### Requirement: Name the MVP architecture as pragmatic Layered Architecture

The system documentation MUST describe the official MVP architecture as pragmatic Layered Architecture with clean boundaries, and MUST NOT present strict Hexagonal Architecture or full Clean Architecture as the adopted MVP style.

#### Scenario: Official architecture wording is aligned
- GIVEN contributor-facing architecture documentation is updated
- WHEN a reviewer checks the stated architecture style
- THEN the documentation SHALL name pragmatic Layered Architecture as the official MVP architecture

#### Scenario: Non-goal wording is explicit
- GIVEN documentation references alternative architecture styles
- WHEN the MVP scope is described
- THEN strict Hexagonal and full Clean Architecture SHALL be stated as out of scope for this change

### Requirement: Define responsibilities for each layer boundary

The system documentation MUST define that controllers handle HTTP and transport concerns, application services coordinate use cases and transactions, domain models hold business rules, and infrastructure owns persistence, security, AI, and framework integration details. Within the current package layout, `assistant` and `config` SHALL be documented as infrastructure-owned edges rather than domain or application centers.

#### Scenario: Contributors can place transport logic correctly
- GIVEN a contributor adds or reviews an endpoint change
- WHEN they consult the architecture guidance
- THEN controllers SHALL be identified as thin adapters rather than business-rule owners

#### Scenario: Contributors can place integration logic correctly
- GIVEN a contributor adds persistence, security, or AI wiring
- WHEN they consult the architecture guidance
- THEN that work SHALL be directed to infrastructure-facing boundaries instead of domain models

### Requirement: Preserve confirmation before AI persistence

The system architecture guidance MUST preserve the PRD rule that AI-interpreted expense data SHALL NOT be persisted without user confirmation, even when AI flows are refactored or documented under the layered model.

#### Scenario: Interpretation stays non-persistent before confirmation
- GIVEN an AI flow extracts expense details from text or voice
- WHEN the interpretation result is produced
- THEN the architecture guidance SHALL require a confirmation state before any persistence step

#### Scenario: Refactoring does not weaken the business rule
- GIVEN a future contributor reorganizes AI-related components
- WHEN they apply this guidance
- THEN they MUST preserve confirmation-before-save as a mandatory business rule

### Requirement: Keep manual entry available independent of AI

The system architecture guidance MUST preserve manual expense entry as an MVP path that remains available even if AI flows fail, are degraded, or are not used.

#### Scenario: AI failure does not remove manual capture
- GIVEN an AI-dependent capture flow is unavailable or errors
- WHEN a user still needs to record an expense
- THEN manual transaction entry SHALL remain a supported MVP flow

#### Scenario: Architecture docs keep manual capture in scope
- GIVEN contributors review supported MVP flows
- WHEN they read the guidance
- THEN manual creation SHALL remain listed independently from AI capture, and manual editing SHALL be treated as target MVP scope that requires an explicit backend implementation change

### Requirement: Prefer MVP pragmatism over unnecessary abstraction

The system documentation SHOULD favor direct layered boundaries that support the challenge MVP and SHALL avoid introducing extra abstraction solely to imitate stricter architectural patterns.

#### Scenario: Proposed changes are evaluated against MVP needs
- GIVEN a contributor proposes new seams, ports, or indirection
- WHEN the change is reviewed against this guidance
- THEN the added abstraction SHALL require a clear MVP benefit rather than architectural purity alone

#### Scenario: Existing compatibility constraints remain visible
- GIVEN contributors review architecture constraints
- WHEN package and API compatibility are documented
- THEN the `infraestructure` spelling and current endpoint compatibility SHALL remain explicit constraints
