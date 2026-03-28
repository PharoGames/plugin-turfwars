package com.pharogames.turfwars.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

public class MapMetadataLoader {

    private static final String METADATA_PATH = "/data/config/map-metadata.json";
    private final Logger logger;
    private final ObjectMapper mapper;

    public MapMetadataLoader(Logger logger) {
        this.logger = logger;
        this.mapper = new ObjectMapper();
    }

    public MapMetadata loadMetadata() {
        File file = new File(METADATA_PATH);
        if (!file.exists() || !file.canRead()) {
            logger.warning("Map metadata file not found or unreadable: " + METADATA_PATH);
            return null;
        }

        try {
            return mapper.readValue(file, MapMetadata.class);
        } catch (IOException e) {
            logger.severe("Failed to parse map metadata: " + e.getMessage());
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MapMetadata {
        @JsonProperty("turfAxis")
        private String turfAxis = "Z";

        @JsonProperty("blueSpawn")
        private Point blueSpawn;

        @JsonProperty("redSpawn")
        private Point redSpawn;

        @JsonProperty("arenaMin")
        private Point arenaMin;

        @JsonProperty("arenaMax")
        private Point arenaMax;

        @JsonProperty("floorY")
        private int floorY = 64;

        @JsonProperty("totalLines")
        private int totalLines = 50;

        public String getTurfAxis() { return turfAxis; }
        public Point getBlueSpawn() { return blueSpawn; }
        public Point getRedSpawn() { return redSpawn; }
        public Point getArenaMin() { return arenaMin; }
        public Point getArenaMax() { return arenaMax; }
        public int getFloorY() { return floorY; }
        public int getTotalLines() { return totalLines; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Point {
        @JsonProperty("x") private double x;
        @JsonProperty("y") private double y;
        @JsonProperty("z") private double z;

        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
    }
}
