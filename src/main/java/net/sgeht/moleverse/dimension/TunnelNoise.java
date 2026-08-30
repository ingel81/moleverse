package net.sgeht.moleverse.dimension;

/**
 * The dice the burrow decoration rolls, which are not dice.
 *
 * <p>Every value here is a hash of a place and a purpose. Nothing is drawn from a
 * stream, so nothing depends on how many rolls came before it: a stretch of
 * corridor answers the same question the same way on the tenth visit as on the
 * first, and two calls that overlap agree about the ground they share. That is
 * what makes {@link TunnelDecorator} idempotent, and it is the reason this is a
 * class of its own rather than four private methods - the rule is the important
 * part, and a rule is easier to keep when it has somewhere to live.</p>
 *
 * <p>The three integer coordinates are deliberately unnamed. Sometimes they are a
 * block position, sometimes a cell index and an axis, sometimes a cell and a
 * discriminator; what matters is only that the same three numbers with the same
 * salt always give the same answer, and that two different purposes never share a
 * salt.</p>
 */
final class TunnelNoise {

    private TunnelNoise() {
    }

    /**
     * A stable value in {@code [0, 1)} for one purpose at one place.
     *
     * <p>Hand rolled rather than {@code Mth.getSeed}, which is deprecated, and
     * rather than a {@code RandomSource} per position, which would allocate a few
     * hundred objects per decoration call for three bytes of answer each.</p>
     */
    static float at(long salt, int a, int b, int c) {
        long h = salt;
        h = h * 0x9E3779B97F4A7C15L + a;
        h = h * 0x9E3779B97F4A7C15L + b;
        h = h * 0x9E3779B97F4A7C15L + c;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 40) * 0x1.0p-24F;
    }

    /** A whole number in {@code [min, max]}, both ends included. */
    static int intBetween(long salt, int a, int b, int c, int min, int max) {
        return min + (int) (at(salt, a, b, c) * (max - min + 1));
    }

    /** A value anywhere between the two bounds. Used where a family varies by degree rather than by kind. */
    static float floatBetween(long salt, int a, int b, int c, float min, float max) {
        return min + at(salt, a, b, c) * (max - min);
    }

    /**
     * How likely a patch still reaches this far from its middle. Zero past the
     * radius, so a patch has an edge; falling off inside it, so the edge is ragged
     * rather than a circle somebody stamped.
     */
    static float patchChance(float density, int radius, int alongOffset, int acrossOffset) {
        int distanceSquared = alongOffset * alongOffset + acrossOffset * acrossOffset;
        int limit = (radius + 1) * (radius + 1);
        return distanceSquared >= limit ? 0.0F : density * (1.0F - (float) distanceSquared / limit);
    }

    /**
     * Where in this cell along the run the patch sits.
     *
     * <p>Cells rather than a per block chance, because a pool of light has to be a
     * pool: scattering the same number of lit blocks evenly gives an evenly lit
     * corridor, which is the one thing the burrow must not be. The cell index is
     * the only input, so two runs crossing the same cell agree on where their
     * patches go - which nobody can see through solid earth, and which keeps the
     * answer stable when the run drifts sideways.</p>
     *
     * <p>The anchor is kept to the middle half of its cell rather than allowed
     * anywhere in it. Over a full cell two neighbouring anchors can land back to
     * back or a whole cell apart, which turns two pools of light into one blob and
     * then leaves a stretch twice the intended length unlit. Halving the play
     * halves the spread at both ends and costs nothing that anyone can see - and
     * it is what lets the light family promise a hard upper bound on how long a
     * dark stretch can get.</p>
     */
    static int anchorAlong(long salt, int cell, int spacing, int axisId) {
        int play = Math.max(1, spacing / 2);
        return cell * spacing + (spacing - play) / 2 + (int) (at(salt, cell, axisId, 0) * play);
    }

    /** A block or two off the centre line, so that nothing lines up down the middle of a run. */
    static int jitter(long salt, int cell, int axisId, int amount) {
        return (int) (at(salt, cell, axisId, 1) * (amount * 2 + 1)) - amount;
    }
}
