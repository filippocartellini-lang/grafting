package dev.grafting;

import dev.grafting.ability.Ability;
import dev.grafting.ability.AbilityService;
import dev.grafting.mode.AbilityGraftMode;
import dev.grafting.mode.HeightGraftMode;
import dev.grafting.mode.TimeGraftMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Grafting: "Reassembly" from the Fool pathway, boiled down to three small modes
 * that all follow one idea - join two things that were never meant to be joined.
 */
public final class GraftingPlugin extends JavaPlugin {

    private final Cooldowns cooldowns = new Cooldowns();
    private final List<GraftMode> modes = new ArrayList<>();
    private final Map<UUID, Integer> selectedMode = new HashMap<>();

    private Items items;
    private AbilityService abilityService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        items = new Items(this);

        AbilityGraftMode abilityMode = new AbilityGraftMode(this);
        abilityService = new AbilityService(this, abilityMode);

        modes.add(abilityMode);
        modes.add(new TimeGraftMode(this));
        modes.add(new HeightGraftMode(this));

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new ItemListener(this), this);
        for (GraftMode mode : modes) {
            if (mode instanceof Listener listener) {
                pluginManager.registerEvents(listener, this);
            }
        }

        getServer().getScheduler().runTaskTimer(this, () -> modes.forEach(GraftMode::tick), 1L, 1L);
    }

    @Override
    public void onDisable() {
        modes.forEach(GraftMode::shutdown);
    }

    public Cooldowns cooldowns() {
        return cooldowns;
    }

    public Items items() {
        return items;
    }

    public AbilityService abilityService() {
        return abilityService;
    }

    public GraftMode currentMode(Player player) {
        return modes.get(selectedMode.getOrDefault(player.getUniqueId(), 0));
    }

    public void cycleMode(Player player) {
        int next = (selectedMode.getOrDefault(player.getUniqueId(), 0) + 1) % modes.size();
        selectedMode.put(player.getUniqueId(), next);
        GraftMode mode = modes.get(next);
        player.sendActionBar(Component.text("Mode: ", NamedTextColor.GRAY)
                .append(Component.text(mode.displayName(), NamedTextColor.LIGHT_PURPLE))
                .append(Component.text("  " + mode.description(), NamedTextColor.DARK_GRAY)));
        Fx.sound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.4f);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("give")) {
            Player target = null;
            if (args.length >= 2) {
                target = Bukkit.getPlayerExact(args[1]);
            } else if (sender instanceof Player self) {
                target = self;
            }
            if (target == null) {
                sender.sendMessage(Component.text("Specify an online player: /grafting give <player>", NamedTextColor.RED));
                return true;
            }
            target.getInventory().addItem(items.wand());
            for (Ability ability : abilityService.all()) {
                target.getInventory().addItem(items.abilityItem(ability));
            }
            sender.sendMessage(Component.text("Gave the Grafting items to " + target.getName() + ".", NamedTextColor.LIGHT_PURPLE));
            return true;
        }
        return false;
    }
}
