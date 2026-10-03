package dev.grafting;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Tiny per-player, per-key cooldown tracker (wall-clock based, in memory only). */
public final class Cooldowns {

    private final Map<String, Long> expiry = new HashMap<>();

    public long remainingMillis(UUID player, String key) {
        String k = player + ":" + key;
        Long end = expiry.get(k);
        if (end == null) {
            return 0;
        }
        long left = end - System.currentTimeMillis();
        if (left <= 0) {
            expiry.remove(k);
            return 0;
        }
        return left;
    }

    public void start(UUID player, String key, long millis) {
        expiry.put(player + ":" + key, System.currentTimeMillis() + millis);
    }

    /** Returns true (and tells the player) if the key is still on cooldown. */
    public boolean blocked(Player player, String key) {
        long left = remainingMillis(player.getUniqueId(), key);
        if (left > 0) {
            Msg.bar(player, "On cooldown: " + Msg.seconds(left) + "s", NamedTextColor.RED);
            return true;
        }
        return false;
    }
}
