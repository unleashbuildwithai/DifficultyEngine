package com.yourname.difficulty.skills;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * SkillGUIListener — Prevents players from taking items out of the /mystats GUI.
 * Identifies the GUI by its title string.
 */
public class SkillGUIListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (isSkillGui(title)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        if (isSkillGui(title)) {
            event.setCancelled(true);
        }
    }

    /** Returns true if the inventory title matches any skill GUI variant. */
    private boolean isSkillGui(String title) {
        if (title == null) return false;
        return title.startsWith("§8✦ §6Skill Tree §8✦")
            || title.startsWith("§8✦ §6")  && title.endsWith("'s Skills §8✦");
    }
}
