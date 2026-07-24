package com.geydev.kalfactions.registry;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.menu.DrillMenu;
import com.geydev.kalfactions.menu.QuarryMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class ModMenuTypes {
    public static final ResourceLocation DRILL_ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "drill");
    public static final DeferredHolder<MenuType<?>, MenuType<DrillMenu>> DRILL =
            DeferredHolder.create(Registries.MENU, DRILL_ID);
    public static final ResourceLocation QUARRY_ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "quarry");
    public static final DeferredHolder<MenuType<?>, MenuType<QuarryMenu>> QUARRY =
            DeferredHolder.create(Registries.MENU, QUARRY_ID);

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.MENU, DRILL_ID, () -> IMenuTypeExtension.create(
                (containerId, playerInventory, extraData) -> new DrillMenu(containerId, playerInventory)
        ));
        event.register(Registries.MENU, QUARRY_ID, () -> IMenuTypeExtension.create(
                (containerId, playerInventory, extraData) ->
                        new QuarryMenu(containerId, playerInventory, extraData.readBlockPos())
        ));
    }

    private ModMenuTypes() {
    }
}
