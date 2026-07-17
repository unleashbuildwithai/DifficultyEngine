package com.yourname.difficulty;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * /gear — Admin-only command that equips a player with max-enchanted
 *         netherite gear and a god-tier netherite sword.
 *
 * Usage:
 *   /gear           → gives gear to yourself
 *   /gear <player>  → gives gear to another player (requires difficultyengine.gear.others)
 *
 * Permission: difficultyengine.gear (default: op)
 */
public class GearCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        // Resolve the target player
        Player target;
        if (args.length >= 1) {
            if (!sender.hasPermission("difficultyengine.gear.others")) {
                sender.sendMessage("§cYou don't have permission to give gear to other players.");
                return true;
            }
            target = sender.getServer().getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found: §f" + args[0]);
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage("§cConsole must specify a player: /gear <player>");
            return true;
        }

        // Give the gear
        giveGear(target);

        sender.sendMessage("§8[§6DifficultyEngine§8] §a☠ God gear given to §f" + target.getName() + "§a.");
        if (!target.equals(sender)) {
            target.sendMessage("§8[§6DifficultyEngine§8] §aYou received §4god-tier netherite gear §afrom §f"
                    + sender.getName() + "§a.");
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // Gear building
    // -------------------------------------------------------------------------

    private void giveGear(Player player) {

        // ── Sword ─────────────────────────────────────────────────────────────
        ItemStack sword = build(Material.NETHERITE_SWORD,
                e(Enchantment.SHARPNESS,   255),
                e(Enchantment.KNOCKBACK,   255),
                e(Enchantment.FIRE_ASPECT, 255),
                e(Enchantment.UNBREAKING,  255),
                e(Enchantment.MENDING,       1));

        // ── Helmet ────────────────────────────────────────────────────────────
        ItemStack helmet = build(Material.NETHERITE_HELMET,
                e(Enchantment.PROTECTION,    255),
                e(Enchantment.UNBREAKING,    255),
                e(Enchantment.MENDING,         1),
                e(Enchantment.RESPIRATION,   255),
                e(Enchantment.AQUA_AFFINITY,   1));

        // ── Chestplate ────────────────────────────────────────────────────────
        ItemStack chest = build(Material.NETHERITE_CHESTPLATE,
                e(Enchantment.PROTECTION, 255),
                e(Enchantment.UNBREAKING, 255),
                e(Enchantment.MENDING,      1));

        // ── Leggings ─────────────────────────────────────────────────────────
        ItemStack legs = build(Material.NETHERITE_LEGGINGS,
                e(Enchantment.PROTECTION, 255),
                e(Enchantment.UNBREAKING, 255),
                e(Enchantment.MENDING,      1));

        // ── Boots ─────────────────────────────────────────────────────────────
        ItemStack boots = build(Material.NETHERITE_BOOTS,
                e(Enchantment.PROTECTION,     255),
                e(Enchantment.UNBREAKING,     255),
                e(Enchantment.MENDING,          1),
                e(Enchantment.FEATHER_FALLING, 255),
                e(Enchantment.DEPTH_STRIDER,    3));

        // Place armor in the dedicated armor slots, sword into inventory
        player.getInventory().setHelmet(helmet);
        player.getInventory().setChestplate(chest);
        player.getInventory().setLeggings(legs);
        player.getInventory().setBoots(boots);
        player.getInventory().addItem(sword);
        player.updateInventory();
    }

    // ── Tiny helper record ────────────────────────────────────────────────────
    private record EnchEntry(Enchantment enchantment, int level) {}

    private EnchEntry e(Enchantment enchantment, int level) {
        return new EnchEntry(enchantment, level);
    }

    /**
     * Builds an ItemStack with the given enchantments.
     * ignoreLevelRestriction = true lets levels exceed vanilla maximums (e.g. 255).
     */
    private ItemStack build(Material material, EnchEntry... enchantments) {
        ItemStack item = new ItemStack(material);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            for (EnchEntry entry : enchantments) {
                meta.addEnchant(entry.enchantment(), entry.level(), true);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
