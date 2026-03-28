package com.pharogames.turfwars.game;

import com.pharogames.turfwars.config.MapMetadataLoader.MapMetadata;
import com.pharogames.teams.api.TeamAPI;
import com.pharogames.teams.model.Team;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

public class TurfManager {

    private final World world;
    private final MapMetadata metadata;
    private final WoolManager woolManager;
    private final Team blueTeam;
    private final Team redTeam;

    private int currentLines; // 0 means red wins, totalLines*2 means blue wins
    private final int totalLines;
    private final boolean isZAxis;

    public TurfManager(World world, MapMetadata metadata, WoolManager woolManager, Team blueTeam, Team redTeam) {
        this.world = world;
        this.metadata = metadata;
        this.woolManager = woolManager;
        this.blueTeam = blueTeam;
        this.redTeam = redTeam;

        this.totalLines = metadata.getTotalLines();
        this.currentLines = totalLines; // Starts in the middle
        this.isZAxis = "Z".equalsIgnoreCase(metadata.getTurfAxis());
    }

    public MapMetadata getMetadata() { return metadata; }
    public Team getBlueTeam() { return blueTeam; }
    public Team getRedTeam() { return redTeam; }

    public void advanceBlue(int lines) {
        int oldLines = currentLines;
        currentLines = Math.min(totalLines * 2, currentLines + lines);
        updateTurf(oldLines, currentLines, true);
    }

    public void advanceRed(int lines) {
        int oldLines = currentLines;
        currentLines = Math.max(0, currentLines - lines);
        updateTurf(oldLines, currentLines, false);
    }

    private void updateTurf(int oldLines, int newLines, boolean blueAdvancing) {
        if (oldLines == newLines) return;

        int minRow = Math.min(oldLines, newLines);
        int maxRow = Math.max(oldLines, newLines);

        Material material = blueAdvancing ? Material.BLUE_WOOL : Material.RED_WOOL;

        int minX = (int) metadata.getArenaMin().getX();
        int maxX = (int) metadata.getArenaMax().getX();
        int minZ = (int) metadata.getArenaMin().getZ();
        int maxZ = (int) metadata.getArenaMax().getZ();
        int floorY = metadata.getFloorY();

        int spanMin = isZAxis ? minX : minZ;
        int spanMax = isZAxis ? maxX : maxZ;
        
        int startCoord = isZAxis ? minZ : minX;

        for (int r = minRow; r < maxRow; r++) {
            int coord = startCoord + r;
            for (int s = spanMin; s <= spanMax; s++) {
                int x = isZAxis ? s : coord;
                int z = isZAxis ? coord : s;

                // Update floor
                Block floorBlock = world.getBlockAt(x, floorY, z);
                floorBlock.setType(material);

                // Destroy enemy wool above floor
                for (int y = floorY + 1; y <= metadata.getArenaMax().getY(); y++) {
                    Block b = world.getBlockAt(x, y, z);
                    if (blueAdvancing && b.getType() == Material.RED_WOOL) {
                        b.setType(Material.AIR);
                        woolManager.removeBlock(b.getLocation());
                    } else if (!blueAdvancing && b.getType() == Material.BLUE_WOOL) {
                        b.setType(Material.AIR);
                        woolManager.removeBlock(b.getLocation());
                    }
                }
            }
        }
    }

    public void resetTurf() {
        int startCoord = isZAxis ? (int) metadata.getArenaMin().getZ() : (int) metadata.getArenaMin().getX();
        int spanMin = isZAxis ? (int) metadata.getArenaMin().getX() : (int) metadata.getArenaMin().getZ();
        int spanMax = isZAxis ? (int) metadata.getArenaMax().getX() : (int) metadata.getArenaMax().getZ();
        int floorY = metadata.getFloorY();

        for (int r = 0; r < totalLines * 2; r++) {
            Material mat = (r < totalLines) ? Material.BLUE_WOOL : Material.RED_WOOL;
            int coord = startCoord + r;

            for (int s = spanMin; s <= spanMax; s++) {
                int x = isZAxis ? s : coord;
                int z = isZAxis ? coord : s;

                Block floorBlock = world.getBlockAt(x, floorY, z);
                floorBlock.setType(mat);
            }
        }
        currentLines = totalLines;
    }

    public void enforceBoundary(Player player) {
        TeamAPI teamAPI = TeamAPI.getInstance();
        if (teamAPI == null) return;

        Team team = teamAPI.getPlayerTeam(player);
        if (team == null) return;

        Location loc = player.getLocation();
        double coord = isZAxis ? loc.getZ() : loc.getX();
        
        int startCoord = isZAxis ? (int) metadata.getArenaMin().getZ() : (int) metadata.getArenaMin().getX();
        double boundary = startCoord + currentLines;

        boolean isBlue = team.equals(blueTeam);
        boolean isRed = team.equals(redTeam);

        if (isBlue && coord > boundary) {
            pushBack(player, true);
        } else if (isRed && coord < boundary) {
            pushBack(player, false);
        }
    }

    private void pushBack(Player player, boolean isBlue) {
        Vector velocity = player.getVelocity();
        if (isZAxis) {
            velocity.setZ(isBlue ? -1.0 : 1.0);
        } else {
            velocity.setX(isBlue ? -1.0 : 1.0);
        }
        velocity.setY(0.2);
        player.setVelocity(velocity);
    }

    public boolean checkWinCondition() {
        return currentLines <= 0 || currentLines >= totalLines * 2;
    }

    public Team getWinningTeam() {
        if (currentLines <= 0) return redTeam;
        if (currentLines >= totalLines * 2) return blueTeam;
        return null; // No winner yet
    }
    
    public int getBlueLines() {
        return currentLines;
    }
    
    public int getRedLines() {
        return (totalLines * 2) - currentLines;
    }
}
