package net.sgeht.moleverse.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;

/**
 * Render state for the mole.
 *
 * <p>Rendering never touches the entity directly. Everything the model needs is
 * copied here once per frame by {@link MoleRenderer#extractRenderState}.</p>
 */
public class MoleRenderState extends LivingEntityRenderState {

    /** Secondary motion of the rearing pose: head sweep, snout twitch, paws. */
    public final AnimationState peekAnimationState = new AnimationState();

    /** Breathing, snout twitch, ear flick. Ambient loop, faded out by speed. */
    public final AnimationState idleAnimationState = new AnimationState();

    /** Alternating paws scooping. Loops while the mole digs, and is aimed by {@link #digAmount}. */
    public final AnimationState digAnimationState = new AnimationState();

    /** Diving in. Plays once. */
    public final AnimationState burrowAnimationState = new AnimationState();

    /** Coming back up. Plays once and ends where the rearing pose sits. */
    public final AnimationState emergeAnimationState = new AnimationState();

    /** 0 while crawling, 1 while fully reared up. Drives the body angle. */
    public float peekAmount;

    /** 0 while level, 1 while fully aimed at the dig direction. */
    public float digAmount;

    /**
     * Where the mole is digging, relative to its own body. Unlike the rearing
     * pose these are not one tuned constant for every mole: two moles dig in
     * different directions at the same time, so the angles travel per entity
     * rather than being read out of {@code MoleDebug} inside the model.
     */
    public float digPitchDegrees;
    public float digYawDegrees;
}
