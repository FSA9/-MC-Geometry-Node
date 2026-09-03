package com.mine.geometry_node.client.ui.editor.datalibrary;

import com.mine.geometry_node.core.node.definition.port.PortType;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Client facade for the server-authoritative Data Library. */
public final class ClientDataLibraryRepository implements DataLibraryUiRepository {
    public static final ClientDataLibraryRepository INSTANCE = new ClientDataLibraryRepository();

    private final RemoteDataLibraryGateway remote = NetworkRemoteDataLibraryGateway.INSTANCE;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();

    private ClientDataLibraryRepository() {}

    @Override
    public List<Entry> entries() {
        return List.copyOf(remote.snapshot());
    }

    @Override
    public void create(PortType type) {
        Entry entry = new Entry(type, UUID.randomUUID(), uniqueKey(type), type.getDefaultValue());
        remote.create(entry, this::notifyChanged);
    }

    @Override
    public void update(Entry entry) {
        remote.update(entry, this::notifyChanged);
    }

    @Override
    public void delete(Set<EntryKey> ids) {
        remote.delete(ids, this::notifyChanged);
    }

    @Override public void addChangeListener(Runnable listener) { if (listener != null) listeners.add(listener); }
    @Override public void removeChangeListener(Runnable listener) { listeners.remove(listener); }

    @Override
    public void refresh(Runnable completion) {
        remote.refresh(completion);
    }

    private String uniqueKey(PortType type) {
        String base = type.name().toLowerCase(java.util.Locale.ROOT);
        Set<String> keys = entries().stream().filter(entry -> entry.type() == type).map(Entry::key)
                .collect(java.util.stream.Collectors.toSet());
        if (!keys.contains(base)) return base;
        for (int suffix = 2; ; suffix++) {
            String candidate = base + " " + suffix;
            if (!keys.contains(candidate)) return candidate;
        }
    }

    private void notifyChanged() {
        listeners.forEach(Runnable::run);
    }
}
