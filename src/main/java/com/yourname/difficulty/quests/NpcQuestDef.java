package com.yourname.difficulty.quests;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/**
 * NpcQuestDef — immutable data record for one NPC-gated quest.
 *
 * Main quests  (id 1–150)  count toward the Quest Skill Cape.
 * Secret quests (id 151–300) are hidden (show as ??? in /questbook) and
 * count toward the Boss Quest Cape (fire-particle aura) when all 150 are done.
 *
 * Requirements:
 *   Kill quest   — player must have accumulated ≥ killCount kills of killTarget
 *   Collect quest — player must have ≥ collectCount of collectItem in inventory
 *                   (items are consumed on completion)
 *
 * Hidden trigger (optional):
 *   If hiddenItem is non-null and the player has ≥ hiddenCount of that item,
 *   those items are consumed and bonusGold is awarded on top of baseGold.
 *
 * Extra mechanic:
 *   requireSneak — player must be crouching to turn in the quest.
 *   This is the core "secret" interaction mechanic for many hidden quests.
 */
public final class NpcQuestDef {

    public final int        id;
    public final String     title;
    public final String     npcName;
    public final boolean    secret;       // hidden in /questbook until discovered
    public final String     dimension;   // "world" / "world_nether" / "world_the_end"

    // ── Requirement (exactly one branch is non-null) ──────────────────────────
    public final EntityType killTarget;   // non-null → kill quest
    public final int        killCount;
    public final Material   collectItem;  // non-null → collect quest
    public final int        collectCount;

    // ── Optional hidden trigger ───────────────────────────────────────────────
    public final Material   hiddenItem;
    public final int        hiddenCount;

    // ── Rewards ───────────────────────────────────────────────────────────────
    public final long  baseGold;
    public final long  bonusGold;   // extra if hidden trigger fires

    // ── Sneak requirement ─────────────────────────────────────────────────────
    public final boolean requireSneak;

    // ── Private constructor (use builders below) ──────────────────────────────
    private NpcQuestDef(B b) {
        id = b.id; title = b.t; npcName = b.n; secret = b.s; dimension = b.d;
        killTarget = b.mob; killCount = b.kc;
        collectItem = b.item; collectCount = b.ic;
        hiddenItem = b.hi; hiddenCount = b.hc;
        baseGold = b.gold; bonusGold = b.bonus;
        requireSneak = b.sneak;
    }

    public boolean isKillQuest()      { return killTarget  != null; }
    public boolean isCollectQuest()   { return collectItem != null; }
    public boolean hasHiddenTrigger() { return hiddenItem  != null; }

    // ── Fluent factory methods ─────────────────────────────────────────────────

    /** Start a kill-count quest definition. */
    public static B kill(int id, String title, String npc, String dim,
                         EntityType mob, int count, long gold) {
        B b = new B(id, title, npc, dim);
        b.mob = mob; b.kc = count; b.gold = gold;
        return b;
    }

    /** Start a collect-items quest definition. */
    public static B collect(int id, String title, String npc, String dim,
                            Material item, int count, long gold) {
        B b = new B(id, title, npc, dim);
        b.item = item; b.ic = count; b.gold = gold;
        return b;
    }

    // ── Builder ────────────────────────────────────────────────────────────────

    public static final class B {
        int id; String t, n, d;
        boolean s = false, sneak = false;
        EntityType mob = null; int kc = 0;
        Material item = null; int ic = 0;
        Material hi = null; int hc = 0;
        long gold = 0, bonus = 0;

        B(int id, String t, String n, String d) {
            this.id = id; this.t = t; this.n = n; this.d = d;
        }

        /** Add a hidden inventory trigger that awards bonus gold and consumes items. */
        public B hidden(Material item, int count, long bonus) {
            hi = item; hc = count; this.bonus = bonus; return this;
        }

        /** Mark this quest as a secret (hidden in GUI until discovered). */
        public B secret() { s = true; return this; }

        /** Player must be sneaking to turn in this quest. */
        public B sneak() { sneak = true; return this; }

        public NpcQuestDef build() { return new NpcQuestDef(this); }
    }
}
