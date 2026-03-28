package com.pharogames.turfwars.game;

import com.pharogames.teams.api.TeamAPI;
import com.pharogames.teams.model.Team;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashSet;
import java.util.Set;

public class WoolManager {

    private final Set<Location> placedWool = new HashSet<>();
    private final Team blueTeam;
    private final Team redTeam;

    public WoolManager(Team blueTeam, Team redTeam) {
        this.blueTeam = blueTeam;
        this.redTeam = redTeam;
    }

    public void giveWool(Player player, int amount) {
        TeamAPI teamAPI = TeamAPI.getInstance();
        if (teamAPI == null) return;

        Team team = teamAPI.getPlayerTeam(player);
        if (team == null) return;

        Material woolType = team.equals(blueTeam) ? Material.BLUE_WOOL : Material.RED_WOOL;
        ItemStack wool = new ItemStack(woolType, amount);

        PlayerInventory inv = player.getInventory();
        inv.addItem(wool);
    }

    public void addBlock(Location loc) {
        placedWool.add(loc);
    }

    public void removeBlock(Location loc) {
        placedWool.remove(loc);
    }

    public boolean isPlacedWool(Location loc) {
        return placedWool.contains(loc);
    }

    public void cleanup() {
        for (Location loc : placedWool) {
            loc.getBlock().setType(Material.AIR);
        }
        placedWool.clear();
    }
}
