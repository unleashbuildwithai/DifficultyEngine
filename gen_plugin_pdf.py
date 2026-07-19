# gen_plugin_pdf.py  -- DifficultyEngine Plugin Architecture & Documentation
# Generates:  C:/Users/Owner/Desktop/DifficultyEngine_Architecture.pdf
# Run:        python gen_plugin_pdf.py
# Requires:   pip install fpdf2

from fpdf import FPDF
import os

OUTPUT = r"C:\Users\Owner\Desktop\DifficultyEngine_Architecture.pdf"

# ── PDF subclass ──────────────────────────────────────────────────────────────

class Doc(FPDF):
    NAVY  = (20,  30,  80)
    WHITE = (255, 255, 255)
    GOLD  = (200, 160,   0)
    DARK  = (30,  30,  30)

    # ── Sanitise Unicode chars that Helvetica (latin-1) cannot handle ─────────
    def normalize_text(self, txt):
        clean = (str(txt)
            .replace('\u2013', '-')   # en-dash
            .replace('\u2014', '--')  # em-dash
            .replace('\u2190', '<-')  # left arrow
            .replace('\u2191', '^')   # up arrow
            .replace('\u2192', '->')  # right arrow
            .replace('\u2193', 'v')   # down arrow
            .replace('\u2265', '>=')  # >=
            .replace('\u2264', '<=')  # <=
            .replace('\u2026', '...') # ellipsis
            .replace('\u2500', '-')   # box horizontal
            .replace('\u2502', '|')   # box vertical
            .replace('\u251c', '+')   # box T-right
            .replace('\u2514', '+')   # box corner
            .replace('\u2518', '+')   # box corner
            .replace('\u2022', '*')   # bullet
            .replace('\u00d7', 'x')   # multiply sign
            .replace('\u2713', 'ok')  # check mark
            .replace('\u2714', 'ok')
            .replace('\u2718', 'x')
            .replace('\u2260', '!=')
            .replace('\u2248', '~=')
            .replace('\u221e', 'inf')
            .replace('\u00b0', 'deg') # degree
            .replace('\u2716', 'x')
            .replace('\u2776', '(1)')
        )
        # Final catch-all: any remaining non-latin-1 character becomes '?'
        clean = clean.encode('latin-1', errors='replace').decode('latin-1')
        return super().normalize_text(clean)

    def header(self):
        self.set_fill_color(*self.NAVY)
        self.rect(0, 0, 210, 16, 'F')
        self.set_font("Helvetica", "B", 10)
        self.set_text_color(*self.WHITE)
        self.set_xy(0, 3)
        self.cell(0, 10, "DifficultyEngine  |  Paper 1.21 Plugin  |  Architecture Reference", align="C")
        self.set_text_color(0, 0, 0)

    def footer(self):
        self.set_y(-13)
        self.set_font("Helvetica", "I", 8)
        self.set_text_color(120, 120, 120)
        self.cell(0, 8, f"DifficultyEngine Architecture  —  Page {self.page_no()}", align="C")

    def h1(self, txt):
        self.set_fill_color(*self.NAVY)
        self.set_text_color(*self.WHITE)
        self.set_font("Helvetica", "B", 13)
        self.cell(0, 10, f"  {txt}", ln=True, fill=True)
        self.set_text_color(0, 0, 0)
        self.ln(3)

    def h2(self, txt):
        self.set_font("Helvetica", "B", 11)
        self.set_text_color(20, 60, 160)
        self.cell(0, 8, txt, ln=True)
        self.set_text_color(0, 0, 0)
        self.ln(1)

    def h3(self, txt):
        self.set_font("Helvetica", "B", 10)
        self.set_text_color(60, 60, 60)
        self.cell(0, 7, txt, ln=True)
        self.set_text_color(0, 0, 0)

    def body(self, txt):
        self.set_font("Helvetica", "", 10)
        self.multi_cell(0, 6, txt)
        self.ln(2)

    def bullet(self, items):
        self.set_font("Helvetica", "", 10)
        for item in items:
            self.set_x(self.l_margin + 4)
            self.cell(6, 6, chr(149))
            self.multi_cell(0, 6, item)
        self.ln(2)

    def code(self, lines, note=""):
        self.set_fill_color(24, 24, 38)
        self.set_text_color(100, 220, 100)
        self.set_font("Courier", "", 9)
        x, y = self.get_x(), self.get_y()
        h = len(lines) * 5.5 + 6
        self.rect(x, y, 182, h, 'F')
        self.set_xy(x + 3, y + 3)
        for line in lines:
            self.cell(0, 5.5, line, ln=True)
            self.set_x(x + 3)
        self.set_text_color(0, 0, 0)
        if note:
            self.set_font("Helvetica", "I", 8.5)
            self.set_text_color(80, 80, 80)
            self.cell(0, 5, "  " + note, ln=True)
            self.set_text_color(0, 0, 0)
        self.ln(3)

    def info_box(self, label, txt, kind="note"):
        colours = {
            "note":    ((220, 240, 255), (0, 100, 200)),
            "tip":     ((220, 255, 220), (0, 130, 60)),
            "warn":    ((255, 230, 230), (180, 0, 0)),
            "quest":   ((255, 245, 215), (160, 110, 0)),
        }
        bg, border = colours.get(kind, colours["note"])
        self.set_fill_color(*bg)
        self.set_draw_color(*border)
        x, y = self.get_x(), self.get_y()
        approx_h = max(14, len(txt) // 80 * 6 + 14)
        self.rect(x, y, 182, approx_h, 'FD')
        self.set_xy(x + 3, y + 2)
        self.set_font("Helvetica", "B", 10)
        self.set_text_color(*border)
        self.cell(0, 6, label, ln=True)
        self.set_x(x + 3)
        self.set_font("Helvetica", "", 9.5)
        self.set_text_color(0, 0, 0)
        self.multi_cell(176, 5.5, txt)
        self.ln(4)

    def two_col_table(self, headers, rows, widths=(80, 102)):
        self.set_fill_color(*self.NAVY)
        self.set_text_color(*self.WHITE)
        self.set_font("Helvetica", "B", 10)
        for h, w in zip(headers, widths):
            self.cell(w, 8, f" {h}", border=1, fill=True)
        self.ln()
        self.set_text_color(0, 0, 0)
        fill = False
        for row in rows:
            self.set_fill_color(235, 240, 255) if fill else self.set_fill_color(255, 255, 255)
            self.set_font("Helvetica", "", 9.5)
            for cell, w in zip(row, widths):
                self.cell(w, 7, f" {cell}", border=1, fill=True)
            self.ln()
            fill = not fill
        self.ln(3)

    def three_col_table(self, headers, rows, widths=(55, 65, 62)):
        self.set_fill_color(*self.NAVY)
        self.set_text_color(*self.WHITE)
        self.set_font("Helvetica", "B", 9)
        for h, w in zip(headers, widths):
            self.cell(w, 7, f" {h}", border=1, fill=True)
        self.ln()
        self.set_text_color(0, 0, 0)
        fill = False
        for row in rows:
            self.set_fill_color(235, 240, 255) if fill else self.set_fill_color(255, 255, 255)
            self.set_font("Helvetica", "", 8.5)
            for cell, w in zip(row, widths):
                self.cell(w, 6, f" {cell}", border=1, fill=True)
            self.ln()
            fill = not fill
        self.ln(3)


# ── Build document ─────────────────────────────────────────────────────────────

pdf = Doc()
pdf.set_auto_page_break(auto=True, margin=16)
pdf.set_margins(13, 20, 13)
pdf.add_page()

# ── COVER ──────────────────────────────────────────────────────────────────────
pdf.set_font("Helvetica", "B", 26)
pdf.set_text_color(20, 30, 80)
pdf.ln(12)
pdf.cell(0, 16, "DifficultyEngine", ln=True, align="C")
pdf.set_font("Helvetica", "", 16)
pdf.set_text_color(60, 60, 60)
pdf.cell(0, 10, "Plugin Architecture & Developer Reference", ln=True, align="C")
pdf.set_font("Helvetica", "", 11)
pdf.cell(0, 8,  "Paper 1.21  |  Java 21  |  2026", ln=True, align="C")
pdf.ln(4)
pdf.set_draw_color(20, 30, 80)
pdf.set_line_width(0.8)
pdf.line(13, pdf.get_y(), 197, pdf.get_y())
pdf.ln(6)
pdf.set_font("Helvetica", "", 10)
pdf.set_text_color(40, 40, 40)
pdf.multi_cell(0, 6,
    "DifficultyEngine is a comprehensive Minecraft Paper 1.21 server plugin that layers an "
    "OSRS-inspired skill system, difficulty scaling, custom gear, magic staffs, quest system "
    "(300 NPC quests across three dimensions), gold currency, party system, and cape cosmetics "
    "onto a vanilla survival experience. This document covers the full package layout, every "
    "major system, admin commands, data files, and in-game mechanics.")
pdf.ln(6)

# Table of Contents
pdf.set_font("Helvetica", "B", 11)
pdf.set_text_color(20, 30, 80)
pdf.cell(0, 7, "Table of Contents", ln=True)
toc = [
    ("1",  "Folder & Package Structure"),
    ("2",  "Core Systems Overview"),
    ("3",  "Skill System  (8 Skills, Lv 1–99)"),
    ("4",  "Quest System  (300 NPC Quests)"),
    ("5",  "Gear System   (Melee / Ranged / Mage)"),
    ("6",  "Magic System  (Staffs, Runes, Spells)"),
    ("7",  "Gold Currency & VIP Shop"),
    ("8",  "Cape System   (Skill Cape, Boss Quest Cape)"),
    ("9",  "Party & Difficulty Scaling"),
    ("10", "Admin Commands Reference"),
    ("11", "Data Files & Persistence"),
    ("12", "Plugin.yml  — Commands & Permissions"),
    ("13", "Quest Registry  — All 300 Quests Summary"),
]
pdf.set_font("Helvetica", "", 10)
pdf.set_text_color(0, 0, 0)
for num, title in toc:
    pdf.set_x(16)
    pdf.cell(10, 6, f"{num}.")
    pdf.cell(0, 6, title, ln=True)
pdf.ln(4)

# ── SECTION 1: Folder Layout ──────────────────────────────────────────────────
pdf.add_page()
pdf.h1("1  Folder & Package Structure")
pdf.body("All Java source lives under src/main/java/com/yourname/difficulty/.")
pdf.code([
    "DifficultyEngine/",
    "  plugin.yml                          # Commands, permissions, api-version: 1.21",
    "  pom.xml                             # Maven build — Paper 1.21 dependency",
    "  src/main/java/com/yourname/difficulty/",
    "    Main.java                         # Plugin entry point — onEnable / onDisable",
    "    ─── bag/         Magic Bag (4-slot expandable storage GUI)",
    "    ─── currency/    Gold coin economy (GoldManager, drop/value listeners)",
    "    ─── gui/         RegistryGUI, CapeSlotGUI (wardrobe)",
    "    ─── items/       ItemFactory, MageGearTier, MeleeGearTier, RangedGearTier",
    "    ─── listeners/   Combat, crafting, equip, glow, cape visual, boss event …",
    "    ─── magic/       Elemental staffs (FIRE/WATER/EARTH/AIR), runes, spells",
    "    ─── party/       PartyManager, PartyListener, PartyHudTask (scoreboard)",
    "    ─── quests/      QuestManager (legacy), NPC quest system (300 quests)",
    "    ─── skills/      SkillManager, SkillType, SkillLevel (XP tables)",
    "    ─── trade/       Player-to-player trade UI",
    "    ─── vip/         VIP Shop (villager NPC, cosmetics)",
    "  DifficultyEngine-RP/                # Optional resource pack (custom textures)",
])
pdf.h2("Key Files by Package")
pdf.three_col_table(
    ["Package", "Key File(s)", "Purpose"],
    [
        ("root",     "Main.java",                "Plugin bootstrap, register all listeners/tasks"),
        ("skills",   "SkillManager.java",        "XP storage (skills.yml), addXp, setLevel, getLevel"),
        ("skills",   "SkillType.java (enum)",    "8 skills: MELEE RANGED DEFENCE PRAYER MAGIC WC FISH FARM"),
        ("skills",   "SkillLevel.java",          "Exact OSRS XP table, getLevelForXp, getXpForLevel"),
        ("skills",   "SkillLvlCommand.java",     "/skilllvl admin command + tab-complete"),
        ("quests",   "NpcQuestRegistry.java",    "All 300 quest definitions (static, immutable)"),
        ("quests",   "NpcQuestManager.java",     "Kill counts, completion tracking, cape awards"),
        ("quests",   "NpcQuestSpawner.java",     "Villager NPC spawning + /questnpc command"),
        ("quests",   "NpcQuestListener.java",    "Right-click NPC -> check reqs -> award"),
        ("quests",   "BossQuestCapeTask.java",   "Fire-ring particle aura for Boss Quest Cape"),
        ("items",    "ItemFactory.java",         "Builds all PDC-tagged custom items"),
        ("magic",    "MagicStaffListener.java",  "Casting, rune consumption, cooldowns"),
        ("listeners","MagicGlowTask.java",       "Element particles — Lv99 Magic only"),
        ("listeners","CapeVisualTask.java",       "Cape particle effects + hologram stand"),
    ],
    widths=(38, 58, 86)
)

# ── SECTION 2: Core Systems ───────────────────────────────────────────────────
pdf.add_page()
pdf.h1("2  Core Systems Overview")
pdf.body(
    "DifficultyEngine is structured around independent systems that communicate through "
    "shared managers injected via constructor. No static singletons — all state lives in "
    "the manager objects held by Main.java."
)
pdf.h2("Dependency Graph (simplified)")
pdf.code([
    "Main.java",
    "  ├─ SkillManager ──────────────────── skills.yml",
    "  ├─ GoldManager  ──────────────────── gold.yml",
    "  ├─ NpcQuestManager ────────────────── npcquest_kills.yml  npcquest_progress.yml",
    "  ├─ NpcQuestSpawner ────────────────── npc_positions.yml",
    "  ├─ QuestManager (legacy) ─────────── questdata.yml",
    "  ├─ PlayerDifficultyManager ────────── difficulty.yml",
    "  ├─ SpellBookManager ───────────────── spellbook.yml",
    "  ├─ MagicBagManager ────────────────── magicbag.yml",
    "  ├─ PartyManager  (in-memory)",
    "  └─ SkillCapeManager + CapeDataManager cape.yml",
])
pdf.h2("Task Schedule")
pdf.two_col_table(
    ["Task", "Interval"],
    [
        ("NightmareSpawnTask   — bonus mob spawning",       "every 300 ticks (15 s)"),
        ("PartyHudTask         — scoreboard HUD refresh",   "every 20 ticks (1 s)"),
        ("CapeVisualTask       — cape particles + hologram", "every 10 ticks (0.5 s)"),
        ("BossQuestCapeTask    — fire-ring aura",           "every 10 ticks (0.5 s)"),
        ("MagicGlowTask        — staff glow (Lv99 only)",   "every 4 ticks (0.2 s)"),
        ("NPC restore (once)   — respawn missing NPCs",     "60 ticks after startup"),
    ]
)

# ── SECTION 3: Skill System ───────────────────────────────────────────────────
pdf.add_page()
pdf.h1("3  Skill System  (8 Skills, Level 1–99)")
pdf.body(
    "The skill system mirrors Old School RuneScape. Each skill uses the exact OSRS XP table. "
    "Players gain XP by performing in-game actions; the XP translates to levels 1–99. "
    "Level 99 in all skills is required for the Max Cape."
)
pdf.h2("Skills")
pdf.two_col_table(
    ["Skill", "XP Source"],
    [
        ("MELEE",       "Killing mobs with melee weapons; combat damage dealt"),
        ("RANGED",      "Firing bows / crossbows; kills with ranged weapons"),
        ("DEFENCE",     "Blocking hits (damage absorbed); equipping shields"),
        ("PRAYER",      "Burying bones on dirt blocks (right-click)"),
        ("MAGIC",       "Casting elemental spells; rune consumption"),
        ("WOODCUTTING", "Chopping logs (axes on wood blocks)"),
        ("FISHING",     "Catching fish (fishing rod in water)"),
        ("FARMING",     "Harvesting crops (hoe on farmland + pick drops)"),
    ]
)
pdf.h2("Admin /skilllvl Command")
pdf.code([
    "/skilllvl <player> <skill> set <1-99>   — hard-set exact level",
    "/skilllvl <player> <skill> add <levels> — add N levels (capped at 99)",
    "/skilllvl <player> <skill> reset        — reset to level 1",
    "/skilllvl <player> all   set <1-99>     — all 8 skills at once",
    "/skilllvl <player> all   reset",
    "",
    "Skill aliases:  attack/attk/melee  def/defense/defence  pray  wc  fish  farm",
], "Requires: difficultyengine.cape.admin (OP by default)")
pdf.h2("XP Formula")
pdf.body(
    "XP is stored as a raw long integer in skills.yml. Level is derived by scanning "
    "the XP_TABLE array (99 entries, exact OSRS values). Level 99 = 13,034,431 XP. "
    "The setLevel() method uses SkillLevel.getXpForLevel(level) to write the exact "
    "minimum XP for that level."
)

# ── SECTION 4: Quest System ───────────────────────────────────────────────────
pdf.add_page()
pdf.h1("4  Quest System  (300 NPC Quests)")
pdf.body(
    "The quest system consists of 300 villager NPC quests distributed across three dimensions. "
    "Each NPC is physically placed in the world by an admin and tagged with a quest ID via PDC. "
    "Players right-click the NPC to interact."
)
pdf.h2("Two Cape Tracks")
pdf.two_col_table(
    ["Track", "Details"],
    [
        ("Main Quests  (IDs 1–150)",  "Required for Quest Skill Cape (= Lv99). Shown in /questbook."),
        ("Secret Quests (IDs 151–300)", "Hidden — show as ??? until discovered. All 150 required for Boss Quest Cape (fire aura)."),
    ]
)
pdf.h2("Dimension Breakdown")
pdf.three_col_table(
    ["Dimension", "Main Quests", "Secret Quests"],
    [
        ("Overworld (world)",         "1–50   (kill + collect)", "151–200 (hidden NPCs, sneak mechanics)"),
        ("Nether (world_nether)",     "51–100 (kill + collect)", "201–250 (bait piglins, bastion golds …)"),
        ("The End (world_the_end)",   "101–150 (kill + collect)","251–300 (dragon capture, MyPet, omega)"),
    ]
)
pdf.h2("Requirement Types")
pdf.bullet([
    "KILL — Player must have accumulated ≥ N kills of entity type E (tracked globally per mob type).",
    "COLLECT — Player must have ≥ N of Material M in inventory when talking to NPC (items consumed).",
    "HIDDEN TRIGGER — Optional extra item check; if player also has hiddenItem×hiddenCount, those are "
    "consumed for bonus gold on top of base reward.",
    "requireSneak — Player must be crouching (sneaking) to trigger quest completion.",
])
pdf.h2("NPC Management (/questnpc)")
pdf.code([
    "/questnpc spawn <1-300>   — Spawn quest NPC at admin location",
    "/questnpc remove <id>     — Despawn + unregister NPC",
    "/questnpc list            — List all placed NPCs (active/missing status)",
    "/questnpc info <id>       — Show quest definition (requirement, reward, hidden trigger)",
], "Admin-only. Tab-complete fills in valid IDs.")
pdf.h2("NPC Persistence")
pdf.body(
    "On server restart, NpcQuestSpawner reads npc_positions.yml and re-spawns any villager "
    "whose entity UUID is no longer valid. Chunk unloads are handled — the entity is checked "
    "and re-created automatically 3 seconds after plugin enable."
)
pdf.h2("Cape Awards")
pdf.bullet([
    "Quest Skill Cape — awarded when countMain() >= 150. Stored in player PDC as 'has_quest_cape'. "
    "Server-wide broadcast + level-up sound.",
    "Boss Quest Cape — awarded when countSecret() >= 150. Stored as 'has_boss_quest_cape'. "
    "Fire-ring particle aura runs every 0.5 s via BossQuestCapeTask.",
])

# ── SECTION 5: Gear System ────────────────────────────────────────────────────
pdf.add_page()
pdf.h1("5  Gear System  (Melee / Ranged / Mage)")
pdf.body(
    "All custom gear pieces are PDC-tagged items. Crafting recipes intercept "
    "PrepareItemCraftEvent to substitute the plain leather/iron result with the "
    "PDC-tagged custom item. Equip listeners enforce level requirements."
)
pdf.h2("Gear Tiers & Level Requirements")
pdf.three_col_table(
    ["Style", "Tier", "Level Req"],
    [
        ("Mage",   "Apprentice (String recipe)",             "Magic 1"),
        ("Mage",   "Standard (Blaze Powder recipe)",         "Magic 40"),
        ("Mage",   "Alchemist (Blue Dye + Ender Eye)",       "Magic 60"),
        ("Mage",   "Master (Black Dye + Amethyst + Dragon Breath)", "Magic 90"),
        ("Melee",  "Iron (Iron Ingot recipe)",                "Melee 1"),
        ("Melee",  "Diamond (Diamond recipe)",               "Melee 40"),
        ("Melee",  "Netherite (Netherite Ingot recipe)",     "Melee 70"),
        ("Melee",  "Dragon (Nether Star + Dragon Breath)",   "Melee 90"),
        ("Ranged", "Leather (String recipe)",                "Ranged 1"),
        ("Ranged", "Chain (Feather + Lapis recipe)",         "Ranged 40"),
        ("Ranged", "Netherite Ranged (Netherite + Feather)", "Ranged 70"),
        ("Ranged", "Dragon Ranged (Nether Star + Arrow)",    "Ranged 90"),
    ]
)
pdf.h2("Special Items")
pdf.bullet([
    "Dragon Arrow — crafted from 4× Prismarine Crystals. Used by Dark Bow + homing arrow mechanic.",
    "GunZ Sword — Lv99 Melee perk: double-tap WASD direction dashes the player forward.",
    "Dark Bow — homing arrows, bonus damage at high Ranged level.",
    "Enchanted Shard (PDC-tagged Amethyst Shard) — required ingredient for elemental staffs.",
])

# ── SECTION 6: Magic System ───────────────────────────────────────────────────
pdf.add_page()
pdf.h1("6  Magic System  (Staffs, Runes, Spells)")
pdf.h2("Elements")
pdf.two_col_table(
    ["Element", "Mechanic"],
    [
        ("FIRE",  "Flame burst — sets target on fire, area explosion effect"),
        ("WATER", "Bubble blast — knocks back, dripping-water particles"),
        ("EARTH", "Boulder throw — slowness, nature particles; triggers Sandstorm mechanic"),
        ("AIR",   "Wind bolt — velocity push, END_ROD particles (electric cyan)"),
    ]
)
pdf.h2("Staff Glow (Level 99 Magic only)")
pdf.body(
    "MagicGlowTask runs every 4 ticks. It checks SkillManager.getLevel(uuid, MAGIC). "
    "If the player's Magic level is below 99, no particles are spawned — the staff "
    "appears as a normal item. At level 99, element-specific ambient particles + a rotating "
    "aura ring appear around the player's hand."
)
pdf.h2("Rune System")
pdf.body(
    "Each element has a Rune item (PDC-tagged) and Rune Dust (PDC-tagged via Magic Cauldron). "
    "Spells consume runes from inventory on cast. "
    "Rune crafting: 4× base material → 8 runes (shapeless recipe)."
)
pdf.h2("Spell Book")
pdf.body(
    "Players find Spell Pages from mob drops (4% chance). Right-clicking a Spell Page unlocks "
    "a random page in their Arcane Tome. The tome lists all unlocked spell combos. "
    "Admins can give pages via /spellpage [player]."
)

# ── SECTION 7: Gold Currency ──────────────────────────────────────────────────
pdf.add_page()
pdf.h1("7  Gold Currency & VIP Shop")
pdf.body(
    "Gold coins are the server economy currency. Balance is stored per-UUID in gold.yml. "
    "Coins drop from mobs scaled by difficulty tier. Players check balance with /gold."
)
pdf.h2("Gold Scaling by Difficulty")
pdf.two_col_table(
    ["Difficulty", "Drop Multiplier"],
    [
        ("Peaceful",   "0.5×"),
        ("Easy",       "1.0× (base)"),
        ("Medium",     "1.5×"),
        ("Hard",       "2.0×"),
        ("Nightmare",  "3.0× (+ 10× if 4+ NM players within 50 blocks)"),
    ]
)
pdf.h2("VIP Shop")
pdf.body(
    "A Villager NPC spawned by /vipshop spawn. Players right-click to browse cosmetic trades "
    "including Unicorn Slippers and other decorative items. All trades use gold coins."
)

# ── SECTION 8: Cape System ────────────────────────────────────────────────────
pdf.add_page()
pdf.h1("8  Cape System")
pdf.h2("Cape Types")
pdf.two_col_table(
    ["Cape", "Requirement & Visual"],
    [
        ("Skill Cape (per skill)",    "Level 99 in that skill — element-coloured particles"),
        ("Max Cape",                  "All 8 skills Lv99 — multi-element aura"),
        ("Boss Cape",                 "Win a Double Boss event without dying — boss aura"),
        ("Quest Skill Cape",          "Complete all 150 main NPC quests — blue/white stars"),
        ("Boss Quest Cape (fire)",    "Complete all 150 secret NPC quests — rotating FLAME ring + SOUL_FIRE_FLAME + lava burst"),
    ]
)
pdf.h2("Cape Wardrobe")
pdf.body(
    "Players open /cape or /mycape to access the Cape Wardrobe GUI. The wardrobe allows "
    "equipping a cape independently of the chestplate slot (both are worn simultaneously). "
    "Capes are identified via PDC keys on the chestplate-slot item."
)
pdf.h2("Boss Quest Cape Visual (BossQuestCapeTask)")
pdf.code([
    "// Runs every 10 ticks for players with 'has_boss_quest_cape' PDC byte == 1",
    "// 10-point FLAME ring orbiting at waist height, slowly rotating",
    "// SOUL_FIRE_FLAME every 2 ticks for blue/orange mix",
    "// LAVA burst every 4 ticks for lava pop effect",
], "The ring angle advances 18° per tick, completing one full rotation per second.")

# ── SECTION 9: Party & Difficulty ─────────────────────────────────────────────
pdf.add_page()
pdf.h1("9  Party & Difficulty Scaling")
pdf.h2("Difficulty Modes")
pdf.two_col_table(
    ["Mode", "Effect"],
    [
        ("Peaceful",  "Very low mob damage + drops. Gold ×0.5."),
        ("Easy",      "Default vanilla-ish scaling."),
        ("Medium",    "Mobs deal +25% damage. Gold ×1.5."),
        ("Hard",      "Mobs deal +50% damage. Gold ×2.0."),
        ("Nightmare", "Mobs deal +100% damage. Bonus mob spawns. Gold ×3.0. Nightmare tag."),
    ]
)
pdf.h2("Group Nightmare Bonus")
pdf.body(
    "When 4 or more Nightmare-mode players are within 50 blocks of each other, difficulty "
    "and rewards scale up by ×10 for all mobs in range (GroupDifficultyListener)."
)
pdf.h2("Party System")
pdf.body(
    "Players form parties via /party invite <player>. Party members see each other's HP "
    "and difficulty on a scoreboard sidebar (PartyHudTask, updates every second). "
    "Parties share quest kill credit for NPC quests."
)

# ── SECTION 10: Admin Commands ────────────────────────────────────────────────
pdf.add_page()
pdf.h1("10  Admin Commands Reference")
pdf.two_col_table(
    ["Command", "Purpose"],
    [
        ("/skilllvl <p> <skill> set/add/reset [val]", "Manually edit player skill levels"),
        ("/questnpc spawn <1-300>",    "Place a quest NPC at admin location"),
        ("/questnpc remove <id>",      "Remove + unregister a quest NPC"),
        ("/questnpc list",             "Show all placed NPCs + alive status"),
        ("/questnpc info <id>",        "Display quest definition details"),
        ("/gear [player]",             "Give max-enchanted netherite god gear"),
        ("/adminlight",                "Toggle personal admin light"),
        ("/curecosmetic [player]",     "Remove cosmetic effects from a player"),
        ("/vipshop spawn",             "Spawn VIP Shop villager NPC"),
        ("/spellpage [player]",        "Give a Spell Page item"),
        ("/spellbook",                 "Open Arcane Tome (admin shortcut)"),
        ("/givebag [player]",          "Give a Magic Bag item"),
        ("/registry",                  "Open Item Registry GUI"),
    ]
)
pdf.info_box("Permission Node",
    "All admin commands require: difficultyengine.cape.admin\n"
    "This permission is OP-only by default (default: op in plugin.yml).", "tip")

# ── SECTION 11: Data Files ─────────────────────────────────────────────────────
pdf.add_page()
pdf.h1("11  Data Files & Persistence")
pdf.two_col_table(
    ["File", "Contents"],
    [
        ("skills.yml",              "skills.<uuid>.<SKILL> = totalXp (long)"),
        ("difficulty.yml",          "difficulty.<uuid> = PEACEFUL|EASY|MEDIUM|HARD|NIGHTMARE"),
        ("gold.yml",                "gold.<uuid> = balance (long)"),
        ("questdata.yml",           "Legacy QuestType progress/completions per player"),
        ("npcquest_kills.yml",      "kills.<uuid>.<ENTITY_TYPE> = count (int)"),
        ("npcquest_progress.yml",   "done.<uuid> = '1,5,23,101,…' (completed quest IDs)"),
        ("npc_positions.yml",       "npcs.<id>.world/x/y/z/uuid — NPC entity tracking"),
        ("cape.yml",                "Cape unlock data (SkillCapeManager)"),
        ("spellbook.yml",           "Unlocked spell pages per player UUID"),
        ("magicbag.yml",            "Magic Bag inventory contents per player"),
    ]
)
pdf.body(
    "All files live in plugins/DifficultyEngine/. They are loaded on enable and saved on "
    "each state change (autosave) plus on disable. No external database is required."
)
pdf.info_box("PDC Keys on Players",
    "has_quest_cape (BYTE)     — 1 if player completed 150 main quests\n"
    "has_boss_quest_cape (BYTE) — 1 if player completed 150 secret quests\n"
    "These survive server restarts as they are stored on the player entity.", "note")

# ── SECTION 12: plugin.yml ────────────────────────────────────────────────────
pdf.add_page()
pdf.h1("12  plugin.yml  — Commands & Permissions")
pdf.h2("Player Commands")
pdf.bullet([
    "/difficulty [peaceful|easy|medium|hard|nightmare] — aliases: diff, setdifficulty",
    "/hpbar — toggle live HP display above mob heads",
    "/sit [on|off] — right-click-to-sit on slabs/stairs",
    "/skills [player] — chat text skill summary",
    "/mystats [player]  /stats — open skill tree GUI",
    "/cape  /mycape — open Cape Wardrobe",
    "/gold — check gold coin balance",
    "/questbook — open quest journal GUI",
    "/party [invite|leave|kick|info]",
    "/trade [player]",
    "/magicbag  /bag  /mbag — open Magic Bag",
    "/spellbook — Arcane Tome (admin shortcut; players craft the tome)",
])
pdf.h2("Admin Commands")
pdf.bullet([
    "/skilllvl  — aliases: setskill, adminskill",
    "/questnpc  — aliases: qnpc",
    "/gear [player]",
    "/adminlight",
    "/curecosmetic [player]",
    "/vipshop spawn",
    "/spellpage [player]",
    "/givebag [player]",
    "/registry",
])
pdf.h2("Permissions")
pdf.two_col_table(
    ["Node", "Default"],
    [
        ("difficultyengine.use",          "true (all players)"),
        ("difficultyengine.gear",         "op"),
        ("difficultyengine.gear.others",  "op"),
        ("difficultyengine.registry",     "op"),
        ("difficultyengine.turbocart",    "op"),
        ("difficultyengine.cape.admin",   "op  ← gates all admin features"),
        ("difficultyengine.skills.others","op  ← /skills <other player>"),
    ]
)

# ── SECTION 13: Quest Registry Summary ────────────────────────────────────────
pdf.add_page()
pdf.h1("13  Quest Registry  — All 300 Quests Summary")
pdf.h2("Main Quests: Overworld (1–50) — Kill + Collect")
pdf.body(
    "Quests 1–10: Kill quests — Zombie, Skeleton, Creeper, Spider, Witch, Slime, "
    "Husk, Stray, Drowned, Phantom. Rewards: 400–700 gp. Quest 1 has hidden trigger: "
    "24 rotten flesh for +250 bonus gold.\n\n"
    "Quests 11–50: Collect quests — Wheat, Carrot, Iron Ingot, Coal, Diamond, White Wool, "
    "Leather, Oak Log, Gravel, Emerald, Cod, Salmon, Pufferfish, Bread, Egg, Milk Bucket, "
    "Honey, Apple, Ink Sac, Feather, Iron Sword, Bow, Shield, Iron Chestplate, Diamond "
    "Pickaxe, Flint, Book, Map, Clock, Compass, Emerald (×3), White Bed, Lead, Gold Ingot, "
    "Redstone, Lapis, String, Bone, Ender Pearl. Rewards: 250–800 gp."
)
pdf.h2("Main Quests: Nether (51–100) — Kill + Collect")
pdf.body(
    "Quests 51–60: Kill — Blaze, Piglin, Hoglin, Ghast, Wither Skeleton, Magma Cube, "
    "Strider, Zoglin, Piglin Brute, Skeleton.\n\n"
    "Quests 61–100: Collect — Quartz, Netherrack, Gold Nugget, Ancient Debris (×1 & ×2), "
    "Basalt, Blackstone, Soul Sand, Warped/Crimson Planks, Nether Brick, Blaze Rod, "
    "Nether Wart, Magma Cream, Ghast Tear, Golden Helmet, Crimson/Warped Fungus, Leather, "
    "Porkchop, Fire Charge, Soul Soil, Shroomlight, Chainmail Chestplate, Piglin Banner "
    "Pattern, Lodestone, Respawn Anchor, Glowstone Dust, Quartz Block, Gold Block, "
    "Obsidian, Gold Ingot (barter), Lava Bucket, Saddle, Brewing Stand, Bone Meal, "
    "Raw Gold, Ancient Debris (×2 again), Ghast Tear (×5), Golden Chestplate, Blaze Rod "
    "(×20, hidden: Ancient Debris ×1 for +500 gp)."
)
pdf.h2("Main Quests: End (101–150) — Kill + Collect")
pdf.body(
    "Quests 101–103: Kill — Enderman, Shulker, Endermite.\n"
    "Quest 104: Kill 1 Ender Dragon (3000 gp).\n\n"
    "Quests 105–150: Collect — Chorus Fruit, Purpur Block, End Stone, Ender Pearl, "
    "Elytra, Shulker Shell, Popped Chorus Fruit, End Rod, Dragon Head, Ender Chest, "
    "Ender Eye, End Stone Bricks, Chorus Flower, Phantom Membrane, Spectral Arrow, "
    "Firework Rocket, End Crystal, more Enderman/Shulker kill quests, Dragon's Breath, "
    "Purpur large counts … culminating in Quest 150: collect 1 Nether Star, hidden: "
    "16 Dragon's Breath → up to 15,000 gp total reward."
)
pdf.h2("Secret Quests (151–300) — Highlights")
pdf.bullet([
    "Overworld secrets (151–200): Cave Spider hunter, Midnight Wool (sneak), Golden Apple, "
    "Vindicator/Evoker/Vex/Pillager/Ravager slayers, Enchanted Book, Amethyst Stash, "
    "Copper Hoard, Echo Shard (sneak), Warden Terror (sneak), Sculk Catalyst/Shrieker, "
    "Witch Coven (hidden: Sugar), Forbidden Apple (hidden: 9 Gold Block = 7,000 gp).",

    "Nether secrets (201–250): Bastion Gold (sneak), Lava Baiter (kill 5 Piglins, "
    "hidden: 2 Lava Buckets), Pigstep Disc (sneak), Brute Gauntlet, Crying Obsidian "
    "hoard, Skull Trophy (3 Wither Skeleton Skulls = 5,000 gp!), Netherite Set + 4 "
    "Ancient Debris hidden (7,000 gp), Nether Legend (50 Blazes, hidden: 20 Blaze Rods).",

    "End secrets (251–300): Dragon Slayer legend (10,000 base + 5,000 hidden), "
    "Lead Dragon = 'I tried to capture it' sneak quest, End Cake Party (sneak), "
    "Respawn Anchor in End (sneak), Shell God (128 Shulker Shells), The Omega "
    "(1 Ender Dragon + 256 Dragon's Breath hidden = 75,000 gp MAXIMUM), "
    "Void Ascension (256 Dragon's Breath + 1 Nether Star hidden = 30,000 gp).",
])
pdf.info_box("Secret Quest Discovery",
    "Secret quests do NOT appear in /questbook until the player right-clicks the NPC. "
    "The NPC name tag shows '§8[?] <npcName>' in dark grey. If requireSneak is true, "
    "the NPC responds with '...' unless the player is crouching, making the quest "
    "doubly hidden.", "quest")

# ── FINAL PAGE ─────────────────────────────────────────────────────────────────
pdf.add_page()
pdf.h1("Quick-Start Checklist")
pdf.body("After dropping the plugin JAR into your Paper 1.21 server's plugins/ folder:")
pdf.bullet([
    "Start server — DifficultyEngine: Ready! in console confirms all systems loaded.",
    "Place NPCs: /questnpc spawn 1 through /questnpc spawn 300 at suitable world locations. "
    "Use /questnpc info <id> to preview each quest before placing.",
    "Overworld NPCs go in villages, caves, farms, ocean temples, etc.",
    "Nether NPCs go in Nether Fortresses, Bastions, Soul Sand Valleys.",
    "End NPCs go on the End Island or in End Cities on outer islands.",
    "Test with /skilllvl <yourself> all set 99 then /questnpc spawn 1 and right-click.",
    "Verify Boss Quest Cape fire aura: manually complete 150 secrets via console "
    "or set npcquest_progress.yml for testing.",
    "Gold drops are live from mob kills — check /gold after a few kills.",
    "Staff crafting: Enchanted Shard (Amethyst + PDC) + element ingredient + Stick.",
    "Magic glow activates automatically once Magic level reaches 99.",
])
pdf.info_box("GitHub Repository",
    "Source: https://github.com/unleashbuildwithai/DifficultyEngine\n"
    "Branch: master\n"
    "Latest commit includes all systems documented in this PDF.", "tip")

pdf.output(OUTPUT)
print(f"PDF saved: {OUTPUT}")
