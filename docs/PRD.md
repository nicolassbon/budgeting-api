## Product Requirements Document — Budgeting MVP

### Overview

Budgeting is an intelligent expense tracking product for busy people who want to reduce the friction of recording daily spending and quickly understand where their money goes.

This target MVP is designed for a technical challenge and should demonstrate a clear, end-to-end product experience: user authentication, AI-assisted expense capture, manual expense management, and a dashboard with actionable visibility.

The backend foundation already exists. The main goal of this MVP is to turn that foundation into a credible product demo with strong business value and a visible frontend experience.

This document describes the **target scope** for the challenge MVP. It is intentionally product-oriented and may include planned capabilities that are not fully implemented yet in the current backend state.

### Problem Statement

Many people want to track expenses, but the day-to-day effort of manually logging each purchase creates friction. When life gets busy, expenses are forgotten, recorded late, or never analyzed.

Users need a faster way to register expenses and a simple way to understand their spending behavior without maintaining a complex spreadsheet or traditional finance app workflow.

### Target User

The primary user is a busy individual who:

- has many daily responsibilities,
- wants to save time when recording expenses,
- needs a quick view of monthly spending,
- wants lightweight guidance on how to improve spending habits.

### Product Goal

Help users capture expenses with minimal effort and turn those records into quick financial visibility.

### Core Value Proposition

Users can log expenses through AI-assisted flows such as chat, text, or voice, validate the interpreted result before saving, and immediately see the impact in their history and dashboard.

### MVP Scope

#### Included in Target MVP

1. **User authentication**
   - Email and password login.
   - Auth is required for the MVP.

2. **Intelligent expense capture**
   - User can submit an expense through text.
   - User can submit an expense through voice.
   - AI interprets amount, category, description, and relevant metadata.
   - The interpreted expense must be shown in a confirmation state before persistence.

3. **Confirmation before save**
   - The user reviews the extracted expense details.
   - The user confirms or rejects/corrects before the record is saved.
   - This is mandatory because AI may misinterpret voice or natural-language input.

4. **Manual expense management**
   - User can create an expense manually without AI.
   - User can edit a previously saved expense.

5. **Dashboard**
   - Monthly spending summary.
   - Spending grouped by category.
   - Recent expenses.
   - Simple AI insight cards embedded in the dashboard.

6. **Expense history**
   - List of saved expenses.
   - Basic filtering or browsing by category and/or date period.
   - Clear path to edit an existing expense.

### Screens / Product Experience

The target MVP should be centered on three main screens:

#### 1. Dashboard
- Quick financial overview.
- Total spent in the current month.
- Breakdown by category.
- Recent transactions.
- Embedded insight cards with simple recommendations or observations.

#### 2. Capture / Chat
- Main AI interaction surface.
- Text input for expense capture.
- Voice input for expense capture.
- AI interpretation result preview.
- Confirmation step before persistence.

#### 3. History / Transactions
- Saved expenses list.
- Manual creation flow.
- Manual editing flow.
- Filters or navigation to inspect past records.

### Business Rules

- AI-generated expense data must not be persisted automatically without user confirmation.
- Manual entry must remain available even if the AI flow fails or is not used.
- Insights should support the dashboard experience, not become a separate product module in the MVP.
- Authentication with email/password is mandatory for MVP completeness.
- Social login with Google is optional and should only be included if it does not endanger the core scope.

### Out of Scope for This MVP

- Full multi-provider social login as a core requirement.
- Complex budgeting plans or savings goals.
- Bank account synchronization.
- Household or multi-user collaboration flows.
- Advanced analytics module separated from the dashboard.
- Receipt photo ingestion as a required feature.

### Optional / Nice to Have

- Google login.
- Expense capture from receipt photos.
- More advanced AI recommendations.
- Better trend comparisons across months.

### Success Criteria

The target MVP is successful if:

- a user can register and log in,
- a user can submit an expense through text or voice,
- the AI-generated interpretation is shown for confirmation before saving,
- the confirmed expense appears in history and dashboard,
- the user can also create and edit expenses manually,
- the dashboard communicates useful spending visibility at a glance,
- the product feels like a coherent demo rather than disconnected backend features.

### Why This MVP Matters

This scope balances product credibility, technical feasibility, and demo clarity for a one-week challenge. It highlights AI orchestration as a practical accelerator, while still showing sound product judgment, error handling, business thinking, and a user-facing experience.
