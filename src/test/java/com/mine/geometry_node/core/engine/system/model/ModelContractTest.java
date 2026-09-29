package com.mine.geometry_node.core.engine.system.model;
import com.mine.geometry_node.core.engine.system.model.asset.*;
import com.mine.geometry_node.core.engine.system.model.math.ModelTransform;
import com.mine.geometry_node.core.engine.system.model.runtime.*;
import com.mine.geometry_node.core.engine.system.model.diagnostic.ModelProblem;
import com.mine.geometry_node.core.engine.system.model.capability.ModelCapabilities;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class ModelContractTest {
    @Test void independentPoseAndSnapshots() {
        Matrix4f source=new Matrix4f().translate(1,0,0);
        ModelScene scene=new ModelScene(List.of(new ModelScene.Node("same",-1,source),new ModelScene.Node("same",0,new Matrix4f().translate(0,2,0))));
        source.identity(); ModelPose a=new ModelPose(scene),b=new ModelPose(scene);
        a.setLocal(0,new Matrix4f().translate(7,0,0));var snapshot=a.snapshot();
        a.reset();snapshot.world(0).identity();
        assertEquals(7,snapshot.world(1).m30());assertEquals(2,snapshot.world(1).m31());
        assertEquals(1,b.snapshot().world(0).m30());assertEquals(1,scene.nodes().getFirst().local().m30());
    }
    @Test void rejectsCyclicCompiledScene() {
        assertThrows(IllegalArgumentException.class,()->new ModelScene(List.of(new ModelScene.Node("cycle",0,new Matrix4f()))));
    }
    @Test void farWorldPositionSubtractsBeforeFloatConversion() {
        var t=new ModelTransform(29_999_999.125,80,20,0,0,0,1,-1,2,3);
        assertEquals(.125f,t.relativeTo(29_999_999,80,20).m30());assertTrue(t.mirrored());
        assertThrows(IllegalArgumentException.class,()->new ModelTransform(0,0,0,0,0,0,0,1,1,1));
        assertThrows(IllegalArgumentException.class,()->new ModelTransform(0,0,0,0,0,0,1,0,1,1));
    }
    @Test void mirrorFixtureHasOutwardWindingAndNormals() {
        for(Matrix4f transform:List.of(new Matrix4f(),new Matrix4f().scale(-1,1,1),new Matrix4f().rotateY(.5f).scale(.5f,2,1))) {
            ModelMesh mesh=ModelProbeFixtures.cube(transform);
            for(int i=0;i<mesh.indexCount();i+=3) {
                int a=mesh.index(i),b=mesh.index(i+1),c=mesh.index(i+2);
                Vector3f normal=position(mesh,b).sub(position(mesh,a)).cross(position(mesh,c).sub(position(mesh,a))).normalize();
                assertTrue(normal.dot(new Vector3f(mesh.attribute(a,3),mesh.attribute(a,4),mesh.attribute(a,5)))>.999f);
            }
        }
    }
    private static Vector3f position(ModelMesh m,int i){return new Vector3f(m.attribute(i,0),m.attribute(i,1),m.attribute(i,2));}
    @Test void completePackageBudgetIncludesExternalTextures() throws Exception {
        String hash="0".repeat(64);
        var packageOk=new ModelManifest("scene.gltf",List.of(new ModelManifest.Dependency("scene.gltf",100,hash),
                new ModelManifest.Dependency("image.png",ModelLimits.MAX_PACKAGE_BYTES-100,hash)),ModelManifest.Distribution.LOCAL_REQUIRED);
        assertEquals(ModelLimits.MAX_PACKAGE_BYTES,packageOk.validate(ModelLimits.DEFAULT));
        var tooLarge=new ModelManifest("model.glb",List.of(new ModelManifest.Dependency("model.glb",ModelLimits.MAX_PACKAGE_BYTES+1,hash)),ModelManifest.Distribution.SERVER_SHARED);
        assertEquals(ModelProblem.Code.BUDGET_EXCEEDED,assertThrows(ModelProblem.class,()->tooLarge.validate(ModelLimits.DEFAULT)).code());
        assertThrows(IllegalArgumentException.class,()->new ModelManifest.Dependency("../bad",1,hash));
        assertThrows(IllegalArgumentException.class,()->new ModelManifest("model.glb",List.of(new ModelManifest.Dependency("model.glb",1,hash),new ModelManifest.Dependency("model.glb",1,hash)),ModelManifest.Distribution.SERVER_SHARED));
    }
    @Test void lateCompletionDisposesInsteadOfResurrecting() {
        var disposals=new AtomicInteger();ModelHandle<String> h=new ModelHandle<>(4,x->disposals.incrementAndGet());
        h.close();assertFalse(h.publish(4,"late"));assertEquals(1,disposals.get());assertEquals(ModelLoadState.DISPOSED,h.state());
        assertThrows(java.util.concurrent.CompletionException.class,()->h.completion().toCompletableFuture().join());
    }
    @Test void futureIsReliableBeforeAndAfterReadyAndGenerationChecked() {
        var disposed=new ArrayList<String>();ModelHandle<String> h=new ModelHandle<>(4,disposed::add);
        assertFalse(h.publish(3,"old"));assertEquals(List.of("old"),disposed);
        assertFalse(h.advance(ModelLoadState.UPLOAD_QUEUED));
        for(var s:List.of(ModelLoadState.READING,ModelLoadState.PARSING,ModelLoadState.CPU_READY,ModelLoadState.UPLOAD_QUEUED))assertTrue(h.advance(s));
        var completion=h.completion();assertTrue(h.publish(4,"good"));assertEquals("good",completion.toCompletableFuture().join());
        assertEquals("good",h.completion().toCompletableFuture().join());h.close();h.close();assertEquals(List.of("old","good"),disposed);
    }
    @Test void availabilityDoesNotImplyVisualVerification() {
        var caps=new ModelCapabilities(Map.of(ModelCapabilities.Feature.SUN_SHADOW,new ModelCapabilities.Evidence(ModelCapabilities.Status.API_AVAILABLE,"v0.4")));
        assertEquals(ModelCapabilities.Status.UNTESTED,caps.features().get(ModelCapabilities.Feature.PBR).status());
        assertThrows(UnsupportedOperationException.class,()->caps.features().clear());
    }
}
