# DifficultyEngine — Bug Fix & Feature Checklist

Resume from this file in a new chat: "Continue DifficultyEngine work, resume from C:\Users\Owner\Desktop\123\DIFFICULTYENGINE_CHECKLIST.md"

## PHASE 1 — Critical Bug Fixes ✅ COMPLETE
- [x] Fix lightning bottle stack consumption (PlayerItemConsumeEvent clone mutation doesn't write back)
- [x] Fix Gunz Sword level check (material-based check ignores custom PDC tag; enforcement removes item instead of blocking)
- [x] Fix /lightningadmin fast-cast crash (zero cooldown = unthrottled per-tick heavy work)
- [x] Peaceful players ignored by all mobs (incl. phantoms)

## PHASE 1.5 — Re-fix Lightning Bottle (event.setItem() didn't write back) ✅ COMPLETE
- [x] Rewrite onDrinkBottle to decrement the ACTUAL held stack (getItemInMainHand/getItemInOffHand), then add empty bottle (drop if inventory full). Fixes infinite-bottle glitch on stacks of 2+.

## PHASE 2 — Rebuild /home GUI (CoreEngine) ✅ COMPLETE (Rename deferred)
- [x] Fix Component.text() legacy-color bug in SettingsGUIManager (LegacyComponentSerializer)
- [x] 50-slot Homes GUI with clickable empty slots to save
- [x] Rank-gated slots (3 free non-member, 10/25/50 by tier; locked slots show "requires higher rank")
- [x] Click saved home -> Teleport / Delete sub-menu (Rename deferred - needs `name` column)
- [x] /home <number> instant teleport

## PHASE 3 — Hover Lore + Earth Magic ✅ COMPLETE
- [x] Standardize staff/rune/book tooltips (level required + mechanic)
  - Staff lore: "Requires Magic Lv 1" + per-element Left/Right-click mechanic lines
  - Rune / Rune Dust: added "Mechanic" lines; Earth Book: "Requires Magic Lv 10" + mechanic
- [x] Fix earth magic input loop (book + block not casting)
  - Single Earth Book (ENCHANTED_BOOK, PDC earth_book) with per-player unlocked tier pages
  - New EarthBookManager (persisted earthbook_data.yml), EarthBookListener (right-click page -> unlock; right-click book -> open view)
  - Earth Pages are now mob drops gated by dimension (Overworld low tiers, Nether Gold/Obsidian/NetherBricks/AncientDebris, End EndStone/EndStoneBricks), weighted lower-more-common
  - Fallback casting: scans highest->lowest unlocked tier with matching block+level, consumes 1 block, "no block to cast" otherwise
  - Removed 8 craftable page recipes; Earth Book now craftable (BOOK+EMERALD+DIRT)

## PHASE 4 — Cleanup Removals ✅ COMPLETE
- [x] Remove ancient portal structure + lightning trigger/message
  - Deleted realm/AncientDebrisPortalListener.java + realm/AncientPortalFrameUtil.java
  - Removed MagicStaffListener portal field/setter + lightning->ANCIENT_DEBRIS portal trigger
  - Removed Main wiring/imports (ancientPortalListener registration)
- [x] Boss audit: ALL 4 bosses use custom Blockbench ItemDisplay models (crimson_boss 3001,
      tempest_boss 3002, void_boss 3003, gilded_boss) with invisible physics carriers
      -> all are reskinned -> KEEP ALL 4 (no removals)
- [x] Ancient Realm / Void Realm: custom-monsters-only lockdown
  - New AncientRealmMonsterLock: cancels vanilla hostile NATURAL/REINFORCEMENTS/DEFAULT spawns
    in ancient_realm + void_realm; custom monsters (de_custom_mob tag), boss spawns, spawners,
    eggs, /spawnmob all pass
  - New AncientRealmSpawnTask (15s): tops up custom monsters near players in the realms
    (giant_zombie, lava_titan, wind_wraith, ghost_boss)
- [x] Added "giant_zombie" custom monster (ZOMBIE_HEAD display, 1600 HP, 2.8x scale) to code
      defaults + server monsters.yml; fixed default monster name colour codes (§, no mojibake)

## PHASE 5 — Big Features (after 1-4 stable) ✅ PARTIAL
- [x] Sub-tick ring-buffer input system (350-380ms window)
  - Reusable ActionInputBuffer (input/ActionInputBuffer.java): O(1) ring of activation
    timestamps, double-activation detection on a 380ms window; powers the Air Staff dash.
- [x] Air staff / Gunz sword dash (incl. mid-air/levitating)
  - Air Staff: double right-click (380ms window, 800ms cd) -> AIR DASH in look direction,
    speed scaled by mage-gear air power; levitation/slow-fall keeps extra lift while hovering;
    single right-click still toggles hover. Visuals: cloud + end-rod + phantom flap.
  - GunZ Sword: dash now levitation-aware — keeps extra lift while Levitation/Slow Falling
    active (previously always clamped to 0.22 upward arc).
- [x] Market safe-zone anchor (/monstergrid) — CoreEngine
  - MonsterGridListener cancels NATURAL/REINFORCEMENTS/DEFAULT hostile spawns within
    config.monstergrid.radius (default 32) of the Market NPC anchor; spawners/eggs/bosses pass.
  - /monstergrid status|on|off|radius <n>|reload (coreengine.admin); persisted to config.yml.
- [x] Repeatable quest tiered scaling (+2..+7 per difficulty)
  - Claimable state now persisted to questdata.yml (was in-memory; lost on restart → re-completions)
  - Each claim increases a repeatable quest's target by the player's difficulty increment
    (Peaceful +2, Easy +3, Medium +4, Hard +5, Nightmare +7)
  - Rewards scale proportionally so harder quests pay more; /questbook progress bar uses scaled target
  - One-time/NPC quests unchanged

## PHASE 6 — Wand Secure (/wandsecure) ✅ COMPLETE
- [x] Selection wand (left/right click -> pos1/pos2 via /markon stick), /wandsecure saves area
- [x] Only xxfatalg0dz (UUID aa1690e5-a819-4028-a801-2fd3d482c533) can use wand + toggle
- [x] Secured area: nothing breakable by anyone (incl. admins/OP)
- [x] /wandsecure off only affects the area you're standing in; "no area found" otherwise
- [x] /wandsecure on re-secures; breaking requires grid off even for owner
- [x] Persisted to regions.yml (survives restart)

## PHASE 7 — No-Spawn 25k + Despawn Threshold ✅ COMPLETE
- [x] Expand no-spawn zone to 25k x 25k x 25k (radius 25000, was 500)
- [x] Monsters lured into the area despawn on crossing the threshold (2s repeating task)

## PHASE 8 — Market GUI Sell / Quick-Buy Rework ✅ COMPLETE
- [x] Sell: click empty slot -> sell menu -> accept -> enter price -> accept
  - Clicking an EMPTY grid slot on the Sell Listings tab opens SELL_CREATE dialog
    (previews your main-hand stack). Accept -> chat prompt for TOTAL price -> listing
    placed via MarketManager.placeSellListing. Cancel returns to the grid.
  - MarketSession: SELL_CREATE view + awaitingSellPrice flag.
- [x] Quick-sell: place items -> "Sell" button; escape/leaving auto-sells (fixed input slot + InventoryCloseEvent auto-sell)
- [x] Buy: "are you sure? Yes/No" confirm (quickBuy toggle, default ON = instant; OFF = confirm dialog)

## PHASE 2 POLISH ✅
- [x] Home rename: added `name` column to player_homes (DB create + ALTER migration);
      HomeDao records name, /sethome <slot> [name]; Homes-GUI options now has a Rename
      button -> chat prompt (cancel = 'cancel'); saved homes display the custom name.
- [x] Mojibake cleanup: removed ALL genuine § mojibake from GUIListener.java (was the only
      CoreEngine file with real multi-level mojibake); core messages now render coloured.

## PROGRESS LOG
- 2026-08-17: Phases 4 + 5 + 8 + Phase 2 polish COMPLETE (both jars built & deployed).
  - Phase 4: audited bosses - all 4 use custom Blockbench ItemDisplay models -> reskinned,
    so kept all. New AncientRealmMonsterLock + AncientRealmSpawnTask keep ancient_realm/void_realm
    custom-monsters-only. Added "giant_zombie" custom monster (ZOMBIE_HEAD, 1600 HP). Fixed build
    drift: sources.txt restored to all 154 files, Vault.jar added to cp_list.txt.
  - Phase 5: reusable ActionInputBuffer (sub-tick 380ms ring), Air Staff double-right-click
    AIR DASH (mage-gear scaled, mid-air/levitate-friendly); GunZ dash levitation-aware.
    /monstergrid: MonsterGridListener + MonsterGridCommand - Market safe-zone anchor (radius 32).
  - Phase 8: sell-create GUI flow (click empty slot -> SELL_CREATE -> accept -> chat price ->
    placeSellListing); quick-sell + buy-confirm from earlier.
  - Phase 2 polish: home rename (name column + renameHome + /sethome <slot> [name] + GUI rename
    prompt); GUIListener.java genuine § mojibake fully removed.
- 2026-08-15: Phases 6, 7, 8 (partial) complete (BUILD SUCCESS, jars deployed).
- 2026-08-15: Phase 3 (Earth Magic + tooltips), Phase 5 quest scaling, Phase 4a (portal removal) — BUILD SUCCESS.
  - Earth magic redesign: single Earth Book (PDC earth_book, craftable BOOK+EMERALD+DIRT) with per-player unlocked
    tier pages. New EarthBookManager (earthbook_data.yml) + EarthBookListener (right-click page -> unlock; book -> open).
    Earth Pages are dimension-gated mob drops (Overworld low tiers / Nether Gold/Obsidian/NetherBricks/AncientDebris /
    End EndStone/EndStoneBricks, weighted lower-more-common). EarthBlockTier +END_STONE/END_STONE_BRICKS.
    Casting falls back highest->lowest unlocked tier with block+level, consumes 1 block, "no block to cast".
    Removed 8 craftable page recipes; removed CustomItemCraftListener earth-page handling.
  - Tooltip standardization: staff lore "Requires Magic Lv 1" + L/R-click mechanic; rune + rune dust "Mechanic";
    Earth Book "Requires Magic Lv 10"; Earth Page "Requires Magic Lv X".
  - Quest claim + progressive scaling: claimable state persisted to questdata.yml (was in-memory); each claim of a
    repeatable monster quest raises its target by difficulty increment (Peaceful +2, Easy +3, Medium +4, Hard +5,
    Nightmare +7) and scales rewards so harder pays more; /questbook uses scaled target. One-time/NPC quests unchanged.
  - Phase 4a: removed ancient realm portal (deleted realm/AncientDebrisPortalListener + AncientPortalFrameUtil;
    removed MagicStaffListener portal field/setter + lightning->AncientDebris trigger; removed Main wiring).
  - BUILD: javac compiles clean (201 class files) using cp_list.txt + Vault.jar; added PeacefulIgnoreListener + new
    EarthBook files to sources.txt (sources/build config had drifted - missing source + Vault dep).
- 2026-08-15: Phases 6, 7, 8 (partial) complete (BUILD SUCCESS, jars deployed).
  - Wand Secure (/wandsecure): owner-only (xxfatalg0dz UUID), per-area on/off, secure flag
    blocks breaking even for OP; regions persisted to regions.yml. Built in CoreEngine,
    reusing the /region + /markon stick selection.
  - No-Spawn 25k: NO_SPAWN_ZONE_RADIUS 500 -> 25000 + despawnNoSpawnZoneMonsters task (2s).
  - Market GUI: quick-sell input slot fixed (now accepts items), escape auto-sells via
    InventoryCloseEvent; buy confirm dialog + quickBuy setting (PlayerSettings).
  - NOTE: Phase 8 "sell menu" flow (click empty slot -> sell menu -> price) deferred.
  - NOTE: GUIListener.java has some pre-existing mojibake § in older string literals.
- 2026-08-15: Phase 1.5 + Phase 2 complete (BUILD SUCCESS, both jars deployed).
  - LightningChargeManager.onDrinkBottle: re-fixed to decrement the ACTUAL held stack
    (getItemInMainHand/getItemInOffHand) — event.setItem() never wrote back, so stacks of 2+ were infinite.
  - SettingsGUIManager: added LegacyComponentSerializer (fixes § codes rendering literally);
    rebuilt Homes GUI to 50 slots with rank gating (NONE=3, MEMBER=10, MEMBER_PLUS=25, MEMBER_PLUS_PLUS=50),
    click empty slot to save, click saved slot -> Teleport/Delete sub-menu.
  - PlayerRank: added maxHomes field. SetHomeCommand: 1-50 + rank cap.
  - GUIListener: handleHomesClick switch (save/options/locked/teleport/delete/back) + teleportToHome.
- 2026-08-15: Phase 1 complete (4 critical bugs fixed, BUILD SUCCESS, auto-deployed).
  - LightningChargeManager.onDrinkBottle: use event.setItem() (clone mutation doesn't write back).
  - ItemLevelListener: added ItemFactory, custom weapon check (GunZ=Melee99, DarkBow=Ranged70),
    revert-slot instead of drop when level too low.
  - MagicStaffListener: fast-cast now has 250ms cooldown floor + always consumes charges.
  - New PeacefulIgnoreListener: hostile mobs + phantoms ignore PEACEFUL players.
