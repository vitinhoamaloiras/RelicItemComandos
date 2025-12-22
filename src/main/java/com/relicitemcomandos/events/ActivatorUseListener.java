package com.relicitemcomandos.events;

import com.relicitemcomandos.BootstrapPlugin;
import lombok.var;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class ActivatorUseListener implements Listener {

    private final BootstrapPlugin plugin;

    public ActivatorUseListener(BootstrapPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta()) return;

        String itemName = item.getItemMeta().getDisplayName();

        var activators = plugin.getConfig().getConfigurationSection("activators").getValues(false);
        for (String key : activators.keySet()) {
            var config = plugin.getConfig().getConfigurationSection("activators." + key);
            String name = config.getString("name");
            if (itemName.equals(name)) {
                Player player = e.getPlayer();
                e.setCancelled(true);
                player.getInventory().remove(item);

                String command = config.getString("command").replace("%player%", player.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                String message = plugin.getConfig()
                        .getString("messages.activator_used")
                        .replace("%activator%", key)
                        .replace("&", "§");

                player.sendMessage(message);

                break;
            }
        }
    }
}
