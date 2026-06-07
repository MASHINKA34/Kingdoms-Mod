package com.geydev.kalfactions.chest;

import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.faction.FactionManager;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * "Management Key": right-clicking a container inside the player's own territory
 * cycles its {@link ChestAccessMode} (Personal -> Faction -> Public -> Whitelist).
 *
 * <p>The protection handler invokes {@link #handleProtectedUse} before vanilla opens
 * the container; {@link #useOn} is a fallback for when no other handler intercepts.</p>
 */
public class AccessTool extends Item {
    public AccessTool(Properties properties) {
        super(properties);
    }

    public static ToolResult handleProtectedUse(
            ServerPlayer player,
            ServerLevel level,
            BlockPos pos,
            InteractionHand hand
    ) {
        if (!isContainer(level, pos)) {
            return ToolResult.PASS;
        }
        return cycleAccess(player, level, pos);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)
                || !(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.PASS;
        }
        return switch (handleProtectedUse(player, level, context.getClickedPos(), context.getHand())) {
            case PASS -> InteractionResult.PASS;
            case CONSUME -> InteractionResult.SUCCESS;
            case DENY -> InteractionResult.FAIL;
        };
    }

    private static ToolResult cycleAccess(ServerPlayer player, ServerLevel level, BlockPos pos) {
        FactionManager manager = FactionManager.get(level);
        UUID factionId = manager.getFactionIdForMember(player.getUUID()).orElse(null);
        if (factionId == null) {
            feedback(player, "kingdoms.access_tool.not_member");
            return ToolResult.CONSUME;
        }
        UUID claimFactionId = manager.getFactionIdAt(ClaimKey.of(level, pos)).orElse(null);
        if (!factionId.equals(claimFactionId)) {
            feedback(player, "kingdoms.access_tool.not_owned");
            return ToolResult.CONSUME;
        }

        ChestAccess.Key key = ChestAccess.Key.of(level, pos);
        ChestAccess existing = manager.getChestAccess(key).orElse(null);
        ChestAccessMode nextMode = existing == null ? ChestAccessMode.PERSONAL : next(existing.mode());
        UUID ownerId = existing == null ? player.getUUID() : existing.ownerId();
        Set<UUID> whitelist = existing == null ? Set.of() : existing.whitelistedPlayers();

        ChestAccess updated = new ChestAccess(key, factionId, ownerId, nextMode, whitelist);
        FactionManager.OperationResult result = manager.setChestAccess(updated);
        if (!result.successful()) {
            feedback(player, "kingdoms.access_tool.not_owned");
            return ToolResult.CONSUME;
        }

        player.displayClientMessage(
                Component.translatable("kingdoms.access_tool.set", Component.translatable(modeKey(nextMode))),
                true
        );
        return ToolResult.CONSUME;
    }

    private static ChestAccessMode next(ChestAccessMode mode) {
        return switch (mode) {
            case PERSONAL -> ChestAccessMode.FACTION;
            case FACTION -> ChestAccessMode.PUBLIC;
            case PUBLIC -> ChestAccessMode.WHITELIST;
            case WHITELIST -> ChestAccessMode.PERSONAL;
        };
    }

    private static String modeKey(ChestAccessMode mode) {
        return "kingdoms.access." + mode.name().toLowerCase(Locale.ROOT);
    }

    private static boolean isContainer(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof Container
                || level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null) != null;
    }

    private static void feedback(ServerPlayer player, String translationKey) {
        player.displayClientMessage(Component.translatable(translationKey), true);
    }

    public enum ToolResult {
        PASS,
        CONSUME,
        DENY
    }
}
