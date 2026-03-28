package com.pharogames.turfwars.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class ConfigLoader {

    private static final String JSON_CONFIG_PATH = "/data/config/plugin-turfwars.json";
    private final JavaPlugin plugin;
    private final ObjectMapper jsonMapper;
    private final ObjectMapper yamlMapper;

    public ConfigLoader(JavaPlugin plugin) {
        this.plugin = plugin;
        this.jsonMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.yamlMapper = new ObjectMapper(new YAMLFactory())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public TurfWarsConfig loadConfig() {
        File jsonFile = new File(JSON_CONFIG_PATH);
        if (jsonFile.exists() && jsonFile.canRead()) {
            try {
                plugin.getLogger().info("Loading configuration from " + JSON_CONFIG_PATH);
                return jsonMapper.readValue(jsonFile, TurfWarsConfig.class);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to parse JSON config at " + JSON_CONFIG_PATH, e);
            }
        }

        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            plugin.getLogger().warning("Failed to create data folder");
        }

        File yamlFile = new File(dataFolder, "config.yml");
        if (!yamlFile.exists()) {
            plugin.getLogger().info("Saving default config.yml");
            try (InputStream in = plugin.getResource("config.yml")) {
                if (in != null) {
                    Files.copy(in, yamlFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to save default config.yml: " + e.getMessage());
            }
        }

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
