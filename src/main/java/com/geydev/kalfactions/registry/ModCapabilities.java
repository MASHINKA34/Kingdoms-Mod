package com.geydev.kalfactions.registry;

import com.geydev.kalfactions.KalFactions;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class ModCapabilities {
    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.DRILL.get(),
                (drill, side) -> new InvWrapper(drill) {
                    @Override
                    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                        return stack;
                    }
                }
        );
    }

    private ModCapabilities() {
    }
}
