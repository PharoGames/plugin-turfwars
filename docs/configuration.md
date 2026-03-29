# Turf Wars Configuration

## Config Keys

The plugin reads from `config.yml` in the plugin data folder (provided by Config Service under scope `plugin:plugin-turfwars`).

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
- **Death Logic (Fake Death):** Vanilla death events are preempted by an `EntityDamageEvent` handler. Fatal damage (including fall damage and void) clears the player's inventory, heals them to max, and places them into spectator mode via `SpectatorAPI`. After `respawnDelayTicks`, players are always restored at their team spawn with kit re-applied, in any active match phase (`BUILD`, `PEACE`, `COMBAT`, etc.). Kill credit, turf line movement, coins, and the elimination broadcast run only during `COMBAT` or `SUDDEN_DEATH` when there is a killer.
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

## Production registration (Admin Dashboard & Metadata Service)

Gamemodes players see in the **Admin Dashboard** and **Metadata Service** are **not** the same string as the Kubernetes **server type** slug.

| Concept | Value | Where it is used |
|--------|--------|------------------|
| **Server type** (orchestrator / image / pod) | `turfwars` | `infrastructure/server-types/turfwars.yaml`, `SERVER_TYPE`, Velocity/orchestrator APIs that allocate pods |
| **Registry gamemode ID** (maps, rotations, UI) | `TURF_WARS` | `metadata.gameType` under `server-types/turfwars.yaml`, MongoDB `gamemodes` collection `_id`, map `gameType`, rotation `gameType` |
| **Kit/stats key inside this plugin** | `turfwars` | `MatchManager` → `GameplayRuntime.openKitSelector(..., "turfwars", ...)` and match stats payload (same pattern as MicroBattles using `microbattles` vs `MICRO_BATTLES`) |

Infrastructure already declares the link between server type and registry ID:

```yaml
# infrastructure/server-types/turfwars.yaml (excerpt)
metadata:
  gameType: TURF_WARS
```

### 1. Register the gamemode in the Admin Dashboard

1. Open **Admin Dashboard** → **Gamemodes** (`/dashboard/gamemodes`).
2. Click **Create Gamemode**.
3. **ID**: enter exactly `TURF_WARS` (must match `metadata.gameType` in `server-types/turfwars.yaml`).  
   Do **not** use `turfwars` here—that is the server-type slug, not the metadata gamemode ID.
4. **Display Name**: e.g. `Turf Wars`.
5. **Enabled**: on.
6. **Default Rotation ID** (optional): create a rotation first (step 2), then paste that rotation’s `_id` here.
7. **Map metadata schema** (recommended): add entries so the map upload/editor UI matches what the game expects. Align **key names** with the JSON in [Map Metadata (`map-metadata.json`)](#map-metadata-map-metadatajson) above. Suggested schema rows:

   | Key | Type | Required | Notes |
   |-----|------|----------|--------|
   | `turfAxis` | `string` | yes | `X` or `Z` |
   | `blueSpawn` | `location` | yes | Team blue spawn |
   | `redSpawn` | `location` | yes | Team red spawn |
   | `arenaMin` | `location` | yes | Arena AABB min corner |
   | `arenaMax` | `location` | yes | Arena AABB max corner |
   | `floorY` | `int` | yes | Floor level for turf strip |
   | `totalLines` | `int` | yes | Half-width in “lines”; default e.g. `50` |

8. Save. The dashboard calls the Metadata Service `POST /gamemodes`, which creates the document in MongoDB.

### 2. Create a rotation

1. **Admin Dashboard** → **Rotations** (`/dashboard/rotations`).
2. Create a rotation with **game type** exactly `TURF_WARS` (same as gamemode `_id`).
3. Attach map IDs that also have `gameType` = `TURF_WARS`.

### 3. Upload maps

1. **Admin Dashboard** → **Maps** → upload (or edit) maps.
2. Set **game type** to `TURF_WARS`.
3. Fill **map metadata** using the schema you defined (spawn, arena bounds, etc.).  
   After orchestration/config merge, the game server still consumes a merged **`/data/config/map-metadata.json`** shaped like the flat JSON in this doc; the pipeline should supply the same field names.

### 4. Matchmaking (Config Service)

Matchmaking loads per–game-type settings from the Config Service using scope `matchmaking.<gameType>` (e.g. `matchmaking.TURF_WARS`) and expects resolved YAML with a `matchmaking.TURF_WARS` (or nested) section—see **Config Service** docs and existing entries for other modes.  
If you add queues or API calls that use `gameType=TURF_WARS`, ensure a matching config entry exists or the service will fall back to defaults (`minPlayers` / `maxPlayers` / `maxWaitSeconds`, etc.).

### 5. What you do *not* need to change for “registration”

- **Server type YAML** and **image manifest** for `turfwars` are already part of infrastructure GitOps.
- Registering the gamemode in the dashboard **only** creates the Metadata Service record so maps, rotations, and tooling know about `TURF_WARS`; it does not deploy pods by itself.
