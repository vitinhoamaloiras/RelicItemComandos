package com.relicitemcomandos.animation;

import com.relicitemcomandos.packet.ActivatorAnimation;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SimpleAnimation extends ActivatorAnimation {

    private class FloatingBlock {
        Location location;
        int relativeX, relativeY, relativeZ;

        FloatingBlock(Location loc, int x, int y, int z) {
            this.location = loc;
            this.relativeX = x;
            this.relativeY = y;
            this.relativeZ = z;
        }
    }

    public SimpleAnimation(Plugin plugin) {
        super(plugin);
    }

    @Override
    protected void prepareAnimation(Player player, AnimationData data) {
    }

    @Override
    protected void executeAnimation(Player player, AnimationData data) {
        List<FloatingBlock> blocks = new ArrayList<>();
        data.metadata.put("blocks", blocks);

        for (int i = 0; i < 8; i++) {
            double angle = (2 * Math.PI / 8) * i;
            double radius = 2;

            int x = (int) (Math.cos(angle) * radius);
            int z = (int) (Math.sin(angle) * radius);
            int y = 1;

            Location blockLoc = player.getLocation().add(x, y, z);
            blockLoc.getBlock().setType(Material.OBSIDIAN);

            blocks.add(new FloatingBlock(blockLoc, x, y, z));
        }

        data.animationTask = scheduler.scheduleAtFixedRate(() -> {
            if (data.cancelled.get() || !player.isOnline()) {
                return;
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    Location playerLoc = player.getLocation();

                    for (FloatingBlock block : blocks) {
                        block.location.getBlock().setType(Material.AIR);
                    }

                    double time = System.currentTimeMillis() / 1000.0;
                    double baseAngle = time * 2;

                    for (int i = 0; i < blocks.size(); i++) {
                        FloatingBlock block = blocks.get(i);
                        double angle = baseAngle + (2 * Math.PI / blocks.size()) * i;

                        int x = (int) (Math.cos(angle) * 2);
                        int z = (int) (Math.sin(angle) * 2);

                        double wave = Math.sin(time * 3 + i) * 0.5;
                        int y = 1 + (int) wave;

                        Location newLoc = playerLoc.clone().add(x, y, z);
                        newLoc.getBlock().setType(Material.OBSIDIAN);
                        block.location = newLoc;
                        block.relativeX = x;
                        block.relativeY = y;
                        block.relativeZ = z;
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }, 0, 100, TimeUnit.MILLISECONDS);

        scheduler.schedule(() -> {
            if (data.animationTask != null && !data.animationTask.isDone()) {
                data.animationTask.cancel(false);
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                @SuppressWarnings("unchecked")
                List<FloatingBlock> blocksToRemove = (List<FloatingBlock>) data.metadata.get("blocks");
                if (blocksToRemove != null) {
                    for (FloatingBlock block : blocksToRemove) {
                        block.location.getBlock().setType(Material.AIR);
                    }
                }
            });

            finishAnimation(player, data);
        }, 5, TimeUnit.SECONDS);
    }

    @Override
    protected void cleanup(Player player, AnimationData data) {
        @SuppressWarnings("unchecked")
        List<FloatingBlock> blocks = (List<FloatingBlock>) data.metadata.get("blocks");
        if (blocks != null) {
            for (FloatingBlock block : blocks) {
                block.location.getBlock().setType(Material.AIR);
            }
            blocks.clear();
        }
    }
}