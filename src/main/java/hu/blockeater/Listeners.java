package hu.blockeater;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Listeners implements Listener {
    private final BlockEaterPlugin plugin;
    private final Map<UUID, Long> crystalCooldown = new HashMap<>();

    public Listeners(BlockEaterPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (plugin.getConfig().getBoolean("give-crystal-on-join", true) && !plugin.items().hasCrystal(p)) {
            p.getInventory().addItem(plugin.items().crystal());
            Msg.chat(p, "§7Kaptál egy §dPortál Kristályt§7. Jobb klikk vele (vagy §f/shop§7) = bolt-portál, bárhol.");
        }
        // az első tárgy ingyen jár
        ItemDef first = plugin.catalog().get(1);
        if (first != null && !d.owned.contains(1) && first.cost() == 0) {
            d.owned.add(1);
            d.dirty = true;
            p.getInventory().addItem(plugin.items().create(first));
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            plugin.sendResourcePack(p);
            plugin.hud().show(p);
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        plugin.helpers().remove(id);
        plugin.vehicles().remove(id);
        plugin.portals().remove(id, false);
        plugin.engine().cancelAll(id);
        plugin.hud().forget(id);
        crystalCooldown.remove(id);
        plugin.data().unload(id);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        plugin.vehicles().remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        UUID id = e.getEntity().getUniqueId();
        plugin.vehicles().remove(id);
        plugin.helpers().remove(id);
        plugin.engine().cancelAll(id);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack hand = e.getItem();
        if (hand == null) return;

        if (plugin.items().isCrystal(hand)) {
            e.setCancelled(true);
            long now = System.currentTimeMillis();
            Long last = crystalCooldown.get(p.getUniqueId());
            if (last != null && now - last < 1500) return;
            crystalCooldown.put(p.getUniqueId(), now);
            plugin.portals().summon(p);
            return;
        }

        int n = plugin.items().itemNumber(hand);
        if (n < 0) return;
        ItemDef def = plugin.catalog().get(n);
        if (def == null) return;
        e.setCancelled(true);
        if (!p.hasPermission("blockeater.use")) {
            Msg.bar(p, "§cNincs jogod használni.");
            return;
        }
        if (!plugin.engine().worldAllowed(p.getWorld())) {
            Msg.bar(p, "§cEbben a világban nem működik.");
            return;
        }
        switch (def.type()) {
            case TOOL, FINAL -> useTool(p, def);
            case HELPER -> plugin.helpers().toggle(p, def);
            case CAR, DOZER -> {
                if (plugin.vehicles().has(p.getUniqueId())) {
                    Msg.bar(p, "§7Már vezetsz egy járművet. Guggolj a leszálláshoz.");
                    return;
                }
                Location at;
                Block clicked = e.getClickedBlock();
                if (clicked != null) {
                    BlockFace f = e.getBlockFace();
                    at = clicked.getRelative(f).getLocation().add(0.5, 0, 0.5);
                } else {
                    at = p.getLocation();
                }
                plugin.vehicles().spawn(p, def, at);
            }
        }
    }

    private void useTool(Player p, ItemDef def) {
        UUID id = p.getUniqueId();
        if (plugin.engine().hasActive(id, true)) {
            Msg.bar(p, "§eMég eszi az előző falatot...");
            return;
        }
        Block target = p.getTargetBlockExact(Math.min(64, 8 + def.radius()));
        Location c;
        if (target != null) {
            c = target.getLocation().add(0.5, 0.5, 0.5);
        } else {
            Vector dir = p.getEyeLocation().getDirection();
            c = p.getEyeLocation().add(dir.multiply(def.radius() + 2));
        }
        plugin.engine().sphere(id, c, def.radius(), def.speed(),
                plugin.protectFloor() ? () -> p.isOnline() ? EatEngine.floorBox(p.getLocation()) : null : null, true);
        if (def.type() == ItemDef.Type.FINAL) {
            p.getWorld().playSound(c, org.bukkit.Sound.ENTITY_WITHER_SPAWN, 1.5f, 0.5f);
            Msg.bar(p, "§4§lA VILÁGFALÓ ÉBRED...");
        } else {
            p.getWorld().playSound(c, org.bukkit.Sound.ENTITY_GENERIC_EAT, 1f, 0.6f);
        }
    }

    // ---- bolt-portál ----
    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Entity en = e.getRightClicked();
        if (en instanceof Interaction && plugin.portals().isPortalEntity(en)) {
            e.setCancelled(true);
            if (e.getHand() == EquipmentSlot.HAND) plugin.shop().open(e.getPlayer(), 0);
        } else if (isOurs(en)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onHitEntity(EntityDamageByEntityEvent e) {
        Entity en = e.getEntity();
        if (en instanceof Interaction && plugin.portals().isPortalEntity(en)) {
            e.setCancelled(true);
            if (e.getDamager() instanceof Player p) plugin.shop().open(p, 0);
        } else if (isOurs(en)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (isOurs(e.getEntity())) e.setCancelled(true);
    }

    private boolean isOurs(Entity e) {
        return e.getPersistentDataContainer().has(plugin.tagKey(), PersistentDataType.STRING);
    }

    // ---- bolt GUI ----
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof ShopGui.Holder h) {
            plugin.shop().click(e, h);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof ShopGui.Holder) e.setCancelled(true);
    }

    // ---- járművek ----
    @EventHandler
    public void onDismount(EntityDismountEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (plugin.vehicles().isSeat(e.getDismounted())) {
            UUID id = p.getUniqueId();
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.vehicles().remove(id));
        }
    }

    // ---- maradék entitások takarítása (pl. összeomlás után) ----
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) {
            if (isOurs(en)) en.remove();
        }
    }
}
