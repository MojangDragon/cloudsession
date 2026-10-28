package hu.blockeater;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A blokkevő motor. Minden pusztítás "munka" (Job): egy gömb, amit tickenként, sebességkorláttal
 * és időkerettel dolgozunk fel, így a hatalmas sugarak sem fagyasztják le a szervert.
 */
public final class EatEngine {

    /** Egy gömb alakú evési munka. */
    public final class Job {
        final UUID owner;
        final World world;
        final int cx, cy, cz, r;
        final long r2;
        final int speed;
        final Supplier<BoundingBox> keepOut;
        final boolean announce;
        // bejárás állapota
        int dx, dy, dz;
        boolean started, done;
        long eaten;
        int chunkX = Integer.MIN_VALUE, chunkZ = Integer.MIN_VALUE;
        boolean chunkOk;

        Job(UUID owner, World world, int cx, int cy, int cz, int r, int speed, Supplier<BoundingBox> keepOut, boolean announce) {
            this.owner = owner; this.world = world; this.cx = cx; this.cy = cy; this.cz = cz; this.r = r;
            this.r2 = (long) r * r; this.speed = Math.max(1, speed); this.keepOut = keepOut; this.announce = announce;
            this.dy = r; this.dx = -r; this.dz = -r;
        }

        public boolean isDone() { return done; }

        public void cancel() { done = true; }

        public long eaten() { return eaten; }

        public UUID owner() { return owner; }

        /** Előreléptet a bejárásban; false, ha vége. */
        private boolean advance() {
            dz++;
            if (dz > r) {
                dz = -r; dx++;
                if (dx > r) {
                    dx = -r; dy--;
                    if (dy < -r) return false;
                }
            }
            return true;
        }
    }

    private final BlockEaterPlugin plugin;
    private final List<Job> jobs = new ArrayList<>();
    private final Map<Material, Boolean> skipCache = new EnumMap<>(Material.class);
    private final Set<Material> containers = EnumSet.noneOf(Material.class);
    private final Set<Material> blacklist = EnumSet.noneOf(Material.class);
    private boolean protectContainers, eatLiquids, effects;
    private long budgetNanos;
    private int tickCounter;

    public EatEngine(BlockEaterPlugin plugin) {
        this.plugin = plugin;
        for (Material m : Material.values()) {
            String n = m.name();
            if (!m.isBlock() || m.isLegacy()) continue;
            if (n.endsWith("CHEST") || n.equals("BARREL") || n.endsWith("SHULKER_BOX") || n.endsWith("FURNACE")
                    || n.equals("HOPPER") || n.equals("DISPENSER") || n.equals("DROPPER") || n.equals("BREWING_STAND")
                    || n.equals("CRAFTER") || n.equals("SMOKER") || n.equals("LECTERN") || n.equals("JUKEBOX")
                    || n.equals("DECORATED_POT") || n.equals("CHISELED_BOOKSHELF") || n.equals("ENDER_CHEST")) {
                containers.add(m);
            }
        }
        reload();
    }

    public void reload() {
        var c = plugin.getConfig();
        protectContainers = c.getBoolean("protect-containers", true);
        eatLiquids = c.getBoolean("eat-liquids", true);
        effects = c.getBoolean("effects", true);
        budgetNanos = Math.max(5, c.getLong("max-ms-per-tick", 30)) * 1_000_000L;
        blacklist.clear();
        for (String s : c.getStringList("blacklist")) {
            Material m = Material.matchMaterial(s);
            if (m != null) blacklist.add(m);
        }
        skipCache.clear();
    }

    public boolean worldAllowed(World w) {
        return !plugin.getConfig().getStringList("disabled-worlds").contains(w.getName());
    }

    /** Új gömb alakú evési munka. */
    public Job sphere(UUID owner, Location center, int radius, int speed, Supplier<BoundingBox> keepOut, boolean announce) {
        Job j = new Job(owner, center.getWorld(), center.getBlockX(), center.getBlockY(), center.getBlockZ(),
                Math.max(1, radius), speed, keepOut, announce);
        jobs.add(j);
        return j;
    }

    public void cancelAll(UUID owner) {
        jobs.removeIf(j -> j.owner.equals(owner));
    }

    public boolean hasActive(UUID owner, boolean announceOnly) {
        for (Job j : jobs) if (j.owner.equals(owner) && !j.done && (!announceOnly || j.announce)) return true;
        return false;
    }

    public int activeJobs() { return jobs.size(); }

    public void clear() { jobs.clear(); }

    private boolean skippable(Material t) {
        Boolean b = skipCache.get(t);
        if (b != null) return b;
        boolean skip = t.isAir() || t.getHardness() < 0 || blacklist.contains(t)
                || (protectContainers && containers.contains(t))
                || (!eatLiquids && (t == Material.WATER || t == Material.LAVA));
        skipCache.put(t, skip);
        return skip;
    }

    /** Minden tickben egyszer. */
    public void tick() {
        tickCounter++;
        if (jobs.isEmpty()) return;
        long start = System.nanoTime();
        long deadline = start + budgetNanos;
        int n = jobs.size();
        int left = n;
        Iterator<Job> it = jobs.iterator();
        while (it.hasNext()) {
            Job j = it.next();
            if (j.done) { it.remove(); left--; continue; }
            long now = System.nanoTime();
            if (now >= deadline) break;
            long slice = (deadline - now) / Math.max(1, left);
            step(j, now + Math.max(slice, 1_000_000L));
            left--;
            if (j.done) it.remove();
        }
    }

    private void step(Job j, long sliceDeadline) {
        World w = j.world;
        if (w == null) { j.done = true; return; }
        BoundingBox keep = j.keepOut == null ? null : j.keepOut.get();
        int minY = w.getMinHeight(), maxY = w.getMaxHeight() - 1;
        int eatenNow = 0, scanned = 0, scanCap = j.speed * 8;
        Block last = null;
        BlockData lastData = null;
        long guard = 0;
        while (eatenNow < j.speed && scanned < scanCap) {
            if ((++guard & 255) == 0 && System.nanoTime() >= sliceDeadline) break;
            if (!j.started) j.started = true;
            else if (!j.advance()) { j.done = true; break; }
            scanned++;
            int dx = j.dx, dy = j.dy, dz = j.dz;
            if ((long) dx * dx + (long) dy * dy + (long) dz * dz > j.r2) continue;
            int x = j.cx + dx, y = j.cy + dy, z = j.cz + dz;
            if (y < minY || y > maxY) continue;
            int chx = x >> 4, chz = z >> 4;
            if (chx != j.chunkX || chz != j.chunkZ) {
                j.chunkX = chx; j.chunkZ = chz;
                j.chunkOk = w.isChunkLoaded(chx, chz);
            }
            if (!j.chunkOk) continue;
            Block b = w.getBlockAt(x, y, z);
            Material t = b.getType();
            if (skippable(t)) continue;
            if (keep != null && keep.contains(x + 0.5, y + 0.5, z + 0.5)) continue;
            if (effects && (eatenNow & 7) == 0) lastData = b.getBlockData();
            b.setType(Material.AIR, false);
            last = b;
            eatenNow++;
        }
        if (eatenNow > 0) {
            j.eaten += eatenNow;
            plugin.credit(j.owner, eatenNow);
            if (effects && last != null) {
                Location l = last.getLocation().add(0.5, 0.5, 0.5);
                if (lastData != null) w.spawnParticle(Particle.BLOCK, l, 8, 0.6, 0.6, 0.6, 0.05, lastData);
                if (tickCounter % 3 == 0) w.playSound(l, Sound.BLOCK_STONE_BREAK, 0.6f, 0.7f + (float) Math.random() * 0.4f);
            }
        }
        if (j.done && j.announce) plugin.eatFinished(j);
    }

    /** Védett 3x3-as padló a láb alatt, hogy ne ess bele a saját lyukadba. */
    public static BoundingBox floorBox(Location l) {
        return new BoundingBox(l.getX() - 1.6, l.getY() - 1.3, l.getZ() - 1.6, l.getX() + 1.6, l.getY() - 0.01, l.getZ() + 1.6);
    }
}
