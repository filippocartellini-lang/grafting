package dev.grafting;

import org.bukkit.entity.Player;

/**
 * One way of "grafting" two things together. The wand cycles through the registered modes.
 * A mode that also needs Bukkit events simply implements {@link org.bukkit.event.Listener} as well.
 */
public interface GraftMode {

    /** Stable id, also used as the cooldown key. */
    String id();

    String displayName();

    String description();

    /** Called when the player right-clicks with the wand while this mode is selected. */
    void use(Player player);

    /** Called every server tick. */
    default void tick() {
    }

    /** Called when the plugin is disabled: undo anything still active. */
    default void shutdown() {
    }
}
