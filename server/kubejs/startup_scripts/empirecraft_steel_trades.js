// ForgeEvents exists in STARTUP scripts in KubeJS 2001.6.5, not in server_scripts.
// LOWEST runs after Overgeared adds its emerald <-> steel factories.
const EC_TradeEvent = Java.loadClass('net.minecraftforge.event.village.VillagerTradesEvent')
const EC_TradePriority = Java.loadClass('net.minecraftforge.eventbus.api.EventPriority')
const EC_SteelEmeraldTrade = Java.loadClass('net.stirdrem.overgeared.event.SteelEmeraldTrade')
ForgeEvents.eventBus().addListener(EC_TradePriority.LOWEST, false, EC_TradeEvent, event => {
  event.trades.values().forEach(offers => offers.removeIf(offer => offer instanceof EC_SteelEmeraldTrade))
})
