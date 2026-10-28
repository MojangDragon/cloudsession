package hu.blockeater;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class Commands implements CommandExecutor, TabCompleter {
    private final BlockEaterPlugin plugin;

    public Commands(BlockEaterPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (cmd.getName().equalsIgnoreCase("shop")) {
            if (!(s instanceof Player p)) { s.sendMessage("Csak játékosoknak."); return true; }
            if (!plugin.engine().worldAllowed(p.getWorld())) { Msg.bar(p, "§cEbben a világban nem működik."); return true; }
            plugin.portals().summon(p);
            return true;
        }
        String sub = a.length == 0 ? "stats" : a[0].toLowerCase();
        switch (sub) {
            case "shop" -> {
                if (s instanceof Player p) plugin.portals().summon(p);
            }
            case "stats" -> {
                Player p;
                if (a.length > 1 && s.hasPermission("blockeater.admin")) {
                    p = Bukkit.getPlayerExact(a[1]);
                    if (p == null) { s.sendMessage("§cA játékos nincs fent."); return true; }
                } else if (s instanceof Player self) {
                    p = self;
                } else { s.sendMessage("Add meg a játékos nevét: /be stats <játékos>"); return true; }
                PlayerData d = plugin.data().get(p.getUniqueId());
                long goal = plugin.catalog().goal();
                s.sendMessage(Msg.PREFIX + "§7" + p.getName() + " megevett: §e" + Fmt.num(d.eaten) + " §7/ §c" + Fmt.num(goal)
                        + " §7(" + String.format(java.util.Locale.ROOT, "%.4f", d.eaten * 100.0 / goal) + "%)");
                s.sendMessage(Msg.PREFIX + "§7Egyenleg: §a" + Fmt.num(d.balance) + " §7blokk, megvett tárgyak: §f" + d.owned.size() + "/"
                        + plugin.catalog().all().size() + " §7| segéd: " + plugin.helpers().has(p.getUniqueId())
                        + " | jármű: " + plugin.vehicles().has(p.getUniqueId()) + " | aktív munkák: " + plugin.engine().activeJobs());
            }
            case "hud" -> {
                if (!(s instanceof Player p)) return true;
                PlayerData d = plugin.data().get(p.getUniqueId());
                d.hud = !d.hud;
                d.dirty = true;
                if (d.hud) plugin.hud().show(p); else plugin.hud().hide(p);
                Msg.chat(p, "§7Jobb oldali kijelző: " + (d.hud ? "§abe" : "§ckI"));
            }
            case "pack" -> {
                if (!(s instanceof Player p)) return true;
                plugin.sendResourcePack(p);
            }
            case "give", "add", "set", "reset", "reload" -> admin(s, sub, a);
            default -> s.sendMessage("§cHasználat: /be [shop|stats|hud|pack]" + (s.hasPermission("blockeater.admin")
                    ? " | give <játékos> <szám> | add <játékos> <összeg> | set <játékos> <összeg> | reset <játékos> | reload" : ""));
        }
        return true;
    }

    private void admin(CommandSender s, String sub, String[] a) {
        if (!s.hasPermission("blockeater.admin")) { s.sendMessage("§cNincs jogod ehhez."); return; }
        if (sub.equals("reload")) {
            plugin.reloadAll();
            s.sendMessage("§aÚjratöltve.");
            return;
        }
        if (a.length < 2) { s.sendMessage("§cHiányzó játékosnév."); return; }
        Player t = Bukkit.getPlayerExact(a[1]);
        if (t == null) { s.sendMessage("§cA játékos nincs fent."); return; }
        PlayerData d = plugin.data().get(t.getUniqueId());
        try {
            switch (sub) {
                case "give" -> {
                    ItemDef def = plugin.catalog().get(Integer.parseInt(a[2]));
                    if (def == null) { s.sendMessage("§cNincs ilyen tárgy (1-" + plugin.catalog().all().size() + ")."); return; }
                    var left = t.getInventory().addItem(plugin.items().create(def));
                    for (ItemStack is : left.values()) t.getWorld().dropItem(t.getLocation(), is);
                    d.owned.add(def.number());
                    d.dirty = true;
                    s.sendMessage("§aOdaadva: " + def.name());
                }
                case "add" -> {
                    long v = Long.parseLong(a[2]);
                    plugin.credit(t.getUniqueId(), v);
                    s.sendMessage("§a+" + Fmt.num(v) + " megevett blokk jóváírva.");
                }
                case "set" -> {
                    long v = Long.parseLong(a[2]);
                    d.eaten = v;
                    d.balance = v;
                    d.dirty = true;
                    s.sendMessage("§aBeállítva: " + Fmt.num(v));
                }
                case "reset" -> {
                    d.eaten = 0; d.balance = 0; d.owned.clear(); d.dirty = true;
                    s.sendMessage("§aVisszaállítva.");
                }
                default -> { }
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException ex) {
            s.sendMessage("§cHibás szám / hiányzó paraméter.");
        }
        plugin.hud().show(t);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        if (!cmd.getName().equalsIgnoreCase("blockeater")) return out;
        if (a.length == 1) {
            List<String> subs = new ArrayList<>(List.of("shop", "stats", "hud", "pack"));
            if (s.hasPermission("blockeater.admin")) subs.addAll(List.of("give", "add", "set", "reset", "reload"));
            for (String x : subs) if (x.startsWith(a[0].toLowerCase())) out.add(x);
        } else if (a.length == 2 && s.hasPermission("blockeater.admin") && !a[0].equalsIgnoreCase("reload")) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase().startsWith(a[1].toLowerCase())) out.add(p.getName());
        }
        return out;
    }
}
