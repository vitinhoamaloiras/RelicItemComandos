package com.relicitemcomandos.events;

import com.relicitemcomandos.BootstrapPlugin;
import com.relicitemcomandos.actionbar.ActionBar;
import lombok.var;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ActivatorUseListener implements Listener {

    private final BootstrapPlugin plugin;
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    public ActivatorUseListener(BootstrapPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return;

        Player player = e.getPlayer();
        ItemMeta meta = item.getItemMeta();
        String itemName = meta.getDisplayName();

        var section = plugin.getConfig().getConfigurationSection("activators");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            var config = section.getConfigurationSection(key);
            if (config == null) continue;

            String name = ChatColor.translateAlternateColorCodes('&', config.getString("name"));
            if (!itemName.equals(name)) continue;

            e.setCancelled(true);

            if (config.getBoolean("block-inv-full", false) && player.getInventory().firstEmpty() == -1) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("messages.inventory_full")));
                return;
            }

            int delay = config.getInt("delay", 0);
            long now = System.currentTimeMillis();
            cooldowns.putIfAbsent(player.getUniqueId(), new HashMap<>());
            Map<String, Long> userCooldowns = cooldowns.get(player.getUniqueId());

            if (userCooldowns.containsKey(key)) {
                long lastUse = userCooldowns.get(key);
                long remaining = delay - ((now - lastUse) / 1000);
                if (remaining > 0) {
                    try {
                        Sound sound = Sound.valueOf(config.getString("som-error"));
                        player.playSound(player.getLocation(), sound, 1f, 1f);
                    } catch (Exception ignored) {}
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            plugin.getConfig().getString("messages.activator_cooldown")
                                    .replace("{time}", String.valueOf(remaining))));
                    return;
                }
            }
            userCooldowns.put(key, now);

            List<String> lore = meta.getLore();
            if (lore != null) {
                for (int i = 0; i < lore.size(); i++) {
                    if (lore.get(i).contains("{usos}") || lore.get(i).contains("Usos")) {
                        String line = ChatColor.stripColor(lore.get(i));
                        int usos = 1;

                        try {
                            String numStr = line.replaceAll("[^0-9]", "");
                            usos = Integer.parseInt(numStr);
                        } catch (NumberFormatException ignored) {}

                        usos--;

                        if (usos <= 0) {
                            player.getInventory().remove(item);
                        } else {
                            lore.set(i, ChatColor.translateAlternateColorCodes('&', "&7Usos: &a" + usos + "&7."));
                            meta.setLore(lore);
                            item.setItemMeta(meta);
                        }
                        break;
                    }
                }
            }

            String command = config.getString("command").replace("%player%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

            try {
                Sound sound = Sound.valueOf(config.getString("som"));
                player.playSound(player.getLocation(), sound, 1f, 1f);
            } catch (Exception ignored) {}

            String usedMsgRaw = plugin.getConfig().getString("messages.activator_used");
            if (usedMsgRaw != null && !usedMsgRaw.isEmpty()) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        usedMsgRaw.replace("%activator%", name)));
            }

            if (config.getBoolean("use-title", false)) {
                String titleRaw = config.getString("title");
                if (titleRaw != null && !titleRaw.isEmpty()) {
                    player.sendTitle(ChatColor.translateAlternateColorCodes('&', titleRaw), "");
                }
            }

            if (config.getBoolean("use-actionbar", false)) {
                String actionbarRaw = config.getString("actionbar");
                if (actionbarRaw != null && !actionbarRaw.isEmpty()) {
                    ActionBar.sendActionBar(plugin, player, ChatColor.translateAlternateColorCodes('&', actionbarRaw));
                }
            }

            break;
        }
    }
}
