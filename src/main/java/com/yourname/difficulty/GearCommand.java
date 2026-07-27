package com.yourname.difficulty;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * /gear â€” Admin-only command that equips a player with max-enchanted
 *         netherite gear including 10Ã— speed boots.
 *
 * Usage:
 *   /gear           â†’ gives gear to yourself
 *   /gear <player>  â†’ gives gear to another player (requires difficultyengine.gear.others)
 *
 * Speed boots:
 *   An AttributeModifier is applied to the boots using ADD_SCALAR operation
 *   with a value of 9.0 â€” this multiplies the base walk speed by (1 + 9) = 10Ã—.
 *   The modifier only activates while the boots are worn (Minecraft attribute system).
 */
public class GearCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        Player target;
        if (args.length >= 1) {
            if (!sender.hasPermission("difficultyengine.gear.others")) {
                sender.sendMessage("Â§cYou don't have permission to give gear to other players.");
                return true;
            }
            target = sender.getServer().getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage("Â§cPlayer not found: Â§f" + args[0]);
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage("Â§cConsole must specify a player: /gear <player>");
            return true;
        }

        giveGear(target);

        sender.sendMessage("Â§8[Â§6DifficultyEngineÂ§8] Â§aâ˜  God gear given to Â§f" + target.getName() + "Â§a.");
        if (!target.equals(sender)) {
            target.sendMessage("Â§8[Â§6DifficultyEngineÂ§8] Â§aYou received Â§4god-tier netherite gear Â§afrom Â§f"
                    + sender.getName() + "Â§a.");
        }

        return true;
    }

    // â”€â”€ Gear building â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void giveGear(Player player) {

        // â”€â”€ Sword â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        ItemStack sword = build(Material.NETHERITE_SWORD,
                e(Enchantment.SHARPNESS,   255),
                e(Enchantment.KNOCKBACK,   255),
                e(Enchantment.FIRE_ASPECT, 255),
                e(Enchantment.UNBREAKING,  255),
                e(Enchantment.MENDING,       1));

        // â”€â”€ Helmet â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        ItemStack helmet = build(Material.NETHERITE_HELMET,
                e(Enchantment.PROTECTION,    255),
                e(Enchantment.UNBREAKING,    255),
                e(Enchantment.MENDING,         1),
                e(Enchantment.RESPIRATION,   255),
                e(Enchantment.AQUA_AFFINITY,   1));

        // â”€â”€ Chestplate â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        ItemStack chest = build(Material.NETHERITE_CHESTPLATE,
                e(Enchantment.PROTECTION, 255),
                e(Enchantment.UNBREAKING, 255),
                e(Enchantment.MENDING,      1));

        // â”€â”€ Leggings â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        ItemStack legs = build(Material.NETHERITE_LEGGINGS,
                e(Enchantment.PROTECTION, 255),
                e(Enchantment.UNBREAKING, 255),
                e(Enchantment.MENDING,      1));

        // â”€â”€ Boots â€” 10Ã— speed via AttributeModifier â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        ItemStack boots = build(Material.NETHERITE_BOOTS,
                e(Enchantment.PROTECTION,     255),
                e(Enchantment.UNBREAKING,     255),
                e(Enchantment.MENDING,          1),
                e(Enchantment.FEATHER_FALLING, 255),
                e(Enchantment.DEPTH_STRIDER,    3));

        // ADD_SCALAR(9.0): final speed = base Ã— (1 + 9) = 10Ã— walk speed
        // Paper 1.21 API: use NamespacedKey + EquipmentSlotGroup (replaces UUID + EquipmentSlot)
        // Modifier only applies while boots are worn â€” no listener needed
        ItemMeta bootsMeta = boots.getItemMeta();
        if (bootsMeta != null) {
            bootsMeta.addAttributeModifier(
                    Attribute.MOVEMENT_SPEED,
                    new AttributeModifier(
                            new NamespacedKey("difficultyengine", "de_gear_speed"),
                            9.0,
                            AttributeModifier.Operation.ADD_SCALAR,
                            EquipmentSlotGroup.FEET
                    ));
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

    // â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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
