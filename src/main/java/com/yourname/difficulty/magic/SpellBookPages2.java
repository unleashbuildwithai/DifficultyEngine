package com.yourname.difficulty.magic;

/**
 * Continuation of {@link SpellBookPages} — pages 23-44 of the Arcane Tome.
 * Split purely to keep each file under the 400-line limit; holds no
 * state/logic of its own.
 */
final class SpellBookPages2 {

    private SpellBookPages2() {}

    static void load() {
        String[] PAGE_CONTENT = SpellBookPages.PAGE_CONTENT;

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
            "§8[37 / 45]";

        // ── Mage Gear visual craft guides (pages 38-41) ──────────────────────

        PAGE_CONTENT[37] =
            "§9Apprentice Gear §8(Lv 1)\n" +
            "§8─────────────────\n" +
            "§7 Hood      Top\n" +
            "§8[§7L§8+§5◆§8+§7~§8]  [§7L§8+§5◆§8+§7~§8]\n" +
            "§9  Hood      Top\n\n" +
            "§7 Legs     Boots\n" +
            "§8[§7L§8+§5◆§8+§7~§8]  [§7L§8+§5◆§8+§7~§8]\n" +
            "§9  Legs    Boots\n" +
            "§8─────────────────\n" +
            "§7L §8= Leather piece\n" +
            "§5◆ §8= Purple Dye\n" +
            "§7~ §8= String\n" +
            "§8Bonus: §e−100ms §8CD/piece\n" +
            "§8[38 / 45]";

        PAGE_CONTENT[38] =
            "§5Mage Gear §8(Lv 30)\n" +
            "§8─────────────────\n" +
            "§7 Hood      Top\n" +
            "§8[§7L§8+§5◆§8+§6⚗§8]  [§7L§8+§5◆§8+§6⚗§8]\n" +
            "§5  Hood      Top\n\n" +
            "§7 Legs     Boots\n" +
            "§8[§7L§8+§5◆§8+§6⚗§8]  [§7L§8+§5◆§8+§6⚗§8]\n" +
            "§5  Legs    Boots\n" +
            "§8─────────────────\n" +
            "§7L §8= Leather piece\n" +
            "§5◆ §8= Purple Dye\n" +
            "§6⚗ §8= Blaze Powder\n" +
            "§8Bonus: §e−250ms §8CD/piece\n" +
            "§8[39 / 45]";

        PAGE_CONTENT[39] =
            "§bAlch Gear §8(Lv 60)\n" +
            "§8─────────────────\n" +
            "§7 Hood      Top\n" +
            "§8[§7L§8+§9◆§8+§6⚗§8+§9E§8]\n" +
            "§b  Hood      Top\n\n" +
            "§7 Legs     Boots\n" +
            "§8[§7L§8+§9◆§8+§6⚗§8+§9E§8]\n" +
            "§b  Legs    Boots\n" +
            "§8─────────────────\n" +
            "§7L §8= Leather piece\n" +
            "§9◆ §8= Blue Dye\n" +
            "§6⚗ §8= Blaze Powder\n" +
            "§9E §8= Eye of Ender\n" +
            "§8Bonus: §e−350ms §8CD/piece\n" +
            "§8[40 / 45]";

        PAGE_CONTENT[40] =
            "§4Master Gear §8(Lv 90)\n" +
            "§8─────────────────\n" +
            "§7 Hood      Top\n" +
            "§8[§7L§8+§8◆§8+§6⚗§8+§5S§8+§4D§8]\n" +
            "§4  Hood      Top\n\n" +
            "§7 Legs     Boots\n" +
            "§8[§7L§8+§8◆§8+§6⚗§8+§5S§8+§4D§8]\n" +
            "§4  Legs    Boots\n" +
            "§8─────────────────\n" +
            "§7L §8= Leather piece\n" +
            "§8◆ §8= Black Dye\n" +
            "§6⚗ §8= Blaze Powder\n" +
            "§5S §8= Enchanted Shard\n" +
            "§4D §8= Dragon Breath\n" +
            "§8Bonus: §e−500ms §8CD/piece\n" +
            "§8[41 / 45]";

        // ── Elemental Proc pages (42-45) — passive on-hit procs ──────────────
        // Each proc is a REAL dice-roll effect (not just a hint) that can fire
        // on ANY basic hit with the matching element, independent of combo
        // chains. Requires the page below to be unlocked AND the listed
        // Magic level, AND (for the boosted 30% rate) the chain favorited via
        // the Arcane Tome's Favorites menu.

        PAGE_CONTENT[41] =
            "§c🔥 Fire Proc: IGNITE\n" +
            "§8──────────────\n" +
            "§7Requires: §eMagic Lv 20+\n" +
            "§7Chance:   §f15%§7 §8(30% ⭐)\n\n" +
            "§7On ANY basic Fire hit, a\n" +
            "§7chance to instantly set the\n" +
            "§7target ablaze — separate\n" +
            "§7from the Scorched combo.\n\n" +
            "§7Passive. No combo needed.\n\n" +
            "§8[42 / 45]";

        PAGE_CONTENT[42] =
            "§b💧 Water Proc: DOUSE\n" +
            "§8──────────────\n" +
            "§7Requires: §eMagic Lv 20+\n" +
            "§7Chance:   §f15%§7 §8(30% ⭐)\n\n" +
            "§7On ANY basic Water hit, a\n" +
            "§7chance to fully extinguish\n" +
            "§7the target AND apply a\n" +
            "§7short chilling slow.\n\n" +
            "§7Passive. No combo needed.\n\n" +
            "§8[43 / 45]";

        PAGE_CONTENT[43] =
            "§2🌿 Earth Proc: STUN\n" +
            "§8──────────────\n" +
            "§7Requires: §eMagic Lv 35+\n" +
            "§7Chance:   §f15%§7 §8(30% ⭐)\n\n" +
            "§7On ANY basic Earth hit, a\n" +
            "§7chance to root the target\n" +
            "§7in place — heavy Slowness\n" +
            "§7+ Mining Fatigue briefly.\n\n" +
            "§7Passive. No combo needed.\n\n" +
            "§8[44 / 45]";

        PAGE_CONTENT[44] =
            "§f💨 Air Proc: BURST\n" +
            "§8──────────────\n" +
            "§7Requires: §eMagic Lv 50+\n" +
            "§7Chance:   §f15%§7 §8(30% ⭐)\n\n" +
            "§7On ANY basic Air hit, a\n" +
            "§7chance to unleash a bonus\n" +
            "§7knockback burst, hurling\n" +
            "§7the target further back.\n\n" +
            "§7Passive. No combo needed.\n\n" +
            "§8[45 / 45]";
    }
}
