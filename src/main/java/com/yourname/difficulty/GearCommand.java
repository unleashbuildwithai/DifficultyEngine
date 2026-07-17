package com.yourname.difficulty;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * /gear — Admin-only command that equips a player with max-enchanted
 *         netherite gear including 10× speed boots.
 *
 * Usage:
 *   /gear           → gives gear to yourself
 *   /gear <player>  → gives gear to another player (requires difficultyengine.gear.others)
 *
 * Speed boots:
 *   An AttributeModifier is applied to the boots using ADD_SCALAR operation
 *   with a value of 9.0 — this multiplies the base walk speed by (1 + 9) = 10×.
 *   The modifier only activates while the boots are worn (Minecraft attribute system).
 */
public class GearCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

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

        giveGear(target);

        sender.sendMessage("§8[§6DifficultyEngine§8] §a☠ God gear given to §f" + target.getName() + "§a.");
        if (!target.equals(sender)) {
            target.sendMessage("§8[§6DifficultyEngine§8] §aYou received §4god-tier netherite gear §afrom §f"
                    + sender.getName() + "§a.");
        }

        return true;
    }

    // ── Gear building ─────────────────────────────────────────────────────────

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

        // ── Boots — 10× speed via AttributeModifier ───────────────────────────
        ItemStack boots = build(Material.NETHERITE_BOOTS,
                e(Enchantment.PROTECTION,     255),
                e(Enchantment.UNBREAKING,     255),
                e(Enchantment.MENDING,          1),
                e(Enchantment.FEATHER_FALLING, 255),
                e(Enchantment.DEPTH_STRIDER,    3));

        // ADD_SCALAR(9.0): final speed = base × (1 + 9) = 10× walk speed
        // Modifier only applies while boots are worn — no listener needed
        ItemMeta bootsMeta = boots.getItemMeta();
        if (bootsMeta != null) {
            UUID modId = UUID.nameUUIDFromBytes(
                    "de_gear_speed".getBytes(StandardCharsets.UTF_8));
            bootsMeta.addAttributeModifier(
                    Attribute.GENERIC_MOVEMENT_SPEED,
                    new AttributeModifier(modId, "de_gear_speed",
                            9.0,
                            AttributeModifier.Operation.ADD_SCALAR,
                            EquipmentSlot.FEET));
            boots.setItemMeta(bootsMeta);
        }

        // Place armor in dedicated slots, sword into inventory
        player.getInventory().setHelmet(helmet);
        player.getInventory().setChestplate(chest);
        player.getInventory().setLeggings(legs);
        player.getInventory().setBoots(boots);
        player.getInventory().addItem(sword);
        player.updateInventory();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private record EnchEntry(Enchantment enchantment, int level) {}

    private EnchEntry e(Enchantment enchantment, int level) {
        return new EnchEntry(enchantment, level);
    }

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
