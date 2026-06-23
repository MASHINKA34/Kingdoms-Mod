package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID, value = Dist.CLIENT)
public final class ClientCombatTooltip {
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        Player player = event.getEntity();
        if (player == null) {
            return;
        }

        ItemStack stack = event.getItemStack();
        int sharpness = sharpnessLevel(stack);
        if (sharpness <= 0) {
            return;
        }

        double baseDamage = mainHandAttackDamage(stack, player);
        if (!Double.isFinite(baseDamage) || baseDamage <= 0.0D) {
            return;
        }

        List<Component> tooltip = event.getToolTip();
        for (int index = 0; index < tooltip.size(); index++) {
            if (isAttackDamageLine(tooltip.get(index))) {
                tooltip.set(index, attackDamageLine(baseDamage + sharpnessBonus(sharpness), player, event.getFlags()));
                return;
            }
        }
    }

    private static int sharpnessLevel(ItemStack stack) {
        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            if (entry.getKey().is(Enchantments.SHARPNESS)) {
                return entry.getIntValue();
            }
        }
        return 0;
    }

    private static double sharpnessBonus(int level) {
        return 1.0D + Math.max(0, level - 1) * 0.5D;
    }

    private static double mainHandAttackDamage(ItemStack stack, Player player) {
        double entityBase = player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
        double[] values = {entityBase, 0.0D, 1.0D};
        stack.forEachModifier(EquipmentSlotGroup.MAINHAND, (attribute, modifier) -> {
            if (!attribute.equals(Attributes.ATTACK_DAMAGE)) {
                return;
            }
            switch (modifier.operation()) {
                case ADD_VALUE -> values[0] += modifier.amount();
                case ADD_MULTIPLIED_BASE -> values[1] += modifier.amount();
                case ADD_MULTIPLIED_TOTAL -> values[2] *= 1.0D + modifier.amount();
            }
        });
        return (values[0] + entityBase * values[1]) * values[2];
    }

    private static Component attackDamageLine(double damage, Player player, TooltipFlag flag) {
        MutableComponent text = Attributes.ATTACK_DAMAGE.value().toBaseComponent(
                damage,
                player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE),
                false,
                flag
        );
        return Component.literal(" ").append(text).withStyle(ChatFormatting.DARK_GREEN);
    }

    private static boolean isAttackDamageLine(Component component) {
        if (component.getContents() instanceof TranslatableContents contents
                && "attribute.modifier.equals.0".equals(contents.getKey())
                && hasAttackDamageArgument(contents)) {
            return true;
        }
        for (Component sibling : component.getSiblings()) {
            if (isAttackDamageLine(sibling)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAttackDamageArgument(TranslatableContents contents) {
        for (Object arg : contents.getArgs()) {
            if (arg instanceof Component component
                    && component.getContents() instanceof TranslatableContents argContents
                    && Attributes.ATTACK_DAMAGE.value().getDescriptionId().equals(argContents.getKey())) {
                return true;
            }
        }
        return false;
    }

    private ClientCombatTooltip() {
    }
}
