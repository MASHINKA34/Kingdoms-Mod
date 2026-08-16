package com.geydev.kalfactions.dungeon;

import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.data.SavedDataFormat;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class ChestTemplateManager extends SavedData {
    public static final String DATA_NAME = "kingdoms_chest_templates";
    public static final Factory<ChestTemplateManager> FACTORY =
            new Factory<>(ChestTemplateManager::new, ChestTemplateManager::load);

    private static final SavedDataFormat FORMAT = new SavedDataFormat(1);
    private static final String TAG_TEMPLATES = "templates";

    private final Map<UUID, ChestTemplate> templates = new LinkedHashMap<>();

    public static ChestTemplateManager get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static ChestTemplateManager get(ServerLevel level) {
        return get(Objects.requireNonNull(level, "level").getServer());
    }

    public synchronized List<ChestTemplate> all() {
        return List.copyOf(templates.values());
    }

    public synchronized int count() {
        return templates.size();
    }

    public synchronized Optional<ChestTemplate> byId(UUID id) {
        return Optional.ofNullable(id == null ? null : templates.get(id));
    }

    public synchronized Optional<ChestTemplate> byName(String name) {
        String normalized = ChestTemplate.normalizeName(name).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return templates.values().stream()
                .filter(template -> template.name().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
    }

    public synchronized SaveResult put(
            ChestTemplate template,
            boolean overwrite,
            Limits limits,
            HolderLookup.Provider registries
    ) {
        if (template.name().isEmpty()) {
            return new SaveResult(Reason.NAME_EMPTY, null);
        }
        if (template.isEmpty()) {
            return new SaveResult(Reason.TEMPLATE_EMPTY, null);
        }
        ChestTemplate existing = byName(template.name()).orElse(null);
        if (existing != null && !overwrite) {
            return new SaveResult(Reason.NAME_TAKEN, null);
        }
        if (existing == null && templates.size() >= limits.maxTemplates()) {
            return new SaveResult(Reason.TOO_MANY, null);
        }
        ChestTemplate stored = existing == null ? template : template.withId(existing.id());
        long total = measure(stored, registries);
        for (ChestTemplate other : templates.values()) {
            if (!other.id().equals(stored.id())) {
                total += measure(other, registries);
            }
        }
        if (total > limits.maxBytes()) {
            return new SaveResult(Reason.TOO_LARGE, null);
        }
        templates.put(stored.id(), stored);
        setDirty();
        return new SaveResult(Reason.OK, stored);
    }

    public synchronized Reason rename(UUID id, String requestedName) {
        ChestTemplate template = templates.get(id);
        if (template == null) {
            return Reason.NOT_FOUND;
        }
        String name = ChestTemplate.normalizeName(requestedName);
        if (name.isEmpty()) {
            return Reason.NAME_EMPTY;
        }
        ChestTemplate existing = byName(name).orElse(null);
        if (existing != null && !existing.id().equals(id)) {
            return Reason.NAME_TAKEN;
        }
        if (template.name().equals(name)) {
            return Reason.OK;
        }
        templates.put(id, template.withName(name));
        setDirty();
        return Reason.OK;
    }

    public synchronized boolean delete(UUID id) {
        if (id == null || templates.remove(id) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    public synchronized long totalBytes(HolderLookup.Provider registries) {
        long total = 0L;
        for (ChestTemplate template : templates.values()) {
            total += measure(template, registries);
        }
        return total;
    }

    public static long measure(ChestTemplate template, HolderLookup.Provider registries) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream stream = new DataOutputStream(buffer)) {
            NbtIo.write(template.save(registries), stream);
        } catch (IOException exception) {
            return Integer.MAX_VALUE;
        }
        return buffer.size();
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        FORMAT.stamp(tag);
        ListTag list = new ListTag();
        for (ChestTemplate template : templates.values()) {
            list.add(template.save(registries));
        }
        tag.put(TAG_TEMPLATES, list);
        return tag;
    }

    static ChestTemplateManager load(CompoundTag saved, HolderLookup.Provider registries) {
        CompoundTag tag = FORMAT.upgrade(saved);
        boolean legacyFormat = FORMAT.outdated(saved);
        ChestTemplateManager manager = new ChestTemplateManager();
        ListTag list = tag.getList(TAG_TEMPLATES, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            ChestTemplate.load(list.getCompound(index), registries)
                    .ifPresent(template -> manager.templates.putIfAbsent(template.id(), template));
        }
        if (legacyFormat) {
            manager.setDirty();
        }
        return manager;
    }

    public enum Reason {
        OK,
        NOT_FOUND,
        NAME_EMPTY,
        NAME_TAKEN,
        TEMPLATE_EMPTY,
        TOO_MANY,
        TOO_LARGE
    }

    public record SaveResult(Reason reason, ChestTemplate template) {
        public boolean successful() {
            return reason == Reason.OK && template != null;
        }
    }

    public record Limits(int maxTemplates, long maxBytes) {
        public static Limits fromConfig() {
            return new Limits(
                    ModConfigSpec.DUNGEON_MAX_CHEST_TEMPLATES.getAsInt(),
                    ModConfigSpec.DUNGEON_MAX_CHEST_TEMPLATE_KILOBYTES.getAsInt() * 1024L
            );
        }
    }
}
