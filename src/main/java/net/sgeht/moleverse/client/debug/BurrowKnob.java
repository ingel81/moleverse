package net.sgeht.moleverse.client.debug;

import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * One number the burrow panel can move, and everything needed to move it back.
 *
 * <p>A knob is a pair of lambdas over a static field, plus the four things a
 * slider cannot work out for itself: the range it is allowed to cover, the short
 * name it shows, the name the constant actually has in the source, and the value
 * that constant was compiled with. The last two are what make
 * {@link BurrowKnobs#changedLines()} able to write a line somebody can paste back
 * into the file, which is the whole point of tuning in game rather than in a
 * config: the panel is a way to find a number, never a place to keep one.</p>
 *
 * <p>Everything travels as a {@code double}, including the fields that are
 * {@code int} and {@code float}. One pair of functional interfaces for ninety-odd
 * knobs is worth more than exactness that would only be spent on the cast back,
 * and {@link Kind} carries the type where it matters - rounding while dragging,
 * and the suffix on the pasted literal, because {@code 0.45} without the
 * {@code F} does not compile into a {@code float} field.</p>
 *
 * <p>The shipped value is read once, when the table is built. That happens the
 * first time the panel is opened, before any slider has been touched, so it is
 * the compiled default and not whatever the last session settled on.</p>
 */
final class BurrowKnob {

    /** What the field behind the knob is, which decides rounding and the pasted literal. */
    enum Kind {
        INT,
        FLOAT,
        DOUBLE
    }

    /** Simple name of the class the constant lives in. Half of the pasted line. */
    private final String owner;

    /** The constant's name, spelled exactly as the source spells it. */
    private final String constant;

    /** What the slider says. Short, because the panel is a strip. */
    private final String label;

    private final Kind kind;
    private final double min;
    private final double max;
    private final DoubleSupplier reader;
    private final DoubleConsumer writer;
    private final double shipped;

    private BurrowKnob(String owner, String constant, String label, Kind kind,
            double min, double max, DoubleSupplier reader, DoubleConsumer writer) {
        this.owner = owner;
        this.constant = constant;
        this.label = label;
        this.kind = kind;
        this.min = min;
        this.max = max;
        this.reader = reader;
        this.writer = writer;
        this.shipped = reader.getAsDouble();
    }

    static BurrowKnob ofInt(String owner, String constant, String label, int min, int max,
            DoubleSupplier reader, DoubleConsumer writer) {
        return new BurrowKnob(owner, constant, label, Kind.INT, min, max, reader, writer);
    }

    static BurrowKnob ofFloat(String owner, String constant, String label, double min, double max,
            DoubleSupplier reader, DoubleConsumer writer) {
        return new BurrowKnob(owner, constant, label, Kind.FLOAT, min, max, reader, writer);
    }

    static BurrowKnob ofDouble(String owner, String constant, String label, double min, double max,
            DoubleSupplier reader, DoubleConsumer writer) {
        return new BurrowKnob(owner, constant, label, Kind.DOUBLE, min, max, reader, writer);
    }

    double min() {
        return this.min;
    }

    double max() {
        return this.max;
    }

    double value() {
        return this.reader.getAsDouble();
    }

    /** Where the handle sits for the current value, as a fraction of the track. */
    double fraction() {
        return this.max > this.min ? (value() - this.min) / (this.max - this.min) : 0.0;
    }

    /**
     * Writes a value through, rounded to whatever the field can hold.
     *
     * <p>Returns the rounded value so a slider can snap its handle to it. An
     * integer knob whose handle sits between two steps is a knob that reads one
     * number and applies another, which is the one thing a tuning instrument may
     * never do.</p>
     */
    double set(double raw) {
        double settled = this.kind == Kind.INT ? Math.rint(raw) : raw;
        this.writer.accept(settled);
        return settled;
    }

    void reset() {
        this.writer.accept(this.shipped);
    }

    /**
     * Whether this has moved off the value it was compiled with.
     *
     * <p>The tolerance is a hundredth of the range, which is well under one step
     * of a slider that is a hundred and fifty pixels wide - so a knob nobody
     * touched never reports itself as moved, and a knob moved by one pixel
     * always does.</p>
     */
    boolean moved() {
        return Math.abs(value() - this.shipped) > (this.max - this.min) * 1.0E-4;
    }

    /** What the slider prints on itself. */
    String text() {
        return this.label + ": " + (this.kind == Kind.INT
                ? String.format(Locale.ROOT, "%d", Math.round(value()))
                : String.format(Locale.ROOT, "%.2f", value()));
    }

    /** The line to paste back into the source, with the shipped value beside it. */
    String sourceLine() {
        return String.format(Locale.ROOT, "%s.%s = %s;   // was %s",
                this.owner, this.constant, literal(value()), literal(this.shipped));
    }

    /** What the tooltip says: where the constant lives, and what it used to be. */
    String origin() {
        return this.owner + "." + this.constant + "  (shipped " + literal(this.shipped) + ")";
    }

    private String literal(double v) {
        return switch (this.kind) {
            case INT -> String.format(Locale.ROOT, "%d", Math.round(v));
            case FLOAT -> String.format(Locale.ROOT, "%.3fF", v);
            case DOUBLE -> String.format(Locale.ROOT, "%.3f", v);
        };
    }
}
