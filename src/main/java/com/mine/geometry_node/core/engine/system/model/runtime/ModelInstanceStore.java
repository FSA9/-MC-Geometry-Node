package com.mine.geometry_node.core.engine.system.model.runtime;

import com.mine.geometry_node.core.engine.system.model.math.ModelTransform;
import java.util.*;
import java.util.function.Consumer;

/** Common, synchronized logical instance CRUD. Asset lifetime is owned by the supplied release action. */
public final class ModelInstanceStore<A> implements AutoCloseable {
    public record Snapshot<A>(String id, A asset, int scene, ModelTransform transform, boolean visible, long revision) {
        public Snapshot { Objects.requireNonNull(id); Objects.requireNonNull(asset); Objects.requireNonNull(transform); }
    }
    private record Entry<A>(Snapshot<A> snapshot, Runnable release) { }
    private final Map<String, Entry<A>> instances = new LinkedHashMap<>();
    private long revision;

    /** Atomic replacement publishes a complete instance before retiring its previous owner. */
    public void put(String id,A asset,int scene,ModelTransform transform,boolean visible,Runnable release) {
        validateId(id); Objects.requireNonNull(release);
        Entry<A> old;
        synchronized(this) {
            old=instances.put(id,new Entry<>(new Snapshot<>(id,asset,scene,transform,visible,++revision),release));
        }
        if(old!=null)old.release.run();
    }
    public synchronized Optional<Snapshot<A>> get(String id) {
        Entry<A> entry=instances.get(id); return entry==null?Optional.empty():Optional.of(entry.snapshot);
    }
    public synchronized List<Snapshot<A>> snapshots() { return instances.values().stream().map(Entry::snapshot).toList(); }
    public synchronized int size() { return instances.size(); }
    public synchronized void transform(String id,ModelTransform transform) { change(id,transform,null,null); }
    public synchronized void visible(String id,boolean visible) { change(id,null,visible,null); }
    public synchronized void scene(String id,int scene) { change(id,null,null,scene); }
    private void change(String id,ModelTransform transform,Boolean visible,Integer scene) {
        Entry<A> entry=instances.get(id);
        if(entry==null)throw new IllegalArgumentException("unknown model instance: "+id);
        Snapshot<A> old=entry.snapshot;
        instances.put(id,new Entry<>(new Snapshot<>(id,old.asset,scene==null?old.scene:scene,transform==null?old.transform:transform,
                visible==null?old.visible:visible,++revision),entry.release));
    }
    public boolean remove(String id) {
        Entry<A> old;
        synchronized(this) { old=instances.remove(id); }
        if(old!=null)old.release.run();
        return old!=null;
    }
    public void clear() {
        List<Entry<A>> old;
        synchronized(this) { old=List.copyOf(instances.values());instances.clear();revision++; }
        old.forEach(entry->entry.release.run());
    }
    @Override public void close() { clear(); }
    public static void validateId(String id) {
        if(id==null||!id.matches("[a-zA-Z0-9_.-]{1,96}"))throw new IllegalArgumentException("instance id must be 1-96 letters, digits, '.', '_' or '-'");
    }
}
