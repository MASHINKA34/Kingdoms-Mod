package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.protection.ProtectionHandler;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ContainerRevalidationGameTests {
    @GameTest(template = "empty", batch = "container_revalidation")
    public static void losingAccessClosesAnOpenDoubleChest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos left = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos right = left.east();
        ServerPlayer player = RegressionPlayers.create(level, left, 0).player();
        FactionManager original = FactionManager.get(level);
        FactionManager manager = new FactionManager();
        Faction owner = faction(manager, UUID.randomUUID(), ClaimKey.of(level, left));
        manager.addMember(owner.id(), player.getUUID());
        level.getServer().overworld().getDataStorage().set(FactionManager.DATA_NAME, manager);
        try {
            level.setBlockAndUpdate(left, Blocks.CHEST.defaultBlockState());
            level.setBlockAndUpdate(right, Blocks.CHEST.defaultBlockState());
            ChestBlockEntity first = (ChestBlockEntity) level.getBlockEntity(left);
            ChestBlockEntity second = (ChestBlockEntity) level.getBlockEntity(right);

            ProtectionHandler.onRightClickBlock(new PlayerInteractEvent.RightClickBlock(
                    player,
                    InteractionHand.MAIN_HAND,
                    left,
                    new BlockHitResult(Vec3.atCenterOf(left), Direction.UP, left, false)));
            player.containerMenu = ChestMenu.sixRows(1, player.getInventory(),
                    new CompoundContainer(first, second));

            ProtectionHandler.onPlayerTick(new PlayerTickEvent.Post(player));
            helper.assertTrue(player.containerMenu != player.inventoryMenu, "member keeps the double chest open");

            manager.removeMember(owner.id(), player.getUUID());
            ProtectionHandler.onPlayerTick(new PlayerTickEvent.Post(player));
            helper.assertTrue(player.containerMenu == player.inventoryMenu, "double chest closes once access is gone");
        } finally {
            level.getServer().overworld().getDataStorage().set(FactionManager.DATA_NAME, original);
        }
        helper.succeed();
    }

    private static Faction faction(FactionManager manager, UUID owner, ClaimKey claim) {
        var created = manager.createFaction(owner, "Vaults", 0x4E7A42,
                ResourceLocation.withDefaultNamespace("stone"), Set.of(), false, claim, 1);
        if (!created.successful()) {
            throw new IllegalStateException("Cannot create test faction: " + created);
        }
        return manager.getFactionForMember(owner).orElseThrow();
    }

    private ContainerRevalidationGameTests() {
    }
}
