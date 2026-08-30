package net.sgeht.moleverse.entity.predator;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.sgeht.moleverse.entity.critter.Earthworm;
import net.sgeht.moleverse.registry.ModSounds;

/**
 * The shrew: the burrow's small hostile, and the mole's actual rival.
 *
 * <p>Not a rat and not a mouse. A shrew is an insectivore like the mole, it
 * lives in the same soil, it eats the same worms, and it has to eat about its
 * own body weight in them every day or it dies - which is the whole of this
 * animal's design. It is not down here to fight the player. It is down here
 * hunting, and the player is in the way of the hunt.</p>
 *
 * <p>Everything follows from that. It goes for earthworms first and by
 * preference. It attacks a player only at close range, and having bitten it
 * leaves rather than pressing - see {@link NipAndDartGoal}, which is where the
 * character actually lives. It runs from a weasel, because a weasel eats
 * shrews. And it drops a worm, because there is one in it.</p>
 *
 * <p>It spawns in twos and threes. One shrew is a nuisance a player walks past;
 * three taking turns out of the dark is the reason to carry a light.</p>
 */
public class Shrew extends BurrowPredator {

    /** How fast it goes about, and how fast it closes on something. */
    private static final double STROLL_SPEED = 1.0;
    private static final double HUNT_SPEED = 1.25;

    /**
     * How far it will run from a weasel, and the two speeds it does it at.
     *
     * <p>Twelve blocks rather than the sixteen a weasel hunts at, on purpose. A
     * shrew that reacted at the weasel's own range would never once be seen in
     * the same room as one, and the moment worth having is the one where a player
     * watches the corridor empty of shrews ahead of something they have not seen
     * yet.</p>
     */
    private static final float WEASEL_FEAR = 12.0F;
    private static final double WALK_AWAY = 1.3;
    private static final double RUN_AWAY = 1.6;

    /** How close a player has to be before it is worth biting. */
    private static final float NIP_RANGE = 2.0F;

    /**
     * How long between squeaks, in the units {@code Mob} counts them in.
     *
     * <p>Half again over the vanilla eighty, and the reason is that shrews come
     * in twos and threes. The interval is per animal, so three of them in a room
     * share the airtime: at eighty each that is a squeak every two seconds from
     * somewhere, which is not chatter but a smoke alarm. At a hundred and twenty
     * a group still talks over itself often enough to sound like a group, and a
     * single shrew heard down a corridor stays an event.</p>
     *
     * <p>The number is not a period. {@code Mob.baseTick} starts a counter at
     * minus this and fires when a roll under a thousand beats it, so this is the
     * guaranteed silence and the mean gap is roughly forty ticks longer.</p>
     */
    private static final int SQUEAK_INTERVAL = 120;

    public Shrew(EntityType<? extends Shrew> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                // At mole scale the player is a quarter size, so a touch under full scale so packs still pass each other.
                .add(Attributes.SCALE, 3.5)
                // Three hearts: two hits with a fist, one with anything sharp.
                // It is meant to die easily. What it is not meant to do is be
                // standing still when the swing arrives.
                .add(Attributes.MAX_HEALTH, 6.0)
                // Two, which is half a heart under a zombie and, more usefully,
                // survivable several times over in leather. The threat is the
                // rate, not the hit.
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                // The fastest thing in the mod. A player cannot outwalk one and
                // should not be able to - the answer to a shrew is light or a
                // weapon, never distance.
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 12.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));

        // Above everything, including the hunt. A shrew that finished its meal
        // while a weasel walked up to it would be a shrew that has misunderstood
        // the food chain it is in the middle of.
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(
                this, Weasel.class, WEASEL_FEAR, WALK_AWAY, RUN_AWAY));

        this.goalSelector.addGoal(2, new NipAndDartGoal(this, HUNT_SPEED));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, STROLL_SPEED));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        // Worms first, and the order of these two matters. The target selector
        // takes the first goal that finds something, so a shrew with a worm in
        // reach ignores the player standing behind it - which is the difference
        // between an animal that is hunting and a monster that is hunting you.
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Earthworm.class, true));

        // A player, but only one that has come close. `mustSee` is on and the
        // range is two blocks, so it is a reaction to being walked past rather
        // than a charge down a corridor. That is what makes it a harasser: the
        // player chooses the fight by getting near it.
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(
                this, Player.class, 10, true, false,
                (target, level) -> this.distanceToSqr(target) <= NIP_RANGE * NIP_RANGE));
    }

    /**
     * Its own voice now, so nothing here is pitched.
     *
     * <p>These used to be the rabbit's, shifted up by half again to turn a
     * rabbit into something the size of a thumb. The files are generated at this
     * animal's own scale - see {@code art/generators/elevenlabs_sounds.py}, whose
     * prompt asks for a shrew and not for a small rabbit - so the correction is
     * gone rather than reduced. Leaving it in would have been a second
     * transposition applied to something already in the right register, which is
     * how a shrew ends up sounding like a kettle.</p>
     */
    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.SHREW_SQUEAK.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return SQUEAK_INTERVAL;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return ModSounds.SHREW_HURT.get();
    }

    /**
     * The same squeal it makes when hit, as a stand-in.
     *
     * <p>There is no generated death sound for this animal yet. Of the two
     * wrong answers available - the rabbit's death, or its own hurt twice - the
     * hurt is much the smaller: a shrew dies to one or two hits, so the death
     * cry lands within a second of a hurt cry and the borrowed voice would be
     * heard back to back with the real one. A repeat is a cheap sound; a
     * different animal is a different animal.</p>
     */
    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.SHREW_HURT.get();
    }

    /**
     * Quiet, and quieter than the animal's size alone would suggest.
     *
     * <p>{@code BurrowCritter} makes the same argument for the passives: a sound
     * at full volume from something a third of a block long carries further than
     * the animal can be seen. It matters more here, because there are two or
     * three of them and they are the thing a player is trying to locate in the
     * dark - a squeak that is loud everywhere tells them nothing about where.</p>
     */
    @Override
    protected float getSoundVolume() {
        return 0.3F;
    }
}
