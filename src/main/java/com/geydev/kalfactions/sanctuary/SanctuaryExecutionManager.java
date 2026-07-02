package com.geydev.kalfactions.sanctuary;

import com.geydev.kalfactions.KalFactions;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class SanctuaryExecutionManager extends SavedData {
    public static final String DATA_NAME = "kingdoms_sanctuary_execution";
    public static final Factory<SanctuaryExecutionManager> FACTORY =
            new Factory<>(SanctuaryExecutionManager::new, SanctuaryExecutionManager::load);

    private static final String TAG_VULNERABLE = "vulnerable";

    private final Set<UUID> vulnerablePlayers = new LinkedHashSet<>();

    public static SanctuaryExecutionManager get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static SanctuaryExecutionManager get(ServerLevel level) {
        return get(Objects.requireNonNull(level, "level").getServer());
    }

    public synchronized boolean setVulnerableUntilDeath(UUID playerId) {
        boolean changed = vulnerablePlayers.add(playerId);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public synchronized boolean isVulnerable(UUID playerId) {
        return vulnerablePlayers.contains(playerId);
    }

    public synchronized boolean clear(UUID playerId) {
        boolean changed = vulnerablePlayers.remove(playerId);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        vulnerablePlayers.stream()
                .sorted()
                .map(NbtUtils::createUUID)
                .forEach(list::add);
        tag.put(TAG_VULNERABLE, list);
        return tag;
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            get(player.serverLevel()).clear(player.getUUID());
        }
    }

    private static SanctuaryExecutionManager load(CompoundTag tag, HolderLookup.Provider registries) {
        SanctuaryExecutionManager manager = new SanctuaryExecutionManager();
        ListTag list = tag.getList(TAG_VULNERABLE, Tag.TAG_INT_ARRAY);
        for (int index = 0; index < list.size(); index++) {
            manager.vulnerablePlayers.add(NbtUtils.loadUUID(list.get(index)));
        }
        return manager;
    }
}
