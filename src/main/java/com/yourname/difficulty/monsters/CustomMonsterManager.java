package com.yourname.difficulty.monsters;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;

import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/**
 * CustomMonsterManager — Defines and spawns fully custom, independent monsters.
 *
 * ── Clean custom-entity architecture ────────────────────────────────────────
 * We no longer "wrap" or reskin a vanilla mob (no giant bats, no modified
 * Withers/Blazes standing in for custom monsters). Instead every custom
 * monster is composed of TWO paired entities:
 *
 *  1. §7Base carrier§r — a completely §finvisible, silent§r vanilla entity
 *     (e.g. ZOMBIE) used ONLY for hitbox, physics, pathfinding AI, and
 *     damage/death handling. The client never sees its model — it is
 *     invisible and its nameplate is suppressed.
 *
 *  2. §7Visual display§r — an {@link ItemDisplay} entity that renders our
 *     actual custom look (a textured/model-data item), billboarded to
 *     always face the viewer. This display is what players actually see,
 *     and it is what carries the monster's name tag.
 *
 * A lightweight repeating sync task streams the carrier's live position to
 * the display every tick, so the "packet-driven" visual tracks the
 * invisible hitbox precisely without any vanilla model rendering underneath.
 *
 * ── Config file: plugins/DifficultyEngine/monsters.yml ──────────────────
 *
 * monsters:
 *   ghost_boss:
 *     base_mob:     SKELETON        # invisible physics/hitbox carrier
 *     name:         "§5The Ghost"
 *     health:       500
 *     damage:       15
 *     speed:        1.4
 *     display_item: NETHER_STAR     # what the client actually sees
 *     display_model_data: 0
 *     display_scale: 1.0
 *     drops:
 *       - NETHER_STAR:1
 *       - NETHERITE_HELMET:1
 *     effects:
 *       - LEACHED_AURA
 *
 * ── Spawn command ─────────────────────────────────────────────────────────
 *  Admin: /spawnmob <monster_id>
 *
 * ── PDC tagging ──────────────────────────────────────────────────────────
 *  Every custom monster's carrier entity is tagged with PDC key
 *  "de_custom_mob" = monster_id. This allows BossEffectListener and
 *  GoldDropListener to identify them.
 */
public class CustomMonsterManager implements Listener {

    /** PDC key for identifying custom monsters. */
    public static final String CUSTOM_MOB_KEY = "de_custom_mob";

    private final JavaPlugin plugin;
    private final File       configFile;
    private final NamespacedKey customMobKey;
    private final NamespacedKey displayLinkKey;
    private final NamespacedKey displayTagKey;

    /** Monster ID → definition. */
    private final Map<String, CustomMonsterDef> definitions = new LinkedHashMap<>();

    /** Carrier UUID → paired visual display UUID. Drives the per-tick sync task. */
    private final Map<UUID, UUID> carrierToDisplay = new HashMap<>();

    private BukkitRunnable syncTask;

    public CustomMonsterManager(JavaPlugin plugin) {
        this.plugin        = plugin;
        this.configFile     = new File(plugin.getDataFolder(), "monsters.yml");
        this.customMobKey   = new NamespacedKey(plugin, CUSTOM_MOB_KEY);
        this.displayLinkKey = new NamespacedKey(plugin, "de_custom_mob_carrier");
        this.displayTagKey  = new NamespacedKey(plugin, "de_custom_mob_display");
        saveDefaultConfig();
        loadDefinitions();
        startSyncTask();
    }

    // ── Config management ─────────────────────────────────────────────────────

    private void saveDefaultConfig() {
        if (configFile.exists()) return;
        String defaultContent =
            "# DifficultyEngine — Custom Monster Definitions\n" +
            "# \n" +
            "# monsters:\n" +
            "#   <monster_id>:\n" +
            "#     base_mob:  <ENTITY_TYPE>   # invisible physics/hitbox carrier only\n" +
            "#     name:      \"<display name>\" # supports §colour codes\n" +
            "#     health:    <number>\n" +
            "#     damage:    <number>\n" +
            "#     speed:     <number>\n" +
            "#     display_item: <MATERIAL>       # what players actually SEE\n" +
            "#     display_model_data: <int>      # optional resource-pack model id\n" +
            "#     display_scale: <float>         # visual scale multiplier\n" +
            "#     drops:\n" +
            "#       - NETHER_STAR:1\n" +
            "#     effects:\n" +
            "#       - LEACHED_AURA\n" +
            "\n" +
            "monsters:\n" +
            "  ghost_boss:\n" +
            "    base_mob: SKELETON\n" +
            "    name: \"§5The Ghost\"\n" +
            "    health: 500\n" +
            "    damage: 15\n" +
            "    speed: 1.4\n" +
            "    display_item: NETHER_STAR\n" +
            "    display_model_data: 0\n" +
            "    display_scale: 1.4\n" +
            "    drops:\n" +
            "      - NETHER_STAR:1\n" +
            "      - NETHERITE_HELMET:1\n" +
            "    effects:\n" +
            "      - LEACHED_AURA\n" +
            "\n" +
            "  lava_titan:\n" +
            "    base_mob: ZOMBIE\n" +
            "    name: \"§c§lLava Titan\"\n" +
            "    health: 800\n" +
            "    damage: 22\n" +
            "    speed: 0.9\n" +
            "    display_item: MAGMA_BLOCK\n" +
            "    display_model_data: 0\n" +
            "    display_scale: 2.2\n" +
            "    drops:\n" +
            "      - MAGMA_BLOCK:16\n" +
            "      - BLAZE_ROD:8\n" +
            "      - NETHER_STAR:1\n" +
            "    effects:\n" +
            "      - FIRE_IMMUNE\n" +
            "\n" +
            "  wind_wraith:\n" +
            "    base_mob: PHANTOM\n" +
            "    name: \"§f§lWind Wraith\"\n" +
            "    health: 300\n" +
            "    damage: 12\n" +
            "    speed: 1.8\n" +
            "    display_item: PHANTOM_MEMBRANE\n" +
            "    display_model_data: 0\n" +
            "    display_scale: 1.8\n" +
            "    drops:\n" +
            "      - PHANTOM_MEMBRANE:4\n" +
            "      - FEATHER:16\n" +
            "    effects:\n" +
            "      - SHRIEK_AURA\n";

        try {
            configFile.getParentFile().mkdirs();
            Files.writeString(configFile.toPath(), defaultContent);
        } catch (IOException e) {
            plugin.getLogger().warning("[Monsters] Failed to write default monsters.yml: " + e.getMessage());
        }
    }

    private void loadDefinitions() {
        definitions.clear();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection section = cfg.getConfigurationSection("monsters");
        if (section == null) {
            plugin.getLogger().info("[Monsters] No monsters defined in monsters.yml.");
            return;
        }

        for (String id : section.getKeys(false)) {
            ConfigurationSection mob = section.getConfigurationSection(id);
            if (mob == null) continue;

            try {
                EntityType type = EntityType.valueOf(
                        mob.getString("base_mob", "ZOMBIE").toUpperCase());
                String  name   = mob.getString("name", "§cCustom Mob");
                double  health = mob.getDouble("health", 20);
                double  damage = mob.getDouble("damage", 3);
                double  speed  = mob.getDouble("speed", 1.0);
                List<String> drops   = mob.getStringList("drops");
                List<String> effects = mob.getStringList("effects");

                Material displayItem;
                try {
                    displayItem = Material.valueOf(mob.getString("display_item", "NETHER_STAR").toUpperCase());
                } catch (IllegalArgumentException ex) {
                    displayItem = Material.NETHER_STAR;
                }
                int   displayModelData = mob.getInt("display_model_data", 0);
                float displayScale     = (float) mob.getDouble("display_scale", 1.0);

                definitions.put(id.toLowerCase(),
                        new CustomMonsterDef(id, type, name, health, damage, speed, drops, effects,
                                displayItem, displayModelData, displayScale));
                plugin.getLogger().info("[Monsters] Loaded: " + id + " (" + type + ", HP=" + health + ")");

            } catch (Exception e) {
                plugin.getLogger().warning("[Monsters] Failed to load '" + id + "': " + e.getMessage());
            }
        }
        plugin.getLogger().info("[Monsters] Loaded " + definitions.size() + " custom monster definitions.");
    }

    // ── Spawning ──────────────────────────────────────────────────────────────

    /**
     * Spawns a custom monster at the given location.
     *
     * @param monsterId  the key from monsters.yml (e.g. "ghost_boss")
     * @param location   where to spawn
     * @return the spawned carrier entity, or null if the ID is unknown
     */
    public LivingEntity spawn(String monsterId, Location location) {
        CustomMonsterDef def = definitions.get(monsterId.toLowerCase());
        if (def == null) {
            plugin.getLogger().warning("[Monsters] Unknown monster: " + monsterId);
            return null;
        }
        return spawnDef(def, location);
    }

    private LivingEntity spawnDef(CustomMonsterDef def, Location location) {
        // ── 1. Spawn the invisible/silent physics + hitbox carrier ──────────
        Entity entity = location.getWorld().spawnEntity(location, def.baseEntityType());
        if (!(entity instanceof LivingEntity mob)) {
            entity.remove();
            return null;
        }

        // Never rendered — purely a physics/hitbox/AI carrier.
        mob.setInvisible(true);
        mob.setSilent(true);
        mob.setCustomNameVisible(false); // name lives on the display entity instead

        // ── HP ────────────────────────────────────────────────────────────
        AttributeInstance maxHp = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHp != null) {
            maxHp.setBaseValue(def.health());
            mob.setHealth(def.health());
        }

        // ── Damage ────────────────────────────────────────────────────────
        AttributeInstance atk = mob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(def.damage());

        // ── Speed ─────────────────────────────────────────────────────────
        AttributeInstance spd = mob.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (spd != null) spd.setBaseValue(def.speed() * 0.25); // normalize

        // ── PDC tag ───────────────────────────────────────────────────────
        mob.getPersistentDataContainer()
                .set(customMobKey, PersistentDataType.STRING, def.id());

        // ── Effects ───────────────────────────────────────────────────────
        for (String effect : def.effects()) {
            applyEffect(mob, effect.toUpperCase());
        }

        // ── 2. Spawn the visual display (billboarded, no vanilla model) ─────
        ItemDisplay display = spawnVisualDisplay(mob, def);

        carrierToDisplay.put(mob.getUniqueId(), display.getUniqueId());

        plugin.getLogger().fine("[Monsters] Spawned '" + def.id() + "' at " + location);
        return mob;
    }

    /** Builds and spawns the ItemDisplay that represents this monster's visual appearance. */
    private ItemDisplay spawnVisualDisplay(LivingEntity carrier, CustomMonsterDef def) {
        Location displayLoc = carrier.getLocation().clone().add(0, 0.05, 0);

        ItemStack visualItem = new ItemStack(def.displayItem());
        if (def.displayCustomModelData() != 0) {
            ItemMeta meta = visualItem.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(def.displayCustomModelData());
                visualItem.setItemMeta(meta);
            }
        }

        ItemDisplay display = carrier.getWorld().spawn(displayLoc, ItemDisplay.class, d -> {
            d.setItemStack(visualItem);
            d.setBillboard(Display.Billboard.CENTER); // always faces the viewer — no rigging needed
            d.setPersistent(false);
            d.setCustomName(def.name());
            d.setCustomNameVisible(true);

            float scale = def.displayScale() <= 0f ? 1.0f : def.displayScale();
            Transformation t = new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new AxisAngle4f(0f, 0f, 0f, 1f),
                    new Vector3f(scale, scale, scale),
                    new AxisAngle4f(0f, 0f, 0f, 1f)
            );
            d.setTransformation(t);

            d.getPersistentDataContainer()
                    .set(displayTagKey, PersistentDataType.BYTE, (byte) 1);
            d.getPersistentDataContainer()
                    .set(displayLinkKey, PersistentDataType.STRING, carrier.getUniqueId().toString());
        });

        return display;
    }

    /** Repeating task that streams the carrier's live position to its paired display entity. */
    private void startSyncTask() {
        if (syncTask != null) return;
        syncTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (carrierToDisplay.isEmpty()) return;
                Iterator<Map.Entry<UUID, UUID>> it = carrierToDisplay.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<UUID, UUID> entry = it.next();
                    Entity carrier = plugin.getServer().getEntity(entry.getKey());
                    Entity display = plugin.getServer().getEntity(entry.getValue());

                    if (!(carrier instanceof LivingEntity le) || le.isDead() || !le.isValid()) {
                        if (display != null && !display.isDead()) display.remove();
                        it.remove();
                        continue;
                    }
                    if (display == null || display.isDead() || !display.isValid()) {
                        it.remove();
                        continue;
                    }

                    Location target = carrier.getLocation().clone().add(0, 0.05, 0);
                    // Only teleport when moved meaningfully (bandwidth-friendly)
                    if (!display.getLocation().getWorld().equals(target.getWorld())
                            || display.getLocation().distanceSquared(target) > 0.0004) {
                        display.teleport(target);
                    }
                }
            }
        };
        syncTask.runTaskTimer(plugin, 1L, 1L);
    }

    private void applyEffect(LivingEntity mob, String effect) {
        switch (effect) {
            case "FIRE_IMMUNE" -> mob.setFireTicks(0);
            case "GLOWING"     -> mob.addPotionEffect(
                    new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));
            // LEACHED_AURA and SHRIEK_AURA are handled by BossEffectListener
            // after the mob is registered as a boss
            default -> plugin.getLogger().fine("[Monsters] Unknown effect: " + effect);
        }
    }

    // ── Cleanup on death ───────────────────────────────────────────────────────

    /** Immediately removes the paired visual display when the carrier dies. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCarrierDeath(EntityDeathEvent event) {
        UUID carrierUuid = event.getEntity().getUniqueId();
        UUID displayUuid = carrierToDisplay.remove(carrierUuid);
        if (displayUuid == null) return;
        Entity display = plugin.getServer().getEntity(displayUuid);
        if (display != null && !display.isDead()) display.remove();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Map<String, CustomMonsterDef> getDefinitions() {
        return Collections.unmodifiableMap(definitions);
    }

    public CustomMonsterDef getDefinition(String id) {
        return definitions.get(id.toLowerCase());
    }

    /** Returns true if this entity is a custom monster carrier. */
    public boolean isCustomMonster(Entity entity) {
        if (!(entity instanceof LivingEntity le)) return false;
        return le.getPersistentDataContainer().has(customMobKey, PersistentDataType.STRING);
    }

    /** Returns the monster ID of a custom monster carrier, or null. */
    public String getCustomMonsterId(Entity entity) {
        if (!(entity instanceof LivingEntity le)) return null;
        return le.getPersistentDataContainer()
                .getOrDefault(customMobKey, PersistentDataType.STRING, null);
    }

    public int getDefinitionCount() { return definitions.size(); }

    /** Reloads monster definitions from disk. */
    public void reload() { loadDefinitions(); }

    /** Sweeps all orphaned custom-monster displays and cancels the sync task (call on plugin disable). */
    public void cleanup() {
        for (UUID displayUuid : new ArrayList<>(carrierToDisplay.values())) {
            Entity display = plugin.getServer().getEntity(displayUuid);
            if (display != null && !display.isDead()) display.remove();
        }
        carrierToDisplay.clear();
        if (syncTask != null) { syncTask.cancel(); syncTask = null; }

        // Sweep orphaned display entities tagged from a previous run/crash
        for (World world : plugin.getServer().getWorlds()) {
            for (ItemDisplay d : world.getEntitiesByClass(ItemDisplay.class)) {
                if (d.getPersistentDataContainer().has(displayTagKey, PersistentDataType.BYTE)) {
                    d.remove();
                }
            }
        }
    }
}
