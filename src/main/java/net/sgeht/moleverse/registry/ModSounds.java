package net.sgeht.moleverse.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.sgeht.moleverse.Moleverse;

/**
 * Sound events of this mod.
 *
 * <p>The event id and the file name are kept apart on purpose: one event may be
 * backed by several files, and the game picks between them at random. The
 * mapping lives in the generated {@code sounds.json}, produced by
 * {@code data.ModSoundProvider}.</p>
 *
 * <p>Source files are converted from {@code audio/raw} by {@code audio/convert.ps1}.
 * They must be mono, otherwise Minecraft plays them without direction or
 * distance falloff.</p>
 */
public final class ModSounds {

    public static final DeferredRegister<SoundEvent> REGISTER =
            DeferredRegister.create(Registries.SOUND_EVENT, Moleverse.MOD_ID);

    /** A mole clawing through soil. Two variants. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MOLE_DIG = register("entity.mole.dig");

    /** A mole sniffing the air. Two variants. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MOLE_SNIFF = register("entity.mole.sniff");

    /** A mole breaking through to the surface. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MOLE_SURFACE = register("entity.mole.surface");

    private ModSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return REGISTER.register(name, () -> SoundEvent.createVariableRangeEvent(Moleverse.id(name)));
    }
}
