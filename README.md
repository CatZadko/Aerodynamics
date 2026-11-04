# Aerodynamics — Paper 26.3 / Java 25

A complete server-side plugin that adds the **Aerodynamics** enchantment for the Happy Ghast.
The build dependency is pinned to `io.papermc.paper:paper-api:26.3.build.241-stable`.
No client-side mods or resource packs are required.
This archive contains the source code; a prebuilt plugin JAR is not included.

## Installation

1. Completely stop your Paper 26.3 server running on Java 25.
2. Place `Aerodynamics-1.0.0.jar` in the `plugins` folder.
3. Start the server. The configuration will be created at `plugins/Aerodynamics/config.yml`.
4. Give yourself OP or the `aerodynamics.admin` permission to use the testing commands.

The enchantment is registered during bootstrap. Installing or removing the JAR requires a full server restart.
Do not load this plugin through PlugMan or `/reload`. `/aero reload` only reloads the configuration.
Before removing the plugin, remove items carrying its registered enchantment or back up your world:
without the custom enchantment being registered, the server cannot preserve it as an active enchantment.

## In-Game Testing

```text
/aero give YourUsername harness 5
/summon minecraft:happy_ghast ~ ~3 ~
/aero status
```

Equip the provided harness on an adult Happy Ghast using a normal right-click, then mount it.
`/aero status` displays the target speed, speed limit, measured speed, effective
`FLYING_SPEED`, TPS, and average tick duration. The measured speed is based on real elapsed seconds,
so at low TPS it will be lower than the nominal speed even without the limiter.

Admin commands:

| Command | Description |
|---|---|
| `/aero give <player> book <1–5>` | Give one enchantment book for testing |
| `/aero give <player> harness <1–5>` | Give an enchanted blue harness |
| `/aero status` | Diagnose the Happy Ghast the player is riding; from the console, show manager status |
| `/aero reload` | Validate and reload `config.yml` |

Admin item distribution intentionally bypasses Survival progression. Regular players are not granted this permission.

## Speeds

| Level | Target speed (blocks/s) |
|---|---:|
| Unenchanted, player-controlled | 5 |
| I | 10 |
| II | 14 |
| III | 19 |
| IV | 24 |
| V | 45 |

Level V follows the latest technical requirement: the speed of a boat on blue ice.
The original concept specified 30 blocks/s for level V. To restore that value, set `flight.speeds.'5'` to `30.0` and run `/aero reload`.

These values describe steady-state horizontal forward flight at 20 TPS, without collisions,
in fully loaded terrain. Ascending, reversing, and turning retain the characteristics of vanilla controls.
For an unenchanted, player-controlled Happy Ghast, the plugin normalizes speed to the configured 5 blocks/s.
Free-flying Happy Ghasts without a pilot retain their original attributes.

In Paper 26.3, the attribute value cannot be treated as blocks/s or simply multiplied by a desired factor.
The native `HappyGhast` implementation uses the value twice:

```text
scaledInput = min(1, 3.9f * attribute)
accelerationPerTick = attribute * 5 / 3 * scaledInput
terminalBps = 20 * accelerationPerTick / (1 - 0.91f)
```

`FlightMath.attributeForBps` inverts this piecewise relationship. The formula is verified by a discrete-physics test.
`flight.speed-calibration` is available for server builds that modify flight physics; on standard Paper, its value is 1.0.

Speed is adjusted using the plugin's own **transient modifier**, `aerodynamics:flight_speed`, on
`Attribute.FLYING_SPEED`. This changes the effective attribute while preserving its base value and modifiers from other plugins.
The modifier is not written to NBT and is removed when the last passenger dismounts, the entity unloads, or the plugin shuts down.
Another plugin controlling the same attribute can affect the resulting speed; two independent speed-control systems
should not be enabled for the same mob.

## Items and Enchantment

`AeroBootstrap` registers the custom key `aerodynamics:aerodynamics` through Paper's Registry API.
This registration does not replace or modify vanilla enchantments. The server sends the description to the client,
so the name **Aerodynamics** appears in the standard enchanting interface and on enchanted items.

`EnchantmentManager` also stores the following values in the Persistent Data Container (PDC):

- `aerodynamics:level` — enchantment level, from 1 to 5;
- `aerodynamics:kind` — `book` or `harness`;
- `aerodynamics:origin` — how the item was obtained.

A level is considered valid only when the PDC data matches the registered enchantment.
Renaming an item or copying its lore does not create a functional enchantment.
PDC is not cryptographic protection against an operator or another trusted plugin with full server access.

The plugin uses real vanilla harnesses in all 16 colors. They are equipped normally, occupy `EquipmentSlot.BODY`,
and can be removed with shears. A regular saddle can also be combined with an enchantment book in an anvil to create a blue harness.
Modern CustomModelData is assigned through a component, without the deprecated `setCustomModelData(int)` method; a custom model is optional.

## Renewable Acquisition

All probabilities are per attempt. Level I and II outcomes are mutually exclusive.

| Source | I | II | Conditions |
|---|---:|---:|---|
| Enchanting table | 8% | 2% | Third enchanting option at level 30 |
| Wild Ghast | 4% | 0.4% | Kill credited to a player |
| Phantom | 0.5% | 0.05% | Kill credited to a player |
| Piglin bartering | 0.4% | 0.1% | Normal gold-ingot barter; the book is added to the result |
| Master Librarian | — | Guaranteed | 64 emeralds + 16 Ghast Tears |

At the enchanting table, you can use a regular book, a saddle, or an unenchanted harness.
On success, the book becomes a pure Aerodynamics enchantment book, the saddle becomes an enchanted blue harness,
and an existing harness retains its color. On failure, a regular book receives normal vanilla enchantments.
If an attempt with a saddle or harness fails, the item stays unchanged, but the attempt still costs 3 levels and 3 lapis lazuli.
Such an attempt requires 15 active bookshelves and at least 30 experience levels; the probability is shown in the action bar.
Creative mode does not consume resources or experience.

Librarians receive a separate trade offer without losing their existing trades.
Existing master librarians receive the offer upon first interaction; newly promoted masters receive it after unlocking their final trades.
The price is not affected by discounts. By default, the trade can be used **once per villager restock**, not once per in-game day.
The trade is not duplicated when the trading interface is reopened.

The enchantment is not included in vanilla tags for random loot, trading, or enchanting. `LootGenerateEvent` also
removes the plugin's items from generated loot, so loot chests do not become a source of books.
Books that players place in ordinary chests are not removed. Blocking every `ItemSpawnEvent` for levels III–V would
also destroy legitimately crafted books when dropped or on player death. The plugin does not do this.

## Anvil and Smithing Table

| Operation | Result | Cost |
|---|---|---:|
| I + I | II | 8 levels |
| II + II | III | 18 levels |
| III + III | IV | 36 levels |
| IV + IV | Not allowed | — |
| Level IV book + Netherite Ingot, smithing table | Level V book | 1 ingot |
| Harness/saddle + level I / II / III / IV / V book | Harness with the corresponding level | 4 / 6 / 8 / 10 / 12 levels |

At the smithing table, the first slot (template) must be **empty**, the second must contain a level IV book,
and the third must contain a Netherite Ingot.
The plugin registers a real `SmithingTransformRecipe` with `RecipeChoice.empty()` and then validates the PDC in
`PrepareSmithingEvent`. Merely displaying an output without registering a recipe would not be sufficient.

Anvil upgrades are only possible with two pure enchantment books of the same level. Harness + harness does not increase the level.
A book of equal or lower level cannot be applied to an already enchanted harness. Renaming a custom item is supported
as a separate operation costing one level. Grindstones are disabled for these items to avoid leaving PDC data behind after disenchanting.
Prior-work anvil penalties are not accumulated for special operations. These operations do not damage the anvil block.

`PrepareAnvilEvent` and `PrepareSmithingEvent` only prepare the result. When the result is taken, the plugin cancels the vanilla click.
On the next tick, it rechecks the open interface, exact input items, item name, experience, and available inventory space,
then consumes the required items/experience and delivers the result exactly once. Normal clicks and Shift-clicks are supported.
Number-key swaps, dropping the result, and double-clicking are blocked for special operations.
Closing the interface, having a full inventory, holding an item on the cursor, or lacking experience does not consume resources.

## Flight Limiter

Movement is detected using `PlayerMoveEvent`, `VehicleMoveEvent`, mounting events, equipment changes, and a fallback
rider check once per second. Attributes are updated by a shared task every 2 ticks for active Happy Ghasts only.
The plugin does not scan every entity in every world each tick, and it does not access the Bukkit API asynchronously.

At levels IV–V, when TPS falls below 18 or smoothed tick duration exceeds 55 ms, the target speed is reduced to 10 blocks/s.
Recovery begins after TPS stays at or above 19 and tick duration stays at or below 45 ms for 8 seconds.
Tick-duration metrics allow a faster response than one-minute TPS measurements. The plugin does not assume that
chunks are the only cause of overload: the safeguard responds to overall server performance degradation.

Acceleration is 6 blocks/s², while target-speed reduction is 16 blocks/s². These values control the target steady-state speed;
actual movement still follows the inertia of vanilla flight physics.

The plugin also checks the flight corridor ahead, taking the Ghast's width, stopping distance, and chunk delivery to all passengers into account.
It uses `World.isChunkLoaded` and `Player.isChunkSent`. The check does not call `getChunkAt`, load or generate chunks, or use `forceload`.
When there are not enough ready chunks ahead, the maximum speed is reduced. If the boundary is already too close,
an emergency stop resets momentum. `EntityMoveEvent` provides an additional safeguard against unexpected turns.
The Ghast resumes moving when normal server-side chunk loading has prepared the area ahead.

A speed of 72.7 blocks/s requires a sufficiently large loaded radius around players. A short view distance may
continually limit level V — this is expected behavior of the safeguard. The limiter cannot guarantee that the server will never freeze.

## Building

### Windows PowerShell

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
```

The script searches for an installed **JDK 25** in the standard Java, Microsoft, Adoptium, Zulu, BellSoft, and `.jdks` directories.
It does not modify the system PATH and selects the correct JDK even if Java 17 is on PATH. For a custom installation directory:

```powershell
.\build.ps1 -JdkPath "C:\Java\jdk-25"
```

### Linux/macOS

```bash
export JAVA_HOME=/path/to/jdk-25
chmod +x mvnw
./mvnw clean verify
```

The resulting file is `target/Aerodynamics-1.0.0.jar`. Maven does not need to be installed separately: the wrapper downloads Maven 3.9.11
and verifies the distribution's SHA-256 checksum. The first build requires access to Maven Central and the Paper repository.

`pom.xml` enables `-Xlint:deprecation` and `-Werror`: using deprecated APIs in the source code causes the build to fail.
Paper dependencies use the `provided` scope and are not bundled into the plugin.

## Classes

| File | Responsibility |
|---|---|
| `AeroBootstrap.java` | Register the enchantment through the Registry API |
| `AerodynamicsPlugin.java` | Plugin lifecycle, listeners, recipe, and commands |
| `config/AeroConfig.java` | Validate and load configuration |
| `item/EnchantmentManager.java` | Manage PDC data, books, and harnesses |
| `item/UpgradePlanner.java` | Validate inputs and calculate results without modifying inventories |
| `listener/AcquisitionListener.java` | Enchanting table, drops, bartering, villagers, and loot exclusions |
| `listener/WorkstationListener.java` | Anvil, smithing table, transactions, and grindstone |
| `listener/FlightListener.java` | Movement, equipment, mounting, and unloading events |
| `flight/FlightManager.java` | Flight attribute, load limiting, and chunk readiness |
| `logic/FlightMath.java` | Speed conversion and stopping distance |
| `logic/LoadGuard.java` | Limiter hysteresis |
| `logic/Chance.java` | Mutually exclusive random level I–II outcomes |
| `logic/Progression.java` | Allowed level transitions |
| `command/AeroCommand.java` | Admin commands |

Source path: `src/main/java/ru/miroslav/aerodynamics/`.

## Validation

Automated tests are located in `src/test/java` and cover acquisition progression, rejection of IV+IV,
smithing-table requirements, probabilities, recovery after overload, negative chunk coordinates, and flight-physics consistency.
Build verification steps and exact results are documented in `VALIDATION.md`.

Before deploying to your main server, test compatibility with its protection and inventory-management plugins:

1. In Survival mode, combine books and take the result using both normal and Shift-clicks; experience and both input items must be consumed exactly once.
2. Repeat with insufficient experience, a full inventory, and an occupied cursor; no result should be granted and nothing should be consumed.
3. Try IV+IV and III+Netherite; neither should produce a result. IV+Netherite with an empty template slot should produce V.
4. Remove a harness with shears, give it to another player, and restart the server; its level should be preserved.
5. In open terrain, compare `/aero status` with the configured speeds; test flight with multiple passengers.
6. To safely test the limiter, temporarily set `enter-tps: 20.0`, `exit-tps: 20.0`,
   `enter-mspt: 1.0`, and `exit-mspt: 1.0`,
   run `/aero reload`, and verify that level IV–V speed is reduced. Then restore the original thresholds and reload again.

## Primary API Sources

- https://docs.papermc.io/paper/dev/project-setup/
- https://docs.papermc.io/paper/dev/registries/
- https://docs.papermc.io/paper/dev/getting-started/paper-plugins/
- https://jd.papermc.io/paper/26.3/
- https://github.com/PaperMC/Paper

The flight formula was cross-checked against the `HappyGhast` and `LivingEntity` classes in the official Paper 26.3 build 241.
