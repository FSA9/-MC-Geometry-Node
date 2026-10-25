# World factions

`model` contains immutable definitions, the world catalogue, instance profiles and validation limits. It has no UI, network, storage or scoreboard dependencies. `storage` owns the bounded versioned JSON representation and atomic file replacement. `service` owns catalogue commands, server lifetime and entity attachment access; `ServerFactionRequests` is the remote permission and publication boundary.

## Public contract

- `ServerFactionService.INSTANCE.snapshot(server)` reads the immutable authoritative catalogue on the server thread.
- `execute(server, expectedRevision, operation, payload)` validates one complete command and writes `<world>/data/geometry_node/factions.json` before publishing the new snapshot. A stale revision throws `IllegalStateException("conflict")`; invalid input throws `IllegalArgumentException`; disk or malformed persisted data throws `IOException`. No-op commands do not advance the revision. `READ` accepts an empty payload and ignores the expected revision.
- `FactionJsonCodec.encode/decode` share the on-disk and wire representation. Version 1 bounds the entire UTF-8 catalogue to 768 KiB so every committed catalogue remains transportable.
- `EntityFactions` adapts the immutable `EntityFactionProfile` to a separately serialized NeoForge attachment. The entity editor exposes the section `factions: {memberships: {uuid: rank}, relations: {uuid: score}}`; validation and reversible replacement participate in its existing permission, loaded-identity, conflict and rollback checks. `replace` is the trusted commit/rollback primitive; callers validate proposed writes first.

| Operation | Payload |
| --- | --- |
| CREATE | `name`, optional `description` |
| UPDATE | `id`, optional `name` and `description` |
| DELETE | `ids` (UUID list, atomic) |
| RELATION | `source`, `target`, `score` |
| RANK | `id`, `level`, `name`, optional `previous` |
| REMOVE_RANK | `id`, `level` |

Names and labels use `RichTextValue`, accepting its canonical map at the boundary. They contain explicit styled text, never HTML or application-managed translation keys. `RANK` without `previous` registers a new integer and rejects duplicates; editing or renumbering an existing entry supplies `previous`. UUID objects and canonical UUID strings are accepted by command parsing; JSON stores strings. Rename and rank changes preserve faction identity.

Faction name uniqueness is authoritative at `CREATE` and `UPDATE` with a changed name key. The comparison key is `name.plain().strip().toLowerCase(Locale.ROOT)`: styles and segment boundaries do not participate; internal whitespace and Unicode normalization forms remain distinct. The authored name is preserved. A collision throws `IllegalArgumentException` (remote reason `duplicate_name`) before storage or revision changes. Updating the same UUID without changing its key may change formatting, case or surrounding whitespace and an exact no-op preserves the revision. Rank labels and descriptions are outside this uniqueness rule.

Legacy catalogues may contain repeated default names such as `新阵营`. Constructors, snapshots and JSON reads retain these names without rejecting the document or silently renaming entries. Description, relation, rank and deletion commands remain available; formatting changes that retain an existing name key also remain available. Creating another duplicate or renaming to another faction's key is rejected, while renaming a legacy duplicate to an available key resolves it normally. Default-name numbering belongs to the UI.

## Semantics and ownership

Scores are independent directed integers -100 through 100, including self-relations. Missing scores read as zero and zero is omitted from storage. No automatic symmetry, inheritance, friendly self-score, AI or combat behavior is introduced. Entity relationships are independent of both membership and faction relationships.

Each entity may belong to multiple factions, each with its own signed 32-bit rank. Rank zero still means membership. Rank labels are optional interpretations of numeric ranks: changing/removing them never rewrites instance ranks. All maps and snapshots are immutable.

Deleting factions removes the definitions and incoming faction relationships in one commit. It never loads chunks or scans persisted entities. Entity memberships and scores referencing deleted factions remain inert stale references: unchanged entries may survive unrelated edits or be removed; adding or changing a stale entry is rejected. New factions receive new server-generated UUIDs. Profile reads and writes use entity attachments, not vanilla scoreboard teams or tags. Unloaded/dead edit targets are rejected by the existing entity editor before mutation.

The unified cache entry `geometry_node:faction.server.snapshot` owns only the derived encoded snapshot. Clearing it never deletes faction data or resets revisions. The authoritative catalogue is not a clearable cache. The registration closes when its owning server stops.
