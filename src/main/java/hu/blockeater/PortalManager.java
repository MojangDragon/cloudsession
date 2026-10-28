package hu.blockeater;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** A bárhová előhívható bolt-portál. */
public final class PortalManager {
    private static final double SCALE = 1.7;

    private static final class Portal {
        ItemDisplay display;
        Interaction hit;
        long expireTick;
    }

    private final BlockEaterPlugin plugin;
    private final Map<UUID, Portal> portals = new HashMap<>();
    private long tick;

    public PortalManager(BlockEaterPlugin plugin) { this.plugin = plugin; }

    public void summon(Player p) {
        remove(p.getUniqueId(), false);
        Location pl = p.getLocation();
        Vector dir = pl.getDirection().setY(0);
        if (dir.lengthSquared() < 1e-6) dir = new Vector(0, 0, 1);
        dir.normalize();
        Location l = pl.clone().add(dir.multiply(3));
        l.setPitch(0);
        l.setYaw(pl.getYaw());

        Portal po = new Portal();
        double half = plugin.catalog().half("shop_portal");
        po.display = l.getWorld().spawn(l, ItemDisplay.class, e -> {
            e.setItemStack(plugin.items().portalStack());
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            e.setTransformation(new Transformation(new Vector3f(0, (float) (half * SCALE), 0),
                    new Quaternionf(), new Vector3f((float) SCALE), new Quaternionf()));
            e.setBrightness(new Display.Brightness(15, 15));
            e.setPersistent(false);
            e.getPersistentDataContainer().set(plugin.tagKey(), PersistentDataType.STRING, "portal");
        });
        po.hit = l.getWorld().spawn(l, Interaction.class, e -> {
            e.setInteractionWidth(2.4f);
            e.setInteractionHeight(2.8f);
            e.setResponsive(true);
            e.setPersistent(false);
            e.getPersistentDataContainer().set(plugin.tagKey(), PersistentDataType.STRING, "portal");
        });
        po.expireTick = tick + plugin.getConfig().getLong("portal-seconds", 90) * 20L;
        portals.put(p.getUniqueId(), po);
        l.getWorld().playSound(l, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.6f);
        l.getWorld().spawnParticle(Particle.PORTAL, l.clone().add(0, 1.3, 0), 80, 0.8, 1.2, 0.8, 0.4);
        Msg.bar(p, "§dBolt-portál előhívva! §7Jobb klikk a portálra a megnyitáshoz.");
    }

    public boolean isPortalEntity(Entity e) {
        String t = e.getPersistentDataContainer().get(plugin.tagKey(), PersistentDataType.STRING);
        return "portal".equals(t);
    }

    public void remove(UUID owner, boolean effect) {
        Portal po = portals.remove(owner);
        if (po == null) return;
        if (effect && po.display.isValid()) {
            Location l = po.display.getLocation().add(0, 1.3, 0);
            l.getWorld().spawnParticle(Particle.PORTAL, l, 60, 0.6, 1.0, 0.6, 0.5);
        }
        po.display.remove();
        po.hit.remove();
    }

    public void removeAll() {
        for (UUID id : new java.util.ArrayList<>(portals.keySet())) remove(id, false);
    }

    public void tick() {
        tick++;
        for (UUID id : new java.util.ArrayList<>(portals.keySet())) {
            Portal po = portals.get(id);
            if (!po.display.isValid() || !po.hit.isValid() || tick >= po.expireTick) {
                remove(id, true);
                continue;
            }
            if (tick % 8 == 0) {
                Location l = po.display.getLocation().add(0, 1.3, 0);
                l.getWorld().spawnParticle(Particle.PORTAL, l, 6, 0.7, 1.1, 0.4, 0.3);
            }
        }
    }
}
