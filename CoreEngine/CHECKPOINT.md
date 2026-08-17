# CoreEngine — Module 1 (Dynamic Market Engine & Order Book) Build Checkpoint

## HOW TO RESUME IN A NEW CHAT
Start a new chat and say:
> "Continue CoreEngine Module 1, resume from C:\Users\Owner\Desktop\123\CoreEngine\CHECKPOINT.md"

The assistant should read this file first, then read only the source files relevant
to the NEXT unchecked phase (not the whole project) before writing more code.

## PROJECT INFO
- Location: `C:\Users\Owner\Desktop\123\CoreEngine` (sibling of `C:\Users\Owner\Desktop\123`
  which hosts the unrelated `DifficultyEngine` plugin — CoreEngine is a SEPARATE plugin,
  own pom.xml, own JAR, own plugin.yml, following the same pattern as the `seperate`
  (SeparatePlug) subfolder already in `123`).
- groupId: `net.yourserver`, artifactId: `CoreEngine`, main class:
  `net.yourserver.coreengine.CoreEngine`
- Target: Paper API 1.21-R0.1-SNAPSHOT, Java 21, HikariCP 5.1.0, SQLite (org.xerial:sqlite-jdbc)
- Database file: `plugins/CoreEngine/core_engine.db` (own DB, independent of DifficultyEngine's
  currency/account system)
- Build command (verified working in this environment):
  ```
  cmd.exe /c "%TEMP%\maven_extract\apache-maven-3.9.9\bin\mvn.cmd" -q -f "C:\Users\Owner\Desktop\123\CoreEngine\pom.xml" package
  ```
  (Portable Maven 3.9.9 extracted to `%TEMP%\maven_extract\apache-maven-3.9.9` — no `mvn` on PATH.
  JDK 21 available at `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot` — pom pins
  `<release>21</release>` so default JDK 25 on PATH still cross-compiles fine; if `mvn.cmd` picks
  the wrong JAVA_HOME, set `$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot'`
  before invoking.)
  Local `.m2` already has `paper-api-1.21-R0.1-SNAPSHOT` cached. HikariCP/sqlite-jdbc will
  resolve from Maven Central (confirmed direct internet access works).
- sqlite-jdbc version pinned: `3.49.1.0` (matches the jar already cached under
  `C:\Users\Owner\Desktop\123\libraries\org\xerial\sqlite-jdbc\3.49.1.0\`, and is a known-good
  recent release confirmed via Maven Central metadata).
- Confirmed API: `ItemStack.serializeAsBytes()` / `ItemStack.deserializeBytes(byte[])` exist on
  this exact cached paper-api jar (verified via `javap`) — full NBT/PDC-preserving byte[] round
  trip, Base64-encoded for TEXT storage in SQLite.

## KEY DESIGN DECISIONS (locked in — do not re-litigate in later sessions)
1. **Sell Listing purchase** (Slot 2 grid / My Sell Orders context): buyer clicks a listing ->
   instant delivery to buyer's inventory (or claimable escrow if inventory full) + instant
   payment to seller's balance. Classic auction-house behavior.
2. **Buy Order fulfillment** (Slot 4 grid): a player holding the matching item+amount clicks a
   buy order -> seller is paid instantly from the buy order's escrowed funds, and the item goes
   into the FULFILLER's 90-day claimable escrow inbox (per spec: "Purchased items from completed
   Buy Orders remain claimable for up to 90 days"). Slot 51 "My Active Buy Orders" shows both
   pending (unfilled, cancellable) buy orders AND fulfilled/claimable escrow entries tied to buy
   orders the viewing player created.
3. **Rank listing caps** apply to the player's COMBINED active SELL + BUY order count (spec just
   says "active listings", no split given).
4. **Economy**: fully internal via `player_profiles.balance` (DOUBLE), no Vault dependency,
   matches the given DatabaseManager schema exactly.
5. **Anti-dupe**: item removed from world instantly on listing (serialized to Base64 bytes via
   `ItemStack.serializeAsBytes()`, full NBT/PDC fidelity preserved); per-player `ReentrantLock`s
   acquired in deterministic UUID-order for any 2-party trade (deadlock-safe); an in-memory
   `processingOrderIds` guard (ConcurrentHashMap-backed set) prevents double-processing of the
   same order across threads; all money/order mutations happen inside ONE JDBC transaction
   (`conn.setAutoCommit(false)` + commit/rollback); item/PDC state is re-verified immediately
   before and after each mutation.
6. **Disconnect protection**: DB commit (money movement + order/escrow state) always completes
   FIRST and is authoritative. Inventory delivery is only attempted after confirming the target
   player is online on the main thread; if not, the item safely falls back into the claimable
   escrow inbox (never lost, never duplicated).
7. **Quick-Sell Floor (Slot 6)**: instantly deletes the item from existence (no escrow, no
   listing) and instantly pays the player `amount * configured buyback unit price` for that
   material (from `config.yml`). Materials with no configured buyback price cannot be quick-sold.
8. **Search filter (Slot 0)**: opens chat input via a `MarketChatListener` (AsyncChatEvent-based)
   that captures the player's next chat line as a material-name substring filter, applied to
   whichever grid (sell listings / buy orders) they were viewing, then reopens the GUI.
9. **Pagination**: sorted strictly ascending by price-per-unit; page 1 = lowest price. 27 slots
   per page (slots 18-44).
10. GUI type string for Module 1: `"MARKET_MAIN"` (matches existing GUIListener switch case).
    Sub-views (my-sells, my-buys, history, confirm) are tracked via `MarketSession` state per
    player UUID, NOT via separate `CustomGUIHolder` guiType strings, so the existing switch
    statement in `GUIListener` (with its other module stub cases untouched) still routes
    everything through `handleMarketClick`.
11. Existing stub cases in `GUIListener` for `CREATE_A_VILLE_SETTINGS` / `SPAWNER_MENU` /
    `HOMES_MENU` are LEFT ALONE (no-op) — out of scope for Module 1, reserved for future modules.
12. `plugin.yml` keeps ALL commands from the provided foundational file (including future-module
    commands like `home`, `markon`, `markoff`, `shards`, `settings`) so future modules can be
    slotted in without editing plugin.yml again, but only `market`, `sell`, `buy`, `givemember`
    get real CommandExecutor implementations in Module 1.

## PHASE CHECKLIST

### Phase 1 — Project Scaffolding & Schema ✅ COMPLETE
- [x] pom.xml
- [x] src/main/resources/plugin.yml
- [x] src/main/resources/config.yml
- [x] database/DatabaseManager.java (extended schema: status column, market_escrow,
      market_transactions tables)
- [x] CoreEngine.java (skeleton onEnable/onDisable, DB init only, no managers yet)
- [x] gui/CustomGUIHolder.java (copied as-is from foundation for now, extended in Phase 6)
- [x] gui/GUIListener.java (copied as-is stub from foundation for now, implemented in Phase 7)
- [x] Compile check (mvn package) — SUCCEEDED, target/CoreEngine.jar produced (14.5MB shaded)

### Phase 2 — Core Models & Utilities ✅ COMPLETE
- [x] market/OrderType.java
- [x] market/OrderStatus.java
- [x] market/MarketOrder.java
- [x] market/EscrowEntry.java (nested Reason enum: SELL_EXPIRED, BUY_FULFILLED, INVENTORY_FULL_FALLBACK)
- [x] market/TransactionRecord.java (nested Type enum: SELL_PURCHASE, BUY_FULFILL, QUICK_SELL)
- [x] market/ItemSerialization.java (Base64 via ItemStack.serializeAsBytes()/deserializeBytes())
- [x] util/MoneyFormat.java (comma-formatted "$2,360 Total" style strings)
- [x] util/PDCKeys.java (NamespacedKey registry for GUI control tagging)
- [x] rank/PlayerRank.java (NONE/MEMBER/MEMBER_PLUS/MEMBER_PLUS_PLUS with getMaxListings())
- [x] rank/RankManager.java (get/set rank_tier, lazy profile creation)
- [x] Compile check (mvn package) — SUCCEEDED, all 16 class files present in target/classes

### Phase 3 — Database DAO & Economy ✅ COMPLETE
- [x] database/dao/MarketDao.java (all SQL/CRUD + transactional fulfill/cancel/expire methods)
- [x] economy/EconomyManager.java (balance read/adjust with per-player locks, atomic
      SELECT+UPDATE inside transactions)
- [x] config/ConfigManager.java (NPC location, expiration hours, rank caps, buyback prices —
      needed by DAO/economy for constants)
- [x] market/MarketLockRegistry.java (created here because EconomyManager depends on it;
      per-player ReentrantLocks with sorted-UUID lockAll/unlockAll)
- [x] Compile check

### Phase 4 — Market Core Engine ✅ COMPLETE
- [x] market/MarketManager.java (place/cancel/fulfill sell+buy orders, dynamic average price
      calc, rank cap enforcement, anti-dupe orchestration)
- [x] market/MarketExpirationTask.java (BukkitRunnable: 24h sell-listing expiry -> escrow,
      90-day escrow cleanup)
- [x] market/MarketResult.java (outcome codes enum used by commands/GUI)
- [x] Compile check — SUCCEEDED (mvn compile, 0 errors)

### Phase 5 — Commands ✅ COMPLETE
- [x] commands/MarketCommand.java (/market - opens Yes/No confirm dialog)
- [x] commands/SellCommand.java (/sell worth <price> | /sell <price>)
- [x] commands/BuyCommand.java (/buy <item_type> <amount> <price_per_item>)
- [x] commands/GiveMemberCommand.java (/givemember <user> <1|2|3>)
- [x] Wire command registration + executors into CoreEngine.java (full onEnable wiring done now)
- [x] Compile check — SUCCEEDED
      NOTE: to make CoreEngine compile, minimal stubs of MarketChatListener +
      PlayerConnectionListener were created now (real implementations in Phase 7),
      and PDCKeys.guiAction() / escrowId() / transactionId() were used by the GUI.

### Phase 6 — GUI System ✅ COMPLETE (build side)
- [x] gui/CustomGUIHolder.java (extended with session-state fields)
- [x] gui/MarketSession.java (per-player transient GUI state: View, BrowseTab, page,
      search filter, awaitingConfirm)
- [x] gui/MarketGUIManager.java (main grid, my-sells, my-buys, history sub-pages,
      quick-sell floor, confirm dialog — full 54-slot layout per spec; listing icons
      carry orderId/escrowId PDC tags + hover lore with unit price & "Total")
- [x] Compile check — SUCCEEDED (mvn compile)
      NOTE: click ROUTING (buyFromSellListing / fulfillBuyOrder / cancelOrder / claim /
      quick-sell / page nav / confirm yes-no) is still TODO in Phase 7 (GUIListener
      handleMarketClick) + MarketChatListener (search capture) + PlayerConnectionListener
      (disconnect session cleanup).

### Phase 7 — Listeners ✅ COMPLETE
- [x] gui/GUIListener.java — full handleMarketClick implementation (page nav, view switching,
      buy/fulfill, cancel, claim, quick-sell confirm, confirm-teleport yes/no; other module
      cases left as no-op stubs, untouched)
- [x] listeners/MarketChatListener.java (search filter chat capture via AsyncChatEvent)
- [x] listeners/PlayerConnectionListener.java (disconnect session cleanup; disconnect-safe
      delivery already guaranteed by MarketManager's escrow fallback)
- [x] Wire listener registration into CoreEngine.java (done in Phase 5)
- [x] Compile check — SUCCEEDED
      NOTE: fixed MarketSession searchMode brace corruption; added ChronoUnit import to
      MarketDao; fixed literal "u00a7" → real § in MarketGUIManager color codes.

### Phase 8 — Final Wiring, Build & Verification ✅ COMPLETE
- [x] Full CoreEngine.java onEnable()/onDisable() wiring review (managers constructed in
      dependency order; expiration task started; listeners + commands registered)
- [x] Full `mvn package` clean build — 0 errors, target/CoreEngine.jar produced (~14.6MB)
- [x] Spec compliance pass — all Module 1 requirements implemented (see INSTALL.md + summary)
- [x] Install instructions written — INSTALL.md (copy jar, config.yml notes, commands, DB)
- [x] CHECKPOINT.md fully checked off, final summary appended

### REFINEMENT (user-requested, this session)
- [x] Dynamic Price Engine now SUPPLY-AND-DEMAND driven: Volume-Weighted Average Price of
      ACTUAL completed trades (SELL_PURCHASE + BUY_FULFILL + QUICK_SELL) over
      market.dynamic-price.window-hours. Placing a listing no longer changes the price.
      Quick-selling feeds QUICK_SELL rows → adjusts (drags) the price. Falls back to the
      spec (lowest sell + highest buy)/2 only when no recent trades exist (cold start).
- [x] Market GUI stats row (slots 46/48/50/52) now shows the live dynamic Market Price,
      the player's balance, remaining listing slots, and the current page.

### REFINEMENT 2 (user-requested — order auto-match + robust anti-manipulation price)
- [x] ORDER CROSSING ENGINE: placing a SELL now auto-fills resting BUY orders bidding >= the
      ask (highest bid first, at the BUY price — seller gets >= ask); leftover stays as a
      sell listing. Placing a BUY auto-fills resting SELL listings asking <= the bid (cheapest
      first, at the SELL price — buyer pays <= bid); leftover escrow stays active. Manual
      click-to-fill/buy is unchanged. Partial fills supported via reduceOrderRemaining +
      remaining-aware buyFromSellListing.
- [x] ROBUST BASE PRICE (anti-manipulation): quick-sell + displayed Market Price now use the
      MEDIAN of confirmed ORGANIC trades (SELL_PURCHASE + BUY_FULFILL; QUICK_SELL excluded to
      avoid a feedback spiral) over market.dynamic-price.base-price-sample-size (default 100k,
      10k-1M range). central-measure config = median|mean. Per-material for quick-sell, with
      config buyback-prices as the cold-start fallback floor.
- [x] Removed the now-superseded getRecentTradeVwap (window-hours VWAP) + window-hours config.

### REFINEMENT 3 (user-requested — Vault economy bridge)
- [x] EconomyManager now routes through Vault.getEconomy() when Vault is present (softdepend
      [Vault] in plugin.yml + net.milkbowl.vault:Vault:1.7.3 provided dep in pom.xml), falling
      back to the internal player_profiles.balance when Vault is absent. Market now shares
      EssentialsX money (/bal, /pay) when Vault is installed.
- [x] Vault 1.7.3 plugin jar downloaded + installed into the server plugins folder + local .m2
      (install:install-file). CoreEngine.jar redeployed.

### MODULE 2 — Create-a-Ville Hub & Player Tools ✅ BUILT (this session)
- [x] Create-a-Ville hub GUI (/settings) — buttons: Homes, Market, Teleport, Pay, Balance,
      Privacy(ghost), Nightvision, Remove Monsters, HUD Stats, TP Privacy.
- [x] Homes system: /home [slot], /sethome <slot>, /delhome <slot>, Homes GUI (player_homes table).
- [x] Teleport: /tp <player>, /tphere <player> (auto-accept if target has /tpauto).
- [x] TP privacy: /tpauto toggle + TpPrivacy EVERYONE/PARTY/NOBODY (party via DifficultyEngine
      reflection), TeleportManager.
- [x] HUD Stats sidebar (StatsHudTask): balance, kills, deaths, shards, time played.
- [x] Toggles: ghost(privacy/vanish), nightvision (potion), remove-monsters (MonsterSpawnListener).
- [x] Economy: /pay <player> <amount>, /bal (Vault/EssentialsX money).
- [x] Wired into CoreEngine (managers + 12 command executors + 4 listeners) + GUIListener routing.
- [x] Full mvn package build clean; CoreEngine.jar redeployed.
      NOTE: Party auto-TP relies on DifficultyEngine PartyManager via reflection (soft, no compile
      dep). Monster gold now bridges to Vault (see DifficultyEngine GoldManager change).

## PROGRESS LOG
- 2026-08-12: Session 1 complete. Created project at C:\Users\Owner\Desktop\123\CoreEngine
  (separate plugin from DifficultyEngine). Phase 1 (scaffolding/schema) and Phase 2 (core
  models/utilities) both written and verified compiling cleanly via
  `mvn clean package` (portable Maven 3.9.9, JDK 21) — produces
  target/CoreEngine.jar (shaded, ~14.5MB). No errors. 16 class files confirmed present.
- 2026-08-12: Session 2 complete. Phase 3 (MarketDao + EconomyManager + ConfigManager +
  MarketLockRegistry) and Phase 4 (MarketManager + MarketExpirationTask + MarketResult)
  written; `mvn compile` verified clean. Notes for later sessions:
  - MarketDao was written via PowerShell append; avoid the editor insert_line approach
    for large files (it corrupted structure). Files are ASCII-clean (no BOM).
  - MarketManager runs synchronously on the main thread by design (inventory access);
    per-player ReentrantLocks + conditional SQL status updates + in-memory
    processingOrderIds guard provide the anti-double-processing guarantees.
  - MarketExpirationTask.start() must be called from CoreEngine.onEnable() (now done).
- 2026-08-12: Session 3 complete. Phase 5 (4 command executors + full CoreEngine wiring with
  getMarketManager/getMarketGuiManager/getRankManager/getEconomyManager accessors + command
  registration + listener registration + expiration task start) and Phase 6 (CustomGUIHolder
  extended, MarketSession, MarketGUIManager full 54-slot build) written; `mvn compile` clean.
  Notes for later sessions:
  - Paper 1.21 chat event class is io.papermc.paper.event.player.AsyncChatEvent (NOT
    org.bukkit.event.player.AsyncChatEvent).
  - MarketGUIManager uses Bukkit.createInventory + CustomGUIHolder("MARKET_MAIN", session);
    all PDC action strings are constants on MarketGUIManager (ACTION_*).
  - Click routing for every ACTION_* still needs to be implemented in Phase 7 handleMarketClick.

- 2026-08-14: Session 4 complete — MODULE 1 FINISHED. Phase 7 (GUIListener full click routing,
  MarketChatListener search capture, PlayerConnectionListener disconnect cleanup) and Phase 8
  (final wiring review, clean build, spec-compliance pass, INSTALL.md) done. `mvn clean package`
  passes with 0 errors. Plus the user-requested refinement: the Dynamic Price Engine is now
  supply-and-demand driven (VWAP of completed trades incl. quick-sells; listings no longer move
  the price) with the spec formula as cold-start fallback, and the GUI stats row shows the live
  market price / balance / listing slots / page.
- 2026-08-14: REFINEMENT 2 (order auto-match + robust price) implemented and building clean:
  crossing engine in MarketManager (crossSellAgainstBuyOrders / crossBuyAgainstSellListings),
  new MarketDao methods (findCrossableBuyOrders / findCrossableSellListings / reduceOrderRemaining
  / getOrganicTradeUnitPrices), robust median pricing (getAverageMarketPrice / getQuickSellBasePrice
  / centralMeasure / median), config switched to base-price-sample-size + central-measure.
  Removed getRecentTradeVwap. INSTALL.md + CHECKPOINT updated.

## CURRENT STATUS
MODULE 1 COMPLETE (incl. order auto-match crossing engine + robust anti-manipulation median
price). Building cleanly to target/CoreEngine.jar. Future modules (Homes, Spawners, Shards,
Create-a-Ville settings) can be slotted into the existing GUIListener switch cases + plugin.yml
commands without touching Module 1.

