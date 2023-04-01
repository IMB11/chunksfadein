package com.koteinik.chunksfadein.config;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

import com.koteinik.chunksfadein.Logger;
import com.koteinik.chunksfadein.MathUtils;
import com.koteinik.chunksfadein.config.ConfigEntry.Type;
import com.koteinik.chunksfadein.core.ChunkData;
import com.koteinik.chunksfadein.core.Curves;
import com.koteinik.chunksfadein.core.FadeTypes;
import com.moandjiezana.toml.Toml;

import net.fabricmc.loader.api.FabricLoader;

public class Config {
    public static final Long CONFIG_VERSION = 1L;
    public static final double MAX_FADE_TIME = 10;
    public static final double MAX_ANIMATION_TIME = 10;
    public static final double MAX_ANIMATION_OFFSET = 319;
    public static final String CONFIG_VERSION_KEY = "config-version";
    public static final String MOD_ENABLED_KEY = "mod-enabled";
    public static final String SHOW_MOD_BUTTON_IN_SETTINGS_KEY = "show-mod-button-in-settings";
    public static final String UPDATE_NOTIFIER_ENABLED_KEY = "update-notifier-enabled";
    public static final String FADE_ENABLED_KEY = "fade-enabled";
    public static final String FADE_TIME_KEY = "fade-time";
    public static final String FADE_TYPE_KEY = "fade-type";
    public static final String ANIMATION_ENABLED_KEY = "animation-enabled";
    public static final String ANIMATE_NEAR_PLAYER_KEY = "animate-near-player";
    public static final String ANIMATION_TIME_KEY = "animation-time";
    public static final String ANIMATION_CURVE_KEY = "animation-curve";
    public static final String ANIMATION_OFFSET_KEY = "animation-offset";

    private static final Map<String, ConfigEntry<?>> entries = new HashMap<>();
    private static File configFile;

    public static boolean isModEnabled;
    public static boolean isFadeEnabled;
    public static boolean isAnimationEnabled;
    public static boolean isUpdateNotifierEnabled;
    public static boolean showModButtonInSettings;
    public static boolean animateNearPlayer;

    public static float animationInitialOffset;
    public static float animationChangePerMs;
    public static float fadeChangePerMs;

    public static FadeTypes fadeType;
    public static Curves animationCurve;

    static {
        addEntry(new ConfigEntry<Integer>(Curves.EASE_OUT.ordinal(), ANIMATION_CURVE_KEY, Type.INTEGER))
                .addListener((o) -> animationCurve = Curves.values()[MathUtils.clamp(o, 0,
                        Curves.values().length - 1)]);
        addEntry(new ConfigEntry<Integer>(FadeTypes.FULL.ordinal(), FADE_TYPE_KEY, Type.INTEGER))
                .addListener((o) -> fadeType = FadeTypes.values()[MathUtils.clamp(o, 0,
                        Curves.values().length - 1)]);

        addEntry(new ConfigEntryDoubleLimitable(0.01, MAX_FADE_TIME, 2.56, FADE_TIME_KEY))
                .addListener((o) -> fadeChangePerMs = fadeChangeFromSeconds(o));
        addEntry(new ConfigEntryDoubleLimitable(0.01, MAX_ANIMATION_TIME, 2.56, ANIMATION_TIME_KEY))
                .addListener((o) -> animationChangePerMs = animationChangeFromSeconds(o));
        addEntry(new ConfigEntryDoubleLimitable(1, MAX_ANIMATION_OFFSET, 64, ANIMATION_OFFSET_KEY))
                .addListener((o) -> {
                    animationInitialOffset = (o).floatValue();
                    ChunkData.reload();
                });

        addEntry(new ConfigEntry<Boolean>(true, MOD_ENABLED_KEY, Type.BOOLEAN))
                .addListener((o) -> isModEnabled = o);
        addEntry(new ConfigEntry<Boolean>(true, FADE_ENABLED_KEY, Type.BOOLEAN))
                .addListener((o) -> {
                    isFadeEnabled = o;
                    ChunkData.reload();
                });
        addEntry(new ConfigEntry<Boolean>(false, ANIMATION_ENABLED_KEY, Type.BOOLEAN))
                .addListener((o) -> isAnimationEnabled = o);
        addEntry(new ConfigEntry<Boolean>(true, UPDATE_NOTIFIER_ENABLED_KEY, Type.BOOLEAN))
                .addListener((o) -> isUpdateNotifierEnabled = o);
        addEntry(new ConfigEntry<Boolean>(true, SHOW_MOD_BUTTON_IN_SETTINGS_KEY, Type.BOOLEAN))
                .addListener((o) -> showModButtonInSettings = o);
        addEntry(new ConfigEntry<Boolean>(true, ANIMATE_NEAR_PLAYER_KEY, Type.BOOLEAN))
                .addListener((o) -> animateNearPlayer = o);
    }

    public static float fadeChangeFromSeconds(double seconds) {
        final float secondsInMs = (float) (seconds * 1000);

        return 1f / secondsInMs;
    }

    public static float secondsFromFadeChange() {
        return 1f / fadeChangePerMs / 1000f;
    }

    public static float animationChangeFromSeconds(double seconds) {
        final float secondsInMs = (float) (seconds * 1000);

        return 1 / secondsInMs;
    }

    public static double secondsFromAnimationChange() {
        return (double) (1 / animationChangePerMs / 1000);
    }

    public static void load() {
        configFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), "chunksfadein.properties");
        Toml toml = new Toml();

        try {
            if (!configFile.exists())
                configFile.createNewFile();

            toml.read(configFile);
        } catch (Exception e) {
        }

        for (ConfigEntry<?> entry : entries.values())
            entry.load(toml);

        Long configVersion = toml.getLong(CONFIG_VERSION_KEY);
        if (configVersion != CONFIG_VERSION) {
            // To save fade time after upgrading the mod
            Logger.info((double) get(FADE_TIME_KEY).value);
            setDouble(FADE_TIME_KEY, (double) get(FADE_TIME_KEY).value * 4);
            setDouble(ANIMATION_TIME_KEY, (double) get(ANIMATION_TIME_KEY).value * 4);
        }
    }

    public static void save() {
        String string = "";

        string += CONFIG_VERSION_KEY + " = " + CONFIG_VERSION + "\n";
        for (ConfigEntry<?> entry : entries.values())
            string += entry.toString();

        try {
            if (!configFile.exists())
                configFile.createNewFile();

            BufferedWriter writer = new BufferedWriter(new FileWriter(configFile));
            writer.write(string);
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
            Logger.error("Failed to save config! If this is an error, please create an issue on github!");
        }
    }

    @SuppressWarnings("unchecked")
    public static void setInteger(String key, Integer value) {
        ((ConfigEntry<Integer>) get(key)).set(value);
    }

    @SuppressWarnings("unchecked")
    public static void setBoolean(String key, Boolean value) {
        ((ConfigEntry<Boolean>) get(key)).set(value);
    }

    @SuppressWarnings("unchecked")
    public static void setDouble(String key, Double value) {
        ((ConfigEntry<Double>) get(key)).set(value);
    }

    public static void reset(String key) {
        get(key).reset();
    }

    private static ConfigEntry<?> get(String key) {
        return (ConfigEntry<?>) entries.get(key);
    }

    private static <T> ConfigEntry<T> addEntry(ConfigEntry<T> entry) {
        entries.put(entry.configKey, entry);
        return entry;
    }
}
