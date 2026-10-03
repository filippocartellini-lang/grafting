package dev.grafting.ability;

import dev.grafting.Fx;
import dev.grafting.GraftingPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Area damage around the caster (the caster is never part of the raw target list). */
public final class WaveAbility implements Ability {

    private final GraftingPlugin plugin;

    public WaveAbility(GraftingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "wave";
    }

    @Override
    public String displayName() {
        return "Arcane Wave";
    }

    @Override
    public String description() {
        return "Damages every creature around you.";
    }

    @Override
    public Material material() {
        return Material.ECHO_SHARD;
    }

    private double radius() {
        return plugin.getConfig().getDouble("abilities.wave.radius", 6.0);
    }

    @Override
    public List<LivingEntity> resolveTargets(Player caster) {
        double r = radius();
        List<LivingEntity> targets = new ArrayList<>();
        for (Entity entity : caster.getNearbyEntities(r, r, r)) {
            if (entity instanceof LivingEntity living
                    && !(entity instanceof ArmorStand)
                    && !entity.getUniqueId().equals(caster.getUniqueId())
                    && entity.getLocation().distanceSquared(caster.getLocation()) <= r * r) {
                targets.add(living);
            }
        }
        return targets;
    }

    @Override
    public void playCastEffect(Player caster) {
        Location ground = caster.getLocation().add(0, 0.2, 0);
        double r = radius();
        Fx.ring(ground, r, 56);
        Fx.ring(ground, r * 0.6, 36);
        Fx.sound(caster.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 0.7f);
    }

    @Override
    public void apply(Player caster, LivingEntity target) {
        Ability.hurt(caster, target, plugin.getConfig().getDouble("abilities.wave.damage", 6.0));
        Fx.burst(target.getLocation().add(0, target.getHeight() / 2.0, 0));
    }
}
