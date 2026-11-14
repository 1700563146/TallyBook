# Changelog

All notable changes to TallyBook are documented here.

## v0.1.1-beta.1 - 2026-11-14

### Added

- Persistent theme selection with system, light and dark modes.
- Three app-wide interface styles: glass, simple and flat.
- Glass background atmosphere, translucent cards, style-aware borders, shapes, elevation and navigation.
- Appearance controls in Settings with immediate preview.

### Changed

- Ledger summary, transaction, search, budget, chart, category and recycle-bin cards now share a coherent visual hierarchy.
- Settings is reorganized into grouped panels with clearer action emphasis.
- Splash window background follows the system light/dark appearance.

## v0.1.0-beta.1 - 2026-11-14

### Added

- Cross-month ledger search with paged results and debounced text matching.
- Filters for all/expense/income/transfer, account and category.
- Version 2 JSON backups with archived categories, sort metadata, transaction tags and timestamps.
- Backup import validation for references, duplicate IDs, currency and record invariants.
- GitHub Actions quality gate and Room v1 to v2 migration test.

### Changed

- Home budget summaries show the most at-risk budget instead of adding overlapping limits.
- Historical category breakdowns keep the names of archived categories.
- Release builds read signing credentials from environment variables.

### Fixed

- Restoring a backup containing an archived category no longer fails or loses its category state.
- Oversized imported or typed amounts are rejected instead of overflowing or wrapping.
- Notifications now re-check permission immediately before posting.
- LIKE wildcard characters are treated literally in search.

### Known limitations

- Timezone changes can still move historical transactions between report buckets.
- The database is not encrypted and the app does not yet provide a biometric app lock.
- CSV import, account reconciliation, recurring transactions and multi-ledger support are not included.
