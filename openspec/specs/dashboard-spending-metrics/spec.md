# Dashboard Spending Metrics Specification

## Purpose

Define owner-scoped spending summary for the Inicio view.

## Requirements

### Requirement: Current-user dashboard summary

The system MUST expose an authenticated `GET /dashboard/spending` endpoint returning current-period expense totals and category breakdown for the authenticated user only.

#### Scenario: Summary with transactions

- GIVEN an authenticated user has transactions dated in the current period
- WHEN the client calls `GET /dashboard/spending`
- THEN the response status SHALL be `200 OK`
- AND the body SHALL include total expenses, period bounds, and category-level totals for that user only

#### Scenario: Empty summary

- GIVEN an authenticated user has no transactions in the current period
- WHEN the client calls `GET /dashboard/spending`
- THEN the response status SHALL be `200 OK`
- AND the body SHALL report zero totals and an empty category breakdown

### Requirement: Owner isolation in dashboard aggregates

The system MUST scope every dashboard aggregate to the authenticated user.

#### Scenario: Summary excludes other users

- GIVEN two users have transactions in the same period and category
- WHEN one user calls `GET /dashboard/spending`
- THEN totals SHALL include only that user's transactions

### Requirement: Reject anonymous dashboard access

The system MUST require authentication for `GET /dashboard/spending`.

#### Scenario: Anonymous request rejected

- GIVEN no authenticated session
- WHEN the client calls `GET /dashboard/spending`
- THEN the response SHALL be `401 Unauthorized`

### Requirement: Exclude savings and goal metrics

The system MUST NOT include savings, goals, or `AHORRO` semantics in dashboard spending metrics.

#### Scenario: Summary covers expenses only

- GIVEN transactions exist only in expense categories
- WHEN the dashboard summary is computed
- THEN the response SHALL contain expense totals only
- AND no savings or goal fields SHALL appear

### Requirement: Period-aware summary

The system MUST compute dashboard totals using persisted transaction dates.

#### Scenario: Period grouping uses transaction date

- GIVEN a transaction dated in the current period and another dated outside it
- WHEN the current-period summary is requested
- THEN only the in-period transaction SHALL contribute to totals
