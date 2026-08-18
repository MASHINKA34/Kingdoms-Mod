package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.faction.InfluenceSourceHandler;
import com.geydev.kalfactions.faction.InfluenceType;
import com.geydev.kalfactions.faction.ScienceIncome;
import com.geydev.kalfactions.faction.ScienceLedger;
import com.geydev.kalfactions.faction.ScienceTags;
import com.geydev.kalfactions.registry.ModItems;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ScienceDiscoveryGameTests {
    @GameTest(template = "empty")
    public static void firstCraftPaysAndRepeatDoesNot(GameTestHelper helper) {
        Fixture fixture = fixture();
        ItemStack stack = new ItemStack(Items.STICK);
        long reward = ScienceIncome.discoveryReward(stack);

        long first = fixture.award(stack);
        long second = fixture.award(stack);

        helper.assertTrue(reward > 0L, "The configured discovery reward is zero");
        helper.assertTrue(first == reward, "The first craft did not pay the discovery reward");
        helper.assertTrue(second == 0L, "A repeated craft paid science again");
        helper.assertTrue(fixture.science() == reward, "The faction banked more than one award");
        helper.assertTrue(fixture.ledger.discoveryCount(fixture.factionId) == 1, "The discovery was counted twice");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void logoutDoesNotRestoreTheReward(GameTestHelper helper) {
        Fixture fixture = fixture();
        ItemStack stack = new ItemStack(Items.STICK);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        long first = fixture.award(stack);
        InfluenceSourceHandler.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
        long afterRelog = fixture.award(stack);

        helper.assertTrue(first > 0L, "The first craft paid nothing");
        helper.assertTrue(afterRelog == 0L, "Logging out reset the discovery and paid again");
        helper.assertTrue(fixture.science() == first, "The faction banked a second award after the logout");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void vanillaCraftsAreSkippedUnlessAllowed(GameTestHelper helper) {
        ResourceLocation vanilla = BuiltInRegistries.ITEM.getKey(Items.STICK);
        ResourceLocation modded = BuiltInRegistries.ITEM.getKey(ModItems.CRYSTAL_SCIENCE.get());

        helper.assertTrue(
                InfluenceSourceHandler.countsAsDiscovery(vanilla) == ModConfigSpec.SCIENCE_DISCOVERY_ALLOW_VANILLA.get(),
                "Vanilla crafts ignored the discoveryAllowVanilla switch"
        );
        helper.assertTrue(
                InfluenceSourceHandler.countsAsDiscovery(modded),
                "A modded craft was not treated as a discovery"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tierMultiplierScalesTheReward(GameTestHelper helper) {
        long base = ModConfigSpec.SCIENCE_DISCOVERY_INFLUENCE.getAsLong();

        helper.assertTrue(
                ScienceTags.discoveryTier(new ItemStack(Items.STICK)) == 0,
                "An untagged item was given a discovery tier"
        );
        helper.assertTrue(
                ScienceTags.discoveryMultiplier(new ItemStack(Items.STICK)) == 1.0D,
                "An untagged item did not use the plain multiplier"
        );
        helper.assertTrue(ScienceIncome.discoveryReward(10L, 2.0D) == 20L, "A x2 tier did not double the reward");
        helper.assertTrue(ScienceIncome.discoveryReward(10L, 4.0D) == 40L, "A x4 tier did not quadruple the reward");
        assertTierReward(helper, base, 1, ModConfigSpec.SCIENCE_DISCOVERY_TIER1_MULTIPLIER.get());
        assertTierReward(helper, base, 2, ModConfigSpec.SCIENCE_DISCOVERY_TIER2_MULTIPLIER.get());
        assertTierReward(helper, base, 3, ModConfigSpec.SCIENCE_DISCOVERY_TIER3_MULTIPLIER.get());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dailyCapTrimsTheGrantToTheRemainder(GameTestHelper helper) {
        long cap = ModConfigSpec.SCIENCE_DAILY_CAP.getAsLong();
        helper.assertTrue(cap > 3L, "The daily science cap is disabled or too small for this test");
        Fixture fixture = fixture();
        fixture.ledger.recordScience(fixture.factionId, System.currentTimeMillis(), cap - 3L);

        long trimmed = fixture.grant(cap);
        long exhausted = fixture.grant(cap);

        helper.assertTrue(trimmed == 3L, "The grant was not trimmed to the daily remainder");
        helper.assertTrue(exhausted == 0L, "Science was granted past the daily cap");
        helper.assertTrue(fixture.science() == 3L, "The faction banked more than the daily remainder");
        helper.assertTrue(
                fixture.ledger.grantedToday(fixture.factionId, System.currentTimeMillis()) == cap,
                "The daily counter does not match the cap"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aCappedDayDoesNotBurnTheDiscovery(GameTestHelper helper) {
        long cap = ModConfigSpec.SCIENCE_DAILY_CAP.getAsLong();
        helper.assertTrue(cap > 0L, "The daily science cap is disabled");
        Fixture fixture = fixture();
        ItemStack stack = new ItemStack(Items.STICK);
        fixture.ledger.recordScience(fixture.factionId, System.currentTimeMillis(), cap);

        long capped = fixture.award(stack);
        fixture.ledger.resetDaily(fixture.factionId);
        long nextDay = fixture.award(stack);

        helper.assertTrue(capped == 0L, "A capped day still paid the discovery");
        helper.assertTrue(nextDay == ScienceIncome.discoveryReward(stack), "The discovery was burnt by the capped day");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void discoveriesAndDailyTotalSurviveReload(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        Fixture fixture = fixture();
        ItemStack stack = new ItemStack(Items.STICK);
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        long now = System.currentTimeMillis();

        long first = fixture.award(stack);
        ScienceLedger reloaded = ScienceLedger.FACTORY
                .deserializer()
                .apply(fixture.ledger.save(new CompoundTag(), registries), registries);

        helper.assertTrue(reloaded.isDiscovered(fixture.factionId, itemId), "The discovery did not survive the reload");
        helper.assertTrue(reloaded.discoveryCount(fixture.factionId) == 1, "The reloaded discovery count is wrong");
        helper.assertTrue(
                reloaded.grantedToday(fixture.factionId, now) == first,
                "The reloaded daily total does not match"
        );
        helper.assertTrue(
                ScienceIncome.awardDiscovery(fixture.factions, reloaded, fixture.factionId, stack, null) == 0L,
                "A restarted server paid the discovery again"
        );
        helper.succeed();
    }

    private static void assertTierReward(GameTestHelper helper, long base, int tier, double multiplier) {
        long expected = Math.round(base * multiplier);
        helper.assertTrue(
                ScienceIncome.discoveryReward(base, ScienceTags.discoveryMultiplier(tier)) == expected,
                "Tier " + tier + " did not use its configured multiplier"
        );
    }

    private static Fixture fixture() {
        FactionManager factions = new FactionManager();
        FactionManager.OperationResult created = factions.createFaction(
                UUID.randomUUID(),
                "Science Test",
                new ClaimKey(Level.OVERWORLD, new ChunkPos(0, 0)),
                1
        );
        return new Fixture(factions, new ScienceLedger(), created.factionId());
    }

    private record Fixture(FactionManager factions, ScienceLedger ledger, UUID factionId) {
        private long award(ItemStack stack) {
            return ScienceIncome.awardDiscovery(factions, ledger, factionId, stack, null);
        }

        private long grant(long amount) {
            return ScienceIncome.grantDailyCapped(factions, ledger, factionId, amount, null);
        }

        private long science() {
            return factions.getFactionById(factionId)
                    .map(faction -> faction.influence(InfluenceType.SCIENCE))
                    .orElse(0L);
        }
    }

    private ScienceDiscoveryGameTests() {
    }
}
