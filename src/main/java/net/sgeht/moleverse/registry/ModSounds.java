package net.sgeht.moleverse.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.sgeht.moleverse.Moleverse;

/**
 * Sound events of this mod.
 *
 * <p>Still empty. The register is already wired up so that later sounds
 * (digging, mole calls, dimension ambience) only need one line:
 * {@code public static final DeferredHolder<SoundEvent, SoundEvent> DIG = register("dig");}</p>
 */
public final class ModSounds {

    public static final DeferredRegister<SoundEvent> REGISTER =
            DeferredRegister.create(Registries.SOUND_EVENT, Moleverse.MOD_ID);

    private ModSounds() {
    }

    @SuppressWarnings("unused")
    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return REGISTER.register(name, () -> SoundEvent.createVariableRangeEvent(Moleverse.id(name)));
    }
}
