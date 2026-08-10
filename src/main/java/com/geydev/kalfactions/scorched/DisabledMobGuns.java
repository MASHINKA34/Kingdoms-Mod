package com.geydev.kalfactions.scorched;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.outpost.cluster.distribution.ResourceZone;
import com.geydev.kalfactions.territory.WorldZonePolicy;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class DisabledMobGuns {
    public static final String SCGUNS_NAMESPACE = "scguns";
    public static final int SWEEP_INTERVAL_TICKS = 40;
    private static final EquipmentSlot[] HANDS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    private static final Set<String> RAID_TAGS = Set.of("RaidBoss", "RaidHenchman", "RaidMount", "MobGunner");
    private static final Set<ResourceZone> DEFAULT_ZONES = Set.of(ResourceZone.YELLOW);
    private static volatile List<? extends String> cachedZoneNames;
    private static volatile Set<ResourceZone> cachedZones = DEFAULT_ZONES;

    public static boolean isEnabled() {
        return ModConfigSpec.SCORCHED_DISABLE_MOB_GUNS_IN_YELLOW_ZONE.get();
    }

    public static Set<ResourceZone> gunFreeZones() {
        List<? extends String> names = ModConfigSpec.SCORCHED_MOB_GUN_FREE_ZONES.get();
        if (names == cachedZoneNames) {
            return cachedZones;
        }
        EnumSet<ResourceZone> zones = EnumSet.noneOf(ResourceZone.class);
        for (String name : names) {
            for (ResourceZone zone : ResourceZone.values()) {
                if (zone.name().equals(name.trim().toUpperCase(Locale.ROOT))) {
                    zones.add(zone);
                }
            }
        }
        cachedZones = zones;
        cachedZoneNames = names;
        return zones;
    }

    public static boolean isGunFreeZone(ServerLevel level, BlockPos pos) {
        return isEnabled()
                && level.dimension().equals(Level.OVERWORLD)
                && gunFreeZones().contains(WorldZonePolicy.zoneAt(level, pos));
    }

    public static boolean blocksGunnerEquip(PathfinderMob mob) {
        return mob.level() instanceof ServerLevel level && isGunFreeZone(level, mob.blockPosition());
    }

    public static boolean isScorchedGun(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return SCGUNS_NAMESPACE.equals(id.getNamespace()) && ScorchedGunSupport.isGun(stack);
    }

    public static boolean holdsScorchedGun(Mob mob) {
        for (EquipmentSlot hand : HANDS) {
            if (isScorchedGun(mob.getItemBySlot(hand))) {
                return true;
            }
        }
        return false;
    }

    public static boolean disarm(Mob mob) {
        boolean disarmed = false;
        for (EquipmentSlot hand : HANDS) {
            if (isScorchedGun(mob.getItemBySlot(hand))) {
                mob.setItemSlot(hand, ItemStack.EMPTY);
                mob.setDropChance(hand, 0.0F);
                disarmed = true;
            }
        }
        if (disarmed) {
            ScorchedGunSupport.removeGunGoals(mob);
        }
        return disarmed;
    }

    public static boolean isSweepable(Mob mob) {
        return mob instanceof Enemy
                && !SCGUNS_NAMESPACE.equals(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getNamespace())
                && !isRaidMob(mob);
    }

    private static boolean isRaidMob(Mob mob) {
        for (String tag : mob.getTags()) {
            if (RAID_TAGS.contains(tag) || tag.startsWith("raid:")) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.tickCount % SWEEP_INTERVAL_TICKS != 0
                || !(entity instanceof Mob mob)
                || !(mob.level() instanceof ServerLevel level)
                || !isSweepable(mob)
                || !holdsScorchedGun(mob)
                || !isGunFreeZone(level, mob.blockPosition())) {
            return;
        }
        disarm(mob);
    }

    private DisabledMobGuns() {
    }
}
