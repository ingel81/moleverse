package net.sgeht.moleverse.data;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.SoundDefinition;
import net.neoforged.neoforge.common.data.SoundDefinitionsProvider;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.registry.ModSounds;

/**
 * Generates {@code assets/moleverse/sounds.json}.
 *
 * <p>Events with more than one file are picked from at random, which keeps a
 * repeated sound from turning mechanical.</p>
 */
public final class ModSoundProvider extends SoundDefinitionsProvider {

    public ModSoundProvider(PackOutput output) {
        super(output, Moleverse.MOD_ID);
    }

    @Override
    public void registerSounds() {
        add(ModSounds.MOLE_DIG, definition()
                .subtitle("subtitles.moleverse.entity.mole.dig")
                .with(
                        sound(Moleverse.id("entity/mole/dig1")),
                        sound(Moleverse.id("entity/mole/dig2"))));

        add(ModSounds.MOLE_SNIFF, definition()
                .subtitle("subtitles.moleverse.entity.mole.sniff")
                .with(
                        sound(Moleverse.id("entity/mole/sniff1")),
                        sound(Moleverse.id("entity/mole/sniff2"))));

        add(ModSounds.MOLE_SURFACE, definition()
                .subtitle("subtitles.moleverse.entity.mole.surface")
                .with(sound(Moleverse.id("entity/mole/surface1"))));
    }
}
