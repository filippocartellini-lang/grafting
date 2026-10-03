package dev.grafting.mode;

import dev.grafting.Fx;
import dev.grafting.GraftMode;
import dev.grafting.GraftingPlugin;
import dev.grafting.Msg;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mode 3 - Height Graft.
 * The "bottom" of the target's fall is grafted onto the "top": whenever the target would land, or has fallen
 * {@code loop-height} blocks, it is sent back up the same vertical line. It cannot move sideways, and it cannot
 * stay in the air: flight is switched off, a minimum downward speed is enforced, and every in-air wrap loses a few
 * blocks of altitude so that even a flying target is dragged to the ground. Each ground impact costs a percentage
 * of the target's maximum health.
 */
public final class HeightGraftMode implements GraftMode, Listener {

    private static final List<PotionEffectType> SUPPRESSED =
            List.of(PotionEffectType.LEVITATION, PotionEffectType.SLOW_FALLING);
    private static final double HORIZONTAL_TOLERANCE_SQ = 0.02 * 0.02;
    private static final double FORCE_DOWN_STEP = 1.5;

    private static final class Lock {
        final UUID target;
        final UUID caster;
        final double anchorX;
        final double anchorZ;
        int remaining;
        int age;
        double lastWrapY;
        double lastY;
        boolean allowFlight;
        boolean flying;
        final List<PotionEffect> suppressed = new ArrayList<>();

        Lock(UUID target, UUID caster, Location at, int remaining) {
            this.target = target;
            this.caster = caster;
            this.anchorX = at.getX();
            this.anchorZ = at.getZ();
            this.remaining = remaining;
            this.lastWrapY = at.getY();
            this.lastY = at.getY();
        }
    }

    private final GraftingPlugin plugin;
    private final Map<UUID, Lock> locks = new HashMap<>();

    /** True while the plugin itself teleports or damages, so our own listeners ignore those events. */
    private boolean internal;

    private final int durationTicks;
    private final int targetRange;
    private final double maxCasterDistanceSq;
    private final double loopHeight;
    private final double airPullBack;
    private final double minClearance;
    private final double minFallSpeed;
    private final double impactPercent;

    public HeightGraftMode(GraftingPlugin plugin) {
        this.plugin = plugin;
        var config = plugin.getConfig();
        this.durationTicks = config.getInt("height-graft.duration-seconds", 8) * 20;
        this.targetRange = config.getInt("height-graft.target-range", 25);
        double maxDistance = config.getDouble("height-graft.max-distance", 64.0);
        this.maxCasterDistanceSq = maxDistance * maxDistance;
        this.loopHeight = config.getDouble("height-graft.loop-height", 12.0);
        this.airPullBack = config.getDouble("height-graft.air-pull-back", 3.0);
        this.minClearance = config.getDouble("height-graft.min-clearance", 6.0);
        this.minFallSpeed = config.getDouble("height-graft.min-fall-speed", 1.0);
        this.impactPercent = config.getDouble("height-graft.impact-damage-percent", 1.5);
    }

    @Override
    public String id() {
        return "height-graft";
    }

    @Override
    public String displayName() {
        return "Height Graft";
    }

    @Override
    public String description() {
        return "Look at a creature: its ground and its sky become the same place.";
    }

    // ------------------------------------------------------------------ casting

    @Override
    public void use(Player caster) {
        if (plugin.cooldowns().blocked(caster, id())) {
            return;
        }
        Entity looked = caster.getTargetEntity(targetRange);
        if (!(looked instanceof LivingEntity target)
                || looked instanceof ArmorStand
                || looked.getUniqueId().equals(caster.getUniqueId())) {
            Msg.bar(caster, "Look at a creature to graft its fall.", NamedTextColor.RED);
            return;
        }
        if (locks.containsKey(target.getUniqueId())) {
            Msg.bar(caster, "That target is already grafted.", NamedTextColor.RED);
            return;
        }
        if (clearanceAbove(target, loopHeight) < minClearance) {
            Msg.bar(caster, "Not enough open space above the target.", NamedTextColor.RED);
            return;
        }

        long cooldown = plugin.getConfig().getLong("height-graft.cooldown-seconds", 90) * 1000L;
        plugin.cooldowns().start(caster.getUniqueId(), id(), cooldown);

        Lock lock = new Lock(target.getUniqueId(), caster.getUniqueId(), target.getLocation(), durationTicks);
        locks.put(target.getUniqueId(), lock);

        if (target instanceof Player player) {
            lock.allowFlight = player.getAllowFlight();
            lock.flying = player.isFlying();
        }
        // Remember the effects we are about to suppress BEFORE removing them, so they can be given back.
        for (PotionEffectType type : SUPPRESSED) {
            PotionEffect effect = target.getPotionEffect(type);
            if (effect != null) {
                lock.suppressed.add(effect);
            }
        }
        suppressFlight(lock, target);

        Fx.sound(target.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f);
        Msg.bar(caster, "Grafted the fall of " + target.getName() + ".", NamedTextColor.LIGHT_PURPLE);

        // A target standing on the ground is lifted to the top of the loop to start falling.
        if (groundBelow(target.getLocation())) {
            wrapUp(lock, target, false);
        }
    }

    // ------------------------------------------------------------------ per-tick logic

    @Override
    public void tick() {
        Iterator<Lock> iterator = locks.values().iterator();
        while (iterator.hasNext()) {
            Lock lock = iterator.next();
            Entity entity = Bukkit.getEntity(lock.target);
            Player caster = Bukkit.getPlayer(lock.caster);

            boolean broken = !(entity instanceof LivingEntity)
                    || !entity.isValid() || entity.isDead()
                    || caster == null || caster.isDead()
                    || !caster.getWorld().equals(entity.getWorld())
                    || caster.getLocation().distanceSquared(entity.getLocation()) > maxCasterDistanceSq
                    || --lock.remaining <= 0;
            if (broken) {
                iterator.remove();
                release(lock, entity);
                continue;
            }
            step(lock, (LivingEntity) entity);
        }
    }

    private void step(Lock lock, LivingEntity target) {
        lock.age++;
        Location loc = target.getLocation();

        // 1) No horizontal movement: pin the target to its vertical line.
        double dx = loc.getX() - lock.anchorX;
        double dz = loc.getZ() - lock.anchorZ;
        if (dx * dx + dz * dz > HORIZONTAL_TOLERANCE_SQ) {
            loc.setX(lock.anchorX);
            loc.setZ(lock.anchorZ);
            teleport(target, loc);
        }

        // 2) No flight of any kind (vanilla flight, elytra, levitation, slow falling).
        suppressFlight(lock, target);

        // 3) Wrap, or keep dragging the target downwards.
        boolean ground = groundBelow(loc);
        boolean liquid = target.isInWater() || target.isInLava();
        double y = loc.getY();

        if (ground || liquid) {
            wrapUp(lock, target, ground && !liquid);
        } else if (lock.lastWrapY - y >= loopHeight) {
            wrapUp(lock, target, false);
        } else {
            // If something (a flight ability) stopped the descent, win the tug of war by moving the target down.
            if (lock.lastY - y < 0.02) {
                forceDown(target, loc);
            }
            Vector velocity = target.getVelocity();
            target.setVelocity(new Vector(0, Math.min(velocity.getY(), -minFallSpeed), 0));
        }
        lock.lastY = target.getLocation().getY();
    }

    /**
     * Sends the target back up its line.
     * Ground impact: back to the full loop height. In-air wrap: up by (height - pull-back), so altitude is lost.
     */
    private void wrapUp(Lock lock, LivingEntity target, boolean groundImpact) {
        if (groundImpact) {
            applyImpactDamage(target);
            if (target.isDead()) {
                return; // the tick loop cleans the lock up
            }
        }

        double room = clearanceAbove(target, loopHeight);
        if (room < minClearance) {
            lock.remaining = 0; // the sky above is blocked now: the loop collapses
            return;
        }
        double up = groundImpact || lock.age == 0 ? room : Math.max(1.0, room - airPullBack);

        Location from = target.getLocation();
        Fx.burst(from.clone().add(0, 1, 0));
        Fx.ring(from.clone().add(0, 0.2, 0), 0.9, 16);

        Location to = from.clone();
        to.setX(lock.anchorX);
        to.setZ(lock.anchorZ);
        to.setY(from.getY() + up);
        teleport(target, to);

        target.setFallDistance(0f);
        target.setVelocity(new Vector(0, -minFallSpeed, 0));
        lock.lastWrapY = to.getY();
        lock.lastY = to.getY();

        Fx.burst(to.clone().add(0, 1, 0));
        Fx.ring(to.clone().add(0, 0.2, 0), 0.9, 16);
        Fx.sound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1.3f);
    }

    private void applyImpactDamage(LivingEntity target) {
        AttributeInstance maxHealth = target.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth != null ? maxHealth.getValue() : 20.0;
        double amount = max * impactPercent / 100.0;
        internal = true;
        try {
            target.damage(amount, DamageSource.builder(DamageType.FALL).build());
        } finally {
            internal = false;
        }
    }

    // ------------------------------------------------------------------ flight suppression

    private void suppressFlight(Lock lock, LivingEntity target) {
        if (target instanceof Player player) {
            if (player.getAllowFlight() || player.isFlying()) {
                player.setFlying(false);
                player.setAllowFlight(false);
            }
            if (player.isGliding()) {
                player.setGliding(false);
            }
        }
        for (PotionEffectType type : SUPPRESSED) {
            target.removePotionEffect(type);
        }
    }

    /** Puts the original flight state and suppressed effects back. */
    private void release(Lock lock, Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        if (living instanceof Player player) {
            player.setAllowFlight(lock.allowFlight);
            if (lock.allowFlight && lock.flying) {
                player.setFlying(true);
            }
        }
        for (PotionEffect effect : lock.suppressed) {
            if (effect.getDuration() < 0) {
                living.addPotionEffect(effect); // infinite effect
                continue;
            }
            int left = effect.getDuration() - lock.age;
            if (left > 0) {
                living.addPotionEffect(new PotionEffect(effect.getType(), left, effect.getAmplifier(),
                        effect.isAmbient(), effect.hasParticles(), effect.hasIcon()));
            }
        }
    }

    // ------------------------------------------------------------------ world queries and movement

    /** Free blocks above the target's head, up to {@code max}, rounded down with a small safety margin. */
    private double clearanceAbove(LivingEntity target, double max) {
        World world = target.getWorld();
        Location top = target.getLocation().add(0, target.getHeight(), 0);
        double worldRoom = world.getMaxHeight() - top.getY() - 1.0;
        RayTraceResult hit = world.rayTraceBlocks(top, new Vector(0, 1, 0), max + 1.0, FluidCollisionMode.NEVER, true);
        double free = hit == null ? max : hit.getHitPosition().getY() - top.getY() - 0.25;
        return Math.max(0.0, Math.floor(Math.min(Math.min(free, max), worldRoom)));
    }

    private boolean groundBelow(Location feet) {
        RayTraceResult hit = feet.getWorld().rayTraceBlocks(feet, new Vector(0, -1, 0), 0.25, FluidCollisionMode.NEVER, true);
        return hit != null;
    }

    private void forceDown(LivingEntity target, Location loc) {
        RayTraceResult hit = loc.getWorld().rayTraceBlocks(loc, new Vector(0, -1, 0), FORCE_DOWN_STEP, FluidCollisionMode.NEVER, true);
        double newY = hit != null ? hit.getHitPosition().getY() : loc.getY() - FORCE_DOWN_STEP;
        loc.setY(newY);
        teleport(target, loc);
    }

    private void teleport(LivingEntity target, Location destination) {
        internal = true;
        try {
            target.teleport(destination);
        } finally {
            internal = false;
        }
    }

    // ------------------------------------------------------------------ events

    /** Vanilla fall damage is replaced by the percentage-based impact damage. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (internal || event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        if (locks.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Pearls, chorus fruit, portals and teleport commands break the loop. Small plugin nudges are ignored. */
    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (internal) {
            return;
        }
        Lock lock = locks.get(event.getPlayer().getUniqueId());
        if (lock == null || event.getTo() == null) {
            return;
        }
        double dx = event.getTo().getX() - lock.anchorX;
        double dz = event.getTo().getZ() - lock.anchorZ;
        if (dx * dx + dz * dz > 9.0) {
            lock.remaining = 0; // the tick loop releases the lock on its next pass
        }
    }

    /** Restore flight state before the player's data is saved. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Lock lock = locks.remove(event.getPlayer().getUniqueId());
        if (lock != null) {
            release(lock, event.getPlayer());
        }
    }

    @Override
    public void shutdown() {
        for (Lock lock : new ArrayList<>(locks.values())) {
            release(lock, Bukkit.getEntity(lock.target));
        }
        locks.clear();
    }
}
