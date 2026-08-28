package net.sgeht.moleverse.entity;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.sgeht.moleverse.block.MoleMound;
import net.sgeht.moleverse.debug.MoleDebug;
import net.sgeht.moleverse.entity.burrow.BurrowConstants;
import net.sgeht.moleverse.entity.burrow.BurrowLog;
import net.sgeht.moleverse.entity.burrow.BurrowState;
import net.sgeht.moleverse.entity.burrow.MoleBurrowGoal;
import net.sgeht.moleverse.entity.burrow.MoleFollowMotherGoal;
import net.sgeht.moleverse.entity.burrow.MoundNetwork;
import net.sgeht.moleverse.registry.ModSounds;

/**
 * The mole. Small, passive, close to the ground.
 *
 * <p>Two things set it apart. The rearing pose it falls into when it stands
 * still, and the burrowing trip it makes when it is bored or frightened. The
 * trip itself is run by {@link MoleBurrowGoal}; what lives here is the state it
 * publishes, the physical side effects of being inside the ground, and the
 * recovery that undoes them if a trip is ever cut short by something other than
 * the goal. A juvenile never makes that trip on its own - it rides along with an
 * adult through {@link MoleFollowMotherGoal}, which needs nothing from this class
 * beyond the same state.</p>
 *
 * <p>The rearing pose is split in two. The secondary motion - head sweeping,
 * snout twitching, paws shifting - comes from the {@code mole_peek} keyframe
 * animation and is driven by {@link #peekAnimationState}. The body angle itself
 * is <em>not</em> a keyframe channel but a plain number, {@link #peekAmount},
 * applied to the root part in the model. Keyframing the root meant fighting
 * three separate coordinate conversions at once; a single blend factor is
 * explicit, tunable at runtime and reusable for aiming a digging mole in any
 * direction, which is exactly what {@link #digAmount} then does.</p>
 *
 * <p>Taming and surfacing with finds are the plan for later versions; see
 * {@code docs/MOLE_DESIGN.md}.</p>
 */
public class Mole extends Animal {

    /**
     * What the mole is doing about digging. Mirrored to the client as a byte
     * because rendering picks its animations and both body angles from it.
     */
    private static final EntityDataAccessor<Byte> DATA_BURROW_STATE =
            SynchedEntityData.defineId(Mole.class, EntityDataSerializers.BYTE);

    /** Ticks a mole must stand still before it rears up to look around. */
    private static final int PEEK_DELAY = 70;

    /** Ticks between two peeks. Slightly longer than the animation itself. */
    private static final int PEEK_INTERVAL = 200;

    /** How long the mole holds the reared pose, in ticks. */
    private static final int PEEK_HOLD = 120;

    /** Fraction of the remaining distance the blend factor covers per tick. */
    private static final float PEEK_BLEND_SPEED = 0.12F;

    /**
     * The same for the dig aim, and faster: diving in is a lunge, not the
     * settling a mole does when it rears up. At this rate the aim is all but
     * complete after eleven ticks, roughly half of {@code mole_burrow}, so the
     * mole points the right way well before that animation ends.
     */
    private static final float DIG_BLEND_SPEED = 0.2F;

    public final AnimationState peekAnimationState = new AnimationState();

    /** Ambient loop. Started once and never stopped; the model fades it by speed. */
    public final AnimationState idleAnimationState = new AnimationState();

    /** Alternating paws scooping. Loops while digging; where it digs is a body angle, not a channel. */
    public final AnimationState digAnimationState = new AnimationState();

    /** Diving in. Plays once, as long as {@code BURROWING} lasts. */
    public final AnimationState burrowAnimationState = new AnimationState();

    /** Coming back up. Plays once, as long as {@code EMERGING} lasts. */
    public final AnimationState emergeAnimationState = new AnimationState();

    private int peekCooldown = PEEK_DELAY;
    private int peekTicksLeft;

    /** 0 while crawling, 1 while fully reared up. Client side only. */
    private float peekAmount;
    private float peekAmountLast;

    /** 0 while level, 1 while fully aimed at the dig direction. Client side only. */
    private float digAmount;
    private float digAmountLast;

    /** Last burrow state the client reacted to, so a change can start or stop an animation. */
    private BurrowState lastAnimatedState = BurrowState.WANDERING;

    // DEBUG ONLY. What is left of the phase-2 harness: /moleverse dig burrow and
    // /moleverse dig emerge still preview the two one-shots on a standing mole,
    // which is how they were judged and how they stay checkable. Client side
    // only now, and it stands aside whenever the state machine is running -
    // holding the mole still and chaining emerge into the rearing pose are the
    // state machine's job.
    private int burrowRequestSeen = MoleDebug.burrowRequest;
    private int emergeRequestSeen = MoleDebug.emergeRequest;
    private int previewBurrowTicks;
    private int previewEmergeTicks;

    /**
     * The burrowing state machine, kept so {@code /moleverse mole burrow} can
     * reach into it.
     *
     * <p>Assigned in {@link #registerGoals()} rather than by a field initialiser:
     * {@code Mob}'s constructor calls that method before this class's own fields
     * exist, so an initialiser here would silently build a second goal that the
     * selector never sees. It stays null on the client, where {@code Mob} skips
     * {@code registerGoals} entirely.</p>
     */
    private @Nullable MoleBurrowGoal burrowGoal;

    /**
     * The mound this mole has left standing open, or null.
     *
     * <p>Saved with the entity, unlike everything else about a trip. The state
     * machine closes the shaft when it finishes, but a world that is saved
     * mid-trip never gets there - and a mound stuck open forever is a change to
     * the world, not just to the mole.</p>
     */
    private @Nullable BlockPos openShaft;

    public Mole(EntityType<? extends Mole> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
                .add(Attributes.MAX_HEALTH, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 12.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BURROW_STATE, BurrowState.WANDERING.id());
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Priority 0 and uninterruptable: once a mole is in the ground, nothing
        // else may claim its movement. It only holds MOVE and LOOK, so FloatGoal
        // above (JUMP) still works when he is swimming rather than digging.
        this.burrowGoal = new MoleBurrowGoal(this);
        this.goalSelector.addGoal(0, this.burrowGoal);
        // Same priority, added after. Two goals at one priority never displace
        // each other and insertion order decides who claims MOVE and LOOK first -
        // which is harmless here, because the two are mutually exclusive by
        // construction: the burrow goal refuses every baby, and the escort
        // refuses every adult.
        this.goalSelector.addGoal(0, new MoleFollowMotherGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.6));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    // --- burrowing ------------------------------------------------------------

    /** Null on the client, and only ever read by {@code /moleverse mole burrow}. */
    public @Nullable MoleBurrowGoal getBurrowGoal() {
        return this.burrowGoal;
    }

    public BurrowState getBurrowState() {
        return BurrowState.byId(this.entityData.get(DATA_BURROW_STATE));
    }

    /**
     * The one way the state changes, so no transition can slip past the log.
     *
     * @param reason short phrase for the debug log, in the mechanic's own words
     */
    public void setBurrowState(BurrowState state, String reason) {
        BurrowState previous = this.getBurrowState();
        if (previous == state) {
            return;
        }
        this.entityData.set(DATA_BURROW_STATE, state.id());
        BurrowLog.stateChange(this, previous, state, reason);
    }

    /**
     * Damage immunity while a trip is under way.
     *
     * <p>Deliberately decided from the state and never through
     * {@code setInvulnerable}: that flag is written to NBT, so a chunk unload
     * halfway down a shaft would serialise a mole that is invulnerable for good.
     * The state is not saved, so it cannot outlive the trip.</p>
     *
     * <p>The two exceptions vanilla grants its own invulnerability are granted
     * here as well. Without them {@code /kill} and a creative punch bounce off a
     * travelling mole, which is exactly the moment someone reaches for them.</p>
     */
    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource damageSource) {
        boolean immuneWhileUnderground = this.getBurrowState().isDamageImmune()
                && !damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && !damageSource.isCreativePlayer();
        return immuneWhileUnderground || super.isInvulnerableTo(level, damageSource);
    }

    /**
     * Turns the mole into something that can be inside the ground: no collision,
     * nothing to see, and no gravity pulling it off the route.
     *
     * <p>{@code noPhysics} is a plain field with no setter, and it also switches
     * off suffocation, which is the other half of what makes this work.</p>
     */
    public void beginUnderground() {
        this.noPhysics = true;
        this.setInvisible(true);
        // Gravity is switched off rather than cancelled every tick, because
        // travel() has already applied it by the time the goal runs. It is one
        // of the flags that survive to NBT, hence the clearing on load below.
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
        this.getNavigation().stop();
    }

    /** Undoes {@link #beginUnderground}. Safe to call on a mole that never dug. */
    public void endUnderground() {
        this.noPhysics = false;
        this.setInvisible(false);
        this.setNoGravity(false);
    }

    /**
     * Being underground outranks the effect list.
     *
     * <p>Vanilla recomputes the invisibility flag from the active mob effects
     * whenever one of them changes, and "none left" means visible. A regeneration
     * running out mid-trip would otherwise pop a travelling mole into view.</p>
     */
    @Override
    protected void updateInvisibilityStatus() {
        if (this.getBurrowState().isBelowGround()) {
            this.setInvisible(true);
            return;
        }
        super.updateInvisibilityStatus();
    }

    /**
     * While he is inside the ground the route moves him, and nothing else may.
     *
     * <p>Stopping the navigation is not enough on its own: the move control ticks
     * <em>after</em> the goals in the same server tick and would still steer
     * towards whatever it was last told, and gravity and fluid drag would be
     * applied on top. Skipping the whole method is the only place that catches
     * all of it.</p>
     */
    @Override
    public void travel(Vec3 travelVector) {
        if (this.getBurrowState().isBelowGround()) {
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }
        super.travel(travelVector);
    }

    /** Lifts the mole to the first free spot above the ground at its own coordinates. */
    public void pushToSurface(ServerLevel level) {
        BlockPos surface = MoundNetwork.surfaceAt(level, this.getBlockX(), this.getBlockZ());
        this.snapTo(surface.getBottomCenter());
        this.setDeltaMovement(Vec3.ZERO);
    }

    /**
     * Recovery. Runs on every load, including a plain chunk reload, not only a
     * world restart.
     *
     * <p>The burrow state is not saved, so a mole always comes back
     * {@code WANDERING}. What can survive a trip is the mess it leaves: a saved
     * {@code NoGravity} flag and a position two blocks inside solid ground. Both
     * are undone here, because a mole embedded in the terrain never gets out on
     * its own.</p>
     */
    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }

        this.setBurrowState(BurrowState.WANDERING, "loaded");
        this.endUnderground();

        // The shaft he went down is closed by the goal when the trip ends - but
        // a trip cut short by saving and quitting never reaches that point, and
        // the fresh goal after loading knows nothing about it. Without this the
        // crater stands open for good and nothing in the game ever shuts it.
        if (this.openShaft != null) {
            MoleMound.setOpen(level, this.openShaft, false);
            BurrowLog.recovered(this, "closed a shaft left open by an interrupted trip");
            this.openShaft = null;
        }

        // No chunk guard: this hook runs once the entity has entered the level's
        // ticking list, which is after its own chunk is there.
        BlockPos here = this.blockPosition();
        if (level.getBlockState(here).isSolid()) {
            BurrowLog.recovered(this, "loaded inside solid ground");
            this.pushToSurface(level);
        }
    }

    /**
     * Shuts the shaft before the mole is gone for good.
     *
     * <p>A dead mob stops ticking its goals - {@code isImmobile} is true from the
     * moment it starts dying - so the goal's own cleanup never runs. Without this
     * the crater stands open for the rest of the world's life, and killing a mole
     * mid-trip is not exotic: it is what someone does when they want it gone.</p>
     */
    @Override
    public void die(DamageSource cause) {
        this.closeOpenShaft();
        super.die(cause);
    }

    /**
     * The same for a mole that is discarded rather than killed.
     *
     * <p>Deliberately not on a chunk unload or a dimension change: there the
     * saved position is the whole point, and closing the shaft would throw away
     * the record that has to survive.</p>
     */
    @Override
    public void onRemovedFromLevel() {
        RemovalReason reason = this.getRemovalReason();
        if (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED) {
            this.closeOpenShaft();
        }
        super.onRemovedFromLevel();
    }

    private void closeOpenShaft() {
        if (this.openShaft != null && this.level() instanceof ServerLevel level) {
            MoleMound.setOpen(level, this.openShaft, false);
            this.openShaft = null;
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        // The burrow state itself is deliberately not saved - a mole always
        // comes back above ground. This one position is, because it is the only
        // record of a block that has to be put back.
        output.storeNullable("OpenShaft", BlockPos.CODEC, this.openShaft);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.openShaft = input.read("OpenShaft", BlockPos.CODEC).orElse(null);
    }

    /**
     * Remembers which mound is standing open on this mole's account, so it can
     * be closed even if the trip never finishes.
     */
    public void setOpenShaft(@Nullable BlockPos pos) {
        this.openShaft = pos;
    }

    // --- ticking --------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide()) {
            // The poses are cosmetic, so they are driven client side only.
            this.idleAnimationState.startIfStopped(this.tickCount);
            this.updateBurrowAnimations();
            this.updateAnimationPreview();
            this.updatePeek();
            this.updateDigAim();
        } else if ((MoleDebug.forcePeek || MoleDebug.forceDig) && !this.getBurrowState().isBusy()) {
            this.holdStillForTuning();
        }
    }

    /**
     * Keeps the mole where it is while a pose is being judged. Without this the
     * goals keep wandering off and the pose is impossible to look at.
     */
    private void holdStillForTuning() {
        this.getNavigation().stop();
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.0, 1.0, 0.0));
    }

    /**
     * Starts and stops the two one-shot animations from the synched state.
     *
     * <p>Driven from a remembered state rather than from
     * {@code onSyncedDataUpdated}, because that hook does not fire for the value
     * a client receives when it starts tracking an entity - a mole that walks
     * into view halfway through its burrow animation would otherwise never play
     * it. Comparing here covers both cases with one mechanism.</p>
     *
     * <p>Stopping matters as much as starting: a non-looping keyframe animation
     * holds its last frame for good, so the state that follows one has to end
     * it or the mole freezes nose-down.</p>
     */
    private void updateBurrowAnimations() {
        BurrowState state = this.getBurrowState();
        if (state == this.lastAnimatedState) {
            return;
        }

        BurrowState previous = this.lastAnimatedState;
        this.lastAnimatedState = state;

        if (previous == BurrowState.BURROWING) {
            this.burrowAnimationState.stop();
        } else if (previous == BurrowState.EMERGING) {
            this.emergeAnimationState.stop();
        }

        switch (state) {
            case BURROWING -> this.burrowAnimationState.start(this.tickCount);
            case EMERGING -> this.emergeAnimationState.start(this.tickCount);
            case WANDERING -> {
                if (previous == BurrowState.EMERGING) {
                    // mole_emerge ends exactly where the rearing pose sits, and
                    // peekAmount is already most of the way to 1 by then. Handing
                    // straight over to a real peek keeps him up there looking
                    // around instead of sinking the moment the animation stops.
                    this.peekAnimationState.start(this.tickCount);
                    this.peekCooldown = PEEK_INTERVAL;
                    this.peekTicksLeft = PEEK_HOLD;
                }
            }
            default -> {
            }
        }
    }

    /**
     * DEBUG ONLY. Plays {@code mole_burrow} or {@code mole_emerge} once on
     * request from {@code /moleverse dig burrow|emerge}, so the animations stay
     * checkable on a mole that is standing about.
     *
     * <p>{@link MoleDebug} counts requests rather than raising a flag, so every
     * mole in the world sees the same one and none of them consumes it for the
     * others. It steps aside while a real trip is running.</p>
     */
    private void updateAnimationPreview() {
        if (this.getBurrowState().isBusy()) {
            return;
        }

        if (MoleDebug.burrowRequest != this.burrowRequestSeen) {
            this.burrowRequestSeen = MoleDebug.burrowRequest;
            this.burrowAnimationState.start(this.tickCount);
            this.previewBurrowTicks = BurrowConstants.BURROW_TICKS;
        } else if (this.previewBurrowTicks > 0 && --this.previewBurrowTicks == 0) {
            this.burrowAnimationState.stop();
        }

        if (MoleDebug.emergeRequest != this.emergeRequestSeen) {
            this.emergeRequestSeen = MoleDebug.emergeRequest;
            this.emergeAnimationState.start(this.tickCount);
            this.previewEmergeTicks = BurrowConstants.EMERGE_TICKS;
        } else if (this.previewEmergeTicks > 0 && --this.previewEmergeTicks == 0) {
            this.emergeAnimationState.stop();
        }
    }

    /** Blend factor for the dig aim, plus the dig cycle that goes with it. */
    private void updateDigAim() {
        this.digAmountLast = this.digAmount;

        boolean digging = MoleDebug.forceDig || this.getBurrowState().isDigging();
        this.digAmount = Mth.lerp(DIG_BLEND_SPEED, this.digAmount, digging ? 1.0F : 0.0F);

        // Kept scooping while the body angle blends back out, so the paws do not
        // snap to rest under a mole that is still tipped into the ground.
        this.digAnimationState.animateWhen(digging || this.digAmount > 0.01F, this.tickCount);
    }

    private void updatePeek() {
        this.peekAmountLast = this.peekAmount;

        boolean wantsToPeek = this.decideWhetherToPeek();

        if (this.isEmerging()) {
            // Linear, not the usual ease. mole_emerge is authored against a
            // fully reared body, so the blend has to arrive at exactly 1 as the
            // animation ends; easing towards it covers only 0.87 in sixteen
            // ticks and then sags - the very jump this handoff exists to avoid.
            this.peekAmount = Math.min(1.0F, this.peekAmount + 1.0F / BurrowConstants.EMERGE_TICKS);
        } else if (wantsToPeek) {
            this.peekAmount = Mth.lerp(PEEK_BLEND_SPEED, this.peekAmount, 1.0F);
        } else {
            this.peekAmount = Mth.lerp(PEEK_BLEND_SPEED, this.peekAmount, 0.0F);
        }
    }

    /** True while the mole is surfacing, whether for real or for a debug preview. */
    private boolean isEmerging() {
        return this.getBurrowState() == BurrowState.EMERGING || this.previewEmergeTicks > 0;
    }

    private boolean decideWhetherToPeek() {
        // mole_emerge is authored to end in the rearing pose, and that pose is
        // not part of the animation - it is this blend factor. Driving it to 1
        // while the mole surfaces is the handoff; without it the animation and
        // the pose do not chain, they jump. The peek keyframes stay out of the
        // way meanwhile: emerge has its own head and paw motion.
        if (this.isEmerging()) {
            // mole_emerge drives the head, snout and paws itself. Leaving the
            // peek keyframes running would fight it on exactly those bones, and
            // a mole that was mid-peek when it surfaced is the common case, not
            // the rare one.
            this.peekAnimationState.stop();
            return true;
        }

        // Nothing to rear up with while he is inside the ground.
        if (this.getBurrowState().isBusy()) {
            this.peekAnimationState.stop();
            this.peekCooldown = PEEK_DELAY;
            this.peekTicksLeft = 0;
            return false;
        }

        boolean moving = this.getDeltaMovement().horizontalDistanceSqr() > BurrowConstants.STILL_THRESHOLD;

        if (moving) {
            this.peekAnimationState.stop();
            this.peekCooldown = PEEK_DELAY;
            this.peekTicksLeft = 0;
            return false;
        }

        if (this.peekTicksLeft > 0) {
            this.peekTicksLeft--;
            return true;
        }

        if (--this.peekCooldown <= 0) {
            this.peekAnimationState.start(this.tickCount);
            this.peekCooldown = PEEK_INTERVAL;
            this.peekTicksLeft = PEEK_HOLD;
            return true;
        }

        this.peekAnimationState.stop();
        return false;
    }

    /** Blend factor between crawling and fully reared up, interpolated for rendering. */
    public float getPeekAmount(float partialTick) {
        return Mth.lerp(partialTick, this.peekAmountLast, this.peekAmount);
    }

    /** Blend factor between level and fully aimed at the dig direction, interpolated for rendering. */
    public float getDigAmount(float partialTick) {
        return Mth.lerp(partialTick, this.digAmountLast, this.digAmount);
    }

    /**
     * Nothing tempts a mole yet. Once there is a food item worth digging for,
     * this decides what it is and unlocks breeding along with it.
     */
    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return null;
    }

    /** Silent while inside the ground - a sniff from two blocks down gives him away. */
    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return this.getBurrowState().isBelowGround() ? null : ModSounds.MOLE_SNIFF.get();
    }
}
