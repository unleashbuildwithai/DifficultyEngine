package com.yourname.difficulty.monsters;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;

/**
 * CustomMonsterManager — Defines and spawns custom-named monsters.
 *
 * ── Config file: plugins/DifficultyEngine/monsters.yml ──────────────────
 *
 * monsters:
 *   ghost_boss:
 *     base_mob:   SKELETON
 *     name:       "§5The Ghost"
 *     health:     500
 *     damage:     15
 *     speed:      1.4
 *     drops:
 *       - NETHER_STAR:1
 *       - NETHERITE_HELMET:1
 *     effects:
 *       - LEACHED_AURA
 *
 * ── Spawn command ─────────────────────────────────────────────────────────
 *  Admin: /spawnmob <monster_id>
 *  Example: /spawnmob ghost_boss
 *
 * ── PDC tagging ──────────────────────────────────────────────────────────
 *  Every custom monster is tagged with PDC key "de_custom_mob" = monster_id.
 *  This allows BossEffectListener and GoldDropListener to identify them.
 */
public class CustomMonsterManager {

    /** PDC key for identifying custom monsters. */
    public static final String CUSTOM_MOB_KEY = "de_custom_mob";

    private final JavaPlugin plugin;
    private final File       configFile;
    private final NamespacedKey customMobKey;

    /** Monster ID → definition. */
    private final Map<String, CustomMonsterDef> definitions = new LinkedHashMap<>();

    public CustomMonsterManager(JavaPlugin plugin) {
        this.plugin      = plugin;
        this.configFile  = new File(plugin.getDataFolder(), "monsters.yml");
        this.customMobKey = new NamespacedKey(plugin, CUSTOM_MOB_KEY);
        saveDefaultConfig();
        loadDefinitions();
    }

    // ── Config management ─────────────────────────────────────────────────────

    private void saveDefaultConfig() {
        if (configFile.exists()) return;
        // Write a default monsters.yml with example entries
        String defaultContent =
            "# DifficultyEngine — Custom Monster Definitions\n" +
            "# \n" +
            "# monsters:\n" +
            "#   <monster_id>:\n" +
            "#     base_mob:  <ENTITY_TYPE>   # e.g. SKELETON, ZOMBIE, WITHER_SKELETON\n" +
            "#     name:      \"<display name>\" # supports §colour codes\n" +
            "#     health:    <number>         # max health (default: vanilla)\n" +
            "#     damage:    <number>         # attack damage\n" +
            "#     speed:     <number>         # movement speed multiplier\n" +
            "#     drops:                      # list of MATERIAL:amount\n" +
            "#       - NETHER_STAR:1\n" +
            "#     effects:                    # LEACHED_AURA, SHRIEK_AURA, etc.\n" +
            "#       - LEACHED_AURA\n" +
            "\n" +
            "monsters:\n" +
            "  ghost_boss:\n" +
            "    base_mob: SKELETON\n" +
            "    name: \"§5The Ghost\"\n" +
            "    health: 500\n" +
            "    damage: 15\n" +
            "    speed: 1.4\n" +
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

                definitions.put(id.toLowerCase(),
                        new CustomMonsterDef(id, type, name, health, damage, speed, drops, effects));
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
     * @return the spawned entity, or null if the ID is unknown
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
        // Spawn base entity
        Entity entity = location.getWorld().spawnEntity(location, def.entityType());
        if (!(entity instanceof LivingEntity mob)) {
            entity.remove();
            return null;
        }

        // ── Name ──────────────────────────────────────────────────────────
        mob.setCustomName(def.name());
        mob.setCustomNameVisible(true);

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

        // ── Custom drops stored in PDC list ───────────────────────────────
        // Drops are handled in CustomMonsterDropListener

        // ── Effects ───────────────────────────────────────────────────────
        for (String effect : def.effects()) {
            applyEffect(mob, effect.toUpperCase());
        }

        plugin.getLogger().fine("[Monsters] Spawned '" + def.id() + "' at " + location);
        return mob;
    }

    private void applyEffect(LivingEntity mob, String effect) {
        switch (effect) {
            case "FIRE_IMMUNE" -> mob.setFireTicks(0);
            case "GLOWING"     -> mob.addPotionEffect(
                    new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));
            case "INVISIBLE"   -> mob.addPotionEffect(
                    new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
            // LEACHED_AURA and SHRIEK_AURA are handled by BossEffectListener
            // after the mob is registered as a boss
            default -> plugin.getLogger().fine("[Monsters] Unknown effect: " + effect);
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Map<String, CustomMonsterDef> getDefinitions() {
        return Collections.unmodifiableMap(definitions);
    }

    public CustomMonsterDef getDefinition(String id) {
        return definitions.get(id.toLowerCase());
    }

    /** Returns true if this entity is a custom monster. */
    public boolean isCustomMonster(Entity entity) {
        if (!(entity instanceof LivingEntity le)) return false;
        return le.getPersistentDataContainer().has(customMobKey, PersistentDataType.STRING);
    }

    /** Returns the monster ID of a custom monster, or null. */
    public String getCustomMonsterId(Entity entity) {
        if (!(entity instanceof LivingEntity le)) return null;
        return le.getPersistentDataContainer()
                .getOrDefault(customMobKey, PersistentDataType.STRING, null);
    }

    public int getDefinitionCount() { return definitions.size(); }

    /** Reloads monster definitions from disk. */
    public void reload() { loadDefinitions(); }
}
