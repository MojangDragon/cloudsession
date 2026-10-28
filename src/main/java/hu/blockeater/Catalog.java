package hu.blockeater;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Az items.yml és a models.yml betöltése. */
public final class Catalog {
    private final List<ItemDef> items = new ArrayList<>();
    private final Map<Integer, ItemDef> byNumber = new HashMap<>();
    private final Map<String, double[]> models = new HashMap<>();   // model -> {half, width}
    private long goal = 1_000_000_000L;

    public Catalog(BlockEaterPlugin plugin) {
        File f = new File(plugin.getDataFolder(), "items.yml");
        if (!f.exists()) plugin.saveResource("items.yml", false);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        goal = y.getLong("goal", goal);
        for (Map<?, ?> m : y.getMapList("items")) {
            ItemDef d = new ItemDef(
                    num(m.get("number")).intValue(), String.valueOf(m.get("model")), String.valueOf(m.get("name")),
                    ItemDef.Type.valueOf(String.valueOf(m.get("type"))), num(m.get("unlock")).longValue(),
                    num(m.get("cost")).longValue(), num(m.get("radius")).intValue(), num(m.get("speed")).intValue(),
                    num(m.get("move")).doubleValue(), num(m.get("tier")).intValue(), String.valueOf(m.get("desc")));
            items.add(d);
            byNumber.put(d.number(), d);
        }
        items.sort((a, b) -> Integer.compare(a.number(), b.number()));

        try (InputStream in = plugin.getResource("models.yml")) {
            if (in != null) {
                YamlConfiguration mm = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                for (String k : mm.getKeys(false)) {
                    ConfigurationSection s = mm.getConfigurationSection(k);
                    if (s != null) models.put(k, new double[]{s.getDouble("half", 0.25), s.getDouble("width", 0.5)});
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("models.yml betöltése sikertelen: " + e.getMessage());
        }
    }

    private static Number num(Object o) {
        return o instanceof Number n ? n : Double.valueOf(String.valueOf(o));
    }

    public List<ItemDef> all() { return Collections.unmodifiableList(items); }

    public ItemDef get(int number) { return byNumber.get(number); }

    public long goal() { return goal; }

    /** A modell magasságának fele (blokkban, 1-es skálán). */
    public double half(String model) {
        double[] d = models.get(model);
        return d == null ? 0.25 : d[0];
    }

    public ItemDef last() { return items.get(items.size() - 1); }

    /** Az első tárgy, aminek a feloldásához még nem elég a megevett blokk. */
    public ItemDef nextLocked(long eaten) {
        for (ItemDef d : items) if (d.unlock() > eaten) return d;
        return null;
    }
}
