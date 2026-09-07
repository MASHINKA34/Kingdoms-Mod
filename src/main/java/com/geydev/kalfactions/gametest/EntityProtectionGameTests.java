package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.faction.FactionManager;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import com.geydev.kalfactions.protection.EntityProtectionHandler;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EntityProtectionGameTests {
    @GameTest(template = "empty", batch = "entity_protection")
    public static void outsidersCannotTouchDecorationsInsideAClaim(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        ServerPlayer owner = RegressionPlayers.create(level, pos, 0).player();
        ServerPlayer outsider = RegressionPlayers.create(level, pos, 0).player();
        FactionManager original = FactionManager.get(level);
        FactionManager manager = new FactionManager();
        claim(manager, owner.getUUID(), ClaimKey.of(level, pos));
        level.getServer().overworld().getDataStorage().set(FactionManager.DATA_NAME, manager);
        try {
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            ArmorStand stand = spawn(level, EntityType.ARMOR_STAND, pos);
            ItemFrame frame = new ItemFrame(level, pos, net.minecraft.core.Direction.NORTH);
            frame.setPos(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
            level.addFreshEntity(frame);
            MinecartChest cart = spawn(level, EntityType.CHEST_MINECART, pos);
            Zombie zombie = spawn(level, EntityType.ZOMBIE, pos);

            for (Entity target : new Entity[] { stand, frame, cart }) {
                helper.assertTrue(
                        EntityProtectionHandler.isProtectedFrom(outsider, target),
                        "outsider blocked from " + target.getType().getDescriptionId());
                helper.assertTrue(
                        !EntityProtectionHandler.isProtectedFrom(owner, target),
                        "owner allowed on " + target.getType().getDescriptionId());
            }
            helper.assertTrue(
                    !EntityProtectionHandler.isProtectedFrom(outsider, zombie),
                    "hostile mobs stay attackable");

            EntityInvulnerabilityCheckEvent event = new EntityInvulnerabilityCheckEvent(
                    stand, level.damageSources().playerAttack(outsider), false);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
            helper.assertTrue(event.isInvulnerable(), "outsider damage is refused");

            EntityInvulnerabilityCheckEvent environmental = new EntityInvulnerabilityCheckEvent(
                    stand, level.damageSources().onFire(), false);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(environmental);
            helper.assertTrue(!environmental.isInvulnerable(), "environmental damage still applies");
        } finally {
            level.getServer().overworld().getDataStorage().set(FactionManager.DATA_NAME, original);
        }
        helper.succeed();
    }

    private static <T extends Entity> T spawn(ServerLevel level, EntityType<T> type, BlockPos pos) {
        T entity = type.spawn(level, pos, MobSpawnType.COMMAND);
        if (entity == null) {
            throw new IllegalStateException("Cannot spawn " + type.getDescriptionId());
        }
        return entity;
    }

    private static void claim(FactionManager manager, UUID owner, ClaimKey claim) {
        var created = manager.createFaction(owner, "Keepers", 0x4E7A42,
                ResourceLocation.withDefaultNamespace("stone"), Set.of(), false, claim, 1);
        if (!created.successful()) {
            throw new IllegalStateException("Cannot create test faction: " + created);
        }
    }

    private EntityProtectionGameTests() {
    }
}
