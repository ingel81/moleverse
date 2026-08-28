package net.sgeht.moleverse.entity.burrow;

/**
 * What a mole is doing about digging right now.
 *
 * <pre>
 * WANDERING   --(bored on diggable ground, or fleeing)--&gt; APPROACHING or BURROWING
 * APPROACHING --(reached an existing mound)-------------&gt; BURROWING
 * APPROACHING --(timeout or path exhausted)-------------&gt; BURROWING here, logged
 * BURROWING   --(animation done)------------------------&gt; UNDERGROUND
 * UNDERGROUND --(route finished or invalid)-------------&gt; EMERGING
 * EMERGING    --(animation done)------------------------&gt; WANDERING
 * </pre>
 *
 * <p>The value travels to the client as a byte, because rendering reacts to it:
 * the animations and both body angles are chosen from the state alone, so no
 * further packet is needed to make a mole look like it is digging.</p>
 *
 * <p>It is deliberately <em>not</em> saved. A mole that comes back from disk is
 * {@code WANDERING} and is put back on the surface by the recovery in
 * {@code Mole.onAddedToLevel}; a half-restored trip would be worse than a
 * restarted one.</p>
 */
public enum BurrowState {

    WANDERING,
    APPROACHING,
    BURROWING,
    UNDERGROUND,
    EMERGING;

    private static final BurrowState[] BY_ID = values();

    public byte id() {
        return (byte) this.ordinal();
    }

    /** Falls back to {@link #WANDERING} rather than throwing: this reads network data. */
    public static BurrowState byId(byte id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : WANDERING;
    }

    /** True while the state machine owns the mole and the other goals must keep out. */
    public boolean isBusy() {
        return this != WANDERING;
    }

    /**
     * Damage immunity, decided here rather than through {@code setInvulnerable}.
     * That flag is written to NBT, so a chunk unload halfway through a trip would
     * serialise a mole that is invulnerable for good.
     *
     * <p>It starts at {@code BURROWING}, not at {@code UNDERGROUND}: the burrow
     * animation is a 1.2 second window in which the mole stands still, and
     * fleeing from a threat is exactly when it is used. It ends after
     * {@code EMERGING} for the mirror-image reason - he is still half in the
     * ground while that animation plays.</p>
     */
    public boolean isDamageImmune() {
        return this == BURROWING || this == UNDERGROUND || this == EMERGING;
    }

    /** True while the mole is inside the ground: invisible, without collision or gravity. */
    public boolean isBelowGround() {
        return this == UNDERGROUND;
    }

    /** True while the body is pitched into the ground and the paws are scooping. */
    public boolean isDigging() {
        return this == BURROWING || this == UNDERGROUND;
    }
}
