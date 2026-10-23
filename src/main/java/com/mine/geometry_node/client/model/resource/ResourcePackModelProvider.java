package com.mine.geometry_node.client.model.resource;

import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;
import com.mine.geometry_node.core.engine.system.model.io.ModelResource;
import com.mine.geometry_node.core.engine.system.model.io.ModelResourcePaths;
import com.mine.geometry_node.core.engine.system.model.io.ModelResourceProvider;
import com.mine.geometry_node.core.engine.system.model.io.StreamModelResourceProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.*;
import java.nio.file.Path;

/** Resource-pack adapter; bounded spooling and path validation remain in the common provider. */
public final class ResourcePackModelProvider implements ModelResourceProvider {
    private final StreamModelResourceProvider delegate;
    public ResourcePackModelProvider(ResourceManager resources,String namespace,String prefix,ModelCancellation cancellation,Path spoolDirectory) throws IOException {
        String confinedPrefix=prefix==null||prefix.isEmpty()?"":ModelResourcePaths.normalize(prefix)+"/";
        this.delegate=new StreamModelResourceProvider(id->{
            String logical=confinedPrefix+ModelResourcePaths.normalize(id);
            Identifier location=Identifier.fromNamespaceAndPath(namespace,logical);
            var resource=resources.getResource(location).orElseThrow(()->new FileNotFoundException("missing model resource "+location));
            return resource.open();
        },spoolDirectory,cancellation);
    }
    @Override public ModelResource open(String id)throws IOException{return delegate.open(id);}
    @Override public void close()throws IOException{delegate.close();}
}
