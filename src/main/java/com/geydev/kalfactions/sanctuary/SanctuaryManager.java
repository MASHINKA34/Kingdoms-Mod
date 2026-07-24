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
    private static final String TAG_AUTOMATIC_CLAIMS = "automaticClaims";
    private static final String TAG_AUTOMATIC_EXCLUSIONS = "automaticExclusions";
    private static final String TAG_AUTOMATIC_INITIALIZED = "automaticInitialized";
    private static final String TAG_AUTOMATIC_ANCHOR = "automaticAnchor";
    private static final String TAG_WORLD_BORDER_INITIALIZED = "worldBorderInitialized";

    private final Set<ClaimKey> manualClaims = new LinkedHashSet<>();
    private final Set<ClaimKey> automaticClaims = new LinkedHashSet<>();
    private final Set<ClaimKey> automaticExclusions = new LinkedHashSet<>();
    private boolean automaticInitialized;
    private BlockPos automaticAnchor;
    private boolean worldBorderInitialized;
    private long revision;

    public static SanctuaryManager get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static SanctuaryManager get(ServerLevel level) {
        return get(Objects.requireNonNull(level, "level").getServer());
    }

    public synchronized Set<ClaimKey> claims() {
        Set<ClaimKey> combined = new LinkedHashSet<>(manualClaims);
        automaticClaims.stream()
                .filter(key -> !automaticExclusions.contains(key))
                .forEach(combined::add);
        return Set.copyOf(combined);
    }

    public synchronized boolean isSanctuary(ClaimKey key) {
        return manualClaims.contains(key)
                || automaticClaims.contains(key) && !automaticExclusions.contains(key);
    }

    public synchronized boolean isSanctuary(Level level, BlockPos pos) {
        return isSanctuary(ClaimKey.of(level, pos));
    }

    public synchronized boolean isSanctuary(Level level, ChunkPos chunk) {
        return isSanctuary(ClaimKey.of(level, chunk));
    }

    public synchronized List<ClaimKey> claimsIn(ResourceKey<Level> dimension) {
        List<ClaimKey> result = new ArrayList<>();
        for (ClaimKey key : claims()) {
            if (key.dimension().equals(dimension)) {
                result.add(key);
            }
        }
        return result;
    }

    public synchronized boolean setClaim(ClaimKey key, boolean claimed) {
        boolean updated;
        if (claimed) {
            if (automaticClaims.contains(key)) {
                updated = automaticExclusions.remove(key);
            } else {
                updated = manualClaims.add(key);
            }
        } else {
            updated = manualClaims.remove(key);
            if (automaticClaims.contains(key)) {
                updated |= automaticExclusions.add(key);
            }
        }
        if (updated) {
            revision++;
            setDirty();
        }
        return updated;
    }

    public synchronized int setClaims(Collection<ClaimKey> keys, boolean claimed) {
        int changed = 0;
        for (ClaimKey key : keys) {
            boolean wasClaimed = isSanctuary(key);
            if (claimed) {
                if (automaticClaims.contains(key)) {
                    automaticExclusions.remove(key);
                } else {
                    manualClaims.add(key);
                }
            } else {
                manualClaims.remove(key);
                if (automaticClaims.contains(key)) {
                    automaticExclusions.add(key);
                }
            }
            if (wasClaimed != isSanctuary(key)) {
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

    public synchronized BlockPos initializeAutomaticSpawn(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        if (!level.dimension().equals(Level.OVERWORLD)) {
            throw new IllegalArgumentException("Automatic spawn sanctuary must be in the overworld");
        }
        if (!worldBorderInitialized) {
            BlockPos spawn = level.getSharedSpawnPos();
            level.getWorldBorder().setCenter(spawn.getX(), spawn.getZ());
            level.getWorldBorder().setSize(
                    com.geydev.kalfactions.config.ModConfigSpec.RESOURCE_WORLD_BORDER_SIZE.getAsInt()
            );
            worldBorderInitialized = true;
            setDirty();
        }
        if (!automaticInitialized) {
            automaticInitialized = true;
            automaticAnchor = findCorePosition(level);
            replaceAutomaticClaims(level.dimension(), automaticAnchor, automaticRadius(), true);
        } else if (automaticAnchor != null) {
            replaceAutomaticClaims(level.dimension(), automaticAnchor, automaticRadius(), false);
        }
        return automaticAnchor;
    }

    public synchronized boolean relocateAutomaticSpawn(ServerLevel level, BlockPos anchor) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(anchor, "anchor");
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return false;
        }
        automaticInitialized = true;
        automaticAnchor = anchor.immutable();
        replaceAutomaticClaims(level.dimension(), automaticAnchor, automaticRadius(), true);
        return true;
    }

    public synchronized boolean clearAutomaticSpawn(ServerLevel level, BlockPos anchor) {
        if (!level.dimension().equals(Level.OVERWORLD)
                || automaticAnchor == null
                || !automaticAnchor.equals(anchor)) {
            return false;
        }
        return clearAutomaticSpawn();
    }

    public synchronized boolean clearAutomaticSpawn() {
        if (automaticAnchor == null && automaticClaims.isEmpty() && automaticExclusions.isEmpty()) {
            return false;
        }
        automaticClaims.clear();
        automaticExclusions.clear();
        automaticAnchor = null;
        automaticInitialized = true;
        revision++;
        setDirty();
        return true;
    }

    public synchronized int clearManualClaims(ResourceKey<Level> dimension) {
        int before = manualClaims.size();
        manualClaims.removeIf(key -> key.dimension().equals(dimension));
        int changed = before - manualClaims.size();
        if (changed > 0) {
            revision++;
            setDirty();
        }
        return changed;
    }

    public synchronized int manualClaimCount(ResourceKey<Level> dimension) {
        return (int) manualClaims.stream()
                .filter(key -> key.dimension().equals(dimension))
                .count();
    }

    public synchronized int automaticClaimCount(ResourceKey<Level> dimension) {
        return (int) automaticClaims.stream()
                .filter(key -> key.dimension().equals(dimension))
                .filter(key -> !automaticExclusions.contains(key))
                .count();
    }

    public synchronized boolean isAutomaticAnchor(ServerLevel level, BlockPos pos) {
        return level.dimension().equals(Level.OVERWORLD)
                && automaticAnchor != null
                && automaticAnchor.equals(pos);
    }

    synchronized boolean replaceAutomaticClaims(
            ResourceKey<Level> dimension,
            BlockPos anchor,
            int radius,
            boolean resetExclusions
    ) {
        Set<ClaimKey> expected = calculateAutomaticClaims(dimension, anchor, radius);
        boolean changed = !automaticClaims.equals(expected);
        automaticClaims.clear();
        automaticClaims.addAll(expected);
        if (resetExclusions) {
            changed |= !automaticExclusions.isEmpty();
            automaticExclusions.clear();
        } else {
            changed |= automaticExclusions.removeIf(key -> !automaticClaims.contains(key));
        }
        if (changed) {
            revision++;
            setDirty();
        }
        return changed;
    }

    static Set<ClaimKey> calculateAutomaticClaims(
            ResourceKey<Level> dimension,
            BlockPos anchor,
            int radius
    ) {
        Set<ClaimKey> claims = new LinkedHashSet<>();
        radius = Math.max(0, radius);
        int minChunkX = Math.floorDiv(anchor.getX() - radius, 16);
        int maxChunkX = Math.floorDiv(anchor.getX() + radius, 16);
        int minChunkZ = Math.floorDiv(anchor.getZ() - radius, 16);
        int maxChunkZ = Math.floorDiv(anchor.getZ() + radius, 16);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                claims.add(new ClaimKey(dimension, chunkX, chunkZ));
            }
        }
        return Set.copyOf(claims);
    }

    private static int automaticRadius() {
        return Math.max(0, com.geydev.kalfactions.config.ModConfigSpec.RESOURCE_BLUE_RADIUS.getAsInt());
    }

    private static BlockPos findCorePosition(ServerLevel level) {
        BlockPos spawn = level.getSharedSpawnPos();
        return level.getBlockState(spawn).canBeReplaced() ? spawn.immutable() : spawn.above().immutable();
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag claimsTag = new ListTag();
        manualClaims.stream()
                .sorted()
                .map(ClaimKey::save)
                .forEach(claimsTag::add);
        tag.put(TAG_CLAIMS, claimsTag);
        ListTag automaticTag = new ListTag();
        automaticClaims.stream()
                .sorted()
                .map(ClaimKey::save)
                .forEach(automaticTag::add);
        tag.put(TAG_AUTOMATIC_CLAIMS, automaticTag);
        ListTag exclusionsTag = new ListTag();
        automaticExclusions.stream()
                .sorted()
                .map(ClaimKey::save)
                .forEach(exclusionsTag::add);
        tag.put(TAG_AUTOMATIC_EXCLUSIONS, exclusionsTag);
        tag.putBoolean(TAG_AUTOMATIC_INITIALIZED, automaticInitialized);
        tag.putBoolean(TAG_WORLD_BORDER_INITIALIZED, worldBorderInitialized);
        if (automaticAnchor != null) {
            tag.putLong(TAG_AUTOMATIC_ANCHOR, automaticAnchor.asLong());
        }
        return tag;
    }

    static SanctuaryManager load(CompoundTag tag, HolderLookup.Provider registries) {
        SanctuaryManager manager = new SanctuaryManager();
        ListTag claimsTag = tag.getList(TAG_CLAIMS, Tag.TAG_COMPOUND);
        for (int index = 0; index < claimsTag.size(); index++) {
            ClaimKey.load(claimsTag.getCompound(index)).ifPresent(manager.manualClaims::add);
        }
        ListTag automaticTag = tag.getList(TAG_AUTOMATIC_CLAIMS, Tag.TAG_COMPOUND);
        for (int index = 0; index < automaticTag.size(); index++) {
            ClaimKey.load(automaticTag.getCompound(index)).ifPresent(manager.automaticClaims::add);
        }
        ListTag exclusionsTag = tag.getList(TAG_AUTOMATIC_EXCLUSIONS, Tag.TAG_COMPOUND);
        for (int index = 0; index < exclusionsTag.size(); index++) {
            ClaimKey.load(exclusionsTag.getCompound(index)).ifPresent(manager.automaticExclusions::add);
        }
        manager.automaticInitialized = tag.getBoolean(TAG_AUTOMATIC_INITIALIZED);
        manager.worldBorderInitialized = tag.getBoolean(TAG_WORLD_BORDER_INITIALIZED);
        manager.automaticAnchor = tag.contains(TAG_AUTOMATIC_ANCHOR)
                ? BlockPos.of(tag.getLong(TAG_AUTOMATIC_ANCHOR))
                : null;
        manager.revision = 1L;
        return manager;
    }
}
