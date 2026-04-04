package com.pharogames.turfwars.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.logging.Logger;

public class MapMetadataLoader {

    private static final String ENV_KEY = "MAP_METADATA";
    private final Logger logger;

    public MapMetadataLoader(Logger logger) {
        this.logger = logger;
    }

    public MapMetadata loadMetadata() {
        MapMetadata envMetadata = loadFromEnv();
        if (envMetadata != null) {
            logger.info("Loaded TurfWars map metadata from MAP_METADATA.");
            return envMetadata;
        }

        logger.warning("Map metadata missing from MAP_METADATA env var.");
        return null;
    }

    private MapMetadata loadFromEnv() {
        String raw = System.getenv(ENV_KEY);
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
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

    private String parseAxis(JsonElement node, MapMetadata metadata) {
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

    private Integer parseInt(JsonElement node) {
        if (node == null || node.isJsonNull()) {
            return null;
        }
        if (node.isJsonPrimitive() && node.getAsJsonPrimitive().isNumber()) {
            return node.getAsInt();
        }
        if (node.isJsonObject()) {
            JsonElement valueNode = node.getAsJsonObject().get("value");
            if (valueNode != null && valueNode.isJsonPrimitive() && valueNode.getAsJsonPrimitive().isNumber()) {
                return valueNode.getAsInt();
            }
        }
        return null;
    }

    private String parseString(JsonElement node) {
        if (node == null || node.isJsonNull()) {
            return null;
        }
        if (node.isJsonPrimitive() && node.getAsJsonPrimitive().isString()) {
            return node.getAsString();
        }
        if (node.isJsonObject()) {
            JsonElement valueNode = node.getAsJsonObject().get("value");
            if (valueNode != null && valueNode.isJsonPrimitive() && valueNode.getAsJsonPrimitive().isString()) {
                return valueNode.getAsString();
            }
        }
        return null;
    }

    private Point parsePoint(JsonElement node) {
        if (node == null || node.isJsonNull()) {
            return null;
        }

        JsonElement valueNode = node;
        if (node.isJsonObject() && node.getAsJsonObject().has("type") && node.getAsJsonObject().has("value")) {
            valueNode = node.getAsJsonObject().get("value");
        }
        if (valueNode == null || !valueNode.isJsonObject()) {
            return null;
        }
        JsonObject obj = valueNode.getAsJsonObject();
        if (!obj.has("x") || !obj.has("y") || !obj.has("z")) {
            return null;
        }

        Point point = new Point();
        point.x = obj.get("x").getAsDouble();
        point.y = obj.get("y").getAsDouble();
        point.z = obj.get("z").getAsDouble();
        return point;
    }

    public static class MapMetadata {
        private String turfAxis = "Z";
        private Point blueSpawn;
        private Point redSpawn;
        private Point arenaMin;
        private Point arenaMax;
        private int floorY = 64;
        private int totalLines = 50;

        public String getTurfAxis() { return turfAxis; }
        public Point getBlueSpawn() { return blueSpawn; }
        public Point getRedSpawn() { return redSpawn; }
        public Point getArenaMin() { return arenaMin; }
        public Point getArenaMax() { return arenaMax; }
        public int getFloorY() { return floorY; }
        public int getTotalLines() { return totalLines; }
    }

    public static class Point {
        private double x;
        private double y;
        private double z;

        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
    }
}
