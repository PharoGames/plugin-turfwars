# Turf Wars Gamemode

Turf Wars is a 2-team bow-combat gamemode where kills advance a "turf line" into enemy territory. Teams build wool walls for cover during build/peace phases; combat phases use bows (one-tap kills). When the turf line advances past enemy-built walls, those walls are destroyed. The game cycles through BUILD -> COMBAT -> PEACE -> COMBAT -> ... until one team is fully overtaken or sudden death resolves a tie.

## Features & Mechanics
- **Advancing Turf Line:** Each kill grants lines based on configuration. Reaching the enemy's end wins the game.
- **Phased Gameplay:**
  - **Build / Peace phases:** Construct cover using team-colored wool. Combat is disabled.
  - **Combat phase:** Bows-only combat with one-tap (1000 damage) kills. No melee damage allowed.
  - **Sudden Death:** After a set number of rounds, peace phases are skipped, and kills are worth double lines.
- **Strict Arrow Economy:** Players receive arrows periodically and on kills. Picking up dropped arrows is disabled to prevent hoarding.
- **Seamless Fake Death:** Vanilla death screens are skipped. Players are instantly converted to spectators, healed, and respawned after a delay.
- **Building Restrictions:** 
  - Players can only place their team-colored wool.
  - Players cannot build on the enemy's side of the turf line.
  - Players can only break wool blocks that were placed by players; map breaking is disabled.
- **Anti-Stall & Quality of Life:** 
  - Hunger is permanently locked to full (players can always sprint).
  - Item dropping is disabled.
  - Void death is handled cleanly by converting to a spectator below a configurable Y-level.
- **Cosmetics Integration:** Supports arrow trails during combat, kill effects, and win effects at the end of the match.
- **Scoreboard:** Tracks phase, remaining time, and the current turf line counts for Blue and Red.

For configuration and map setup details, see [docs/configuration.md](docs/configuration.md).
