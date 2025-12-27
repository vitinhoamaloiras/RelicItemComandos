package com.relicitemcomandos.animation;

import com.relicitemcomandos.BootstrapPlugin;
import com.relicitemcomandos.actionbar.ActionBar;
import com.relicitemcomandos.packet.ActivatorAnimation;
import lombok.var;
import net.minecraft.server.v1_8_R3.*;
import org.bukkit.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class Animation extends ActivatorAnimation {

    private final BootstrapPlugin bootstrapPlugin;

    private static class BlockData {
        EntityArmorStand stand;
        double angle;
        double radius;

        BlockData(EntityArmorStand stand, double angle, double radius) {
            this.stand = stand;
            this.angle = angle;
            this.radius = radius;
        }
    }

    public Animation(Plugin plugin, BootstrapPlugin bootstrapPlugin) {
        super(plugin);
        this.bootstrapPlugin = bootstrapPlugin;
    }

    @Override
    protected void prepareAnimation(Player player, AnimationData data) {

        List<BlockData> blocks = new ArrayList<>();
        data.metadata.put("blocks", blocks);

        ItemStack blockItem = parseMaterial(
                plugin.getConfig().getString("animation.block-type", "OBSIDIAN")
        );

        Location base = player.getLocation().clone().add(0, 1.2, 0);

        for (int i = 0; i < 8; i++) {
            double angle = (2 * Math.PI / 8) * i;

            EntityArmorStand stand = new EntityArmorStand(
                    ((org.bukkit.craftbukkit.v1_8_R3.CraftWorld) base.getWorld()).getHandle()
            );

            stand.setInvisible(true);
            stand.setGravity(false);
            stand.setBasePlate(false);
            stand.setArms(false);

            stand.setEquipment(0,
                    org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack.asNMSCopy(blockItem)
            );

            blocks.add(new BlockData(stand, angle, 2.0));

            sendPacket(player, new PacketPlayOutSpawnEntityLiving(stand));
            sendPacket(player, new PacketPlayOutEntityEquipment(
                    stand.getId(), 0, stand.getEquipment(0)));
        }
    }

    @Override
    protected void executeAnimation(Player player, AnimationData data) {

        @SuppressWarnings("unchecked")
        List<BlockData> blocks = (List<BlockData>) data.metadata.get("blocks");

        Sound startSound = getSound("animation.start-sound");
        Sound removeSound = getSound("animation.remove-sound");
        Sound endSound = getSound("animation.end-sound");

        Effect removeParticle = getEffect("animation.particles.remove", Effect.SMOKE);
        Effect finishParticle = getEffect("animation.particles.finish", Effect.ENDER_SIGNAL);

        new BukkitRunnable() {
            int tick = 0;
            boolean removing = false;
            int removeCooldown = 0;

            @Override
            public void run() {

                if (!player.isOnline()) {
                    cancel();
                    cleanup(player, data);
                    return;
                }

                Location center = player.getLocation().clone().add(0, 1.2, 0);

                /* GIRO */
                for (BlockData block : blocks) {
                    block.angle += Math.PI / 12;

                    double x = center.getX() + Math.cos(block.angle) * block.radius;
                    double z = center.getZ() + Math.sin(block.angle) * block.radius;

                    sendPacket(player, new PacketPlayOutEntityTeleport(
                            block.stand.getId(),
                            (int) (x * 32),
                            (int) (center.getY() * 32),
                            (int) (z * 32),
                            (byte) 0,
                            (byte) 0,
                            false
                    ));
                }

                if (tick % 20 == 0 && startSound != null) {
                    player.playSound(player.getLocation(), startSound, 1f, 1f);
                }

                if (!removing && tick >= 60) {
                    removing = true;
                }

                tick++;

                if (!removing) return;

                if (removeCooldown > 0) {
                    removeCooldown--;
                    return;
                }

                int removed = 0;

                while (!blocks.isEmpty() && removed < 2) {
                    BlockData block = blocks.remove(0);

                    Location loc = center.clone().add(
                            Math.cos(block.angle) * block.radius,
                            0,
                            Math.sin(block.angle) * block.radius
                    );

                    player.getWorld().spigot().playEffect(
                            loc,
                            removeParticle,
                            0, 0,
                            0.25f, 0.25f, 0.25f,
                            0f, 15, 32
                    );

                    if (removeSound != null) {
                        player.playSound(loc, removeSound, 1f, 1f);
                    }

                    sendPacket(player, new PacketPlayOutEntityDestroy(block.stand.getId()));
                    removed++;
                }

                removeCooldown = 20;

                /* FINAL */
                if (blocks.isEmpty()) {

                    player.getWorld().spigot().playEffect(
                            player.getLocation().add(0, 2, 0),
                            finishParticle,
                            0, 0,
                            0.6f, 0.6f, 0.6f,
                            0.6f, 30, 64
                    );

                    if (endSound != null) {
                        player.playSound(player.getLocation(), endSound, 1f, 1f);
                    }

                    executePostAnimation(player);

                    cancel();
                    finishAnimation(player, data);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void executePostAnimation(Player player) {

        var section = bootstrapPlugin.getConfig().getConfigurationSection("activators");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            var config = section.getConfigurationSection(key);
            if (config == null) continue;

            String name = color(
                    bootstrapPlugin.getConfig().getString("name", "Ativador")
            );

            playSound(player, config.getString("som"));

            String usedMsg = bootstrapPlugin.getConfig().getString("messages.activator_used");
            if (usedMsg != null && !usedMsg.isEmpty()) {
                player.sendMessage(
                        color(usedMsg.replace("%activator%", name))
                );
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

            String command = config.getString("command");
            if (command != null && !command.isEmpty()) {
                Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(),
                        command.replace("%player%", player.getName())
                );
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

    @Override
    protected void cleanup(Player player, AnimationData data) {
        @SuppressWarnings("unchecked")
        List<BlockData> blocks = (List<BlockData>) data.metadata.get("blocks");

        if (blocks != null) {
            for (BlockData block : blocks) {
                sendPacket(player, new PacketPlayOutEntityDestroy(block.stand.getId()));
            }
            blocks.clear();
        }

        super.cleanup(player, data);
    }

    private ItemStack parseMaterial(String input) {
        try {
            if (input.contains(":")) {
                String[] split = input.split(":");
                return new ItemStack(
                        Material.valueOf(split[0]),
                        1,
                        Short.parseShort(split[1])
                );
            }
            return new ItemStack(Material.valueOf(input));
        } catch (Exception e) {
            return new ItemStack(Material.OBSIDIAN);
        }
    }

    private Sound getSound(String path) {
        try {
            return Sound.valueOf(plugin.getConfig().getString(path));
        } catch (Exception e) {
            return null;
        }
    }

    private Effect getEffect(String path, Effect def) {
        try {
            return Effect.valueOf(plugin.getConfig().getString(path));
        } catch (Exception e) {
            return def;
        }
    }
}
