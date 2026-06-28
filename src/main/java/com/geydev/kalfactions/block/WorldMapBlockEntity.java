package com.geydev.kalfactions.block;

import com.geydev.kalfactions.registry.ModBlockEntities;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class WorldMapBlockEntity extends BlockEntity {
    private static final String TAG_LABEL_DISPLAY = "LabelDisplay";
    private static final String ENTITY_MAP_POS = "kingdomsWorldMapPos";
    private static final Component LABEL = Component.translatable("block.kingdoms.world_map.label");
    private static final int CHECK_INTERVAL_TICKS = 40;
    private static final double LABEL_HEIGHT = 5.4D;

    private UUID labelDisplayId;
    private int labelCheckDelay = CHECK_INTERVAL_TICKS;

    public WorldMapBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WORLD_MAP.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WorldMapBlockEntity map) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (++map.labelCheckDelay < CHECK_INTERVAL_TICKS) {
            return;
        }
        map.labelCheckDelay = 0;
        map.ensureLabel(serverLevel);
    }

    public void removeLabel(ServerLevel level) {
        if (labelDisplayId != null && level.getEntity(labelDisplayId) instanceof Display.TextDisplay display) {
            display.discard();
        }
        for (Display.TextDisplay display : level.getEntitiesOfClass(Display.TextDisplay.class, labelSearchBox(), this::belongsToMap)) {
            display.discard();
        }
        labelDisplayId = null;
        setChanged();
    }

    private void ensureLabel(ServerLevel level) {
        Display.TextDisplay display = findLabel(level);
        if (display == null) {
            display = createLabel(level);
        } else {
            configureLabel(level, display);
        }
        if (display != null && !display.getUUID().equals(labelDisplayId)) {
            labelDisplayId = display.getUUID();
            setChanged();
        }
    }

    private Display.TextDisplay findLabel(ServerLevel level) {
        Display.TextDisplay selected = null;
        if (labelDisplayId != null) {
            Entity entity = level.getEntity(labelDisplayId);
            if (entity instanceof Display.TextDisplay display && belongsToMap(display)) {
                selected = display;
            }
        }
        List<Display.TextDisplay> matches =
                level.getEntitiesOfClass(Display.TextDisplay.class, labelSearchBox(), this::belongsToMap);
        for (Display.TextDisplay match : matches) {
            if (selected == null) {
                selected = match;
            } else if (selected != match) {
                match.discard();
            }
        }
        return selected;
    }

    private Display.TextDisplay createLabel(ServerLevel level) {
        Display.TextDisplay display = EntityType.TEXT_DISPLAY.create(level);
        if (display == null) {
            return null;
        }
        UUID uuid = labelDisplayId == null ? UUID.randomUUID() : labelDisplayId;
        display.setUUID(uuid);
        configureLabel(level, display);
        return level.addFreshEntity(display) ? display : null;
    }

    private void configureLabel(ServerLevel level, Display.TextDisplay display) {
        display.moveTo(
                worldPosition.getX() + 0.5D,
                worldPosition.getY() + LABEL_HEIGHT,
                worldPosition.getZ() + 0.5D,
                0.0F,
                0.0F
        );
        display.setNoGravity(true);
        display.setInvulnerable(true);
        display.setSilent(true);
        display.getPersistentData().putLong(ENTITY_MAP_POS, worldPosition.asLong());
        CompoundTag tag = display.saveWithoutId(new CompoundTag());
        tag.putString(Display.TAG_BILLBOARD, "center");
        tag.putFloat(Display.TAG_VIEW_RANGE, 1.5F);
        tag.putInt("line_width", 200);
        tag.putBoolean("shadow", true);
        tag.putBoolean("see_through", false);
        tag.putInt("background", 0x60000000);
        tag.putString(Display.TextDisplay.TAG_TEXT, Component.Serializer.toJson(LABEL, level.registryAccess()));
        display.load(tag);
    }

    private boolean belongsToMap(Display.TextDisplay display) {
        CompoundTag tag = display.getPersistentData();
        return tag.contains(ENTITY_MAP_POS) && tag.getLong(ENTITY_MAP_POS) == worldPosition.asLong();
    }

    private AABB labelSearchBox() {
        return new AABB(worldPosition).inflate(3.0D, 7.0D, 3.0D);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        labelDisplayId = tag.hasUUID(TAG_LABEL_DISPLAY) ? tag.getUUID(TAG_LABEL_DISPLAY) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (labelDisplayId != null) {
            tag.putUUID(TAG_LABEL_DISPLAY, labelDisplayId);
        }
    }
}
