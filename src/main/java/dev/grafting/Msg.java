package dev.grafting;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public final class Msg {

    private Msg() {
    }

    public static void bar(Player player, String text, NamedTextColor color) {
        player.sendActionBar(Component.text(text, color));
    }

    public static String seconds(long millis) {
        return String.valueOf((millis + 999) / 1000);
    }
}
