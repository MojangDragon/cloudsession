package hu.blockeater;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** A bolt: itt veheted meg a tárgyakat a megevett blokkokért. */
public final class ShopGui {
    private static final int PAGE = 45;

    public static final class Holder implements InventoryHolder {
        final int page;
        Inventory inv;
        Holder(int page) { this.page = page; }
        @Override public Inventory getInventory() { return inv; }
    }

    private final BlockEaterPlugin plugin;

    public ShopGui(BlockEaterPlugin plugin) { this.plugin = plugin; }

    public void open(Player p, int page) {
        List<ItemDef> all = plugin.catalog().all();
        int pages = (all.size() + PAGE - 1) / PAGE;
        page = Math.max(0, Math.min(pages - 1, page));
        Holder h = new Holder(page);
        Inventory inv = Bukkit.createInventory(h, 54, "§5§lBlokk-Bolt §8(" + (page + 1) + "/" + pages + ")");
        h.inv = inv;
        PlayerData d = plugin.data().get(p.getUniqueId());

        for (int i = 0; i < PAGE; i++) {
            int idx = page * PAGE + i;
            if (idx >= all.size()) break;
            inv.setItem(i, icon(all.get(idx), d));
        }
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);
        if (page > 0) inv.setItem(45, named(Material.ARROW, "§e« Előző oldal", null));
        if (page < pages - 1) inv.setItem(53, named(Material.ARROW, "§eKövetkező oldal »", null));
        inv.setItem(49, named(Material.GOLD_INGOT, "§6§lÁllásod", List.of(
                "§r§7Megevett blokk: §e" + Fmt.num(d.eaten),
                "§r§7Egyenleg: §a" + Fmt.num(d.balance) + " blokk",
                "§r§7Cél: §c" + Fmt.num(plugin.catalog().goal()) + " blokk")));
        p.openInventory(inv);
    }

    private ItemStack icon(ItemDef def, PlayerData d) {
        ItemStack is = plugin.items().create(def);
        ItemMeta m = is.getItemMeta();
        List<String> lore = new ArrayList<>(m.getLore());
        lore.add("§r ");
        boolean owned = d.owned.contains(def.number());
        boolean unlocked = d.eaten >= def.unlock();
        if (owned) {
            lore.add("§r§a✔ Megvetted §7- kattints az újbóli átvételhez");
        } else if (!unlocked) {
            lore.add("§r§c✖ Zárolva §7- még §c" + Fmt.num(def.unlock() - d.eaten) + " §7megevett blokk kell");
            lore.add("§r§7Feloldás: §f" + Fmt.num(def.unlock()) + " megevett blokk");
            lore.add("§r§7Ár: §f" + Fmt.num(def.cost()) + " blokk");
        } else if (d.balance < def.cost()) {
            lore.add("§r§e⚠ Feloldva, de kevés a blokk");
            lore.add("§r§7Ár: §f" + Fmt.num(def.cost()) + " blokk §7(hiányzik: §c" + Fmt.num(def.cost() - d.balance) + "§7)");
        } else {
            lore.add("§r§a➤ Kattints a vásárláshoz!");
            lore.add("§r§7Ár: §f" + Fmt.num(def.cost()) + " blokk");
        }
        m.setLore(lore);
        is.setItemMeta(m);
        return is;
    }

    private static ItemStack named(Material mat, String name, List<String> lore) {
        ItemStack is = new ItemStack(mat);
        ItemMeta m = is.getItemMeta();
        m.setItemName(name);
        if (lore != null) m.setLore(lore);
        is.setItemMeta(m);
        return is;
    }

    public void click(InventoryClickEvent e, Holder h) {
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != h.inv) return;
        int slot = e.getSlot();
        if (slot == 45 && h.page > 0) { open(p, h.page - 1); return; }
        if (slot == 53) { open(p, h.page + 1); return; }
        if (slot < 0 || slot >= PAGE) return;
        int idx = h.page * PAGE + slot;
        List<ItemDef> all = plugin.catalog().all();
        if (idx >= all.size()) return;
        buy(p, all.get(idx), h.page);
    }

    private void buy(Player p, ItemDef def, int page) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d.owned.contains(def.number())) {
            if (plugin.items().has(p, def.number())) {
                Msg.bar(p, "§eMár nálad van ez a tárgy.");
                return;
            }
            give(p, def);
            Msg.bar(p, "§aÚjra átvetted: " + ItemFactory.color(def) + def.name());
            return;
        }
        if (d.eaten < def.unlock()) {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            Msg.bar(p, "§cMég nincs feloldva! Még " + Fmt.num(def.unlock() - d.eaten) + " megevett blokk kell.");
            return;
        }
        if (d.balance < def.cost()) {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            Msg.bar(p, "§cKevés a blokk! Hiányzik: " + Fmt.num(def.cost() - d.balance));
            return;
        }
        d.balance -= def.cost();
        d.owned.add(def.number());
        d.dirty = true;
        give(p, def);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        Msg.chat(p, "§aMegvetted: " + ItemFactory.color(def) + def.name() + " §7(-" + Fmt.num(def.cost()) + " blokk)");
        if (def.number() == plugin.catalog().last().number()) {
            Bukkit.broadcastMessage(Msg.PREFIX + "§c§l" + p.getName() + " §emegvette a legfőbb fegyvert: §4§lA Világfaló§e!");
        }
        open(p, page);
    }

    private void give(Player p, ItemDef def) {
        var left = p.getInventory().addItem(plugin.items().create(def));
        for (ItemStack is : left.values()) p.getWorld().dropItem(p.getLocation(), is);
    }
}
