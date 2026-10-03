package dev.grafting;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Turns clicks into actions.
 * Wand: left click = next mode, right click = use. Ability items: right click = cast.
 * Clicking an entity can fire several events for one physical click (entity event + item event),
 * so every action is de-duplicated per player and per tick.
 */
public final class ItemListener implements Listener {

    private final GraftingPlugin plugin;
    private final Map<UUID, Integer> lastActionTick = new HashMap<>();

    public ItemListener(GraftingPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean firstActionThisTick(Player player) {
        int now = Bukkit.getCurrentTick();
        Integer last = lastActionTick.put(player.getUniqueId(), now);
        return last == null || last != now;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() == Action.PHYSICAL) {
            return;
        }
        ItemStack item = event.getItem();
        boolean wand = plugin.items().isWand(item);
        String abilityId = plugin.items().abilityId(item);
        if (!wand && abilityId == null) {
            return;
        }
        event.setCancelled(true); // no block breaking / chest opening with our items

        Player player = event.getPlayer();
        if (!firstActionThisTick(player)) {
            return;
        }
        boolean left = event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK;
        if (wand) {
            if (left) {
                plugin.cycleMode(player);
            } else {
                plugin.currentMode(player).use(player);
            }
        } else if (!left) {
            plugin.abilityService().cast(player, abilityId);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        boolean wand = plugin.items().isWand(item);
        String abilityId = plugin.items().abilityId(item);
        if (!wand && abilityId == null) {
            return;
        }
        event.setCancelled(true);
        if (!firstActionThisTick(player)) {
            return;
        }
        if (wand) {
            plugin.currentMode(player).use(player);
        } else {
            plugin.abilityService().cast(player, abilityId);
        }
    }

    /** Hitting something with the wand only changes mode; it never deals damage. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWandHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!plugin.items().isWand(player.getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);
        if (firstActionThisTick(player)) {
            plugin.cycleMode(player);
        }
    }
}
