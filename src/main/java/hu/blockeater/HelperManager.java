package hu.blockeater;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Segédek: követik a játékost és megeszik a blokkokat maguk körül. */
public final class HelperManager {
    private static final class Helper {
        ItemDisplay display;
        ItemDef def;
        EatEngine.Job job;
        Location lastCenter;
        int sinceJob;
    }

    private final BlockEaterPlugin plugin;
    private final Map<UUID, Helper> helpers = new HashMap<>();
    private int tick;

    public HelperManager(BlockEaterPlugin plugin) { this.plugin = plugin; }

    public boolean has(UUID id) { return helpers.containsKey(id); }

    /** Előhívja / elküldi a segédet. */
    public void toggle(Player p, ItemDef def) {
        Helper cur = helpers.get(p.getUniqueId());
        if (cur != null) {
            boolean same = cur.def.number() == def.number();
            remove(p.getUniqueId());
            if (same) {
                Msg.bar(p, "§7A segéd elment pihenni.");
                return;
            }
        }
        if (!plugin.engine().worldAllowed(p.getWorld())) {
            Msg.bar(p, "§cEbben a világban nem működik.");
            return;
        }
        Helper h = new Helper();
        h.def = def;
        Location l = anchor(p);
        double scale = 1.1 + def.tier() * 0.12;
        h.display = p.getWorld().spawn(l, ItemDisplay.class, e -> {
            e.setItemStack(plugin.items().create(def));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            e.setTransformation(new Transformation(new Vector3f(0, (float) (plugin.catalog().half(def.model()) * scale), 0),
                    new Quaternionf(), new Vector3f((float) scale), new Quaternionf()));
            e.setTeleportDuration(3);
            e.setBrightness(new Display.Brightness(14, 14));
            e.setPersistent(false);
            e.getPersistentDataContainer().set(plugin.tagKey(), PersistentDataType.STRING, "helper");
        });
        helpers.put(p.getUniqueId(), h);
        p.getWorld().spawnParticle(Particle.CLOUD, l, 20, 0.3, 0.3, 0.3, 0.05);
        Msg.bar(p, ItemFactory.color(def) + def.name() + " §7követ és eszi a blokkokat.");
    }

    private Location anchor(Player p) {
        Location l = p.getLocation();
        double yaw = Math.toRadians(l.getYaw());
        double rx = Math.cos(yaw), rz = Math.sin(yaw);        // balra mutató vektor
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);       // előre
        double bob = Math.sin(tick / 8.0) * 0.12;
        Location a = l.clone().add(-rx * 1.4 - fx * 0.6, 0.9 + bob, -rz * 1.4 - fz * 0.6);
        a.setYaw(l.getYaw() + 180);
        a.setPitch(0);
        return a;
    }

    public void remove(UUID id) {
        Helper h = helpers.remove(id);
        if (h == null) return;
        if (h.display != null) h.display.remove();
        if (h.job != null) h.job.cancel();
    }

    public void removeAll() {
        for (UUID id : new java.util.ArrayList<>(helpers.keySet())) remove(id);
    }

    public void tick() {
        tick++;
        for (UUID id : new java.util.ArrayList<>(helpers.keySet())) {
            Helper h = helpers.get(id);
            Player p = plugin.getServer().getPlayer(id);
            if (p == null || !p.isOnline() || p.isDead() || h.display == null || !h.display.isValid()
                    || !plugin.engine().worldAllowed(p.getWorld())) {
                remove(id);
                continue;
            }
            Location a = anchor(p);
            if (!a.getWorld().equals(h.display.getWorld())) {
                h.display.teleport(a);
            } else if (h.display.getLocation().distanceSquared(a) > 0.01) {
                h.display.teleport(a);
            }
            h.sinceJob++;
            if (h.job == null || h.job.isDone()) {
                Location c = h.display.getLocation();
                boolean moved = h.lastCenter == null || !h.lastCenter.getWorld().equals(c.getWorld())
                        || h.lastCenter.distanceSquared(c) > 2.25;
                if (moved || h.sinceJob > 40) {
                    h.lastCenter = c.clone();
                    h.sinceJob = 0;
                    h.job = plugin.engine().sphere(id, c, h.def.radius(), h.def.speed(),
                            plugin.protectFloor() ? () -> p.isOnline() ? EatEngine.floorBox(p.getLocation()) : null : null, false);
                }
            }
        }
    }
}
