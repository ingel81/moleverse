package net.sgeht.moleverse.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.sgeht.moleverse.Moleverse;

/**
 * Sound-Events der Mod.
 *
 * <p>Noch leer. Der Register ist bereits verdrahtet, damit spaetere Sounds
 * (Graben, Maulwurf-Laute, Dimensions-Ambiente) nur noch eine Zeile brauchen:
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
