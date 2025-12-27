package com.relicitemcomandos.packet;

import com.relicitemcomandos.time.Cooldown;
import lombok.Getter;
import net.minecraft.server.v1_8_R3.*;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class ActivatorAnimation {

    protected final Plugin plugin;
    @Getter
    protected final ConcurrentHashMap<Integer, AnimationData> activeAnimations;
    protected final ConcurrentHashMap<Player, ConcurrentHashMap<Integer, AnimationData>> playerAnimations;
    protected final AtomicInteger animationIdCounter;
    protected final AtomicInteger entityIdCounter;
    protected final ScheduledExecutorService scheduler;

    public ActivatorAnimation(Plugin plugin) {
        this.plugin = plugin;
        this.activeAnimations = new ConcurrentHashMap<>();
        this.playerAnimations = new ConcurrentHashMap<>();
        this.animationIdCounter = new AtomicInteger(0);
        this.entityIdCounter = new AtomicInteger(2000000);
        this.scheduler = Executors.newScheduledThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
                new ThreadFactory() {
                    private final AtomicInteger threadCount = new AtomicInteger(0);
                    @Override
                    public Thread newThread(@NonNull Runnable r) {
                        Thread thread = new Thread(r, "LootBox-Animation-" + threadCount.incrementAndGet());
                        thread.setDaemon(true);
                        return thread;
                    }
                }
        );
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public CompletableFuture<ItemStack> startAnimation(String nameId, Player player, Location location, Runnable postAnimationCallback) {
        CompletableFuture<ItemStack> future = new CompletableFuture<>();
        int animationId = animationIdCounter.incrementAndGet();

        AnimationData data = new AnimationData(animationId, player, location, nameId);

        if (postAnimationCallback != null) {
            data.metadata.put("postAnimationCallback", postAnimationCallback);
        }

        activeAnimations.put(animationId, data);
        playerAnimations.computeIfAbsent(player, k -> new ConcurrentHashMap<>()).put(animationId, data);

        CompletableFuture.runAsync(() -> prepareAnimation(player, data), scheduler)
                .thenRun(() -> {
                    if (!data.cancelled.get()) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> executeAnimation(player, data));
                    } else {
                        future.completeExceptionally(new CancellationException("Animation was cancelled"));
                    }
                })
                .exceptionally(throwable -> {
                    handleError(player, data, throwable);
                    return null;
                });

        return future;
    }

    public CompletableFuture<ItemStack> startAnimation(String nameId, Player player, Location location) {
        return startAnimation(nameId, player, location, null);
    }

    protected ScheduledFuture<?> scheduleAnimation(Runnable task, long delayTicks, long periodTicks) {
        long delayMs = delayTicks * 50L;
        long periodMs = periodTicks * 50L;

        return scheduler.scheduleAtFixedRate(() -> plugin.getServer().getScheduler().runTask(plugin, task), delayMs, periodMs, TimeUnit.MILLISECONDS);
    }

    public boolean cancelAnimation(Player player, int animationId) {
        AnimationData data = activeAnimations.remove(animationId);
        if (data != null && data.player.equals(player)) {
            data.cancelled.set(true);
            if (data.animationTask != null && !data.animationTask.isDone()) {
                data.animationTask.cancel(false);
            }

            ConcurrentHashMap<Integer, AnimationData> playerAnims = playerAnimations.get(player);
            if (playerAnims != null) {
                playerAnims.remove(animationId);
                if (playerAnims.isEmpty()) {
                    playerAnimations.remove(player);
                }
            }

            cleanup(player, data);
            return true;
        }
        return false;
    }

    public void cancelAllAnimations(Player player) {
        ConcurrentHashMap<Integer, AnimationData> playerAnims = playerAnimations.remove(player);
        if (playerAnims != null) {
            for (Map.Entry<Integer, AnimationData> entry : playerAnims.entrySet()) {
                AnimationData data = activeAnimations.remove(entry.getKey());
                if (data != null) {
                    data.cancelled.set(true);
                    if (data.animationTask != null && !data.animationTask.isDone()) {
                        data.animationTask.cancel(false);
                    }
                    cleanup(player, data);
                }
            }
        }
    }

    public boolean hasActiveAnimation(Player player) {
        ConcurrentHashMap<Integer, AnimationData> playerAnims = playerAnimations.get(player);
        return playerAnims != null && !playerAnims.isEmpty();
    }

    public int getActiveAnimationCount(Player player) {
        ConcurrentHashMap<Integer, AnimationData> playerAnims = playerAnimations.get(player);
        return playerAnims != null ? playerAnims.size() : 0;
    }

    protected int generateEntityId() {
        return entityIdCounter.incrementAndGet();
    }

    protected void sendPacket(Player player, Packet<?> packet) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
            }
        });
    }

    protected abstract void prepareAnimation(Player player, AnimationData data);

    protected abstract void executeAnimation(Player player, AnimationData data);

    protected void cleanup(Player player, AnimationData data) {
    }

    protected void finishAnimation(Player player, AnimationData data) {
        AnimationData removed = activeAnimations.remove(data.id);

        if (removed != null) {
            ConcurrentHashMap<Integer, AnimationData> playerAnims = playerAnimations.get(player);
            if (playerAnims != null) {
                playerAnims.remove(data.id);
                if (playerAnims.isEmpty()) {
                    playerAnimations.remove(player);
                }
            }

            if (!data.cancelled.get()) {
                Cooldown.start("lootbox", player.getUniqueId(), 3);
                cleanup(player, data);
            }
        }
    }

    protected void handleError(Player player, AnimationData data, Throwable throwable) {
        AnimationData removed = activeAnimations.remove(data.id);

        if (removed != null) {
            ConcurrentHashMap<Integer, AnimationData> playerAnims = playerAnimations.get(player);
            if (playerAnims != null) {
                playerAnims.remove(data.id);
                if (playerAnims.isEmpty()) {
                    playerAnimations.remove(player);
                }
            }

            cleanup(player, data);
        }
    }

    protected static class AnimationData {
        public final int id;
        public final Player player;
        public final Location location;
        public final AtomicBoolean cancelled;
        public final ConcurrentHashMap<String, Object> metadata;
        public final String nameId;
        public volatile ScheduledFuture<?> animationTask;

        public AnimationData(int id, Player player, Location location, String nameId) {
            this.id = id;
            this.player = player;
            this.location = location;
            this.cancelled = new AtomicBoolean(false);
            this.metadata = new ConcurrentHashMap<>();
            this.nameId = nameId;
        }
    }
}