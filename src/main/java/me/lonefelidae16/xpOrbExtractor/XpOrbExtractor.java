package me.lonefelidae16.xpOrbExtractor;

import me.lonefelidae16.groominglib.api.PrefixableMessageFactory;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

public final class XpOrbExtractor implements ModInitializer {
    public static final Logger LOGGER = LogManager.getLogger(XpOrbExtractor.class, new PrefixableMessageFactory(XpOrbExtractor.class.getSimpleName()));
    public static final String MOD_ID = "xp-orb-extractor";
    public static final String TAG_XP_AMOUNT = "xp_amount";

    private static final XpOrbExtractorConfig CONFIG = XpOrbExtractorConfig.load();

    public static class SoundEvents {
        public static final SoundEvent XP_DRAIN_FAIL = new SoundEvent(Identifier.fromNamespaceAndPath(MOD_ID, "xp_drain_fail"), Optional.empty());
    }

    @Override
    public void onInitialize() {
        XpOrbExtractorCommands.setup();
    }

    public static XpOrbExtractorConfig config() {
        return CONFIG;
    }
}
