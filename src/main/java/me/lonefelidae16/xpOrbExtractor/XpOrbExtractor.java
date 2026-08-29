package me.lonefelidae16.xpOrbExtractor;

import me.lonefelidae16.groominglib.api.PrefixableMessageFactory;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class XpOrbExtractor implements ModInitializer {
    public static final Logger LOGGER = LogManager.getLogger(XpOrbExtractor.class, new PrefixableMessageFactory(XpOrbExtractor.class.getSimpleName()));
    public static final String MOD_ID = "xp-orb-extractor";
    public static final String TAG_XP_AMOUNT = "xp_amount";
    public static final String TAG_HAS_XP_AMOUNT = "has_xp_amount";

    private static final XpOrbExtractorConfig CONFIG = XpOrbExtractorConfig.load();
    private static final Set<DelayedTickTask> SERVER_TASKS = ConcurrentHashMap.newKeySet();

    public static class SoundEvents {
        public static final SoundEvent XP_DRAIN_FAIL = new SoundEvent(Identifier.fromNamespaceAndPath(MOD_ID, "xp_drain_fail"), Optional.empty());
    }

    @Override
    public void onInitialize() {
        XpOrbExtractorCommands.setup();

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!SERVER_TASKS.isEmpty()) {
                for (var tickTask : SERVER_TASKS) {
                    if (server.getTickCount() == tickTask.ticks) {
                        server.execute(() -> {
                            tickTask.task.accept(server);
                            tickTask.bDone = true;
                        });
                    }
                }
                SERVER_TASKS.removeIf(tickTask -> tickTask.bDone);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SERVER_TASKS.clear();
        });
    }

    public static XpOrbExtractorConfig config() {
        return CONFIG;
    }

    public static void schedule(MinecraftServer server, int delayTicks, Consumer<MinecraftServer> task) {
        SERVER_TASKS.add(new DelayedTickTask(delayTicks + server.getTickCount(), task));
    }

    private static class DelayedTickTask {
        int ticks;
        final Consumer<MinecraftServer> task;
        boolean bDone;

        DelayedTickTask(int ticks, Consumer<MinecraftServer> task) {
            this.ticks = ticks;
            this.task = task;
            this.bDone = false;
        }
    }
}
