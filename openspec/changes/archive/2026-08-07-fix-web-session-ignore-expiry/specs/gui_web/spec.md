# Web GUI Delta

## ADDED Requirements

### Requirement: Ignore rule lifetimes

Ignore kinds SHALL have distinct, observable lifetimes:

- **Session** ignores SHALL expire when a draft patch is finalized into a patch and when a draft is stashed for amend. They SHALL NOT expire on recomposition or amend finalization (history surgery does not end the author's patch-building session), and SHALL NOT expire on server restart.
- **Value** ignores SHALL be pruned automatically when the ignored option's live value no longer equals the recorded target value.
- **Permanent** ignores SHALL persist until explicitly removed.
- **Directory** ignores SHALL be stored as ignored paths in `moc.json` (not in the ignore store) and persist until explicitly removed.
- **Recomposition-session** ignores SHALL be cleared when a recomposition/amend session starts, is cancelled, or is finalized.

When session ignores expire, the server SHALL broadcast `ignores_changed` so all connected clients refetch and previously hidden changes resurface.

#### Scenario: Session ignores expire on patch finalize
- **WHEN** a draft containing staged entries is finalized into a patch while session ignores exist
- **THEN** all session ignores are removed and `ignores_changed` is broadcast

#### Scenario: Session ignores expire on amend stash
- **WHEN** a draft is stashed for amend via `POST /api/draft/finalize-for-amend` while session ignores exist
- **THEN** all session ignores are removed and `ignores_changed` is broadcast

#### Scenario: Session ignores survive recomposition finalize
- **WHEN** a recomposition or amend session is finalized while session ignores exist
- **THEN** the session ignores are preserved (only recomposition-session ignores are cleared)

#### Scenario: Session ignores survive restart
- **WHEN** the server restarts with unexpired session ignores
- **THEN** they are still in effect (they are not tied to the process lifetime)

#### Scenario: Value ignore self-prunes
- **WHEN** a diff endpoint is served and a value ignore's recorded target no longer matches the option's live value
- **THEN** the stale value ignore is removed and the change becomes visible
