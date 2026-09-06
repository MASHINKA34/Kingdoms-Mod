package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.market.MarketPlot;
import com.geydev.kalfactions.market.MarketPlotManager;
import com.geydev.kalfactions.market.MarketPlotService;
import dev.ithundxr.createnumismatics.Numismatics;
import dev.ithundxr.createnumismatics.content.backend.BankAccount;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MarketPaymentGameTests {
    @GameTest(template = "empty", batch = "market_payment")
    public static void resaleRejectsOverflowBeforeChargingBuyer(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var buyer = RegressionPlayers.create(level, pos, 0).player();
        UUID sellerId = UUID.randomUUID();
        BankAccount seller = Numismatics.BANK.getOrCreateAccount(sellerId, BankAccount.Type.PLAYER);
        BankAccount buyerAccount = Numismatics.BANK.getOrCreateAccount(buyer.getUUID(), BankAccount.Type.PLAYER);
        seller.deposit(Integer.MAX_VALUE - 99);
        buyerAccount.deposit(100);
        MarketPlotManager manager = MarketPlotManager.get(level);
        MarketPlot plot = manager.create(level.dimension(), new BoundingBox(pos), 100);
        plot.setOwner(sellerId, "Seller");
        plot.setResalePrice(100);
        try {
            MarketPlotService.buy(buyer, plot.id());
            helper.assertValueEqual(buyerAccount.getBalance(), 100, "rejected purchase preserves buyer funds");
            helper.assertValueEqual(seller.getBalance(), Integer.MAX_VALUE - 99, "seller balance cannot overflow");
            helper.assertTrue(plot.isOwnedBy(sellerId), "rejected purchase preserves ownership");
            seller.deduct(1);
            MarketPlotService.buy(buyer, plot.id());
            helper.assertValueEqual(buyerAccount.getBalance(), 0, "successful purchase charged once");
            helper.assertValueEqual(seller.getBalance(), Integer.MAX_VALUE, "exact bank limit is accepted");
            helper.assertTrue(plot.isOwnedBy(buyer.getUUID()), "successful purchase transfers ownership");
        } finally {
            manager.remove(plot.id());
            seller.deduct(seller.getBalance());
            buyerAccount.deduct(buyerAccount.getBalance());
        }
        helper.succeed();
    }

    private MarketPaymentGameTests() {
    }
}
