package dev.grafting;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;

/** Particle and sound helpers. Everything Grafting does is purple. */
public final class Fx {

    public static final Particle.DustOptions PURPLE = new Particle.DustOptions(Color.fromRGB(150, 60, 220), 1.2f);

    private Fx() {
    }

    /** Purple haze around an entity. */
    public static void aura(LivingEntity entity) {
        double half = entity.getHeight() / 2.0;
        Location center = entity.getLocation().add(0, half, 0);
        entity.getWorld().spawnParticle(Particle.DUST, center, 8, 0.45, half, 0.45, 0, PURPLE);
    }

    /** Horizontal ring of purple dust. */
    public static void ring(Location center, double radius, int points) {
        World world = center.getWorld();
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            world.spawnParticle(Particle.DUST,
                    center.getX() + radius * Math.cos(angle), center.getY(), center.getZ() + radius * Math.sin(angle),
                    1, 0, 0, 0, 0, PURPLE);
        }
    }

    public static void burst(Location location) {
        location.getWorld().spawnParticle(Particle.PORTAL, location, 30, 0.4, 0.8, 0.4, 0.3);
    }

    public static void sound(Location location, Sound sound, float pitch) {
        location.getWorld().playSound(location, sound, 1.0f, pitch);
    }
}
