package net.sgeht.moleverse.registry;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.dimension.plan.BurrowLedger;

/**
 * Data attachments of this mod.
 *
 * <p>A NeoForge registry rather than a vanilla one, which is the only thing that
 * makes this class look different from its neighbours: the key comes from
 * {@link NeoForgeRegistries.Keys} instead of {@code Registries}. Everything else -
 * one {@code DeferredRegister}, attached in {@code ModRegistries.register} and
 * nowhere else - is the house pattern.</p>
 */
public final class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> REGISTER =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Moleverse.MOD_ID);

    /**
     * What a burrow chunk has already had carved and dressed into it.
     *
     * <p>Server side only in practice - nothing about it is worth sending to a
     * client, which sees the blocks themselves - so there is no sync handler.</p>
     *
     * <p>The write is skipped for an empty ledger. Every chunk of the burrow that
     * is asked for its data gets one, most of them have nothing in them, and a
     * chunk file is not the place to store several thousand empty compounds.</p>
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<BurrowLedger>> BURROW_LEDGER =
            REGISTER.register("burrow_ledger", () -> AttachmentType
                    .builder(() -> BurrowLedger.EMPTY)
                    .serialize(BurrowLedger.CODEC, ledger -> !ledger.isEmpty())
                    .build());

    private ModAttachments() {
    }
}
