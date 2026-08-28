package net.sgeht.moleverse.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration of this mod.
 *
 * <p>Split according to the NeoForge convention:
 * COMMON is loaded on both sides and not synchronised;
 * SERVER is world specific and synchronised to the client;
 * CLIENT is client only.
 * Only COMMON is needed so far.</p>
 *
 * <p>Tuning knobs for model poses deliberately do <em>not</em> live here. They
 * are runtime values in {@code client.debug.MoleDebug}, adjustable in game with
 * {@code /moleverse peek ...}, because editing a file and reloading breaks the
 * look-and-adjust loop that getting a pose right depends on.</p>
 */
public final class MoleverseConfig {

    private static final ModConfigSpec.Builder COMMON_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING = COMMON_BUILDER
            .comment("Write additional Moleverse debug output to the log.")
            .define("debugLogging", false);

    public static final ModConfigSpec.BooleanValue GREET_PLAYER = COMMON_BUILDER
            .comment("Greet the player with a Moleverse message when they join a world.")
            .define("greetPlayer", true);

    public static final ModConfigSpec COMMON_SPEC = COMMON_BUILDER.build();

    private MoleverseConfig() {
    }
}
