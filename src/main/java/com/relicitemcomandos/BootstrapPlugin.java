package com.relicitemcomandos;

import com.relicitemcomandos.commands.ActivatorCommand;
import com.relicitemcomandos.events.ActivatorUseListener;
import com.relicplugins.plugins.platform.bukkit.resources.command.PluginClassLoader;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class BootstrapPlugin extends JavaPlugin {

    @Getter
    public static BootstrapPlugin instance;

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveDefaultConfig();
        }
        PluginClassLoader classLoader = new PluginClassLoader(this);
        getServer().getPluginManager().registerEvents(new ActivatorUseListener(this), this);
        classLoader.loadCommand(ActivatorCommand.class);
        getLogger().info("Plugin carregado.");
        getLogger().info("Carregando sistemas...");
        long startTime = System.currentTimeMillis();

        int activatorCount = 0;
        if (getConfig().isConfigurationSection("activators")) {
            activatorCount = getConfig()
                    .getConfigurationSection("activators")
                    .getKeys(false)
                    .size();
        }

        long endTime = System.currentTimeMillis();
        long elapsedTime = endTime - startTime;

        getLogger().info("Carregado " + activatorCount + " ativadores em " + elapsedTime + "ms");
    }

    @Override
    public void onDisable() {
    }
}
