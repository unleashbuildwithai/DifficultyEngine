"""
apply_earth_page_changes.py
Applies 3 targeted changes to MagicStaffListener.java:
1. Add BookMeta import
2. Smarter castEarth fail message (shows which page to craft)
3. Add onReadEarthPage event handler + buildEarthPageReadable method
"""

path = r'c:\Users\Owner\Desktop\123\src\main\java\com\yourname\difficulty\magic\MagicStaffListener.java'

with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

original = content

# ── 1. Add BookMeta import ────────────────────────────────────────────────────
OLD_IMPORT = 'import org.bukkit.inventory.ItemStack;'
NEW_IMPORT  = 'import org.bukkit.inventory.ItemStack;\nimport org.bukkit.inventory.meta.BookMeta;'
if OLD_IMPORT in content and 'BookMeta' not in content:
    content = content.replace(OLD_IMPORT, NEW_IMPORT, 1)
    print("✓ Added BookMeta import")
else:
    print("- BookMeta import already present or anchor not found")

# ── 2. Smarter castEarth fail message ─────────────────────────────────────────
OLD_MSG = ('                player.getInventory().addItem(itemFactory.buildRune(MagicElement.EARTH, 1));\n'
           '                player.sendActionBar("§2[Earth] §cNeed a block + Earth Magic Page in inventory! §8(Lv§a" + magicLevel + "§8)");\n'
           '                return;')

NEW_MSG = ('                player.getInventory().addItem(itemFactory.buildRune(MagicElement.EARTH, 1));\n'
           '                // Tell the player exactly which Earth Magic Page to craft\n'
           '                EarthBlockTier missingPage = null;\n'
           '                for (EarthBlockTier t : EarthBlockTier.values()) {\n'
           '                    if (magicLevel >= t.levelRequired && playerHasBlock(player, t.material)) {\n'
           '                        missingPage = t;\n'
           '                    }\n'
           '                }\n'
           '                if (missingPage != null) {\n'
           '                    player.sendActionBar("§2[Earth] §7Craft §2Earth Page: " + missingPage.displayName\n'
           '                        + " §8(Book + " + missingPage.material.name() + " + String)"\n'
           '                        + " §7then §aright-click §7the page to read it!");\n'
           '                } else {\n'
           '                    player.sendActionBar("§2[Earth] §cNeed a throwable block + Earth Magic Page! "\n'
           '                        + "§8Craft: §7Book + Block + String §8(Lv §a" + magicLevel + "§8+)");\n'
           '                }\n'
           '                return;')

if OLD_MSG in content:
    content = content.replace(OLD_MSG, NEW_MSG, 1)
    print("✓ Updated castEarth fail message")
else:
    print("- castEarth fail message anchor not found — skipping")

# ── 3. Add onReadEarthPage + buildEarthPageReadable before the closing } ───────
EARTH_PAGE_CODE = '''
    // ══════════════════════════════════════════════════════════════════════════
    //  EARTH MAGIC PAGE — RIGHT-CLICK TO READ
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * When a player right-clicks while holding an Earth Magic Page (a BOOK item
     * with our PDC key), open a written-book GUI showing what that page does.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onReadEarthPage(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player    player = event.getPlayer();
        ItemStack hand   = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() != Material.BOOK) return;
        if (!hand.hasItemMeta()) return;

        var pdc = hand.getItemMeta().getPersistentDataContainer();
        for (EarthBlockTier tier : EarthBlockTier.values()) {
            org.bukkit.NamespacedKey pageKey = itemFactory.getEarthPageKey(tier);
            if (pageKey == null || !pdc.has(pageKey, org.bukkit.persistence.PersistentDataType.BYTE)) continue;
            event.setCancelled(true);
            player.openBook(buildEarthPageReadable(tier));
            return;
        }
    }

    /**
     * Builds a temporary WRITTEN_BOOK describing the given Earth Magic Page tier.
     * Opened via {@link Player#openBook(ItemStack)} — displayed only, not given to the player.
     */
    private ItemStack buildEarthPageReadable(EarthBlockTier tier) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta != null) {
            meta.setTitle("§2Earth Magic Page");
            meta.setAuthor("DifficultyEngine");
            meta.setGeneration(BookMeta.Generation.ORIGINAL);
            meta.addPage(
                "§2§l─ Earth Magic Page ─\\n\\n" +
                "§7Tier: " + tier.displayName + "\\n" +
                "§8Req: §aMagic Lv " + tier.levelRequired + "\\n\\n" +
                "§7Carry this page in\\nyour inventory to\\nthrow §2" + tier.displayName +
                "§7 blocks\\nwith the §2Earth Staff§7.\\n\\n" +
                "§8Not consumed — keep\\nit in your bag!"
            );
            meta.addPage(
                "§2§l─ Block Stats ─\\n\\n" +
                "§7Block: §f" + tier.material.name() + "\\n\\n" +
                "§61st hit (TRAP):\\n" +
                "§c  " + (int)(tier.trapDamage/2) + " ❤  §7+ §cSlowness\\n\\n" +
                "§62nd hit (SUFFOCATE):\\n" +
                "§c  " + (int)(tier.suffocateDamage/2) + " ❤\\n\\n" +
                "§8Higher tier blocks\\ndeal more damage!"
            );
            meta.addPage(
                "§2§l─ Crafting More ─\\n\\n" +
                "§7Once you have a page,\\nthe recipe is in your\\nrecipe book.\\n\\n" +
                "§6Recipe:\\n§7Book\\n+ §f" + tier.material.name() + "\\n§7+ String\\n\\n" +
                "§8[DifficultyEngine]\\n§8Earth Magic System"
            );
            book.setItemMeta(meta);
        }
        return book;
    }
'''

# Insert just before the last closing brace of the class
if 'onReadEarthPage' not in content:
    last_brace = content.rfind('\n}')
    if last_brace != -1:
        content = content[:last_brace] + EARTH_PAGE_CODE + '\n}'
        print("✓ Added onReadEarthPage + buildEarthPageReadable")
    else:
        print("- Could not find closing brace to insert earth page methods")
else:
    print("- onReadEarthPage already present")

# ── Save ──────────────────────────────────────────────────────────────────────
with open(path, 'w', encoding='utf-8') as f:
    f.write(content)

print(f"\nDone. Content changed: {content != original}")
print(f"BookMeta in imports: {'import org.bukkit.inventory.meta.BookMeta' in content}")
print(f"onReadEarthPage present: {'onReadEarthPage' in content}")
print(f"buildEarthPageReadable present: {'buildEarthPageReadable' in content}")
print(f"Smarter fail message present: {'missingPage' in content}")
print(f"Package line: {content[:50]}")
