package dev.grafting.ability;

import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * A demo ability. The important design point: an ability does NOT pick its victims and hit them in one go.
 * It first RESOLVES a list of targets, and only afterwards APPLIES its effect to the final list.
 * This is to make ability grafting easier to develop, even though it might not be how abilities in the server work.
 */
public interface Ability {

    String id();

    String displayName();

    String description();

    Material material();

    /** Who this ability would affect, before any grafting. */
    List<LivingEntity> resolveTargets(Player caster);

    /** Applies the effect to one (possibly redirected) target. */
    void apply(Player caster, LivingEntity target);

    /** Optional visual/sound played once per cast. */
    default void playCastEffect(Player caster) {
    }

    /** Damage helper: never uses the caster as the damager when the caster hits himself. */
    static void hurt(Player caster, LivingEntity target, double amount) {
        if (target.getUniqueId().equals(caster.getUniqueId())) {
            target.damage(amount);
        } else {
            target.damage(amount, caster);
        }
    }
}
