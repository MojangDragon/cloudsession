package hu.blockeater;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A jobb oldali, alap Minecraft-os kijelző (sidebar scoreboard):
 * megevett blokkok, egyenleg, következő tárgy és a cél.
 */
public final class Hud {
    private static final String L_EATEN = "§6§lMegevett blokk";
    private static final String L_BAL = "§a§lEgyenleg";
    private static final String L_GOAL = "§c§lHátra az 1 milliárdig";

    private static final class View {
        Scoreboard sb;
        Objective obj;
        String nextLine = "";
    }

    private final BlockEaterPlugin plugin;
    private final Map<UUID, View> views = new HashMap<>();

    public Hud(BlockEaterPlugin plugin) { this.plugin = plugin; }

    public void show(Player p) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (!d.hud) { hide(p); return; }
        View v = views.get(p.getUniqueId());
        if (v == null) {
            v = new View();
            v.sb = Bukkit.getScoreboardManager().getNewScoreboard();
            v.obj = v.sb.registerNewObjective("blockeater", "dummy", "§5§l⛏ BLOKK EVŐ");
            v.obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            views.put(p.getUniqueId(), v);
        }
        if (p.getScoreboard() != v.sb) p.setScoreboard(v.sb);
        update(p, v, d);
    }

    public void hide(Player p) {
        View v = views.remove(p.getUniqueId());
        if (v != null && p.getScoreboard() == v.sb) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private void update(Player p, View v, PlayerData d) {
        v.obj.getScore(L_EATEN).setScore(Fmt.clampInt(d.eaten));
        v.obj.getScore(L_BAL).setScore(Fmt.clampInt(d.balance));
        long goal = plugin.catalog().goal();
        v.obj.getScore(L_GOAL).setScore(Fmt.clampInt(Math.max(0, goal - d.eaten)));

        ItemDef next = plugin.catalog().nextLocked(d.eaten);
        String line;
        int val;
        if (next == null) {
            line = "§d§lMinden feloldva!";
            val = 0;
        } else {
            line = "§b➜ " + ItemFactory.color(next) + next.name();
            val = Fmt.clampInt(next.unlock() - d.eaten);
        }
        if (!line.equals(v.nextLine)) {
            if (!v.nextLine.isEmpty()) v.sb.resetScores(v.nextLine);
            v.nextLine = line;
        }
        v.obj.getScore(line).setScore(val);
    }

    public void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = plugin.data().get(p.getUniqueId());
            if (d.hud) show(p);
        }
    }

    public void forget(UUID id) { views.remove(id); }
}
