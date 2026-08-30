package net.sgeht.moleverse.client.debug;

import java.util.ArrayList;
import java.util.List;

import net.sgeht.moleverse.client.BurrowAmbience;
import net.sgeht.moleverse.client.BurrowScratching;
import net.sgeht.moleverse.dimension.TunnelDecorator;
import net.sgeht.moleverse.dimension.TunnelGrain;

/**
 * Every number {@code /moleverse burrow panel} can reach, in the order it is
 * worth walking through.
 *
 * <p>The table is the panel's whole content: {@link BurrowTunePanel} builds one
 * slider per knob and one heading per section and knows nothing else about the
 * burrow. Adding a dial is therefore one line here, which is the reason this file
 * is a table and not a screen.</p>
 *
 * <h2>Why the sections are in this order</h2>
 *
 * <p>Down the list is roughly outwards from the player. Light first, because
 * whether the burrow is navigable at all is decided there and every other
 * decoration is judged in the light it happens to have. Then the things a corridor
 * is read by - roots, floor, water - then its surfaces, then the grain underneath
 * all of them, which is the dial that moves every family at once and so belongs
 * where somebody who has already looked at the families will find it. The ambience
 * comes last because it is the only part that can be judged standing still.</p>
 *
 * <h2>The split that matters</h2>
 *
 * <p>{@link Section#serverSide()} marks the sections that only mean anything where
 * this client owns the world. Decoration is placed by the server, so a slider in
 * the first eleven sections writes into a static that a real multiplayer client
 * shares with nobody; the ambience sections below them are client code and work
 * anywhere. {@link BurrowTunePanel} greys the first group out rather than letting
 * it move a value that will never leave the machine.</p>
 *
 * <h2>Ranges</h2>
 *
 * <p>The bounds are wide enough to be wrong in both directions - a tuning
 * instrument that cannot reach an ugly value cannot show why the settled one is
 * right - and narrow enough that no slider can produce a division by zero or a
 * pass that never finishes. Every spacing therefore starts at two or more, because
 * each is a divisor in {@code Math.floorDiv}.</p>
 */
final class BurrowKnobs {

    private static final String DECORATOR = "TunnelDecorator";
    private static final String GRAIN = "TunnelGrain";
    private static final String AMBIENCE = "BurrowAmbience";
    private static final String SCRATCHING = "BurrowScratching";

    /** A group of knobs under one heading, and whether it needs a server of ours. */
    record Section(String title, boolean serverSide, List<BurrowKnob> knobs) {
    }

    private static final List<Section> SECTIONS = build();

    private BurrowKnobs() {
    }

    static List<Section> sections() {
        return SECTIONS;
    }

    /** Every knob back to the value it was compiled with. */
    static void reset() {
        for (Section section : SECTIONS) {
            for (BurrowKnob knob : section.knobs()) {
                knob.reset();
            }
        }
    }

    /**
     * One pasteable line per knob that has moved, and nothing for the rest.
     *
     * <p>Only the moved ones, because the point of the list is the edit it turns
     * into. Ninety-nine lines of which four matter is a list nobody reads.</p>
     */
    static List<String> changedLines() {
        List<String> lines = new ArrayList<>();
        for (Section section : SECTIONS) {
            for (BurrowKnob knob : section.knobs()) {
                if (knob.moved()) {
                    lines.add(knob.sourceLine());
                }
            }
        }
        return lines;
    }

    private static List<Section> build() {
        List<Section> sections = new ArrayList<>();

        sections.add(new Section("Light", true, List.of(
                BurrowKnob.ofInt(DECORATOR, "GLOW_SPACING", "pool spacing", 4, 48,
                        () -> TunnelDecorator.GLOW_SPACING, v -> TunnelDecorator.GLOW_SPACING = (int) v),
                BurrowKnob.ofInt(DECORATOR, "GLOW_RADIUS_MIN", "radius min", 0, 6,
                        () -> TunnelDecorator.GLOW_RADIUS_MIN, v -> TunnelDecorator.GLOW_RADIUS_MIN = (int) v),
                BurrowKnob.ofInt(DECORATOR, "GLOW_RADIUS_MAX", "radius max", 1, 8,
                        () -> TunnelDecorator.GLOW_RADIUS_MAX, v -> TunnelDecorator.GLOW_RADIUS_MAX = (int) v),
                BurrowKnob.ofFloat(DECORATOR, "GLOW_DENSITY_MIN", "density min", 0.0, 1.0,
                        () -> TunnelDecorator.GLOW_DENSITY_MIN, v -> TunnelDecorator.GLOW_DENSITY_MIN = (float) v),
                BurrowKnob.ofFloat(DECORATOR, "GLOW_DENSITY_MAX", "density max", 0.0, 1.0,
                        () -> TunnelDecorator.GLOW_DENSITY_MAX, v -> TunnelDecorator.GLOW_DENSITY_MAX = (float) v),
                BurrowKnob.ofFloat(DECORATOR, "GLOW_GRAND_CHANCE", "grand chance", 0.0, 1.0,
                        () -> TunnelDecorator.GLOW_GRAND_CHANCE, v -> TunnelDecorator.GLOW_GRAND_CHANCE = (float) v),
                BurrowKnob.ofInt(DECORATOR, "GLOW_GRAND_RADIUS", "grand radius", 1, 10,
                        () -> TunnelDecorator.GLOW_GRAND_RADIUS, v -> TunnelDecorator.GLOW_GRAND_RADIUS = (int) v),
                BurrowKnob.ofFloat(DECORATOR, "GLOW_GRAND_DENSITY", "grand density", 0.0, 1.0,
                        () -> TunnelDecorator.GLOW_GRAND_DENSITY, v -> TunnelDecorator.GLOW_GRAND_DENSITY = (float) v),
                BurrowKnob.ofFloat(DECORATOR, "GLOW_FRINGE_CHANCE", "threads hang", 0.0, 1.0,
                        () -> TunnelDecorator.GLOW_FRINGE_CHANCE, v -> TunnelDecorator.GLOW_FRINGE_CHANCE = (float) v),
                BurrowKnob.ofInt(DECORATOR, "GLOW_WALL_BLEED_DEPTH", "bleed depth", 0, 5,
                        () -> TunnelDecorator.GLOW_WALL_BLEED_DEPTH, v -> TunnelDecorator.GLOW_WALL_BLEED_DEPTH = (int) v),
                BurrowKnob.ofFloat(DECORATOR, "GLOW_WALL_BLEED_CHANCE", "bleed chance", 0.0, 1.0,
                        () -> TunnelDecorator.GLOW_WALL_BLEED_CHANCE, v -> TunnelDecorator.GLOW_WALL_BLEED_CHANCE = (float) v),
                BurrowKnob.ofFloat(DECORATOR, "GLOW_UNDERFOOT_MOSS_CHANCE", "moss underfoot", 0.0, 1.0,
                        () -> TunnelDecorator.GLOW_UNDERFOOT_MOSS_CHANCE, v -> TunnelDecorator.GLOW_UNDERFOOT_MOSS_CHANCE = (float) v))));

        sections.add(new Section("Roots", true, List.of(
                BurrowKnob.ofInt(DECORATOR, "ROOT_SPACING", "spacing", 2, 40,
                        () -> TunnelDecorator.ROOT_SPACING, v -> TunnelDecorator.ROOT_SPACING = (int) v),
                BurrowKnob.ofFloat(DECORATOR, "ROOT_STANDING_CHANCE", "standing share", 0.0, 1.0,
                        () -> TunnelDecorator.ROOT_STANDING_CHANCE, v -> TunnelDecorator.ROOT_STANDING_CHANCE = (float) v),
                BurrowKnob.ofInt(DECORATOR, "ROOT_HANG_DEPTH", "hang depth", 1, 5,
                        () -> TunnelDecorator.ROOT_HANG_DEPTH, v -> TunnelDecorator.ROOT_HANG_DEPTH = (int) v),
                BurrowKnob.ofInt(DECORATOR, "ROOT_HANG_HALF_WIDTH", "hang width", 0, 3,
                        () -> TunnelDecorator.ROOT_HANG_HALF_WIDTH, v -> TunnelDecorator.ROOT_HANG_HALF_WIDTH = (int) v),
                BurrowKnob.ofFloat(DECORATOR, "ROOT_IN_BARE_CHANCE", "in bare ground", 0.0, 1.0,
                        () -> TunnelDecorator.ROOT_IN_BARE_CHANCE, v -> TunnelDecorator.ROOT_IN_BARE_CHANCE = (float) v))));

        sections.add(new Section("Floor", true, List.of(
                BurrowKnob.ofInt(DECORATOR, "FLOOR_SPACING", "stretch spacing", 2, 24,
                        () -> TunnelDecorator.FLOOR_SPACING, v -> TunnelDecorator.FLOOR_SPACING = (int) v),
                BurrowKnob.ofInt(DECORATOR, "FLOOR_RADIUS", "stretch radius", 1, 6,
                        () -> TunnelDecorator.FLOOR_RADIUS, v -> TunnelDecorator.FLOOR_RADIUS = (int) v),
                BurrowKnob.ofFloat(DECORATOR, "FLOOR_DENSITY", "stretch density", 0.0, 1.0,
                        () -> TunnelDecorator.FLOOR_DENSITY, v -> TunnelDecorator.FLOOR_DENSITY = (float) v),
                BurrowKnob.ofFloat(DECORATOR, "BOULDER_CHANCE", "boulders", 0.0, 1.0,
                        () -> TunnelDecorator.BOULDER_CHANCE, v -> TunnelDecorator.BOULDER_CHANCE = (float) v),
                BurrowKnob.ofFloat(DECORATOR, "MOSS_CARPET_CHANCE", "moss carpet", 0.0, 1.0,
                        () -> TunnelDecorator.MOSS_CARPET_CHANCE, v -> TunnelDecorator.MOSS_CARPET_CHANCE = (float) v))));

        sections.add(new Section("Trodden line", true, List.of(
                BurrowKnob.ofInt(DECORATOR, "PATH_RUN", "stretch length", 2, 20,
                        () -> TunnelDecorator.PATH_RUN, v -> TunnelDecorator.PATH_RUN = (int) v),
                BurrowKnob.ofFloat(DECORATOR, "PATH_CHANCE", "worn share", 0.0, 1.0,
                        () -> TunnelDecorator.PATH_CHANCE, v -> TunnelDecorator.PATH_CHANCE = (float) v),
                BurrowKnob.ofFloat(DECORATOR, "PATH_DENSITY", "packing", 0.0, 1.0,
                        () -> TunnelDecorator.PATH_DENSITY, v -> TunnelDecorator.PATH_DENSITY = (float) v),
                BurrowKnob.ofInt(DECORATOR, "PATH_HALF_WIDTH", "half width", 0, 3,
                        () -> TunnelDecorator.PATH_HALF_WIDTH, v -> TunnelDecorator.PATH_HALF_WIDTH = (int) v))));

        sections.add(new Section("Water", true, List.of(
                BurrowKnob.ofInt(DECORATOR, "PUDDLE_SPACING", "seep spacing", 10, 200,
                        () -> TunnelDecorator.PUDDLE_SPACING, v -> TunnelDecorator.PUDDLE_SPACING = (int) v),
                BurrowKnob.ofInt(DECORATOR, "PUDDLE_RADIUS", "seep radius", 0, 4,
                        () -> TunnelDecorator.PUDDLE_RADIUS, v -> TunnelDecorator.PUDDLE_RADIUS = (int) v),
                BurrowKnob.ofInt(DECORATOR, "SEEP_BANK_REACH", "bank reach", 0, 5,
                        () -> TunnelDecorator.SEEP_BANK_REACH, v -> TunnelDecorator.SEEP_BANK_REACH = (int) v),
                BurrowKnob.ofFloat(DECORATOR, "SEEP_BANK_CHANCE", "bank chance", 0.0, 1.0,
                        () -> TunnelDecorator.SEEP_BANK_CHANCE, v -> TunnelDecorator.SEEP_BANK_CHANCE = (float) v))));

        sections.add(new Section("Walls", true, List.of(
                BurrowKnob.ofInt(DECORATOR, "WALL_VEIN_RUN", "band length", 1, 12,
                        () -> TunnelDecorator.WALL_VEIN_RUN, v -> TunnelDecorator.WALL_VEIN_RUN = (int) v),
                BurrowKnob.ofFloat(DECORATOR, "WALL_VEIN_DENSITY", "band density", 0.0, 1.0,
                        () -> TunnelDecorator.WALL_VEIN_DENSITY, v -> TunnelDecorator.WALL_VEIN_DENSITY = (float) v),
                BurrowKnob.ofFloat(DECORATOR, "WALL_STRAY_CHANCE", "stray blocks", 0.0, 1.0,
                        () -> TunnelDecorator.WALL_STRAY_CHANCE, v -> TunnelDecorator.WALL_STRAY_CHANCE = (float) v),
                BurrowKnob.ofInt(DECORATOR, "WALL_STUB_REACH", "stub reach", 1, 4,
                        () -> TunnelDecorator.WALL_STUB_REACH, v -> TunnelDecorator.WALL_STUB_REACH = (int) v),
                BurrowKnob.ofFloat(DECORATOR, "WALL_MINERAL_CHANCE", "mineral buds", 0.0, 0.5,
                        () -> TunnelDecorator.WALL_MINERAL_CHANCE, v -> TunnelDecorator.WALL_MINERAL_CHANCE = (float) v))));

        sections.add(new Section("Ceiling", true, List.of(
                BurrowKnob.ofInt(DECORATOR, "CEILING_POCKET_CELL", "pocket cell", 1, 10,
                        () -> TunnelDecorator.CEILING_POCKET_CELL, v -> TunnelDecorator.CEILING_POCKET_CELL = (int) v),
                BurrowKnob.ofFloat(DECORATOR, "CEILING_GRIT_DENSITY", "grit density", 0.0, 1.0,
                        () -> TunnelDecorator.CEILING_GRIT_DENSITY, v -> TunnelDecorator.CEILING_GRIT_DENSITY = (float) v),
                BurrowKnob.ofFloat(DECORATOR, "CEILING_ROOT_CHANCE", "dark roots", 0.0, 1.0,
                        () -> TunnelDecorator.CEILING_ROOT_CHANCE, v -> TunnelDecorator.CEILING_ROOT_CHANCE = (float) v))));

        sections.add(new Section("Fringe", true, List.of(
                BurrowKnob.ofInt(DECORATOR, "FRINGE_WIDTH", "width", 1, 3,
                        () -> TunnelDecorator.FRINGE_WIDTH, v -> TunnelDecorator.FRINGE_WIDTH = (int) v),
                BurrowKnob.ofFloat(DECORATOR, "FRINGE_MOSS_SHARE", "moss share", 0.0, 1.0,
                        () -> TunnelDecorator.FRINGE_MOSS_SHARE, v -> TunnelDecorator.FRINGE_MOSS_SHARE = (float) v),
                BurrowKnob.ofFloat(DECORATOR, "FRINGE_RED_SHARE", "red share", 0.0, 1.0,
                        () -> TunnelDecorator.FRINGE_RED_SHARE, v -> TunnelDecorator.FRINGE_RED_SHARE = (float) v),
                BurrowKnob.ofFloat(DECORATOR, "FRINGE_CEILING_SHARE", "crown share", 0.0, 1.0,
                        () -> TunnelDecorator.FRINGE_CEILING_SHARE, v -> TunnelDecorator.FRINGE_CEILING_SHARE = (float) v))));

        sections.add(new Section("Grain mix", true, List.of(
                BurrowKnob.ofInt(GRAIN, "CELL", "cell size", 2, 24,
                        () -> TunnelGrain.CELL, v -> TunnelGrain.CELL = (int) v),
                BurrowKnob.ofFloat(GRAIN, "ROOTY_SHARE", "rooty share", 0.0, 1.0,
                        () -> TunnelGrain.ROOTY_SHARE, v -> TunnelGrain.ROOTY_SHARE = (float) v),
                BurrowKnob.ofFloat(GRAIN, "MOSSY_SHARE", "mossy share", 0.0, 1.0,
                        () -> TunnelGrain.MOSSY_SHARE, v -> TunnelGrain.MOSSY_SHARE = (float) v),
                BurrowKnob.ofFloat(GRAIN, "STONY_SHARE", "stony share", 0.0, 1.0,
                        () -> TunnelGrain.STONY_SHARE, v -> TunnelGrain.STONY_SHARE = (float) v))));

        sections.add(new Section("Grain: surfaces", true, List.of(
                BurrowKnob.ofFloat(GRAIN, "ROOTY_WALL_VEIN", "rooty vein", 0.0, 1.0,
                        () -> TunnelGrain.ROOTY_WALL_VEIN, v -> TunnelGrain.ROOTY_WALL_VEIN = (float) v),
                BurrowKnob.ofFloat(GRAIN, "MOSSY_WALL_VEIN", "mossy vein", 0.0, 1.0,
                        () -> TunnelGrain.MOSSY_WALL_VEIN, v -> TunnelGrain.MOSSY_WALL_VEIN = (float) v),
                BurrowKnob.ofFloat(GRAIN, "STONY_WALL_VEIN", "stony vein", 0.0, 1.0,
                        () -> TunnelGrain.STONY_WALL_VEIN, v -> TunnelGrain.STONY_WALL_VEIN = (float) v),
                BurrowKnob.ofFloat(GRAIN, "BARE_WALL_VEIN", "bare vein", 0.0, 1.0,
                        () -> TunnelGrain.BARE_WALL_VEIN, v -> TunnelGrain.BARE_WALL_VEIN = (float) v),
                BurrowKnob.ofFloat(GRAIN, "ROOTY_CEILING_GRIT", "rooty grit", 0.0, 1.0,
                        () -> TunnelGrain.ROOTY_CEILING_GRIT, v -> TunnelGrain.ROOTY_CEILING_GRIT = (float) v),
                BurrowKnob.ofFloat(GRAIN, "MOSSY_CEILING_GRIT", "mossy grit", 0.0, 1.0,
                        () -> TunnelGrain.MOSSY_CEILING_GRIT, v -> TunnelGrain.MOSSY_CEILING_GRIT = (float) v),
                BurrowKnob.ofFloat(GRAIN, "STONY_CEILING_GRIT", "stony grit", 0.0, 1.0,
                        () -> TunnelGrain.STONY_CEILING_GRIT, v -> TunnelGrain.STONY_CEILING_GRIT = (float) v),
                BurrowKnob.ofFloat(GRAIN, "BARE_CEILING_GRIT", "bare grit", 0.0, 1.0,
                        () -> TunnelGrain.BARE_CEILING_GRIT, v -> TunnelGrain.BARE_CEILING_GRIT = (float) v))));

        sections.add(new Section("Grain: growth", true, List.of(
                BurrowKnob.ofFloat(GRAIN, "ROOTY_ROOT_STUB", "rooty stubs", 0.0, 0.5,
                        () -> TunnelGrain.ROOTY_ROOT_STUB, v -> TunnelGrain.ROOTY_ROOT_STUB = (float) v),
                BurrowKnob.ofFloat(GRAIN, "MOSSY_ROOT_STUB", "mossy stubs", 0.0, 0.5,
                        () -> TunnelGrain.MOSSY_ROOT_STUB, v -> TunnelGrain.MOSSY_ROOT_STUB = (float) v),
                BurrowKnob.ofFloat(GRAIN, "STONY_ROOT_STUB", "stony stubs", 0.0, 0.5,
                        () -> TunnelGrain.STONY_ROOT_STUB, v -> TunnelGrain.STONY_ROOT_STUB = (float) v),
                BurrowKnob.ofFloat(GRAIN, "BARE_ROOT_STUB", "bare stubs", 0.0, 0.5,
                        () -> TunnelGrain.BARE_ROOT_STUB, v -> TunnelGrain.BARE_ROOT_STUB = (float) v),
                BurrowKnob.ofFloat(GRAIN, "ROOTY_FRINGE", "rooty fringe", 0.0, 1.0,
                        () -> TunnelGrain.ROOTY_FRINGE, v -> TunnelGrain.ROOTY_FRINGE = (float) v),
                BurrowKnob.ofFloat(GRAIN, "MOSSY_FRINGE", "mossy fringe", 0.0, 1.0,
                        () -> TunnelGrain.MOSSY_FRINGE, v -> TunnelGrain.MOSSY_FRINGE = (float) v),
                BurrowKnob.ofFloat(GRAIN, "STONY_FRINGE", "stony fringe", 0.0, 1.0,
                        () -> TunnelGrain.STONY_FRINGE, v -> TunnelGrain.STONY_FRINGE = (float) v),
                BurrowKnob.ofFloat(GRAIN, "BARE_FRINGE", "bare fringe", 0.0, 1.0,
                        () -> TunnelGrain.BARE_FRINGE, v -> TunnelGrain.BARE_FRINGE = (float) v))));

        sections.add(new Section("Motes", false, List.of(
                BurrowKnob.ofInt(AMBIENCE, "MOTE_ONE_IN", "mote 1 in", 1, 60,
                        () -> BurrowAmbience.MOTE_ONE_IN, v -> BurrowAmbience.MOTE_ONE_IN = (int) v),
                BurrowKnob.ofDouble(AMBIENCE, "MOTE_RADIUS", "mote radius", 1.0, 16.0,
                        () -> BurrowAmbience.MOTE_RADIUS, v -> BurrowAmbience.MOTE_RADIUS = v),
                BurrowKnob.ofDouble(AMBIENCE, "MOTE_DROP_GAP", "mote drop gap", 0.0, 1.0,
                        () -> BurrowAmbience.MOTE_DROP_GAP, v -> BurrowAmbience.MOTE_DROP_GAP = v),
                BurrowKnob.ofInt(AMBIENCE, "DRIP_ONE_IN", "drip 1 in", 1, 400,
                        () -> BurrowAmbience.DRIP_ONE_IN, v -> BurrowAmbience.DRIP_ONE_IN = (int) v),
                BurrowKnob.ofDouble(AMBIENCE, "DRIP_RADIUS", "drip radius", 1.0, 16.0,
                        () -> BurrowAmbience.DRIP_RADIUS, v -> BurrowAmbience.DRIP_RADIUS = v),
                BurrowKnob.ofInt(AMBIENCE, "REFUGE_LIGHT", "refuge light", 0, 15,
                        () -> BurrowAmbience.REFUGE_LIGHT, v -> BurrowAmbience.REFUGE_LIGHT = (int) v),
                BurrowKnob.ofInt(AMBIENCE, "CEILING_REACH", "ceiling reach", 1, 24,
                        () -> BurrowAmbience.CEILING_REACH, v -> BurrowAmbience.CEILING_REACH = (int) v))));

        sections.add(new Section("Spores", false, List.of(
                BurrowKnob.ofInt(AMBIENCE, "SPORE_ONE_IN", "attempt 1 in", 1, 60,
                        () -> BurrowAmbience.SPORE_ONE_IN, v -> BurrowAmbience.SPORE_ONE_IN = (int) v),
                BurrowKnob.ofInt(AMBIENCE, "SPORE_PROBES", "darts per try", 1, 12,
                        () -> BurrowAmbience.SPORE_PROBES, v -> BurrowAmbience.SPORE_PROBES = (int) v),
                BurrowKnob.ofDouble(AMBIENCE, "SPORE_RADIUS", "dart radius", 0.5, 10.0,
                        () -> BurrowAmbience.SPORE_RADIUS, v -> BurrowAmbience.SPORE_RADIUS = v))));

        sections.add(new Section("Ambient sound", false, List.of(
                BurrowKnob.ofInt(AMBIENCE, "SOUND_MIN_DELAY", "min delay", 20, 1200,
                        () -> BurrowAmbience.SOUND_MIN_DELAY, v -> BurrowAmbience.SOUND_MIN_DELAY = (int) v),
                BurrowKnob.ofInt(AMBIENCE, "SOUND_DELAY_SPREAD", "delay spread", 1, 1200,
                        () -> BurrowAmbience.SOUND_DELAY_SPREAD, v -> BurrowAmbience.SOUND_DELAY_SPREAD = (int) v),
                BurrowKnob.ofDouble(AMBIENCE, "SOUND_MIN_DISTANCE", "min distance", 0.0, 24.0,
                        () -> BurrowAmbience.SOUND_MIN_DISTANCE, v -> BurrowAmbience.SOUND_MIN_DISTANCE = v),
                BurrowKnob.ofDouble(AMBIENCE, "SOUND_DISTANCE_SPREAD", "distance spread", 0.0, 24.0,
                        () -> BurrowAmbience.SOUND_DISTANCE_SPREAD, v -> BurrowAmbience.SOUND_DISTANCE_SPREAD = v),
                BurrowKnob.ofDouble(AMBIENCE, "SOUND_VERTICAL_SPREAD", "height spread", 0.0, 12.0,
                        () -> BurrowAmbience.SOUND_VERTICAL_SPREAD, v -> BurrowAmbience.SOUND_VERTICAL_SPREAD = v))));

        sections.add(new Section("Scratching: bursts", false, List.of(
                BurrowKnob.ofInt(SCRATCHING, "BURST_MIN_DELAY", "min delay", 20, 3000,
                        () -> BurrowScratching.BURST_MIN_DELAY, v -> BurrowScratching.BURST_MIN_DELAY = (int) v),
                BurrowKnob.ofInt(SCRATCHING, "BURST_DELAY_SPREAD", "delay spread", 1, 3000,
                        () -> BurrowScratching.BURST_DELAY_SPREAD, v -> BurrowScratching.BURST_DELAY_SPREAD = (int) v),
                BurrowKnob.ofInt(SCRATCHING, "BURST_RETRY_DELAY", "retry delay", 1, 200,
                        () -> BurrowScratching.BURST_RETRY_DELAY, v -> BurrowScratching.BURST_RETRY_DELAY = (int) v),
                BurrowKnob.ofInt(SCRATCHING, "SCRATCH_MIN_COUNT", "strokes min", 1, 8,
                        () -> BurrowScratching.SCRATCH_MIN_COUNT, v -> BurrowScratching.SCRATCH_MIN_COUNT = (int) v),
                BurrowKnob.ofInt(SCRATCHING, "SCRATCH_COUNT_SPREAD", "strokes spread", 1, 8,
                        () -> BurrowScratching.SCRATCH_COUNT_SPREAD, v -> BurrowScratching.SCRATCH_COUNT_SPREAD = (int) v),
                BurrowKnob.ofInt(SCRATCHING, "SCRATCH_MIN_GAP", "stroke gap", 1, 80,
                        () -> BurrowScratching.SCRATCH_MIN_GAP, v -> BurrowScratching.SCRATCH_MIN_GAP = (int) v),
                BurrowKnob.ofInt(SCRATCHING, "SCRATCH_GAP_SPREAD", "gap spread", 1, 80,
                        () -> BurrowScratching.SCRATCH_GAP_SPREAD, v -> BurrowScratching.SCRATCH_GAP_SPREAD = (int) v),
                BurrowKnob.ofInt(SCRATCHING, "OVERHEAD_ONE_IN", "overhead 1 in", 1, 12,
                        () -> BurrowScratching.OVERHEAD_ONE_IN, v -> BurrowScratching.OVERHEAD_ONE_IN = (int) v))));

        sections.add(new Section("Scratching: aim", false, List.of(
                BurrowKnob.ofInt(SCRATCHING, "PROBE_REACH", "probe reach", 1, 24,
                        () -> BurrowScratching.PROBE_REACH, v -> BurrowScratching.PROBE_REACH = (int) v),
                BurrowKnob.ofDouble(SCRATCHING, "AIM_HEIGHT_SPREAD", "aim height", 0.0, 4.0,
                        () -> BurrowScratching.AIM_HEIGHT_SPREAD, v -> BurrowScratching.AIM_HEIGHT_SPREAD = v),
                BurrowKnob.ofInt(SCRATCHING, "SCRATCH_MIN_DEPTH", "depth min", 1, 8,
                        () -> BurrowScratching.SCRATCH_MIN_DEPTH, v -> BurrowScratching.SCRATCH_MIN_DEPTH = (int) v),
                BurrowKnob.ofInt(SCRATCHING, "SCRATCH_DEPTH_SPREAD", "depth spread", 1, 8,
                        () -> BurrowScratching.SCRATCH_DEPTH_SPREAD, v -> BurrowScratching.SCRATCH_DEPTH_SPREAD = (int) v),
                BurrowKnob.ofDouble(SCRATCHING, "SCRATCH_DRIFT", "drift", 0.0, 1.5,
                        () -> BurrowScratching.SCRATCH_DRIFT, v -> BurrowScratching.SCRATCH_DRIFT = v),
                BurrowKnob.ofDouble(SCRATCHING, "SCRATCH_JITTER", "jitter", 0.0, 1.0,
                        () -> BurrowScratching.SCRATCH_JITTER, v -> BurrowScratching.SCRATCH_JITTER = v))));

        sections.add(new Section("Scratching: sound", false, List.of(
                BurrowKnob.ofFloat(SCRATCHING, "SCRATCH_VOLUME", "paw volume", 0.0, 1.0,
                        () -> BurrowScratching.SCRATCH_VOLUME, v -> BurrowScratching.SCRATCH_VOLUME = (float) v),
                BurrowKnob.ofFloat(SCRATCHING, "SCRATCH_MIN_PITCH", "paw pitch", 0.2, 2.0,
                        () -> BurrowScratching.SCRATCH_MIN_PITCH, v -> BurrowScratching.SCRATCH_MIN_PITCH = (float) v),
                BurrowKnob.ofFloat(SCRATCHING, "SCRATCH_PITCH_SPREAD", "pitch spread", 0.0, 1.0,
                        () -> BurrowScratching.SCRATCH_PITCH_SPREAD, v -> BurrowScratching.SCRATCH_PITCH_SPREAD = (float) v),
                BurrowKnob.ofFloat(SCRATCHING, "GRIT_VOLUME", "grit volume", 0.0, 1.0,
                        () -> BurrowScratching.GRIT_VOLUME, v -> BurrowScratching.GRIT_VOLUME = (float) v),
                BurrowKnob.ofFloat(SCRATCHING, "GRIT_PITCH_FACTOR", "grit pitch x", 0.5, 1.5,
                        () -> BurrowScratching.GRIT_PITCH_FACTOR, v -> BurrowScratching.GRIT_PITCH_FACTOR = (float) v),
                BurrowKnob.ofInt(SCRATCHING, "SHOWER_MIN", "soil min", 0, 10,
                        () -> BurrowScratching.SHOWER_MIN, v -> BurrowScratching.SHOWER_MIN = (int) v),
                BurrowKnob.ofInt(SCRATCHING, "SHOWER_SPREAD", "soil spread", 1, 10,
                        () -> BurrowScratching.SHOWER_SPREAD, v -> BurrowScratching.SHOWER_SPREAD = (int) v),
                BurrowKnob.ofDouble(SCRATCHING, "SHOWER_DROP_GAP", "soil drop gap", 0.0, 1.0,
                        () -> BurrowScratching.SHOWER_DROP_GAP, v -> BurrowScratching.SHOWER_DROP_GAP = v))));

        return List.copyOf(sections);
    }
}
