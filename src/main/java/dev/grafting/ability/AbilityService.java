package dev.grafting.ability;

import dev.grafting.GraftingPlugin;
import dev.grafting.Msg;
import dev.grafting.mode.AbilityGraftMode;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public final class AbilityService {

    private final GraftingPlugin plugin;
    private final AbilityGraftMode graft;
    private final Map<String, Ability> abilities = new LinkedHashMap<>();

    public AbilityService(GraftingPlugin plugin, AbilityGraftMode graft) {
        this.plugin = plugin;
        this.graft = graft;
        register(new StrikeAbility(plugin));
        register(new BlessingAbility(plugin));
        register(new WaveAbility(plugin));
    }

    private void register(Ability ability) {
        abilities.put(ability.id(), ability);
    }

    public Collection<Ability> all() {
        return abilities.values();
    }

    public void cast(Player caster, String id) {
        Ability ability = abilities.get(id);
        if (ability == null) {
            return;
        }
        String cooldownKey = "ability:" + id;
        if (plugin.cooldowns().blocked(caster, cooldownKey)) {
            return;
        }

        List<LivingEntity> raw = ability.resolveTargets(caster);
        if (raw.isEmpty()) {
            Msg.bar(caster, "No valid target.", NamedTextColor.RED);
            return;
        }

        List<LivingEntity> finalTargets = graft.redirect(caster, raw);

        long cooldown = plugin.getConfig().getLong("abilities.cooldown-seconds", 3) * 1000L;
        plugin.cooldowns().start(caster.getUniqueId(), cooldownKey, cooldown);

        ability.playCastEffect(caster);
        for (LivingEntity target : finalTargets) {
            ability.apply(caster, target);
        }
    }
}
