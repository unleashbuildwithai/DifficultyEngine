package com.yourname.difficulty.casting;

import com.yourname.difficulty.items.ItemFactory;
import com.yourname.difficulty.magic.MagicElement;
import com.yourname.difficulty.party.PartyManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
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

        // Only show the queue HUD if the player has a Support Staff in their inventory.
        // Without a Support Staff the HUD is irrelevant and would just confuse players
        // who are using staffs for normal combat.
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
            player.sendActionBar(hud + " §8(right-click §dSupport Staff §8to activate)");
        }, 2L);
    }

    // ── Party member hit tracking (combo gate) ────────────────────────────────

    /**
     * When a player hits another player with the Support Staff (left-click),
     * and the target is in the same party, record it as the combo target.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPartyMemberHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity()  instanceof Player target))   return;

        // Must be holding support staff
        ItemStack hand = attacker.getInventory().getItemInMainHand();
        if (!isSupportStaff(hand)) return;

        // Must be in the same party
        if (partyManager == null) return;
        if (!partyManager.isInParty(attacker.getUniqueId())) return;
        Set<UUID> members = partyManager.getPartyMembers(attacker.getUniqueId());
        if (!members.contains(target.getUniqueId())) return;

        // Record combo gate
        lastPartyHitTarget.put(attacker.getUniqueId(), target.getUniqueId());
        lastPartyHitTime.put(attacker.getUniqueId(), System.currentTimeMillis());

        attacker.sendActionBar("§5✦ §7Combo loaded! Right-click §dSupport Staff §7within §a5s §7for full buffs on §b"
                + target.getName() + "§7!");
        attacker.getWorld().spawnParticle(Particle.ENCHANT,
                target.getLocation().add(0, 1, 0), 20, 0.4, 0.4, 0.4, 0.1);
    }

    // ── SupportStaff right-click ──────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSupportStaffUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (!isSupportStaff(item)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        // ── Consume Support Rune ──────────────────────────────────────────────
        if (!consumeSupportRune(player)) {
            player.sendMessage("§c✗ §7No §dSupport Rune§7! Craft from §5Phantom Membrane×4§7.");
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            return;
        }

        // ── Consume food (Cooked Mutton or Baked Potato) ──────────────────────
        if (!consumeSupportFood(player)) {
            // Refund the rune since food wasn't available
            player.getInventory().addItem(itemFactory.buildSupportRune());
            player.sendMessage("§c✗ §7Need §6Cooked Mutton §7or §eBaked Potato §7to cast support!");
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            return;
        }

        // ── Determine combo gate ──────────────────────────────────────────────
        long now = System.currentTimeMillis();
        UUID partyTargetId = lastPartyHitTarget.get(player.getUniqueId());
        long hitTime       = lastPartyHitTime.getOrDefault(player.getUniqueId(), 0L);
        boolean hasComboGate = (partyTargetId != null && (now - hitTime) <= PARTY_HIT_WINDOW_MS);

        if (hasComboGate) {
            // Clear gate immediately
            lastPartyHitTarget.remove(player.getUniqueId());
            lastPartyHitTime.remove(player.getUniqueId());

            Player target = plugin.getServer().getPlayer(partyTargetId);
            if (target != null && target.isOnline()) {
                applyFullSupportBuffs(player, target);
                return;
            }
        }

        // ── No combo gate → check for elemental combo first ──────────────────
        if (queueManager.hasQueue(player.getUniqueId())) {
            String key  = queueManager.queueKey(player.getUniqueId());
            BuffType buff = COMBO_MAP.get(key);

            if (buff != null) {
                queueManager.clearQueue(player.getUniqueId());
                buffLogic.apply(player, buff);
                discovered.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(buff);
                player.getWorld().playSound(player.getLocation(),
                        Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
                player.sendMessage("§5✦ §7Combo: " + buff.displayName() + " §7activated!");
                return;
            } else {
                player.sendMessage("§c✗ §7Unknown combo: §8[§7" + key.replace(',', '+') + "§8]");
                player.sendMessage("§7Check the §5Combo Book §7for valid recipes.");
                player.getWorld().playSound(player.getLocation(),
                        Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                queueManager.clearQueue(player.getUniqueId());
                // Fall through to splash healing
            }
        }

        // ── Splash mode: heal party members nearby, damage others ─────────────
        applySplashSupport(player);
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

        // ── HEALING PAGE ──────────────────────────────────────────────────────
        if (itemFactory.hasSupportPage(caster, ItemFactory.SUPPORT_PAGE_HEALING_KEY)) {
            double healAmount = 6.0;
            double newHealth = Math.min(target.getMaxHealth(), target.getHealth() + healAmount);
            target.setHealth(newHealth);
            caster.getWorld().spawnParticle(Particle.HEART,
                    target.getLocation().add(0, 2, 0), 8, 0.4, 0.3, 0.4, 0.05);
            applied.append("§c❤ Heal §8| ");
            count++;
        }

        // ── SPEED PAGE ────────────────────────────────────────────────────────
        if (itemFactory.hasSupportPage(caster, ItemFactory.SUPPORT_PAGE_SPEED_KEY)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 400, 1, false, true, true));
            applied.append("§f⚡ Speed II §8| ");
            count++;
        }

        // ── DEFENCE PAGE ─────────────────────────────────────────────────────
        if (itemFactory.hasSupportPage(caster, ItemFactory.SUPPORT_PAGE_DEFENCE_KEY)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 300, 1, false, true, true));
            applied.append("§9🛡 Resist II §8| ");
            count++;
        }

        // ── COMBAT BOOST PAGE ─────────────────────────────────────────────────
        if (itemFactory.hasSupportPage(caster, ItemFactory.SUPPORT_PAGE_COMBAT_KEY)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 300, 0, false, true, true));
            applied.append("§c⚔ Strength I §8| ");
            count++;
        }

        // ── STRENGTH PAGE (upgrade — overrides combat boost) ──────────────────
        if (itemFactory.hasSupportPage(caster, ItemFactory.SUPPORT_PAGE_STRENGTH_KEY)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 1, false, true, true));
            applied.append("§4⚔⚔ Strength II §8| ");
            count++;
        }

        // ── CRIT PAGE ────────────────────────────────────────────────────────
        if (itemFactory.hasSupportPage(caster, ItemFactory.SUPPORT_PAGE_CRIT_KEY)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 400, 1, false, true, true));
            applied.append("§6✦ Crit Boost §8| ");
            count++;
        }

        // ── PRAYER PIERCE PAGE ────────────────────────────────────────────────
        if (itemFactory.hasSupportPage(caster, ItemFactory.SUPPORT_PAGE_PRAYER_KEY)) {
            // Remove resistance from the target temporarily — lets them hit through prayer
            target.removePotionEffect(PotionEffectType.RESISTANCE);
            target.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 200, 0, false, true, true));
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

    // ── Discovered combo tracking ─────────────────────────────────────────────

    /** Returns all combos a player has discovered. */
    public Set<BuffType> getDiscovered(UUID playerUuid) {
        return discovered.getOrDefault(playerUuid, Set.of());
    }

    /** Returns the full combo map (used by the Combo Book builder). */
    public static Map<String, BuffType> getComboMap() {
        return Collections.unmodifiableMap(COMBO_MAP);
    }

    /**
     * Returns the number of unique combos in the registry.
     * (De-duplicated — some keys map to the same BuffType)
     */
    public static int getUniqueCombos() {
        return (int) COMBO_MAP.values().stream().distinct().count();
    }
}
