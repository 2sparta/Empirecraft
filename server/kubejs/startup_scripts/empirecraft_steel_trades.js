// ForgeEvents exists in STARTUP scripts in KubeJS 2001.6.5, not in server_scripts.
// LOWEST runs after Overgeared adds its emerald <-> steel factories.
const EC_TradeEvent = Java.loadClass('net.minecraftforge.event.village.VillagerTradesEvent')
const EC_TradePriority = Java.loadClass('net.minecraftforge.eventbus.api.EventPriority')
const EC_SteelEmeraldTrade = Java.loadClass('net.stirdrem.overgeared.event.SteelEmeraldTrade')
ForgeEvents.eventBus().addListener(EC_TradePriority.LOWEST, false, EC_TradeEvent, event => {
  // Rhino 2001 probes named Map members with String keys; Fastutil's int map rejects them.
  // Vanilla villager career levels are 1..5. Numeric indexing keeps the keys as integers.
  for (var level = 1; level <= 5; level++) {
    if (!(level in event.trades)) continue
    var offers = event.trades[level]
    if (offers) offers.removeIf(offer => offer instanceof EC_SteelEmeraldTrade)
  }
})
