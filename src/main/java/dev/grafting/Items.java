package dev.grafting;

import dev.grafting.ability.Ability;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/** Creates and recognises the plugin's items (tagged through the persistent data container). */
public final class Items {

    private final NamespacedKey wandKey;
    private final NamespacedKey abilityKey;

    public Items(Plugin plugin) {
        this.wandKey = new NamespacedKey(plugin, "wand");
        this.abilityKey = new NamespacedKey(plugin, "ability");
    }

    public ItemStack wand() {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plain("Wand of Grafting", NamedTextColor.LIGHT_PURPLE));
        meta.lore(List.of(
                plain("Left click: change mode", NamedTextColor.GRAY),
                plain("Right click: use the current mode", NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack abilityItem(Ability ability) {
        ItemStack item = new ItemStack(ability.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plain(ability.displayName(), NamedTextColor.AQUA));
        meta.lore(List.of(
                plain(ability.description(), NamedTextColor.GRAY),
                plain("Right click to cast", NamedTextColor.DARK_GRAY)));
        meta.getPersistentDataContainer().set(abilityKey, PersistentDataType.STRING, ability.id());
        item.setItemMeta(meta);
        return item;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    /** The ability id stored on the item, or null if it is not an ability item. */
    public String abilityId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(abilityKey, PersistentDataType.STRING);
    }

    private static Component plain(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
