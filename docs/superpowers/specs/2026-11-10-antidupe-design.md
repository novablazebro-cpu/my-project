# AntiDupe: design

Date: 2026-11-10. Status: waiting for Nova's review.

## Goal

A Paper plugin for a Minecraft 26.3 survival server that stops item duplication, removes
duplicated copies after the fact where that can be proven, and never removes or punishes on
a guess.

The server runs in offline mode with AuthMe and FastLogin. Known dupes there: shulker box
dupes and relog/rejoin dupes. Players keep finding new ones.

An anti-cheat (movement and combat checks) is a separate, later project in the same plugin.
It is out of scope here.

## What success looks like

1. A duplicated unstackable item, or a duplicated shulker box with its contents, is removed
   automatically no matter which method made it.
2. A legit item is never removed. If the plugin is ever wrong, an admin can give the item back.
3. A player who only received a duped item (trade, gift, pickup) is never warned, kicked or banned.
4. The shulker and relog dupes that work today stop working.
5. No noticeable lag: work happens only when items move, never by scanning the world.

## Limit we accept

Loose stackable items (for example 64 diamonds not inside a shulker box) cannot carry a
serial, because items with different data do not stack. They are protected by the guards
in section 4 and by alerts, not by after-the-fact removal.

## Design

### 1. Serials

- A serial is a random unique ID stored in the item's persistent data container (the
  storage area Paper gives plugins on every item). It survives dropping, pickup, chests,
  death, trading and relogging.
- Covered items: every item whose maximum stack size is 1, which includes shulker boxes,
  tools, weapons, armour, elytra and totems. A config list can exclude item types.
- An item gets its serial the first time the plugin sees it without one: crafted, picked up,
  clicked in an inventory, or found in a player's inventory on join.
- Shulker boxes as blocks: when a box is placed, its serial is copied to the placed block;
  when the block is broken, it is copied back to the dropped item. Whether the game keeps
  the serial by itself here is unverified, so the plugin does not rely on it.
- Admins in creative mode and items made by commands are treated like any other new item:
  they get a fresh serial. Copying an item in creative mode gives the copy a new serial.

### 2. Registry

- Records, for each serial, where it was last seen: a player's inventory or ender chest, a
  container block at a position, a placed shulker block, an item on the ground, or inside
  a shulker box (by that box's serial).
- Kept in memory, saved to an SQLite file in the plugin folder. Writes are batched off the
  main server thread so saving never stalls the game.
- Entries for items destroyed (lava, void, despawn, cactus) are removed when the plugin
  sees the destruction, and otherwise by verification in section 3.

### 3. Detection: verify before delete

When a serial is seen at a new location:

1. Look up its last known location.
2. If that location is not loaded, or no longer holds the serial, the item moved. Update
   the registry. Nothing else happens.
3. If the old location still holds the serial, there are two copies. This is proof.

On proof:

- The newer copy is removed and written to quarantine (section 5). If it is a shulker box,
  its contents go with it.
- The old location being unloaded is never treated as proof. The check is repeated when
  that chunk or player loads.

Inventories are checked when they are opened, when items are clicked or moved in them, on
pickup, on join and on quit. There is no periodic scan of the world.

### 4. Guards

Shulker boxes:
- A shulker block cannot be broken, pushed by a piston or moved by a hopper while a player
  has it open.
- A shulker item cannot be moved out of an inventory while its own inventory view is open.

Relogging (works alongside AuthMe; the plugin detects AuthMe and listens for its login event):
- Between joining and finishing AuthMe login, a player cannot drop, pick up, move or use
  items, and cannot open containers.
- If a player name is already online, the second connection is refused. The first is not kicked.
- On quit, the player's data is saved immediately, and items dropped or put in containers
  in the last moments before quitting are logged.
- Every join and quit near an item transfer is logged, so a new relog method shows up in
  the logs even if it gets through.

### 5. Quarantine

Removed items are stored, complete with all their data, in the SQLite file with: serial,
who held it, where, when and why. `/antidupe restore <id>` gives one back. Entries are kept
for 30 days (configurable).

### 6. Who gets punished

- Punished: the player who was holding or acting on the item when the second copy appeared
  (for example the player whose click, drop or join produced it).
- Not punished: a player who holds a copy but was not present when it appeared. They lose
  the copy and get a message saying the item was a duplicate. Staff are alerted.
- If the plugin cannot tell which of the two, nobody is punished. The copy is quarantined
  and staff are alerted.

### 7. Punishment ladder

Defaults, all editable in `config.yml`:

| Offence | Action |
|---|---|
| 1st | Warning message |
| 2nd | Kick |
| 3rd and later | 2-hour ban |

- Offences are counted per player name, because offline mode has no verified account IDs.
- An offence expires after 30 days.
- Bans are stored by the plugin and enforced at login, so they work before AuthMe login.
- Players with the `antidupe.bypass` permission are never punished.

### 8. Suspected cases

Events that look wrong but are not proof (for example a stackable item count rising right
after a relog) are never acted on. They are written to the log and sent to online staff
with the `antidupe.alerts` permission.

### 9. Commands

All need the `antidupe.admin` permission.

- `/antidupe history <player>`: offences and quarantined items.
- `/antidupe inspect`: serial and registry record of the item in hand.
- `/antidupe pardon <player>`: clear offences and lift a ban.
- `/antidupe restore <id>`: return a quarantined item.
- `/antidupe reload`: reload the config.

### 10. Code layout

Each part has one job and can be tested alone.

| Part | Job | Needs a server to test |
|---|---|---|
| `SerialService` | Read and write serials on items | Yes |
| `Registry` | Remember last location per serial | No |
| `DupeDetector` | The verify-before-delete decision | No |
| `Quarantine` | Store and restore removed items | Partly |
| `PunishmentLadder` | Count offences, pick the action | No |
| `listeners/*` | Turn game events into calls to the parts above | Yes |
| `guards/*` | Shulker and relog guards | Yes |
| `Storage` | SQLite reads and writes | No |

### 11. Build and versions

- Gradle project, Java 25 (required by Paper 26.1 and later).
- Target server: Paper 26.3 (confirmed by Nova). Build against `paper-api` 26.3 with
  `api-version: '26.3'`.
- Fallback, only if the 26.3 API is not yet published: build against the newest 26.x API
  and set `api-version` to match. Paper loads plugins built for an older API version on a
  newer server, so the jar still runs on 26.3. The jar is then tested on a 26.3 test server
  before release either way.
- No server internals are used, only the public Paper API, so updates are less likely to break it.
- AuthMe is an optional dependency: the plugin runs without it and skips the login-gap guard.

### 12. Testing

- Unit tests (JUnit) for `Registry`, `DupeDetector`, `PunishmentLadder` and `Storage`.
- A real Paper test server for everything that touches items and events, including:
  the serial surviving drop, pickup, chest, death and shulker place and break; a forced
  duplicate being caught and quarantined; a moved item not being flagged; the ladder
  stepping warn, kick, ban; restore returning the exact item.
- The relog guards can be tested with a second connection to the test server. The actual
  dupe method Nova's players use is unknown, so that guard is confirmed only on the live
  server's logs.

## Not included

- Anti-cheat checks (next project).
- Tracking stackable items individually.
- Rolling back the world or inventories to an earlier time.
- A web panel or Discord alerts.
