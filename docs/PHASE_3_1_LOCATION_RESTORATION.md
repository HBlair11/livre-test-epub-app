# Phase 3.1 — Logical Location Restoration

## Goal
Restore the same reading *passage* on cold open (including after typography changes),
not merely the same spine index + scroll ratio.

## Changes
1. **Schema** (`books.location_quote`, migration 16 → 17)
   - Nullable TEXT. Legacy rows remain null and keep the ratio fallback.
2. **Persistence**
   - When progress is saved, also capture `Caesura.pageSnippet(currentPage)`
     (trimmed to 240 chars) into `location_quote`.
3. **Restore**
   - On chapter load after open, if `location_quote` is present, call
     `pageForTextAnchor(quote, fallbackPage)` before falling back to ratio.
4. **Pure helper** `LocationRestore` + unit tests for the decision logic.

## Acceptance
- Reopen after font-size change lands on a page containing the saved quote when possible.
- Progress % may change; the logical place does not jump arbitrarily.
- Migration is non-destructive.
- Existing unit tests + new `LocationRestoreTest` pass.

## Out of scope
- Selection / highlight re-anchoring (Phase 3.3–3.4)
- TTS lifecycle (Phase 3.6)
