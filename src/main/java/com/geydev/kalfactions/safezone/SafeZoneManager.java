package com.geydev.kalfactions.safezone;

import com.geydev.kalfactions.data.SavedDataFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

public final class SafeZoneManager extends SavedData {
    public static final String DATA_NAME = "kingdoms_safezones";
    public static final int MAX_ZONES = 256;
    public static final int MAX_ID_LENGTH = 32;
    public static final int MAX_SIDE = 512;
    public static final Factory<SafeZoneManager> FACTORY =
            new Factory<>(SafeZoneManager::new, SafeZoneManager::load);

    private static final SavedDataFormat FORMAT = new SavedDataFormat(1);
    private static final String TAG_ZONES = "zones";

    private final Map<String, SafeZone> zones = new LinkedHashMap<>();

    public static SafeZoneManager get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static SafeZoneManager get(ServerLevel level) {
        return get(Objects.requireNonNull(level, "level").getServer());
    }

    public synchronized List<SafeZone> all() {
        return List.copyOf(zones.values());
    }

    public synchronized int count() {
        return zones.size();
    }

    public synchronized Optional<SafeZone> byId(String id) {
        return Optional.ofNullable(zones.get(normalizeId(id)));
    }

    public synchronized Reason add(String id, ResourceKey<Level> dimension, BlockPos first, BlockPos second) {
        String normalized = normalizeId(id);
        if (!isValidId(normalized)) {
            return Reason.INVALID_ID;
        }
        if (zones.containsKey(normalized)) {
            return Reason.DUPLICATE;
        }
        if (zones.size() >= MAX_ZONES) {
            return Reason.TOO_MANY;
        }
        if (!withinLimits(first, second)) {
            return Reason.TOO_LARGE;
        }
        zones.put(normalized, SafeZone.of(normalized, dimension, first, second));
        setDirty();
        return Reason.OK;
    }

    public synchronized boolean remove(String id) {
        if (zones.remove(normalizeId(id)) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    public synchronized boolean isProtected(ResourceKey<Level> dimension, Vec3 position) {
        for (SafeZone zone : zones.values()) {
            if (zone.contains(dimension, position)) {
                return true;
            }
        }
        return false;
    }

    public synchronized boolean isProtected(Level level, BlockPos pos) {
        return isProtected(level.dimension(), Vec3.atCenterOf(pos));
    }

    public static String normalizeId(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        return trimmed.length() > MAX_ID_LENGTH ? trimmed.substring(0, MAX_ID_LENGTH) : trimmed;
    }

    public static boolean isValidId(String id) {
        if (id == null || id.isEmpty() || id.length() > MAX_ID_LENGTH) {
            return false;
        }
        for (int index = 0; index < id.length(); index++) {
            char symbol = id.charAt(index);
            boolean allowed = symbol >= 'a' && symbol <= 'z'
                    || symbol >= '0' && symbol <= '9'
                    || symbol == '_'
                    || symbol == '-';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    private static boolean withinLimits(BlockPos first, BlockPos second) {
        return side(first.getX(), second.getX()) <= MAX_SIDE
                && side(first.getY(), second.getY()) <= MAX_SIDE
                && side(first.getZ(), second.getZ()) <= MAX_SIDE;
    }

    private static long side(int first, int second) {
        return Math.abs((long) first - (long) second) + 1L;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        FORMAT.stamp(tag);
        ListTag list = new ListTag();
        for (SafeZone zone : zones.values()) {
            list.add(zone.save());
        }
        tag.put(TAG_ZONES, list);
        return tag;
    }

    static SafeZoneManager load(CompoundTag saved, HolderLookup.Provider registries) {
        CompoundTag tag = FORMAT.upgrade(saved);
        boolean legacyFormat = FORMAT.outdated(saved);
        SafeZoneManager manager = new SafeZoneManager();
        ListTag list = tag.getList(TAG_ZONES, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            SafeZone.load(list.getCompound(index))
                    .ifPresent(zone -> manager.zones.putIfAbsent(zone.id(), zone));
        }
        if (legacyFormat) {
            manager.setDirty();
        }
        return manager;
    }

    public enum Reason {
        OK,
        INVALID_ID,
        DUPLICATE,
        TOO_MANY,
        TOO_LARGE
    }
}
