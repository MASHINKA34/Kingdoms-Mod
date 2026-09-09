package com.geydev.kalfactions.protection;

import com.geydev.kalfactions.KalFactions;
import java.util.function.Predicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class BannedMobs {
    public static final TagKey<EntityType<?>> BANNED_MOBS = TagKey.create(
            Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "banned_mobs")
    );
    private static final Predicate<EntityType<?>> TAGGED = type -> type.is(BANNED_MOBS);

    private static volatile Predicate<EntityType<?>> source = TAGGED;

    public static boolean isBanned(Entity entity) {
        return entity instanceof Mob && isBanned(entity.getType());
    }

    public static boolean isBanned(EntityType<?> type) {
        return source.test(type);
    }

    public static void override(Predicate<EntityType<?>> replacement) {
        source = replacement == null ? TAGGED : replacement;
    }

    public static void reset() {
        source = TAGGED;
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && isBanned(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private BannedMobs() {
    }
}
