package com.pharogames.turfwars.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class ConfigLoader {

    private final JavaPlugin plugin;
    private final ObjectMapper yamlMapper;

    public ConfigLoader(JavaPlugin plugin) {
        this.plugin = plugin;
        this.yamlMapper = new ObjectMapper(new YAMLFactory())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public TurfWarsConfig loadConfig() {
        plugin.saveDefaultConfig();
        File yamlFile = new File(plugin.getDataFolder(), "config.yml");

        if (yamlFile.exists() && yamlFile.canRead()) {
            try {
                plugin.getLogger().info("Loading configuration from plugins/TurfWars/config.yml");
                return yamlMapper.readValue(yamlFile, TurfWarsConfig.class);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to parse YAML config at " + yamlFile.getAbsolutePath(), e);
            }
        }

        plugin.getLogger().warning("No configuration file found; using defaults.");
        return new TurfWarsConfig();
    }
}