package com.relicitemcomandos.commands;

import com.cryptomorin.xseries.profiles.builder.XSkull;
import com.cryptomorin.xseries.profiles.objects.ProfileInputType;
import com.cryptomorin.xseries.profiles.objects.Profileable;
import com.relicitemcomandos.BootstrapPlugin;
import com.relicplugins.plugins.platform.bukkit.resources.command.BukkitCommand;
import com.relicplugins.plugins.platform.bukkit.resources.command.BukkitSender;
import com.relicplugins.plugins.platform.bukkit.resources.command.subcommand.Subcommand;
import lombok.var;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

public class ActivatorCommand extends BukkitCommand {

    public ActivatorCommand() {
        super("activator");
    }

    @Override
    public boolean onExecute(BukkitSender sender, String s, String[] args) {
        if (!(sender.getCommandSender() instanceof Player)) {
            sender.sendMessage(color(BootstrapPlugin.getInstance().getConfig().getString("messages.no_player")));
            return true;
        }

        Player player = (Player) sender.getPlayer();
        Map<String, Object> activators = BootstrapPlugin.getInstance().getConfig().getConfigurationSection("activators").getValues(false);

        player.sendMessage(color("§aLista de ativadores:"));

        activators.keySet().forEach(key ->
                player.sendMessage(color("§f- §e" + key))
        );


        return true;
    }

    @Subcommand("give")
    public boolean onSubCommand(BukkitSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(color(BootstrapPlugin.getInstance().getConfig().getString("messages.usage_give")));
            return true;
        }

        String playerName = args[0];
        String activatorKey = args[1];

        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage(color(BootstrapPlugin.getInstance().getConfig().getString("messages.player_not_found")));
            return true;
        }

        if (!BootstrapPlugin.getInstance().getConfig().contains("activators." + activatorKey)) {
            sender.sendMessage(color(BootstrapPlugin.getInstance().getConfig().getString("messages.activator_not_found")));
            return true;
        }

        ItemStack item = createActivatorItem(target, activatorKey);
        target.getInventory().addItem(item);
        sender.sendMessage(color(BootstrapPlugin.getInstance().getConfig().getString("messages.activator_given").replace("%player%", target.getName())));

        return true;
    }

    private ItemStack createActivatorItem(Player player, String activatorKey) {
        var config = BootstrapPlugin.getInstance().getConfig().getConfigurationSection("activators." + activatorKey);

        ItemStack item;
        if (config.contains("texture")) {
            item = XSkull.createItem()
                    .profile(Profileable.of(ProfileInputType.TEXTURE_URL, config.getString("texture")))
                    .apply();
        } else {
            String[] parts = config.getString("item").split(":");
            Material mat = Material.getMaterial(parts[0].toUpperCase());
            int data = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            item = new ItemStack(mat, 1, (short) data);
        }

        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(config.getString("name").replace("%player%", player.getName())));

        List<String> lore = config.getStringList("lore");
        for (int i = 0; i < lore.size(); i++) {
            lore.set(i, color(lore.get(i).replace("%player%", player.getName())));
        }
        meta.setLore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private String color(String message) {
        return message == null ? "" : message.replace("&", "§");
    }
}
