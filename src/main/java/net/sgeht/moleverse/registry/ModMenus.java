package net.sgeht.moleverse.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.menu.ExchangeStationMenu;

/**
 * Menu types of this mod: the server half of every screen with slots in it.
 *
 * <p>The constructor referenced here is the two-argument one, which is what the
 * client calls when the server tells it to open the menu. Anything a menu needs
 * beyond the player's inventory would have to be sent alongside, through
 * {@code IMenuTypeExtension.create}. Nothing here does - the slots carry
 * everything the station's screen shows.</p>
 */
public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> REGISTER =
            DeferredRegister.create(Registries.MENU, Moleverse.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<ExchangeStationMenu>> EXCHANGE_STATION =
            REGISTER.register(
                    "exchange_station",
                    () -> new MenuType<ExchangeStationMenu>(ExchangeStationMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private ModMenus() {
    }
}
