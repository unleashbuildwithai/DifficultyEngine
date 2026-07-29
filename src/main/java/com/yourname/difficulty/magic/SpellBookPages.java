package com.yourname.difficulty.magic;

/**
 * Static page content for the Arcane Tome (see {@link SpellBookManager}).
 * Extracted purely to keep {@code SpellBookManager} under the 400-line limit —
 * holds no state/logic of its own. Pages 0-22 live here; pages 23-44 live in
 * {@link SpellBookPages2} (split further to stay under 400 lines each).
 */
final class SpellBookPages {

    static final String[] PAGE_CONTENT = new String[SpellBookManager.TOTAL_PAGES];

    private SpellBookPages() {}

    static {
        PAGE_CONTENT[0]  =
            "§5The Arcane Tome\n" +
            "§8──────────────\n" +
            "§7A grimoire of elemental\n" +
            "§7magic, combos and secrets.\n\n" +
            "§7Find §dSpell Pages §7dropped\n" +
            "§7by hostile mobs to unlock\n" +
            "§7each chapter.\n\n" +
            "§7Every combo/status chain has\n" +
            "§7a base §f15%§7 chance to proc\n" +
            "§7and display its hint on a\n" +
            "§7matching basic hit.\n\n" +
            "§e⭐ Favoriting §7a chain (via the\n" +
            "§7Tome's Favorites menu) adds\n" +
            "§7§a+15%§7 to that chance §8(30%\n" +
            "§8total)§7 — so star the combos\n" +
            "§7you rely on most!\n\n" +
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

        SpellBookPages2.load();
    }
}
