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
4. `COMBAT`: Bows only combat. Kills advance the turf line. Arrow generation runs.
5. `PEACE`: No combat. Players receive some wool to rebuild cover.
6. `SUDDEN_DEATH`: Reached after X rounds. No more peace phases. Kills are worth more lines.
7. `ENDED`: Match is over, stats reported, win effects play, redirect to lobby.

## Mechanics Enforced by GameListener

To ensure smooth gameplay, the following vanilla mechanics are overridden or blocked:
- **Block Placement/Breaking:** Only `BLUE_WOOL` and `RED_WOOL` can be placed. Breaking map blocks is disallowed; only placed wool can be broken. Building on enemy territory (past the current turf line) is explicitly blocked.
- **Damage/PvP:** Only arrows do damage, scaled to 1000.0 (one-tap kill). Melee damage is cancelled entirely.
- **Death Logic (Fake Death):** Vanilla death events are preempted by an `EntityDamageEvent` handler. Fatal damage clears the player's inventory, heals them to max, and places them into spectator mode via `SpectatorAPI`. This prevents vanilla death screens and allows immediate respawn timing logic.
- **Item Drops & Pickups:** Players cannot drop items. Arrow pickups are blocked to enforce the arrow economy via `ArrowManager`.
- **Food/Hunger:** Hunger loss is disabled; players are locked at full food (20) to always allow sprinting.
- **Boundary Enforcement:** Players attempting to cross the turf line are forcibly pushed backwards.

## Dependencies & Integrations

TurfWars integrates closely with the following shared plugins:
- **Teams (`plugin-teams`)**: Two teams (Blue/Red), friendly fire disabled.
- **Spectator (`plugin-spectator`)**: Manages fake deaths and late-joiners.
- **Cosmetics (`plugin-cosmetics`)**: Renders arrow trails dynamically during the `COMBAT` phase and plays kill/win effects.
- **GameplayRuntime (`plugin-gameplay-runtime`)**: Provides kit selection for bows/armor, though custom arrows/wool are given directly by Turf Wars logic.
- **PlayerData & Coins**: Reports match stats (kills, wins, duration) and grants coin rewards per kill.
- **Communicator & Scoreboard**: Handles all titles, messages, action bars, and scoreboard lines.
- **RelayBackend (`plugin-relay-backend`)**: Defers starting until expected matchmaking players arrive, and sends players back to the lobby when the match ends.
