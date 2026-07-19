package com.yourname.difficulty.quests;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * NpcQuestSpawner — manages quest villager NPCs in the world.
 *
 * NPCs are placed by admins via  /questnpc spawn <questId>
 * Their UUID + location is stored in npc_positions.yml.
 * On WorldLoadEvent the spawner restores any NPC whose entity no longer exists.
 *
 * PDC on each villager:
 *   NamespacedKey "quest_npc_id"  (INTEGER) — the quest definition ID
 *
 * Admin commands:
 *   /questnpc spawn <id>   — spawn NPC for quest <id> at your feet
 *   /questnpc remove <id>  — remove NPC for quest <id> from the world
 *   /questnpc list         — list all registered NPCs and their status
 *   /questnpc info <id>    — show quest definition for <id>
 */
public class NpcQuestSpawner implements Listener, CommandExecutor, TabCompleter {

    public static final String PDC_QUEST_NPC_ID = "quest_npc_id";

    private final JavaPlugin   plugin;
    private final NamespacedKey npcKey;

    private final File                 posFile;
    private YamlConfiguration          posData;

    /** questId → entity UUID */
    private final Map<Integer, UUID> npcEntityMap = new HashMap<>();

    public NpcQuestSpawner(JavaPlugin plugin) {
        this.plugin  = plugin;
        this.npcKey  = new NamespacedKey(plugin, PDC_QUEST_NPC_ID);
        this.posFile = new File(plugin.getDataFolder(), "npc_positions.yml");
        load();
    }

    // ── WorldLoadEvent — restore NPCs after chunk reload ─────────────────────

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin,
                this::restoreMissingNpcs, 40L); // 2 s delay
    }

    /** Re-spawn any quest NPC whose entity UUID is no longer valid. */
    public void restoreMissingNpcs() {
        if (!posData.isConfigurationSection("npcs")) return;

        for (String idStr : posData.getConfigurationSection("npcs").getKeys(false)) {
            int questId;
            try { questId = Integer.parseInt(idStr); }
            catch (NumberFormatException e) { continue; }

            NpcQuestDef def = NpcQuestRegistry.byId(questId);
            if (def == null) continue;

            String path = "npcs." + questId;
            String worldName = posData.getString(path + ".world", "world");
            double x = posData.getDouble(path + ".x");
            double y = posData.getDouble(path + ".y");
            double z = posData.getDouble(path + ".z");
            String uuidStr = posData.getString(path + ".uuid", "");

            // Check if entity still exists
            boolean exists = false;
            try {
                UUID uuid = UUID.fromString(uuidStr);
                exists = plugin.getServer().getEntity(uuid) instanceof Villager;
                if (exists) { npcEntityMap.put(questId, uuid); }
            } catch (IllegalArgumentException ignored) {}

            if (!exists) {
                World w = plugin.getServer().getWorld(worldName);
                if (w == null) continue;
                Location loc = new Location(w, x, y, z);
                spawnNpc(def, loc, true);
                plugin.getLogger().info("[NpcQuestSpawner] Restored quest NPC #" + questId
                        + " (" + def.npcName + ") in " + worldName);
            }
        }
    }

    // ── Spawn / remove ────────────────────────────────────────────────────────

    private Villager spawnNpc(NpcQuestDef def, Location loc, boolean silent) {
        Villager v = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
        v.setCustomName(colourName(def));
        v.setCustomNameVisible(true);
        v.setAI(false);
        v.setInvulnerable(true);
        v.setSilent(true);
        v.setVillagerType(Villager.Type.PLAINS);
        v.getPersistentDataContainer().set(npcKey, PersistentDataType.INTEGER, def.id);

        npcEntityMap.put(def.id, v.getUniqueId());
        savePosition(def, loc, v.getUniqueId());

        if (!silent) plugin.getLogger().info("[NpcQuestSpawner] Spawned NPC #" + def.id
                + " (" + def.npcName + ") at " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
        return v;
    }

    private String colourName(NpcQuestDef def) {
        if (def.secret) return "§8[?] §7" + def.npcName;
        String dim = switch (def.dimension) {
            case "world_nether"  -> "§c";
            case "world_the_end" -> "§d";
            default              -> "§a";
        };
        return dim + "✦ " + def.npcName;
    }

    private void removeNpc(int questId) {
        UUID entityUuid = npcEntityMap.remove(questId);
        if (entityUuid != null) {
            var entity = plugin.getServer().getEntity(entityUuid);
            if (entity != null) entity.remove();
        }
        posData.set("npcs." + questId, null);
        save();
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void savePosition(NpcQuestDef def, Location loc, UUID entityUuid) {
        String path = "npcs." + def.id;
        posData.set(path + ".world", loc.getWorld().getName());
        posData.set(path + ".x", loc.getX());
        posData.set(path + ".y", loc.getY());
        posData.set(path + ".z", loc.getZ());
        posData.set(path + ".uuid", entityUuid.toString());
        save();
    }

    private void load() {
        posData = YamlConfiguration.loadConfiguration(posFile);
        plugin.getLogger().info("[NpcQuestSpawner] Loaded NPC position data.");
    }

    private void save() {
        try { posData.save(posFile); }
        catch (IOException e) { plugin.getLogger().warning("[NpcQuestSpawner] Save failed: " + e.getMessage()); }
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    /**
     * Returns the quest ID stored on a villager entity via PDC, or -1 if not a quest NPC.
     */
    public int getQuestId(Villager v) {
        return v.getPersistentDataContainer()
                .getOrDefault(npcKey, PersistentDataType.INTEGER, -1);
    }

    // ── /questnpc command ─────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        if (!sender.hasPermission("difficultyengine.cape.admin")) {
            sender.sendMessage("§c✗ §7No permission.");
            return true;
        }

        if (args.length == 0) { sendHelp(sender, label); return true; }

        return switch (args[0].toLowerCase()) {
            case "spawn"  -> cmdSpawn(sender, args);
            case "remove" -> cmdRemove(sender, args);
            case "list"   -> cmdList(sender);
            case "info"   -> cmdInfo(sender, args);
            default       -> { sendHelp(sender, label); yield true; }
        };
    }

    private boolean cmdSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can spawn NPCs.");
            return true;
        }
        if (args.length < 2) { sender.sendMessage("§cUsage: /questnpc spawn <id>"); return true; }

        int id;
        try { id = Integer.parseInt(args[1]); }
        catch (NumberFormatException e) { sender.sendMessage("§cInvalid ID."); return true; }

        NpcQuestDef def = NpcQuestRegistry.byId(id);
        if (def == null) { sender.sendMessage("§cNo quest found with ID " + id + "."); return true; }

        if (npcEntityMap.containsKey(id)) {
            sender.sendMessage("§eNPC for quest #" + id + " already exists. Use /questnpc remove first.");
            return true;
        }

        Location loc = player.getLocation();
        spawnNpc(def, loc, false);
        sender.sendMessage("§6✦ §7Spawned §eQuest NPC #" + id + " §8(" + def.npcName + ")§7 at your location.");
        return true;
    }

    private boolean cmdRemove(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage("§cUsage: /questnpc remove <id>"); return true; }
        int id;
        try { id = Integer.parseInt(args[1]); }
        catch (NumberFormatException e) { sender.sendMessage("§cInvalid ID."); return true; }

        removeNpc(id);
        sender.sendMessage("§6✦ §7Removed NPC for quest §e#" + id + "§7.");
        return true;
    }

    private boolean cmdList(CommandSender sender) {
        sender.sendMessage("§8" + "═".repeat(40));
        sender.sendMessage("  §6✦ Quest NPC Registry §8— §7" + npcEntityMap.size() + " placed");
        sender.sendMessage("§8" + "═".repeat(40));
        if (npcEntityMap.isEmpty()) {
            sender.sendMessage("  §7No NPCs placed yet. Use §e/questnpc spawn <id>§7.");
        } else {
            for (var entry : new TreeMap<>(npcEntityMap).entrySet()) {
                NpcQuestDef def = NpcQuestRegistry.byId(entry.getKey());
                String name = def != null ? def.npcName : "?";
                boolean alive = plugin.getServer().getEntity(entry.getValue()) != null;
                sender.sendMessage("  §e#" + entry.getKey() + " §7" + name
                        + (alive ? " §a[active]" : " §c[missing]"));
            }
        }
        sender.sendMessage("§8" + "═".repeat(40));
        return true;
    }

    private boolean cmdInfo(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage("§cUsage: /questnpc info <id>"); return true; }
        int id;
        try { id = Integer.parseInt(args[1]); }
        catch (NumberFormatException e) { sender.sendMessage("§cInvalid ID."); return true; }

        NpcQuestDef def = NpcQuestRegistry.byId(id);
        if (def == null) { sender.sendMessage("§cNo quest #" + id + " in registry."); return true; }

        sender.sendMessage("§8" + "═".repeat(40));
        sender.sendMessage("  §6#" + def.id + " §e" + def.title
                + (def.secret ? " §8[SECRET]" : ""));
        sender.sendMessage("  §7NPC: §f" + def.npcName + "  §7Dim: §f" + def.dimension);
        if (def.isKillQuest())
            sender.sendMessage("  §7Req: §cKill §f" + def.killCount + "× §c" + def.killTarget.name());
        if (def.isCollectQuest())
            sender.sendMessage("  §7Req: §aCollect §f" + def.collectCount + "× §a" + def.collectItem.name());
        if (def.hasHiddenTrigger())
            sender.sendMessage("  §7Hidden: §d" + def.hiddenCount + "× " + def.hiddenItem.name()
                    + " §8→ §6+" + def.bonusGold + " gp bonus");
        if (def.requireSneak)
            sender.sendMessage("  §7Mechanic: §eMust sneak to turn in");
        sender.sendMessage("  §7Reward: §6" + def.baseGold + " gp base"
                + (def.bonusGold > 0 ? " + §6" + def.bonusGold + " §7bonus" : ""));
        sender.sendMessage("§8" + "═".repeat(40));
        return true;
    }

    private void sendHelp(CommandSender s, String label) {
        s.sendMessage("§6/questnpc spawn <id>   §7— Place NPC at your feet");
        s.sendMessage("§6/questnpc remove <id>  §7— Despawn + unregister NPC");
        s.sendMessage("§6/questnpc list         §7— List all placed NPCs");
        s.sendMessage("§6/questnpc info <id>    §7— Show quest definition");
        s.sendMessage("§7IDs 1–150 = main quests, 151–300 = secret quests");
    }

    // ── Tab completion ─────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (!sender.hasPermission("difficultyengine.cape.admin")) return List.of();
        if (args.length == 1)
            return List.of("spawn", "remove", "list", "info").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        if (args.length == 2 && (args[0].equalsIgnoreCase("spawn") || args[0].equalsIgnoreCase("info")))
            return NpcQuestRegistry.all().stream()
                    .map(q -> String.valueOf(q.id))
                    .filter(s -> s.startsWith(args[1])).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("remove"))
            return npcEntityMap.keySet().stream()
                    .map(String::valueOf)
                    .filter(s -> s.startsWith(args[1])).toList();
        return List.of();
    }
}
