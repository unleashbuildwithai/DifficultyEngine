package com.yourname.difficulty.items;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

/**
 * DragonArmourHeartsTask — grants bonus max-HP for wearing Dragon-tier melee armour.
 *
 * ── Bonus ─────────────────────────────────────────────────────────────────────
 *  +2 hearts (4 HP) per Dragon-tier piece worn (helmet/chestplate/leggings/boots).
 *  Full 4-piece set bonus: +3 additional hearts (6 HP).
 *
 *  0 pieces  = +0 hearts
 *  1 piece   = +2 hearts  (4 HP)
 *  2 pieces  = +4 hearts  (8 HP)
 *  3 pieces  = +6 hearts  (12 HP)
 *  4 pieces  = +8 hearts (16 HP) + 3 set bonus hearts (6 HP) = +11 hearts total (22 HP)
 *
 * Implemented as an AttributeModifier on GENERIC_MAX_HEALTH, applied/refreshed every
 * 20 ticks (1s) for all online players — same technique as SkillBonusManager's
 * Defence HP bonus, kept in its own tick task since it depends on live armour state
 * rather than a stored skill level.
 */
public class DragonArmourHeartsTask extends BukkitRunnable {

    private static final UUID   DRAGON_HP_UUID = UUID.fromString("d4a90000-1234-4dee-9a1e-de9dea9ea9ea");
    private static final String DRAGON_HP_KEY  = "difficultyengine_dragon_armour_hp";
    private static final double HEARTS_PER_PIECE = 4.0; // 2 hearts = 4 HP
    private static final double FULL_SET_BONUS    = 6.0; // 3 extra hearts = 6 HP

    private final JavaPlugin  plugin;
    private final ItemFactory itemFactory;

    public DragonArmourHeartsTask(JavaPlugin plugin, ItemFactory itemFactory) {
        this.plugin      = plugin;
        this.itemFactory = itemFactory;
    }

    @Override
    public void run() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            applyBonus(player);
        }
    }

    private void applyBonus(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;

        int dragonPieces = 0;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece != null && itemFactory.getMeleeGearTier(piece) == MeleeGearTier.DRAGON) {
                dragonPieces++;
            }
        }

        double bonus = dragonPieces * HEARTS_PER_PIECE;
        if (dragonPieces >= 4) bonus += FULL_SET_BONUS;

        // Remove any existing modifier from this plugin before re-adding (avoids stacking)
        attr.getModifiers().stream()
            .filter(m -> DRAGON_HP_UUID.equals(m.getUniqueId()))
            .forEach(attr::removeModifier);

        if (bonus <= 0) return;

        attr.addModifier(new AttributeModifier(
                DRAGON_HP_UUID,
                DRAGON_HP_KEY,
                bonus,
                AttributeModifier.Operation.ADD_NUMBER
        ));

        if (player.getHealth() > attr.getValue()) {
            player.setHealth(attr.getValue());
        }
    }

    /** Removes the Dragon Armour HP modifier for a player (e.g. on plugin disable). */
    public static void removeBonus(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        attr.getModifiers().stream()
            .filter(m -> DRAGON_HP_UUID.equals(m.getUniqueId()))
            .forEach(attr::removeModifier);
    }
}
