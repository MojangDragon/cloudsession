package hu.blockeater;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Játékosadatok: plugins/BlockEater/players/<uuid>.yml */
public final class DataStore {
    private final BlockEaterPlugin plugin;
    private final File dir;
    private final Map<UUID, PlayerData> cache = new HashMap<>();

    public DataStore(BlockEaterPlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "players");
        dir.mkdirs();
    }

    public PlayerData get(UUID id) {
        return cache.computeIfAbsent(id, this::load);
    }

    private PlayerData load(UUID id) {
        PlayerData d = new PlayerData();
        d.hud = plugin.getConfig().getBoolean("hud-default", true);
        File f = new File(dir, id + ".yml");
        if (f.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            d.eaten = y.getLong("eaten");
            d.balance = y.getLong("balance");
            d.hud = y.getBoolean("hud", d.hud);
            List<Integer> own = y.getIntegerList("owned");
            d.owned.addAll(own);
        }
        return d;
    }

    public void save(UUID id) {
        PlayerData d = cache.get(id);
        if (d == null || !d.dirty) return;
        YamlConfiguration y = new YamlConfiguration();
        y.set("eaten", d.eaten);
        y.set("balance", d.balance);
        y.set("hud", d.hud);
        y.set("owned", new java.util.ArrayList<>(d.owned));
        try {
            y.save(new File(dir, id + ".yml"));
            d.dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("Mentés sikertelen (" + id + "): " + e.getMessage());
        }
    }

    public void saveAll() {
        for (UUID id : new java.util.ArrayList<>(cache.keySet())) save(id);
    }

    public void unload(UUID id) {
        save(id);
        cache.remove(id);
    }
}
