package net.sgeht.moleverse.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.sgeht.moleverse.Moleverse;

/** Creative mode tabs of this mod. */
public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> REGISTER =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Moleverse.MOD_ID);

    /**
     * Catch-all tab for everything from Moleverse. One tab is enough while the
     * mod is small; split it by theme once there is more content.
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = REGISTER.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + Moleverse.MOD_ID + ".main"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> ModItems.MOLE_PELT.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(ModItems.MOLE_PELT.get());
                        output.accept(ModItems.EARTHWORM.get());
                        output.accept(ModItems.FAT_WORM.get());
                        output.accept(ModItems.GLOW_WORM.get());
                        output.accept(ModItems.WORM_BOX.get());
                        output.accept(ModItems.MOLE_TRAP.get());
                        output.accept(ModItems.MOLE_IN_SACK.get());
                        output.accept(ModItems.LOOSE_SOIL.get());
                        output.accept(ModItems.MOLE_MOUND.get());
                        output.accept(ModItems.PREPARED_MOLE_MOUND.get());
                        output.accept(ModItems.SHAFT_LANTERN.get());
                        output.accept(ModItems.SHRINK_POST.get());
                        output.accept(ModItems.EXCHANGE_STATION.get());
                        output.accept(ModItems.GRUNTING_POST.get());
                        output.accept(ModItems.COLONY_BOARD.get());
                        output.accept(ModItems.WORM_LARDER.get());
                        output.accept(ModItems.ROOT_BEAM.get());
                        output.accept(ModItems.GLOW_MYCELIUM.get());
                        output.accept(ModItems.DEEP_EARTH.get());
                        output.accept(ModItems.MOLE_SPAWN_EGG.get());
                        output.accept(ModItems.GREAT_WORM_SPAWN_EGG.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
