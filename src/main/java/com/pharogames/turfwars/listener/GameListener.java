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
        
        // Enforce boundary
        matchManager.getTurfManager().enforceBoundary(event.getPlayer());
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

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        event.setCancelled(true); // Cancel native death screen

        if (!matchManager.isInProgress()) return;

        Player killer = victim.getKiller();
        matchManager.onKill(killer, victim);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;

        if (!matchManager.isInProgress()) {
            event.setCancelled(true);
            return;
        }

        Material type = event.getBlockPlaced().getType();
        if (type == Material.BLUE_WOOL || type == Material.RED_WOOL) {
            matchManager.getWoolManager().addBlock(event.getBlockPlaced().getLocation());
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
}
