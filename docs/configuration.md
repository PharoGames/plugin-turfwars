# Turf Wars Configuration

## Config Keys

The plugin reads from `/data/config/plugin-turfwars.json` (provided by Config Service under scope `plugin:plugin-turfwars`). If not found, it falls back to `config.yml` in the plugin data folder.

```yaml
countdownTime: 10
buildTimerSeconds: 40
combatTimerSeconds: 90
peaceTimerSeconds: 16
arrowRegenIntervalSeconds: 7
arrowsOnKill: 1
buildWoolAmount: 50
peaceWoolAmount: 25
linesPerKill: 1
suddenDeathAfterRounds: 5
suddenDeathLinesPerKill: 2
respawnDelayTicks: 40
voidDeathY: -5
expectedPlayerWaitTimeout: 20
kitSelectorItemId: turfwars_kit_selector
kitSelectorSlot: 8
```

## Map Metadata (`map-metadata.json`)

The map metadata must contain bounds, spawn points, and the axis for the turf line:

```json
{
  "turfAxis": "Z",
  "blueSpawn": { "x": 0, "y": 65, "z": -40 },
  "redSpawn": { "x": 0, "y": 65, "z": 40 },
  "arenaMin": { "x": -30, "y": 60, "z": -50 },
  "arenaMax": { "x": 30, "y": 90, "z": 50 },
  "floorY": 64,
  "totalLines": 50
}
```

- `turfAxis`: Can be `X` or `Z`. The lines will advance along this axis.
- `totalLines`: The number of lines each team starts with.
- `arenaMin` / `arenaMax`: The bounding box of the arena. Used to determine the span of the turf lines and where to clear blocks.
- `floorY`: The Y-level of the arena floor, where the turf line block changes happen.

## Game Phases

1. `WAITING`: Waiting for players. Kits can be selected.
2. `COUNTDOWN`: Short countdown before teleport.
3. `BUILD`: Players receive initial wool to build cover. No combat.
4. `COMBAT`: Bows only combat. Kills advance the turf line.
5. `PEACE`: No combat. Players receive some wool to rebuild cover.
6. `SUDDEN_DEATH`: Reached after X rounds. No more peace phases. Kills are worth more lines.
7. `ENDED`: Match is over, stats reported.
