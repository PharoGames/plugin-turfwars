package com.pharogames.turfwars.listener;

import com.pharogames.turfwars.game.GamePhase;
import com.pharogames.turfwars.game.MatchManager;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class GameListener implements Listener {

    private final MatchManager matchManager;

    public GameListener(MatchManager matchManager) {
        this.matchManager = matchManager;
    }

    @EventHandler
    public void onProjectileLaunch(org.bukkit.event.entity.ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Arrow arrow && arrow.getShooter() instanceof Player shooter) {
            com.pharogames.cosmetics.api.CosmeticsAPI cosmetics = com.pharogames.cosmetics.api.CosmeticsAPI.getInstance();
            if (cosmetics != null) {
                cosmetics.trackArrow(shooter, arrow);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        matchManager.onPlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        matchManager.onPlayerQuit(event.getPlayer());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!matchManager.isInProgress()) return;
        
        Player player = event.getPlayer();
        
        // Check void death
        if (player.getLocation().getY() < matchManager.getConfig().getVoidDeathY()) {
            com.pharogames.spectator.api.SpectatorAPI spectator = com.pharogames.spectator.api.SpectatorAPI.getInstance();
            boolean isSpec = spectator != null ? spectator.isSpectator(player) : player.getGameMode() == GameMode.SPECTATOR;
            
            if (!isSpec) {
                // Fake death
                player.setHealth(player.getMaxHealth());
                player.setFoodLevel(20);
                player.setFireTicks(0);
                player.getInventory().clear();
                
                if (spectator != null) {
                    spectator.setSpectator(player);
                } else {
                    player.setGameMode(GameMode.SPECTATOR);
                }
                matchManager.onKill(null, player);
                return;
            }
        }

        // Enforce boundary
        if (player.getGameMode() != GameMode.SPECTATOR) {
            matchManager.getTurfManager().enforceBoundary(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        GamePhase phase = matchManager.getCurrentPhase();
        if (phase == GamePhase.BUILD || phase == GamePhase.PEACE) {
            event.setCancelled(true);
            return;
        }

        if (event.getDamager() instanceof Arrow arrow && arrow.getShooter() instanceof Player shooter) {
            // One-tap with arrows
            event.setDamage(1000.0);
        } else if (event.getDamager() instanceof Player) {
            // No melee damage
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageWouldKill(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        if (!matchManager.isInProgress()) return;

        com.pharogames.spectator.api.SpectatorAPI spectator = com.pharogames.spectator.api.SpectatorAPI.getInstance();
        if (spectator != null && spectator.isSpectator(victim)) return;

        if (event.getFinalDamage() < victim.getHealth()) return;

        event.setCancelled(true); // Prevent the actual death

        // Fake death
        victim.setHealth(victim.getMaxHealth());
        victim.setFoodLevel(20);
        victim.setFireTicks(0);
        victim.getInventory().clear();
        
        Player killer = null;
        if (event instanceof EntityDamageByEntityEvent entityEvent) {
            if (entityEvent.getDamager() instanceof Arrow arrow && arrow.getShooter() instanceof Player p) {
                killer = p;
            } else if (entityEvent.getDamager() instanceof Player p) {
                killer = p;
            }
        }
        
        if (spectator != null) {
            spectator.setSpectator(victim); // Uses spectator plugin
        } else {
            victim.setGameMode(GameMode.SPECTATOR); // Fallback
        }

        matchManager.onKill(killer, victim);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        // Fallback for /kill or similar that bypasses EntityDamageEvent
        event.setDeathMessage(null);
        if (!matchManager.isInProgress()) return;
        
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        
        // Put in spectator mode for the delay
        com.pharogames.spectator.api.SpectatorAPI spectator = com.pharogames.spectator.api.SpectatorAPI.getInstance();
        if (spectator != null) {
            spectator.setSpectator(victim);
        } else {
            victim.setGameMode(GameMode.SPECTATOR);
        }
        
        matchManager.onKill(killer, victim);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;

        if (!matchManager.isInProgress()) {
            event.setCancelled(true);
            return;
        }

        Player player = event.getPlayer();
        org.bukkit.Location loc = event.getBlockPlaced().getLocation();
        com.pharogames.turfwars.config.MapMetadataLoader.MapMetadata metadata = matchManager.getTurfManager().getMetadata();
        boolean isZAxis = "Z".equalsIgnoreCase(metadata.getTurfAxis());
        
        double coord = isZAxis ? loc.getZ() : loc.getX();
        int startCoord = isZAxis ? (int) metadata.getArenaMin().getZ() : (int) metadata.getArenaMin().getX();
        double boundary = startCoord + matchManager.getTurfManager().getBlueLines();

        com.pharogames.teams.api.TeamAPI teamAPI = com.pharogames.teams.api.TeamAPI.getInstance();
        if (teamAPI != null) {
            com.pharogames.teams.model.Team team = teamAPI.getPlayerTeam(player);
            if (team != null) {
                boolean isBlue = team.equals(matchManager.getTurfManager().getBlueTeam());
                boolean isRed = team.equals(matchManager.getTurfManager().getRedTeam());

                if (isBlue && coord > boundary) {
                    event.setCancelled(true);
                    return;
                } else if (isRed && coord < boundary) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        Material type = event.getBlockPlaced().getType();
        if (type == Material.BLUE_WOOL || type == Material.RED_WOOL) {
            matchManager.getWoolManager().addBlock(loc);
        } else {
            event.setCancelled(true); // Only allow placing wool
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;

        if (!matchManager.isInProgress()) {
            event.setCancelled(true);
            return;
        }

        if (matchManager.getWoolManager().isPlacedWool(event.getBlock().getLocation())) {
            matchManager.getWoolManager().removeBlock(event.getBlock().getLocation());
            event.getBlock().setType(Material.AIR);
        } else {
            event.setCancelled(true); // Cannot break map
        }
    }

    @EventHandler
    public void onPlayerDropItem(org.bukkit.event.player.PlayerDropItemEvent event) {
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }
}
