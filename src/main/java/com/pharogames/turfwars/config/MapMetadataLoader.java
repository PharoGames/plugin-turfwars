package com.pharogames.turfwars.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

public class MapMetadataLoader {

    private static final String METADATA_PATH = "/data/config/map-metadata.json";
    private static final String ENV_KEY = "MAP_METADATA";
    private final Logger logger;
    private final ObjectMapper mapper;

    public MapMetadataLoader(Logger logger) {
        this.logger = logger;
        this.mapper = new ObjectMapper();
    }

    public MapMetadata loadMetadata() {
        MapMetadata envMetadata = loadFromEnv();
        if (envMetadata != null) {
            logger.info("Loaded TurfWars map metadata from MAP_METADATA.");
            return envMetadata;
        }

        File file = new File(METADATA_PATH);
        if (!file.exists() || !file.canRead()) {
            logger.warning("Map metadata file not found or unreadable: " + METADATA_PATH);
            return null;
        }

        try {
            MapMetadata metadata = mapper.readValue(file, MapMetadata.class);
            return normalizeAndValidate(metadata, "file " + METADATA_PATH);
        } catch (IOException e) {
            logger.severe("Failed to parse map metadata: " + e.getMessage());
            return null;
        }
    }

    private MapMetadata loadFromEnv() {
        String raw = System.getenv(ENV_KEY);
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            JsonNode root = mapper.readTree(raw);
            MapMetadata metadata = new MapMetadata();
            metadata.blueSpawn = parsePoint(root.get("blueSpawn"));
            metadata.redSpawn = parsePoint(root.get("redSpawn"));
            metadata.arenaMin = parsePoint(root.get("arenaMin"));
            metadata.arenaMax = parsePoint(root.get("arenaMax"));

            Integer floorY = parseInt(root.get("floorY"));
            if (floorY != null) {
                metadata.floorY = floorY;
            }

            Integer totalLines = parseInt(root.get("totalLines"));
            if (totalLines != null) {
                metadata.totalLines = totalLines;
            }

            metadata.turfAxis = parseAxis(root.get("turfAxis"), metadata);
            return normalizeAndValidate(metadata, "MAP_METADATA");
        } catch (Exception e) {
            logger.warning("MAP_METADATA is not valid TurfWars metadata: " + e.getMessage());
            return null;
        }
    }

    private MapMetadata normalizeAndValidate(MapMetadata metadata, String source) {
        if (metadata == null) {
            return null;
        }
        if (metadata.blueSpawn == null || metadata.redSpawn == null
                || metadata.arenaMin == null || metadata.arenaMax == null) {
            logger.severe("Incomplete TurfWars map metadata from " + source + ": missing spawn(s) or arena bounds.");
            return null;
        }
        if (metadata.totalLines <= 0) {
            logger.severe("Invalid TurfWars totalLines from " + source + ": " + metadata.totalLines);
            return null;
        }
        metadata.turfAxis = normalizeAxis(metadata.turfAxis, metadata, source);
        return metadata;
    }

    private String parseAxis(JsonNode node, MapMetadata metadata) {
        String raw = parseString(node);
        if (raw == null || raw.isBlank()) {
            return inferAxis(metadata, "missing turfAxis");
        }
        return normalizeAxis(raw, metadata, "MAP_METADATA");
    }

    private String normalizeAxis(String raw, MapMetadata metadata, String source) {
        if (raw == null) {
            return inferAxis(metadata, source);
        }
        String normalized = raw.trim().toUpperCase();
        if ("X".equals(normalized) || "Z".equals(normalized)) {
            return normalized;
        }
        logger.warning("Invalid TurfWars turfAxis '" + raw + "' from " + source + "; inferring from arena bounds.");
        return inferAxis(metadata, source);
    }

    private String inferAxis(MapMetadata metadata, String source) {
        if (metadata == null || metadata.arenaMin == null || metadata.arenaMax == null) {
            logger.warning("Cannot infer TurfWars turfAxis from " + source + "; defaulting to Z.");
            return "Z";
        }
        double xSpan = Math.abs(metadata.arenaMax.x - metadata.arenaMin.x);
        double zSpan = Math.abs(metadata.arenaMax.z - metadata.arenaMin.z);
        String inferred = zSpan >= xSpan ? "Z" : "X";
        logger.info("Inferred TurfWars turfAxis=" + inferred + " from arena bounds (" + source + ").");
        return inferred;
    }

    private Integer parseInt(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isInt() || node.isLong()) {
            return node.asInt();
        }
        if (node.isObject()) {
            JsonNode valueNode = node.get("value");
            if (valueNode != null && valueNode.isNumber()) {
                return valueNode.asInt();
            }
        }
        return null;
    }

    private String parseString(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isObject()) {
            JsonNode valueNode = node.get("value");
            if (valueNode != null && valueNode.isTextual()) {
                return valueNode.asText();
            }
        }
        return null;
    }

    private Point parsePoint(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        JsonNode valueNode = node;
        if (node.isObject() && node.has("type") && node.has("value")) {
            valueNode = node.get("value");
        }
        if (valueNode == null || !valueNode.isObject()) {
            return null;
        }
        if (!valueNode.has("x") || !valueNode.has("y") || !valueNode.has("z")) {
            return null;
        }

        Point point = new Point();
        point.x = valueNode.get("x").asDouble();
        point.y = valueNode.get("y").asDouble();
        point.z = valueNode.get("z").asDouble();
        return point;
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
