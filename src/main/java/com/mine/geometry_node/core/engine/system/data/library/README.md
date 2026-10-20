# Data Library metadata

`DataLibraryEntry` stores independent `value`, `tags`, and `data` fields. `data` is the
custom metadata dictionary, not vanilla NBT, item components, or the stored entry value.
Old files without `data` read as an empty dictionary. New data uses the registered graph
NBT codec, stored as a canonical SNBT string in the JSON data field. This preserves typed nested
values, Double precision, 64-bit integers, and registered read-only values without a port JSON codec. Validation and detached
snapshots use `DataDictionary` and `GraphMetadataValuePolicy` limits.

Keep all three fields when replacing/copying/renaming/moving an entry. The retained five/six
argument constructors are for genuinely new entries and legacy callers; they create empty data.
The entry fingerprint includes data with dictionary keys sorted recursively. Tags and data
share the same CAS boundary, so concurrent changes cannot overwrite each other.

`DataLibraryMetadata.EntryTarget` exposes both TAG and DATA capabilities on the server.
`DataLibraryFileStore.updateDataAtomic` rechecks authority and the expected entry fingerprint
under the same per-file transaction lock as tag and entry writes. Reentrant transactions fail.

Client UI must use `DataLibraryRepository.replaceData(expected, replacement, completion)`.
The completion receives an empty string after authoritative save/refresh succeeds, or a
translation key on failure; `ClientDataLibraryRepository` actively fails pending completions
on disconnect. Synchronous invalid/stale input can throw. Read pending state using
`isUpdatePending` and errors using `dataUpdateMessage`. There is no separate network protocol:
metadata edits reuse normal asynchronous entry update transfers and snapshot refreshes.
