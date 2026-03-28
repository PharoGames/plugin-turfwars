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

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class MatchManager {

    private static final String GAMEMODE_ID = "turfwars";

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

            if (shouldStartCountdown()) {
                startCountdown();
            }
        } else {
            SpectatorAPI spectator = SpectatorAPI.getInstance();
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
    }

    public void onKill(Player killer, Player victim) {
        if (currentPhase != GamePhase.COMBAT && currentPhase != GamePhase.SUDDEN_DEATH) return;

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
                int linesToAdvance = currentPhase == GamePhase.SUDDEN_DEATH ? config.getSuddenDeathLinesPerKill() : config.getLinesPerKill();

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
            
            String msgKey = "turfwars.player_eliminated";
            broadcastKey(msgKey, Map.of("victim", victim.getName(), "killer", killer.getName()));
        }

        checkWinCondition();
        
        if (currentPhase != GamePhase.ENDED) {
            // Schedule respawn
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (victim.isOnline()) {
                    teleportToSpawn(victim);
                    victim.setGameMode(org.bukkit.GameMode.SURVIVAL);
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
        if (team == null) return;

        com.pharogames.turfwars.config.MapMetadataLoader.Point p = team.equals(blueTeam) ? mapMetadata.getBlueSpawn() : mapMetadata.getRedSpawn();
        Location loc = new Location(world, p.getX(), p.getY(), p.getZ());
        
        // Face the center (Z=0 or X=0 approx)
        if ("Z".equalsIgnoreCase(mapMetadata.getTurfAxis())) {
            loc.setYaw(loc.getZ() > 0 ? 180 : 0);
        } else {
            loc.setYaw(loc.getX() > 0 ? 90 : -90);
        }
        
        player.teleport(loc);
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
