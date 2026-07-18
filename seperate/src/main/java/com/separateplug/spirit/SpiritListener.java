package com.separateplug.spirit;

import com.separateplug.combat.CombatHitBar;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * SpiritListener — Core listener for the spirit system.
 *
 * ── Join/Quit ──────────────────────────────────────────────────────────────────
 *  First join: random spirit assigned, staff given, title shown.
 *  Quit:       spirit data saved and evicted from cache.
 *
 * ── Spirit staff casting (right-click) ────────────────────────────────────────
 *  Fires a Snowball in the crosshair direction — NO aimbot.
 *    FIRE  bolt → on hit: 3s fire + damage
 *    WATER bolt → on hit: Slowness II (4s) + damage
 *    EARTH bolt → on hit: Mining Fatigue + Slowness (4s) + damage
 *    AIR   bolt → on hit: Massive knockback + damage
 *
 * ── Staff drop on death / kill ────────────────────────────────────────────────
 *  Player death: 5 % chance to add a copy of their bound spirit staff to drops.
 *  Player kills: 5 % chance to give the killer a copy of the victim's bound staff.
 */
public class SpiritListener implements Listener {

    private static final Random  RAND       = new Random();
    private static final double  BOLT_SPEED = 2.5;
    private static final long    COOLDOWN   = 1500L; // ms

    private final JavaPlugin     plugin;
    private final SpiritManager  spiritManager;
    private final SpiritItems    spiritItems;
    private final CombatHitBar   combatBar;

    /** Projectile UUID → caster UUID */
    private final Map<UUID, UUID>         projOwner   = new HashMap<>();
    /** Projectile UUID → SpiritType */
    private final Map<UUID, SpiritType>   projElement = new HashMap<>();
    /** Caster UUID → last cast millis */
    private final Map<UUID, Long>         cooldowns   = new HashMap<>();

    public SpiritListener(JavaPlugin plugin, SpiritManager spiritManager,
                          SpiritItems spiritItems, CombatHitBar combatBar) {
        this.plugin        = plugin;
        this.spiritManager = spiritManager;
        this.spiritItems   = spiritItems;
        this.combatBar     = combatBar;
    }

    // ── Join / Quit ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (spiritManager.isFirstJoin(player.getUniqueId())) {
            // First ever join — assign random spirit and give staff
            SpiritType type = spiritManager.assignRandomSpirit(player.getUniqueId());
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                player.getInventory().addItem(spiritItems.buildSpiritStaff(type));
                player.sendTitle(
                    type.color + "§l" + type.name().charAt(0)
                        + type.name().substring(1).toLowerCase() + " Spirit",
                    "§7Your bound spirit has been chosen!",
                    10, 60, 20
                );
                player.sendMessage(type.color + "✦ §7Your spirit: §r" + type.displayName
                    + type.color + "  — §7No runes needed. Just right-click!");
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }, 20L);
        } else {
            spiritManager.loadPlayer(player);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        spiritManager.savePlayer(event.getPlayer().getUniqueId());
        spiritManager.evict(event.getPlayer().getUniqueId());
        combatBar.remove(event.getPlayer().getUniqueId());
    }

    // ── Spirit staff casting ──────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
        SpiritType type = spiritItems.getSpiritTypeFromItem(hand);
        if (type == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        // Cooldown check (1.5s)
        long now = System.currentTimeMillis();
        if (now - cooldowns.getOrDefault(player.getUniqueId(), 0L) < COOLDOWN) {
            long left = COOLDOWN - (now - cooldowns.getOrDefault(player.getUniqueId(), 0L));
            player.sendActionBar(type.color + "Cooldown: §e" + String.format("%.1f", left / 1000.0) + "s");
            return;
        }
        cooldowns.put(player.getUniqueId(), now);

        Snowball bolt = player.launchProjectile(Snowball.class);
        bolt.setVelocity(player.getLocation().getDirection().multiply(BOLT_SPEED));
        projOwner.put(bolt.getUniqueId(), player.getUniqueId());
        projElement.put(bolt.getUniqueId(), type);

        // Particle trail
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!bolt.isValid()) { task.cancel(); return; }
            try {
                if (type.trailParticle == Particle.BLOCK) {
                    bolt.getWorld().spawnParticle(Particle.BLOCK, bolt.getLocation(),
                        4, 0.1, 0.1, 0.1, Material.DIRT.createBlockData());
                } else {
                    bolt.getWorld().spawnParticle(type.trailParticle,
                        bolt.getLocation(), 4, 0.1, 0.1, 0.1, 0.05);
                }
            } catch (Exception ignored) {}
        }, 0L, 1L);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 0.8f, 1.4f);
        player.sendActionBar(type.color + type.displayName + " §7bolt fired!");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        UUID projId   = event.getEntity().getUniqueId();
        UUID ownerId  = projOwner.remove(projId);
        SpiritType type = projElement.remove(projId);
        if (ownerId == null || type == null) return;
        if (!(event.getHitEntity() instanceof LivingEntity target)) return;
        if (target.getUniqueId().equals(ownerId)) return;

        Player owner = plugin.getServer().getPlayer(ownerId);

        switch (type) {
            case FIRE -> {
                target.setFireTicks(60); // 3s
                target.damage(4.0, owner);
                target.getWorld().spawnParticle(Particle.FLAME,
                    target.getLocation().add(0, 1, 0), 20, 0.4, 0.4, 0.4, 0.05);
                if (owner != null) owner.sendActionBar("§c🔥 §7Fire bolt hit!");
            }
            case WATER -> {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 2, false, true, true));
                target.damage(3.0, owner);
                target.getWorld().spawnParticle(Particle.SPLASH,
                    target.getLocation().add(0, 1, 0), 30, 0.4, 0.4, 0.4, 0.1);
                if (owner != null) owner.sendActionBar("§b💧 §7Water bolt hit! Target slowed.");
            }
            case EARTH -> {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 2, false, true, true));
                target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 80, 1, false, true, true));
                target.damage(3.5, owner);
                target.getWorld().spawnParticle(Particle.BLOCK,
                    target.getLocation().add(0, 1, 0), 20, 0.4, 0.4, 0.4,
                    Material.DIRT.createBlockData());
                if (owner != null) owner.sendActionBar("§2🌿 §7Earth bolt hit! Target grounded.");
            }
            case AIR -> {
                Vector dir = owner != null
                    ? target.getLocation().subtract(owner.getLocation()).toVector().normalize()
                    : new Vector(0, 1, 0);
                dir.setY(0.4);
                target.setVelocity(dir.multiply(2.0));
                target.damage(2.5, owner);
                target.getWorld().spawnParticle(Particle.CLOUD,
                    target.getLocation().add(0, 1, 0), 30, 0.6, 0.6, 0.6, 0.2);
                if (owner != null) owner.sendActionBar("§7💨 §7Air bolt hit! Target blasted away!");
            }
        }

        // Register hit in combat bar
        if (owner != null && target instanceof Player) {
            boolean filled = combatBar.registerHit(owner);
            if (filled) {
                owner.sendActionBar("§c§l⚡ STUN READY! §7— your next strike will stun!");
                owner.playSound(owner.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);
            }
        }
    }

    // ── Staff drop on death / kill ─────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        SpiritType spirit = spiritManager.getBoundSpirit(victim.getUniqueId());
        if (spirit == null) return;

        // 5 % chance to drop a copy of the bound spirit staff
        if (RAND.nextInt(100) < 5) {
            event.getDrops().add(spiritItems.buildSpiritStaff(spirit));
            // Broadcast to nearby players
            for (Entity e : victim.getWorld().getNearbyEntities(victim.getLocation(), 30, 30, 30)) {
                if (e instanceof Player p)
                    p.sendMessage("§6★ §7" + victim.getName() + "'s " + spirit.displayName
                        + " §7spirit staff was dropped!");
            }
        }

        // Kill reward: if killed by a player, 5 % chance killer gets a duplicate staff
        if (victim.getKiller() != null) {
            Player killer = victim.getKiller();
            if (RAND.nextInt(100) < 5) {
                killer.getInventory().addItem(spiritItems.buildSpiritStaff(spirit));
                killer.sendMessage("§6★ §7You collected " + victim.getName() + "'s "
                    + spirit.displayName + "§7!");
                killer.playSound(killer.getLocation(),
                    Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
            }
        }
    }

    // ── Combat bar: track melee hits too ──────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMeleeHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player)) return;
        // Register hit for any weapon hit between players (spirit staff OR stun sword)
        combatBar.registerHit(attacker);
    }
}
