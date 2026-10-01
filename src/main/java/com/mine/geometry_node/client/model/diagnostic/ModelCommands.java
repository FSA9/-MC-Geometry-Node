package com.mine.geometry_node.client.model.diagnostic;

import com.google.gson.GsonBuilder;
import com.mine.geometry_node.client.model.runtime.ClientModelService;
import com.mine.geometry_node.client.model.render.ModelStaticRenderer;
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
        LiteralArgumentBuilder<S> root=LiteralArgumentBuilder.literal("model");
        root.then(LiteralArgumentBuilder.<S>literal("load").then(RequiredArgumentBuilder.<S,String>argument("path",StringArgumentType.greedyString()).executes(c->run(()->{
            load("preview",MODELS.load(localPath(StringArgumentType.getString(c,"path"))));
        }))));
        root.then(LiteralArgumentBuilder.<S>literal("create").then(ModelCommands.<S>id().then(RequiredArgumentBuilder.<S,String>argument("path",StringArgumentType.greedyString()).executes(c->run(()->{
            String id=StringArgumentType.getString(c,"id");load(id,MODELS.create(id,ClientModelService.Source.local(localPath(StringArgumentType.getString(c,"path"))),ClientModelService.previewTransform(),-1,true));
        })))));
        root.then(LiteralArgumentBuilder.<S>literal("resource").then(RequiredArgumentBuilder.<S,String>argument("identifier",StringArgumentType.string()).executes(c->run(()->{
            load("preview",MODELS.loadResource(Identifier.parse(StringArgumentType.getString(c,"identifier"))));
        }))));
        var clear=LiteralArgumentBuilder.<S>literal("clear").executes(c->run(()->{MODELS.clear();say("All client model instances cleared.");}));
        clear.then(LiteralArgumentBuilder.<S>literal("temporary").executes(c->run(()->{MODELS.clearTemporary();say("Temporary client model instances cleared.");})));
        root.then(clear);
        root.then(LiteralArgumentBuilder.<S>literal("remove").then(ModelCommands.<S>id().executes(c->run(()->say(MODELS.remove(StringArgumentType.getString(c,"id"))?"Model removed.":"No such model instance.")))));
        root.then(LiteralArgumentBuilder.<S>literal("reload").then(ModelCommands.<S>id().executes(c->run(()->{
            String id=StringArgumentType.getString(c,"id");load(id,MODELS.reload(id));
        }))));
        root.then(LiteralArgumentBuilder.<S>literal("query").then(ModelCommands.<S>id().executes(c->run(()->say(ModelReportSummary.query(MODELS.queryReport(StringArgumentType.getString(c,"id"))))))));
        root.then(LiteralArgumentBuilder.<S>literal("status").executes(c->run(()->say(ModelReportSummary.status(MODELS.report())+" /model report saves full diagnostics."))));
        root.then(LiteralArgumentBuilder.<S>literal("report").executes(c->run(ModelCommands::report)));
        root.then(LiteralArgumentBuilder.<S>literal("backend")
                .then(LiteralArgumentBuilder.<S>literal("resident").executes(c->run(()->{
                    ModelStaticRenderer.setIrisResidentEnabled(true); say("Iris resident main/shadow enabled; warm up before comparing metrics.");
                })))
                .then(LiteralArgumentBuilder.<S>literal("compat").executes(c->run(()->{
                    ModelStaticRenderer.setIrisResidentEnabled(false); say("Iris immediate compatibility path enabled for comparison; geometry uploads every frame.");
                }))));
        root.then(LiteralArgumentBuilder.<S>literal("metrics").then(LiteralArgumentBuilder.<S>literal("reset").executes(c->run(()->{
            ModelStaticRenderer.resetPerformanceMetrics(); say("Model CPU/transfer metrics reset; resident assets preserved.");
        }))));
        root.then(LiteralArgumentBuilder.<S>literal("update").then(update()));
        dispatcher.register(root);
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
        MODELS.transform(id,change.apply(instance.transform()));say("Model "+id+" updated.");
    }
    private static <S> double d(CommandContext<S> c,String name){return DoubleArgumentType.getDouble(c,name);}
    private static Path localPath(String input) {
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
            if(error==null)say("Model "+id+" ready. Use /model query "+id+" for import details.");
            else say("Model "+id+": "+ClientModelService.rootMessage(error));
        });
    }
    private static void report() {
        var snapshot = MODELS.report(); var context = ModelReportContext.capture();
        String summary = ModelReportSummary.status(snapshot);
        Path gameDirectory = Minecraft.getInstance().gameDirectory.toPath();
        Path target = gameDirectory.resolve("geometry_node/model/p5-report.json");
        REPORT_IO.execute(()->{
            String message;
            try{
                ModelReportContext.addFileEvidence(context, gameDirectory); snapshot.put("testContext", context);
                String text = JSON.toJson(snapshot);
                Files.createDirectories(target.getParent());Files.writeString(target,text);
                // Keep immutable evidence across shaderpack switches and repeated runs.
                Path archive = target.resolveSibling("p5-report-" + java.time.Instant.now().toString().replace(':', '-') + ".json");
                Files.writeString(archive, text); message=summary+" Report: "+target;
            }
            catch(Exception error){message="Model report failed: "+error.getMessage();}
            String response=message;Minecraft.getInstance().execute(()->say(response));
        });
    }
    private static int run(Runnable action){try{action.run();return 1;}catch(RuntimeException error){say("Model: "+ClientModelService.rootMessage(error));return 0;}}
    private static void say(String text){ClientCommandUtils.sendClientMsg(text);}
}
