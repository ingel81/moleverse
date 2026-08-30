package net.sgeht.moleverse.entity.critter;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.sgeht.moleverse.registry.ModBlocks;
import net.sgeht.moleverse.registry.ModSounds;

/**
 * Find a worm larder, sit against it, and if it stays dark, eat it.
 *
 * <p>Written rather than assembled out of {@code MoveToBlockGoal} on purpose,
 * and the reason is one line of that class: it paths to {@code blockPos.above()}
 * and measures arrival from there, because every vanilla user of it stands
 * <em>on</em> its target - turtle eggs, farmland, a beehive's flower. A larder
 * is packed into a wall on five sides. A grub has to end up beside it, not on
 * top of it, and bending the vanilla goal into that shape means overriding the
 * target, the distance, the give-up clock and the stay timer, at which point
 * nothing of it is left. It is also the goal that carries the whole mechanic,
 * so it is the one that should be readable on its own.</p>
 *
 * <h2>The clock is the mechanic</h2>
 *
 * <p>{@link Grub#CHEW_TICKS} of contact with a dark larder, and it goes. The
 * count resets to nothing the moment the larder is lit, rather than pausing:
 * light has to actually save the larder, not merely postpone it, or a player
 * who lights a room comes back to find it eaten anyway and learns the wrong
 * lesson.</p>
 *
 * <h2>Cost</h2>
 *
 * <p>{@link #findLarder()} walks a box of about twelve thousand blocks and is
 * the most expensive thing in these three creatures. It runs at most once every
 * {@link #SEARCH_INTERVAL} ticks per grub, it returns on the first hit so a
 * grub that is already at a larder scans almost nothing, and the grubs that pay
 * the full price are precisely the ones with no larder in range - which
 * {@link Grub#GIVE_UP_TICKS} removes two minutes later. The expensive case is
 * the self-terminating one.</p>
 */
public class EatLarderGoal extends Goal {

    /** How often it is allowed to look, in ticks. */
    private static final int SEARCH_INTERVAL = 100;

    /** How long it will keep trying to reach a larder it cannot get to. */
    private static final int UNREACHABLE_TICKS = 600;

    /**
     * How long a larder that defeated the pathfinder is off the menu.
     *
     * <p>Without this the goal is a loop: give up after
     * {@link #UNREACHABLE_TICKS}, wait out one {@link #SEARCH_INTERVAL}, find
     * the <em>same</em> larder - it is still the nearest - and set off again,
     * forever. A larder sealed behind a wall (buried in a nest trove, walled
     * off by a player) turned every grub near it into a pendulum. One shunned
     * position is enough: the search skips it, finds the next-nearest or
     * nothing, and after a minute the seal gets another honest try.</p>
     */
    private static final int SHUN_TICKS = 1200;

    /** How fast it goes to one. Slightly above its stroll - a grub with a purpose. */
    private static final double APPROACH_SPEED = 1.0;

    private final Grub grub;

    private BlockPos larder;
    private int cooldown;
    private int chewing;
    private int travelling;
    private BlockPos shunned;
    private int shunnedFor;

    public EatLarderGoal(Grub grub) {
        this.grub = grub;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.grub.isFed()) {
            return false;
        }
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        this.cooldown = SEARCH_INTERVAL;
        if (this.shunnedFor > 0) {
            this.shunnedFor -= SEARCH_INTERVAL;
            if (this.shunnedFor <= 0) {
                this.shunned = null;
            }
        }

        this.larder = findLarder();
        if (this.larder == null) {
            return false;
        }
        // Seen one, so the give-up clock goes back to zero even if the grub
        // never manages to reach it. "There are larders here" and "I ate one"
        // are different questions, and only the first one keeps it alive.
        this.grub.noticeLarder();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return !this.grub.isFed()
                && this.larder != null
                && isLarder(this.larder)
                && this.travelling < UNREACHABLE_TICKS;
    }

    @Override
    public void start() {
        this.chewing = 0;
        this.travelling = 0;
        moveToLarder();
    }

    @Override
    public void stop() {
        // Giving up on the way there, not on the chewing: remember which larder
        // beat the pathfinder, so the next search walks past it.
        if (this.travelling >= UNREACHABLE_TICKS && this.larder != null) {
            this.shunned = this.larder;
            this.shunnedFor = SHUN_TICKS;
        }
        this.larder = null;
        this.chewing = 0;
        this.travelling = 0;
        this.grub.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (this.larder == null) {
            return;
        }

        if (!this.larder.closerToCenterThan(this.grub.position(), Grub.REACH)) {
            this.chewing = 0;
            this.travelling++;
            if (this.grub.getNavigation().isDone()) {
                moveToLarder();
            }
            return;
        }

        // Arrived. Face it, so a grub at work is visibly at work rather than
        // standing next to a wall with its back to it.
        this.grub.getNavigation().stop();
        this.grub.getLookControl().setLookAt(
                this.larder.getX() + 0.5, this.larder.getY() + 0.5, this.larder.getZ() + 0.5);

        if (light(this.larder) >= Grub.SAFE_LIGHT) {
            // Lit. Not paused - reset. See the class note.
            this.chewing = 0;
            return;
        }

        if (++this.chewing % 20 == 0) {
            crumbs();
        }
        if (this.chewing >= Grub.CHEW_TICKS) {
            eat();
        }
    }

    private void moveToLarder() {
        this.grub.getNavigation().moveTo(
                this.larder.getX() + 0.5, this.larder.getY(), this.larder.getZ() + 0.5, APPROACH_SPEED);
    }

    /**
     * The nearest larder, searched outwards.
     *
     * <p>Column by column from the grub's own position rather than in raster
     * order, so the first hit is close to the nearest one and the loop usually
     * ends immediately. Vertical first, because the alcoves a larder sits in
     * are wider than they are tall and a grub is far more likely to want the
     * one on its own level.</p>
     */
    private BlockPos findLarder() {
        BlockPos origin = this.grub.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int radius = 0; radius <= Grub.SEARCH_RANGE; radius++) {
            for (int dy = -Grub.SEARCH_HEIGHT; dy <= Grub.SEARCH_HEIGHT; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    // The shell of this radius only - the inside was covered by
                    // an earlier pass. On the two edge columns every dz counts;
                    // between them only the two ends do, so the step jumps
                    // straight across. Testing every cell and skipping the
                    // inside ones instead costs five times the iterations for
                    // the same answer.
                    int step = Math.abs(dx) == radius ? 1 : Math.max(2 * radius, 1);
                    for (int dz = -radius; dz <= radius; dz += step) {
                        cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                        if (isLarder(cursor) && !cursor.equals(this.shunned)) {
                            return cursor.immutable();
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean isLarder(BlockPos pos) {
        return this.grub.level().getBlockState(pos).is(ModBlocks.WORM_LARDER.get());
    }

    private int light(BlockPos pos) {
        return this.grub.level().getBrightness(LightLayer.BLOCK, pos);
    }

    /** A few crumbs off the face while it works, once a second. */
    private void crumbs() {
        if (!(this.grub.level() instanceof ServerLevel server)) {
            return;
        }
        BlockState state = server.getBlockState(this.larder);
        server.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, state, this.larder),
                this.larder.getX() + 0.5, this.larder.getY() + 0.5, this.larder.getZ() + 0.5,
                3, 0.35, 0.35, 0.35, 0.0);

        // The munch is a second and a half long and crumbs() runs once a second,
        // so every third one. Chew, gap, chew is an animal eating; every second
        // is a drone that happens to be at a larder.
        if (this.chewing % 60 == 0) {
            server.playSound(null, this.larder, ModSounds.GRUB_MUNCH.get(),
                    SoundSource.NEUTRAL, 0.35F, 1.0F);
        }
    }

    /**
     * Takes the block, and stops.
     *
     * <p>{@code destroyBlock} with dropping off: the worms in that larder went
     * into the grub, and a larder that showers a player with earthworms as it
     * is eaten would make grubs a farm rather than a threat.</p>
     */
    private void eat() {
        if (!(this.grub.level() instanceof ServerLevel server)) {
            return;
        }
        BlockState state = server.getBlockState(this.larder);
        server.destroyBlock(this.larder, false, this.grub);
        server.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, state, this.larder),
                this.larder.getX() + 0.5, this.larder.getY() + 0.5, this.larder.getZ() + 0.5,
                20, 0.4, 0.4, 0.4, 0.05);
        server.playSound(null, this.larder, SoundEvents.SLIME_SQUISH,
                SoundSource.NEUTRAL, 0.5F, 0.7F);

        this.grub.setFed();
        this.larder = null;
    }
}
