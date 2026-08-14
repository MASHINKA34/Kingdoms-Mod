package com.geydev.kalfactions.blackzone;

import com.geydev.kalfactions.KalFactions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;

public final class BlackZoneDamage {
    public static final ResourceKey<DamageType> BLACK_ZONE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "black_zone")
    );

    public static void kill(ServerLevel level, Player player) {
        if (!player.isAlive()) {
            return;
        }
        player.hurt(source(level), Float.MAX_VALUE);
    }

    public static DamageSource source(ServerLevel level) {
        return new DamageSource(level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(BLACK_ZONE));
    }

    private BlackZoneDamage() {
    }
}
