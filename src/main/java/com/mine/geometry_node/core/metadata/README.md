# Tag / Data capability kernel

This package is common Java code. It does not know about Minecraft entities, dimensions, graph execution, UI, files, or history. Owner adapters belong to their domain.

| API | Use |
| --- | --- |
| `TagSet` | Shared immutable ordered-set normalization/membership/batch transforms. |
| `TagPolicy` | Exact custom tags or an explicit legacy compatibility convention. Never infer policy from a label. |
| `TagAccess` | Read/add/remove/replace/clear tags through an owner's atomic storage boundary. |
| `DataDictionary` | Validate and snapshot dictionary values, compare content, reject cycles/deep inputs. |
| `DataValuePolicy` | Application-specific value snapshots and equality. Mutable leaves must be detached on every read and write. |
| `DataAccess` | Read/find/put/remove/replace or atomically patch a dictionary. |
| `MetadataStore` | The owner adapter: live reads and atomic updates, with lifetime/authority checks and commit notifications. |
| `MetadataCapabilities` | Register named, typed target adapters with TAG and/or DATA capability. `GLOBAL` is the application's shared registry. |

Tags are an ordered set, data is a dictionary. Empty data keys remain valid for compatibility; keys are case-sensitive. Missing data returns null, but top-level null values are rejected; use removal explicitly. Lists and nested dictionaries may contain null if the value policy supports it. Data returned by accessors is detached from the owner, including mutable application values.

Registration requires a namespaced type ID, target class and explicit capabilities. A target can be an entity wrapper, a document session, or a record containing the owner context and stable identity. The registry does not select arbitrary subclasses or inspect target internals. New targets implement an adapter; new tag names and user dictionary keys do not require registration.

```java
registry.register("addon:example", ExampleTarget.class,
        Set.of(MetadataCapabilities.Capability.TAG),
        target -> new MetadataCapabilities.Access(
                new TagAccess(target.tagStore(), TagPolicy.EXACT), null));

registry.resolve("addon:example", target).requireTags().add("任务目标");
```

Addons register common-side adapters through `GeometryNodePlugin.registerMetadata(MetadataRegistrationContext)` using their addon namespace. Client-only adapters are registered during client initialization and must never be referenced by common initialization.

An adapter's `update` must resolve the current owner and run the transformation **inside** its lock/owner thread/file transaction, validate before publishing, and compare content before dirty marking or notification. A batch is atomic within one capability store. Separate tag and data stores do not promise a cross-store transaction. Never implement a synchronous store by merely queueing an asynchronous network request and reporting success.

Do not retain snapshots as mutable storage, write directly to owner lists, create a second authoritative copy, or use a query-result row as the owner. A persistent owner's unload is not deletion. Recreated resources need incarnation checks so old handles cannot modify new instances.

Legacy policies preserve existing graph/library conventions and limits at migration boundaries. New custom object tags use EXACT. Vanilla registry tags and entity command tags remain separate APIs. Raw graph history snapshots must remain raw.

Architecture and integration inventory: [object-metadata.md](../../../../../../../../docs/architecture/object-metadata.md).
