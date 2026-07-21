package com.yourname.difficulty.vip;

import com.yourname.difficulty.currency.GoldManager;
import com.yourname.difficulty.items.ItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * VipShopListener — VIP Shop system.
 *
 * ── Villager NPC ─────────────────────────────────────────────────────────────
 *  Spawned with /vipshop spawn.  Has PDC tag "vip_shop_keeper".
 *  Invulnerable, AI-disabled, custom name.
 *  Right-clicking opens the VIP shop GUI.
 *
 * ── Shop Items ────────────────────────────────────────────────────────────────
 *  🦄 Unicorn Slippers — §e5,000 gp
 *    Cosmetic leather boots that create a rainbow particle trail at the feet.
 *    The rainbow effect runs as a repeating task for all online players wearing them.
 *
 * ── Rainbow Trail ─────────────────────────────────────────────────────────────
 *  Every 3 ticks: spawn a DUST particle at the player's foot level, cycling
 *  through 7 rainbow colours in sequence.
 */
public class VipShopListener implements Listener {

    public  static final String VIP_VILLAGER_KEY = "vip_shop_keeper";
    private static final String TITLE            = "§6✦ §eVIP Shop §6✦";
    private static final int    SIZE             = 27;

    // Shop item slots and prices
    private static final int  SLOT_CLASSIC_POTION   = 10;
    private static final long PRICE_CLASSIC_POTION  = 2_500L;
    private static final int  SLOT_AXOLOTL   = 11;
    private static final long PRICE_AXOLOTL  = 5_000L;
    private static final int  SLOT_FIRE_SPARK_POTION = 12;
    private static final long PRICE_FIRE_SPARK_POTION = 2_500L;
    private static final int  SLOT_SLIPPERS  = 13;
    private static final long PRICE_SLIPPERS = 5_000L;
    private static final int  SLOT_BLUE_POTION      = 14;
    private static final long PRICE_BLUE_POTION     = 2_500L;

    private final JavaPlugin  plugin;
    private final ItemFactory itemFactory;
    private final GoldManager goldManager;
    private final NamespacedKey vipVillagerKey;

    /**
     * Rainbow ring — 12 vivid colours evenly spaced around the HSB hue wheel.
     * More colours = smoother, more vibrant ring. Each colour is fully saturated
     * (S=1, B=1) so they are as vibrant as possible.
     */
    private static final Color[] RAINBOW = buildVibrantRainbow(12);
    /** Per-player rotation tick counter for the spinning ring effect. */
    private final Map<UUID, Integer> rainbowIndex = new HashMap<>();
    private BukkitTask rainbowTask;

    /** Builds N fully-saturated rainbow colours evenly spaced around the hue wheel. */
    private static Color[] buildVibrantRainbow(int count) {
        Color[] colors = new Color[count];
        for (int i = 0; i < count; i++) {
            float hue = (float) i / count;
            java.awt.Color awt = java.awt.Color.getHSBColor(hue, 1.0f, 1.0f);
            colors[i] = Color.fromRGB(awt.getRed(), awt.getGreen(), awt.getBlue());
        }
        return colors;
    }

    public VipShopListener(JavaPlugin plugin, ItemFactory itemFactory, GoldManager goldManager) {
        this.plugin         = plugin;
        this.itemFactory    = itemFactory;
        this.goldManager    = goldManager;
        this.vipVillagerKey = new NamespacedKey(plugin, VIP_VILLAGER_KEY);
        startRainbowTask();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  VILLAGER INTERACTION
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)) return;
        if (!isVipShopKeeper(villager)) return;

        event.setCancelled(true); // prevent vanilla trade GUI
        openShop(event.getPlayer());
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SHOP GUI
    // ══════════════════════════════════════════════════════════════════════════

    private void openShop(Player player) {
        Inventory inv = Bukkit.createInventory(null, SIZE, TITLE);

        // Fill with decorative glass
        ItemStack glass = makeFiller();
        for (int i = 0; i < SIZE; i++) inv.setItem(i, glass);

        // Classic Lightning Potion
        inv.setItem(SLOT_CLASSIC_POTION, makeShopItem(
            itemFactory.buildClassicLightningPotion(),
            PRICE_CLASSIC_POTION,
            "§7Permanently resets your lightning",
            "§7appearance to the §eClassic/Default§7 style."
        ));

        // Rainbow Axolotl
        inv.setItem(SLOT_AXOLOTL, makeShopItem(
            itemFactory.buildRainbowAxolotl(),
            PRICE_AXOLOTL,
            "§7Right-click to summon a §dBlue Axolotl §7companion!",
            "§7Spawns with a §drainbow shimmer trail§7."
        ));

        // Fire & Sparks Lightning Potion
        inv.setItem(SLOT_FIRE_SPARK_POTION, makeShopItem(
            itemFactory.buildFireSparkLightningPotion(),
            PRICE_FIRE_SPARK_POTION,
            "§7Permanently sets your lightning",
            "§7appearance to §cFire & Sparks§7 style."
        ));

        // Unicorn Slippers
        inv.setItem(SLOT_SLIPPERS, makeShopItem(
            itemFactory.buildUnicornSlippers(),
            PRICE_SLIPPERS,
            "§7Creates a §drainbow aura§7 at your feet!",
            "§7Purely cosmetic — works over any boots."
        ));

        // Blue Lightning Potion
        inv.setItem(SLOT_BLUE_POTION, makeShopItem(
            itemFactory.buildBlueLightningPotion(),
            PRICE_BLUE_POTION,
            "§7Permanently sets your lightning",
            "§7appearance to the new §bBlue Lightning§7 style."
        ));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 1.0f, 1.2f);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onShopClick(InventoryClickEvent event) {
        if (!TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(event.getView().getTopInventory())) return;

        int slot = event.getSlot();

        if (slot == SLOT_CLASSIC_POTION) {
            handlePurchase(player, itemFactory.buildClassicLightningPotion(), PRICE_CLASSIC_POTION, "Classic Lightning Potion");
        }
        if (slot == SLOT_AXOLOTL) {
            handlePurchase(player, itemFactory.buildRainbowAxolotl(), PRICE_AXOLOTL, "Rainbow Axolotl");
        }
        if (slot == SLOT_FIRE_SPARK_POTION) {
            handlePurchase(player, itemFactory.buildFireSparkLightningPotion(), PRICE_FIRE_SPARK_POTION, "Fire & Sparks Lightning Potion");
        }
        if (slot == SLOT_SLIPPERS) {
            handlePurchase(player, itemFactory.buildUnicornSlippers(), PRICE_SLIPPERS, "Unicorn Slippers");
        }
        if (slot == SLOT_BLUE_POTION) {
            handlePurchase(player, itemFactory.buildBlueLightningPotion(), PRICE_BLUE_POTION, "Blue Lightning Potion");
        }
    }

    private void handlePurchase(Player player, ItemStack item, long price, String itemName) {
        long balance = goldManager.getBalance(player.getUniqueId());
        if (balance < price) {
            player.sendMessage("§c✗ §7Not enough gold! Need §e" + GoldManager.formatGold(price)
                + " gp§7, have §e" + GoldManager.formatGold(balance) + " gp§7.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        goldManager.spendGold(player.getUniqueId(), price);
        player.getInventory().addItem(item);
        player.sendMessage("§6✦ §7Purchased: §f" + itemName + " §7for §e"
            + GoldManager.formatGold(price) + " gp§7!");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        player.closeInventory();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  UNICORN SLIPPERS RAINBOW EFFECT
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Spawns a smooth, rotating rainbow ring at the player's feet.
     *
     * Every 3 ticks all 12 rainbow colours are placed simultaneously at evenly-
     * spaced points around a circle of radius 0.50 blocks.  A slow rotation is
     * applied (0.3 radians per tick ≈ one full spin every ~21 ticks / 1.05 s)
     * so the ring looks like it is spinning around the player.
     *
     * Particle size is 1.8 (large, vivid) with near-zero spread so each dot
     * lands exactly on the ring — no random "asterisk" splatter.
     */
    private void startRainbowTask() {
        rainbowTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                ItemStack boots = player.getInventory().getBoots();
                if (!itemFactory.isUnicornSlippers(boots)) continue;

                int tick = rainbowIndex.getOrDefault(player.getUniqueId(), 0);
                rainbowIndex.put(player.getUniqueId(), tick + 1);

                // Base angle rotates smoothly each tick
                double baseAngle = tick * 0.30;
                Location center  = player.getLocation().clone().add(0, 0.12, 0);
                double   radius  = 0.50;

                // Spawn ALL colours at once — one dot per colour around the circle
                for (int i = 0; i < RAINBOW.length; i++) {
                    double angle = baseAngle + (2.0 * Math.PI * i / RAINBOW.length);
                    double dx    = Math.cos(angle) * radius;
                    double dz    = Math.sin(angle) * radius;
                    Location dot = center.clone().add(dx, 0.0, dz);
                    player.getWorld().spawnParticle(
                        Particle.DUST,
                        dot, 2,
                        0.02, 0.02, 0.02,   // almost no spread — precise ring placement
                        0.0,
                        new Particle.DustOptions(RAINBOW[i], 1.8f) // large vibrant dots
                    );
                }
            }
        }, 5L, 3L);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  RAINBOW AXOLOTL — right-click to release companion
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * When a player right-clicks while holding the Rainbow Axolotl bucket:
     *  1. Consume 1 Rainbow Axolotl item from their hand.
     *  2. Return 1 regular AXOLOTL_BUCKET (the "preserved original").
     *  3. Spawn a BLUE-variant Axolotl companion 2 blocks in front of the player.
     *  4. Start a rainbow particle trail orbiting the axolotl.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRainbowAxolotlUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player    player = event.getPlayer();
        ItemStack hand   = player.getInventory().getItemInMainHand();
        if (!itemFactory.isRainbowAxolotl(hand)) return;

        event.setCancelled(true);

        // Consume the Rainbow Axolotl item
        if (hand.getAmount() > 1) hand.setAmount(hand.getAmount() - 1);
        else player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

        // Return a regular axolotl bucket (original preserved)
        player.getInventory().addItem(new ItemStack(Material.AXOLOTL_BUCKET));

        // Spawn Blue axolotl companion 2 blocks in front
        Location spawnLoc = player.getLocation().add(
            player.getLocation().getDirection().setY(0).normalize().multiply(2));
        spawnLoc.setY(player.getLocation().getY());

        Axolotl companion = (Axolotl) player.getWorld().spawnEntity(spawnLoc, EntityType.AXOLOTL);
        companion.setVariant(Axolotl.Variant.BLUE);
        companion.setCustomName("§d✨ §bRainbow Companion");
        companion.setCustomNameVisible(true);
        companion.setAI(true);
        companion.setRemoveWhenFarAway(false);

        // Start rainbow shimmer trail on the axolotl
        startAxolotlRainbowTrail(companion);

        player.sendMessage("§d🌈 §7Your §dRainbow Axolotl §7companion has been released!");
        player.sendMessage("  §8A regular axolotl bucket was returned to preserve the original.");
        player.playSound(player.getLocation(), Sound.ENTITY_AXOLOTL_IDLE_WATER, 1.0f, 1.5f);
        player.getWorld().spawnParticle(Particle.HEART,
            companion.getLocation().add(0, 1, 0), 8, 0.4, 0.2, 0.4, 0.05);
        player.getWorld().spawnParticle(Particle.ENCHANT,
            companion.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
    }

    /**
     * Starts a rainbow shimmer particle ring that orbits the given axolotl entity.
     * Uses the same 12-colour RAINBOW array as the slippers, cycling through with
     * a slow rotation every 3 ticks for a smooth iridescent effect.
     */
    private void startAxolotlRainbowTrail(Axolotl axolotl) {
        final int[] tickRef = {0};
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!axolotl.isValid() || axolotl.isDead()) { task.cancel(); return; }

            int tick = tickRef[0]++;
            double baseAngle = tick * 0.25;
            Location center  = axolotl.getLocation().clone().add(0, 0.3, 0);
            double   radius  = 0.60;

            for (int i = 0; i < RAINBOW.length; i++) {
                double angle = baseAngle + (2.0 * Math.PI * i / RAINBOW.length);
                double dx    = Math.cos(angle) * radius;
                double dz    = Math.sin(angle) * radius;
                Location dot = center.clone().add(dx, 0.0, dz);
                axolotl.getWorld().spawnParticle(
                    Particle.DUST,
                    dot, 1,
                    0.02, 0.02, 0.02,
                    0.0,
                    new Particle.DustOptions(RAINBOW[i], 1.6f)
                );
            }
        }, 5L, 3L);
    }

    public void shutdown() {
        if (rainbowTask != null) rainbowTask.cancel();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  VIP VILLAGER HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    /** Spawns a VIP Shop Keeper villager at the given location. */
    public void spawnVipKeeper(Location loc) {
        Villager v = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
        v.setCustomName("§6✦ §eVIP Shop Keeper §6✦");
        v.setCustomNameVisible(true);
        v.setAI(false);
        v.setInvulnerable(true);
        v.setVillagerType(Villager.Type.PLAINS);
        v.setProfession(Villager.Profession.CARTOGRAPHER);
        v.getPersistentDataContainer().set(vipVillagerKey, PersistentDataType.BYTE, (byte) 1);
    }

    public boolean isVipShopKeeper(Villager villager) {
        return villager.getPersistentDataContainer().has(vipVillagerKey, PersistentDataType.BYTE);
    }

    /** Prevent VIP shop keepers from taking damage. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVipDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Villager v)) return;
        if (isVipShopKeeper(v)) event.setCancelled(true);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ITEM BUILDERS
    // ══════════════════════════════════════════════════════════════════════════

    private ItemStack makeShopItem(ItemStack base, long price, String... extraLore) {
        ItemStack display = base.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            if (meta.hasLore()) lore.addAll(meta.getLore());
            lore.add("");
            lore.add("§8" + "─".repeat(22));
            lore.add("§6Price: §e" + GoldManager.formatGold(price) + " gp");
            for (String line : extraLore) lore.add(line);
            lore.add("§8Click to purchase.");
            lore.add("§8" + "─".repeat(22));
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }

    private ItemStack makeFiller() {
        ItemStack g = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = g.getItemMeta();
        if (m != null) { m.setDisplayName("§8"); g.setItemMeta(m); }
        return g;
    }
}
