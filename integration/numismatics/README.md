# Numismatics integration contract

The runtime implementation is
`com.geydev.kalfactions.command.NumismaticsEconomy`, because the repository's
main source set only compiles `src/main/java` and the build files are owned by
another workstream.

- Every treasury amount is stored in spurs.
- Coin values come directly from `dev.ithundxr.createnumismatics.content.backend.Coin`.
- Inventory extraction uses `CoinItem.extract` with simulation before commit.
- A payment chooses the smallest representable amount at or above the request,
  then returns the difference using Numismatics denominations.
- Withdrawals and disband refunds use `Coin.asStack`; inventory overflow is
  handled by Minecraft's `Inventory.placeItemBackInInventory`.
