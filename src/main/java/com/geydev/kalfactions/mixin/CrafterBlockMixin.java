package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.recipe.StatueCraftingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CrafterBlock.class)
public abstract class CrafterBlockMixin {
    @Inject(method = "dispenseFrom", at = @At("HEAD"), cancellable = true)
    private void kingdoms$denyStatueCrafting(BlockState state, ServerLevel level, BlockPos pos, CallbackInfo ci) {
        if (!(level.getBlockEntity(pos) instanceof CrafterBlockEntity crafter)) {
            return;
        }
        boolean statueRecipe = CrafterBlock.getPotentialResults(level, crafter.asCraftInput())
                .map(RecipeHolder::value)
                .filter(StatueCraftingRecipe.class::isInstance)
                .isPresent();
        if (statueRecipe) {
            level.levelEvent(1050, pos, 0);
            ci.cancel();
        }
    }
}
