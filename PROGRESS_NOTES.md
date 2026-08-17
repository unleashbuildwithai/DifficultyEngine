# PROGRESS NOTES — Dragon Armour + Monster/VIP cleanup commands + Quest Eggs + Air Rune fix
(Updated as work completes — read this first if session gets interrupted)

## TASK 0: Air Rune showing as a door (STATUS: DONE)
ROOT CAUSE: Not a bug in our plugin. The Nexo plugin (installed separately on the
server, plugins/Nexo) defines furniture items (e.g. "large_wooden_door") using base
Material PAPER with its own custom_model_data values, and ships its own full
assets/minecraft/models/item/paper.json override file in its resource pack. Depending
on resource-pack layering/load order, Nexo's paper.json can shadow/replace
DifficultyEngine-RP's paper.json (which used custom_model_data 3004 on PAPER for the
Air Rune), making the Air Rune LOOK like a door model while its actual ItemMeta/PDC
tag (and displayed name "Air Rune") stays correct — exactly what the user reported.
FIX: Moved the Air Rune off the shared vanilla PAPER item entirely onto Material.SUGAR
(not used by Nexo's defaults — checked its nexo_defaults/*.yml files: only PAPER,
CHAINMAIL_*, ELYTRA, WOLF_ARMOR, DIAMOND_HORSE_ARMOR, NETHERITE_*, SHIELD, TRIDENT are
used, no SUGAR). Changes:
  - magic/MagicElement.java: AIR rune material PAPER -> SUGAR
  - DifficultyEngine-RP/assets/minecraft/models/item/paper.json: removed the override
  - DifficultyEngine-RP/assets/minecraft/models/item/sugar.json: NEW file with the
    custom_model_data 3004 -> difficultyengine:item/air_rune override (texture files
    under assets/difficultyengine are untouched, reused as-is)
  - gen_element_pngs.py: updated RUNE_TARGETS + doc comment paper->sugar for future regens
IMPORTANT ADDITIONAL FINDING: the LIVE server's server.properties has resource-pack=
BLANK (require-resource-pack=false too) — it does NOT use vanilla resource-pack delivery
at all. Instead the Nexo plugin (settings.yml Pack.generation.generate_pack: true,
Pack.dispatch.send_on_join/send_pre_join: true) builds its OWN merged pack.zip and
dispatches THAT to clients — this is very likely the actual mechanism that caused the
Air Rune/door collision (two independently-built packs both touching paper.json,
whichever Nexo included last silently won for that file). Nexo already supports merging
in other resource packs cleanly via Pack.import.external_packs + from_location, so:
  - Copied DifficultyEngine-RP/assets + pack.mcmeta into
    "server/plugins/Nexo/pack/external_packs/DifficultyEngine-RP/" (same layout as the
    pre-existing illithid_sword/workshop_six external packs already there)
  - Registered it in plugins/Nexo/settings.yml under Pack.import.from_location and
    external_pack_order (loaded LAST so it wins any remaining file-name collisions)
  - vanilla server.properties resource-pack-sha1 was ALSO updated as a harmless backup/
    fallback in case Nexo's own dispatch is ever disabled, but Nexo's own merged pack.zip
    is what actually reaches players today.
DifficultyEngine-RP.zip on disk (project root) was re-zipped with the sugar.json fix
using Python's zipfile module (NOT PowerShell Compress-Archive, which produces backslash
path separators that break Minecraft's zip parser) — new SHA1:
28347f3bf874378388de7f457a85d51cfa237a80

## TASK 1: New admin commands (STATUS: DONE)
- `/spawnmob remove <all|monster_id>` — removes custom monsters (CustomMonsterManager.removeMonsters()).
- `/vipshop remove` — removes VIP Shop Keeper villager(s) (VipShopListener.removeVipKeepers()).
- Wired into Main.java's existing "spawnmob" and "vipshop" registerCmd blocks.
- plugin.yml (both root and src/main/resources copies) usage strings updated.

## TASK 2: Dragon Armour rework (STATUS: DONE, pending compile verification)
Reuses the EXISTING `MeleeGearTier.DRAGON` tier (items/MeleeGearTier.java, PDC tag
`dragon_melee_gear`, ×2.50 defence / ×1.60 damage bonus — UNCHANGED).

RECIPE CORRECTION (per user, "its not netherite brick its netherite ingot"): the
originally-planned fake "Netherite Brick" custom item was SCRAPPED. Final recipe per
piece (helmet/chestplate/leggings/boots) = 9 total ingredients (Bukkit hard cap,
confirmed via javap decompile of actual Paper API jar):
  1x correct NETHERITE_[piece]  +  2x Netherite Ingot (vanilla)  +  5x Diamond
  +  1x Charged Magic Bottle (matched via RecipeChoice.ExactChoice since it's a
     PDC-tagged custom POTION item, not a plain Material)
Recipe key names: dragon_armour_helmet / _chestplate / _leggings / _boots
Registered in Main.java's registerCraftingRecipes(), NOT added to allRecipeKeys (so
NOT auto-discovered on join — gated behind the Dragon Armour Page, see below).

Gate: new "Dragon Armour Page" item (NEW custom item, Material.BOOK, not consumed).
  - 1% drop chance on death of: vanilla Wither, vanilla EnderDragon, Infernal Blazefiend
    (Crimson boss), Void Zurion (Void Wither boss), Tempest Overlord.
    Implemented via ONE new listener `DragonArmourPageDropListener` using
    `bossEffectListener.isBoss(entity)` (already covers Crimson/Tempest/Void/Gilded since
    they all call `registerBoss()`) OR `instanceof Wither/EnderDragon`.
  - On pickup (EntityPickupItemEvent) → `player.discoverRecipe()` for all 4 piece recipes.
    Added as new handler in existing `CustomItemCraftListener.java` (same pattern as
    Earth Magic Page discovery).

Netherite Brick (NEW simple custom item, needed as recipe ingredient):
  - Recipe: 2x Netherite Scrap + 4x Iron Ingot → 1 Netherite Brick. Freely discoverable
    (added to allRecipeKeys, not gated).

Hearts bonus (NEW mechanic, does not exist anywhere else in the codebase):
  - +2 hearts (4 HP) per DRAGON-tier melee piece worn → 4 pieces = +8 hearts (16 HP).
  - Full set (all 4) bonus: +3 more hearts (6 HP) → total +11 hearts (22 HP) at full set.
  - Implemented via NEW repeating task `DragonArmourHeartsTask` (every 20 ticks, like
    other tick-based systems in this project e.g. CapeVisualTask/PartyHudTask) that scans
    online players' armour contents, counts DRAGON-tier pieces via
    `itemFactory.getMeleeGearTier(piece)`, and applies/updates an `AttributeModifier` on
    `Attribute.GENERIC_MAX_HEALTH` (same technique as `SkillBonusManager.applyDefenceHpBonus`).

Gate: "Dragon Armour Page" item (NEW, ItemFactory.buildDragonArmourPage/isDragonArmourPage,
Material.BOOK, PDC key de_dragon_armour_page, not consumed on pickup).
  - 1% drop chance on death of: vanilla Wither, vanilla EnderDragon, Infernal Blazefiend
    (Crimson boss carrier = Blaze), Void Zurion (Void boss carrier = Wither, so already
    covered by the Wither check), Tempest Overlord (carrier = Phantom).
  - NEW file boss/DragonArmourPageDropListener.java: checks instanceof Wither/EnderDragon
    OR (instanceof Blaze/Phantom AND bossEffectListener.isBoss(entity)) so random vanilla
    Blazes/Phantoms never drop it — only tracked boss carriers do.
  - Registered in Main.java right after MeleeGearEquipListener registration.
  - Added to registry page 4 (Melee Gear) for admin preview via buildRegistryPage4().
  - On pickup (EntityPickupItemEvent) -> discovers all 4 dragon_armour_* recipes.
    New handler onPickupDragonArmourPage() added to existing CustomItemCraftListener.java.

MeleeGearCraftListener.java: renamed old ungated melee_dragon_helmet/chestplate/
leggings/boots case keys to dragon_armour_helmet/chestplate/leggings/boots (result-build
logic itself unchanged — still buildMeleeGearPiece(MeleeGearTier.DRAGON, ...)).

Hearts bonus (NEW mechanic): +2 hearts (4 HP) per Dragon-tier piece worn, +3 more hearts
(6 HP) full-4-piece-set bonus (total +11 hearts / 22 HP at full set). NEW file
items/DragonArmourHeartsTask.java (extends BukkitRunnable, scheduled every 20 ticks in
Main.java's onEnable alongside other tick tasks), applies an AttributeModifier on
Attribute.GENERIC_MAX_HEALTH (same technique as SkillBonusManager.applyDefenceHpBonus,
own UUID d4a90000-1234-4dee-9a1e-de9dea9ea9ea so it never collides with the Defence one).

Files touched (all done):
  - items/ItemFactory.java        — buildDragonArmourPage/isDragonArmourPage, added to
                                     registry page 4. (buildNetheriteBrick removed —
                                     scrapped per user correction.)
  - listeners/MeleeGearCraftListener.java — dragon_armour_* recipe result-swap cases.
  - listeners/CustomItemCraftListener.java — onPickupDragonArmourPage discovery handler.
  - boss/DragonArmourPageDropListener.java — NEW file, 1% page drop.
  - items/DragonArmourHeartsTask.java — NEW file, hearts bonus tick task.
  - Main.java — registerCraftingRecipes() edits (4 gated dragon_armour_* recipes replacing
    old melee_dragon_* block), added RecipeChoice import, onEnable() registers new
    listener (DragonArmourPageDropListener) + task (DragonArmourHeartsTask).

## TASK 3: Quest NPC "eggs" in Registry (STATUS: DONE, pending compile verification)
FULL SCOPE (not scoped-down): ALL 306 quests get a Villager Spawn Egg item, auto-paginated
45-per-page across 7 NEW registry pages (12–18). Page count computed dynamically from
NpcQuestRegistry.all().size() so it self-adjusts if quest count ever changes
(ItemFactory.QUEST_EGG_PAGE_START=12, QUEST_EGG_PAGE_END computed). Right-clicking a block
with an egg spawns that quest's NPC there (consumes 1 egg from the stack), reusing
NpcQuestSpawner's existing persisted save/track logic via new public wrapper
`spawnNpcById(int questId, Location loc)`, so egg-placed NPCs behave identically to
/questnpc spawn <id> (persist across restarts, show in /questnpc list, removable via
/questnpc remove <id> or /npcwipe). Requires difficultyengine.cape.admin to place.
The pre-existing `/questnpc spawn <id>` command is fully unaffected/still works.

Files touched (all done):
  - quests/NpcQuestSpawner.java — new public spawnNpcById() wrapper around private spawnNpc().
  - quests/QuestEggListener.java — NEW file, handles right-click-block placement of eggs.
  - items/ItemFactory.java — buildQuestEgg(NpcQuestDef)/isQuestEgg/getQuestEggId,
    QUEST_EGG_PAGE_START/END constants, buildQuestEggPage(int), getPageItems() routes
    pages >= 12 to buildQuestEggPage.
  - gui/RegistryGUI.java — PAGE_COUNT 11->18, PAGE_NAMES array extended (12-18 "Quest
    Eggs X/7"), pageLabel() lore text updated to mention pages 12-18.
  - Main.java — registers QuestEggListener right after NpcQuestListener registration.

## PRE-EXISTING BUGS FIXED ALONG THE WAY (found while compiling, unrelated to this
## session's feature requests, but were blocking ALL compilation so had to be fixed):
  - 18 files used deprecated/removed `Attribute.GENERIC_*` constants (GENERIC_MAX_HEALTH,
    GENERIC_ATTACK_DAMAGE, GENERIC_MOVEMENT_SPEED, GENERIC_FOLLOW_RANGE, GENERIC_SCALE)
    that don't exist in the actual running server's API (paper-api 26.2.build.60-beta) —
    renamed to MAX_HEALTH/ATTACK_DAMAGE/MOVEMENT_SPEED/FOLLOW_RANGE/SCALE via a safe
    scripted find-replace across all 18 files (this same class of bug was already fixed
    once before per "note read next.txt" but had reverted, likely from a backup restore).
  - NpcQuestRegistry.java line 305 used `Material.CHAIN` (does not exist) instead of
    `Material.IRON_CHAIN` — fixed.
  - pom.xml declared paper-api version 1.21-R0.1-SNAPSHOT but the ACTUALLY RUNNING
    server.jar reports version "26.2-65-fc9375a" (confirmed via `java -jar server.jar
    --version`) — these are different API generations (old used GENERIC_* attribute
    names, new does not). Installed the correct jar
    (libraries/io/papermc/paper/paper-api/26.2.build.60-beta/paper-api-26.2.build.60-beta.jar)
    into the local .m2 repo and bumped pom.xml's dependency version to match. NOTE:
    `mvn package` still fails after this because several transitive dependencies
    (org.joml:joml, net.kyori:adventure-api, net.md-5:bungeecord-chat — all already
    present under libraries/ for the manual javac path) are not declared in pom.xml's
    <dependencies> block. This was NOT fully fixed (deprioritized to stay in budget) —
    the plugin jar for TODAY was instead built directly via javac using the same
    cp_list.txt classpath already used by build_check.bat, which compiles 100% cleanly.
    If Maven builds are needed going forward, add explicit <dependency> entries for
    those 3 libraries (versions: adventure-api 5.2.0, joml 1.10.8, bungeecord-chat
    1.21-R0.2-deprecated+build.21) with <scope>provided</scope>.

## FINAL STEP (STATUS: DONE)
1. Ran the full compile via javac (same classpath as build_check.bat) — confirmed 100%
   clean, 197 .class files produced, zero errors (only harmless deprecation notes).
2. Re-zipped DifficultyEngine-RP/ into DifficultyEngine-RP.zip using Python's zipfile
   module (PowerShell Compress-Archive produces backslash path separators that break
   Minecraft's zip parser — do NOT use it for resource packs). New SHA1:
   28347f3bf874378388de7f457a85d51cfa237a80
3. Built DifficultyEngine.jar directly via `jar cf` from the compiled classes +
   plugin.yml + config.yml, and copied it to
   "c:/Users/Owner/Desktop/minecraft server/server/plugins/DifficultyEngine.jar"
   (overwriting the old one) — this IS the live server's plugins folder.
4. Copied DifficultyEngine-RP's assets + pack.mcmeta into
   "server/plugins/Nexo/pack/external_packs/DifficultyEngine-RP/" and registered it in
   Nexo's settings.yml (Pack.import.from_location + external_pack_order, loaded LAST)
   so Nexo's own merged resource pack now includes our fixed Air Rune model without
   file-collision risk going forward. Also updated the (unused-by-Nexo, but harmless-
   to-keep-in-sync) server.properties resource-pack-sha1 field to the new hash.

## REMAINING FOR USER TO DO
- Restart the Minecraft server (or fully stop/start — not just /reload, since jar files
  and plugin.yml changes need a fresh JVM load) to pick up: the new /spawnmob remove and
  /vipshop remove commands, the reworked Dragon Armour recipe + hearts bonus, the 7 new
  Quest NPC Egg registry pages (12–18, all 306 quests), and the fixed Air Rune icon.
- Existing Dragon armour pieces already crafted/owned by players are NOT retroactively
  changed — only new crafting attempts use the new gated recipe (Dragon Armour Page +
  2 Netherite Ingot + 5 Diamond + 1 Charged Magic Bottle per piece).
- First join after restart, players may see a resource-pack re-download prompt (new
  Nexo pack hash) — this is expected and one-time.
