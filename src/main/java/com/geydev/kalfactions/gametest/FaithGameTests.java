package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.faith.FaithEffects;
import com.geydev.kalfactions.faith.FaithGod;
import com.geydev.kalfactions.faith.FaithManager;
import com.geydev.kalfactions.faith.FaithQuest;
import com.geydev.kalfactions.faith.FaithQuests;
import com.geydev.kalfactions.faith.FaithRequirement;
import com.geydev.kalfactions.faith.FaithService;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FaithGameTests {
    @GameTest(template = "empty")
    public static void levelRisesOnlyWithTheWholeQuest(GameTestHelper helper) {
        FaithManager manager = new FaithManager();
        UUID faction = UUID.randomUUID();
        FaithGod god = FaithGod.SCIENCE;
        FaithQuest quest = FaithService.quest(manager, faction, god, manager.level(faction, god));

        helper.assertTrue(
                !quest.isComplete(manager.delivered(faction, god), 0L, 0),
                "An untouched quest counted as complete"
        );
        for (int index = 0; index < quest.requirements().size(); index++) {
            manager.addDelivered(faction, god, index, quest.requirements().get(index).count());
        }
        helper.assertTrue(
                quest.isComplete(manager.delivered(faction, god), quest.spurs(), quest.kills()),
                "A fully delivered quest still counted as incomplete"
        );
        helper.assertTrue(manager.advanceLevel(faction, god), "Level did not rise");
        helper.assertTrue(manager.level(faction, god) == FaithGod.MIN_LEVEL + 1, "Level rose by the wrong step");
        helper.assertTrue(manager.delivered(faction, god).length == 0, "Progress survived the level up");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void warQuestNeedsKillsAndTrophyOnMilestones(GameTestHelper helper) {
        FaithManager manager = new FaithManager();
        UUID faction = UUID.randomUUID();
        FaithGod god = FaithGod.WAR;
        manager.setLevel(faction, god, 2);
        FaithQuest quest = FaithService.quest(manager, faction, god, manager.level(faction, god));

        helper.assertTrue(quest.level() == 3, "Milestone quest was built for the wrong level");
        helper.assertTrue(!quest.killsOrTrophy(), "Level 3 accepted kills or a trophy instead of both");
        int trophyIndex = quest.trophyIndex();
        helper.assertTrue(trophyIndex >= 0, "War quest has no trophy requirement");
        for (int index = 0; index < quest.requirements().size(); index++) {
            manager.addDelivered(faction, god, index, quest.requirements().get(index).count());
        }
        helper.assertTrue(
                !quest.isComplete(manager.delivered(faction, god), 0L, quest.kills() - 1),
                "Milestone quest passed without the kills"
        );
        helper.assertTrue(
                quest.isComplete(manager.delivered(faction, god), 0L, quest.kills()),
                "Milestone quest failed with kills and trophy delivered"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void offeringTakesExactlyOneSet(GameTestHelper helper) {
        FaithManager manager = new FaithManager();
        UUID faction = UUID.randomUUID();
        FaithGod god = FaithGod.SCIENCE;
        FaithQuest quest = FaithService.quest(manager, faction, god, manager.level(faction, god));
        FaithRequirement crystals = quest.requirements().getFirst();
        int required = crystals.count();
        helper.assertTrue(required > 0, "Crystal cost is zero, nothing to measure");

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().clearContent();
        ItemStack stock = new ItemStack(god.crystal(), Math.min(64, required * 3));
        int stocked = stock.getCount();
        player.getInventory().add(stock);

        int taken = FaithService.applyOffering(manager, faction, god, quest, player.getInventory());
        helper.assertTrue(taken >= required, "First offering did not take the crystals");
        helper.assertTrue(
                manager.deliveredAt(faction, god, 0) == required,
                "First offering banked " + manager.deliveredAt(faction, god, 0) + " instead of " + required
        );
        helper.assertTrue(
                player.getInventory().countItem(god.crystal()) == stocked - required,
                "First offering took the wrong number of crystals"
        );

        FaithService.applyOffering(manager, faction, god, quest, player.getInventory());
        helper.assertTrue(
                manager.deliveredAt(faction, god, 0) == required,
                "Second offering banked crystals beyond the requirement"
        );
        helper.assertTrue(
                player.getInventory().countItem(god.crystal()) == stocked - required,
                "Second offering charged the player twice"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void buffCoversEveryMemberUntilItExpires(GameTestHelper helper) {
        FaithManager manager = new FaithManager();
        UUID faction = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        FaithGod god = FaithGod.ECONOMY;
        long now = 1_000_000L;
        long end = now + 30L * 60_000L;
        manager.activateBuff(faction, god, end);

        helper.assertTrue(manager.buffActive(faction, god, now), "Buff did not start");
        helper.assertTrue(!manager.hasForfeited(first, god, end), "First member lost the buff without dying");
        helper.assertTrue(!manager.hasForfeited(second, god, end), "Second member lost the buff without dying");
        helper.assertTrue(!manager.buffActive(faction, god, end), "Buff outlived its window");
        helper.assertTrue(!manager.buffActive(faction, god, end + 1L), "Buff outlived its window");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void deathDropsTheBuffOnlyForTheDeadMember(GameTestHelper helper) {
        FaithManager manager = new FaithManager();
        UUID faction = UUID.randomUUID();
        UUID dead = UUID.randomUUID();
        UUID alive = UUID.randomUUID();
        FaithGod god = FaithGod.WAR;
        long now = 2_000_000L;
        long end = now + 30L * 60_000L;
        manager.activateBuff(faction, god, end);
        manager.forfeit(dead, god, end);

        helper.assertTrue(manager.hasForfeited(dead, god, end), "The dead member kept the buff");
        helper.assertTrue(!manager.hasForfeited(alive, god, end), "A living member lost the buff");
        helper.assertTrue(manager.buffActive(faction, god, now), "The faction lost the buff over one death");

        long nextEnd = end + 60_000L;
        manager.activateBuff(faction, god, nextEnd);
        helper.assertTrue(!manager.hasForfeited(dead, god, nextEnd), "A new window stayed forfeited");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void warHealthModifierGoesOnAndOff(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        helper.assertTrue(attribute != null, "Mock player has no max health attribute");
        double base = player.getMaxHealth();

        FaithEffects.applyWarHealth(player, 6.0D);
        helper.assertTrue(
                player.getMaxHealth() == base + 6.0D,
                "Max health did not grow: " + player.getMaxHealth()
        );
        helper.assertTrue(
                attribute.getModifier(FaithEffects.WAR_HEALTH_MODIFIER_ID) != null,
                "Max health modifier was not installed"
        );

        player.setHealth(player.getMaxHealth());
        FaithEffects.applyWarHealth(player, 0.0D);
        helper.assertTrue(
                attribute.getModifier(FaithEffects.WAR_HEALTH_MODIFIER_ID) == null,
                "Max health modifier survived the buff"
        );
        helper.assertTrue(player.getMaxHealth() == base, "Max health did not return to normal");
        helper.assertTrue(player.getHealth() <= base, "Health was not clamped back to the new maximum");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void questRollIsStableAndRerollChangesIt(GameTestHelper helper) {
        UUID faction = UUID.randomUUID();
        FaithGod god = FaithGod.SCIENCE;
        FaithQuest first = FaithQuests.build(faction, god, 4, 0);
        FaithQuest again = FaithQuests.build(faction, god, 4, 0);
        helper.assertTrue(
                first.requirements().equals(again.requirements()),
                "The same seed rolled a different quest"
        );
        FaithQuest rerolled = FaithQuests.build(faction, god, 4, 1);
        helper.assertTrue(
                !first.requirements().equals(rerolled.requirements()) || first.requirements().size() <= 1,
                "A reroll produced the very same demands"
        );
        helper.succeed();
    }

    private FaithGameTests() {
    }
}
