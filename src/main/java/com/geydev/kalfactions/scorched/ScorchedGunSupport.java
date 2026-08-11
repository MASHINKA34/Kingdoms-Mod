package com.geydev.kalfactions.scorched;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import top.ribs.scguns.entity.ai.GunAttackGoal;
import top.ribs.scguns.item.GunItem;

final class ScorchedGunSupport {
    static boolean isGun(ItemStack stack) {
        return stack.getItem() instanceof GunItem;
    }

    static void removeGunGoals(Mob mob) {
        mob.goalSelector.removeAllGoals(goal -> goal instanceof GunAttackGoal);
    }

    private ScorchedGunSupport() {
    }
}
