# Global data UI

`GlobalDataView` owns category navigation, factories and section lifecycle only. Its `Section`
descriptors create implementations of `GlobalDataSection`; adding a category does not change faction code.
`FactionCatalogView` owns faction search, batch checks/deletion and the list/detail split.
`FactionEditorView` owns retained forms for one stable faction UUID; `FactionRanksView` only interprets rank labels.
Repository snapshots and commands remain in `client/faction`, never in `UiStateContext`.

Entities are the first navigation category and the initial page. `EntityCatalogView` hosts
`WorldObjectsView.Mode.ENTITIES` and the shared `DatabaseDetailsPanel`; world objects use
`Mode.WORLD`. Both modes share the same table, preview cells, metadata controls and theme.
Queries set `WorldObjectEntityFilter.Scope` before the server result limit. Entity filters,
selection and column widths use independent `globaldata:entities.*` state keys.
`EntityRegistrationView` shares asynchronous read/write controls between directory cells and
the editor's General category. Registration never enters the entity attributes draft.
Unloaded entries retain UUID/dimension previews and read-only metadata; editing additionally
requires a loaded, alive entity. A completed metadata read is identified by `fetched()`, not
by whether the source permits editing.

Shared integration controls are in `components/integration/faction`:

- `FactionMembershipsView` composes the same `UiTableColumns`, `UiTableRow`, `UiTableViewport`,
  `UiTableFilterHeader` and `UiColumnFilterPopover` as relations. It owns name, membership and integer
  range filters, registered-rank menus and join/leave row actions; its owner supplies data and writes.
  Entity factions have a separate top-level page, outside `EntityAttributesView`.
- `FactionRelationsView(Context, UiTheme, Supplier<FactionCatalog>, Supplier<Map<UUID,Integer>>, BiConsumer<UUID,Integer>)`
  exposes `refresh()`, `setEditable(BooleanSupplier)`, `bindState(UiStateContext,String)`, `suspend()`, and `dispose()`.
  It displays a directed matrix row, including a faction's relation to itself, with missing scores read as zero.
  Name and inclusive integer score filters combine with AND; header clicks cycle original/ascending/descending.
- `FactionTextViews.display(RichTextValue)` delegates to the existing formatted-text renderer.
- `FactionRichField` displays formatted text and opens `ExpandedTextInputOverlay`.
- `FactionIntegerInput` confirms on Enter or blur, restores on Escape, preserves incomplete input across
  refreshes, and exposes `hasPendingChanges()`, `discard()`, `dispose()`.
- `FactionCatalogSubscription(View, ClientFactionRepository, Runnable)` coalesces notifications on the UI
  owner. `open()` refreshes the shared connection catalog; `close()` detaches presentation only.

The database tab is `DatabaseViewState.Page.GLOBAL_DATA`. Layout extents are persistent area preferences;
selection, search and batch checks are memory state scoped by connection/player. Incomplete controls are
retained only within the live view, not saved to disk. Changing connection destroys the old forms and
rejects obsolete completion callbacks. Hiding the page never cancels a submitted repository write.

Global category navigation shares `UiNavigationRow` with world objects. Database and faction page tabs
share `UiSubTabs`; the faction detail surface uses the same sidebar theme token as database properties.
Faction UUIDs remain internal references, with no ID labels, tooltips or name-search matching.
`FactionRichField.Appearance.FORM` frames name/description previews; `INLINE` serves rank-table cells.
