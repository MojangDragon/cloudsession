package hu.blockeater;

import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
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

/** Autók és dózerek: beleülsz, vezeted (W/A/S/D), és megeszik, ami az útjukba kerül. */
public final class VehicleManager {
    private static final class Vehicle {
        ArmorStand seat;
        ItemDisplay display;
        ItemDef def;
        UUID driver;
        EatEngine.Job job;
        int age;
        double scale;
    }

    private final BlockEaterPlugin plugin;
    private final Map<UUID, Vehicle> byDriver = new HashMap<>();

    public VehicleManager(BlockEaterPlugin plugin) { this.plugin = plugin; }

    public boolean has(UUID driver) { return byDriver.containsKey(driver); }

    public boolean isSeat(org.bukkit.entity.Entity e) {
        for (Vehicle v : byDriver.values()) if (v.seat != null && v.seat.equals(e)) return true;
        return false;
    }

    public UUID driverOfSeat(org.bukkit.entity.Entity e) {
        for (Vehicle v : byDriver.values()) if (v.seat != null && v.seat.equals(e)) return v.driver;
        return null;
    }

    public void spawn(Player p, ItemDef def, Location at) {
        remove(p.getUniqueId());
        if (!plugin.engine().worldAllowed(p.getWorld())) {
            Msg.bar(p, "§cEbben a világban nem működik.");
            return;
        }
        Vehicle v = new Vehicle();
        v.def = def;
        v.driver = p.getUniqueId();
        v.scale = def.type() == ItemDef.Type.DOZER ? 2.6 : 2.2;
        Location l = at.clone();
        l.setYaw(p.getLocation().getYaw());
        l.setPitch(0);
        v.seat = l.getWorld().spawn(l, ArmorStand.class, s -> {
            s.setInvisible(true);
            s.setSilent(true);
            s.setInvulnerable(true);
            s.setBasePlate(false);
            s.setArms(false);
            s.setCollidable(false);
            s.setPersistent(false);
            s.setGravity(true);
            s.getPersistentDataContainer().set(plugin.tagKey(), PersistentDataType.STRING, "vehicle");
        });
        double half = plugin.catalog().half(def.model());
        v.display = l.getWorld().spawn(l, ItemDisplay.class, e -> {
            e.setItemStack(plugin.items().create(def));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            e.setTransformation(new Transformation(new Vector3f(0, (float) (half * v.scale), 0),
                    new Quaternionf(), new Vector3f((float) v.scale), new Quaternionf()));
            e.setTeleportDuration(2);
            e.setBrightness(new Display.Brightness(14, 14));
            e.setPersistent(false);
            e.getPersistentDataContainer().set(plugin.tagKey(), PersistentDataType.STRING, "vehicle");
        });
        byDriver.put(p.getUniqueId(), v);
        v.seat.addPassenger(p);
        l.getWorld().spawnParticle(Particle.CLOUD, l, 25, 0.8, 0.3, 0.8, 0.05);
        Msg.bar(p, ItemFactory.color(def) + def.name() + " §7- W/A/S/D: vezetés, guggolás: leszállás");
    }

    public void remove(UUID driver) {
        Vehicle v = byDriver.remove(driver);
        if (v == null) return;
        if (v.job != null) v.job.cancel();
        if (v.display != null) v.display.remove();
        if (v.seat != null) {
            v.seat.eject();
            v.seat.remove();
        }
    }

    public void removeAll() {
        for (UUID id : new java.util.ArrayList<>(byDriver.keySet())) remove(id);
    }

    public void tick() {
        for (UUID id : new java.util.ArrayList<>(byDriver.keySet())) {
            Vehicle v = byDriver.get(id);
            Player p = plugin.getServer().getPlayer(id);
            v.age++;
            if (p == null || !p.isOnline() || p.isDead() || v.seat == null || !v.seat.isValid()
                    || v.display == null || !v.display.isValid()
                    || (v.age > 5 && !v.seat.getPassengers().contains(p))) {
                remove(id);
                continue;
            }
            drive(v, p);
        }
    }

    private void drive(Vehicle v, Player p) {
        Input in = p.getCurrentInput();
        double f = (in.isForward() ? 1 : 0) - (in.isBackward() ? 1 : 0);
        double s = (in.isLeft() ? 1 : 0) - (in.isRight() ? 1 : 0);
        Location sl = v.seat.getLocation();
        double yaw = Math.toRadians(p.getLocation().getYaw());
        Vector fwd = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        Vector left = new Vector(Math.cos(yaw), 0, Math.sin(yaw));
        double speed = v.def.move();
        Vector horizontal = fwd.clone().multiply(f * speed).add(left.clone().multiply(s * speed * 0.5));
        double vy = v.seat.getVelocity().getY();
        if (plugin.getConfig().getBoolean("debug", false) && v.age % 20 == 0) {
            plugin.getLogger().info("[debug] jármű bemenet f=" + f + " s=" + s + " vel=" + horizontal + " pos=" + sl.toVector()
                    + " onGround=" + v.seat.isOnGround());
        }

        // 1 blokkos lépcsők / a kitermelt út szélének átugrása
        if (horizontal.lengthSquared() > 0.0001 && v.seat.isOnGround()) {
            Vector n = horizontal.clone().normalize();
            Block ahead = sl.clone().add(n.getX() * 0.9, 0.1, n.getZ() * 0.9).getBlock();
            if (!ahead.isPassable() && ahead.getRelative(0, 1, 0).isPassable()
                    && ahead.getRelative(0, 2, 0).isPassable()) vy = 0.42;
        }
        v.seat.setVelocity(new Vector(horizontal.getX(), vy, horizontal.getZ()));

        // a megjelenítés követi az ülést
        Location dl = sl.clone();
        dl.setYaw((float) Math.toDegrees(yaw) + 180f);
        dl.setPitch(0);
        v.display.teleport(dl);

        // evés: a járműtől előre, egy folyosót nyit (a gömb alja a talajszint)
        if (horizontal.lengthSquared() > 0.0001 && (v.job == null || v.job.isDone())) {
            Vector n = horizontal.clone().normalize();
            int r = v.def.radius();
            Location c = sl.clone().add(n.getX() * (r * 0.6 + 0.6), r, n.getZ() * (r * 0.6 + 0.6));
            v.job = plugin.engine().sphere(v.driver, c, r, v.def.speed(),
                    () -> v.seat.isValid() ? EatEngine.floorBox(v.seat.getLocation()) : null, false);
        }
    }
}
