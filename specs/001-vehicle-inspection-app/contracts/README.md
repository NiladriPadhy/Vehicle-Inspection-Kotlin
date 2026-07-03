# Contracts

**Feature**: `001-vehicle-inspection-app`

This folder documents the contracts the app depends on. Because this is a mobile
application (not a public web API), "contracts" means:

1. **Domain ports** — repository interfaces the presentation/domain layers depend on, and
   which the data layer implements (`repositories.md`).
2. **External service contracts** — the request/response shapes and validation rules for
   Gemini Vision (`ai-gemini-contract.md`) and VIN decode (`vin-decode-contract.md`).
3. **Backend data contracts** — Firestore document shapes (`firestore-contract.md`) and
   Storage paths (`storage-contract.md`).
4. **Report contract** — canonical report JSON schema (`report-json-schema.json`).

All Kotlin interface signatures below are illustrative of the intended contract and will be
finalized during implementation. Every AI response MUST pass validation before use
(Constitution Principle VIII).
