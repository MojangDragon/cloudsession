package hu.blockeater;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class BlockEaterPlugin extends JavaPlugin {
    private Catalog catalog;
    private DataStore data;
    private ItemFactory items;
    private EatEngine engine;
    private HelperManager helpers;
    private VehicleManager vehicles;
    private PortalManager portals;
    private ShopGui shop;
    private Hud hud;
    private ResourcePackServer packServer;
    private NamespacedKey tagKey;
    private boolean protectFloor;
    private int tickN;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        tagKey = new NamespacedKey(this, "tag");
        catalog = new Catalog(this);
        data = new DataStore(this);
        items = new ItemFactory(this);
        engine = new EatEngine(this);
        helpers = new HelperManager(this);
        vehicles = new VehicleManager(this);
        portals = new PortalManager(this);
        shop = new ShopGui(this);
        hud = new Hud(this);
        protectFloor = getConfig().getBoolean("protect-floor", true);

        getServer().getPluginManager().registerEvents(new Listeners(this), this);
        Commands cmds = new Commands(this);
        for (String c : new String[]{"blockeater", "shop"}) {
            var pc = getCommand(c);
            if (pc != null) { pc.setExecutor(cmds); pc.setTabCompleter(cmds); }
        }

        packServer = new ResourcePackServer(this);
        if (getConfig().getBoolean("resource-pack.embedded-server.enabled", false)) {
            int port = getConfig().getInt("resource-pack.embedded-server.port", 8123);
            if (packServer.start(port)) getLogger().info("Resource pack szerver fut a " + port + " porton.");
        }

        // maradék entitások (pl. összeomlás után)
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e.getPersistentDataContainer().has(tagKey, PersistentDataType.STRING)) e.remove();
            }
        }

        getServer().getScheduler().runTaskTimer(this, this::tick, 1L, 1L);

        // már fent lévő játékosok (reload esetén)
        for (Player p : Bukkit.getOnlinePlayers()) hud.show(p);
        getLogger().info(catalog.all().size() + " tárgy betöltve. Cél: " + Fmt.num(catalog.goal()) + " blokk.");
    }

    @Override
    public void onDisable() {
        if (helpers != null) helpers.removeAll();
        if (vehicles != null) vehicles.removeAll();
        if (portals != null) portals.removeAll();
        if (engine != null) engine.clear();
        if (packServer != null) packServer.stop();
        if (data != null) data.saveAll();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (hud != null) hud.hide(p);
        }
    }

    private void tick() {
        tickN++;
        engine.tick();
        helpers.tick();
        vehicles.tick();
        portals.tick();
        if (tickN % 10 == 0) hud.tick();
        int save = Math.max(200, getConfig().getInt("save-interval-ticks", 6000));
        if (tickN % save == 0) data.saveAll();
    }

    public void reloadAll() {
        reloadConfig();
        protectFloor = getConfig().getBoolean("protect-floor", true);
        catalog = new Catalog(this);
        engine.reload();
    }

    /** Jóváír megevett blokkokat (megevett + egyenleg), és jelzi az újonnan feloldott tárgyakat. */
    public void credit(UUID id, long n) {
        PlayerData d = data.get(id);
        long before = d.eaten;
        d.eaten += n;
        d.balance += n;
        d.dirty = true;
        Player p = Bukkit.getPlayer(id);
        if (p == null) return;
        for (ItemDef def : catalog.all()) {
            if (def.unlock() > before && def.unlock() <= d.eaten) {
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                Msg.chat(p, "§aÚj tárgy feloldva a boltban: " + ItemFactory.color(def) + "§l" + def.name()
                        + " §7(ár: " + Fmt.num(def.cost()) + " blokk) §8- §f/shop");
            }
        }
    }

    /** Egy kézi evés (jobb klikk) befejeződött. */
    public void eatFinished(EatEngine.Job j) {
        Player p = Bukkit.getPlayer(j.owner());
        if (p == null) return;
        Msg.bar(p, "§6+" + Fmt.num(j.eaten()) + " blokk §7| összesen: §e" + Fmt.num(data.get(p.getUniqueId()).eaten));
    }

    public void sendResourcePack(Player p) {
        var c = getConfig();
        String url = c.getString("resource-pack.url", "");
        String sha = c.getString("resource-pack.sha1", "");
        if (packServer.running() && (url == null || url.isBlank())) {
            String host = c.getString("resource-pack.embedded-server.public-host", "");
            if (host == null || host.isBlank()) host = Bukkit.getIp().isBlank() ? "localhost" : Bukkit.getIp();
            url = "http://" + host + ":" + c.getInt("resource-pack.embedded-server.port", 8123) + "/blockeater.zip";
            sha = packServer.sha1();
        }
        if (url == null || url.isBlank()) return;
        byte[] hash = null;
        if (sha != null && sha.length() == 40) {
            hash = new byte[20];
            for (int i = 0; i < 20; i++) hash[i] = (byte) Integer.parseInt(sha.substring(i * 2, i * 2 + 2), 16);
        }
        String prompt = c.getString("resource-pack.prompt", "");
        p.setResourcePack(url, hash, prompt == null ? "" : prompt, c.getBoolean("resource-pack.force", false));
    }

    public Catalog catalog() { return catalog; }
    public DataStore data() { return data; }
    public ItemFactory items() { return items; }
    public EatEngine engine() { return engine; }
    public HelperManager helpers() { return helpers; }
    public VehicleManager vehicles() { return vehicles; }
    public PortalManager portals() { return portals; }
    public ShopGui shop() { return shop; }
    public Hud hud() { return hud; }
    public NamespacedKey tagKey() { return tagKey; }
    public boolean protectFloor() { return protectFloor; }
}
