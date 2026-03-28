package com.pharogames.turfwars.game;

import com.pharogames.turfwars.config.TurfWarsConfig;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collection;

public class ArrowManager {

    private final JavaPlugin plugin;
    private final TurfWarsConfig config;
    private BukkitRunnable regenTask;

    public ArrowManager(JavaPlugin plugin, TurfWarsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void startRegen(java.util.function.Supplier<Collection<Player>> alivePlayersSupplier) {
        int intervalTicks = config.getArrowRegenIntervalSeconds() * 20;
        regenTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : alivePlayersSupplier.get()) {
                    if (player.isOnline()) {
                        giveArrows(player, 1);
                    }
                }
            }
        };
        regenTask.runTaskTimer(plugin, intervalTicks, intervalTicks);
    }

    public void stopRegen() {
        if (regenTask != null && !regenTask.isCancelled()) {
            regenTask.cancel();
            regenTask = null;
        }
    }

    public void grantKillArrows(Player player) {
        giveArrows(player, config.getArrowsOnKill());
    }

    private void giveArrows(Player player, int amount) {
        ItemStack arrows = new ItemStack(Material.ARROW, amount);
        player.getInventory().addItem(arrows);
    }
}
