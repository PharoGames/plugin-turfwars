package com.pharogames.turfwars.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration POJO for TurfWars.
 * Loaded from /data/config/plugin-turfwars.json (written by configloader),
 * with fallback to config.yml in the plugin data folder.
 */
public class TurfWarsConfig {

    @JsonProperty("countdownTime")
    private int countdownTime = 10;

    @JsonProperty("buildTimerSeconds")
    private int buildTimerSeconds = 40;

    @JsonProperty("combatTimerSeconds")
    private int combatTimerSeconds = 90;

    @JsonProperty("peaceTimerSeconds")
    private int peaceTimerSeconds = 16;

    @JsonProperty("arrowRegenIntervalSeconds")
    private int arrowRegenIntervalSeconds = 7;

    @JsonProperty("arrowsOnKill")
    private int arrowsOnKill = 1;

    @JsonProperty("buildWoolAmount")
    private int buildWoolAmount = 50;

    @JsonProperty("peaceWoolAmount")
    private int peaceWoolAmount = 25;

    @JsonProperty("linesPerKill")
    private int linesPerKill = 1;

    @JsonProperty("suddenDeathAfterRounds")
    private int suddenDeathAfterRounds = 5;

    @JsonProperty("suddenDeathLinesPerKill")
    private int suddenDeathLinesPerKill = 2;

    @JsonProperty("respawnDelayTicks")
    private int respawnDelayTicks = 40;

    @JsonProperty("voidDeathY")
    private int voidDeathY = -5;

    @JsonProperty("expectedPlayerWaitTimeout")
    private int expectedPlayerWaitTimeout = 20;

    @JsonProperty("kitSelectorItemId")
    private String kitSelectorItemId = "turfwars_kit_selector";

    @JsonProperty("kitSelectorSlot")
    private int kitSelectorSlot = 8;

    // ========== Getters / Setters ==========

    public int getCountdownTime() { return countdownTime; }
    public void setCountdownTime(int countdownTime) { this.countdownTime = countdownTime; }

    public int getBuildTimerSeconds() { return buildTimerSeconds; }
    public void setBuildTimerSeconds(int buildTimerSeconds) { this.buildTimerSeconds = buildTimerSeconds; }

    public int getCombatTimerSeconds() { return combatTimerSeconds; }
    public void setCombatTimerSeconds(int combatTimerSeconds) { this.combatTimerSeconds = combatTimerSeconds; }

    public int getPeaceTimerSeconds() { return peaceTimerSeconds; }
    public void setPeaceTimerSeconds(int peaceTimerSeconds) { this.peaceTimerSeconds = peaceTimerSeconds; }

    public int getArrowRegenIntervalSeconds() { return arrowRegenIntervalSeconds; }
    public void setArrowRegenIntervalSeconds(int arrowRegenIntervalSeconds) { this.arrowRegenIntervalSeconds = arrowRegenIntervalSeconds; }

    public int getArrowsOnKill() { return arrowsOnKill; }
    public void setArrowsOnKill(int arrowsOnKill) { this.arrowsOnKill = arrowsOnKill; }

    public int getBuildWoolAmount() { return buildWoolAmount; }
    public void setBuildWoolAmount(int buildWoolAmount) { this.buildWoolAmount = buildWoolAmount; }

    public int getPeaceWoolAmount() { return peaceWoolAmount; }
    public void setPeaceWoolAmount(int peaceWoolAmount) { this.peaceWoolAmount = peaceWoolAmount; }

    public int getLinesPerKill() { return linesPerKill; }
    public void setLinesPerKill(int linesPerKill) { this.linesPerKill = linesPerKill; }

    public int getSuddenDeathAfterRounds() { return suddenDeathAfterRounds; }
    public void setSuddenDeathAfterRounds(int suddenDeathAfterRounds) { this.suddenDeathAfterRounds = suddenDeathAfterRounds; }

    public int getSuddenDeathLinesPerKill() { return suddenDeathLinesPerKill; }
    public void setSuddenDeathLinesPerKill(int suddenDeathLinesPerKill) { this.suddenDeathLinesPerKill = suddenDeathLinesPerKill; }

    public int getRespawnDelayTicks() { return respawnDelayTicks; }
    public void setRespawnDelayTicks(int respawnDelayTicks) { this.respawnDelayTicks = respawnDelayTicks; }

    public int getVoidDeathY() { return voidDeathY; }
    public void setVoidDeathY(int voidDeathY) { this.voidDeathY = voidDeathY; }

    public int getExpectedPlayerWaitTimeout() { return expectedPlayerWaitTimeout; }
    public void setExpectedPlayerWaitTimeout(int expectedPlayerWaitTimeout) { this.expectedPlayerWaitTimeout = expectedPlayerWaitTimeout; }

    public String getKitSelectorItemId() { return kitSelectorItemId; }
    public void setKitSelectorItemId(String kitSelectorItemId) { this.kitSelectorItemId = kitSelectorItemId; }

    public int getKitSelectorSlot() { return kitSelectorSlot; }
    public void setKitSelectorSlot(int kitSelectorSlot) { this.kitSelectorSlot = kitSelectorSlot; }
}
