package com.geydev.kalfactions.sanctuary;

import com.geydev.kalfactions.claim.ClaimKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public final class SanctuaryManager extends SavedData {
    public static final String DATA_NAME = "kingdoms_sanctuary";
    public static final UUID SANCTUARY_FACTION_ID = new UUID(0x5A4E_4354_5541_5259L, 0x5350_4157_4E5A_4F4EL);
    public static final int SANCTUARY_COLOR = 0x32D6C8;
    public static final Factory<SanctuaryManager> FACTORY =
            new Factory<>(SanctuaryManager::new, SanctuaryManager::load);

    private static final String TAG_CLAIMS = "claims";

    private final Set<ClaimKey> claims = new LinkedHashSet<>();
    private long revision;

    public static SanctuaryManager get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static SanctuaryManager get(ServerLevel level) {
        return get(Objects.requireNonNull(level, "level").getServer());
    }

    public synchronized Set<ClaimKey> claims() {
        return Set.copyOf(claims);
    }

    public synchronized boolean isSanctuary(ClaimKey key) {
        return claims.contains(key);
    }

    public synchronized boolean isSanctuary(Level level, BlockPos pos) {
        return isSanctuary(ClaimKey.of(level, pos));
    }

    public synchronized boolean isSanctuary(Level level, ChunkPos chunk) {
        return isSanctuary(ClaimKey.of(level, chunk));
    }

    public synchronized List<ClaimKey> claimsIn(ResourceKey<Level> dimension) {
        List<ClaimKey> result = new ArrayList<>();
        for (ClaimKey key : claims) {
            if (key.dimension().equals(dimension)) {
                result.add(key);
            }
        }
        return result;
    }

    public synchronized boolean setClaim(ClaimKey key, boolean claimed) {
        boolean updated = claimed ? claims.add(key) : claims.remove(key);
        if (updated) {
            revision++;
            setDirty();
        }
        return updated;
    }

    public synchronized int setClaims(Collection<ClaimKey> keys, boolean claimed) {
        int changed = 0;
        for (ClaimKey key : keys) {
            boolean updated = claimed ? claims.add(key) : claims.remove(key);
            if (updated) {
                changed++;
            }
        }
        if (changed > 0) {
            revision++;
            setDirty();
        }
        return changed;
    }

    public synchronized long revision() {
        return revision;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag claimsTag = new ListTag();
        claims.stream()
                .sorted()
                .map(ClaimKey::save)
                .forEach(claimsTag::add);
        tag.put(TAG_CLAIMS, claimsTag);
        return tag;
    }

    private static SanctuaryManager load(CompoundTag tag, HolderLookup.Provider registries) {
        SanctuaryManager manager = new SanctuaryManager();
        ListTag claimsTag = tag.getList(TAG_CLAIMS, Tag.TAG_COMPOUND);
        for (int index = 0; index < claimsTag.size(); index++) {
            ClaimKey.load(claimsTag.getCompound(index)).ifPresent(manager.claims::add);
        }
        manager.revision = 1L;
        return manager;
    }
}
