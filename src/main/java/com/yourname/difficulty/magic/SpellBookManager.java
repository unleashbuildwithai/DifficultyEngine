package com.yourname.difficulty.magic;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * SpellBookManager — Manages the Arcane Tome discovery system.
 *
 * 37 pages total, all starting as "???" until the player finds a Spell Page
 * item in the world. Right-clicking a Spell Page unlocks a random locked page.
 * Right-clicking the Spell Tome (or using /spellbook) opens a WRITTEN_BOOK
 * view showing all pages — discovered ones reveal combo/mechanic info,
 * undiscovered ones show "???".
 *
 * Drop source: 4 % chance from any hostile mob killed by a player.
 *
 * Persistence: plugins/DifficultyEngine/spellbook_data.yml
 */
public class SpellBookManager {

    public static final int    TOTAL_PAGES    = 37;
    public static final String SPELL_TOME_KEY = "spell_tome";
    public static final String SPELL_PAGE_KEY = "spell_page";

    private final JavaPlugin              plugin;
    private final NamespacedKey           spellTomeKey;
    private final NamespacedKey           spellPageKey;
    private final Map<UUID, Set<Integer>> unlockedPages = new HashMap<>();
    private       File                    dataFile;
    private       YamlConfiguration       dataCfg;

    // ── 37 page contents (0-indexed) ─────────────────────────────────────────
    private static final String[] PAGE_CONTENT = new String[TOTAL_PAGES];

    static {
        PAGE_CONTENT[0]  =
            "§5The Arcane Tome\n" +
            "§8──────────────\n" +
            "§7A grimoire of elemental\n" +
            "§7magic, combos and secrets.\n\n" +
            "§7Find §dSpell Pages §7dropped\n" +
            "§7by hostile mobs to unlock\n" +
            "§7each chapter.\n\n" +
            "§8Right-click to read.\n\n" +
            "§8[1 / 37]";

        PAGE_CONTENT[1]  =
            "§6The Four Elements\n" +
            "§8──────────────\n" +
            "§c🔥 Fire §7— Burn & Scorch\n" +
            "§b💧 Water §7— Soak targets\n" +
            "§2🌿 Earth §7— Slow & Crack\n" +
            "§f💨 Air §7— Launch & Freeze\n\n" +
            "§7Craft a staff from each\n" +
            "§7element. Chain them for\n" +
            "§dpowerful combo effects!\n\n" +
            "§8[2 / 37]";

        PAGE_CONTENT[2]  =
            "§c🔥 Fire Staff\n" +
            "§8──────────────\n" +
            "§7Fires a §cSmall Fireball\n" +
            "§7that deals damage on hit.\n\n" +
            "§7Base hit applies:\n" +
            "§8▶ §cSCORCHED §7(3 sec)\n" +
            "§8▶ Short fire DoT\n\n" +
            "§7Hits frozen/statue targets\n" +
            "§7to trigger powerful combos.\n\n" +
            "§8[3 / 37]";

        PAGE_CONTENT[3]  =
            "§b💧 Water Staff\n" +
            "§8──────────────\n" +
            "§7Fires a water bolt.\n\n" +
            "§7Base hit applies:\n" +
            "§8▶ §bWET §7(5–10 sec)\n" +
            "§8▶ Normal damage\n\n" +
            "§7Tip: right-click a block\n" +
            "§7with a water bucket to\n" +
            "§7place a 5-block stream.\n\n" +
            "§8[4 / 37]";

        PAGE_CONTENT[4]  =
            "§2🌿 Earth Staff\n" +
            "§8──────────────\n" +
            "§7Fires a dirt bolt.\n\n" +
            "§7Base hit applies:\n" +
            "§8▶ Slowness I (2 sec)\n" +
            "§8▶ Normal damage\n\n" +
            "§7Hit a WET target to apply\n" +
            "§6MUDDY §7(Slowness IV) and\n" +
            "§7enable the §eSTATUE §7combo.\n\n" +
            "§8[5 / 37]";

        PAGE_CONTENT[5]  =
            "§f💨 Air Staff\n" +
            "§8──────────────\n" +
            "§7No projectile! Targets the\n" +
            "§7nearest mob within §e20 blocks\n" +
            "§7and blasts it instantly.\n\n" +
            "§7Base hit:\n" +
            "§8▶ Heavy knockback\n" +
            "§8▶ Scales with Magic level\n" +
            "§8▶ Closer = harder KB\n\n" +
            "§7No rune needed!\n\n" +
            "§8[6 / 37]";

        PAGE_CONTENT[6]  =
            "§dStatus Effects\n" +
            "§8──────────────\n" +
            "§7Hitting targets with a staff\n" +
            "§7applies STATUS EFFECTS.\n\n" +
            "§7Chain statuses across\n" +
            "§7elements for combos that\n" +
            "§7deal bonus damage, crowd\n" +
            "§7control, or §cinstant kills!\n\n" +
            "§7Read on to discover each\n" +
            "§7status and combo.\n\n" +
            "§8[7 / 37]";

        PAGE_CONTENT[7]  =
            "§b[WET]\n" +
            "§8──────────────\n" +
            "§7Source:   §bWater §7(base)\n" +
            "§7Duration: §e5–10 sec\n\n" +
            "§7Opens combos:\n" +
            "§8▶ §2Earth §7→ §6MUDDY\n" +
            "§8▶ §fAir §7→ §bCHILLED\n" +
            "§8▶ §cFire §7→ Extinguish\n\n" +
            "§7Gateway to most chains.\n" +
            "§7Always open with Water\n" +
            "§7on a dry target.\n\n" +
            "§8[8 / 37]";

        PAGE_CONTENT[8]  =
            "§6[MUDDY]\n" +
            "§8──────────────\n" +
            "§7Source:   §bWet §7+§2 Earth\n" +
            "§7Duration: §e15–30 sec\n" +
            "§7Effect:   §cSlowness IV\n\n" +
            "§7Opens combos:\n" +
            "§8▶ §cFire §7→ §eSTATUE\n" +
            "§8▶ §fAir §7→ §fMud Launch\n\n" +
            "§7Heavy mud coats target.\n" +
            "§7Set up STATUE for the\n" +
            "§7devastating Air kill.\n\n" +
            "§8[9 / 37]";

        PAGE_CONTENT[9]  =
            "§b[CHILLED]\n" +
            "§8──────────────\n" +
            "§7Source:   §bWet §7+§f Air\n" +
            "§7Duration: §e2.5 sec ONLY!\n" +
            "§7Effect:   §bSlowness II\n\n" +
            "§7Opens combos:\n" +
            "§8▶ §fAir §7→ §bFROZEN\n" +
            "§8▶ §2Earth §7→ §fCracked Ice\n\n" +
            "§7Window is very short!\n" +
            "§7Cast Air IMMEDIATELY after\n" +
            "§7to freeze the target.\n\n" +
            "§8[10 / 37]";

        PAGE_CONTENT[10] =
            "§b[FROZEN]\n" +
            "§8──────────────\n" +
            "§7Source:   §bChilled §7+§f Air\n" +
            "§7Duration: §e5 sec\n" +
            "§7Effect:   §cTotal freeze\n\n" +
            "§7Opens combos:\n" +
            "§8▶ §fAir §7→ §c§l☠ INSTANT DEATH\n" +
            "§8▶ §cFire §7→ Thaw Explosion\n\n" +
            "§7Target is frozen solid.\n" +
            "§7One Air gust SHATTERS them!\n\n" +
            "§8[11 / 37]";

        PAGE_CONTENT[11] =
            "§e[STATUE]\n" +
            "§8──────────────\n" +
            "§7Source:   §6Muddy §7+§c Fire\n" +
            "§7Duration: §e8 sec\n" +
            "§7Effect:   §cTotal freeze\n\n" +
            "§7Opens combos:\n" +
            "§8▶ §fAir §7→ §c§l☠ INSTANT DEATH\n" +
            "§8▶ §2Earth §7→ Crumble\n\n" +
            "§7Dirt hardens around target.\n" +
            "§7One Air gust CRUMBLES them!\n\n" +
            "§8[12 / 37]";

        PAGE_CONTENT[12] =
            "§c[SCORCHED]\n" +
            "§8──────────────\n" +
            "§7Source:   §cFire §7(dry target)\n" +
            "§7Duration: §e3 sec ONLY!\n" +
            "§7Effect:   Mild fire DoT\n\n" +
            "§7Opens combos:\n" +
            "§8▶ §cFire §7→ §cBLAZING\n" +
            "§8▶ §bWater §7→ Steam Burst\n" +
            "§8▶ §fAir §7→ Fanned Flames\n\n" +
            "§7Window very short!\n" +
            "§7Hit again fast!\n\n" +
            "§8[13 / 37]";

        PAGE_CONTENT[13] =
            "§c[BLAZING]\n" +
            "§8──────────────\n" +
            "§7Source:   §cScorched §7+§c Fire\n" +
            "§7Duration: §e5 sec\n" +
            "§7Effect:   Heavy fire DoT\n\n" +
            "§7Opens combos:\n" +
            "§8▶ §bWater §7→ §bSTEAM EXPLOSION\n" +
            "§8▶ §fAir §7→ §cINFERNO BLAST\n\n" +
            "§7Target engulfed in intense\n" +
            "§7flame. Biggest fire state!\n\n" +
            "§8[14 / 37]";

        PAGE_CONTENT[14] =
            "§5[MIND BOMB]\n" +
            "§8──────────────\n" +
            "§7Source:   Any hit\n" +
            "§8          §75% with 2+ Mage Gear\n" +
            "§7Duration: §e5 sec\n" +
            "§7Effect:   §5Nausea + Blindness\n\n" +
            "§730% chance of FALLEN.\n\n" +
            "§7Requires wearing 2 or more\n" +
            "§5Mage Gear §7pieces.\n" +
            "§8(Leather + Purple Dye\n" +
            "§8 + Blaze Powder)\n\n" +
            "§8[15 / 37]";

        PAGE_CONTENT[15] =
            "§c[FALLEN]\n" +
            "§8──────────────\n" +
            "§7Source:   §5Mind Bomb §7(30%)\n" +
            "§7Duration: §e3 sec (auto)\n" +
            "§7Effect:   §cCrawl pose\n\n" +
            "§7Recovery options:\n" +
            "§8▶ Press §fSPACE §7to get up\n" +
            "§8   instantly\n" +
            "§8▶ Auto-recovers after 3s\n\n" +
            "§7Target cannot fight back\n" +
            "§7effectively while fallen.\n\n" +
            "§8[16 / 37]";

        PAGE_CONTENT[16] =
            "§c➜ Combo: BLAZING\n" +
            "§8──────────────\n" +
            "§c1. Fire §7→ §cScorched\n" +
            "§c2. Fire §7→ §c§lBLAZING!\n\n" +
            "§7Result:\n" +
            "§8▶ Intense fire DoT (5s)\n" +
            "§8▶ High fire tick count\n" +
            "§8▶ +XP bonus\n\n" +
            "§7Follow up with Water for\n" +
            "§bSteam Explosion §7or Air for\n" +
            "§cInferno Blast!\n\n" +
            "§8[17 / 37]";

        PAGE_CONTENT[17] =
            "§b➜ Combo: Extinguish\n" +
            "§8──────────────\n" +
            "§b1. Water §7→ §bWet\n" +
            "§c2. Fire §7→ §bExtinguish!\n\n" +
            "§7Result:\n" +
            "§8▶ Fire put out (no damage)\n" +
            "§8▶ Steam burst visual\n\n" +
            "§7Counter-play: if you fire\n" +
            "§7hit a WET target, it\n" +
            "§7absorbs the fire instead\n" +
            "§7of burning them.\n\n" +
            "§8[18 / 37]";

        PAGE_CONTENT[18] =
            "§e➜ Combo: STATUE\n" +
            "§8──────────────\n" +
            "§b1. Water §7→ §bWet\n" +
            "§21. Earth §7→ §6Muddy\n" +
            "§c3. Fire §7→ §e§lSTATUE! (8s)\n\n" +
            "§7Result:\n" +
            "§8▶ 8 second total freeze\n" +
            "§8▶ §cAir §8= §c§lINSTANT DEATH!\n\n" +
            "§73-element combo.\n" +
            "§7Master move!\n\n" +
            "§8[19 / 37]";

        PAGE_CONTENT[19] =
            "§b➜ Combo: Thaw\n" +
            "§8──────────────\n" +
            "§b1. Water §7→ §bWet\n" +
            "§f2. Air §7→ §bChilled\n" +
            "§c3. Fire §7→ §bThaw!\n\n" +
            "§7Result:\n" +
            "§8▶ Chill removed\n" +
            "§8▶ Steam pop + damage\n\n" +
            "§7Stops the freeze chain\n" +
            "§7in exchange for burst\n" +
            "§7damage instead.\n\n" +
            "§8[20 / 37]";

        PAGE_CONTENT[20] =
            "§b➜ Combo: THAW EXPL\n" +
            "§8──────────────\n" +
            "§b1. Water §7→ §bWet\n" +
            "§f2. Air §7→ §bChill\n" +
            "§f3. Air §7→ §bFrozen\n" +
            "§c4. Fire §7→ §b§lTHAW EXPLOSION!\n\n" +
            "§7Result:\n" +
            "§8▶ AoE fire + steam\n" +
            "§8▶ High burst damage\n" +
            "§8▶ Area blast\n\n" +
            "§8[21 / 37]";

        PAGE_CONTENT[21] =
            "§b➜ Combo: Steam Burst\n" +
            "§8──────────────\n" +
            "§c1. Fire §7→ §cScorched\n" +
            "§b2. Water §7→ §b§lSteam Burst!\n\n" +
            "§7Result:\n" +
            "§8▶ Scorch removed early\n" +
            "§8▶ Bonus damage\n" +
            "§8▶ Steam explosion VFX\n\n" +
            "§7Quick 2-hit combo.\n" +
            "§7Great for fast DPS!\n\n" +
            "§8[22 / 37]";

        PAGE_CONTENT[22] =
            "§b➜ Combo: STEAM EXPL\n" +
            "§8──────────────\n" +
            "§c1. Fire §7→ §cScorched\n" +
            "§c2. Fire §7→ §cBlazing\n" +
            "§b3. Water §7→ §b§lSTEAM EXPLOSION!\n\n" +
            "§7Result:\n" +
            "§8▶ AoE knockback\n" +
            "§8▶ Maximum water damage\n" +
            "§8▶ Huge steam burst\n\n" +
            "§8[23 / 37]";

        PAGE_CONTENT[23] =
            "§b➜ Base: WET\n" +
            "§8──────────────\n" +
            "§bWater §7on dry target\n" +
            "§7= §b§lWET!\n\n" +
            "§7Always your opening move.\n" +
            "§7Opens the most combos:\n" +
            "§8▶ Earth → §6MUDDY\n" +
            "§8▶ Air → §bCHILLED\n" +
            "§8▶ Fire → Extinguish\n\n" +
            "§7Start every chain here.\n\n" +
            "§8[24 / 37]";

        PAGE_CONTENT[24] =
            "§6➜ Combo: MUDDY\n" +
            "§8──────────────\n" +
            "§b1. Water §7→ §bWet\n" +
            "§22. Earth §7→ §6§lMUDDY!\n\n" +
            "§7Result:\n" +
            "§8▶ Slowness IV (15–30s)\n" +
            "§8▶ Opens STATUE combo\n" +
            "§8▶ Opens Mud Launch\n\n" +
            "§7Classic 2-element setup.\n" +
            "§7Water → Earth, always!\n\n" +
            "§8[25 / 37]";

        PAGE_CONTENT[25] =
            "§b➜ Combo: Cracked Ice\n" +
            "§8──────────────\n" +
            "§b1. Water §7→ §bWet\n" +
            "§f2. Air §7→ §bChilled\n" +
            "§23. Earth §7→ §b§lCRACKED ICE!\n\n" +
            "§7Result:\n" +
            "§8▶ Blindness (3s)\n" +
            "§8▶ Slowness VI (5s)\n" +
            "§8▶ Bonus damage\n\n" +
            "§73-element debuffer!\n\n" +
            "§8[26 / 37]";

        PAGE_CONTENT[26] =
            "§6➜ Combo: Crumble\n" +
            "§8──────────────\n" +
            "§7Target must be in\n" +
            "§e§lSTATUE §7state, then:\n" +
            "§21. Earth §7→ §6§lCRUMBLE!\n\n" +
            "§7Result:\n" +
            "§8▶ Statue removed early\n" +
            "§8▶ Heavy bonus damage\n" +
            "§8▶ Dirt explosion VFX\n\n" +
            "§7Saves target from Air death\n" +
            "§7but deals massive damage.\n\n" +
            "§8[27 / 37]";

        PAGE_CONTENT[27] =
            "§c➜ ☠ SHATTERED\n" +
            "§8──────────────\n" +
            "§b1. Water §7→ §bWet\n" +
            "§f2. Air §7→ §bChilled\n" +
            "§f3. Air §7→ §bFROZEN\n" +
            "§f4. Air §7→ §c§l☠ SHATTERED!\n\n" +
            "§c§lINSTANT DEATH!\n\n" +
            "§7Requires 4 casts.\n" +
            "§7Time each window carefully!\n" +
            "§7Hardest freeze chain.\n\n" +
            "§8[28 / 37]";

        PAGE_CONTENT[28] =
            "§c➜ ☠ CRUMBLED\n" +
            "§8──────────────\n" +
            "§b1. Water §7→ §bWet\n" +
            "§22. Earth §7→ §6Muddy\n" +
            "§c3. Fire §7→ §eStatue\n" +
            "§f4. Air §7→ §c§l☠ CRUMBLED!\n\n" +
            "§c§lINSTANT DEATH!\n\n" +
            "§7Requires 4 casts.\n" +
            "§7Hardest combo in the game!\n\n" +
            "§8[29 / 37]";

        PAGE_CONTENT[29] =
            "§b➜ Combo: CHILLED\n" +
            "§8──────────────\n" +
            "§7Target must be §bWET§7, then:\n" +
            "§f1. Air §7→ §b§lCHILLED!\n\n" +
            "§7Result:\n" +
            "§8▶ Slowness II\n" +
            "§8▶ Only 2.5 sec window!\n" +
            "§8▶ Opens FROZEN chain\n\n" +
            "§7Cast Air again IMMEDIATELY!\n" +
            "§7Don't wait!\n\n" +
            "§8[30 / 37]";

        PAGE_CONTENT[30] =
            "§b➜ Combo: FROZEN\n" +
            "§8──────────────\n" +
            "§7Target must be §bCHILLED§7, then:\n" +
            "§f1. Air §7→ §b§lFROZEN!\n\n" +
            "§7Result:\n" +
            "§8▶ Total freeze (5s)\n" +
            "§8▶ Opens Air instant kill\n" +
            "§8▶ Opens Thaw Explosion\n\n" +
            "§7One more Air gust\n" +
            "§7SHATTERS them!\n\n" +
            "§8[31 / 37]";

        PAGE_CONTENT[31] =
            "§f➜ Combo: Mud Launch\n" +
            "§8──────────────\n" +
            "§7Target must be §6MUDDY§7, then:\n" +
            "§f1. Air §7→ §f§lMUD LAUNCH!\n\n" +
            "§7Result:\n" +
            "§8▶ Massive upward KB\n" +
            "§8▶ Scales with Magic level\n" +
            "§8▶ Fall damage!\n\n" +
            "§7Catapults the target\n" +
            "§7skyward. Near cliffs = deadly.\n\n" +
            "§8[32 / 37]";

        PAGE_CONTENT[32] =
            "§c➜ Combo: INFERNO\n" +
            "§8──────────────\n" +
            "§c1. Fire §7→ §cScorched\n" +
            "§c2. Fire §7→ §cBlazing\n" +
            "§f3. Air §7→ §c§l🔥 INFERNO BLAST!\n\n" +
            "§7Result:\n" +
            "§8▶ Massive fire knockback\n" +
            "§8▶ Intense fire DoT\n" +
            "§8▶ Max fire damage\n\n" +
            "§7Best pure-fire kill chain!\n\n" +
            "§8[33 / 37]";

        PAGE_CONTENT[33] =
            "§c➜ Combo: Fan Flames\n" +
            "§8──────────────\n" +
            "§7Target must be §cSCORCHED§7, then:\n" +
            "§f1. Air §7→ §c§lFanned Flames!\n\n" +
            "§7Result:\n" +
            "§8▶ Extended fire ticks\n" +
            "§8▶ More fire duration\n" +
            "§8▶ Bonus fire damage\n\n" +
            "§7Quick 2-hit combo.\n" +
            "§7Fire → Air is fast!\n\n" +
            "§8[34 / 37]";

        PAGE_CONTENT[34] =
            "§5✦ Mage Gear\n" +
            "§8──────────────\n" +
            "§7Craft leather armor with:\n" +
            "§8  Leather piece\n" +
            "§8+ §5Purple Dye\n" +
            "§8+ §6Blaze Powder\n\n" +
            "§7Bonus per piece:\n" +
            "§8▶ §7−250ms cast cooldown\n\n" +
            "§7With 2+ pieces:\n" +
            "§8▶ §55% Mind Bomb chance\n" +
            "§8   on every hit!\n\n" +
            "§8[35 / 37]";

        PAGE_CONTENT[35] =
            "§7Cooldown Formula\n" +
            "§8──────────────\n" +
            "§7Base:     §e3000ms\n" +
            "§7By level: §7−(Lv/99)×2000ms\n" +
            "§7Per gear: §7−250ms each\n" +
            "§7Minimum:  §e500ms\n\n" +
            "§7Lv 99 + 4 Mage Gear:\n" +
            "§e500ms cooldown!\n" +
            "§7That's 2 casts/second!\n" +
            "§7Chain combos very fast.\n\n" +
            "§8[36 / 37]";

        PAGE_CONTENT[36] =
            "§d✦ Advanced Tips\n" +
            "§8──────────────\n" +
            "§7▶ Level Magic via casting\n" +
            "§7  and landing hits\n" +
            "§7▶ Combos grant §d+25 XP\n" +
            "§7▶ Air needs no rune —\n" +
            "§7  always available!\n" +
            "§7▶ Open with Water for\n" +
            "§7  the best chains\n" +
            "§7▶ Freeze chain (4 hits)\n" +
            "§7  is deadliest!\n" +
            "§7▶ Find more §dSpell Pages!\n\n" +
            "§8[37 / 37]";
    }

    // ─────────────────────────────────────────────────────────────────────────

    public SpellBookManager(JavaPlugin plugin) {
        this.plugin       = plugin;
        this.spellTomeKey = new NamespacedKey(plugin, SPELL_TOME_KEY);
        this.spellPageKey = new NamespacedKey(plugin, SPELL_PAGE_KEY);
        load();
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void load() {
        plugin.getDataFolder().mkdirs();
        dataFile = new File(plugin.getDataFolder(), "spellbook_data.yml");
        dataCfg  = YamlConfiguration.loadConfiguration(dataFile);

        var pagesSection = dataCfg.getConfigurationSection("pages");
        if (pagesSection != null) {
            for (String key : pagesSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    List<Integer> list = dataCfg.getIntegerList("pages." + key);
                    unlockedPages.put(uuid, new HashSet<>(list));
                } catch (IllegalArgumentException ignored) { }
            }
        }
    }

    public void save() {
        dataCfg.set("pages", null); // wipe old entries
        for (var entry : unlockedPages.entrySet()) {
            dataCfg.set("pages." + entry.getKey().toString(),
                    new ArrayList<>(entry.getValue()));
        }
        try {
            dataCfg.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save spellbook data!", e);
        }
    }

    // ── Page management ───────────────────────────────────────────────────────

    public Set<Integer> getUnlockedPages(UUID uuid) {
        return unlockedPages.getOrDefault(uuid, Collections.emptySet());
    }

    public int getUnlockedCount(UUID uuid) {
        return unlockedPages.getOrDefault(uuid, Collections.emptySet()).size();
    }

    public boolean allUnlocked(UUID uuid) {
        return getUnlockedCount(uuid) >= TOTAL_PAGES;
    }

    /**
     * Unlocks a random locked page for the given player.
     *
     * @return the 1-indexed page number that was unlocked, or {@code -1} if all pages
     *         are already unlocked.
     */
    public int discoverRandomPage(UUID uuid) {
        Set<Integer> unlocked = unlockedPages.computeIfAbsent(uuid, k -> new HashSet<>());

        List<Integer> locked = new ArrayList<>();
        for (int i = 0; i < TOTAL_PAGES; i++) {
            if (!unlocked.contains(i)) locked.add(i);
        }
        if (locked.isEmpty()) return -1;

        int idx = locked.get(new Random().nextInt(locked.size()));
        unlocked.add(idx);
        save();
        return idx + 1; // 1-indexed for display
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    /** The carryable Arcane Tome item. Right-clicking opens the book view. */
    public ItemStack buildSpellTomeItem() {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§5✦ The Arcane Tome");
            meta.setLore(List.of(
                "§8" + "─".repeat(22),
                "§7A grimoire of elemental magic.",
                "§7Collect §dSpell Pages §7to unlock",
                "§7hidden combo knowledge.",
                "§8" + "─".repeat(22),
                "§8Right-click to read.",
                "§8" + "─".repeat(22),
                "§8[DifficultyEngine — Spell Tome]"
            ));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer()
                    .set(spellTomeKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isSpellTome(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                   .has(spellTomeKey, PersistentDataType.BYTE);
    }

    /** A single-use consumable that unlocks one random page in the tome. */
    public ItemStack buildSpellPageItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§d✧ Spell Page");
            meta.setLore(List.of(
                "§8" + "─".repeat(22),
                "§7A page torn from an ancient",
                "§7arcane grimoire.",
                "§8" + "─".repeat(22),
                "§8Right-click to absorb — unlocks",
                "§8one random §dSpell Tome §8page.",
                "§8" + "─".repeat(22),
                "§8[DifficultyEngine — Spell Page]"
            ));
            meta.getPersistentDataContainer()
                    .set(spellPageKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isSpellPage(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                   .has(spellPageKey, PersistentDataType.BYTE);
    }

    // ── Book rendering ────────────────────────────────────────────────────────

    /**
     * Builds a {@link Material#WRITTEN_BOOK} with all 37 pages.
     * Unlocked pages show the real content; locked ones show "???".
     * Pass to {@link org.bukkit.entity.Player#openBook(ItemStack)} — the book
     * is never given to the player, only shown as a UI.
     */
    public ItemStack buildBookForPlayer(UUID uuid) {
        Set<Integer> unlocked = unlockedPages.getOrDefault(uuid, Collections.emptySet());

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) return book;

        meta.setTitle("The Arcane Tome");
        meta.setAuthor("Ancient Mage");
        meta.setGeneration(BookMeta.Generation.ORIGINAL);

        for (int i = 0; i < TOTAL_PAGES; i++) {
            if (unlocked.contains(i)) {
                meta.addPage(PAGE_CONTENT[i]);
            } else {
                meta.addPage(
                    "§8[Page " + (i + 1) + " / " + TOTAL_PAGES + "]\n\n" +
                    "§7???\n\n" +
                    "§8This page has not\n" +
                    "§8been discovered yet.\n\n" +
                    "§8Find a §dSpell Page\n" +
                    "§8dropped by hostile\n" +
                    "§8mobs to unlock it."
                );
            }
        }

        book.setItemMeta(meta);
        return book;
    }
}
