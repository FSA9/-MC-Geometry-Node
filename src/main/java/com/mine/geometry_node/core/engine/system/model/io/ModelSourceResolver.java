package com.mine.geometry_node.core.engine.system.model.io;
import com.mine.geometry_node.core.engine.system.model.diagnostic.ModelProblem;
import java.io.IOException;
import java.net.*;
import java.nio.file.*;
/** Relative URI resolution for a single explicitly selected local root. */
public final class ModelSourceResolver {
    private ModelSourceResolver() {}
    public static Path resolve(Path root, Path model, String reference) throws IOException {
        try {
            URI uri=new URI(reference);
            if(uri.isAbsolute() || uri.getRawAuthority()!=null || uri.getQuery()!=null || uri.getFragment()!=null)
                throw new IllegalArgumentException("only relative asset URIs are allowed");
            String decoded=uri.getPath();
            if(decoded==null || decoded.isBlank() || decoded.startsWith("/") || decoded.contains("\\") || decoded.contains(":"))
                throw new IllegalArgumentException("invalid relative URI");
            Path realRoot=root.toRealPath();
            Path realModel=model.toRealPath();
            if(!realModel.startsWith(realRoot)) throw new IllegalArgumentException("model outside source root");
            Path target=realModel.getParent().resolve(decoded).normalize();
            if(!target.startsWith(realRoot)) throw new IllegalArgumentException("dependency escapes source root");
            Path realTarget=target.toRealPath();
            if(!realTarget.startsWith(realRoot) || !Files.isRegularFile(realTarget))
                throw new IllegalArgumentException("dependency symlink escapes root or is not a file");
            return realTarget;
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new ModelProblem(ModelProblem.Code.INVALID_SOURCE,reference,e.getMessage());
        }
    }
}
