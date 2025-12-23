package com.relicitemcomandos.actionbar;

import java.util.Iterator;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

@Deprecated
public final class ActionBar {
    private ActionBar() {
    }

    public static void sendActionBar(Plugin plugin, Player player, String message) {
        com.cryptomorin.xseries.messages.ActionBar.sendActionBar(plugin, player, message);
    }

    public static void sendActionBar(Player player, String message) {
        com.cryptomorin.xseries.messages.ActionBar.sendActionBar(player, message);
    }

    public static void sendActionBar(Plugin plugin, final Player player, final String message, final long duration) {
        (new BukkitRunnable() {
            long repeater = duration;

            public void run() {
                com.cryptomorin.xseries.messages.ActionBar.sendActionBar(player, message);
                this.repeater -= 40L;
                if (this.repeater - 40L < -20L) {
                    this.cancel();
                }

            }
        }).runTaskTimerAsynchronously(plugin, 0L, 40L);
    }

    public static void sendPlayersActionBar(String message) {
        Iterator var1 = Bukkit.getOnlinePlayers().iterator();

        while (var1.hasNext()) {
            Player player = (Player) var1.next();
            sendActionBar(player, message);
        }

    }

    public static void clearActionBar(Player player) {
        sendActionBar(player, " ");
    }

    public static void clearPlayersActionBar() {
        Iterator var0 = Bukkit.getOnlinePlayers().iterator();

        while (var0.hasNext()) {
            Player player = (Player) var0.next();
            clearActionBar(player);
        }

    }
}