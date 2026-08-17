# CoreEngine — Module 1 (Dynamic Market Engine & Order Book) — Install Guide

## What this is
A standalone Paper 1.21+ plugin (Java 21) providing a full two-sided market
with sell listings, buy orders, escrow, a server quick-sell floor, a
supply-and-demand dynamic price engine, rank-based listing caps, and strict
anti-duplication guarantees.

It is **independent** of the `DifficultyEngine` plugin in the parent `123`
folder — its own JAR, own `plugin.yml`, own SQLite database
(`core_engine.db`), and its own internal economy (`player_profiles.balance`).

## Build
```
cmd.exe /c "%TEMP%\maven_extract\apache-maven-3.9.9\bin\mvn.cmd" -f "C:\Users\Owner\Desktop\123\CoreEngine\pom.xml" clean package
```
Output: `C:\Users\Owner\Desktop\123\CoreEngine\target\CoreEngine.jar`
(shaded, ~14.6 MB — bundles HikariCP + sqlite-jdbc).

## Install
1. Copy `target\CoreEngine.jar` into your server's `plugins\` folder
   (e.g. `C:\Users\Owner\Desktop\minecraft server\server\plugins\`).
2. Start (or reload) the server. On first run the plugin creates
   `plugins\CoreEngine\` containing `config.yml` and `core_engine.db`.
3. Edit `plugins\CoreEngine\config.yml` to set:
   - `market.npc-teleport.*` — the Market NPC location for `/market` "Yes".
   - `market.buyback-prices` — per-material quick-sell FLOOR (fallback when
     there is no trade history yet; the live quick-sell price is the robust
     market average once trades exist).
   - `market.rank-listing-caps` — listing caps per rank tier.
   - `market.dynamic-price.base-price-sample-size` — how many recent confirmed
     trades to sample for the average (default 100000; 10k-1M).
   - `market.dynamic-price.central-measure` — `median` (default, anti-
     manipulation) or `mean`.

## How the market matches orders (auto-fill)
- **Selling**: if a resting BUY order bids >= your sell price, your items
  auto-fill that order at the BUY order's price (you get >= your ask). Any
  leftover stays as a sell listing that players can click to buy.
- **Buying**: if a resting SELL listing asks <= your buy price, your order
  auto-fills it at the SELL listing's price (you pay <= your bid). Leftover
  escrow stays as an active buy order.
- Players can still **click any order** to fill/buy it manually at that
  shop's listed price.
- **Quick-sell** pays the robust market average (median of confirmed organic
  buys/sells), not a fixed price, and is resistant to manipulation.

## Economy note (Vault question)
Module 1 ships with a **self-contained internal economy**
(`player_profiles.balance`), so it works with zero external dependencies.
If you later want the market to share money with other plugins (e.g.
EssentialsX `/bal` / `/pay`), the clean path is to add **Vault** as a soft
dependency and route `EconomyManager` through `Vault.getEconomy()` instead
of the internal balance column — that is a small, isolated change to
`EconomyManager` only (see the "Vault" section in the final summary).

## Commands
| Command | Permission | Description |
|---------|-----------|-------------|
| `/market` | `coreengine.use` | Yes/No prompt: teleport to NPC or open the Market GUI |
| `/sell worth <price>` or `/sell <price>` | `coreengine.use` | List the held stack for the total price |
| `/buy <item> <amount> <price_per_item>` | `coreengine.use` | Create a buy order (escrows amount × price) |
| `/givemember <player> <1\|2\|3>` | `coreengine.admin` | Grant Member / Member+ / Member++ |

## Database
- `market_orders` — sell/buy orders with status + expiration.
- `market_escrow` — claimable inbox (expired sells, fulfilled buys, fallbacks).
- `market_transactions` — permanent buy/sell/quick-sell history (feeds the
  dynamic price engine).
- `player_profiles` — rank tier + balance + future module fields.

## Expiration
- Sell listings expire after 24 hours → item moves to the seller's
  Unclaimed Escrow inbox.
- Buy-order items remain claimable for 90 days, then are auto-purged.
- Sweep runs every 5 minutes (configurable).
