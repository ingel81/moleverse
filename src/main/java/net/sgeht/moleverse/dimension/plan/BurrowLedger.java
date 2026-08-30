package net.sgeht.moleverse.dimension.plan;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What one burrow chunk has already had done to it, written to disk with the
 * chunk.
 *
 * <p>The other half of {@link BurrowPlan}. The plan says what the burrow ought to
 * contain; this says what this particular sixteen by sixteen column has actually
 * been given, and the difference between the two is the work a reconcile does.
 * That difference is also what makes an existing world migrate itself: a chunk
 * saved before any of this existed carries no ledger at all, which reads as
 * "everything is missing" and is carved into shape the first time it loads.</p>
 *
 * <h2>Two states, not one</h2>
 *
 * <p>A feature is carved into a chunk and then, separately, dressed in it, and
 * the two cannot be one flag - see {@link BurrowFeature#decorateWithin} for why
 * they must not even happen in the same tick. A chunk carved while its
 * neighbours were still solid earth is a chunk that has to be dressed later,
 * when they are not, and it needs to remember precisely that it is owed a
 * dressing.</p>
 *
 * <p>{@link #carved} keeps the hash and {@link #decorated} only the key, because
 * the two answer different questions. The carve asks "is what is in the ground
 * still what the plan says", which needs the fingerprint. The dressing asks "has
 * this been done since it was last cut", and the reconciler answers that by
 * striking a key out of {@link #decorated} whenever it carves that feature
 * again - so a re-dug run loses the dressing that belonged to its old shape and
 * gets one for the new.</p>
 *
 * <h2>Immutable, and replaced rather than edited</h2>
 *
 * <p>A chunk is only written to disk when something marks it unsaved, and an
 * attachment mutated in place marks nothing - {@code AttachmentType}'s own
 * javadoc says so for chunks in as many words. So every change here builds a new
 * ledger, hands it to {@code setData} and marks the chunk, and there is no path
 * that could quietly skip the marking.</p>
 *
 * <p><strong>A codec that cannot read its own file destroys it</strong> - and
 * this one is deliberately the forgiving kind, which {@code ColonyStore}'s is
 * not. Both fields are {@code lenientOptionalFieldOf} with an empty default, so a
 * field that is missing <em>or malformed</em> reads as empty rather than as an
 * error. The difference matters because the two hold different kinds of data: a
 * colony is irreplaceable and a silent empty default there would quietly delete a
 * world's history, while a ledger is derived - the worst an empty one can do is
 * have a chunk carve ground it had already carved. Strict parsing here would
 * throw inside chunk loading instead, and take the whole world down over a record
 * the game can rebuild from the store in a tick.</p>
 */
public record BurrowLedger(Map<String, Integer> carved, Set<String> decorated) {

    /** A chunk nothing has been done to yet, which is also the default value of the attachment. */
    public static final BurrowLedger EMPTY = new BurrowLedger(Map.of(), Set.of());

    /**
     * The stored form.
     *
     * <p>{@link #decorated} is written out sorted. A set has no order of its own,
     * so without this the same ledger could encode to two different byte
     * sequences on two saves and mark chunks as changed that had not changed.</p>
     */
    public static final MapCodec<BurrowLedger> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .lenientOptionalFieldOf("carved", Map.of()).forGetter(BurrowLedger::carved),
            Codec.STRING.listOf()
                    .lenientOptionalFieldOf("decorated", List.of())
                    .forGetter(ledger -> ledger.decorated().stream().sorted().toList()))
            .apply(instance, BurrowLedger::of));

    public BurrowLedger {
        carved = Map.copyOf(carved);
        decorated = Set.copyOf(decorated);
    }

    private static BurrowLedger of(Map<String, Integer> carved, List<String> decorated) {
        return new BurrowLedger(carved, Set.copyOf(decorated));
    }

    /**
     * Whether this feature is in the ground here in exactly the shape the plan
     * currently describes.
     *
     * <p>The hash has to match, not merely the key. A run a mole re-dug at a new
     * depth keeps its key and changes its fingerprint, and that is the whole
     * signal that it wants carving again.</p>
     */
    public boolean isCarved(String key, int hash) {
        Integer applied = this.carved.get(key);
        return applied != null && applied == hash;
    }

    /** Whether this feature has been dressed here since it was last cut. */
    public boolean isDecorated(String key) {
        return this.decorated.contains(key);
    }

    /**
     * Whether this chunk has had nothing done to it.
     *
     * <p>Asked by the attachment type before it writes: there is one of these per
     * chunk and most of the burrow is untouched ground, so an empty ledger is left
     * out of the save file entirely rather than stored as two empty containers.</p>
     */
    public boolean isEmpty() {
        return this.carved.isEmpty() && this.decorated.isEmpty();
    }
}
