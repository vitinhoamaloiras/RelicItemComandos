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

import java.util.*;

public class ActivatorUseListener implements Listener {

    private final BootstrapPlugin plugin;
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    public ActivatorUseListener(BootstrapPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        if (!meta.hasDisplayName()) return;

        Player player = e.getPlayer();
        String itemName = meta.getDisplayName();

        var section = plugin.getConfig().getConfigurationSection("activators");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            var config = section.getConfigurationSection(key);
            if (config == null) continue;

            String name = ChatColor.translateAlternateColorCodes('&', config.getString("name"));
            if (!itemName.equals(name)) continue;

            e.setCancelled(true);

            if (config.getBoolean("block-inv-full", false)
                    && player.getInventory().firstEmpty() == -1) {
                player.sendMessage(color(plugin.getConfig().getString("messages.inventory_full")));
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
                    playSound(player, config.getString("som-error"));
                    player.sendMessage(color(
                            plugin.getConfig()
                                    .getString("messages.activator_cooldown")
                                    .replace("{time}", String.valueOf(remaining))
                    ));
                    return;
                }
            }

            userCooldowns.put(key, now);

            List<String> lore = meta.getLore();
            if (lore != null) {
                for (int i = 0; i < lore.size(); i++) {
                    String clean = ChatColor.stripColor(lore.get(i));
                    if (!clean.contains("Usos")) continue;

                    int usos = Integer.parseInt(clean.replaceAll("\\D+", ""));
                    usos--;

                    if (usos <= 0) {
                        player.getInventory().remove(item);
                    } else {
                        lore.set(i, color("&7Usos: &a" + usos + "&7."));
                        meta.setLore(lore);
                        item.setItemMeta(meta);
                    }
                    break;
                }
            }

            Runnable postAnimation = () -> {

                playSound(player, config.getString("som"));

                String usedMsg = plugin.getConfig().getString("messages.activator_used");
                if (usedMsg != null && !usedMsg.isEmpty()) {
                    player.sendMessage(color(
                            usedMsg.replace("%activator%", name)
                    ));
                }

                if (config.getBoolean("use-title", false)) {
                    String title = config.getString("title");
                    if (title != null && !title.isEmpty()) {
                        player.sendTitle(color(title), "");
                    }
                }

                if (config.getBoolean("use-actionbar", false)) {
                    String bar = config.getString("actionbar");
                    if (bar != null && !bar.isEmpty()) {
                        ActionBar.sendActionBar(plugin, player, color(bar));
                        Bukkit.getScheduler().runTaskLater(plugin,
                                () -> ActionBar.sendActionBar(plugin, player, ""),
                                60L
                        );
                    }
                }

                String command = config.getString("command")
                        .replace("%player%", player.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            };

            if (plugin.getConfig().getBoolean("animation.use", true)) {
                plugin.getAnimation()
                        .startAnimation(key, player, player.getLocation(), postAnimation);
            } else {
                postAnimation.run();
            }
            break;
        }
    }

    private void playSound(Player player, String soundName) {
        try {
            player.playSound(player.getLocation(),
                    Sound.valueOf(soundName), 1f, 1f);
        } catch (Exception ignored) {}
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
