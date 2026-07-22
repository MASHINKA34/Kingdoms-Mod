package com.geydev.kalfactions.outpost.trader;

import com.geydev.kalfactions.claim.ClaimKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public final class TraderWorldData extends SavedData {
    public static final int FORMAT_VERSION = 2;
    public static final int MAX_POINTS = 128;
    public static final int MAX_WANDERING_EVENTS = 2048;
    public static final int MAX_ROLLED_OFFERS = 16;
    private static final String DATA_NAME = "kingdoms_trader_world";
    private static final SavedData.Factory<TraderWorldData> FACTORY =
            new SavedData.Factory<>(TraderWorldData::new, TraderWorldData::load);

    private final Map<UUID, SpawnPoint> points = new LinkedHashMap<>();
    private final Map<UUID, WanderingEvent> wandering = new LinkedHashMap<>();
    private ActiveContraband contraband;
    private long contrabandCooldownUntil;
    private long wanderingNextRollAt;
    private int wanderingRollCursor;

    public static TraderWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public synchronized List<SpawnPoint> points() {
        return points.values().stream().sorted(Comparator.comparing(SpawnPoint::id)).toList();
    }

    public synchronized Optional<SpawnPoint> point(UUID id) {
        return Optional.ofNullable(points.get(id));
    }

    public synchronized AddPointResult addPoint(ResourceKey<Level> dimension, BlockPos pos, float yaw) {
        if (points.size() >= MAX_POINTS) {
            return new AddPointResult(null, false);
        }
        SpawnPoint point = new SpawnPoint(UUID.randomUUID(), dimension, pos, yaw);
        points.put(point.id(), point);
        setDirty();
        return new AddPointResult(point, true);
    }

    public synchronized boolean addPoint(SpawnPoint point) {
        if (point == null || points.size() >= MAX_POINTS || points.containsKey(point.id())) {
            return false;
        }
        points.put(point.id(), point);
        setDirty();
        return true;
    }

    public synchronized boolean removePoint(UUID id) {
        if (points.remove(id) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    public synchronized Optional<SpawnPoint> nearestPoint(ResourceKey<Level> dimension, BlockPos pos, double radius) {
        double radiusSquared = Math.max(0.0D, radius) * Math.max(0.0D, radius);
        return points.values().stream()
                .filter(point -> point.dimension().equals(dimension))
                .filter(point -> point.pos().distSqr(pos) <= radiusSquared)
                .min(Comparator.comparingDouble(point -> point.pos().distSqr(pos)));
    }

    public synchronized Optional<ActiveContraband> contraband() {
        return Optional.ofNullable(contraband);
    }

    public synchronized long contrabandCooldownUntil() {
        return contrabandCooldownUntil;
    }

    public synchronized long wanderingNextRollAt() {
        return wanderingNextRollAt;
    }

    public synchronized void setWanderingNextRollAt(long value) {
        wanderingNextRollAt = Math.max(0L, value);
        setDirty();
    }

    public synchronized int wanderingRollCursor() {
        return wanderingRollCursor;
    }

    public synchronized void setWanderingRollCursor(int value) {
        wanderingRollCursor = Math.max(0, value);
        setDirty();
    }

    public synchronized boolean beginContraband(ActiveContraband active) {
        if (active == null || contraband != null) {
            return false;
        }
        contraband = active;
        setDirty();
        return true;
    }

    public synchronized void clearContraband(long cooldownUntil) {
        contraband = null;
        contrabandCooldownUntil = Math.max(contrabandCooldownUntil, cooldownUntil);
        setDirty();
    }

    public synchronized boolean cancelContraband(UUID eventId) {
        if (contraband == null || !contraband.eventId().equals(eventId)) {
            return false;
        }
        contraband = null;
        setDirty();
        return true;
    }

    public synchronized Optional<WanderingEvent> wandering(UUID factionId) {
        return Optional.ofNullable(wandering.get(factionId));
    }

    public synchronized List<WanderingEvent> wanderingEvents() {
        return List.copyOf(wandering.values());
    }

    public synchronized boolean putWandering(WanderingEvent event) {
        if (event == null || (!wandering.containsKey(event.factionId()) && wandering.size() >= MAX_WANDERING_EVENTS)) {
            return false;
        }
        wandering.put(event.factionId(), event);
        setDirty();
        return true;
    }

    public synchronized void finishWandering(UUID factionId, long cooldownUntil) {
        WanderingEvent current = wandering.get(factionId);
        if (current == null) {
            wandering.put(factionId, WanderingEvent.cooldown(factionId, cooldownUntil));
        } else {
            wandering.put(factionId, current.asCooldown(cooldownUntil));
        }
        setDirty();
    }

    public synchronized boolean removeWandering(UUID factionId) {
        if (wandering.remove(factionId) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("formatVersion", FORMAT_VERSION);
        tag.putLong("contrabandCooldownUntil", contrabandCooldownUntil);
        tag.putLong("wanderingNextRollAt", wanderingNextRollAt);
        tag.putInt("wanderingRollCursor", wanderingRollCursor);
        ListTag pointTags = new ListTag();
        for (SpawnPoint point : points.values()) {
            pointTags.add(point.save());
        }
        tag.put("points", pointTags);
        if (contraband != null) {
            tag.put("contraband", contraband.save());
        }
        ListTag wanderingTags = new ListTag();
        for (WanderingEvent event : wandering.values()) {
            wanderingTags.add(event.save());
        }
        tag.put("wandering", wanderingTags);
        return tag;
    }

    static TraderWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        TraderWorldData data = new TraderWorldData();
        boolean repaired = tag.getInt("formatVersion") != FORMAT_VERSION;
        data.contrabandCooldownUntil = Math.max(0L, tag.getLong("contrabandCooldownUntil"));
        data.wanderingNextRollAt = Math.max(0L, tag.getLong("wanderingNextRollAt"));
        data.wanderingRollCursor = Math.max(0, tag.getInt("wanderingRollCursor"));
        ListTag points = tag.getList("points", Tag.TAG_COMPOUND);
        for (int index = 0; index < Math.min(points.size(), MAX_POINTS); index++) {
            Optional<SpawnPoint> loaded = SpawnPoint.load(points.getCompound(index));
            if (loaded.isPresent() && !data.points.containsKey(loaded.get().id())) {
                data.points.put(loaded.get().id(), loaded.get());
            } else {
                repaired = true;
            }
        }
        if (points.size() > MAX_POINTS) {
            repaired = true;
        }
        if (tag.contains("contraband", Tag.TAG_COMPOUND)) {
            data.contraband = ActiveContraband.load(tag.getCompound("contraband")).orElse(null);
            repaired |= data.contraband == null;
        }
        ListTag wandering = tag.getList("wandering", Tag.TAG_COMPOUND);
        for (int index = 0; index < Math.min(wandering.size(), MAX_WANDERING_EVENTS); index++) {
            Optional<WanderingEvent> loaded = WanderingEvent.load(wandering.getCompound(index));
            if (loaded.isPresent() && !data.wandering.containsKey(loaded.get().factionId())) {
                data.wandering.put(loaded.get().factionId(), loaded.get());
            } else {
                repaired = true;
            }
        }
        if (wandering.size() > MAX_WANDERING_EVENTS) {
            repaired = true;
        }
        if (repaired) {
            data.setDirty();
        }
        return data;
    }

    public record AddPointResult(SpawnPoint point, boolean added) {
    }

    public record SpawnPoint(UUID id, ResourceKey<Level> dimension, BlockPos pos, float yaw) {
        public SpawnPoint {
            if (id == null || dimension == null || pos == null || !Float.isFinite(yaw)) {
                throw new IllegalArgumentException("Invalid trader spawn point");
            }
            pos = pos.immutable();
            yaw = Math.clamp(yaw, -360.0F, 360.0F);
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", id);
            tag.putString("dimension", dimension.location().toString());
            tag.putLong("pos", pos.asLong());
            tag.putFloat("yaw", yaw);
            return tag;
        }

        private static Optional<SpawnPoint> load(CompoundTag tag) {
            if (!tag.hasUUID("id") || !tag.contains("pos", Tag.TAG_LONG)) {
                return Optional.empty();
            }
            ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString("dimension"));
            float yaw = tag.getFloat("yaw");
            if (dimensionId == null || !Float.isFinite(yaw)) {
                return Optional.empty();
            }
            return Optional.of(new SpawnPoint(
                    tag.getUUID("id"),
                    ResourceKey.create(Registries.DIMENSION, dimensionId),
                    BlockPos.of(tag.getLong("pos")),
                    yaw
            ));
        }
    }

    public record ActiveContraband(
            UUID eventId,
            UUID entityId,
            UUID pointId,
            ResourceKey<Level> dimension,
            BlockPos pos,
            long expiresAt
    ) {
        public ActiveContraband {
            if (eventId == null || entityId == null || pointId == null || dimension == null || pos == null) {
                throw new IllegalArgumentException("Invalid contraband event");
            }
            pos = pos.immutable();
            expiresAt = Math.max(0L, expiresAt);
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("eventId", eventId);
            tag.putUUID("entityId", entityId);
            tag.putUUID("pointId", pointId);
            tag.putString("dimension", dimension.location().toString());
            tag.putLong("pos", pos.asLong());
            tag.putLong("expiresAt", expiresAt);
            return tag;
        }

        private static Optional<ActiveContraband> load(CompoundTag tag) {
            if (!tag.hasUUID("eventId") || !tag.hasUUID("entityId") || !tag.hasUUID("pointId")
                    || !tag.contains("pos", Tag.TAG_LONG) || !tag.contains("expiresAt", Tag.TAG_LONG)) {
                return Optional.empty();
            }
            ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString("dimension"));
            if (dimensionId == null) {
                return Optional.empty();
            }
            return Optional.of(new ActiveContraband(
                    tag.getUUID("eventId"), tag.getUUID("entityId"), tag.getUUID("pointId"),
                    ResourceKey.create(Registries.DIMENSION, dimensionId), BlockPos.of(tag.getLong("pos")),
                    tag.getLong("expiresAt")
            ));
        }
    }

    public record RolledOffer(String id, long price) {
        public RolledOffer {
            if (id == null || id.isBlank() || id.length() > 64 || price < 0L) {
                throw new IllegalArgumentException("Invalid wandering offer");
            }
        }
    }

    public record WanderingEvent(
            UUID factionId,
            UUID eventId,
            UUID entityId,
            ClaimKey claim,
            BlockPos pos,
            List<RolledOffer> offers,
            long expiresAt,
            long cooldownUntil
    ) {
        public WanderingEvent {
            if (factionId == null || claim == null || pos == null || offers == null
                    || offers.size() > MAX_ROLLED_OFFERS) {
                throw new IllegalArgumentException("Invalid wandering event");
            }
            pos = pos.immutable();
            offers = List.copyOf(offers);
            expiresAt = Math.max(0L, expiresAt);
            cooldownUntil = Math.max(0L, cooldownUntil);
        }

        public boolean active() {
            return eventId != null && entityId != null;
        }

        public static WanderingEvent cooldown(UUID factionId, long cooldownUntil) {
            return new WanderingEvent(
                    factionId, null, null, new ClaimKey(Level.OVERWORLD, 0, 0), BlockPos.ZERO,
                    List.of(), 0L, cooldownUntil
            );
        }

        private WanderingEvent asCooldown(long until) {
            return new WanderingEvent(factionId, null, null, claim, pos, List.of(), 0L, Math.max(until, cooldownUntil));
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("factionId", factionId);
            if (eventId != null) {
                tag.putUUID("eventId", eventId);
            }
            if (entityId != null) {
                tag.putUUID("entityId", entityId);
            }
            tag.put("claim", claim.save());
            tag.putLong("pos", pos.asLong());
            tag.putLong("expiresAt", expiresAt);
            tag.putLong("cooldownUntil", cooldownUntil);
            ListTag offerTags = new ListTag();
            for (RolledOffer offer : offers) {
                CompoundTag offerTag = new CompoundTag();
                offerTag.putString("id", offer.id());
                offerTag.putLong("price", offer.price());
                offerTags.add(offerTag);
            }
            tag.put("offers", offerTags);
            return tag;
        }

        private static Optional<WanderingEvent> load(CompoundTag tag) {
            if (!tag.hasUUID("factionId") || !tag.contains("claim", Tag.TAG_COMPOUND)
                    || !tag.contains("pos", Tag.TAG_LONG)) {
                return Optional.empty();
            }
            Optional<ClaimKey> claim = ClaimKey.load(tag.getCompound("claim"));
            if (claim.isEmpty()) {
                return Optional.empty();
            }
            ListTag offerTags = tag.getList("offers", Tag.TAG_COMPOUND);
            if (offerTags.size() > MAX_ROLLED_OFFERS) {
                return Optional.empty();
            }
            List<RolledOffer> offers = new ArrayList<>();
            try {
                for (int index = 0; index < offerTags.size(); index++) {
                    CompoundTag offer = offerTags.getCompound(index);
                    offers.add(new RolledOffer(offer.getString("id"), offer.getLong("price")));
                }
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
            UUID eventId = tag.hasUUID("eventId") ? tag.getUUID("eventId") : null;
            UUID entityId = tag.hasUUID("entityId") ? tag.getUUID("entityId") : null;
            if ((eventId == null) != (entityId == null)) {
                return Optional.empty();
            }
            return Optional.of(new WanderingEvent(
                    tag.getUUID("factionId"), eventId, entityId, claim.get(), BlockPos.of(tag.getLong("pos")),
                    offers, tag.getLong("expiresAt"), tag.getLong("cooldownUntil")
            ));
        }
    }
}
