package com.pharogames.turfwars.game;

import com.pharogames.communicator.api.CommunicatorAPI;
import com.pharogames.cosmetics.api.CosmeticsAPI;
import com.pharogames.gameplay.api.GameplayAPI;
import com.pharogames.items.api.ItemsAPI;
import com.pharogames.playerdata.api.MatchStatsReport;
import com.pharogames.playerdata.api.PlayerDataAPI;
import com.pharogames.playerdata.api.StatUpdate;
import com.pharogames.coins.api.CoinsAPI;
import com.pharogames.relay.RelayBackendPlugin;
import com.pharogames.relay.api.BackendNetworkAPI;
import com.pharogames.relay.lifecycle.ServerState;
import com.pharogames.scoreboard.api.ScoreboardAPI;
import com.pharogames.scoreboard.types.ScoreboardType;
import com.pharogames.spectator.api.SpectatorAPI;
import com.pharogames.teams.api.TeamAPI;
import com.pharogames.teams.model.Team;
import com.pharogames.turfwars.config.MapMetadataLoader.MapMetadata;
import com.pharogames.turfwars.config.TurfWarsConfig;
import com.pharogames.turfwars.scoreboard.TurfWarsScoreboardProvider;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class MatchManager {

    private static final String GAMEMODE_ID = "turfwars";
    private static final String DEBUG_LOG_PATH = "/home/venur/pharogames/.cursor/debug-194ceb.log";
    private static final String DEBUG_SESSION_ID = "194ceb";
    private static final String DEBUG_RUN_ID = "pre-fix";
    private static final ObjectMapper DEBUG_MAPPER = new ObjectMapper();

    private final JavaPlugin plugin;
    private final TurfWarsConfig config;
    private final World world;
    private final TurfWarsScoreboardProvider scoreboardProvider;
    private final MapMetadata mapMetadata;

    private Team blueTeam;
    private Team redTeam;

    private GamePhase currentPhase = GamePhase.WAITING;
    private BukkitTask phaseTimer;
    private int phaseSecondsRemaining;
    private Instant matchStartTime;
    private int roundsCompleted = 0;

    private TurfManager turfManager;
    private WoolManager woolManager;
    private ArrowManager arrowManager;

    private final Map<UUID, Integer> killTracker = new HashMap<>();

    public MatchManager(JavaPlugin plugin, TurfWarsConfig config, World world, 
                        TurfWarsScoreboardProvider scoreboardProvider, MapMetadata mapMetadata) {
        this.plugin = plugin;
        this.config = config;
        this.world = world;
        this.scoreboardProvider = scoreboardProvider;
        this.mapMetadata = mapMetadata;

        setupTeams();

        this.woolManager = new WoolManager(blueTeam, redTeam);
        this.turfManager = new TurfManager(world, mapMetadata, woolManager, blueTeam, redTeam);
        this.arrowManager = new ArrowManager(plugin, config);

        scoreboardProvider.setTurfManager(turfManager);
    }

    private void setupTeams() {
        TeamAPI teamAPI = TeamAPI.getInstance();
        if (teamAPI != null) {
            blueTeam = teamAPI.createTeam("Blue", NamedTextColor.BLUE);
            teamAPI.setFriendlyFire(blueTeam, false);
            redTeam = teamAPI.createTeam("Red", NamedTextColor.RED);
            teamAPI.setFriendlyFire(redTeam, false);
        }
    }

    public GamePhase getCurrentPhase() { return currentPhase; }
    public int getPhaseSecondsRemaining() { return phaseSecondsRemaining; }
    public boolean isInProgress() { return currentPhase != GamePhase.WAITING && currentPhase != GamePhase.ENDED; }
    public TurfManager getTurfManager() { return turfManager; }
    public WoolManager getWoolManager() { return woolManager; }
    public TurfWarsConfig getConfig() { return config; }

    public void onPlayerJoin(Player player) {
        BackendNetworkAPI api = RelayBackendPlugin.getAPI();
        TeamAPI teamAPI = TeamAPI.getInstance();
        Team currentTeam = teamAPI != null ? teamAPI.getPlayerTeam(player) : null;
        boolean hasExpectedPlayers = api != null && api.hasExpectedPlayers();
        int expectedCount = hasExpectedPlayers ? api.getExpectedPlayers().size() : -1;
        boolean expectedPlayer = !hasExpectedPlayers || api.isExpectedPlayer(player.getUniqueId());
        // #region agent log
        debugLog("H1", "MatchManager.java:onPlayerJoin:98", "player_joined", Map.of(
                "playerUuid", player.getUniqueId().toString(),
                "phase", currentPhase.name(),
                "onlineCount", world.getPlayers().size(),
                "hasExpectedPlayers", hasExpectedPlayers,
                "expectedCount", expectedCount,
                "expectedPlayer", expectedPlayer,
                "team", currentTeam != null ? currentTeam.getName() : "NONE"
        ));
        // #endregion
        if (api != null && api.hasExpectedPlayers() && !api.isExpectedPlayer(player.getUniqueId())) {
            SpectatorAPI spectator = SpectatorAPI.getInstance();
            if (spectator != null) {
                spectator.setSpectator(player);
                setScoreboard(player, ScoreboardType.SPECTATOR);
            }
            return;
        }

        if (currentPhase == GamePhase.WAITING) {
            player.teleport(world.getSpawnLocation());
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            setScoreboard(player, ScoreboardType.LOBBY);

            openKitSelectorPreview(player);
            giveKitSelectorItem(player);

            // #region agent log
            debugLog("H1", "MatchManager.java:onPlayerJoin:118", "countdown_gate_evaluated", Map.of(
                    "playerUuid", player.getUniqueId().toString(),
                    "shouldStartCountdown", shouldStartCountdown(),
                    "onlineCount", world.getPlayers().size(),
                    "hasExpectedPlayers", hasExpectedPlayers,
                    "expectedCount", expectedCount
            ));
            // #endregion
            if (shouldStartCountdown()) {
                startCountdown();
            }
        } else {
            SpectatorAPI spectator = SpectatorAPI.getInstance();
            // #region agent log
            debugLog("H2", "MatchManager.java:onPlayerJoin:121", "late_join_sent_to_spectator", Map.of(
                    "playerUuid", player.getUniqueId().toString(),
                    "phase", currentPhase.name(),
                    "team", currentTeam != null ? currentTeam.getName() : "NONE"
            ));
            // #endregion
            if (spectator != null) {
                spectator.setSpectator(player);
                setScoreboard(player, ScoreboardType.SPECTATOR);
            }
        }
    }

    public void onPlayerQuit(Player player) {
        if (currentPhase == GamePhase.COUNTDOWN && !shouldStartCountdown()) {
            cancelTimer();
            setPhase(GamePhase.WAITING);
            broadcastKey("turfwars.countdown_cancelled");
        } else if (isInProgress()) {
            checkWinCondition(); // Maybe they were the last player on a team
        }
    }

    private boolean shouldStartCountdown() {
        BackendNetworkAPI api = RelayBackendPlugin.getAPI();
        if (api != null && api.hasExpectedPlayers()) {
            return world.getPlayers().size() >= api.getExpectedPlayers().size();
        }
        return world.getPlayers().size() >= 4; // Arbitrary min for testing
    }

    private void startCountdown() {
        if (currentPhase != GamePhase.WAITING) return;
        setPhase(GamePhase.COUNTDOWN);

        phaseSecondsRemaining = config.getCountdownTime();
        
        // Auto balance teams
        TeamAPI teamAPI = TeamAPI.getInstance();
        if (teamAPI != null) {
            List<Player> players = new ArrayList<>(world.getPlayers());
            for (int i = 0; i < players.size(); i++) {
                Team team = (i % 2 == 0) ? blueTeam : redTeam;
                teamAPI.addPlayer(players.get(i), team);
                // #region agent log
                debugLog("H2", "MatchManager.java:startCountdown:159", "countdown_team_assigned", Map.of(
                        "playerUuid", players.get(i).getUniqueId().toString(),
                        "team", team != null ? team.getName() : "NONE",
                        "onlineCount", players.size()
                ));
                // #endregion
            }
        }

        for (Player p : world.getPlayers()) {
            teleportToSpawn(p);
        }

        phaseTimer = new BukkitRunnable() {
            @Override
            public void run() {
                if (phaseSecondsRemaining <= 0) {
                    cancel();
                    startBuildPhase();
                    return;
                }
                if (phaseSecondsRemaining <= 5 || phaseSecondsRemaining % 10 == 0) {
                    broadcastKey("turfwars.countdown_tick", Map.of("seconds", String.valueOf(phaseSecondsRemaining)));
                }
                phaseSecondsRemaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void startBuildPhase() {
        setPhase(GamePhase.BUILD);
        matchStartTime = Instant.now();
        roundsCompleted = 0;
        killTracker.clear();
        turfManager.resetTurf();

        GameplayAPI gameplay = GameplayAPI.getInstance();
        List<String> allKitIds = gameplay != null ? new ArrayList<>(gameplay.getAllKitIds()) : List.of();

        for (Player p : world.getPlayers()) {
            p.getInventory().clear();
            setScoreboard(p, ScoreboardType.IN_GAME);
            
            if (gameplay != null) {
                String selected = gameplay.getPreSelectedKit(p);
                if (selected == null && !allKitIds.isEmpty()) {
                    selected = allKitIds.get(ThreadLocalRandom.current().nextInt(allKitIds.size()));
                }
                if (selected != null) {
                    gameplay.giveKit(p, selected);
                }
            }

            woolManager.giveWool(p, config.getBuildWoolAmount());
        }

        broadcastKey("turfwars.build_start");

        phaseSecondsRemaining = config.getBuildTimerSeconds();
        phaseTimer = new BukkitRunnable() {
            @Override
            public void run() {
                if (phaseSecondsRemaining <= 0) {
                    cancel();
                    startCombatPhase();
                    return;
                }
                phaseSecondsRemaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void startCombatPhase() {
        setPhase(GamePhase.COMBAT);
        broadcastKey("turfwars.combat_start");

        arrowManager.startRegen(this::getAlivePlayers);

        CosmeticsAPI cosmetics = CosmeticsAPI.getInstance();
        if (cosmetics != null) {
            cosmetics.startArrowTrails();
        }

        phaseSecondsRemaining = config.getCombatTimerSeconds();
        phaseTimer = new BukkitRunnable() {
            @Override
            public void run() {
                if (phaseSecondsRemaining <= 0) {
                    cancel();
                    arrowManager.stopRegen();
                    roundsCompleted++;
                    if (roundsCompleted >= config.getSuddenDeathAfterRounds()) {
                        startSuddenDeath();
                    } else {
                        startPeacePhase();
                    }
                    return;
                }
                phaseSecondsRemaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void startPeacePhase() {
        setPhase(GamePhase.PEACE);
        broadcastKey("turfwars.peace_start");

        for (Player p : getAlivePlayers()) {
            woolManager.giveWool(p, config.getPeaceWoolAmount());
        }

        phaseSecondsRemaining = config.getPeaceTimerSeconds();
        phaseTimer = new BukkitRunnable() {
            @Override
            public void run() {
                if (phaseSecondsRemaining <= 0) {
                    cancel();
                    startCombatPhase();
                    return;
                }
                phaseSecondsRemaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void startSuddenDeath() {
        setPhase(GamePhase.SUDDEN_DEATH);
        broadcastKey("turfwars.sudden_death_start");
        arrowManager.startRegen(this::getAlivePlayers);
        
        phaseSecondsRemaining = 0;
        phaseTimer = new BukkitRunnable() {
            @Override
            public void run() {
                phaseSecondsRemaining++;
                int interval = config.getSuddenDeathWoolIntervalSeconds();
                int amount = config.getSuddenDeathWoolAmount();
                
                if (interval > 0 && phaseSecondsRemaining % interval == 0) {
                    if (amount > 0) {
                        for (Player p : getAlivePlayers()) {
                            woolManager.giveWool(p, amount);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private int calculateLinesPerKill() {
        com.pharogames.teams.api.TeamAPI teamAPI = com.pharogames.teams.api.TeamAPI.getInstance();
        if (teamAPI == null) return 1;
        
        com.pharogames.turfwars.game.TurfManager turfManager = getTurfManager();
        com.pharogames.teams.model.Team blueTeam = turfManager.getBlueTeam();
        com.pharogames.teams.model.Team redTeam = turfManager.getRedTeam();
        
        int bSize = blueTeam != null ? blueTeam.getPlayers().size() : 0;
        int rSize = redTeam != null ? redTeam.getPlayers().size() : 0;
        int maxTeamSize = Math.max(bSize, rSize);
        
        int baseLines;
        if (maxTeamSize <= 1) baseLines = 10;
        else if (maxTeamSize == 2) baseLines = 8;
        else if (maxTeamSize == 3) baseLines = 6;
        else if (maxTeamSize == 4) baseLines = 4;
        else if (maxTeamSize == 5) baseLines = 3;
        else if (maxTeamSize <= 10) baseLines = 2;
        else baseLines = 1;

        if (currentPhase == GamePhase.SUDDEN_DEATH) {
            return baseLines * 2;
        }
        return baseLines;
    }

    public void onKill(Player killer, Player victim) {
        // PvP rewards, turf lines, and elimination broadcast only during combat.
        // Fall, void, and other deaths still schedule respawn below.
        if (currentPhase == GamePhase.COMBAT || currentPhase == GamePhase.SUDDEN_DEATH) {
            if (killer != null) {
                killTracker.merge(killer.getUniqueId(), 1, Integer::sum);
                arrowManager.grantKillArrows(killer);

                CoinsAPI coinsApi = CoinsAPI.getInstance();
                if (coinsApi != null) {
                    coinsApi.recordKill(killer.getUniqueId(), victim.getUniqueId());
                }

                TeamAPI teamAPI = TeamAPI.getInstance();
                if (teamAPI != null) {
                    Team killerTeam = teamAPI.getPlayerTeam(killer);
                    int linesToAdvance = calculateLinesPerKill();

                    if (killerTeam != null && killerTeam.equals(blueTeam)) {
                        turfManager.advanceBlue(linesToAdvance);
                    } else if (killerTeam != null && killerTeam.equals(redTeam)) {
                        turfManager.advanceRed(linesToAdvance);
                    }
                }

                CosmeticsAPI cosmetics = CosmeticsAPI.getInstance();
                if (cosmetics != null) {
                    String effectId = cosmetics.getKillEffect(killer.getUniqueId());
                    if (effectId != null) {
                        cosmetics.playKillEffect(effectId, victim.getLocation());
                    }
                }

                String victimName = victim.getName();
                String killerName = killer.getName();

                if (teamAPI != null) {
                    Team victimTeam = teamAPI.getPlayerTeam(victim);
                    Team killerTeam = teamAPI.getPlayerTeam(killer);

                    if (victimTeam != null) {
                        if (victimTeam.equals(blueTeam)) {
                            victimName = "<blue>" + victimName + "</blue>";
                        } else if (victimTeam.equals(redTeam)) {
                            victimName = "<red>" + victimName + "</red>";
                        }
                    }

                    if (killerTeam != null) {
                        if (killerTeam.equals(blueTeam)) {
                            killerName = "<blue>" + killerName + "</blue>";
                        } else if (killerTeam.equals(redTeam)) {
                            killerName = "<red>" + killerName + "</red>";
                        }
                    }
                }

                String msgKey = "turfwars.player_eliminated";
                broadcastKey(msgKey, Map.of("victim", victimName, "killer", killerName));
            }
        }

        checkWinCondition();
        
        if (currentPhase != GamePhase.ENDED) {
            // Schedule respawn
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (victim.isOnline()) {
                    TeamAPI respawnTeamApi = TeamAPI.getInstance();
                    Team victimTeam = respawnTeamApi != null ? respawnTeamApi.getPlayerTeam(victim) : null;
                    SpectatorAPI spectator = SpectatorAPI.getInstance();
                    // #region agent log
                    debugLog("H3", "MatchManager.java:onKill:327", "respawn_task_running", Map.of(
                            "victimUuid", victim.getUniqueId().toString(),
                            "phase", currentPhase.name(),
                            "team", victimTeam != null ? victimTeam.getName() : "NONE",
                            "locationX", victim.getLocation().getX(),
                            "locationY", victim.getLocation().getY(),
                            "locationZ", victim.getLocation().getZ()
                    ));
                    // #endregion
                    if (spectator != null && spectator.isSpectator(victim)) {
                        spectator.removeSpectator(victim);
                        // #region agent log
                        debugLog("H3", "MatchManager.java:onKill:338", "respawn_removed_spectator_state", Map.of(
                                "victimUuid", victim.getUniqueId().toString(),
                                "team", victimTeam != null ? victimTeam.getName() : "NONE"
                        ));
                        // #endregion
                    }
                    teleportToSpawn(victim);
                    victim.setGameMode(org.bukkit.GameMode.SURVIVAL);
                    victim.setAllowFlight(false);
                    victim.setFlying(false);
                    victim.setHealth(victim.getMaxHealth());
                    victim.setFoodLevel(20);
                    victim.setFireTicks(0);
                    giveRespawnItems(victim);
                }
            }, config.getRespawnDelayTicks());
        }
    }

    private void giveRespawnItems(Player player) {
        GameplayAPI gameplay = GameplayAPI.getInstance();
        if (gameplay != null) {
            String kitId = gameplay.getSessionKit(player);
            if (kitId == null) {
                kitId = gameplay.getPreSelectedKit(player);
            }
            if (kitId != null) {
                gameplay.giveKit(player, kitId);
            }
        }
    }

    private void checkWinCondition() {
        if (turfManager.checkWinCondition()) {
            endMatch(turfManager.getWinningTeam());
            return;
        }

        // Check if a team has no players left
        TeamAPI teamAPI = TeamAPI.getInstance();
        if (teamAPI != null) {
            boolean blueAlive = blueTeam.getPlayers().stream()
                .map(plugin.getServer()::getPlayer)
                .anyMatch(p -> p != null && p.isOnline());
            boolean redAlive = redTeam.getPlayers().stream()
                .map(plugin.getServer()::getPlayer)
                .anyMatch(p -> p != null && p.isOnline());

            if (!blueAlive && redAlive) endMatch(redTeam);
            else if (!redAlive && blueAlive) endMatch(blueTeam);
        }
    }

    private void endMatch(Team winningTeam) {
        if (currentPhase == GamePhase.ENDED) return;
        setPhase(GamePhase.ENDED);
        cancelTimer();
        arrowManager.stopRegen();

        CosmeticsAPI cosmetics = CosmeticsAPI.getInstance();
        if (cosmetics != null) {
            cosmetics.stopArrowTrails();
        }

        if (winningTeam != null) {
            String colorName = NamedTextColor.NAMES.keyOrThrow(winningTeam.getColor());
            broadcastKey("turfwars.win", Map.of("color", colorName, "team", winningTeam.getName()));

            if (cosmetics != null) {
                for (UUID uuid : winningTeam.getPlayers()) {
                    Player p = plugin.getServer().getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        String effectId = cosmetics.getWinEffect(uuid);
                        if (effectId != null) {
                            cosmetics.playWinEffect(p, effectId);
                        }
                    }
                }
            }
        }

        if (!killTracker.isEmpty()) {
            List<Map.Entry<UUID, Integer>> sortedKillers = new ArrayList<>(killTracker.entrySet());
            sortedKillers.sort((a, b) -> b.getValue().compareTo(a.getValue()));

            broadcastKey("turfwars.top_killers_header");
            int rank = 1;
            for (int i = 0; i < Math.min(3, sortedKillers.size()); i++) {
                Map.Entry<UUID, Integer> entry = sortedKillers.get(i);
                Player p = plugin.getServer().getPlayer(entry.getKey());
                String name = p != null ? p.getName() : plugin.getServer().getOfflinePlayer(entry.getKey()).getName();
                if (name == null) name = "Unknown";
                
                TeamAPI teamAPI = TeamAPI.getInstance();
                if (teamAPI != null && p != null) {
                    Team team = teamAPI.getPlayerTeam(p);
                    if (team != null) {
                        if (team.equals(blueTeam)) name = "<blue>" + name + "</blue>";
                        else if (team.equals(redTeam)) name = "<red>" + name + "</red>";
                    }
                }

                broadcastKey("turfwars.top_killer_" + rank, Map.of("name", name, "kills", String.valueOf(entry.getValue())));
                rank++;
            }
        }

        reportStats(winningTeam);

        BackendNetworkAPI networkApi = RelayBackendPlugin.getAPI();
        if (networkApi != null) {
            networkApi.setState(ServerState.ENDING);
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (networkApi != null) {
                networkApi.sendAllToLobby();
            }
        }, 100L);
    }

    private void reportStats(Team winningTeam) {
        PlayerDataAPI pda = PlayerDataAPI.getInstance();
        if (pda == null) return;

        String matchId = "TW_" + System.currentTimeMillis();
        MatchStatsReport.Builder report = MatchStatsReport.builder(matchId, GAMEMODE_ID)
                .startedAt(matchStartTime)
                .endedAt(Instant.now());

        if (winningTeam != null) {
            winningTeam.getPlayers().stream()
                    .map(plugin.getServer()::getOfflinePlayer)
                    .filter(p -> p.getUniqueId() != null)
                    .findFirst()
                    .ifPresent(p -> report.winner(p.getUniqueId()));
        }

        for (Map.Entry<UUID, Integer> entry : killTracker.entrySet()) {
            UUID uuid = entry.getKey();
            int kills = entry.getValue();
            boolean isWinner = winningTeam != null && winningTeam.hasPlayer(uuid);
            int xp = (isWinner ? 15 : 5) + (kills * 2);

            StatUpdate stat = StatUpdate.builder(uuid.toString())
                    .stat("kills", kills)
                    .stat("wins", isWinner ? 1 : 0)
                    .xp(xp)
                    .build();
            report.addPlayer(stat);
        }

        pda.reportMatchStats(report.build());
    }

    public void teleportToSpawn(Player player) {
        TeamAPI teamAPI = TeamAPI.getInstance();
        if (teamAPI == null) return;

        Team team = teamAPI.getPlayerTeam(player);
        if (team == null) {
            // #region agent log
            debugLog("H3", "MatchManager.java:teleportToSpawn:450", "teleport_skipped_missing_team", Map.of(
                    "playerUuid", player.getUniqueId().toString(),
                    "phase", currentPhase.name(),
                    "locationX", player.getLocation().getX(),
                    "locationY", player.getLocation().getY(),
                    "locationZ", player.getLocation().getZ()
            ));
            // #endregion
            return;
        }

        com.pharogames.turfwars.config.MapMetadataLoader.Point p = team.equals(blueTeam) ? mapMetadata.getBlueSpawn() : mapMetadata.getRedSpawn();
        Location loc = new Location(world, p.getX(), p.getY(), p.getZ());
        
        // Face the center (Z=0 or X=0 approx)
        if ("Z".equalsIgnoreCase(mapMetadata.getTurfAxis())) {
            loc.setYaw(loc.getZ() > 0 ? 180 : 0);
        } else {
            loc.setYaw(loc.getX() > 0 ? 90 : -90);
        }

        // #region agent log
        debugLog("H4", "MatchManager.java:teleportToSpawn:463", "teleporting_to_team_spawn", Map.of(
                "playerUuid", player.getUniqueId().toString(),
                "team", team.getName(),
                "targetX", loc.getX(),
                "targetY", loc.getY(),
                "targetZ", loc.getZ(),
                "phase", currentPhase.name()
        ));
        // #endregion
        player.teleport(loc);
    }

    private void debugLog(String hypothesisId, String location, String message, Map<String, Object> data) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("sessionId", DEBUG_SESSION_ID);
            payload.put("runId", DEBUG_RUN_ID);
            payload.put("hypothesisId", hypothesisId);
            payload.put("location", location);
            payload.put("message", message);
            payload.put("data", data);
            payload.put("timestamp", System.currentTimeMillis());
            payload.put("id", "log_" + System.currentTimeMillis() + "_" + hypothesisId);
            Files.writeString(
                    Path.of(DEBUG_LOG_PATH),
                    DEBUG_MAPPER.writeValueAsString(payload) + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (Exception ignored) {
        }
    }

    private List<Player> getAlivePlayers() {
        List<Player> alive = new ArrayList<>();
        SpectatorAPI spectator = SpectatorAPI.getInstance();
        for (Player p : world.getPlayers()) {
            if (spectator == null || !spectator.isSpectator(p)) {
                alive.add(p);
            }
        }
        return alive;
    }

    private void openKitSelectorPreview(Player player) {
        GameplayAPI gameplay = GameplayAPI.getInstance();
        if (gameplay != null) {
            gameplay.openKitSelector(player, GAMEMODE_ID, true);
        }
    }

    private void giveKitSelectorItem(Player player) {
        ItemsAPI items = ItemsAPI.getInstance();
        if (items != null) {
            ItemStack selectorItem = items.createItem(config.getKitSelectorItemId(), player);
            if (selectorItem != null) {
                player.getInventory().setItem(config.getKitSelectorSlot(), selectorItem);
            }
        }
    }

    private void setPhase(GamePhase phase) {
        currentPhase = phase;
        if (scoreboardProvider != null) {
            // Update logic is dynamic on next tick
        }
    }

    private void cancelTimer() {
        if (phaseTimer != null && !phaseTimer.isCancelled()) {
            phaseTimer.cancel();
            phaseTimer = null;
        }
    }

    private void setScoreboard(Player player, ScoreboardType type) {
        ScoreboardAPI scoreboard = ScoreboardAPI.getInstance();
        if (scoreboard != null) {
            scoreboard.setScoreboard(player, type);
        }
    }

    private void broadcastKey(String key) {
        CommunicatorAPI comm = CommunicatorAPI.getInstance();
        if (comm != null) comm.broadcast(key);
    }

    private void broadcastKey(String key, Map<String, String> replacements) {
        CommunicatorAPI comm = CommunicatorAPI.getInstance();
        if (comm != null) comm.broadcast(key, replacements);
    }

    public void cleanup() {
        cancelTimer();
        if (arrowManager != null) arrowManager.stopRegen();
        if (woolManager != null) woolManager.cleanup();

        CosmeticsAPI cosmetics = CosmeticsAPI.getInstance();
        if (cosmetics != null) {
            cosmetics.stopArrowTrails();
            cosmetics.cleanup();
        }
    }
}
