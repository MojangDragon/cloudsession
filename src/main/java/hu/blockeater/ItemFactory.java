package hu.blockeater;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** A tárgyak ItemStack-jei (egyedi item_model-lel, ami a resource packből jön). */
public final class ItemFactory {
    public static final String NS = "blockeater";
    private final NamespacedKey keyItem;
    private final NamespacedKey keyCrystal;

    public ItemFactory(BlockEaterPlugin plugin) {
        keyItem = new NamespacedKey(plugin, "item");
        keyCrystal = new NamespacedKey(plugin, "crystal");
    }

    public static NamespacedKey model(String name) {
        return NamespacedKey.fromString(NS + ":" + name);
    }

    public ItemStack create(ItemDef d) {
        ItemStack is = new ItemStack(Material.PAPER);
        ItemMeta m = is.getItemMeta();
        m.setItemModel(model(d.model()));
        m.setItemName(color(d) + "§l" + d.name());
        m.setMaxStackSize(1);
        m.getPersistentDataContainer().set(keyItem, PersistentDataType.INTEGER, d.number());
        List<String> lore = new ArrayList<>();
        lore.add("§r§8" + d.type().label() + " §7• #" + d.number());
        lore.add("§r§7" + d.desc());
        lore.add("§r ");
        lore.add("§r§7Sugár: §f" + d.radius() + " blokk");
        lore.add("§r§7Evési sebesség: §f" + Fmt.num(d.speed()) + " blokk/tick");
        if (d.type().isVehicle()) lore.add("§r§7Haladás: §f" + String.format("%.2f", d.move()) + " blokk/tick");
        m.setLore(lore);
        is.setItemMeta(m);
        return is;
    }

    public ItemStack crystal() {
        ItemStack is = new ItemStack(Material.PAPER);
        ItemMeta m = is.getItemMeta();
        m.setItemModel(model("portal_crystal"));
        m.setItemName("§d§lPortál Kristály");
        m.setMaxStackSize(1);
        m.setLore(List.of("§r§7Jobb klikk: bolt-portált idéz elő előtted.",
                "§r§7Parancs: §f/shop"));
        m.getPersistentDataContainer().set(keyCrystal, PersistentDataType.BYTE, (byte) 1);
        is.setItemMeta(m);
        return is;
    }

    /** A világban álló portál megjelenítő tárgya. */
    public ItemStack portalStack() {
        ItemStack is = new ItemStack(Material.PAPER);
        ItemMeta m = is.getItemMeta();
        m.setItemModel(model("shop_portal"));
        is.setItemMeta(m);
        return is;
    }

    /** A tárgy száma, vagy -1. */
    public int itemNumber(ItemStack is) {
        if (is == null || !is.hasItemMeta()) return -1;
        Integer v = is.getItemMeta().getPersistentDataContainer().get(keyItem, PersistentDataType.INTEGER);
        return v == null ? -1 : v;
    }

    public boolean isCrystal(ItemStack is) {
        return is != null && is.hasItemMeta()
                && is.getItemMeta().getPersistentDataContainer().has(keyCrystal, PersistentDataType.BYTE);
    }

    public boolean hasCrystal(org.bukkit.entity.Player p) {
        for (ItemStack is : p.getInventory().getContents()) if (isCrystal(is)) return true;
        return false;
    }

    public boolean has(org.bukkit.entity.Player p, int number) {
        for (ItemStack is : p.getInventory().getContents()) if (itemNumber(is) == number) return true;
        return false;
    }

    public static String color(ItemDef d) {
        return switch (d.tier()) {
            case 0 -> "§6";
            case 1 -> "§7";
            case 2 -> "§f";
            case 3 -> "§e";
            case 4 -> "§b";
            case 5 -> "§a";
            case 6 -> "§5";
            case 7 -> "§c";
            case 8 -> "§9";
            default -> "§4";
        };
    }
}
