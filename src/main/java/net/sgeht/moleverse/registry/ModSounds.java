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

    /** A weasel's warning, close and dry. Three variants. Only heard while it hunts. */
    public static final DeferredHolder<SoundEvent, SoundEvent> WEASEL_HISS = register("entity.weasel.hiss");

    /** Excited chatter, the moment it takes a target. Three variants. */
    public static final DeferredHolder<SoundEvent, SoundEvent> WEASEL_CHITTER = register("entity.weasel.chitter");

    /** A weasel yelping. Two variants, and its death cry until one is generated. */
    public static final DeferredHolder<SoundEvent, SoundEvent> WEASEL_HURT = register("entity.weasel.hurt");

    /** A shrew squeaking. Three variants. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SHREW_SQUEAK = register("entity.shrew.squeak");

    /** A shrew squealing. Two variants, and its death cry until one is generated. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SHREW_HURT = register("entity.shrew.hurt");

    /** Claws through packed earth, heard through a wall. Three variants. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BURROW_SCRATCH = register("ambient.burrow.scratch");

    /** The burrow's room tone. Played by the biome's ambient loop, never from code. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BURROW_UNDEREARTH = register("ambient.burrow.underearth");

    /** The burrow's one music cue. Played by the biome's background music. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BURROW_THEME = register("music.burrow_theme");

    /** A big worm's body over damp earth. Three variants. */
    public static final DeferredHolder<SoundEvent, SoundEvent> WORM_SLITHER = register("entity.great_worm.slither");

    /** Dry chitin ticking somewhere in the dark. Two variants. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BEETLE_CLICK = register("entity.soil_beetle.click");

    /** A grub working on a larder. Two variants. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GRUB_MUNCH = register("entity.grub.munch");

    /** Braided roots taking a player's weight. Two variants. */
    public static final DeferredHolder<SoundEvent, SoundEvent> LADDER_RUSTLE = register("block.root_ladder.rustle");

    private ModSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return REGISTER.register(name, () -> SoundEvent.createVariableRangeEvent(Moleverse.id(name)));
    }
}
