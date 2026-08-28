package me.lonefelidae16.xpOrbExtractor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class XpOrbExtractorConfig {
    public enum DrainTarget {
        LEVEL("command.xporbextractor.text.level"),
        XP("command.xporbextractor.text.xp");

        private final String key;

        DrainTarget(String key) {
            this.key = key;
        }

        public Component asComponent() {
            return Component.translatable(this.key);
        }
    }

    public enum DrainDepletion {
        BOTTLES("command.xporbextractor.text.bottles"),
        ORBS("command.xporbextractor.text.orbs");

        private final String key;

        DrainDepletion(String key) {
            this.key = key;
        }

        public Component asComponent() {
            return Component.translatable(this.key);
        }
    }

    private static final Path CONFIG_FILE = Path.of("./config", XpOrbExtractor.MOD_ID, "config.json");
    private static final Gson CONVERTER = new GsonBuilder()
            .registerTypeAdapter(DrainTarget.class, new TypeAdapter<DrainTarget>() {
                @Override
                public void write(JsonWriter out, DrainTarget value) throws IOException {
                    out.value(value.name());
                }

                @Override
                public DrainTarget read(JsonReader in) throws IOException {
                    try {
                        return DrainTarget.valueOf(in.nextString());
                    } catch (Exception ignore) {
                    }
                    return DrainTarget.XP;
                }
            })
            .registerTypeAdapter(DrainDepletion.class, new TypeAdapter<DrainDepletion>() {
                @Override
                public void write(JsonWriter out, DrainDepletion value) throws IOException {
                    out.value(value.name());
                }

                @Override
                public DrainDepletion read(JsonReader in) throws IOException {
                    try {
                        return DrainDepletion.valueOf(in.nextString());
                    } catch (Exception ignore){
                    }
                    return DrainDepletion.ORBS;
                }
            })
            .create();

    public boolean bModEnabled = true;
    public DrainTarget drainTarget = DrainTarget.XP;
    public int amountToDrain = 500;
    public DrainDepletion depletion = DrainDepletion.ORBS;

    public static XpOrbExtractorConfig load() {
        final File configFile = CONFIG_FILE.toFile();
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                return Objects.requireNonNull(CONVERTER.fromJson(reader, XpOrbExtractorConfig.class));
            } catch (Exception ex) {
                XpOrbExtractor.LOGGER.error("Failed to load config file, resetting.");
            }
        }
        var instance = new XpOrbExtractorConfig();
        save(instance);
        return instance;
    }

    public static void save(XpOrbExtractorConfig config) {
        try {
            Path dir = CONFIG_FILE.getParent();
            if (!Files.isDirectory(dir)) {
                Files.createDirectories(dir);
            }
            Files.writeString(CONFIG_FILE, CONVERTER.toJson(config));
        } catch (Exception ex) {
            XpOrbExtractor.LOGGER.error("Failed to write config file.", ex);
        }
    }
}
