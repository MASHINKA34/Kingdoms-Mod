package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.item.SafeZoneWandItem;
import com.geydev.kalfactions.market.PlotSelection;
import com.geydev.kalfactions.registry.ModDataComponents;
import com.geydev.kalfactions.registry.ModItems;
import com.geydev.kalfactions.safezone.SafeZone;
import com.geydev.kalfactions.safezone.SafeZoneEvents;
import com.geydev.kalfactions.safezone.SafeZoneManager;
import com.geydev.kalfactions.safezone.SafeZonePayloads;
import com.geydev.kalfactions.safezone.SafeZoneService;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SafeZoneGameTests {
    @GameTest(template = "empty", batch = "safezone", timeoutTicks = 300)
    public static void damageInsideASafeZoneIsCancelled(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SafeZoneManager manager = SafeZoneManager.get(level);
        BlockPos center = remotePos(level, 1);
        String id = "kingdoms-test-shelter";
        ServerPlayer victim = mockPlayer(level, "kingdoms-safezone-victim", center);
        try {
            addZone(helper, manager, id, level, center);
            helper.assertTrue(
                    hurt(victim, level.damageSources().generic()).isCanceled(),
                    "generic damage inside a safe zone is cancelled"
            );
            helper.assertTrue(
                    hurt(victim, level.damageSources().fall()).isCanceled(),
                    "fall damage inside a safe zone is cancelled"
            );

            victim.setPos(center.getX() + 0.5D, center.getY(), center.getZ() + 64.5D);
            helper.assertFalse(
                    hurt(victim, level.damageSources().generic()).isCanceled(),
                    "damage outside the safe zone still applies"
            );
        } finally {
            manager.remove(id);
            victim.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "safezone", timeoutTicks = 300)
    public static void aPlayerInsideASafeZoneCannotHitPlayersOutside(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SafeZoneManager manager = SafeZoneManager.get(level);
        BlockPos center = remotePos(level, 2);
        String id = "kingdoms-test-tower";
        ServerPlayer attacker = mockPlayer(level, "kingdoms-safezone-attacker", center);
        ServerPlayer victim = mockPlayer(level, "kingdoms-safezone-target", center.offset(0, 0, 64));
        try {
            addZone(helper, manager, id, level, center);
            helper.assertFalse(
                    manager.isProtected(level.dimension(), victim.position()),
                    "the victim stands outside the safe zone"
            );
            helper.assertTrue(
                    hurt(victim, level.damageSources().playerAttack(attacker)).isCanceled(),
                    "pvp damage from inside a safe zone is cancelled"
            );

            manager.remove(id);
            helper.assertFalse(
                    hurt(victim, level.damageSources().playerAttack(attacker)).isCanceled(),
                    "without the safe zone the same attack lands"
            );
        } finally {
            manager.remove(id);
            attacker.discard();
            victim.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "safezone", timeoutTicks = 300)
    public static void zonesValidateTheirIdAndSize(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SafeZoneManager manager = SafeZoneManager.get(level);
        BlockPos center = remotePos(level, 3);
        String id = "kingdoms-test-bounds";
        try {
            manager.remove(id);
            helper.assertValueEqual(
                    manager.add("Плохой ID", level.dimension(), center, center),
                    SafeZoneManager.Reason.INVALID_ID,
                    "an id outside a-z0-9_- is rejected"
            );
            helper.assertValueEqual(
                    manager.add(
                            id,
                            level.dimension(),
                            center,
                            center.offset(SafeZoneManager.MAX_SIDE, 0, 0)
                    ),
                    SafeZoneManager.Reason.TOO_LARGE,
                    "a zone longer than the limit is rejected"
            );
            helper.assertValueEqual(
                    manager.add(id, level.dimension(), center, center.offset(4, 4, 4)),
                    SafeZoneManager.Reason.OK,
                    "a zone inside the limits is created"
            );
            helper.assertValueEqual(
                    manager.add(id, level.dimension(), center, center),
                    SafeZoneManager.Reason.DUPLICATE,
                    "the same id is not reused"
            );

            SafeZone zone = manager.byId(id).orElseThrow();
            helper.assertValueEqual(zone.min(), center, "zone minimum corner");
            helper.assertValueEqual(zone.max(), center.offset(4, 4, 4), "zone maximum corner");
            helper.assertTrue(
                    zone.contains(level.dimension(), center.getCenter()),
                    "the zone covers its own corner block"
            );
            helper.assertFalse(
                    zone.contains(Level.NETHER, center.getCenter()),
                    "the zone is bound to its dimension"
            );
            helper.assertFalse(
                    zone.contains(level.dimension(), center.offset(5, 5, 5).getCenter()),
                    "the zone stops at its maximum corner"
            );
        } finally {
            manager.remove(id);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "safezone", timeoutTicks = 300)
    public static void theWandMarksBothCornersForOperatorsOnly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos first = helper.absolutePos(new BlockPos(0, 1, 0));
        BlockPos second = helper.absolutePos(new BlockPos(2, 3, 2));
        ServerPlayer visitor = mockPlayer(level, "kingdoms-wand-visitor", first, 0);
        ServerPlayer operator = mockPlayer(level, "kingdoms-wand-operator", first, 4);
        SafeZoneWandItem wand = ModItems.SAFE_ZONE_WAND.get();
        try {
            ItemStack stack = new ItemStack(wand);
            visitor.setItemInHand(InteractionHand.MAIN_HAND, stack);
            helper.assertValueEqual(
                    wand.useOn(useContext(visitor, first)),
                    InteractionResult.FAIL,
                    "a player without permissions is refused"
            );
            helper.assertTrue(
                    SafeZoneWandItem.selectionOf(stack) == null,
                    "a refused click stores nothing"
            );

            operator.setItemInHand(InteractionHand.MAIN_HAND, stack);
            wand.useOn(useContext(operator, first));
            PlotSelection started = SafeZoneWandItem.selectionOf(stack);
            helper.assertTrue(started != null, "the first corner is stored");
            helper.assertFalse(started.isComplete(), "one corner is not a complete selection");
            helper.assertValueEqual(started.first(), first, "first corner");

            wand.useOn(useContext(operator, second));
            PlotSelection completed = SafeZoneWandItem.selectionOf(stack);
            helper.assertTrue(completed.isComplete(), "the second corner completes the selection");
            helper.assertValueEqual(completed.second().orElseThrow(), second, "second corner");

            operator.setShiftKeyDown(true);
            wand.useOn(useContext(operator, first));
            helper.assertTrue(
                    SafeZoneWandItem.selectionOf(stack) == null,
                    "a sneaking click clears the selection"
            );
        } finally {
            visitor.discard();
            operator.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "safezone", timeoutTicks = 300)
    public static void zoneEntriesSurviveTheNetworkCodec(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SafeZonePayloads.S2CSyncSafeZones payload = new SafeZonePayloads.S2CSyncSafeZones(
                level.dimension().location(),
                List.of(new SafeZonePayloads.ZoneEntry("kingdoms-test-codec", -8, -60, -8, 8, 70, 8))
        );
        RegistryFriendlyByteBuf buffer =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        try {
            SafeZonePayloads.S2CSyncSafeZones.STREAM_CODEC.encode(buffer, payload);
            SafeZonePayloads.S2CSyncSafeZones decoded =
                    SafeZonePayloads.S2CSyncSafeZones.STREAM_CODEC.decode(buffer);
            helper.assertValueEqual(decoded.dimension(), payload.dimension(), "synced dimension");
            helper.assertValueEqual(decoded.zones(), payload.zones(), "synced zones");
            helper.assertTrue(decoded.zones().getFirst().contains(0, 0, 0), "the entry covers its inside");
            helper.assertFalse(decoded.zones().getFirst().contains(9, 0, 0), "the entry stops at its edge");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "safezone", timeoutTicks = 300)
    public static void ctrlScrollDragsTheSelectionFace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos first = helper.absolutePos(new BlockPos(0, 1, 0));
        BlockPos second = helper.absolutePos(new BlockPos(2, 1, 2));
        ServerPlayer visitor = mockPlayer(level, "kingdoms-drag-visitor", first, 0);
        ServerPlayer operator = mockPlayer(level, "kingdoms-drag-operator", first, 4);
        try {
            ItemStack stack = new ItemStack(ModItems.SAFE_ZONE_WAND.get());
            stack.set(
                    ModDataComponents.SAFE_ZONE_SELECTION.get(),
                    PlotSelection.start(level, first).withSecond(second)
            );

            visitor.setItemInHand(InteractionHand.MAIN_HAND, stack);
            helper.assertFalse(
                    SafeZoneService.adjustSelection(visitor, (byte) Direction.UP.ordinal(), (byte) 1),
                    "a player without permissions cannot drag a face"
            );

            operator.setItemInHand(InteractionHand.MAIN_HAND, stack);
            helper.assertTrue(
                    SafeZoneService.adjustSelection(operator, (byte) Direction.UP.ordinal(), (byte) 1),
                    "the operator drags the top face"
            );
            helper.assertValueEqual(spans(stack), new BlockPos(3, 2, 3), "the zone grew one block upwards");

            helper.assertTrue(
                    SafeZoneService.adjustSelection(operator, (byte) Direction.WEST.ordinal(), (byte) 1),
                    "the operator drags the west face"
            );
            helper.assertValueEqual(spans(stack), new BlockPos(4, 2, 3), "the zone grew one block westwards");

            helper.assertTrue(
                    SafeZoneService.adjustSelection(operator, (byte) Direction.UP.ordinal(), (byte) -1),
                    "the top face comes back"
            );
            helper.assertValueEqual(spans(stack), new BlockPos(4, 1, 3), "the zone shrank back down");

            helper.assertTrue(
                    SafeZoneService.adjustSelection(operator, (byte) Direction.UP.ordinal(), (byte) -1),
                    "the top face is dragged past its floor"
            );
            helper.assertValueEqual(spans(stack), new BlockPos(4, 1, 3), "a side never shrinks below one block");

            helper.assertFalse(
                    SafeZoneService.adjustSelection(operator, (byte) 42, (byte) 1),
                    "an unknown face is rejected"
            );
            helper.assertFalse(
                    SafeZoneService.adjustSelection(operator, (byte) Direction.UP.ordinal(), (byte) 0),
                    "a zero step is rejected"
            );
        } finally {
            visitor.discard();
            operator.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "safezone", timeoutTicks = 300)
    public static void onlyZonesOfTheCurrentDimensionAreSynced(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SafeZoneManager manager = SafeZoneManager.get(level);
        BlockPos center = remotePos(level, 4);
        String here = "kingdoms-test-here";
        String elsewhere = "kingdoms-test-elsewhere";
        try {
            manager.remove(here);
            manager.remove(elsewhere);
            manager.add(here, level.dimension(), center, center.offset(3, 3, 3));
            manager.add(elsewhere, Level.NETHER, center, center.offset(3, 3, 3));

            List<SafeZonePayloads.ZoneEntry> entries = SafeZoneService.entriesFor(level, level.dimension());

            helper.assertValueEqual(entries.size(), 1, "only the current dimension is synced");
            SafeZonePayloads.ZoneEntry entry = entries.getFirst();
            helper.assertValueEqual(entry.id(), here, "synced id");
            helper.assertValueEqual(entry.minY(), center.getY(), "synced minimum height");
            helper.assertValueEqual(entry.maxY(), center.getY() + 3, "synced maximum height");
            helper.assertTrue(
                    entry.contains(center.getX(), center.getY(), center.getZ()),
                    "the synced entry covers the zone corner"
            );
        } finally {
            manager.remove(here);
            manager.remove(elsewhere);
        }
        helper.succeed();
    }

    private static BlockPos spans(ItemStack wand) {
        BoundingBox box = SafeZoneWandItem.selectionOf(wand).box().orElseThrow();
        return new BlockPos(box.getXSpan(), box.getYSpan(), box.getZSpan());
    }

    @GameTest(template = "empty", batch = "safezone", timeoutTicks = 300)
    public static void rightClickingInsideTheSelectionCreatesTheZone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SafeZoneManager manager = SafeZoneManager.get(level);
        BlockPos first = helper.absolutePos(new BlockPos(0, 1, 0));
        BlockPos second = helper.absolutePos(new BlockPos(3, 4, 3));
        BlockPos inside = helper.absolutePos(new BlockPos(1, 2, 1));
        ServerPlayer operator = mockPlayer(level, "kingdoms-create-operator", first, 4);
        String created = null;
        try {
            ItemStack stack = new ItemStack(ModItems.SAFE_ZONE_WAND.get());
            operator.setItemInHand(InteractionHand.MAIN_HAND, stack);
            ModItems.SAFE_ZONE_WAND.get().useOn(useContext(operator, first));
            ModItems.SAFE_ZONE_WAND.get().useOn(useContext(operator, second));
            helper.assertTrue(
                    SafeZoneWandItem.selectionOf(stack).isComplete(),
                    "both corners are marked"
            );

            int before = manager.count();
            ModItems.SAFE_ZONE_WAND.get().useOn(useContext(operator, inside));

            helper.assertValueEqual(manager.count(), before + 1, "a zone was created");
            SafeZone zone = manager.zoneAt(level.dimension(), inside).orElseThrow();
            created = zone.id();
            helper.assertValueEqual(zone.min(), first, "the zone keeps the first corner");
            helper.assertValueEqual(zone.max(), second, "the zone keeps the second corner");
            helper.assertTrue(
                    SafeZoneWandItem.selectionOf(stack) == null,
                    "creating the zone clears the selection"
            );

            helper.assertTrue(
                    SafeZoneEvents.isProtected(operator),
                    "the operator standing inside is protected"
            );
        } finally {
            if (created != null) {
                manager.remove(created);
            }
            operator.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "safezone", timeoutTicks = 300)
    public static void sneakingLeftClickRemovesTheZoneUnderTheWand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SafeZoneManager manager = SafeZoneManager.get(level);
        BlockPos center = remotePos(level, 5);
        String id = "kingdoms-test-erase";
        ServerPlayer operator = mockPlayer(level, "kingdoms-erase-operator", center, 4);
        ServerPlayer visitor = mockPlayer(level, "kingdoms-erase-visitor", center, 0);
        try {
            addZone(helper, manager, id, level, center);
            ItemStack stack = new ItemStack(ModItems.SAFE_ZONE_WAND.get());

            operator.setItemInHand(InteractionHand.MAIN_HAND, stack);
            operator.setShiftKeyDown(false);
            helper.assertFalse(
                    leftClick(operator, center).isCanceled(),
                    "a plain left click leaves the zone alone"
            );
            helper.assertTrue(manager.byId(id).isPresent(), "the zone survived the plain click");

            visitor.setItemInHand(InteractionHand.MAIN_HAND, stack);
            visitor.setShiftKeyDown(true);
            helper.assertFalse(
                    leftClick(visitor, center).isCanceled(),
                    "a player without permissions cannot remove a zone"
            );
            helper.assertTrue(manager.byId(id).isPresent(), "the zone survived the visitor");

            operator.setShiftKeyDown(true);
            helper.assertTrue(
                    leftClick(operator, center).isCanceled(),
                    "the sneaking click is consumed instead of breaking the block"
            );
            helper.assertTrue(manager.byId(id).isEmpty(), "the zone was removed");
        } finally {
            manager.remove(id);
            operator.discard();
            visitor.discard();
        }
        helper.succeed();
    }

    private static PlayerInteractEvent.LeftClickBlock leftClick(ServerPlayer player, BlockPos pos) {
        return CommonHooks.onLeftClickBlock(
                player,
                pos,
                Direction.UP,
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
        );
    }

    private static UseOnContext useContext(ServerPlayer player, BlockPos pos) {
        return new UseOnContext(
                player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)
        );
    }

    private static void addZone(
            GameTestHelper helper,
            SafeZoneManager manager,
            String id,
            ServerLevel level,
            BlockPos center
    ) {
        manager.remove(id);
        helper.assertValueEqual(
                manager.add(id, level.dimension(), center.offset(-8, -8, -8), center.offset(8, 8, 8)),
                SafeZoneManager.Reason.OK,
                "the safe zone was created"
        );
    }

    private static LivingIncomingDamageEvent hurt(LivingEntity victim, DamageSource source) {
        LivingIncomingDamageEvent event =
                new LivingIncomingDamageEvent(victim, new DamageContainer(source, 5.0F));
        NeoForge.EVENT_BUS.post(event);
        return event;
    }

    private static BlockPos remotePos(ServerLevel level, int index) {
        BlockPos spawn = level.getSharedSpawnPos();
        return new BlockPos(
                spawn.getX() + 80_000 + index * 1_024,
                level.getSeaLevel() + 24,
                spawn.getZ() + 80_000
        );
    }

    private static ServerPlayer mockPlayer(ServerLevel level, String name, BlockPos pos) {
        return mockPlayer(level, name, pos, 0);
    }

    private static ServerPlayer mockPlayer(ServerLevel level, String name, BlockPos pos, int permissions) {
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer player =
                new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
                    @Override
                    protected int getPermissionLevel() {
                        return permissions;
                    }

                    @Override
                    public void displayClientMessage(Component message, boolean actionBar) {
                    }
                };
        player.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        return player;
    }

    private SafeZoneGameTests() {
    }
}
