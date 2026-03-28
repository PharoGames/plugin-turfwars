package com.pharogames.turfwars;

import com.pharogames.items.api.InteractType;
import com.pharogames.items.api.ItemsAPI;
import com.pharogames.scoreboard.api.ScoreboardAPI;
import com.pharogames.turfwars.config.ConfigLoader;
import com.pharogames.turfwars.config.MapMetadataLoader;
import com.pharogames.turfwars.config.TurfWarsConfig;
import com.pharogames.turfwars.game.MatchManager;
import com.pharogames.turfwars.listener.GameListener;
import com.pharogames.turfwars.scoreboard.TurfWarsScoreboardProvider;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

public class TurfWarsPlugin extends JavaPlugin {

    private TurfWarsConfig config;
    private MatchManager matchManager;
    private TurfWarsScoreboardProvider scoreboardProvider;

    @Override
    public void onEnable() {
        ConfigLoader configLoader = new ConfigLoader(this);
        try {
            config = configLoader.loadConfig();
        } catch (IllegalStateException e) {
            getLogger().severe("Invalid configuration: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        MapMetadataLoader metaLoader = new MapMetadataLoader(getLogger());
        MapMetadataLoader.MapMetadata mapMetadata = metaLoader.loadMetadata();
        if (mapMetadata == null) {
            getLogger().severe("Map metadata missing. Gamemode cannot start.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        World world = getServer().getWorlds().get(0);

        scoreboardProvider = new TurfWarsScoreboardProvider();
        matchManager = new MatchManager(this, config, world, scoreboardProvider, mapMetadata);
        scoreboardProvider.setMatchManager(matchManager);

        ScoreboardAPI scoreboardAPI = ScoreboardAPI.getInstance();
        if (scoreboardAPI != null) {
            scoreboardAPI.registerScoreboardProvider(scoreboardProvider);
        }

        getServer().getPluginManager().registerEvents(new GameListener(matchManager), this);

        registerKitSelectorInteraction();

        getLogger().info("TurfWars plugin enabled.");
    }

    @Override
    public void onDisable() {
        if (matchManager != null) {
            matchManager.cleanup();
        }
        unregisterKitSelectorInteraction();

        ScoreboardAPI scoreboardAPI = ScoreboardAPI.getInstance();
        if (scoreboardAPI != null && scoreboardProvider != null) {
            scoreboardAPI.unregisterScoreboardProvider(scoreboardProvider);
        }
    }

    private void registerKitSelectorInteraction() {
        ItemsAPI items = ItemsAPI.getInstance();
        if (items != null && config != null) {
            items.registerInteraction(config.getKitSelectorItemId(), InteractType.ANY_RIGHT_CLICK, (player, item, type) -> {
                if (matchManager.getCurrentPhase() == com.pharogames.turfwars.game.GamePhase.WAITING || 
                    matchManager.getCurrentPhase() == com.pharogames.turfwars.game.GamePhase.COUNTDOWN) {
                    com.pharogames.gameplay.api.GameplayAPI gameplay = com.pharogames.gameplay.api.GameplayAPI.getInstance();
                    if (gameplay != null) {
                        gameplay.openKitSelector(player, "turfwars", true);
                    }
                }
            });
        }
    }

    private void unregisterKitSelectorInteraction() {
        ItemsAPI items = ItemsAPI.getInstance();
        if (items != null && config != null) {
            items.unregisterInteractions(config.getKitSelectorItemId());
        }
    }
}
