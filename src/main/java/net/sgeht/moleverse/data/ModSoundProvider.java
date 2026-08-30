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
 *
 * <h2>Where a volume belongs</h2>
 *
 * <p>Three separate multipliers reach the mixer, and putting a number in the
 * wrong one is the mistake this file exists to prevent. The volume set here is
 * the <em>file's</em> level - how loud that recording is relative to the rest of
 * the pack. {@code LivingEntity.getSoundVolume} is the <em>animal's</em> level,
 * which is why the shrew is quiet everywhere at once rather than once per event.
 * And the volume handed to a {@code playSound} call is that <em>moment's</em>
 * level, which is where {@code client.BurrowScratching} keeps its dials so the
 * tuning panel can move them without a rebuild.</p>
 *
 * <p>So an event whose loudness is already decided somewhere else is left at 1.0
 * here on purpose, and says so. Two attenuations stacked by accident is a sound
 * nobody can find again.</p>
 */
public final class ModSoundProvider extends SoundDefinitionsProvider {

    /**
     * The weasel's voice, one step under full.
     *
     * <p>It has no {@code getSoundVolume} of its own, so this is the only place
     * its loudness is decided. Under full because everything it says is meant to
     * arrive from somewhere in the dark rather than from the middle of the room.</p>
     */
    private static final float WEASEL_VOLUME = 0.8F;

    /**
     * The room tone, well under everything.
     *
     * <p>The loop plays continuously and non-positionally - see
     * {@code BiomeAmbientSoundsHandler.LoopSoundInstance}, which fades it in to a
     * factor of 1.0 and leaves it there - so this number is the whole of its
     * presence. It is a floor for the corridor to sit on, not something a player
     * should ever notice starting.</p>
     */
    private static final float UNDEREARTH_VOLUME = 0.4F;

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

        // The weasel. Three hisses and three chitters, because these are the two
        // sounds a player hears while something is deciding about them and a
        // repeat inside one encounter would give the animal away as a machine.
        add(ModSounds.WEASEL_HISS, definition()
                .subtitle("subtitles.moleverse.entity.weasel.hiss")
                .with(
                        weasel("entity/weasel/weasel_hiss1"),
                        weasel("entity/weasel/weasel_hiss2"),
                        weasel("entity/weasel/weasel_hiss3")));

        add(ModSounds.WEASEL_CHITTER, definition()
                .subtitle("subtitles.moleverse.entity.weasel.chitter")
                .with(
                        weasel("entity/weasel/weasel_chitter1"),
                        weasel("entity/weasel/weasel_chitter2"),
                        weasel("entity/weasel/weasel_chitter3")));

        add(ModSounds.WEASEL_HURT, definition()
                .subtitle("subtitles.moleverse.entity.weasel.hurt")
                .with(
                        weasel("entity/weasel/weasel_hurt1"),
                        weasel("entity/weasel/weasel_hurt2")));

        // The shrew, at file level. Its quiet is `Shrew.getSoundVolume`, which is
        // 0.3 for the reason written out there - and taking another fifth off it
        // here would put two or three animals a player is trying to locate in the
        // dark under a quarter of everything else in the room.
        add(ModSounds.SHREW_SQUEAK, definition()
                .subtitle("subtitles.moleverse.entity.shrew.squeak")
                .with(
                        sound(Moleverse.id("entity/shrew/shrew_squeak1")),
                        sound(Moleverse.id("entity/shrew/shrew_squeak2")),
                        sound(Moleverse.id("entity/shrew/shrew_squeak3"))));

        add(ModSounds.SHREW_HURT, definition()
                .subtitle("subtitles.moleverse.entity.shrew.hurt")
                .with(
                        sound(Moleverse.id("entity/shrew/shrew_hurt1")),
                        sound(Moleverse.id("entity/shrew/shrew_hurt2"))));

        // The three that are not predators, all left at file level. Two of them
        // are played through a level-side `playSound` whose call site names its
        // own volume - the worm's glide and the grub's chewing - and the beetle
        // goes through `BurrowCritter.getSoundVolume`, which is 0.25 for every
        // passive at once. Nothing here has a loudness left to decide.
        add(ModSounds.WORM_SLITHER, definition()
                .subtitle("subtitles.moleverse.entity.great_worm.slither")
                .with(
                        sound(Moleverse.id("entity/great_worm/worm_slither1")),
                        sound(Moleverse.id("entity/great_worm/worm_slither2")),
                        sound(Moleverse.id("entity/great_worm/worm_slither3"))));

        add(ModSounds.BEETLE_CLICK, definition()
                .subtitle("subtitles.moleverse.entity.soil_beetle.click")
                .with(
                        sound(Moleverse.id("entity/soil_beetle/beetle_click1")),
                        sound(Moleverse.id("entity/soil_beetle/beetle_click2"))));

        add(ModSounds.GRUB_MUNCH, definition()
                .subtitle("subtitles.moleverse.entity.grub.munch")
                .with(
                        sound(Moleverse.id("entity/grub/grub_munch1")),
                        sound(Moleverse.id("entity/grub/grub_munch2"))));

        // Not an animal: the rope taking a player's weight.
        add(ModSounds.LADDER_RUSTLE, definition()
                .subtitle("subtitles.moleverse.block.root_ladder.rustle")
                .with(
                        sound(Moleverse.id("block/root_ladder/ladder_rustle1")),
                        sound(Moleverse.id("block/root_ladder/ladder_rustle2"))));

        // The mole behind the wall. Left at file level: every stroke is played
        // through `BurrowScratching.SCRATCH_VOLUME` and `GRIT_VOLUME`, which are
        // on sliders, and a second attenuation here would silently halve the
        // range those sliders cover.
        add(ModSounds.BURROW_SCRATCH, definition()
                .subtitle("subtitles.moleverse.ambient.burrow.scratch")
                .with(
                        sound(Moleverse.id("ambient/burrow/scratch1")),
                        sound(Moleverse.id("ambient/burrow/scratch2")),
                        sound(Moleverse.id("ambient/burrow/scratch3"))));

        // No subtitle on either of the last two, and it is not an oversight. A
        // subtitle is a line that appears when something happens; these two never
        // stop, so the line would never leave the screen and would tell a player
        // reading subtitles nothing they did not know on arrival.
        //
        // Neither is streamed either. Streaming is for the minute-plus files
        // NeoForge's javadoc names - a record, a real music track. Twelve seconds
        // and twenty seconds sit in memory for nothing, and a streamed loop is
        // the one case where reading from disk can be heard as a gap at the seam.
        add(ModSounds.BURROW_UNDEREARTH, definition()
                .with(sound(Moleverse.id("ambient/burrow/underearth_loop1"))
                        .volume(UNDEREARTH_VOLUME)));

        add(ModSounds.BURROW_THEME, definition()
                .with(sound(Moleverse.id("music/burrow_theme1"))));
    }

    /** One weasel file at the weasel's level. Saves repeating the volume six times. */
    private static SoundDefinition.Sound weasel(String path) {
        return sound(Moleverse.id(path)).volume(WEASEL_VOLUME);
    }
}
