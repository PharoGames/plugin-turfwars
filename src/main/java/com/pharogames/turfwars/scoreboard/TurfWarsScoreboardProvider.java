package com.pharogames.turfwars.scoreboard;

import com.pharogames.scoreboard.api.PlaceholderResolver;
import com.pharogames.scoreboard.api.ScoreboardProvider;
import com.pharogames.turfwars.game.MatchManager;
import com.pharogames.turfwars.game.TurfManager;
import org.bukkit.entity.Player;

import java.util.Map;

public class TurfWarsScoreboardProvider implements ScoreboardProvider {

    private MatchManager matchManager;
    private TurfManager turfManager;

    public void setMatchManager(MatchManager matchManager) {
        this.matchManager = matchManager;
    }

    public void setTurfManager(TurfManager turfManager) {
        this.turfManager = turfManager;
    }

    @Override
    public Map<String, PlaceholderResolver> getPlaceholders() {
        return Map.of(
            "time_remaining", player -> matchManager != null ? String.valueOf(matchManager.getPhaseSecondsRemaining()) : "0",
            "blue_lines", player -> turfManager != null ? String.valueOf(turfManager.getBlueLines()) : "0",
            "red_lines", player -> turfManager != null ? String.valueOf(turfManager.getRedLines()) : "0"
        );
    }

    @Override
    public String getScoreboardKey(Player player) {
        if (matchManager == null) {
            return "tw_waiting";
        }

        switch (matchManager.getCurrentPhase()) {
            case WAITING:
            case COUNTDOWN:
                return "tw_waiting";
            case BUILD:
                return "tw_build";
            case COMBAT:
                return "tw_combat";
            case PEACE:
                return "tw_peace";
            case SUDDEN_DEATH:
                return "tw_suddendeath";
            case ENDED:
                return "tw_ended";
            default:
                return "tw_waiting";
        }
    }
}
