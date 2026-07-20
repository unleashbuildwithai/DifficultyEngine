package com.yourname.difficulty.skills;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

/**
 * CapeEquipListener — Enforces level requirements for skill capes.
 *
 * Rules:
 *   • Admin (difficultyengine.cape.admin) → always allowed + grants Level 99
 *   • Player with Level 99 in the skill   → allowed to wear the cape
 *   • Anyone else                         → equip is CANCELLED with an error message
 *
 * Max Cape requirement: ALL skills must be Level 99 (or admin bypass).
 *
 * Detection covers two equip paths:
 *   1. Direct drag/click into the chestplate slot (slot 38)
 *   2. Shift-click on a cape item anywhere in the player inventory
 */
public class CapeEquipListener implements Listener {

    /** Slot index in the player's inventory that corresponds to the chestplate. */
    private static final int CHESTPLATE_SLOT = 38;

    /** PDC key stored on the Player entity — records which axolotl variant they rolled. */
    private static final String AXOLOTL_VARIANT_KEY = "de_cape_axolotl_variant";

    private static final Random RAND = new Random();

    private final SkillManager     skillManager;
    private final SkillCapeManager capeManager;
    private final JavaPlugin        plugin;
    private final NamespacedKey     axolotlVariantNsk;

    public CapeEquipListener(SkillManager skillManager, SkillCapeManager capeManager, JavaPlugin plugin) {
        this.skillManager      = skillManager;
        this.capeManager       = capeManager;
        this.plugin            = plugin;
        this.axolotlVariantNsk = new NamespacedKey(plugin, AXOLOTL_VARIANT_KEY);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;
        if (!(event.getClickedInventory() instanceof PlayerInventory)) return;

        ItemStack cursor  = event.getCursor();
        ItemStack clicked = event.getCurrentItem();

        ItemStack capeCandidate = null;

        // Case 1: Direct place into chestplate slot
        if (event.getSlot() == CHESTPLATE_SLOT
                && cursor != null && !cursor.getType().isAir()) {
            capeCandidate = cursor;
        }

        // Case 2: Shift-click a cape item
        if (event.isShiftClick()
                && clicked != null && !clicked.getType().isAir()
                && capeManager.isAnyCape(clicked)) {
            capeCandidate = clicked;
        }

        if (capeCandidate == null || !capeManager.isAnyCape(capeCandidate)) return;

        // ── Admin bypass ──────────────────────────────────────────────────────
        if (player.hasPermission("difficultyengine.cape.admin")) {
            applyAdminPerk(player, capeCandidate);
            return; // allow equip
        }

        // ── Level check ───────────────────────────────────────────────────────
        if (!meetsRequirement(player, capeCandidate)) {
            event.setCancelled(true);
            sendRequirementMessage(player, capeCandidate);
        }
        // ── Roll axolotl variant on any Lv99 skill cape equip ────────────────
        // Only roll if the player doesn't already have one stored.
        if (!capeManager.isBossCape(capeCandidate) && !capeManager.isMaxCape(capeCandidate)) {
            SkillType skill = capeManager.getCapeSkill(capeCandidate);
            if (skill != null) {
                rollAxolotlVariant(player);
            }
        }

        // If requirement met, allow equip silently (no admin perk granted)
    }

    // ── Requirement check ─────────────────────────────────────────────────────

    private boolean meetsRequirement(Player player, ItemStack cape) {
        // Boss Cape: earned via gameplay — no level requirement
        if (capeManager.isBossCape(cape)) return true;
        if (capeManager.isMaxCape(cape)) {
            // Max Cape: ALL skills must be 99
            return skillManager.isMaxed(player.getUniqueId());
        }
        SkillType skill = capeManager.getCapeSkill(cape);
        if (skill == null) return true; // unknown cape — don't block
        return skillManager.getLevel(player.getUniqueId(), skill) >= SkillLevel.MAX_LEVEL;
    }

    private void sendRequirementMessage(Player player, ItemStack cape) {
        if (capeManager.isBossCape(cape)) return; // no requirement, never shown
        if (capeManager.isMaxCape(cape)) {
            player.sendMessage("§c✗ §7You need §aLevel 99 §7in §fevery skill §7to wear the §5★ Max Cape§7.");
            player.sendMessage("§7Use §e/mystats §7to check your progress.");
        } else {
            SkillType skill = capeManager.getCapeSkill(cape);
            if (skill == null) return;
            int current = skillManager.getLevel(player.getUniqueId(), skill);
            player.sendMessage("§c✗ §7You need §aLevel 99 §7in §"
                    + skill.getColorCode().charAt(1)
                    + SkillCapeManager.symbol(skill) + " " + skill.getDisplayName()
                    + " §7to wear this cape.");
            player.sendMessage("  §7Your level: §e" + current + " §8/ §a99");
            player.sendMessage("  §7Use §e/mystats §7to view your skill progress.");
        }
    }

    // ── Axolotl variant rolling ────────────────────────────────────────────────

    /**
     * Rolls a random axolotl variant for the player if they don't already have one.
     * Stored permanently in the player's PDC so it survives restarts.
     * Variants: LEUCISTIC (white), WILD (brown), GOLD, CYAN, BLUE (rarest).
     */
    private void rollAxolotlVariant(Player player) {
        // Don't re-roll if already assigned
        if (player.getPersistentDataContainer().has(axolotlVariantNsk, PersistentDataType.STRING)) return;

        Axolotl.Variant[] variants = Axolotl.Variant.values();
        // Weight BLUE (rarest) at 1-in-20 chance; others equally weighted
        Axolotl.Variant chosen;
        if (RAND.nextInt(20) == 0) {
            chosen = Axolotl.Variant.BLUE;
        } else {
            Axolotl.Variant[] common = { Axolotl.Variant.LUCY, Axolotl.Variant.WILD,
                                          Axolotl.Variant.GOLD, Axolotl.Variant.CYAN };
            chosen = common[RAND.nextInt(common.length)];
        }

        player.getPersistentDataContainer().set(axolotlVariantNsk, PersistentDataType.STRING, chosen.name());

        String colour = switch (chosen) {
            case LUCY  -> "§fLeuci (White)";
            case WILD  -> "§6Wild (Brown)";
            case GOLD  -> "§eGold";
            case CYAN  -> "§bCyan";
            case BLUE  -> "§9§lBlue §8(★ Rare!)";
            default    -> chosen.name();
        };

        player.sendMessage("§d✦ §7Your cape bound a §d" + colour + "§7 axolotl variant!");
        player.sendMessage("  §8This variant will follow you as a companion when fishing.");
    }

    /** Returns the stored axolotl variant for a player, or LUCY as default. */
    public Axolotl.Variant getAxolotlVariant(Player player) {
        String stored = player.getPersistentDataContainer()
                .getOrDefault(axolotlVariantNsk, PersistentDataType.STRING, "LUCY");
        try {
            return Axolotl.Variant.valueOf(stored);
        } catch (IllegalArgumentException e) {
            return Axolotl.Variant.LUCY;
        }
    }

    // ── Admin perk ────────────────────────────────────────────────────────────

    /** Silently sets skill(s) to Level 99 — no announcement shown. */
    private void applyAdminPerk(Player player, ItemStack cape) {
        if (capeManager.isBossCape(cape)) return; // Boss cape has no skill to set
        if (capeManager.isMaxCape(cape)) {
            skillManager.setAllToMax(player.getUniqueId());
        } else {
            SkillType skill = capeManager.getCapeSkill(cape);
            if (skill != null) skillManager.setToMax(player.getUniqueId(), skill);
        }
    }
}
