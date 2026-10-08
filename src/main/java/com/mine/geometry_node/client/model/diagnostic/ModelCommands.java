package com.mine.geometry_node.client.model.diagnostic;

import com.mine.geometry_node.GeometryNode;
import com.google.gson.GsonBuilder;
import com.mine.geometry_node.client.model.runtime.ClientModelService;
import com.mine.geometry_node.client.model.render.bvh.ModelLightingProfiler;
import com.mine.geometry_node.core.command.client.ClientCommandUtils;
import com.mine.geometry_node.core.engine.system.model.math.ModelTransform;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.*;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Small command adapter over the model service. No file IO, decoding or mesh building runs in command callbacks. */
public final class ModelCommands {
    private static final ClientModelService MODELS=ClientModelService.INSTANCE;
    private static final com.google.gson.Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final ThreadPoolExecutor REPORT_IO=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(2),task->{Thread t=new Thread(task,"GeometryNode-ModelReport");t.setDaemon(true);return t;});
    static {REPORT_IO.allowCoreThreadTimeOut(true);}
    private ModelCommands(){}
    public static <S> void register(CommandDispatcher<S> dispatcher) {
        LiteralArgumentBuilder<S> root=LiteralArgumentBuilder.<S>literal("model")
                .executes(c->run(()->say(ModelReportSummary.status(MODELS.report()))));
        root.then(LiteralArgumentBuilder.<S>literal("load").then(RequiredArgumentBuilder.<S,String>argument("path",StringArgumentType.greedyString()).executes(c->run(()->{
            load("preview",MODELS.load(localPath(StringArgumentType.getString(c,"path"))));
        }))));
        root.then(LiteralArgumentBuilder.<S>literal("create").then(ModelCommands.<S>id().then(RequiredArgumentBuilder.<S,String>argument("path",StringArgumentType.greedyString()).executes(c->run(()->{
            String id=StringArgumentType.getString(c,"id");load(id,MODELS.create(id,ClientModelService.Source.local(localPath(StringArgumentType.getString(c,"path"))),ClientModelService.previewTransform(),-1,true));
        })))));
        root.then(LiteralArgumentBuilder.<S>literal("resource").then(RequiredArgumentBuilder.<S,String>argument("identifier",StringArgumentType.string()).executes(c->run(()->{
            load("preview",MODELS.loadResource(Identifier.parse(StringArgumentType.getString(c,"identifier"))));
        }))));
        var clear=LiteralArgumentBuilder.<S>literal("clear").executes(c->run(()->{MODELS.clear();com.mine.geometry_node.client.model.render.bvh.ModelLocalLightProviders.clearAttached();say("All client model instances cleared.");}));
        clear.then(LiteralArgumentBuilder.<S>literal("temporary").executes(c->run(()->{MODELS.clearTemporary();say("Temporary client model instances cleared.");})));
        root.then(clear);
        root.then(LiteralArgumentBuilder.<S>literal("remove").then(ModelCommands.<S>id().executes(c->run(()->say(MODELS.remove(StringArgumentType.getString(c,"id"))?"Model removed.":"No such model instance.")))));
        root.then(LiteralArgumentBuilder.<S>literal("reload").then(ModelCommands.<S>id().executes(c->run(()->{
            String id=StringArgumentType.getString(c,"id");load(id,MODELS.reload(id));
        }))));
        root.then(LiteralArgumentBuilder.<S>literal("status")
                .executes(c->run(()->say(ModelReportSummary.status(MODELS.report()))))
                .then(ModelCommands.<S>id().executes(c->run(()->say(ModelReportSummary.query(MODELS.queryReport(StringArgumentType.getString(c,"id"))))))));
        ModelRenderCommands.register(root);
        ModelCollisionCommands.register(root);
        root.then(LiteralArgumentBuilder.<S>literal("hud")
                .executes(c -> ModelHud.command(null))
                .then(LiteralArgumentBuilder.<S>literal("on").executes(c -> ModelHud.command(true)))
                .then(LiteralArgumentBuilder.<S>literal("off").executes(c -> ModelHud.command(false)))
                .then(LiteralArgumentBuilder.<S>literal("status").executes(c -> ModelHud.command(null))));
        root.then(LiteralArgumentBuilder.<S>literal("report").executes(c->run(ModelCommands::report)));
        root.then(LiteralArgumentBuilder.<S>literal("material").then(material()));
        root.then(LiteralArgumentBuilder.<S>literal("update").then(update()));
        dispatcher.register(root);
    }
    static <S> LiteralArgumentBuilder<S> profileCommands() {
        return LiteralArgumentBuilder.<S>literal("profile")
                .then(LiteralArgumentBuilder.<S>literal("lighting")
                        .executes(c->run(()->startLightingProfile(20)))
                        .then(RequiredArgumentBuilder.<S,Integer>argument("frames",IntegerArgumentType.integer(1,120))
                                .executes(c->run(()->startLightingProfile(IntegerArgumentType.getInteger(c,"frames"))))))
                .then(LiteralArgumentBuilder.<S>literal("samples")
                        .executes(c->run(()->startQualityProfile(60)))
                        .then(RequiredArgumentBuilder.<S,Integer>argument("frames",IntegerArgumentType.integer(1,120))
                                .executes(c->run(()->startQualityProfile(IntegerArgumentType.getInteger(c,"frames"))))))
                .then(LiteralArgumentBuilder.<S>literal("resolution")
                        .executes(c->run(()->startResolutionProfile(60)))
                        .then(RequiredArgumentBuilder.<S,Integer>argument("frames",IntegerArgumentType.integer(1,120))
                                .executes(c->run(()->startResolutionProfile(IntegerArgumentType.getInteger(c,"frames"))))))
                .then(LiteralArgumentBuilder.<S>literal("status").executes(c->run(()->say("Lighting profile: "+ModelLightingProfiler.status()))))
                .then(LiteralArgumentBuilder.<S>literal("cancel").executes(c->run(()->{
                    ModelLightingProfiler.cancel(); say("Lighting profile cancelled; original modules and quality restored.");
                })));
    }

    private static void startLightingProfile(int frames) {
        requireLightingProfileIdle();
        ModelLightingProfiler.start(frames, ModelCommands::writeLightingProfile);
        say("Lighting profile started: "+ModelLightingProfiler.stageCount()+" stages, 60 warmup frames and "+frames+" samples per stage. /model local profile cancel restores settings.");
    }
    private static void startQualityProfile(int frames) {
        requireLightingProfileIdle();
        ModelLightingProfiler.startQuality(frames, ModelCommands::writeLightingProfile);
        say("Local-light samples profile started: 4 / 3 / 2 / 1 / 4 samples; auto starts from full resolution. 60 warmup frames and "+frames+" samples per stage. Keep the scene stable; original settings are restored afterwards.");
    }
    private static void startResolutionProfile(int frames) {
        requireLightingProfileIdle();
        ModelLightingProfiler.startResolution(frames, ModelCommands::writeLightingProfile);
        say("Local-light resolution profile started: 1 / 0.75 / 0.5 / 0.25 / 1; auto starts from 4 samples. 60 warmup frames and "+frames+" samples per stage. Keep the scene stable; original settings are restored afterwards.");
    }
    static void requireLightingProfileIdle() {
        if (ModelLightingProfiler.active()) throw new IllegalStateException("profile running: "+ModelLightingProfiler.status()+"; wait or use /model local profile cancel before changing settings");
    }
    private static void writeLightingProfile(java.util.Map<String,Object> report) {
        Path directory = Minecraft.getInstance().gameDirectory.toPath().resolve("geometry_node/model");
        var capturedAt = java.time.Instant.now();
        var output = new java.util.LinkedHashMap<String,Object>(report);
        output.put("capture", ModelReportContext.capture(capturedAt));
        try {
            output.put("performance", ModelPerformanceReport.create(MODELS.report(), java.util.Map.of()));
        } catch (RuntimeException error) {
            GeometryNode.LOGGER.error("Lighting profile snapshot failed", error);
            throw error;
        }
        try {
        REPORT_IO.execute(()->{
            String message;
            try {
                String text = JSON.toJson(output);
                Path target = ModelReportFiles.write(directory, capturedAt, text);
                message="Lighting profile complete. Report: "+target;
            } catch(Exception error) {
                GeometryNode.LOGGER.error("Lighting profile write failed", error);
                message="Lighting profile write failed: "+ClientModelService.rootMessage(error);
            }
            String response=message; Minecraft.getInstance().execute(()->say(response));
        });
        } catch (RuntimeException error) {
            GeometryNode.LOGGER.error("Lighting profile scheduling failed", error);
            throw error;
        }
    }
    private static <S> RequiredArgumentBuilder<S,String> material() {
        var instance=ModelCommands.<S>id();
        var index=RequiredArgumentBuilder.<S,Integer>argument("materialIndex",IntegerArgumentType.integer(0));
        for(String property:java.util.List.of("metallic","roughness","normalScale","occlusionStrength","emissiveStrength")) {
            index.then(LiteralArgumentBuilder.<S>literal(property).then(RequiredArgumentBuilder.<S,Float>argument("value",FloatArgumentType.floatArg())
                    .executes(c->run(()->{
                        int materialIndex=IntegerArgumentType.getInteger(c,"materialIndex");
                        int affected=MODELS.overrideMaterial(StringArgumentType.getString(c,"id"),materialIndex,property,FloatArgumentType.getFloat(c,"value"));
                        say("Material "+materialIndex+" "+property+" queued on shared asset ("+affected+" instances); /model report shows pendingMaterialUpdates and failedMaterialUpdates.");
                    }))));
        }
        index.then(LiteralArgumentBuilder.<S>literal("reset").executes(c->run(()->{
            int materialIndex=IntegerArgumentType.getInteger(c,"materialIndex");
            int affected=MODELS.resetMaterial(StringArgumentType.getString(c,"id"),materialIndex);
            say("Material "+materialIndex+" reset queued on shared asset ("+affected+" instances); wait for pendingMaterialUpdates=0 and failedMaterialUpdates=0.");
        })));
        return instance.then(index);
    }
    private static <S> RequiredArgumentBuilder<S,String> update() {
        var id=ModelCommands.<S>id();
        id.then(LiteralArgumentBuilder.<S>literal("position").then(vector("x","y","z",c->modify(c,t->new ModelTransform(d(c,"x"),d(c,"y"),d(c,"z"),t.qx(),t.qy(),t.qz(),t.qw(),t.sx(),t.sy(),t.sz())))));
        id.then(LiteralArgumentBuilder.<S>literal("translate").then(vector("x","y","z",c->modify(c,t->new ModelTransform(t.x()+d(c,"x"),t.y()+d(c,"y"),t.z()+d(c,"z"),t.qx(),t.qy(),t.qz(),t.qw(),t.sx(),t.sy(),t.sz())))));
        id.then(LiteralArgumentBuilder.<S>literal("scale").then(vector("x","y","z",c->modify(c,t->new ModelTransform(t.x(),t.y(),t.z(),t.qx(),t.qy(),t.qz(),t.qw(),(float)d(c,"x"),(float)d(c,"y"),(float)d(c,"z"))))));
        id.then(LiteralArgumentBuilder.<S>literal("rotation").then(RequiredArgumentBuilder.<S,Double>argument("qx",DoubleArgumentType.doubleArg()).then(RequiredArgumentBuilder.<S,Double>argument("qy",DoubleArgumentType.doubleArg()).then(RequiredArgumentBuilder.<S,Double>argument("qz",DoubleArgumentType.doubleArg()).then(RequiredArgumentBuilder.<S,Double>argument("qw",DoubleArgumentType.doubleArg()).executes(c->run(()->modify(c,t->new ModelTransform(t.x(),t.y(),t.z(),(float)d(c,"qx"),(float)d(c,"qy"),(float)d(c,"qz"),(float)d(c,"qw"),t.sx(),t.sy(),t.sz())))))))));
        id.then(LiteralArgumentBuilder.<S>literal("visible").then(RequiredArgumentBuilder.<S,Boolean>argument("visible",BoolArgumentType.bool()).executes(c->run(()->MODELS.visible(StringArgumentType.getString(c,"id"),BoolArgumentType.getBool(c,"visible"))))));
        id.then(LiteralArgumentBuilder.<S>literal("scene").then(RequiredArgumentBuilder.<S,Integer>argument("scene",IntegerArgumentType.integer(0)).executes(c->run(()->MODELS.scene(StringArgumentType.getString(c,"id"),IntegerArgumentType.getInteger(c,"scene"))))));
        return id;
    }
    private static <S> RequiredArgumentBuilder<S,String> id(){return RequiredArgumentBuilder.<S,String>argument("id",StringArgumentType.word());}
    private static <S> RequiredArgumentBuilder<S,Double> vector(String x,String y,String z,Consumer<CommandContext<S>> action) {
        return RequiredArgumentBuilder.<S,Double>argument(x,DoubleArgumentType.doubleArg()).then(RequiredArgumentBuilder.<S,Double>argument(y,DoubleArgumentType.doubleArg()).then(RequiredArgumentBuilder.<S,Double>argument(z,DoubleArgumentType.doubleArg()).executes(c->run(()->action.accept(c)))));
    }
    private static <S> void modify(CommandContext<S> context,java.util.function.UnaryOperator<ModelTransform> change) {
        String id=StringArgumentType.getString(context,"id");var instance=MODELS.query(id).orElseThrow(()->new IllegalArgumentException("unknown model instance: "+id));
        MODELS.transformAsync(id,change.apply(instance.transform())).whenComplete((ignored,error)->{
            if(error==null)say("Model "+id+" updated.");
            else say("Model "+id+": "+ClientModelService.rootMessage(error));
        });
    }
    private static <S> double d(CommandContext<S> c,String name){return DoubleArgumentType.getDouble(c,name);}
    static Path localPath(String input) {
        String text=input.strip();if(text.length()>1&&text.startsWith("\"")&&text.endsWith("\""))text=text.substring(1,text.length()-1);
        Path path=Path.of(text);
        if(path.isAbsolute()) {
            Path normalized=path.normalize();
            if(!Files.isRegularFile(normalized)) throw new IllegalArgumentException("model file does not exist: "+normalized);
            return normalized;
        }
        Path gameDirectory=Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize();
        Path candidate=gameDirectory.resolve(path).normalize();
        if(Files.isRegularFile(candidate)) return candidate;
        // Development runs use <project>/run as the game directory while fixtures live in <project>/docs.
        // Walk a bounded number of parents so fixture commands remain convenient without accepting an unbounded search.
        Path parent=gameDirectory;
        for(int i=0;i<3&&parent.getParent()!=null;i++){
            parent=parent.getParent();
            candidate=parent.resolve(path).normalize();
            if(Files.isRegularFile(candidate)) return candidate;
        }
        throw new IllegalArgumentException("model file does not exist: "+gameDirectory.resolve(path).normalize());
    }
    private static void load(String id,CompletionStage<?> completion) {
        say("Model "+id+" queued for import.");
        completion.whenComplete((value,error)->{
            if(error==null)say("Model "+id+" ready. /model status "+id);
            else say("Model "+id+": "+ClientModelService.rootMessage(error));
        });
    }
    private static void report() {
        var capturedAt = java.time.Instant.now();
        final java.util.Map<String,Object> source;
        final java.util.Map<String,Object> snapshot;
        final String summary;
        try {
            source = MODELS.report();
            snapshot = ModelPerformanceReport.create(source, ModelReportContext.capture(capturedAt));
            summary = ModelReportSummary.status(source);
        } catch (RuntimeException error) {
            GeometryNode.LOGGER.error("Model report snapshot failed", error);
            throw error;
        }
        Path directory = Minecraft.getInstance().gameDirectory.toPath().resolve("geometry_node/model");
        try {
        REPORT_IO.execute(()->{
            String message;
            try{
                String text = JSON.toJson(snapshot);
                Path target = ModelReportFiles.write(directory, capturedAt, text);
                message=summary+" Report: "+target;
            }
            catch(Exception error){
                GeometryNode.LOGGER.error("Model report write failed", error);
                message="Model report failed: "+ClientModelService.rootMessage(error);
            }
            String response=message;Minecraft.getInstance().execute(()->say(response));
        });
        } catch (RuntimeException error) {
            GeometryNode.LOGGER.error("Model report scheduling failed", error);
            throw error;
        }
    }
    static int run(Runnable action){try{action.run();return 1;}catch(RuntimeException error){say("Model: "+ClientModelService.rootMessage(error));return 0;}}
    static void say(String text){ClientCommandUtils.sendClientMsg(text);}
}
