package com.yourname.difficulty.listeners;

import com.yourname.difficulty.casting.ArmedSupportEffect;
import com.yourname.difficulty.casting.CastingEngine;
import com.yourname.difficulty.items.ItemFactory;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;

/**
 * SupportPotionListener — Handles drinking Support Blessing Potions.
 *
 * Arming rules (per Magic Rewrite spec): drinking a Blessing Potion arms it
 * for a later ranged Support Staff cast ONLY if ALL THREE of the following
 * are true:
 *   1. The player carries the master §5Support Book§7.
 *   2. The player carries the specific §5Support Page§7 matching this potion's id.
 *   3. The player carries at least one §dSupport Rune§7.
 * If any of the three is missing, the potion shatters (existing behaviour).
 *
 * On successful arm: the potion's effects are applied to the drinker AND
 * stored as an {@link ArmedSupportEffect} on {@link CastingEngine} for a
 * later ranged splash cast via the Support Staff's right-click
 * (see CastingEngine#onSupportStaffUse). Non-stacking: if the player already
 * has an unexpired armed effect, the new potion is wasted (still consumed,
 * still requires the rune present — but does NOT re-arm/replace).
 */
public class SupportPotionListener implements Listener {

    private final ItemFactory itemFactory;
    /** Optional CastingEngine — wired in via setter from Main after both exist. */
    private CastingEngine castingEngine;

    public SupportPotionListener(ItemFactory itemFactory) {
        this.itemFactory = itemFactory;
    }

    /** Wires in the CastingEngine so armed potions can be stored for the Support Staff cast. */
    public void setCastingEngine(CastingEngine castingEngine) {
        this.castingEngine = castingEngine;
    }

    private boolean isSupportPotion(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        org.bukkit.persistence.PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        for (NamespacedKey key : pdc.getKeys()) {
            if (key.getKey().startsWith("de_support_potion_")) {
                return true;
            }
        }
        return false;
    }

    /** Extracts the potion id (e.g. "vitality_surge") from the PDC key on the potion item. */
    private String extractPotionId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        org.bukkit.persistence.PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        for (NamespacedKey key : pdc.getKeys()) {
            if (key.getKey().startsWith("de_support_potion_")) {
                return key.getKey().substring("de_support_potion_".length());
            }
        }
        return null;
    }

    private boolean hasSupportRune(Player player) {
        for (ItemStack s : player.getInventory().getContents()) {
            if (itemFactory.isSupportRune(s)) {
                return true;
            }
        }
        return false;
    }

    private void consumeSupportRune(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (itemFactory.isSupportRune(contents[i])) {
                ItemStack item = contents[i];
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                    player.getInventory().setItem(i, item);
                } else {
                    player.getInventory().setItem(i, new ItemStack(Material.AIR));
                }
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPotionConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (!isSupportPotion(item)) return;

        Player player = event.getPlayer();
        String potionId = extractPotionId(item);

        // ── ALL THREE arming requirements must be true ────────────────────────
        boolean hasBook = itemFactory.hasSupportBook(player);
        boolean hasPage = potionId != null && itemFactory.hasSupportPageForPotion(player, potionId);
        boolean hasRune = hasSupportRune(player);

        if (hasBook && hasPage && hasRune) {
            // Cancel consumption so the potion is unlimited use!
            event.setCancelled(true);

            // Consume one Support Rune as the "unlimited cast cost"
            consumeSupportRune(player);

            // Manually apply potion effects since the consume event is cancelled
            PotionMeta meta = (PotionMeta) item.getItemMeta();
            List<PotionEffect> effects = new ArrayList<>();
            if (meta != null && meta.hasCustomEffects()) {
                for (PotionEffect effect : meta.getCustomEffects()) {
                    player.addPotionEffect(effect);
                    effects.add(effect);
                }
            }

            // ── Arm the effect for a ranged Support Staff cast (non-stacking) ──
            if (castingEngine != null) {
                ArmedSupportEffect existing = castingEngine.getArmedEffect(player.getUniqueId());
                if (existing == null) {
                    castingEngine.armEffect(player, effects, potionId);
                    player.sendMessage("§5✦ §7Blessing armed! §7Right-click your §dSupport Staff§7 to unleash it at range.");
                } else {
                    player.sendMessage("§c✗ §7You already have an armed Blessing! §7This potion's arming was wasted.");
                }
            }

            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1.0f, 1.0f);
            player.sendMessage("§5✦ §7Blessing applied successfully! §8(1× Support Rune consumed)");
        } else {
            // Missing Support Book, Support Page, or Support Rune -> Potion breaks and shatters!
            event.setCancelled(true);

            // Remove the potion from their hand (breaks it)
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            }

            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SPLASH_POTION_BREAK, 1.0f, 0.8f);
            player.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, player.getLocation().add(0, 1, 0), 20, 0.3, 0.3, 0.3, 0.05);

            StringBuilder missing = new StringBuilder();
            if (!hasBook) missing.append("§5Support Book§7, ");
            if (!hasPage) missing.append("§5matching Support Page§7, ");
            if (!hasRune) missing.append("§dSupport Rune§7, ");
            String missingStr = missing.length() > 0 ? missing.substring(0, missing.length() - 2) : "requirements";

            player.sendMessage("§c✗ §4SHATTERED! §7The blessing potion shattered — missing: " + missingStr + "§7!");
        }
    }
}
