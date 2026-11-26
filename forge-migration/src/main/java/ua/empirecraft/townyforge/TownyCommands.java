final class TownyCommands {
    private final java.util.function.Supplier<TownyStore> store;
    private final TownyTravel travel;

    TownyCommands(java.util.function.Supplier<TownyStore> store) {
        this.store = store;
        travel = new TownyTravel(store);
    }
}
