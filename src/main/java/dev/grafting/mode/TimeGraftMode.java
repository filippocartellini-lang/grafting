package dev.grafting.mode;

import dev.grafting.Fx;
import dev.grafting.GraftMode;
import dev.grafting.GraftingPlugin;
import dev.grafting.Msg;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Mode 2 - Time graft (self only).
 * Using it saves a checkpoint of your body. After a few seconds you are pulled back to it, as if
 * the moment of the cast and the moment of the return had been joined and everything in between skipped.
 * If you would die inside that window, the graft triggers early and saves you.
 * Only the "physical" state is saved (position, health, hunger, air, fire, potion effects).
 * Inventory and blocks are deliberately not touched - see the README.
 */
public final class TimeGraftMode implements GraftMode, Listener {

    private static final class Checkpoint {
        final Location location;
        final double health;
        final int food;
        final float saturation;
        final int air;
        final int fire;
        final List<PotionEffect> effects;
        int remaining;

        Checkpoint(Player player, int remaining) {
            this.location = player.getLocation();
            this.health = player.getHealth();
            this.food = player.getFoodLevel();
            this.saturation = player.getSaturation();
            this.air = player.getRemainingAir();
            this.fire = player.getFireTicks();
            this.effects = new ArrayList<>(player.getActivePotionEffects());
            this.remaining = remaining;
        }
    }

    private final GraftingPlugin plugin;
    private final Map<UUID, Checkpoint> checkpoints = new HashMap<>();

    public TimeGraftMode(GraftingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "time-graft";
    }

    @Override
    public String displayName() {
        return "Time Graft";
    }

    @Override
    public String description() {
        return "Save a checkpoint now; return to it in a few seconds.";
    }

    @Override
    public void use(Player player) {
        if (checkpoints.containsKey(player.getUniqueId())) {
            Msg.bar(player, "Your checkpoint is already set.", NamedTextColor.RED);
            return;
        }
        if (plugin.cooldowns().blocked(player, id())) {
            return;
        }
        int seconds = plugin.getConfig().getInt("time-graft.duration-seconds", 10);
        checkpoints.put(player.getUniqueId(), new Checkpoint(player, seconds * 20));

        long cooldown = plugin.getConfig().getLong("time-graft.cooldown-seconds", 90) * 1000L;
        plugin.cooldowns().start(player.getUniqueId(), id(), cooldown);

        Fx.ring(player.getLocation().add(0, 0.1, 0), 0.8, 20);
        Fx.sound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f);
        Msg.bar(player, "Checkpoint saved. Returning in " + seconds + "s.", NamedTextColor.LIGHT_PURPLE);
    }

    /** If a blow would kill a player with an active checkpoint, the graft fires early and saves them. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Checkpoint checkpoint = checkpoints.get(player.getUniqueId());
        if (checkpoint == null || player.getHealth() - event.getFinalDamage() > 0) {
            return;
        }
        event.setCancelled(true);
        checkpoints.remove(player.getUniqueId());
        // Brief immunity so nothing else can kill the player before the restore runs next tick.
        player.setNoDamageTicks(20);
        // Do NOT change health or teleport from inside the damage event: do it on the next tick.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && !player.isDead()) {
                restore(player, checkpoint, true);
            }
        });
    }

    @Override
    public void tick() {
        Iterator<Map.Entry<UUID, Checkpoint>> iterator = checkpoints.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Checkpoint> entry = iterator.next();
            Checkpoint checkpoint = entry.getValue();
            Player player = Bukkit.getPlayer(entry.getKey());

            // Offline, dead, or moved to another world: the two moments can no longer be joined.
            if (player == null || player.isDead() || !player.getWorld().equals(checkpoint.location.getWorld())) {
                iterator.remove();
                continue;
            }

            if (checkpoint.remaining % 5 == 0) {
                Fx.ring(checkpoint.location.clone().add(0, 0.1, 0), 0.8, 20);
                // Same action bar slot as the mode name: show how long until time folds back.
                Msg.bar(player,
                        String.format(Locale.ROOT, "Time Graft - returning in %.1fs", checkpoint.remaining / 20.0),
                        NamedTextColor.LIGHT_PURPLE);
            }
            if (--checkpoint.remaining <= 0) {
                iterator.remove();
                restore(player, checkpoint, false);
            }
        }
    }

    private void restore(Player player, Checkpoint checkpoint, boolean savedFromDeath) {
        try {
            doRestore(player, checkpoint, savedFromDeath);
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Time graft restore failed", e);
        }
    }

    private void doRestore(Player player, Checkpoint checkpoint, boolean savedFromDeath) {
        // Health first: it is the most important part and must not depend on anything below it.
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth != null ? maxHealth.getValue() : 20.0;
        player.setHealth(Math.max(1.0, Math.min(checkpoint.health, max)));

        Fx.burst(player.getLocation().add(0, 1, 0));

        player.leaveVehicle();
        player.teleport(checkpoint.location);
        player.setFallDistance(0f);
        player.setVelocity(new Vector(0, 0, 0));

        player.setFoodLevel(checkpoint.food);
        player.setSaturation(checkpoint.saturation);
        player.setRemainingAir(checkpoint.air);
        player.setFireTicks(checkpoint.fire);

        for (PotionEffect active : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(active.getType());
        }
        for (PotionEffect effect : checkpoint.effects) {
            player.addPotionEffect(effect);
        }
        if (savedFromDeath) {
            player.setNoDamageTicks(20); // one second of breathing room after the rescue
        }

        Fx.burst(player.getLocation().add(0, 1, 0));
        Fx.sound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f);
        Msg.bar(player,
                savedFromDeath ? "The graft pulled you back from death." : "Time folded back to your checkpoint.",
                NamedTextColor.LIGHT_PURPLE);
    }

    @Override
    public void shutdown() {
        checkpoints.clear();
    }
}
