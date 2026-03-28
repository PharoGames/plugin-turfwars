package com.pharogames.turfwars.scoreboard;

import com.pharogames.scoreboard.api.ScoreboardProvider;
import com.pharogames.scoreboard.types.ScoreboardType;
import com.pharogames.communicator.api.CommunicatorAPI;
import com.pharogames.teams.model.Team;
import com.pharogames.turfwars.game.MatchManager;
import com.pharogames.turfwars.game.TurfManager;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

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
    public Component getTitle(Player player, ScoreboardType type) {
        CommunicatorAPI comm = CommunicatorAPI.getInstance();
        if (comm != null) {
            return comm.resolve("turfwars.scoreboard.title");
        }
        return Component.text("Turf Wars");
    }

    @Override
    public List<Component> getLines(Player player, ScoreboardType type) {
        List<Component> lines = new ArrayList<>();
        CommunicatorAPI comm = CommunicatorAPI.getInstance();

        if (comm == null || matchManager == null || turfManager == null) {
            return lines;
        }

        switch (matchManager.getCurrentPhase()) {
            case WAITING:
            case COUNTDOWN:
                lines.add(comm.resolve("turfwars.scoreboard.phase.waiting"));
                lines.add(Component.empty());
                lines.add(comm.resolve("turfwars.scoreboard.starting_in", java.util.Map.of("seconds", String.valueOf(matchManager.getPhaseSecondsRemaining()))));
                break;
            case BUILD:
                lines.add(comm.resolve("turfwars.scoreboard.phase.build"));
                lines.add(Component.empty());
                lines.add(comm.resolve("turfwars.scoreboard.time_left", java.util.Map.of("seconds", String.valueOf(matchManager.getPhaseSecondsRemaining()))));
                break;
            case COMBAT:
                lines.add(comm.resolve("turfwars.scoreboard.phase.combat"));
                lines.add(Component.empty());
                lines.add(comm.resolve("turfwars.scoreboard.time_left", java.util.Map.of("seconds", String.valueOf(matchManager.getPhaseSecondsRemaining()))));
                break;
            case PEACE:
                lines.add(comm.resolve("turfwars.scoreboard.phase.peace"));
                lines.add(Component.empty());
                lines.add(comm.resolve("turfwars.scoreboard.time_left", java.util.Map.of("seconds", String.valueOf(matchManager.getPhaseSecondsRemaining()))));
                break;
            case SUDDEN_DEATH:
                lines.add(comm.resolve("turfwars.scoreboard.phase.sudden_death"));
                lines.add(Component.empty());
                break;
            case ENDED:
                lines.add(comm.resolve("turfwars.scoreboard.phase.ended"));
                lines.add(Component.empty());
                break;
        }

        if (matchManager.isInProgress()) {
            lines.add(Component.empty());
            lines.add(comm.resolve("turfwars.scoreboard.blue_lines", java.util.Map.of("lines", String.valueOf(turfManager.getBlueLines()))));
            lines.add(comm.resolve("turfwars.scoreboard.red_lines", java.util.Map.of("lines", String.valueOf(turfManager.getRedLines()))));
        }

        return lines;
    }
}
