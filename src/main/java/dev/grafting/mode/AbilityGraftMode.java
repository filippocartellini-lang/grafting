package dev.grafting.mode;

import dev.grafting.Fx;
import dev.grafting.GraftMode;
import dev.grafting.GraftingPlugin;
import dev.grafting.Msg;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mode 1 - Ability graft.
 * X grafts Y. While the graft lasts, every ability Y casts has its targets swapped:
 * if X would be hit, Y is hit instead; if Y would receive something (heal, buff), X receives it.
 * The plugin never needs to know whether an ability is harmful or beneficial - it only swaps who is on the list.
 */
public final class AbilityGraftMode implements GraftMode {

    private static final class Graft {
        final UUID grafter;
        final UUID victim;
        int remaining;
        int age;

        Graft(UUID grafter, UUID victim, int remaining) {
            this.grafter = grafter;
            this.victim = victim;
            this.remaining = remaining;
        }
    }

    private final GraftingPlugin plugin;
    private final Map<UUID, Graft> byGrafter = new HashMap<>();
    private final Map<UUID, Graft> byVictim = new HashMap<>();

    public AbilityGraftMode(GraftingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "ability-graft";
    }

    @Override
    public String displayName() {
        return "Ability Graft";
    }

    @Override
    public String description() {
        return "Look at a player: their abilities get tangled with yours.";
    }

    @Override
    public void use(Player grafter) {
        if (plugin.cooldowns().blocked(grafter, id())) {
            return;
        }
        int range = plugin.getConfig().getInt("ability-graft.target-range", 20);
        Entity looked = grafter.getTargetEntity(range);
        if (!(looked instanceof Player victim) || victim.getUniqueId().equals(grafter.getUniqueId())) {
            Msg.bar(grafter, "Look at another player to graft them.", NamedTextColor.RED);
            return;
        }
        if (byGrafter.containsKey(grafter.getUniqueId())) {
            Msg.bar(grafter, "You already have an active graft.", NamedTextColor.RED);
            return;
        }
        if (byVictim.containsKey(victim.getUniqueId())) {
            Msg.bar(grafter, "That player is already grafted.", NamedTextColor.RED);
            return;
        }

        int seconds = plugin.getConfig().getInt("ability-graft.duration-seconds", 20);
        Graft graft = new Graft(grafter.getUniqueId(), victim.getUniqueId(), seconds * 20);
        byGrafter.put(grafter.getUniqueId(), graft);
        byVictim.put(victim.getUniqueId(), graft);

        long cooldown = plugin.getConfig().getLong("ability-graft.cooldown-seconds", 120) * 1000L;
        plugin.cooldowns().start(grafter.getUniqueId(), id(), cooldown);

        // Concealment: the victim gets no message - only the purple haze around them gives it away.
        Fx.burst(victim.getLocation().add(0, 1, 0));
        Fx.sound(grafter.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f);
        Msg.bar(grafter, "Grafted " + victim.getName() + " for " + seconds + "s.", NamedTextColor.LIGHT_PURPLE);
    }

    /**
     * Called by the ability service between "resolve targets" and "apply".
     * Swaps the grafter and the victim wherever they appear in the list (as a set, so nobody is hit twice).
     * If both are already on the list the set is unchanged: the two are already "the same place".
     */
    public List<LivingEntity> redirect(Player caster, List<LivingEntity> raw) {
        Graft graft = byVictim.get(caster.getUniqueId());
        if (graft == null) {
            return raw;
        }
        Player grafter = Bukkit.getPlayer(graft.grafter);
        if (grafter == null) {
            return raw;
        }

        // The victim tried to use an ability while grafted: audible cue, whatever the outcome.
        Fx.sound(caster.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 0.5f);

        Set<UUID> before = new LinkedHashSet<>();
        Set<LivingEntity> swapped = new LinkedHashSet<>();
        for (LivingEntity target : raw) {
            before.add(target.getUniqueId());
            if (target.getUniqueId().equals(grafter.getUniqueId())) {
                swapped.add(caster);
            } else if (target.getUniqueId().equals(caster.getUniqueId())) {
                swapped.add(grafter);
            } else {
                swapped.add(target);
            }
        }

        Set<UUID> after = new LinkedHashSet<>();
        for (LivingEntity target : swapped) {
            after.add(target.getUniqueId());
        }
        if (!before.equals(after)) {
            Fx.burst(caster.getLocation().add(0, 1, 0));
            Msg.bar(grafter, caster.getName() + "'s ability was redirected.", NamedTextColor.LIGHT_PURPLE);
        }
        return new ArrayList<>(swapped);
    }

    @Override
    public void tick() {
        double maxDistance = plugin.getConfig().getDouble("ability-graft.max-distance", 64.0);
        Iterator<Graft> iterator = byGrafter.values().iterator();
        while (iterator.hasNext()) {
            Graft graft = iterator.next();
            Player grafter = Bukkit.getPlayer(graft.grafter);
            Player victim = Bukkit.getPlayer(graft.victim);

            boolean broken = --graft.remaining <= 0
                    || grafter == null || victim == null
                    || grafter.isDead() || victim.isDead()
                    || !grafter.getWorld().equals(victim.getWorld())
                    || grafter.getLocation().distanceSquared(victim.getLocation()) > maxDistance * maxDistance;
            if (broken) {
                iterator.remove();
                byVictim.remove(graft.victim);
                if (grafter != null) {
                    Msg.bar(grafter, "The graft has unravelled.", NamedTextColor.GRAY);
                }
                continue;
            }

            if (++graft.age % 4 == 0) {
                Fx.aura(victim);
            }
        }
    }

    @Override
    public void shutdown() {
        byGrafter.clear();
        byVictim.clear();
    }
}
