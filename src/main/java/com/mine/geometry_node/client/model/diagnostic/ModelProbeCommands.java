package com.mine.geometry_node.client.model.diagnostic;
import com.mine.geometry_node.client.model.render.ModelProbeController;
import com.mine.geometry_node.core.command.client.ClientCommandUtils;
import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.*;
import net.minecraft.client.Minecraft;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
/** Explicit local P0 commands. Large IO never runs in command/render callbacks. */
public final class ModelProbeCommands {
    private static final ThreadPoolExecutor IO=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4),r->{
        Thread t=new Thread(r,"GeometryNode-ModelProbeIO");t.setDaemon(true);return t;});
    private static final AtomicLong GENERATION=new AtomicLong();
    private static volatile ModelCancellation cancellation=new ModelCancellation();
    static { IO.allowCoreThreadTimeOut(true); }
    private ModelProbeCommands() {}
    public static <S> void register(CommandDispatcher<S> dispatcher) {
        LiteralArgumentBuilder<S> root=LiteralArgumentBuilder.<S>literal("model_probe");
        for(var mode:ModelProbeController.Mode.values()) root.then(LiteralArgumentBuilder.<S>literal(mode.name().toLowerCase(java.util.Locale.ROOT))
                .executes(c->{ModelProbeController.INSTANCE.show(mode);say("Probe shown: "+mode+"; BOTH: lower=host, upper=resident. Left/center/right=normal/mirror/nonuniform.");return 1;}));
        root.then(LiteralArgumentBuilder.<S>literal("clear").executes(c->{cancel();ModelProbeController.INSTANCE.clear();say("Probe cleared; GPU retirement queued after frame fence.");return 1;}));
        root.then(LiteralArgumentBuilder.<S>literal("status").executes(c->{say(new com.google.gson.Gson().toJson(ModelProbeController.INSTANCE.report()));return 1;}));
        root.then(LiteralArgumentBuilder.<S>literal("report").executes(c->{
            var game=Minecraft.getInstance().gameDirectory.toPath();var render=ModelProbeController.INSTANCE.report();
            return work(token->ModelProbeAudit.writeReport(game,render,token).toString());}));
        root.then(LiteralArgumentBuilder.<S>literal("inspect").then(RequiredArgumentBuilder.<S,String>argument("path",StringArgumentType.greedyString()).executes(c->{
            String input=StringArgumentType.getString(c,"path");Path file=Path.of(input);
            if(!file.isAbsolute())file=Minecraft.getInstance().gameDirectory.toPath().resolve(file);
            Path selected=file.normalize();return work(token->new com.google.gson.Gson().toJson(ModelProbeAudit.inspect(selected,token)));})));
        dispatcher.register(root);
    }
    public static void cancel() {GENERATION.incrementAndGet();cancellation.cancel();cancellation=new ModelCancellation();IO.getQueue().clear();}
    private interface Job {String run(ModelCancellation token) throws Exception;}
    private static int work(Job job) {
        long generation=GENERATION.get();ModelCancellation token=cancellation;
        try {IO.execute(()->{
            String response;
            try{response=job.run(token);}catch(Exception e){response="Model probe: "+e.getMessage();}
            String completed=response;
            Minecraft.getInstance().execute(()->{if(generation==GENERATION.get())say(completed);});
        });say("Probe IO queued");return 1;}catch(RejectedExecutionException e){say("Probe IO queue full");return 0;}
    }
    private static void say(String text){ClientCommandUtils.sendClientMsg(text);}
}
