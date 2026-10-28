package hu.blockeater;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

public final class Msg {
    public static final String PREFIX = "§6[BlockEater] §r";

    private Msg() {}

    public static void bar(Player p, String text) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(text));
    }

    public static void chat(Player p, String text) {
        p.sendMessage(PREFIX + text);
    }
}
