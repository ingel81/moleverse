package net.sgeht.moleverse.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Konfiguration der Mod.
 *
 * <p>Aufteilung nach NeoForge-Konvention:
 * COMMON = auf beiden Seiten geladen, nicht synchronisiert;
 * SERVER = weltspezifisch, wird zum Client synchronisiert;
 * CLIENT = rein clientseitig.
 * Bisher wird nur COMMON gebraucht.</p>
 */
public final class MoleverseConfig {

    private static final ModConfigSpec.Builder COMMON_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING = COMMON_BUILDER
            .comment("Zusaetzliche Debug-Ausgaben von Moleverse ins Log schreiben.")
            .define("debugLogging", false);

    public static final ModConfigSpec.BooleanValue GREET_PLAYER = COMMON_BUILDER
            .comment("Spieler beim Betreten der Welt mit einer Moleverse-Nachricht begruessen.")
            .define("greetPlayer", true);

    public static final ModConfigSpec COMMON_SPEC = COMMON_BUILDER.build();

    private MoleverseConfig() {
    }
}
