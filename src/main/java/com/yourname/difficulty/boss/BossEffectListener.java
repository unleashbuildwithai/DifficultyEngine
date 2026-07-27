package com.yourname.difficulty.boss;

import com.yourname.difficulty.items.ItemFactory;
import com.yourname.difficulty.magic.MagicElement;
import com.yourname.difficulty.skills.SkillManager;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.NamespacedKey;

import java.util.*;

/**
 * BossEffectListener — Handles all boss-related effect events.
 *
 * ── Responsibilities ──────────────────────────────────────────────────────
 *  1. EntityDamageByEntityEvent
 *     • When boss damages player → apply NAUSEATED (Nausea 5s)
 *     • When player with Earth Staff attacks boss during boss's Fire attack
 *       → cancel fire damage
 *     • When player with Air Staff hits the Shriek ArmorStand
 *       → remove the stand, mark boss VULNERABLE for 5s
 *
 *  2. PlayerMoveEvent
 *     • Check if player enters a "Splat" zone (PDC-tagged ArmorStand area)
 *     • If so, apply LEACHED + NAUSEATED from registry
 *
 *  3. EntityDeathEvent
 *     • Clean up tracked boss from the boss set and registry
 *
 *  4. Boss registration
 *     • When a boss entity spawns (via BossEventListener), it should be
 *       registered via registerBoss(). This listener tags the boss with PDC.
 *
 * NOTE: The castEnchantment() buff system and the boss immunity /
 * non-hostility rules that used to live in this class have been extracted
 * to {@link BossEnchantmentManager} and {@link BossImmunityListener}
 * respectively (see getEnchantmentManager()), to keep this file under the
 * project's 400-line cleanliness guideline. Behaviour is unchanged.
 */
public class BossEffectListener implements Listener {

    // ── PDC Keys ──────────────────────────────────────────────────────────────
    private static final String PDC_IS_BOSS     = "de_is_boss";
    private static final String PDC_IS_SPLAT    = "de_splat_zone";
    private static final String PDC_IS_SHRIEK   = "de_shriek_stand";

    /** Radius around a Splat-zone ArmorStand that triggers the effect. */
    private static final double SPLAT_RADIUS = 3.5;

    /** Damage bonus multiplier when attacking a Vulnerable boss. */
    private static final double VULNERABLE_DAMAGE_BONUS = 2.0;

    /** Vulnerability duration (5 seconds). */
    private static final long VULNERABLE_DURATION_MS = 5_000L;

    private final JavaPlugin     plugin;
    private final EffectRegistry registry;
    private final ItemFactory    itemFactory;

    /** Set of active tracked boss UUIDs — shared with BossEffectTask. */
    public final Set<UUID> trackedBosses = Collections.synchronizedSet(new HashSet<>());

    /** castEnchantment logic extracted to its own class — see getEnchantmentManager(). */
    private final BossEnchantmentManager enchantmentManager;

    private final NamespacedKey bossPdcKey;
    private final NamespacedKey splatPdcKey;
    private final NamespacedKey shriekPdcKey;

    public BossEffectListener(JavaPlugin plugin, EffectRegistry registry,
                               SkillManager skillManager, ItemFactory itemFactory) {
        this.plugin       = plugin;
        this.registry     = registry;
        this.itemFactory  = itemFactory;
        this.enchantmentManager = new BossEnchantmentManager(registry, skillManager);
        this.bossPdcKey   = new NamespacedKey(plugin, PDC_IS_BOSS);
        this.splatPdcKey  = new NamespacedKey(plugin, PDC_IS_SPLAT);
        this.shriekPdcKey = new NamespacedKey(plugin, PDC_IS_SHRIEK);

        // Sweep orphaned shriek stands left from prior reloads/crashes with a small delay on startup
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (org.bukkit.World world : plugin.getServer().getWorlds()) {
                for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
                    if (stand.getPersistentDataContainer().has(shriekPdcKey, PersistentDataType.BYTE) 
                            || "§5⚡ Shriek".equals(stand.getCustomName())) {
                        stand.remove();
                    }
                }
            }
            plugin.getLogger().info("[Boss] Completed startup sweep of orphaned Shriek stands.");
        }, 100L); // 5 seconds delay to ensure worlds are fully loaded
    }

    /** Access to the extracted castEnchantment()/hasEnchantmentBuff() logic. */
    public BossEnchantmentManager getEnchantmentManager() {
        return enchantmentManager;
    }

    // ── Boss registration ─────────────────────────────────────────────────────

    /**
     * Registers a living entity as a trackable boss.
     * Tags it with PDC and adds it to the tracked set.
     */
    public void registerBoss(LivingEntity boss) {
        boss.getPersistentDataContainer()
                .set(bossPdcKey, PersistentDataType.BYTE, (byte) 1);
        trackedBosses.add(boss.getUniqueId());
    }

    /** Returns true if the entity is a registered boss. */
    public boolean isBoss(Entity entity) {
        if (!(entity instanceof LivingEntity le)) return false;
        return le.getPersistentDataContainer().has(bossPdcKey, PersistentDataType.BYTE)
                || trackedBosses.contains(entity.getUniqueId());
    }

    /**
     * Spawns a Shriek distortion ArmorStand near the boss.
     * The ArmorStand is invisible, invulnerable, and tagged with PDC.
     */
    public void spawnShriek(LivingEntity boss) {
        Location loc = boss.getLocation().clone().add(0, 0.1, 0);
        ArmorStand stand = (ArmorStand) boss.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setInvulnerable(false); // Allow Air Staff hits
        stand.setGravity(false);
        stand.setCustomName("§5⚡ Shriek");
        stand.setCustomNameVisible(true);
        stand.getPersistentDataContainer()
                .set(shriekPdcKey, PersistentDataType.BYTE, (byte) 1);
        stand.setPersistent(false); // Do not save to world files — prevents orphaned ghost stands on restart!
        registry.registerShriek(boss.getUniqueId(), stand.getUniqueId());
        plugin.getLogger().info("[Boss] Shriek spawned for " + boss.getType() + " at " + loc);

        // Automatically remove the shriek stand after 30 seconds (600 ticks) so it doesn't just sit there forever spitting particles
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (stand.isValid()) {
                stand.remove();
                registry.clearShriek(boss.getUniqueId());
                plugin.getLogger().info("[Boss] Shriek automatically cleared for " + boss.getType() + " after timeout.");
            }
        }, 600L); // 30 seconds
    }

    // ── Splat zone spawning ───────────────────────────────────────────────────

    /**
     * Spawns a Splat zone marker at the given location.
     * This is an invisible ArmorStand tagged with PDC_IS_SPLAT.
     * Players who walk within SPLAT_RADIUS blocks receive Leached + Nauseated.
     *
     * @param durationTicks how long the zone persists (0 = permanent until removed)
     */
    public void spawnSplatZone(Location loc, int durationTicks) {
        ArmorStand marker = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        marker.setVisible(false);
        marker.setInvulnerable(true);
        marker.setGravity(false);
        marker.setCustomName("§4● Splat Zone");
        marker.setCustomNameVisible(false);
        marker.getPersistentDataContainer()
                .set(splatPdcKey, PersistentDataType.BYTE, (byte) 1);

        // Particle ring to show the zone
        plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            int ticks = 0;
            @Override public void run() {
                if (!marker.isValid() || marker.isDead()) return;
                ticks++;
                // Draw a ring of particles
                for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 8) {
                    double x = SPLAT_RADIUS * Math.cos(angle);
                    double z = SPLAT_RADIUS * Math.sin(angle);
                    loc.getWorld().spawnParticle(Particle.LAVA,
                            loc.clone().add(x, 0.1, z), 1, 0, 0, 0, 0);
                }
                if (durationTicks > 0 && ticks >= durationTicks) {
                    marker.remove();
                }
            }
        }, 0L, 10L);
    }

    // ── EntityDamageByEntityEvent ─────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {

        // ── 1. Boss damages Player → Nauseated ────────────────────────────
        if (isBoss(event.getDamager()) && event.getEntity() instanceof Player victim) {
            registry.applyTicks(victim.getUniqueId(), EffectType.NAUSEATED, 100); // 5s
            victim.addPotionEffect(new PotionEffect(
                    PotionEffectType.NAUSEA, 100, 0, false, true, true));
        }

        // ── 2. Player attacks an entity ───────────────────────────────────
        if (!(event.getDamager() instanceof Player attacker)) return;

        Entity target = event.getEntity();

        // ── 2a. Air Staff on Shriek ArmorStand → Vulnerable boss ──────────
        if (target instanceof ArmorStand stand
                && stand.getPersistentDataContainer().has(shriekPdcKey, PersistentDataType.BYTE)) {

            ItemStack held = attacker.getInventory().getItemInMainHand();
            MagicElement el = itemFactory.getStaffElement(held);
            if (el == MagicElement.AIR) {
                event.setCancelled(true);
                stand.remove();

                // Find the boss this Shriek belongs to
                for (UUID bossUuid : trackedBosses) {
                    UUID shriekUuid = registry.getShriekStand(bossUuid);
                    if (shriekUuid != null && shriekUuid.equals(stand.getUniqueId())) {
                        registry.clearShriek(bossUuid);
                        registry.setVulnerable(bossUuid, VULNERABLE_DURATION_MS);

                        // Visual + sound feedback
                        stand.getWorld().strikeLightningEffect(stand.getLocation());
                        stand.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER,
                                stand.getLocation(), 5, 1, 1, 1, 0);

                        attacker.sendMessage("§b⚡ §7Wind shattered the §5Shriek§7! Boss is §c§lVULNERABLE §7for 5 seconds!");
                        attacker.sendTitle("§c§lBOSS VULNERABLE!", "§75 seconds — deal bonus damage!", 5, 60, 10);
                        break;
                    }
                }
                return;
            }
        }

        // ── 2b. Vulnerable boss → bonus damage ────────────────────────────
        if (isBoss(target) && registry.isVulnerable(target.getUniqueId())) {
            event.setDamage(event.getDamage() * VULNERABLE_DAMAGE_BONUS);
            attacker.sendActionBar("§c⚔ §7Vulnerable hit! §c×" + VULNERABLE_DAMAGE_BONUS + " §7damage!");
        }

        // ── 2c. Earth Staff vs boss Fire attack → cancel fire damage ──────
        // If the boss is the FIRE type and player attacks with Earth Staff,
        // remove fire from the player and cancel incoming fire damage.
        if (isBoss(target)) {
            ItemStack held = attacker.getInventory().getItemInMainHand();
            MagicElement el = itemFactory.getStaffElement(held);
            if (el == MagicElement.EARTH && attacker.getFireTicks() > 0) {
                attacker.setFireTicks(0);
                attacker.sendActionBar("§2🌿 §7Earth cancelled fire damage!");
            }
        }
    }

    // ── PlayerMoveEvent — Splat zone detection ────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only check if the player moved to a new block (performance)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        Player player = event.getPlayer();
        Location loc  = player.getLocation();

        // Check nearby ArmorStands for Splat zones
        for (Entity nearby : loc.getWorld().getNearbyEntities(
                loc, SPLAT_RADIUS + 1, 2, SPLAT_RADIUS + 1)) {
            if (!(nearby instanceof ArmorStand stand)) continue;
            if (!stand.getPersistentDataContainer().has(splatPdcKey, PersistentDataType.BYTE)) continue;

            double dist = nearby.getLocation().distance(loc);
            if (dist <= SPLAT_RADIUS) {
                registry.applyTicks(player.getUniqueId(), EffectType.SPLAT,    60); // 3s
                registry.applyTicks(player.getUniqueId(), EffectType.LEACHED, 100); // 5s
                player.sendActionBar("§4☠ §cYou are in a Splat zone! §4☠");
            }
        }
    }

    // ── EntityDeathEvent — Boss cleanup ───────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossDeath(EntityDeathEvent event) {
        UUID uuid = event.getEntity().getUniqueId();
        if (!trackedBosses.contains(uuid)) return;

        trackedBosses.remove(uuid);
        registry.clearVulnerable(uuid);

        // Remove the Shriek stand if it's still around
        UUID shriekUuid = registry.getShriekStand(uuid);
        if (shriekUuid != null) {
            Entity shriek = plugin.getServer().getEntity(shriekUuid);
            if (shriek != null) shriek.remove();
            registry.clearShriek(uuid);
        }
    }
}
