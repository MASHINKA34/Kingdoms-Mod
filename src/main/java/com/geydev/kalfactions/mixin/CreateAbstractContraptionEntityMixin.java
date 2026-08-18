package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.protection.MachineProtection;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.StructureTransform;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContraptionEntity.class)
public abstract class CreateAbstractContraptionEntityMixin extends Entity {
    protected CreateAbstractContraptionEntityMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Shadow
    public abstract Contraption getContraption();

    @Shadow
    protected abstract StructureTransform makeStructureTransform();

    @Inject(method = "disassemble", at = @At("HEAD"), cancellable = true)
    private void kingdoms$protectQuarryFromContraptionDisassembly(CallbackInfo ci) {
        Contraption contraption = getContraption();
        if (contraption == null) {
            return;
        }
        StructureTransform transform = makeStructureTransform();
        if (contraption.getBlocks().keySet().stream()
                .map(transform::apply)
                .anyMatch(pos -> !MachineProtection.canContraptionAct(level(), pos))) {
            ci.cancel();
        }
    }
}
