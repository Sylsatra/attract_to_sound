package com.example.soundattract.config.separate;

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.List;

public class VoiceConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLE_VOICE_CHAT_INTEGRATION;
    public static final ForgeConfigSpec.IntValue VOICE_CHAT_WHISPER_RANGE;
    public static final ForgeConfigSpec.IntValue VOICE_CHAT_NORMAL_RANGE;
    public static final ForgeConfigSpec.DoubleValue VOICE_CHAT_WEIGHT;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> VOICE_CHAT_DB_THRESHOLD_MAP;

    static {
        BUILDER.comment("Sound Attract Mod - Voice Chat Configuration").push("voice_chat");

        BUILDER.push("Simple VC");
        ENABLE_VOICE_CHAT_INTEGRATION = BUILDER.comment(
                "[DEPRECATED as of 6.3.3] Moved to soundattract/server-rules.toml (SERVER config).",
                "The value here is no longer read at runtime; kept only so existing config files do not error.")
                .define("enableVoiceChatIntegration", true);
        VOICE_CHAT_WHISPER_RANGE = BUILDER.comment(
                "[DEPRECATED as of 6.3.4] Moved to soundattract/server-rules.toml (SERVER config).",
                "The value here is no longer read at runtime; kept only so existing config files do not error.")
                .defineInRange("voiceChatWhisperRange", 16, 1, 64);
        VOICE_CHAT_NORMAL_RANGE = BUILDER.comment(
                "[DEPRECATED as of 6.3.4] Moved to soundattract/server-rules.toml (SERVER config).",
                "The value here is no longer read at runtime; kept only so existing config files do not error.")
                .defineInRange("voiceChatNormalRange", 32, 1, 128);
        VOICE_CHAT_WEIGHT = BUILDER.comment(
                "[DEPRECATED as of 6.3.4] Moved to soundattract/server-rules.toml (SERVER config).",
                "The value here is no longer read at runtime; kept only so existing config files do not error.")
                .defineInRange("voiceChatWeight", 9.0, 0.0, 10.0);
        VOICE_CHAT_DB_THRESHOLD_MAP = BUILDER.comment(
                "[DEPRECATED as of 6.3.4] Moved to soundattract/server-rules.toml (SERVER config).",
                "The value here is no longer read at runtime; kept only so existing config files do not error."
        ).defineList("voiceChatDbThresholdMap", java.util.Arrays.asList("110:2.0", "90:1.8", "75:1.5", "50:1.0", "30:0.7", "10:0.3", "0:0.05"), obj -> obj instanceof String && ((String) obj).contains(":"));
        
        BUILDER.pop();
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
