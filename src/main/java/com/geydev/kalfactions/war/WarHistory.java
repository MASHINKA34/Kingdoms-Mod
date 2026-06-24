package com.geydev.kalfactions.war;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

public final class WarHistory extends SavedData {
    public static final int MAX_RECORDS = 200;
    public static final String DATA_NAME = "kingdoms_war_history";
    public static final Factory<WarHistory> FACTORY = new Factory<>(WarHistory::new, WarHistory::load);

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG_RECORDS = "records";

    private final List<WarRecord> records = new ArrayList<>();

    public static WarHistory get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static WarHistory get(ServerLevel level) {
        return get(Objects.requireNonNull(level, "level").getServer());
    }

    public synchronized void add(WarRecord record) {
        if (record == null) {
            return;
        }
        records.add(0, record);
        trim();
        setDirty();
    }

    public synchronized List<WarRecord> records() {
        return List.copyOf(records);
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag recordsTag = new ListTag();
        for (WarRecord record : records) {
            recordsTag.add(record.save());
        }
        tag.put(TAG_RECORDS, recordsTag);
        return tag;
    }

    private static WarHistory load(CompoundTag tag, HolderLookup.Provider registries) {
        WarHistory history = new WarHistory();
        boolean repaired = false;
        ListTag recordsTag = tag.getList(TAG_RECORDS, Tag.TAG_COMPOUND);
        for (int index = 0; index < recordsTag.size(); index++) {
            var record = WarRecord.load(recordsTag.getCompound(index));
            if (record.isEmpty()) {
                repaired = true;
                LOGGER.warn("Skipped invalid war history entry at NBT index {}", index);
                continue;
            }
            history.records.add(record.get());
            if (history.records.size() >= MAX_RECORDS) {
                break;
            }
        }
        if (recordsTag.size() > MAX_RECORDS) {
            repaired = true;
        }
        if (repaired) {
            history.setDirty();
        }
        return history;
    }

    private void trim() {
        while (records.size() > MAX_RECORDS) {
            records.remove(records.size() - 1);
        }
    }
}
