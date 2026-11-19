# Changelog

All notable changes to TallyBook are documented here.

## v0.1.3-beta.1 - 2026-11-19

### Added

- A redesigned transaction entry flow that groups note, amount and date into one primary card.
- Payment-method cards for WeChat, Alipay, bank cards, cash and custom accounts, with method icons and everyday-use ordering.
- New installations now include a zero-balance WeChat Pay account alongside cash, Alipay and bank card.

### Changed

- Category selection now follows payment-method selection and is labelled "账目分类".
- Transfers use the same payment-method language for the source and destination accounts.

## v0.1.2-beta.1 - 2026-11-16

### Added

- Ledger multi-select mode with select-all, atomic bulk soft delete and one-action batch undo.
- Hierarchical category display in category management and the budget editor.
- Create a new top-level category or child category inside the budget flow; the new category is selected and its budget name is filled automatically.
- Parent and child category budgets show whether descendant spending is included.

### Changed

- Category management now displays the parent/child tree and supports creating or moving categories between levels.
- Long-pressing a ledger row enters multi-select instead of deleting immediately, reducing accidental deletion.
- Budget progress carries its category scope so the budget screen can identify subtree budgets.

### Fixed

- A single delete undo request is no longer cleared before the restore coroutine reads its transaction ids.
- The ledger now has a single scroll owner, fixing repeated up/down jumps when dragging down from the summary or chart area.

## v0.1.1-beta.1 - 2026-11-14

### Added

- Persistent theme selection with system, light and dark modes.
- Two app-wide interface styles: simple and flat.
- Appearance controls in Settings with immediate preview.
- WeChat Pay XLSX bill import with preview, deterministic de-duplication and automatic category mapping.
- Refund matching against original refunded expenses, with unmatched refunds reported and skipped safely.
- Native XLSX parsing with ZIP/XML size limits and no third-party file parser dependency.

### Changed

- Ledger summary, transaction, search, budget, chart, category and recycle-bin cards now share a coherent visual hierarchy without borders or drop shadows.
- Settings uses category pages instead of one long vertically stacked settings screen.
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
