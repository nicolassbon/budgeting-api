# Tasks: Password Reset Link

## Implementation

- [x] Add public forgot-password endpoint with generic `202 Accepted` response.
- [x] Add public reset-password endpoint with token and new password handling.
- [x] Store short-lived password reset tokens as hashes and mark them single-use.
- [x] Integrate password reset email delivery through configurable Resend settings.
- [x] Add the minimal Flyway schema migration for reset tokens.
- [x] Add focused tests for externally visible behavior and core failure paths.

## Review Workload Forecast

- 400-line budget risk: Medium
- Chained PRs recommended: No
- Decision needed before apply: No
- Chain strategy: pending
