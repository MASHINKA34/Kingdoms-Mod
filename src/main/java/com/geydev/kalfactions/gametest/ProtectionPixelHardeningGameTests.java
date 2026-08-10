package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import net.mcreator.protectionpixel.network.ProtectionPixelModVariables;
import net.mcreator.protectionpixel.procedures.FlydamageProcedure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProtectionPixelHardeningGameTests {
    private static final BlockPos BLOCK_POS = new BlockPos(1, 3, 1);

    @GameTest(template = "empty")
    public static void platformRejectsHopperInsertion(GameTestHelper helper) {
        assertRejectsAutomation(helper, pp("armorloadplatform"));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hangerRejectsHopperInsertion(GameTestHelper helper) {
        assertRejectsAutomation(helper, pp("armorhanger"));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void crashingIntoAWallFoldsTheManeuveringWing(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos abs = helper.absolutePos(BLOCK_POS);
        player.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
        Item openWing = BuiltInRegistries.ITEM.get(pp("openmaneuveringwing"));
        Item foldedWing = BuiltInRegistries.ITEM.get(pp("maneuveringwing"));

        ProtectionPixelModVariables.PlayerVariables variables =
                player.getData(ProtectionPixelModVariables.PLAYER_VARIABLES);
        variables.motorinterface = new ItemStack(openWing);
        variables.markSyncDirty();

        DamageSource flyIntoWall = new DamageSource(
                helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(DamageTypes.FLY_INTO_WALL)
        );
        FlydamageProcedure.execute(flyIntoWall, player);

        Item nowEquipped = player.getData(ProtectionPixelModVariables.PLAYER_VARIABLES).motorinterface.getItem();
        helper.assertTrue(
                nowEquipped == foldedWing,
                "Wing stayed " + BuiltInRegistries.ITEM.getKey(nowEquipped) + " after crashing into a wall"
        );
        helper.succeed();
    }

    private static void assertRejectsAutomation(GameTestHelper helper, ResourceLocation blockId) {
        helper.setBlock(BLOCK_POS, BuiltInRegistries.BLOCK.get(blockId).defaultBlockState());
        BlockPos abs = helper.absolutePos(BLOCK_POS);
        ItemStack plate = new ItemStack(BuiltInRegistries.ITEM.get(pp("ironarmorplate")));
        for (Direction direction : Direction.values()) {
            IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, abs, direction);
            if (handler == null) {
                continue;
            }
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack leftover = handler.insertItem(slot, plate.copy(), false);
                helper.assertTrue(
                        leftover.getCount() == plate.getCount(),
                        blockId + " accepted an automated insert into slot " + slot + " from " + direction
                );
            }
        }
    }

    private static ResourceLocation pp(String path) {
        return ResourceLocation.fromNamespaceAndPath("protection_pixel", path);
    }
}
