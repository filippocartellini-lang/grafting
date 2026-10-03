package dev.grafting.ability;

import dev.grafting.Fx;
import dev.grafting.GraftingPlugin;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/** Self-target buff: the textbook "beneficial" ability, whose target is the caster. */
public final class BlessingAbility implements Ability {

    private final GraftingPlugin plugin;

    public BlessingAbility(GraftingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "blessing";
    }

    @Override
    public String displayName() {
        return "Blessing";
    }

    @Override
    public String description() {
        return "Grants Speed and Regeneration to yourself.";
    }

    @Override
    public Material material() {
        return Material.HEART_OF_THE_SEA;
    }

    @Override
    public List<LivingEntity> resolveTargets(Player caster) {
        return List.of(caster);
    }

    @Override
    public void apply(Player caster, LivingEntity target) {
        int ticks = plugin.getConfig().getInt("abilities.blessing.duration-seconds", 10) * 20;
        target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, 1));
        target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, ticks, 1));
        Fx.aura(target);
        Fx.sound(target.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f);
    }
}
