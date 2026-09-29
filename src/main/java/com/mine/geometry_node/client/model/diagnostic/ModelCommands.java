package com.mine.geometry_node.client.model.diagnostic;

import com.google.gson.GsonBuilder;
import com.mine.geometry_node.client.model.runtime.ClientModelService;
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
        root.then(LiteralArgumentBuilder.<S>literal("clear").executes(c->run(()->{MODELS.clear();say("All client model instances cleared.");})));
        root.then(LiteralArgumentBuilder.<S>literal("remove").then(ModelCommands.<S>id().executes(c->run(()->say(MODELS.remove(StringArgumentType.getString(c,"id"))?"Model removed.":"No such model instance.")))));
        root.then(LiteralArgumentBuilder.<S>literal("query").then(ModelCommands.<S>id().executes(c->run(()->say(JSON.toJson(MODELS.queryReport(StringArgumentType.getString(c,"id"))))))));
        root.then(LiteralArgumentBuilder.<S>literal("status").executes(c->run(()->say(JSON.toJson(MODELS.report())))));
        root.then(LiteralArgumentBuilder.<S>literal("report").executes(c->run(ModelCommands::report)));
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
        Path path=Path.of(text);if(!path.isAbsolute())path=Minecraft.getInstance().gameDirectory.toPath().resolve(path);return path.normalize();
    }
    private static void load(String id,CompletionStage<?> completion) {
        say("Model "+id+" queued for import.");
        completion.whenComplete((value,error)->{
            if(error==null)say("Model "+id+" ready. Use /model query "+id+" for import details.");
            else say("Model "+id+": "+ClientModelService.rootMessage(error));
        });
    }
    private static void report() {
        String text=JSON.toJson(MODELS.report());Path target=Minecraft.getInstance().gameDirectory.toPath().resolve("geometry_node/model/p2-report.json");
        REPORT_IO.execute(()->{
            String message;
            try{Files.createDirectories(target.getParent());Files.writeString(target,text);message="Model report: "+target;}
            catch(Exception error){message="Model report failed: "+error.getMessage();}
            String response=message;Minecraft.getInstance().execute(()->say(response));
        });
    }
    private static int run(Runnable action){try{action.run();return 1;}catch(RuntimeException error){say("Model: "+ClientModelService.rootMessage(error));return 0;}}
    private static void say(String text){ClientCommandUtils.sendClientMsg(text);}
}
