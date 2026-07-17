# DifficultyEngine

A **per-player difficulty scaling** plugin for Paper 1.21.  
Every player on the server chooses their own challenge level — mobs that spawn near you are scaled to match your setting, and only yours.

---

## Commands

### `/difficulty`
**Aliases:** `/diff`, `/setdifficulty`

View or change your personal difficulty level.

| Usage | Description |
|---|---|
| `/difficulty` | Shows your current difficulty |
| `/difficulty peaceful` | Hostile mobs ignore you completely |
| `/difficulty easy` | Vanilla experience — no changes *(default)* |
| `/difficulty hard` | Mobs near you spawn tougher |
| `/difficulty nightmare` | Full nightmare mode — see below |

---

### `/hpbar`
**Aliases:** `/showhp`, `/healthbar`, `/hpdisplay`

**Toggles the live HP display above mob heads.**

When **ON** — every mob you hit shows its health above its head:

```
❤ 18 / 25
```

The number updates in real-time with each hit and disappears when the mob dies.  
Type `/hpbar` again to turn it off.

> **Tip:** The HP shown reflects actual scaled health — so a Nightmare zombie might show `❤ 30 / 30` instead of the vanilla `20 / 20`.

---

## Difficulty Tiers Explained

| Tier | Mob HP | Mob Damage | Mob Speed | Follow Range | Bonus Spawns |
|---|---|---|---|---|---|
| ☮ **Peaceful** | ×0.75 | ×0.75 | ×1.00 | 16 blocks | No |
| ✦ **Easy** | ×1.00 | ×1.00 | ×1.00 | 20 blocks | No |
| ⚔ **Hard** | ×1.25 | ×1.15 | ×1.05 | 32 blocks | No |
| ☠ **Nightmare** | ×1.50 | ×1.25 | ×1.15 | 64 blocks | Yes (+3 mobs every 30s) |

### Notes

- **Peaceful** — Mobs will not target you. If a Nightmare player is nearby, that protection is removed (the Nightmare player absorbs all aggro).
- **Easy** — Vanilla behavior. No stat changes.
- **Hard** — Mobs that spawn within 64 blocks of you receive boosted stats.
- **Nightmare** — Everything in Hard, plus:
  - Extra hostile mobs spawn near you every 30 seconds
  - Mobs have a 35% chance to re-target you even if they were targeting someone else
  - Your difficulty setting is saved and persists through server restarts

---

## Multiplayer Behavior

| Situation | Result |
|---|---|
| You're **Nightmare**, friend is **Peaceful** | Mobs spawn at Nightmare stats; your friend is left alone unless they're right next to you |
| You're **Easy**, friend is **Nightmare** | Mobs near you both spawn at Nightmare tier (highest nearby player wins) |
| You're **Peaceful** alone | Mobs spawn weakly and ignore you |

---

## FAQ

**Does my difficulty setting save when I log out?**  
Yes — your choice is saved to disk automatically and restored next time you join.

**Can I change my difficulty at any time?**  
Yes, as many times as you like. Only mobs that spawn *after* you change will be affected.

**Does `/hpbar` persist after I log out?**  
No — it's a session toggle. You'll need to run `/hpbar` again when you rejoin. This is intentional so it doesn't clutter everyone's screen by default.

**What counts as a "nearby" player for mob scaling?**  
Mobs check within a 64-block radius at the moment they spawn. The highest difficulty player in that radius determines the mob's stats.
