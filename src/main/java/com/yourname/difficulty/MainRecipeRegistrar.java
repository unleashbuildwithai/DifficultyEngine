package com.yourname.difficulty;

import com.yourname.difficulty.items.EarthBlockTier;
import com.yourname.difficulty.items.ItemFactory;
import com.yourname.difficulty.magic.MagicElement;
import com.yourname.difficulty.magic.SpellBookManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Registers all crafting recipes for the plugin. Extracted from {@link Main}
 * to keep it under the 400-line limit — holds no state of its own.
 */
final class MainRecipeRegistrar {

    private MainRecipeRegistrar() {}

    static void registerAll(JavaPlugin plugin, ItemFactory itemFactory,
                             SpellBookManager spellBookManager, List<NamespacedKey> allRecipeKeys) {
        for (MagicElement el : MagicElement.values()) {
            NamespacedKey key = new NamespacedKey(plugin, el.staffKey + "_recipe");
            ItemStack staffResult = itemFactory.buildStaff(el);
            ShapelessRecipe recipe = new ShapelessRecipe(key, staffResult);
            recipe.addIngredient(Material.AMETHYST_SHARD);
            recipe.addIngredient(el.staffCraftIngredient);
            recipe.addIngredient(Material.STICK);
            plugin.getServer().addRecipe(recipe);
            allRecipeKeys.add(key);
        }

        for (MagicElement el : MagicElement.values()) {
            NamespacedKey key = new NamespacedKey(plugin, el.runeKey + "_recipe");
            ItemStack runeResult = itemFactory.buildRune(el, 8);
            ShapelessRecipe recipe = new ShapelessRecipe(key, runeResult);
            recipe.addIngredient(4, el.runeCraftIngredient);
            plugin.getServer().addRecipe(recipe);
            allRecipeKeys.add(key);
        }

        NamespacedKey dragonArrowRecipe = new NamespacedKey(plugin, "dragon_arrow_recipe");
        ItemStack dragonArrowResult = itemFactory.buildDragonArrow(4);
        ShapelessRecipe dragonArrowR = new ShapelessRecipe(dragonArrowRecipe, dragonArrowResult);
        dragonArrowR.addIngredient(4, Material.PRISMARINE_CRYSTALS);
        plugin.getServer().addRecipe(dragonArrowR);
        allRecipeKeys.add(dragonArrowRecipe);

        for (String[] entry : new String[][]{
            {"LEATHER_HELMET","mage_hood_recipe"},{"LEATHER_CHESTPLATE","mage_robe_top_recipe"},
            {"LEATHER_LEGGINGS","mage_robe_bottom_recipe"},{"LEATHER_BOOTS","mage_boots_recipe"}
        }) {
            Material mat = Material.valueOf(entry[0]);
            NamespacedKey k = new NamespacedKey(plugin, entry[1]);
            ShapelessRecipe r = new ShapelessRecipe(k, new ItemStack(mat));
            r.addIngredient(mat); r.addIngredient(Material.PURPLE_DYE); r.addIngredient(Material.BLAZE_POWDER);
            plugin.getServer().addRecipe(r); allRecipeKeys.add(k);
        }

        for (String[] entry : new String[][]{
            {"LEATHER_HELMET","apprentice_hood_recipe"},{"LEATHER_CHESTPLATE","apprentice_top_recipe"},
            {"LEATHER_LEGGINGS","apprentice_bottom_recipe"},{"LEATHER_BOOTS","apprentice_boots_recipe"}
        }) {
            Material mat = Material.valueOf(entry[0]);
            NamespacedKey k = new NamespacedKey(plugin, entry[1]);
            ShapelessRecipe r = new ShapelessRecipe(k, new ItemStack(mat));
            r.addIngredient(mat); r.addIngredient(Material.PURPLE_DYE); r.addIngredient(Material.STRING);
            plugin.getServer().addRecipe(r); allRecipeKeys.add(k);
        }

        for (String[] entry : new String[][]{
            {"LEATHER_HELMET","alch_hood_recipe"},{"LEATHER_CHESTPLATE","alch_top_recipe"},
            {"LEATHER_LEGGINGS","alch_bottom_recipe"},{"LEATHER_BOOTS","alch_boots_recipe"}
        }) {
            Material mat = Material.valueOf(entry[0]);
            NamespacedKey k = new NamespacedKey(plugin, entry[1]);
            ShapelessRecipe r = new ShapelessRecipe(k, new ItemStack(mat));
            r.addIngredient(mat); r.addIngredient(Material.BLUE_DYE);
            r.addIngredient(Material.BLAZE_POWDER); r.addIngredient(Material.ENDER_EYE);
            plugin.getServer().addRecipe(r); allRecipeKeys.add(k);
        }

        for (String[] entry : new String[][]{
            {"LEATHER_HELMET","master_hood_recipe"},{"LEATHER_CHESTPLATE","master_top_recipe"},
            {"LEATHER_LEGGINGS","master_bottom_recipe"},{"LEATHER_BOOTS","master_boots_recipe"}
        }) {
            Material mat = Material.valueOf(entry[0]);
            NamespacedKey k = new NamespacedKey(plugin, entry[1]);
            ShapelessRecipe r = new ShapelessRecipe(k, new ItemStack(mat));
            r.addIngredient(mat); r.addIngredient(Material.BLACK_DYE);
            r.addIngredient(Material.BLAZE_POWDER); r.addIngredient(Material.AMETHYST_SHARD);
            r.addIngredient(Material.DRAGON_BREATH);
            plugin.getServer().addRecipe(r); allRecipeKeys.add(k);
        }

        addCauldronRecipe(plugin, allRecipeKeys, "cauldron_fire_basic",    16, Material.LAVA_BUCKET,  4, Material.NETHERRACK);
        addCauldronRecipe(plugin, allRecipeKeys, "cauldron_fire_premium",   64, Material.LAVA_BUCKET,  4, Material.NETHERRACK);
        addCauldronRecipe(plugin, allRecipeKeys, "cauldron_water_basic",   16, Material.WATER_BUCKET, 4, Material.PRISMARINE_SHARD);
        addCauldronRecipe(plugin, allRecipeKeys, "cauldron_water_premium",  64, Material.WATER_BUCKET, 4, Material.PRISMARINE_SHARD);
        addCauldronRecipe(plugin, allRecipeKeys, "cauldron_earth_basic",   16, Material.WATER_BUCKET, 4, Material.DIRT);
        addCauldronRecipe(plugin, allRecipeKeys, "cauldron_earth_premium",  64, Material.WATER_BUCKET, 4, Material.DIRT);

        NamespacedKey cab = new NamespacedKey(plugin, "cauldron_air_basic");
        ShapelessRecipe cabR = new ShapelessRecipe(cab, new ItemStack(Material.GLOWSTONE_DUST, 16));
        cabR.addIngredient(Material.CAULDRON); cabR.addIngredient(Material.PUFFERFISH); cabR.addIngredient(Material.WATER_BUCKET);
        plugin.getServer().addRecipe(cabR); allRecipeKeys.add(cab);

        NamespacedKey capP = new NamespacedKey(plugin, "cauldron_air_premium");
        ShapelessRecipe capR = new ShapelessRecipe(capP, new ItemStack(Material.GLOWSTONE_DUST, 64));
        capR.addIngredient(Material.CAULDRON); capR.addIngredient(Material.PUFFERFISH);
        capR.addIngredient(Material.WATER_BUCKET); capR.addIngredient(Material.DIAMOND);
        plugin.getServer().addRecipe(capR); allRecipeKeys.add(capP);

        for (String[] e : new String[][]{
            {"IRON_HELMET","melee_iron_helmet"},{"IRON_CHESTPLATE","melee_iron_chestplate"},
            {"IRON_LEGGINGS","melee_iron_leggings"},{"IRON_BOOTS","melee_iron_boots"}
        }) { addMeleeRecipe(plugin, allRecipeKeys, e[0], e[1], Material.IRON_INGOT); }
        for (String[] e : new String[][]{
            {"DIAMOND_HELMET","melee_diamond_helmet"},{"DIAMOND_CHESTPLATE","melee_diamond_chestplate"},
            {"DIAMOND_LEGGINGS","melee_diamond_leggings"},{"DIAMOND_BOOTS","melee_diamond_boots"}
        }) { addMeleeRecipe(plugin, allRecipeKeys, e[0], e[1], Material.DIAMOND); }
        for (String[] e : new String[][]{
            {"NETHERITE_HELMET","melee_netherite_helmet"},{"NETHERITE_CHESTPLATE","melee_netherite_chestplate"},
            {"NETHERITE_LEGGINGS","melee_netherite_leggings"},{"NETHERITE_BOOTS","melee_netherite_boots"}
        }) { addMeleeRecipe(plugin, allRecipeKeys, e[0], e[1], Material.NETHERITE_INGOT); }
        for (String[] e : new String[][]{
            {"NETHERITE_HELMET","melee_dragon_helmet"},{"NETHERITE_CHESTPLATE","melee_dragon_chestplate"},
            {"NETHERITE_LEGGINGS","melee_dragon_leggings"},{"NETHERITE_BOOTS","melee_dragon_boots"}
        }) {
            Material mat = Material.valueOf(e[0]); NamespacedKey k = new NamespacedKey(plugin, e[1]);
            ShapelessRecipe r = new ShapelessRecipe(k, new ItemStack(mat));
            r.addIngredient(mat); r.addIngredient(Material.NETHER_STAR); r.addIngredient(Material.DRAGON_BREATH);
            plugin.getServer().addRecipe(r); allRecipeKeys.add(k);
        }

        for (String[] e : new String[][]{
            {"LEATHER_HELMET","ranged_leather_helmet"},{"LEATHER_CHESTPLATE","ranged_leather_chestplate"},
            {"LEATHER_LEGGINGS","ranged_leather_leggings"},{"LEATHER_BOOTS","ranged_leather_boots"}
        }) { addMeleeRecipe(plugin, allRecipeKeys, e[0], e[1], Material.STRING); }
        for (String[] e : new String[][]{
            {"CHAINMAIL_HELMET","ranged_chain_helmet"},{"CHAINMAIL_CHESTPLATE","ranged_chain_chestplate"},
            {"CHAINMAIL_LEGGINGS","ranged_chain_leggings"},{"CHAINMAIL_BOOTS","ranged_chain_boots"}
        }) {
            Material mat = Material.valueOf(e[0]); NamespacedKey k = new NamespacedKey(plugin, e[1]);
            ShapelessRecipe r = new ShapelessRecipe(k, new ItemStack(mat));
            r.addIngredient(mat); r.addIngredient(Material.FEATHER); r.addIngredient(Material.LAPIS_LAZULI);
            plugin.getServer().addRecipe(r); allRecipeKeys.add(k);
        }
        for (String[] e : new String[][]{
            {"NETHERITE_HELMET","ranged_netherite_helmet"},{"NETHERITE_CHESTPLATE","ranged_netherite_chestplate"},
            {"NETHERITE_LEGGINGS","ranged_netherite_leggings"},{"NETHERITE_BOOTS","ranged_netherite_boots"}
        }) {
            Material mat = Material.valueOf(e[0]); NamespacedKey k = new NamespacedKey(plugin, e[1]);
            ShapelessRecipe r = new ShapelessRecipe(k, new ItemStack(mat));
            r.addIngredient(mat); r.addIngredient(Material.NETHERITE_INGOT); r.addIngredient(Material.FEATHER);
            plugin.getServer().addRecipe(r); allRecipeKeys.add(k);
        }
        for (String[] e : new String[][]{
            {"NETHERITE_HELMET","ranged_dragon_helmet"},{"NETHERITE_CHESTPLATE","ranged_dragon_chestplate"},
            {"NETHERITE_LEGGINGS","ranged_dragon_leggings"},{"NETHERITE_BOOTS","ranged_dragon_boots"}
        }) {
            Material mat = Material.valueOf(e[0]); NamespacedKey k = new NamespacedKey(plugin, e[1]);
            ShapelessRecipe r = new ShapelessRecipe(k, new ItemStack(mat));
            r.addIngredient(mat); r.addIngredient(Material.NETHER_STAR); r.addIngredient(Material.ARROW);
            plugin.getServer().addRecipe(r); allRecipeKeys.add(k);
        }

        NamespacedKey atk = new NamespacedKey(plugin, "arcane_tome_recipe");
        ShapelessRecipe atr = new ShapelessRecipe(atk, spellBookManager.buildArcaneTomeItem());
        atr.addIngredient(Material.BOOK); atr.addIngredient(Material.AMETHYST_SHARD); atr.addIngredient(Material.PURPLE_DYE);
        plugin.getServer().addRecipe(atr); allRecipeKeys.add(atk);

        NamespacedKey spk = new NamespacedKey(plugin, "soulfur_potion_recipe");
        ShapelessRecipe spr = new ShapelessRecipe(spk, new ItemStack(Material.POTION));
        spr.addIngredient(Material.GLASS_BOTTLE); spr.addIngredient(Material.SOUL_SAND);
        spr.addIngredient(Material.BLAZE_POWDER); spr.addIngredient(Material.NETHER_WART);
        plugin.getServer().addRecipe(spr); allRecipeKeys.add(spk);

        NamespacedKey tmk = new NamespacedKey(plugin, "turbo_minecart_recipe");
        ShapelessRecipe tmr = new ShapelessRecipe(tmk, new ItemStack(Material.MINECART));
        tmr.addIngredient(Material.MINECART); tmr.addIngredient(Material.POWERED_RAIL);
        tmr.addIngredient(Material.REDSTONE); tmr.addIngredient(Material.GOLD_INGOT);
        plugin.getServer().addRecipe(tmr); allRecipeKeys.add(tmk);

        NamespacedKey mbk = new NamespacedKey(plugin, "magic_bag_recipe");
        ShapelessRecipe mbr = new ShapelessRecipe(mbk, new ItemStack(Material.CHEST));
        mbr.addIngredient(Material.CHEST); mbr.addIngredient(Material.ENDER_PEARL);
        mbr.addIngredient(Material.AMETHYST_SHARD); mbr.addIngredient(Material.PURPLE_DYE);
        mbr.addIngredient(Material.STRING);
        plugin.getServer().addRecipe(mbr); allRecipeKeys.add(mbk);

        for (EarthBlockTier tier : EarthBlockTier.values()) {
            NamespacedKey pk = new NamespacedKey(plugin, "de_earth_page_recipe_" + tier.name().toLowerCase());
            ShapelessRecipe pr = new ShapelessRecipe(pk, new ItemStack(Material.BOOK));
            pr.addIngredient(Material.BOOK); pr.addIngredient(tier.material); pr.addIngredient(Material.STRING);
            plugin.getServer().addRecipe(pr);
        }

        addBookRecipe(plugin, allRecipeKeys, "novice_magic_primer_recipe", itemFactory.buildNoviceMagicPrimer(),
                Material.BOOK, Material.PAPER, Material.FEATHER);
        addBookRecipe(plugin, allRecipeKeys, "mages_primer_recipe", itemFactory.buildMagesPrimerBook(),
                Material.BOOK, Material.PAPER, Material.BLAZE_POWDER);
        addBookRecipe(plugin, allRecipeKeys, "elemental_theory_recipe", itemFactory.buildElementalTheoryBook(),
                Material.BOOK, Material.AMETHYST_SHARD, Material.PAPER);
        addBookRecipe(plugin, allRecipeKeys, "hidden_arts_recipe", itemFactory.buildHiddenArtsBook(),
                Material.BOOK, Material.NETHER_STAR, Material.PAPER);
        addBookRecipe(plugin, allRecipeKeys, "mage_gear_guide_recipe", itemFactory.buildMageGearGuide(),
                Material.BOOK, Material.LEATHER, Material.PURPLE_DYE);

        // ── Empty Magic Bottle — lightning capture vessel ──────────────────────
        // Recipe: 4× Glass Pane + Leather + String + Enchanted Book → 1 Empty Magic Bottle
        NamespacedKey emptyBottleKey = new NamespacedKey(plugin, "empty_magic_bottle_recipe");
        ShapelessRecipe emptyBottleRecipe = new ShapelessRecipe(emptyBottleKey,
                new ItemStack(Material.GLASS_BOTTLE));
        emptyBottleRecipe.addIngredient(4, Material.GLASS_PANE);
        emptyBottleRecipe.addIngredient(Material.LEATHER);
        emptyBottleRecipe.addIngredient(Material.STRING);
        emptyBottleRecipe.addIngredient(Material.ENCHANTED_BOOK);
        plugin.getServer().addRecipe(emptyBottleRecipe);
        allRecipeKeys.add(emptyBottleKey);

        plugin.getLogger().info("DifficultyEngine: Registered " + allRecipeKeys.size() + " crafting recipes.");
    }

    private static void addCauldronRecipe(JavaPlugin plugin, List<NamespacedKey> allRecipeKeys,
                                           String key, int amount, Material bucket, int qty, Material filler) {
        NamespacedKey k = new NamespacedKey(plugin, key);
        ShapelessRecipe r = new ShapelessRecipe(k, new ItemStack(Material.GLOWSTONE_DUST, amount));
        r.addIngredient(Material.CAULDRON); r.addIngredient(bucket); r.addIngredient(qty, filler);
        if (key.contains("premium")) r.addIngredient(Material.DIAMOND);
        plugin.getServer().addRecipe(r); allRecipeKeys.add(k);
    }

    private static void addMeleeRecipe(JavaPlugin plugin, List<NamespacedKey> allRecipeKeys,
                                        String matName, String keyName, Material ingredient) {
        Material mat = Material.valueOf(matName); NamespacedKey k = new NamespacedKey(plugin, keyName);
        ShapelessRecipe r = new ShapelessRecipe(k, new ItemStack(mat));
        r.addIngredient(mat); r.addIngredient(ingredient);
        plugin.getServer().addRecipe(r); allRecipeKeys.add(k);
    }

    private static void addBookRecipe(JavaPlugin plugin, List<NamespacedKey> allRecipeKeys,
                                       String keyName, ItemStack result, Material... ingredients) {
        NamespacedKey k = new NamespacedKey(plugin, keyName);
        ShapelessRecipe r = new ShapelessRecipe(k, result);
        for (Material m : ingredients) r.addIngredient(m);
        plugin.getServer().addRecipe(r); allRecipeKeys.add(k);
    }
}
