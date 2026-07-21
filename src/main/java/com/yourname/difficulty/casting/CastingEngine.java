package com.yourname.difficulty.casting;

import com.yourname.difficulty.items.ItemFactory;
import com.yourname.difficulty.magic.MagicElement;
import com.yourname.difficulty.party.PartyManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.projectiles.ProjectileSource;
import java.util.concurrent.ThreadLocalRandom;
import com.yourname.difficulty.magic.LightningChargeManager;
import com.yourname.difficulty.bag.MagicBagManager;
import java.util.*;

/**
 * CastingEngine — Listens for SupportStaff right-clicks and resolves combos.
 *
 * ── Workflow ──────────────────────────────────────────────────────────────
 *  1. Player casts an element with a staff → CastingQueueManager adds it.
 *  2. Player right-clicks with the SupportStaff.
 *  3. CastingEngine reads queue.toString() → checks against comboMap.
 *  4. If match found → BuffLogic.apply(player, buffType).
 *  5. Queue is cleared. Discovered combos are added to the player's Arcane Tome.
 *
 * ── Support Staff Mechanics ──────────────────────────────────────────────
 *  Requires: 1× Support Rune + 1× Cooked Mutton OR Baked Potato per use.
 *
 *  Combo gate (hit party member → right-click within 5 s):
 *    With combo  → apply support spell buffs to the hit party member
 *    No combo    → AoE splash (heals party members in 8 blocks, damages others)
 *
 *  Support Pages (7 discoverable pages in inventory = unlock that buff):
 *    HEALING, SPEED, DEFENCE, COMBAT, STRENGTH, CRIT, PRAYER_PIERCE
 *
 * ── Combo Map ────────────────────────────────────────────────────────────
 *  Key format: "ELEMENT,ELEMENT[,ELEMENT[,ELEMENT]]"
 *  Built statically with all 15 combos from BuffType.
 *
 * ── SupportStaff ─────────────────────────────────────────────────────────
 *  Material: BLAZE_ROD with PDC key "de_support_staff"
 *  Craft: Book + Nether Star + all 4 rune types (any order, shapeless)
 */
public class CastingEngine implements Listener {

    /** PDC key identifying the SupportStaff item. */
    public static final String SUPPORT_STAFF_KEY = "de_support_staff";

    /** Cooldown map for Support Staff use to prevent healing/buff spamming. */
    private final Map<UUID, Long> lastSupportStaffUse = new HashMap<>();

    public static final String SELECTION_TITLE = "§5✦ Support Spell Selection ✦";

    private static final String[] SUPPORT_SPELL_IDS = {
        "vitality_surge",      // Healing
        "zephyr_momentum",     // Speed
        "aegis_ward",          // Defence
        "berserker_resonance", // Combat Boost
        "aetheric_shielding",  // Strength
        "fortune_aura",        // Crit
        "veil_of_silence"      // Prayer
    };

    private static final String[] SUPPORT_PAGE_KEYS = {
        ItemFactory.SUPPORT_PAGE_HEALING_KEY,
        ItemFactory.SUPPORT_PAGE_SPEED_KEY,
        ItemFactory.SUPPORT_PAGE_DEFENCE_KEY,
        ItemFactory.SUPPORT_PAGE_COMBAT_KEY,
        ItemFactory.SUPPORT_PAGE_STRENGTH_KEY,
        ItemFactory.SUPPORT_PAGE_CRIT_KEY,
        ItemFactory.SUPPORT_PAGE_PRAYER_KEY
    };

    private static final String[] SUPPORT_SPELL_NAMES = {
        "Healing (Vitality Surge)",
        "Faster Speed (Zephyr's Momentum)",
        "Defence (Aegis Ward)",
        "Combat Boost (Berserker's Resonance)",
        "Strength (Aetheric Shielding)",
        "Crit Attack (Fortune's Aura)",
        "Prayer Pierce (Veil of Silence)"
    };

    /** The full combo recipe map: queue key string → BuffType */
    private static final Map<String, BuffType> COMBO_MAP;

    static {
        COMBO_MAP = new LinkedHashMap<>();
        // ── 2-element combos ──────────────────────────────────────────────
        COMBO_MAP.put("FIRE,FIRE",    BuffType.INFERNO_BURST);
        COMBO_MAP.put("WATER,FIRE",   BuffType.STEAM_BLAST);
        COMBO_MAP.put("FIRE,WATER",   BuffType.STEAM_BLAST);
        COMBO_MAP.put("FIRE,EARTH",   BuffType.MAGMA_TRAP);
        COMBO_MAP.put("EARTH,FIRE",   BuffType.FORTIFY);
        COMBO_MAP.put("AIR,FIRE",     BuffType.TORNADO_FLAME);
        COMBO_MAP.put("FIRE,AIR",     BuffType.BLAZE_DASH);
        COMBO_MAP.put("WATER,WATER",  BuffType.TIDAL_SURGE);
        COMBO_MAP.put("EARTH,EARTH",  BuffType.STONE_SKIN);
        COMBO_MAP.put("AIR,AIR",      BuffType.GALE_FORCE);
        COMBO_MAP.put("EARTH,WATER",  BuffType.QUICKSAND);
        COMBO_MAP.put("WATER,EARTH",  BuffType.MUD_WALL);
        COMBO_MAP.put("AIR,WATER",    BuffType.MIST_VEIL);
        COMBO_MAP.put("WATER,AIR",    BuffType.CLEANSE);
        COMBO_MAP.put("EARTH,AIR",    BuffType.SANDSTORM);
        COMBO_MAP.put("AIR,EARTH",    BuffType.SANDSTORM);
        // ── 4-element grand combo ─────────────────────────────────────────
        COMBO_MAP.put("FIRE,WATER,EARTH,AIR", BuffType.GRAND_HARMONY);
        COMBO_MAP.put("WATER,FIRE,AIR,EARTH", BuffType.GRAND_HARMONY);
        COMBO_MAP.put("EARTH,AIR,FIRE,WATER", BuffType.GRAND_HARMONY);
        COMBO_MAP.put("AIR,EARTH,WATER,FIRE", BuffType.GRAND_HARMONY);
    }

    private final JavaPlugin          plugin;
    private final CastingQueueManager queueManager;
    private final BuffLogic            buffLogic;
    private final ItemFactory          itemFactory;
    private final NamespacedKey        staffKey;

    /** Optional PartyManager — wired in from Main after construction. */
    private PartyManager partyManager = null;
    private LightningChargeManager lightningChargeManager = null;
    private MagicBagManager magicBagManager = null;

    /** Tracks which combos each player has discovered (for Arcane Tome). */
    private final Map<UUID, Set<BuffType>> discovered = new HashMap<>();

    // ── Support Staff combo gate ──────────────────────────────────────────────
    /**
     * When a player left-clicks a party member while holding the support staff,
     * we record the target UUID and timestamp.  If they right-click the staff
     * within PARTY_HIT_WINDOW_MS the target receives the full support buff.
     */
    private static final long PARTY_HIT_WINDOW_MS = 5_000L;
    private final Map<UUID, UUID> lastPartyHitTarget = new HashMap<>();
    private final Map<UUID, Long> lastPartyHitTime   = new HashMap<>();

    public CastingEngine(JavaPlugin plugin, CastingQueueManager queueManager,
                         BuffLogic buffLogic, ItemFactory itemFactory) {
        this.plugin       = plugin;
        this.queueManager = queueManager;
        this.buffLogic    = buffLogic;
        this.itemFactory  = itemFactory;
        this.staffKey     = new NamespacedKey(plugin, SUPPORT_STAFF_KEY);
    }

    /** Wires in the PartyManager for combo-gate checks. */
    public void setPartyManager(PartyManager pm) { this.partyManager = pm; }
    public void setLightningChargeManager(LightningChargeManager lcm) { this.lightningChargeManager = lcm; }
    public void setMagicBagManager(MagicBagManager mbm) { this.magicBagManager = mbm; }

    // ── Build SupportStaff item ───────────────────────────────────────────────

    /** Builds the SupportStaff item (BREEZE_ROD with PDC + lore). */
    public ItemStack buildSupportStaff() {
        ItemStack staff = new ItemStack(Material.BREEZE_ROD);
        var meta = staff.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§5✦ §dSupport Staff");
            meta.setLore(List.of(
                "§7Cast elements, then right-click",
                "§7this staff to trigger a combo.",
                "§8(Fire, Water, Earth, Air)",
                "",
                "§5Combo gate: §7left-click a party",
                "§7member first (within 5s) for",
                "§5full support buffs§7.",
                "",
                "§8Requires: §dSupport Rune §8+ §6Cooked Mutton §8/ §eBaked Potato",
                "§5[Right-click to activate]"
            ));
            meta.getPersistentDataContainer()
                    .set(staffKey, PersistentDataType.BYTE, (byte) 1);
            staff.setItemMeta(meta);
        }
        return staff;
    }

    /** Returns true if the item is a SupportStaff. */
    public boolean isSupportStaff(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(staffKey, PersistentDataType.BYTE);
    }

    // ── Notify the engine that a player cast an element ──────────────────────

    /**
     * Called by MagicStaffListener whenever a player successfully casts a spell.
     * Adds the element to the player's queue and shows HUD feedback after a 2-tick
     * delay so the element's own action bar message displays first.
     */
    public void onElementCast(Player player, MagicElement element) {
        final List<MagicElement> queue = new ArrayList<>(queueManager.addCast(player.getUniqueId(), element));

        // ── Auto-resolve Combos ──────────────────────────────────────────────
        // The books are what make combos now! Combos resolve instantly on element cast,
        // completely bypassing the clunky Support Staff right-click.
        String key = queueManager.queueKey(player.getUniqueId());
        if (COMBO_MAP.containsKey(key)) {
            BuffType combo = COMBO_MAP.get(key);
            buffLogic.apply(player, combo);

            // Record discovery
            discovered.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(combo);

            // Clear queue
            queueManager.clearQueue(player.getUniqueId());
            player.sendActionBar("§5✦ §d§lCOMBO TRIGGERED! §d" + combo.name().replace('_', ' ') + " §5✦");
            return;
        }

        // Only show the queue HUD if the player has a Support Staff in their inventory.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            // Check if player has a support staff
            boolean hasSupportStaff = false;
            for (org.bukkit.inventory.ItemStack s : player.getInventory().getContents()) {
                if (isSupportStaff(s)) { hasSupportStaff = true; break; }
            }
            if (!hasSupportStaff) return; // No support staff — show no queue HUD

            StringBuilder hud = new StringBuilder("§7Queue: ");
            for (MagicElement el : queue) {
                hud.append(el.color).append(el.name().charAt(0));
                hud.append("§8·");
            }
            player.sendActionBar(hud + " §7(Automatic combo on next staff cast!)");
        }, 2L);
    }

    // ── Party member hit tracking (combo gate) ────────────────────────────────

    /**
     * When a player hits another player with the Support Staff (left-click),
     * and the target is in the same party, record it as the combo target.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPartyMemberHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity()  instanceof LivingEntity target)) return;

        // Must be holding support staff
        ItemStack hand = attacker.getInventory().getItemInMainHand();
        if (!isSupportStaff(hand)) return;

        // Check if target is a player in the same party
        boolean inSameParty = false;
        if (target instanceof Player targetPlayer && partyManager != null && partyManager.isInParty(attacker.getUniqueId())) {
            Set<UUID> members = partyManager.getPartyMembers(attacker.getUniqueId());
            if (members.contains(targetPlayer.getUniqueId())) {
                inSameParty = true;
            }
        }

        if (inSameParty) {
            // Cancel staff damage on party member
            event.setCancelled(true);

            // Record combo gate
            lastPartyHitTarget.put(attacker.getUniqueId(), target.getUniqueId());
            lastPartyHitTime.put(attacker.getUniqueId(), System.currentTimeMillis());

            attacker.sendActionBar("§5✦ §7Targeted §b" + target.getName() + "§7! Right-click within §a5s §7to apply buffs!");
            attacker.getWorld().spawnParticle(Particle.ENCHANT,
                    target.getLocation().add(0, 1, 0), 20, 0.4, 0.4, 0.4, 0.1);
            attacker.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        } else {
            // Not in same party -> cancel event damage and trigger basic staff attack!
            event.setCancelled(true);
            executeSupportBasicAttack(attacker);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSupportStaffLeftClickInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        ItemStack hand = event.getItem();
        if (!isSupportStaff(hand)) return;

        // Cancel default physical swings/block breaking
        event.setCancelled(true);
        executeSupportBasicAttack(event.getPlayer());
    }

    // ── SupportStaff right-click ──────────────────────────────────────────────

    private boolean hasSupportRequirements(Player player) {
        boolean hasRune = false;
        boolean hasFood = false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (itemFactory.isSupportRune(item)) hasRune = true;
            if (item.getType() == Material.COOKED_MUTTON || item.getType() == Material.BAKED_POTATO) hasFood = true;
        }
        return hasRune && hasFood;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSupportStaffUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (!isSupportStaff(item)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        // ── 7-second Cooldown Check ──────────────────────────────────────────
        long now = System.currentTimeMillis();
        Long lastUse = lastSupportStaffUse.get(player.getUniqueId());
        if (lastUse != null && now - lastUse < 7000L) {
            long timeLeft = (7000L - (now - lastUse)) / 1000L + 1;
            player.sendActionBar("§c✗ §7Support Staff is on cooldown for " + timeLeft + "s!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        // Check cost requirements: 1x Support Rune + (1x Cooked Mutton OR Baked Potato)
        if (!hasSupportRequirements(player)) {
            player.sendActionBar("§c✗ §7Requires §d1x Support Rune §7+ (§6Cooked Mutton §7or §eBaked Potato§7)!");
            player.sendMessage("§c✗ §7Requires §d1x Support Rune §7+ (§6Cooked Mutton §7or §eBaked Potato§7) to cast!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        lastSupportStaffUse.put(player.getUniqueId(), now);
        UUID casterId = player.getUniqueId();
        UUID targetId = lastPartyHitTarget.get(casterId);
        Long hitTime = lastPartyHitTime.get(casterId);
        boolean hasComboTarget = (targetId != null && hitTime != null && (System.currentTimeMillis() - hitTime <= 5000L));

        if (hasComboTarget) {
            Player targetPlayer = org.bukkit.Bukkit.getPlayer(targetId);
            if (targetPlayer != null && targetPlayer.isOnline() && targetPlayer.getWorld().equals(player.getWorld()) 
                    && targetPlayer.getLocation().distanceSquared(player.getLocation()) <= 900.0) {
                // Consume costs
                consumeSupportRune(player);
                consumeSupportFood(player);

                // Apply full buffs based on active support pages
                applyFullSupportBuffs(player, targetPlayer);

                // Clear combo target
                lastPartyHitTarget.remove(casterId);
                lastPartyHitTime.remove(casterId);
            } else {
                // Target offline/out-of-range -> fallback to splash support
                consumeSupportRune(player);
                consumeSupportFood(player);
                applySplashSupport(player);
            }
        } else {
            // Splash Mode
            consumeSupportRune(player);
            consumeSupportFood(player);
            applySplashSupport(player);
        }
    }

    private boolean hasSupportPotion(Player player, String id) {
        NamespacedKey pKey = new NamespacedKey(plugin, "de_support_potion_" + id);
        for (ItemStack s : player.getInventory().getContents()) {
            if (s != null && s.hasItemMeta() && s.getItemMeta().getPersistentDataContainer().has(pKey, PersistentDataType.BYTE)) {
                return true;
            }
        }
        return false;
    }

    public void openSelectionGUI(Player player) {
        org.bukkit.inventory.Inventory inv = org.bukkit.Bukkit.createInventory(null, 9, SELECTION_TITLE);

        for (int i = 0; i < 7; i++) {
            String id = SUPPORT_SPELL_IDS[i];
            String pageKey = SUPPORT_PAGE_KEYS[i];
            String name = SUPPORT_SPELL_NAMES[i];

            boolean hasPage = itemFactory.hasSupportPage(player, pageKey);
            boolean hasPotion = hasSupportPotion(player, id);

            ItemStack displayItem;
            if (hasPage && hasPotion) {
                displayItem = buildSelectionItem(id, name, true);
            } else {
                displayItem = buildSelectionItem(id, name, false);
            }
            inv.setItem(i, displayItem);
        }

        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        org.bukkit.inventory.meta.ItemMeta gm = glass.getItemMeta();
        if (gm != null) { gm.setDisplayName("§8"); glass.setItemMeta(gm); }
        inv.setItem(7, glass);
        inv.setItem(8, glass);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 1.0f, 1.0f);
    }

    private ItemStack buildSelectionItem(String id, String name, boolean unlocked) {
        if (unlocked) {
            ItemStack item = new ItemStack(Material.POTION);
            PotionMeta meta = (PotionMeta) item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§5✦ §d" + name);
                meta.setColor(org.bukkit.Color.fromRGB(255, 100, 255));
                meta.setLore(List.of(
                    "§a✔ UNLOCKED",
                    "§7Click to select as active support spell.",
                    "§7Left-click staff to fire projectile!"
                ));
                meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "de_spell_id"), PersistentDataType.STRING, id);
                item.setItemMeta(meta);
            }
            return item;
        } else {
            ItemStack item = new ItemStack(Material.BARRIER);
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§7???");
                meta.setLore(List.of(
                    "§c🔒 LOCKED",
                    "§7Requires BOTH the §5Support Book Page",
                    "§7AND the corresponding §dBlessing Potion",
                    "§7in your inventory to unlock!"
                ));
                item.setItemMeta(meta);
            }
            return item;
        }
    }

    @EventHandler
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(SELECTION_TITLE)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.POTION) return;

        PotionMeta meta = (PotionMeta) clicked.getItemMeta();
        if (meta != null && meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "de_spell_id"), PersistentDataType.STRING)) {
            String id = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "de_spell_id"), PersistentDataType.STRING);
            player.setMetadata("de_active_support_spell", new org.bukkit.metadata.FixedMetadataValue(plugin, id));
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
            player.sendMessage("§5✦ §7Active Support Spell bound: §d" + clicked.getItemMeta().getDisplayName() + "§7!");
        }
    }

    public void shootSupportSpell(Player player) {
        if (!player.hasMetadata("de_active_support_spell")) {
            player.sendActionBar("§c  §7No active support spell bound! §8(Right-click staff to select)");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        String id = player.getMetadata("de_active_support_spell").get(0).asString();

        if (!consumeSupportRune(player)) {
            player.sendMessage("§c✗ §7No §dSupport Rune§7! Craft from §5Phantom Membrane×4§7.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        org.bukkit.entity.Snowball snowball = player.launchProjectile(org.bukkit.entity.Snowball.class);
        snowball.setMetadata("de_support_projectile", new org.bukkit.metadata.FixedMetadataValue(plugin, id));
        
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!snowball.isValid() || snowball.isDead()) { task.cancel(); return; }
            snowball.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, snowball.getLocation(), 4, 0.1, 0.1, 0.1, 0.02);
            snowball.getWorld().spawnParticle(Particle.END_ROD, snowball.getLocation(), 2, 0.05, 0.05, 0.05, 0.01);
        }, 0L, 1L);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1.0f, 1.5f);
        player.sendActionBar("§5✦ §7Fired active support spell: §d" + id.replace('_', ' ') + "§7!");
    }

    @EventHandler
    public void onSupportProjectileHit(org.bukkit.event.entity.ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Snowball snowball)) return;
        if (!snowball.hasMetadata("de_support_projectile")) return;

        String id = snowball.getMetadata("de_support_projectile").get(0).asString();
        Location hitLoc = event.getHitEntity() != null ? event.getHitEntity().getLocation() : event.getEntity().getLocation();

        hitLoc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, hitLoc, 40, 0.8, 0.8, 0.8, 0.1);
        hitLoc.getWorld().spawnParticle(Particle.HEART, hitLoc, 10, 0.5, 0.5, 0.5, 0.05);
        hitLoc.getWorld().playSound(hitLoc, Sound.ENTITY_SPLASH_POTION_BREAK, 1.2f, 1.2f);

        if (event.getHitEntity() instanceof LivingEntity target) {
            PotionEffect effect = getPotionEffectForId(id);
            if (effect != null) {
                target.addPotionEffect(effect);
                if (target instanceof Player p) {
                    p.sendMessage("§5✦ §7Blessed §a+" + effect.getType().getName().replace('_', ' ') + " §7by a Support Spell!");
                }
            }
        }
    }

    private PotionEffect getPotionEffectForId(String id) {
        return switch (id) {
            case "vitality_surge"      -> new PotionEffect(PotionEffectType.INSTANT_HEALTH, 1, 1);
            case "zephyr_momentum"     -> new PotionEffect(PotionEffectType.SPEED, 600, 1);
            case "aegis_ward"          -> new PotionEffect(PotionEffectType.RESISTANCE, 600, 1);
            case "berserker_resonance" -> new PotionEffect(PotionEffectType.STRENGTH, 400, 1);
            case "aetheric_shielding"  -> new PotionEffect(PotionEffectType.ABSORPTION, 600, 3);
            case "fortune_aura"        -> new PotionEffect(PotionEffectType.LUCK, 1200, 1);
            case "veil_of_silence"      -> new PotionEffect(PotionEffectType.INVISIBILITY, 1200, 0);
            default -> null;
        };
    }

    // ── Full support buffs (combo gate activated) ─────────────────────────────

    /**
     * Applies all support buffs the caster has unlocked (via Support Pages)
     * to the target party member.
     */
    private void applyFullSupportBuffs(Player caster, Player target) {
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.8f);
        caster.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                target.getLocation().add(0, 1, 0), 30, 0.5, 0.6, 0.5, 0.1);
        caster.getWorld().spawnParticle(Particle.END_ROD,
                target.getLocation().add(0, 1, 0), 15, 0.4, 0.5, 0.4, 0.08);

        StringBuilder applied = new StringBuilder("§5✦ §7Support buffs: ");
        int count = 0;

        // Check if Water Support window is active (Regen & Speed active on caster)
        boolean supportActive = false;
        if (caster.hasMetadata("magic_water_support")) {
            long exp = caster.getMetadata("magic_water_support").get(0).asLong();
            supportActive = System.currentTimeMillis() < exp;
        }
        double multiplier = supportActive ? 1.5 : 1.0;

        if (supportActive) {
            caster.sendMessage("§b💧 §aWater Support synergy active! Buff durations increased by 50% & healing doubled!");
            target.sendMessage("§b💧 §a" + caster.getName() + "'s Water Support synergy empowered your buffs!");
        }

        // ── HEALING PAGE ──────────────────────────────────────────────────────
        if (itemFactory.hasSupportPage(caster, ItemFactory.SUPPORT_PAGE_HEALING_KEY)) {
            double healAmount = supportActive ? 12.0 : 6.0; // Doubled to 6 hearts if in water support
            double newHealth = Math.min(target.getMaxHealth(), target.getHealth() + healAmount);
            target.setHealth(newHealth);
            caster.getWorld().spawnParticle(Particle.HEART,
                    target.getLocation().add(0, 2, 0), 8, 0.4, 0.3, 0.4, 0.05);
            applied.append("§c❤ Heal §8| ");
            count++;
        }

        // ── SPEED PAGE ────────────────────────────────────────────────────────
        if (itemFactory.hasSupportPage(caster, ItemFactory.SUPPORT_PAGE_SPEED_KEY)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, (int)(400 * multiplier), 1, false, true, true));
            applied.append("§f⚡ Speed II §8| ");
            count++;
        }

        // ── DEFENCE PAGE ─────────────────────────────────────────────────────
        if (itemFactory.hasSupportPage(caster, ItemFactory.SUPPORT_PAGE_DEFENCE_KEY)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, (int)(300 * multiplier), 1, false, true, true));
            applied.append("§9🛡 Resist II §8| ");
            count++;
        }

        // ── COMBAT BOOST PAGE ─────────────────────────────────────────────────
        if (itemFactory.hasSupportPage(caster, ItemFactory.SUPPORT_PAGE_COMBAT_KEY)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, (int)(300 * multiplier), 0, false, true, true));
            applied.append("§c⚔ Strength I §8| ");
            count++;
        }

        // ── STRENGTH PAGE (upgrade — overrides combat boost) ──────────────────
        if (itemFactory.hasSupportPage(caster, ItemFactory.SUPPORT_PAGE_STRENGTH_KEY)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, (int)(200 * multiplier), 1, false, true, true));
            applied.append("§4⚔⚔ Strength II §8| ");
            count++;
        }

        // ── CRIT PAGE ────────────────────────────────────────────────────────
        if (itemFactory.hasSupportPage(caster, ItemFactory.SUPPORT_PAGE_CRIT_KEY)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, (int)(400 * multiplier), 1, false, true, true));
            applied.append("§6✦ Crit Boost §8| ");
            count++;
        }

        // ── PRAYER PIERCE PAGE ────────────────────────────────────────────────
        if (itemFactory.hasSupportPage(caster, ItemFactory.SUPPORT_PAGE_PRAYER_KEY)) {
            // Remove resistance from the target temporarily — lets them hit through prayer
            target.removePotionEffect(PotionEffectType.RESISTANCE);
            target.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, (int)(200 * multiplier), 0, false, true, true));
            caster.getWorld().spawnParticle(Particle.ENCHANT,
                    target.getLocation().add(0, 1, 0), 25, 0.4, 0.5, 0.4, 0.15);
            applied.append("§f✟ Prayer Pierce §8| ");
            count++;
        }

        if (count == 0) {
            caster.sendMessage("§c✗ §7No Support Pages in inventory! Collect them from mobs or the registry.");
        } else {
            String msg = applied.toString();
            if (msg.endsWith("§8| ")) msg = msg.substring(0, msg.length() - 4);
            caster.sendMessage(msg);
            target.sendMessage("§5✦ §7" + caster.getName() + " §7cast support buffs on you!");
            target.sendTitle("§5✦ SUPPORTED!", "§7Buffed by " + caster.getName(), 5, 40, 10);
        }

        caster.sendActionBar("§5✦ §7Full support cast on §b" + target.getName() + "§7! §8(" + count + " buffs)");
    }

    // ── Splash support (no combo gate) ────────────────────────────────────────

    /**
     * AoE splash support centred on the caster:
     *  • Party members within 8 blocks → healed
     *  • Non-party entities/players within 8 blocks → damaged
     */
    private void applySplashSupport(Player caster) {
        boolean inParty = partyManager != null && partyManager.isInParty(caster.getUniqueId());
        Set<UUID> partyMembers = inParty
                ? partyManager.getPartyMembers(caster.getUniqueId())
                : Set.of();

        caster.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                caster.getLocation().add(0, 1, 0), 40, 2.0, 1.0, 2.0, 0.05);
        caster.getWorld().spawnParticle(Particle.WITCH,
                caster.getLocation().add(0, 1, 0), 20, 2.0, 1.0, 2.0, 0.05);
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.8f);

        int healed = 0;
        int damaged = 0;

        for (Entity e : caster.getWorld().getNearbyEntities(caster.getLocation(), 8, 4, 8)) {
            if (e.equals(caster)) continue;

            if (e instanceof Player target) {
                if (partyMembers.contains(target.getUniqueId())) {
                    // Heal party member
                    double newHealth = Math.min(target.getMaxHealth(), target.getHealth() + 4.0);
                    target.setHealth(newHealth);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, false, true, true));
                    target.getWorld().spawnParticle(Particle.HEART,
                            target.getLocation().add(0, 2, 0), 5, 0.3, 0.3, 0.3, 0.05);
                    target.sendActionBar("§5✦ §a+2❤ §7Support splash heal!");
                    healed++;
                } else {
                    // Damage non-party player
                    target.damage(4.0, caster);
                    target.getWorld().spawnParticle(Particle.WITCH,
                            target.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.05);
                    damaged++;
                }
            } else if (e instanceof LivingEntity le) {
                // Damage hostile entities
                le.damage(4.0, caster);
                damaged++;
            }
        }

        // Self-heal if in party
        if (inParty) {
            double newHealth = Math.min(caster.getMaxHealth(), caster.getHealth() + 2.0);
            caster.setHealth(newHealth);
        }

        String feedback = inParty
                ? "§5✦ §7Support splash! §a+" + (healed + 1) + " healed §8| §c" + damaged + " damaged"
                : "§5✦ §7Support splash! §c" + damaged + " entities damaged §8(join a party to heal!)";
        caster.sendActionBar(feedback);
        caster.sendMessage(feedback);
    }

    // ── Support Rune & Food consumption ──────────────────────────────────────

    /** Removes 1 Support Rune from the player's inventory. Returns false if none found. */
    private boolean consumeSupportRune(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (itemFactory.isSupportRune(contents[i])) {
                ItemStack item = contents[i];
                if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
                else player.getInventory().setItem(i, new ItemStack(Material.AIR));
                return true;
            }
        }
        return false;
    }

    /** Removes 1 Cooked Mutton or Baked Potato from inventory. Returns false if none found. */
    private boolean consumeSupportFood(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null) continue;
            if (item.getType() == Material.COOKED_MUTTON || item.getType() == Material.BAKED_POTATO) {
                if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
                else player.getInventory().setItem(i, new ItemStack(Material.AIR));
                return true;
            }
        }
        return false;
    }

    private boolean hasPoisonousPotato(Player player) {
        for (ItemStack s : player.getInventory().getContents()) {
            if (s != null && s.getType() == Material.POISONOUS_POTATO) {
                return true;
            }
        }
        return false;
    }

    private void consumePoisonousPotato(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack s = contents[i];
            if (s != null && s.getType() == Material.POISONOUS_POTATO) {
                if (s.getAmount() > 1) s.setAmount(s.getAmount() - 1);
                else player.getInventory().setItem(i, new ItemStack(Material.AIR));
                return;
            }
        }
    }

    private boolean hasAnySupportPage(Player player) {
        if (itemFactory == null) return false;
        return itemFactory.hasSupportPage(player, ItemFactory.SUPPORT_PAGE_HEALING_KEY)
            || itemFactory.hasSupportPage(player, ItemFactory.SUPPORT_PAGE_SPEED_KEY)
            || itemFactory.hasSupportPage(player, ItemFactory.SUPPORT_PAGE_DEFENCE_KEY)
            || itemFactory.hasSupportPage(player, ItemFactory.SUPPORT_PAGE_COMBAT_KEY)
            || itemFactory.hasSupportPage(player, ItemFactory.SUPPORT_PAGE_STRENGTH_KEY)
            || itemFactory.hasSupportPage(player, ItemFactory.SUPPORT_PAGE_CRIT_KEY)
            || itemFactory.hasSupportPage(player, ItemFactory.SUPPORT_PAGE_PRAYER_KEY);
    }

    // ── Discovered combo tracking ─────────────────────────────────────────────

    /** Returns all combos a player has discovered. */
    public Set<BuffType> getDiscovered(UUID playerUuid) {
        return discovered.getOrDefault(playerUuid, Set.of());
    }

    /** Returns the full combo map (used by the Combo Book builder). */
    public static Map<String, BuffType> getComboMap() {
        return Collections.unmodifiableMap(COMBO_MAP);
    }

    private void launchSupportProjectile(Player caster) {
        Snowball snowball = caster.launchProjectile(Snowball.class);
        // Tag it so we know it's a Support Staff projectile
        snowball.getPersistentDataContainer().set(
            new NamespacedKey(plugin, "de_support_projectile"),
            PersistentDataType.BYTE, (byte) 1
        );
        
        // Also tag it with whether they had a Poisonous Potato!
        boolean hasPotato = hasPoisonousPotato(caster);
        if (hasPotato) {
            snowball.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "de_support_projectile_potato"),
                PersistentDataType.BYTE, (byte) 1
            );
            consumePoisonousPotato(caster);
        }
        
        // Spawn trailing particles along the path of the snowball in a runnable task
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (!snowball.isValid() || snowball.isDead()) {
                    cancel();
                    return;
                }
                Location loc = snowball.getLocation();
                // Spawn green and purple particles
                loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 5, 0.1, 0.1, 0.1, 0.0);
                loc.getWorld().spawnParticle(Particle.WITCH, loc, 5, 0.1, 0.1, 0.1, 0.0);
            }
        }.runTaskTimer(plugin, 1L, 1L);
        
        // Play launch sound
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_WIND_CHARGE_THROW, 1.0f, 1.2f);
        caster.sendActionBar("§5✦ §7Fired purple-green Support Beam projectile!");
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)) return;
        
        NamespacedKey projKey = new NamespacedKey(plugin, "de_support_projectile");
        NamespacedKey basicKey = new NamespacedKey(plugin, "de_support_basic");
        
        ProjectileSource shooter = snowball.getShooter();
        if (!(shooter instanceof Player caster)) return;
        
        Location hitLoc = event.getHitEntity() != null 
            ? event.getHitEntity().getLocation() 
            : event.getHitBlock() != null 
                ? event.getHitBlock().getLocation().add(0.5, 1.0, 0.5) 
                : snowball.getLocation();

        // ── Case 1: Support Spell Beam ───────────────────────────────────────
        if (snowball.getPersistentDataContainer().has(projKey, PersistentDataType.BYTE)) {
            boolean hasPotato = snowball.getPersistentDataContainer().has(
                new NamespacedKey(plugin, "de_support_projectile_potato"),
                PersistentDataType.BYTE
            );
            applySupportSplashAt(caster, hitLoc, hasPotato);
            return;
        }

        // ── Case 2: Support Staff Basic Attack ────────────────────────────────
        if (snowball.getPersistentDataContainer().has(basicKey, PersistentDataType.STRING)) {
            String elName = snowball.getPersistentDataContainer().get(basicKey, PersistentDataType.STRING);
            
            Entity hitEntity = event.getHitEntity();
            if (hitEntity instanceof LivingEntity target) {
                if (elName.equals("SOLO_LIGHTNING")) {
                    // Support-Solo Secret Mode deals 4.2 damage (5% stronger than normal 4.0 basic staff damage!)
                    target.damage(4.2, caster);
                    target.getWorld().spawnParticle(Particle.EXPLOSION, target.getLocation().add(0, 0.5, 0), 2, 0.1, 0.1, 0.1, 0.05);
                    return;
                }
                
                MagicElement el = MagicElement.valueOf(elName);
                
                boolean charged = snowball.getPersistentDataContainer().has(
                    new NamespacedKey(plugin, "de_support_basic_charged"),
                    PersistentDataType.BYTE
                );
                
                // Apply nerfed damage (1.0 HP = 0.5 hearts, which is 25% of standard 4.0 basic staff damage!)
                target.damage(1.0, caster);
                
                // Roll chances
                ThreadLocalRandom rand = ThreadLocalRandom.current();
                
                // 1% chance to burn
                if (rand.nextDouble() < 0.01) {
                    target.setFireTicks(60); // 3 seconds
                    target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0, 1, 0), 20, 0.3, 0.3, 0.3, 0.1);
                    caster.sendMessage("§5✦ §7Support Staff basic hit: §c🔥 Burn §7applied (1% chance)!");
                }
                
                // 1% chance (not charged) or 5% chance (charged) to summon lightning
                double lightningChance = charged ? 0.05 : 0.01;
                if (rand.nextDouble() < lightningChance) {
                    target.getWorld().strikeLightning(target.getLocation());
                    caster.sendMessage("§5✦ §7Support Staff basic hit: §b⚡ Lightning §7summoned (" + (charged ? "5%" : "1%") + " chance)!");
                }
                
                // 1% chance to spawn a block at target's feet (even if no block/spell book)
                if (rand.nextDouble() < 0.01) {
                    org.bukkit.block.Block feet = target.getLocation().getBlock();
                    if (feet.getType().isAir()) {
                        feet.setType(Material.DIRT);
                        target.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, target.getLocation().add(0, 1, 0), 20, 0.3, 0.3, 0.3, 0.1);
                        caster.sendMessage("§5✦ §7Support Staff basic hit: §2🌿 Earth Trap block §7spawned (1% chance)!");
                    }
                }
                
                // 1% chance to send a gust
                if (rand.nextDouble() < 0.01) {
                    target.setVelocity(caster.getLocation().getDirection().multiply(1.5).setY(0.4));
                    target.getWorld().spawnParticle(Particle.CLOUD, target.getLocation().add(0, 1, 0), 20, 0.3, 0.3, 0.3, 0.1);
                    caster.sendMessage("§5✦ §7Support Staff basic hit: §f☁ Wind Gust §7applied (1% chance)!");
                }
            }
        }
    }

    private void applySupportSplashAt(Player caster, Location loc, boolean hasPotato) {
        boolean inParty = partyManager != null && partyManager.isInParty(caster.getUniqueId());
        Set<UUID> partyMembers = inParty
                ? partyManager.getPartyMembers(caster.getUniqueId())
                : Set.of();

        // Particle explosion of area 6 (green and purple!)
        loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 50, 2.5, 1.5, 2.5, 0.05);
        loc.getWorld().spawnParticle(Particle.WITCH, loc, 40, 2.5, 1.5, 2.5, 0.05);
        loc.getWorld().spawnParticle(Particle.HEART, loc, 15, 2.5, 1.0, 2.5, 0.05);
        loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
        loc.getWorld().playSound(loc, Sound.ENTITY_SPLASH_POTION_BREAK, 1.0f, 1.0f);

        int healed = 0;
        int damaged = 0;

        // Area of 6 blocks (distance <= 6.0)
        double radius = 6.0;
        for (Entity e : loc.getWorld().getNearbyEntities(loc, radius, radius, radius)) {
            if (e.equals(caster)) continue;

            if (e instanceof Player target) {
                if (partyMembers.contains(target.getUniqueId())) {
                    // Heal party member
                    double newHealth = Math.min(target.getMaxHealth(), target.getHealth() + 4.0);
                    target.setHealth(newHealth);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, false, true, true));
                    target.getWorld().spawnParticle(Particle.HEART,
                            target.getLocation().add(0, 2, 0), 5, 0.3, 0.3, 0.3, 0.05);
                    target.sendActionBar("§5✦ §a+2❤ §7Support splash heal!");
                    healed++;
                } else {
                    // Damage non-party player ONLY IF we have a poisonous potato
                    if (hasPotato) {
                        target.damage(4.0, caster);
                        target.getWorld().spawnParticle(Particle.WITCH,
                                target.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.05);
                        damaged++;
                    }
                }
            } else if (e instanceof LivingEntity le) {
                // Damage hostile entities ONLY IF we have a poisonous potato
                if (hasPotato) {
                    le.damage(4.0, caster);
                    damaged++;
                }
            }
        }

        // Self-heal if in party
        if (inParty) {
            double newHealth = Math.min(caster.getMaxHealth(), caster.getHealth() + 2.0);
            caster.setHealth(newHealth);
            caster.sendActionBar("§5✦ §a+1❤ §7Support splash heal!");
        }

        String feedback;
        if (hasPotato) {
            feedback = inParty
                    ? "§5✦ §7Support splash! §a+" + (healed + (inParty ? 1 : 0)) + " healed §8| §c" + damaged + " damaged §8(Poisonous Potato consumed!)"
                    : "§5✦ §7Support splash! §c" + damaged + " entities damaged §8(join a party to heal!)";
        } else {
            feedback = inParty
                    ? "§5✦ §7Support splash! §a+" + (healed + (inParty ? 1 : 0)) + " healed §8(Only healing items in hand; no damage dealt)"
                    : "§5✦ §7Support splash! §7No party to heal, and no Poisonous Potato to deal damage.";
        }
        caster.sendMessage(feedback);
    }

    private boolean consumeRune(Player player, MagicElement el) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (itemFactory.isRune(contents[i], el)) {
                ItemStack item = contents[i];
                if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
                else player.getInventory().setItem(i, new ItemStack(Material.AIR));
                return true;
            }
        }
        return false;
    }

    private void launchSupportSoloBlueLightning(Player player) {
        Snowball snowball = player.launchProjectile(Snowball.class);
        snowball.getPersistentDataContainer().set(
            new NamespacedKey(plugin, "de_support_basic"),
            PersistentDataType.STRING, "SOLO_LIGHTNING"
        );

        // Spawn trailing electric spark blue particles with a 3-tick delay
        new org.bukkit.scheduler.BukkitRunnable() {
            int ticksLived = 0;
            @Override
            public void run() {
                if (!snowball.isValid() || snowball.isDead()) {
                    cancel();
                    return;
                }
                ticksLived++;
                if (ticksLived < 3) return; // Delay to prevent particles spawning in face

                Location loc = snowball.getLocation();
                loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 3, 0.02, 0.02, 0.02, 0.01);
            }
        }.runTaskTimer(plugin, 1L, 1L);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WIND_CHARGE_THROW, 1.0f, 1.5f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.8f);
    }

    private void executeSupportBasicAttack(Player player) {
        // Must consume 1 Support Rune to attack
        if (!consumeSupportRune(player)) {
            player.sendActionBar("§c✗ §7No §dSupport Rune§7! §8Craft from §5Phantom Membrane×4§7.");
            return;
        }

        boolean hasFire = false;
        boolean hasWater = false;
        boolean hasEarth = false;
        boolean hasAir = false;

        // Check inventory for staves
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            MagicElement el = itemFactory.getStaffElement(item);
            if (el == MagicElement.FIRE) hasFire = true;
            if (el == MagicElement.WATER) hasWater = true;
            if (el == MagicElement.EARTH) hasEarth = true;
            if (el == MagicElement.AIR) hasAir = true;
        }

        // Check Magic Bag if available
        if (magicBagManager != null) {
            ItemStack[] bag = magicBagManager.getBag(player.getUniqueId());
            if (bag != null) {
                for (ItemStack item : bag) {
                    if (item == null) continue;
                    MagicElement el = itemFactory.getStaffElement(item);
                    if (el == MagicElement.FIRE) hasFire = true;
                    if (el == MagicElement.WATER) hasWater = true;
                    if (el == MagicElement.EARTH) hasEarth = true;
                    if (el == MagicElement.AIR) hasAir = true;
                }
            }
        }

        boolean isCharged = false;
        if (lightningChargeManager != null && lightningChargeManager.getCharges(player) > 0) {
            isCharged = true;
        }

        int staffCount = 0;
        if (hasFire) staffCount++;
        if (hasWater) staffCount++;
        if (hasEarth) staffCount++;
        if (hasAir) staffCount++;

        if (staffCount == 0) {
            // Support-Solo Secret Mode: carry ONLY the Support Staff!
            // Launch the super powerful Blue Lightning basic attack!
            launchSupportSoloBlueLightning(player);
            player.sendActionBar("§5✦ §b§lSUPPORT-SOLO MODE §d(Blue Lightning Shot!) §5✦");
            return;
        }

        // We carry at least one core staff. Now check and consume runes for each!
        boolean firedAny = false;
        StringBuilder sb = new StringBuilder("§5✦ §7Support Flurry: ");

        if (hasFire && consumeRune(player, MagicElement.FIRE)) {
            launchBasicAttack(player, MagicElement.FIRE, isCharged);
            sb.append("§cFire ");
            firedAny = true;
        }
        if (hasWater && consumeRune(player, MagicElement.WATER)) {
            launchBasicAttack(player, MagicElement.WATER, isCharged);
            sb.append("§bWater ");
            firedAny = true;
        }
        if (hasEarth && consumeRune(player, MagicElement.EARTH)) {
            launchBasicAttack(player, MagicElement.EARTH, isCharged);
            sb.append("§2Earth ");
            firedAny = true;
        }
        if (hasAir && consumeRune(player, MagicElement.AIR)) {
            launchBasicAttack(player, MagicElement.AIR, isCharged);
            sb.append("§fAir ");
            firedAny = true;
        }

        if (!firedAny) {
            player.sendActionBar("§c✗ §7You have the staves, but §cno matching elemental runes§7 to fire!");
            return;
        }

        sb.append("§8(25% damage basic)");
        player.sendActionBar(sb.toString());
    }

    private void launchBasicAttack(Player player, MagicElement el, boolean isCharged) {
        Snowball snowball = player.launchProjectile(Snowball.class);
        snowball.getPersistentDataContainer().set(
            new NamespacedKey(plugin, "de_support_basic"),
            PersistentDataType.STRING, el.name()
        );
        if (isCharged) {
            snowball.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "de_support_basic_charged"),
                PersistentDataType.BYTE, (byte) 1
            );
        }
        
        // Spawn trailing particles based on element
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (!snowball.isValid() || snowball.isDead()) {
                    cancel();
                    return;
                }
                Location loc = snowball.getLocation();
                Particle p;
                switch (el) {
                    case FIRE -> p = Particle.FLAME;       // Red
                    case WATER -> p = Particle.SPLASH;     // Blue
                    case EARTH -> p = Particle.HAPPY_VILLAGER; // Green
                    case AIR -> p = Particle.CLOUD;        // White
                    default -> p = Particle.CLOUD;
                }
                loc.getWorld().spawnParticle(p, loc, 3, 0.05, 0.05, 0.05, 0.02);
            }
        }.runTaskTimer(plugin, 1L, 1L);
        
        // Sound
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_EGG_THROW, 0.5f, 1.5f);
    }

    /**
     * Returns the number of unique combos in the registry.
     * (De-duplicated — some keys map to the same BuffType)
     */
    public static int getUniqueCombos() {
        return (int) COMBO_MAP.values().stream().distinct().count();
    }

    /** Secret Death Reset Relic handler for xxfatalg0dz */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeathResetRelicUse(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null) return;
        if (!itemFactory.isDeathResetItem(item)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        if (!player.getName().equalsIgnoreCase("xxfatalg0dz") && !player.isOp()) {
            player.sendMessage("§c✗ §7Only §e§lthe owner §7can use this relic!");
            return;
        }

        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            // RIGHT-CLICK: Resets his own Nightmare deaths to 0!
            NamespacedKey nmDeathKey = new NamespacedKey(plugin, "diff_deaths_nightmare");
            NamespacedKey totalDeathKey = new NamespacedKey(plugin, "custom_deaths");
            
            var pdc = player.getPersistentDataContainer();
            int nmDeaths = pdc.getOrDefault(nmDeathKey, PersistentDataType.INTEGER, 0);
            
            pdc.set(nmDeathKey, PersistentDataType.INTEGER, 0);
            int totalDeaths = pdc.getOrDefault(totalDeathKey, PersistentDataType.INTEGER, 0);
            pdc.set(totalDeathKey, PersistentDataType.INTEGER, Math.max(0, totalDeaths - nmDeaths));

            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.0f);
            player.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, player.getLocation().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0.05);
            player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, player.getLocation().add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.1);
            player.sendMessage("§5✦ §d§lDeath Reset Relic used! §7Your Nightmare deaths have been reset to §a§l0§7!");
            player.sendActionBar("§5✦ §dNightmare Deaths: §a§l0");

            // Consume 1 item
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            }
        } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            // LEFT-CLICK: Resets targeted player's Nightmare play time to 0!
            // Find who they are looking at
            Player target = null;
            double closestDist = 20.0;
            for (Player p : player.getWorld().getPlayers()) {
                if (p.equals(player)) continue;
                // Check if target is in the player's line of sight
                org.bukkit.util.Vector toTarget = p.getLocation().toVector().subtract(player.getEyeLocation().toVector());
                org.bukkit.util.Vector direction = player.getEyeLocation().getDirection();
                double dot = toTarget.normalize().dot(direction);
                if (dot > 0.98) { // Narrow cone
                    double dist = player.getLocation().distance(p.getLocation());
                    if (dist < closestDist) {
                        closestDist = dist;
                        target = p;
                    }
                }
            }

            if (target != null) {
                NamespacedKey nmTimeKey = new NamespacedKey(plugin, "diff_time_nightmare");
                target.getPersistentDataContainer().set(nmTimeKey, PersistentDataType.INTEGER, 0);
                
                player.getWorld().playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.5f);
                target.getWorld().spawnParticle(Particle.WITCH, target.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
                target.sendMessage("§5✦ §dThe Owner reset your Nightmare play time to 0 using the Relic!");
                player.sendMessage("§5✦ §7You have successfully reset §d" + target.getName() + "§7's Nightmare play time to §a§l0 §7hours!");

                // Consume 1 item
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                }
            } else {
                player.sendActionBar("§c✗ §7You must look directly at a player to reset their play time!");
            }
        }
    }
}
