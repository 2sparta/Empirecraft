package guardtest;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import ua.empirecraft.feudal.Database;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("empirecraft_towny_probe")
@Mod.EventBusSubscriber(modid = "empirecraft_towny_probe")
public final class ForgeImpactProbe {
    private static int ticks;

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && ticks > 0 && --ticks == 0) verify(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void test(ServerStartedEvent event) {
        ticks = 40;
    }

    private static void verify(net.minecraft.server.MinecraftServer server) {
        server.execute(() -> {
            try {
                var level = server.m_129783_();
                var claim = new BlockPos(Integer.getInteger("empirecraft.test.claimX", 8), 65,
                        Integer.getInteger("empirecraft.test.claimZ", 8));
                var wilderness = new BlockPos(Integer.getInteger("empirecraft.test.wildX", 24), 65,
                        Integer.getInteger("empirecraft.test.wildZ", 8));
                if (Boolean.getBoolean("empirecraft.test.freshFeudal")) verifyFresh(server, level, claim);
                if (Boolean.getBoolean("empirecraft.test.mailRestart")) verifyMailRestart(server, level);
                for (String owner : new String[]{"claimguard", "siegeworks"}) {
                    Class<?> gate = Class.forName("ua.empirecraft." + owner + ".TownyGate");
                    Class<?> target = Class.forName(gate.getName() + "$Target");
                    var allows = gate.getMethod("allows", Object.class, int.class, int.class, int.class, target, UUID.class);
                    Object instance = gate.getConstructor().newInstance();
                    for (Object type : target.getEnumConstants()) {
                        require(!(Boolean) allows.invoke(instance, level, claim.m_123341_(), claim.m_123342_(),
                                claim.m_123343_(), type, null), owner + " claim " + type);
                        require((Boolean) allows.invoke(instance, level, wilderness.m_123341_(), wilderness.m_123342_(),
                                wilderness.m_123343_(), type, null), owner + " wilderness " + type);
                    }
                }
                var terrain = Class.forName("ua.empirecraft.tfc.OneTimeTerrain")
                        .getDeclaredMethod("wilderness", ServerLevel.class, BlockPos.class);
                terrain.setAccessible(true);
                require(!(Boolean) terrain.invoke(null, level, claim), "TFC protected claim");
                require((Boolean) terrain.invoke(null, level, wilderness), "TFC changes in wilderness");
                if (Boolean.getBoolean("empirecraft.test.nativeBridges")) {
                    Class<?> map = Class.forName("ua.empirecraft.townymap.TownyAccess");
                    var constructor = map.getDeclaredConstructor();
                    constructor.setAccessible(true);
                    var layers = map.getDeclaredMethod("snapshotAll", boolean.class);
                    layers.setAccessible(true);
                    var views = (java.util.Map<?, ?>) layers.invoke(constructor.newInstance(), true);
                    require(views.values().stream().filter(java.util.Map.class::isInstance)
                            .map(java.util.Map.class::cast).anyMatch(view -> !view.isEmpty()), "JourneyMap projected native claims");
                    Class<?> recruits = Class.forName("ua.townyrecruits.Bridge");
                    var instance = recruits.getDeclaredField("INSTANCE");
                    instance.setAccessible(true);
                    String status = (String) recruits.getMethod("status").invoke(instance.get(null));
                    require(status.contains("ready=true") && status.contains("census=true"), "TownyRecruits " + status);
                    if (Boolean.getBoolean("empirecraft.test.recruitMenu")) {
                        var commands = server.m_129892_().m_82094_().getRoot();
                        var hiring = commands.getChild("trecruit");
                        require(hiring != null && hiring.getChild("hire") != null && hiring.getChild("dismiss") != null,
                                "Native hiring and dismissal commands");
                        var recruitment = recruits.getDeclaredField("recruitment");
                        recruitment.setAccessible(true);
                        Object service = recruitment.get(instance.get(null));
                        var fake = FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "TestRecruitMenu"));
                        Class<?> menuType = Class.forName("ua.townyrecruits.RecruitmentCommands$ForgeHireMenu");
                        var menuConstructor = menuType.getDeclaredConstructor(int.class, Inventory.class, ServerPlayer.class, service.getClass());
                        menuConstructor.setAccessible(true);
                        var menu = (ChestMenu) menuConstructor.newInstance(1, fake.m_150109_(), fake, service);
                        for (int slot : new int[]{11, 13, 15}) {
                            require(!menu.m_39261_().m_8020_(slot).m_41619_(), "Native recruit menu action at " + slot);
                            require(menu.m_7648_(fake, slot).m_41619_(), "Recruit menu icons cannot be extracted");
                        }
                    }
                    Class<?> towny = Class.forName("ua.empirecraft.townyforge.TownyForge");
                    String archive = (String) towny.getMethod("feudalMap").invoke(null);
                    require(archive != null && archive.contains("\"estates\""), "Feudal map archive available");
                    System.out.println("FORGE_TOWNY_BRIDGES_PASS " + status);
                }
                System.out.println("FORGE_TOWNY_IMPACT_PASS");
            } catch (Exception | AssertionError error) {
                System.out.println("FORGE_TOWNY_IMPACT_FAIL " + error);
                error.printStackTrace();
            } finally {
                server.m_7570_(false);
            }
        });
    }

    private static void verifyFresh(net.minecraft.server.MinecraftServer server, ServerLevel level, BlockPos claim)
            throws Exception {
        var towny = Class.forName("ua.empirecraft.townyforge.TownyForge");
        String initial = (String) towny.getMethod("integrationSnapshot").invoke(null);
        require(initial.contains("\"towns\":{}") && initial.contains("\"claims\":[]"), "empty Towny world");
        var providerType = Class.forName("ua.empirecraft.feudal.ForgeFeudal");
        Object provider = providerType.getMethod("active").invoke(null);
        require(provider != null, "live Feudal in the fresh world");
        var sails = Class.forName("com.quintonc.vs_sails.protection.FleetNative");
        require(sails.getMethod("plugin").invoke(null) == provider, "Valkyrien Skies fleet provider");
        var dispatcher = server.m_129892_().m_82094_();
        var town = dispatcher.getRoot().getChild("town");
        require(town.getChild("jail") == null && town.getChild("unjail") == null, "jail commands removed");
        for (String command : new String[]{"market", "treasury", "provisions", "fleet", "shiprequest", "currency"}) {
            require(dispatcher.getRoot().getChild(command) != null, "registered " + command);
        }
        var mayor = FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "ForgeMayor"));
        mayor.m_6034_(claim.m_123341_() + .5, claim.m_123342_(), claim.m_123343_() + .5);
        var source = mayor.m_20203_().m_81325_(0);
        require(server.m_129892_().m_230957_(source, "town new ForgeProbe") == 1, "found a fresh town");
        require(server.m_129892_().m_230957_(source, "town state status") == 1, "native Feudal town synchronization");
        require(server.m_129892_().m_230957_(source, "treasury balance") == 1, "native town treasury");
        require(server.m_129892_().m_230957_(source, "market create Forge Bazaar") == 1, "native market project");
        if (Boolean.getBoolean("empirecraft.test.provisionInventory")) {
            var bread = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.f_42406_, 16);
            mayor.m_150109_().f_35974_.set(0, bread);
            require(server.m_129892_().m_230957_(source, "provisions deposit 1 8") == 1, "save provision deposit to disk");
            require(mayor.m_150109_().f_35974_.get(0).m_41613_() == 8, "inventory debit");
            require(server.m_129892_().m_230957_(source, "provisions withdraw 1 4") == 1, "save provision withdrawal to disk");
            require(mayor.m_150109_().f_35974_.get(0).m_41613_() == 12, "inventory credit");
        }
        if (Boolean.getBoolean("empirecraft.test.fairMail")) verifyFairMail(server, level, mayor, claim, provider);
        String map = (String) providerType.getMethod("mapView").invoke(null);
        var request = providerType.getMethod("engagementRequest", String.class, java.util.Map.class);
        var point = request.invoke(provider, "point", java.util.Map.of("town", "", "world", "minecraft:overworld",
                "x", claim.m_123341_(), "z", claim.m_123343_()));
        require(point.toString().contains("UNKNOWN") && map.contains("ForgeProbe"), "native engagement classifies the new claim");
        System.out.println("FORGE_FEUDAL_FRESH_PASS");
    }

    @SuppressWarnings("unchecked")
    private static void verifyFairMail(net.minecraft.server.MinecraftServer server, ServerLevel level, ServerPlayer mayor,
                                       BlockPos claim, Object provider) throws Exception {
        var database = provider.getClass().getDeclaredField("db");
        database.setAccessible(true);
        var db = (Database) database.get(provider);
        String market = Database.string(db.one("SELECT id FROM cm_markets"), "id");
        var commands = server.m_129892_();
        var source = mayor.m_20203_().m_81325_(2);
        int x = claim.m_123341_(), y = claim.m_123342_(), z = claim.m_123343_();
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            level.m_7731_(claim.m_7918_(dx, -1, dz), Blocks.f_50069_.m_49966_(), 2);
        }
        require(commands.m_230957_(source, "market claim " + market + " " + (x >> 4) + " " + (z >> 4) + " true probe") == 1, "claim market");
        var bounds = (x - 2) + " " + (y - 1) + " " + (z - 2) + " " + (x + 2) + " " + (y + 3) + " " + (z + 2);
        require(commands.m_230957_(source, "market building " + market + " GOODS " + bounds + " " + x + " " + (y - 1) + " " + z + " probe") == 1, "register market building");
        require(commands.m_230957_(source, "market submit " + market) == 1, "submit market");
        require(commands.m_230957_(source, "market review " + market + " GOODS true probe") == 1, "review market");
        require(commands.m_230957_(source, "market state " + market + " OPEN probe") == 1, "open market");
        require(commands.m_230957_(source, "town menu") == 1 && commands.m_230957_(source, "market menu") == 1, "Forge command menus");
        require(commands.m_230957_(source, "fleet menu") == 1 && commands.m_230957_(source, "currency menu") == 1, "fleet and currency command menus");

        var withUuid = net.minecraft.server.players.PlayerList.class.getDeclaredField("f_11197_");
        withUuid.setAccessible(true);
        var players = (Map<UUID, ServerPlayer>) withUuid.get(server.m_6846_());
        players.put(mayor.m_20148_(), mayor);
        try {
            var contact = Class.forName("ua.empirecraft.townymap.contact.ContactMail");
            Object data = contact.getMethod("data").invoke(null);
            UUID recipient = UUID.randomUUID();
            var pos = claim.m_7918_(1, 0, 1);
            var box = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("contact", "white_mailbox"));
            require(box != null, "installed Contact mailbox");
            var property = BlockStateProperties.f_61401_;
            level.m_7731_(pos, box.m_49966_().m_61124_(property, DoubleBlockHalf.LOWER), 2);
            level.m_7731_(pos.m_7494_(), box.m_49966_().m_61124_(property, DoubleBlockHalf.UPPER), 2);
            data.getClass().getMethod("resetMailboxContents", UUID.class).invoke(data, recipient);
            data.getClass().getMethod("setMailboxData", UUID.class, ResourceKey.class, BlockPos.class)
                    .invoke(data, recipient, level.m_46472_(), pos.m_7494_());
            String start = ZonedDateTime.now(ZoneId.of("Europe/Kyiv")).plusDays(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
            require(commands.m_230957_(source, "market fair create " + market + " " + start
                    + " 2 GOODS * 0 0 0 Forge_Fair Welcome") == 1, "create fair");
            String fair = Database.string(db.one("SELECT id FROM cm_fairs"), "id");
            require(commands.m_230957_(source, "market fair letter edit " + fair + " Запрошення на ярмарок") == 1, "edit invitation");
            var inventory = mayor.m_150109_();
            inventory.f_35977_ = 0;
            inventory.f_35974_.set(0, new ItemStack(Items.f_42516_, 3));
            require(commands.m_230957_(source, "market fair letter preview " + fair) == 1, "physical postcard sample");
            require(inventory.f_35974_.get(0).m_41613_() == 2, "sample uses exactly one paper");
            require(commands.m_230957_(source, "market fair letter preview " + fair) == 1
                    && inventory.f_35974_.get(0).m_41613_() == 2, "preview replay is free");
            int sampleSlot = 1;
            var card = inventory.f_35974_.get(sampleSlot);
            require(!card.m_41619_() && card.m_41783_().m_128461_("Text").contains("Запрошення"), "Contact sample contains the invitation");
            inventory.f_35977_ = sampleSlot;
            var sample = db.one("SELECT id,version FROM cm_mail_samples WHERE fair=?", fair);
            String confirm = "market fair letter confirm " + fair + " " + Database.number(sample, "version") + " " + Database.string(sample, "id");
            require(commands.m_230957_(source, confirm) == 1, "queue postage");
            require(inventory.f_35974_.get(0).m_41613_() == 1 && inventory.f_35974_.get(sampleSlot).m_41619_(), "sample and one copy consumed");
            require(commands.m_230957_(source, "market mail status " + fair) == 1, "mail status command");
            var marketsField = provider.getClass().getDeclaredField("markets");
            marketsField.setAccessible(true);
            Object markets = marketsField.get(provider);
            Object fairs = markets.getClass().getField("fairs").get(markets);
            Object mail = fairs.getClass().getField("mail").get(fairs);
            mail.getClass().getMethod("deliver").invoke(mail);
            var contents = (SimpleContainer) data.getClass().getMethod("getMailboxContents", UUID.class).invoke(data, recipient);
            require(contents.m_8020_(0).m_41783_().m_128461_("Text").equals(card.m_41783_().m_128461_("Text")),
                    "one real Contact postcard delivered to an offline recipient");
            // Simulate a lost H2 acknowledgement after the native write reached disk.
            db.execute("UPDATE cm_mail_jobs SET state='SENDING',next_try=0 WHERE person=?", recipient.toString());
            mail.getClass().getMethod("deliver").invoke(mail);
            require(contents.m_8020_(1).m_41619_() && db.one("SELECT id FROM cm_mail_jobs WHERE state='DELIVERED'") != null,
                    "persistent Contact receipt prevents duplicate delivery");
            System.out.println("FORGE_FEUDAL_MAIL_PASS");
        } finally {
            players.remove(mayor.m_20148_());
        }
    }

    @SuppressWarnings("unchecked")
    private static void verifyMailRestart(net.minecraft.server.MinecraftServer server, ServerLevel level) throws Exception {
        Class<?> type = Class.forName("ua.empirecraft.feudal.ForgeFeudal");
        var provider = type.getMethod("active").invoke(null);
        var database = type.getDeclaredField("db");
        database.setAccessible(true);
        var db = (Database) database.get(provider);
        var job = db.one("SELECT j.id,j.person,s.actor FROM cm_mail_jobs j JOIN cm_mail_samples s ON s.id=j.sample "
                + "WHERE j.state='DELIVERED'");
        require(job != null, "Feudal acknowledged delivery before restarting");
        UUID recipient = UUID.fromString(Database.string(job, "person"));
        var contact = Class.forName("ua.empirecraft.townymap.contact.ContactMail");
        Object data = contact.getMethod("data").invoke(null);
        var contents = (SimpleContainer) data.getClass().getMethod("getMailboxContents", UUID.class).invoke(data, recipient);
        require(!contents.m_8020_(0).m_41619_() && contents.m_8020_(1).m_41619_(), "Contact restored one invitation after restart");

        UUID sender = UUID.fromString(Database.string(job, "actor"));
        var player = FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(sender, "ForgeMayor"));
        var playersByUuid = net.minecraft.server.players.PlayerList.class.getDeclaredField("f_11197_");
        playersByUuid.setAccessible(true);
        var players = (Map<UUID, ServerPlayer>) playersByUuid.get(server.m_6846_());
        players.put(sender, player);
        try {
            var commands = server.m_129892_();
            var source = player.m_20203_().m_81325_(2);
            var created = (Map<String, Object>) contact.getMethod("handleNative", Map.class).invoke(null,
                    Map.of("action", "template", "actor", sender.toString(), "sample", UUID.randomUUID().toString(), "text", "Forge restart"));
            require(created.get("item") instanceof ItemStack, "native template without Bukkit classes");
            require(commands.m_230957_(source, "town state charter ForgeProbe Пробний_статут_нової_держави") == 1, "state decision command");
            String decision = Database.string(db.one("SELECT id FROM political_decisions WHERE action='CHARTER' AND status='OPEN'"), "id");
            require(commands.m_230957_(source, "town state vote " + decision + " true") == 1, "state vote command");
            require(db.one("SELECT id FROM political_decisions WHERE id=? AND status='APPLIED'", decision) != null, "state vote applied");
            require(commands.m_230957_(source, "town union create ForgeProbe CONFEDERATION Forge League") == 1, "union draft command");
            require(commands.m_230957_(source, "town dynasty house ForgeProbe Forge House") == 1, "dynasty command");
            require(commands.m_230957_(source, "market fair list") == 1 && commands.m_230957_(source, "market mail status") == 1,
                    "restored fair and mail commands");
        } finally {
            players.remove(sender);
        }

        String id = Database.string(job, "id");
        db.execute("UPDATE cm_mail_jobs SET state='SENDING',next_try=0 WHERE id=?", id);
        var marketsField = type.getDeclaredField("markets");
        marketsField.setAccessible(true);
        Object markets = marketsField.get(provider);
        Object fairs = markets.getClass().getField("fairs").get(markets);
        Object mail = fairs.getClass().getField("mail").get(fairs);
        mail.getClass().getMethod("deliver").invoke(mail);
        require(contents.m_8020_(1).m_41619_() && db.one("SELECT id FROM cm_mail_jobs WHERE id=? AND state='DELIVERED'", id) != null,
                "persistent Contact receipt prevents duplication across restart");
        System.out.println("FORGE_FEUDAL_MAIL_RESTART_PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
