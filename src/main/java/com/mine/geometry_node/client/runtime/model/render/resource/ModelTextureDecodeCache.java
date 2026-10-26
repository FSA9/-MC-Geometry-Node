package com.mine.geometry_node.client.runtime.model.render.resource;

import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;
import java.io.IOException;
import java.util.*;

/** One preparation's CPU image cache. Sampler ownership remains with each independently uploaded texture. */
public final class ModelTextureDecodeCache {
    private record Key(GltfAsset.Image image, boolean srgb, boolean mipmaps) {
        @Override public boolean equals(Object other) {
            return other instanceof Key key && image == key.image && srgb == key.srgb && mipmaps == key.mipmaps;
        }
        @Override public int hashCode() { return 31 * (31 * System.identityHashCode(image) + Boolean.hashCode(srgb)) + Boolean.hashCode(mipmaps); }
    }
    private final Map<Key,ModelTextureBackend.Decoded> decoded=new HashMap<>();
    private long bytes;
    public ModelTextureBackend.Decoded decode(GltfAsset.Texture texture, boolean srgb, ModelCancellation token,long remaining) throws IOException {
        token.check();
        var key=new Key(texture.image(),srgb,texture.sampler().minFilter()>=9984);
        var value=decoded.get(key);
        if(value!=null)return value;
        value=ModelTextureBackend.decode(texture,token,remaining,srgb);
        decoded.put(key,value);bytes+=value.bytes;
        return value;
    }
    public long bytes(){return bytes;}
    public Collection<ModelTextureBackend.Decoded> values(){return decoded.values();}
    @SafeVarargs public static Set<ModelTextureBackend.Decoded> distinct(Collection<ModelTextureBackend.Decoded>... groups) {
        Set<ModelTextureBackend.Decoded> result=Collections.newSetFromMap(new IdentityHashMap<>());
        for(var group:groups)result.addAll(group);
        return result;
    }
    @SafeVarargs public static long bytes(Collection<ModelTextureBackend.Decoded>... groups) {
        return distinct(groups).stream().mapToLong(image->image.bytes).sum();
    }
    @SafeVarargs public static void close(Collection<ModelTextureBackend.Decoded>... groups) {
        distinct(groups).forEach(ModelTextureBackend.Decoded::close);
    }
}
