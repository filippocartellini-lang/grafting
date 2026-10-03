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

import java.util.List;

/** Single-target damage: hits the creature the caster is looking at. */
public final class StrikeAbility implements Ability {

    private final GraftingPlugin plugin;

    public StrikeAbility(GraftingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "strike";
    }

    @Override
    public String displayName() {
        return "Arcane Strike";
    }

    @Override
    public String description() {
        return "Damages the creature you are looking at.";
    }

    @Override
    public Material material() {
        return Material.BLAZE_ROD;
    }

    @Override
    public List<LivingEntity> resolveTargets(Player caster) {
        int range = (int) plugin.getConfig().getDouble("abilities.strike.range", 20);
        Entity looked = caster.getTargetEntity(range);
        if (looked instanceof LivingEntity living
                && !(looked instanceof ArmorStand)
                && !looked.getUniqueId().equals(caster.getUniqueId())) {
            return List.of(living);
        }
        return List.of();
    }

    @Override
    public void apply(Player caster, LivingEntity target) {
        Ability.hurt(caster, target, plugin.getConfig().getDouble("abilities.strike.damage", 8.0));
        Location center = target.getLocation().add(0, target.getHeight() / 2.0, 0);
        Fx.burst(center);
        Fx.sound(center, Sound.ENTITY_EVOKER_CAST_SPELL, 1.2f);
    }
}
