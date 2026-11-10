# AntiDupe

A Paper **26.3** plugin that stops item duplication on survival servers, removes duplicated copies
after the fact when it can prove them, and only punishes the player who made the copy.

**Download:** [`release/AntiDupe-0.1.0.jar`](release/AntiDupe-0.1.0.jar)

## Install

1. Server on Paper 26.3 with Java 25.
2. Put `AntiDupe-0.1.0.jar` in `plugins/` and restart (not `/reload`).
3. Optional: edit `plugins/AntiDupe/config.yml`, then `/antidupe reload`.

Works with AuthMe and FastLogin (offline-mode servers). AuthMe is optional.

## How it works

**Serial numbers.** Every item that cannot stack (shulker boxes, tools, weapons, armour, elytra,
totems, books and quills...) gets a hidden random serial number, stored inside the item. A dupe
copies the serial too, so two items with the same serial are proof of a dupe, whatever method
made them.

**Verify before delete.** When an item with a serial shows up somewhere, AntiDupe checks the place
it was last seen. If the item is gone from there, it simply moved. If it is still there, the new
one is a copy and is removed. If the old place cannot be checked (player offline, chunk unloaded),
nothing happens until it can.

**Shulker boxes.** The box has a serial, and so does every unstackable item inside it. A duped box
is removed whole. If a dupe gives the box a new serial, the gear inside is still caught, item by item.

**Guards.**
- A shulker box that someone has open cannot be broken, pushed by a piston or emptied by a hopper.
- A second login with a name that is already online is refused (instead of kicking the first
  connection mid-save, a classic relog dupe).
- With AuthMe: until a player types `/login`, they cannot drop, pick up, move or use any item.

**Quarantine.** Removed copies are saved for 30 days. `/antidupe restore <id>` gives one back.

**Punishments** (editable): 1st offence warning, 2nd kick, 3rd and later 2-hour ban. Offences
expire after 30 days. Several copies removed within 10 seconds are one offence.

**Nobody is punished when it is not clear who made the copy:**
- a player who picks up or is given a copy (it is removed; they get a message saying they are not in trouble),
- copies found when opening a chest,
- clicks in a chest that two or more players have open,
- the first 30 minutes after a server crash (crashes can duplicate items by themselves).

Staff with `antidupe.alerts` see every removal. A player who rejoins with 64+ more of a stackable
item than they left with triggers a "suspected" alert; nobody is punished for that.

## Commands and permissions

| Command | What it does |
|---|---|
| `/antidupe history <player>` | Offences, ban, quarantined items |
| `/antidupe inspect` | Serial and last known place of the item in your hand |
| `/antidupe pardon <player>` | Clear offences and lift the ban |
| `/antidupe restore <id> [player]` | Give back a quarantined item (with a fresh serial) |
| `/antidupe reload` | Reload `config.yml` |

| Permission | Default |
|---|---|
| `antidupe.admin` (commands) | op |
| `antidupe.alerts` (see alerts) | op |
| `antidupe.bypass` (never punished; copies still removed) | nobody |

## Limits: read these

- **Stackable items (diamonds, iron, food) cannot be un-duped after the fact.** They cannot carry
  serials, because different serials would stop them stacking. They are protected by the guards,
  by being inside a tracked shulker box, and by the "suspected" alert.
- **New dupe methods.** Any dupe of an unstackable item is caught. A brand-new dupe of loose
  stackables may get through; the suspected alerts and logs are how you will find it.
- **Plugins that copy items.** Kit, shop or GUI plugins that hand out copies of a *saved* item
  could hand out the same serial twice. If you use one, add those items to `excluded-items`, or run
  `mode: monitor` for a day and read the alerts before switching to `enforce`.
- **Admin rollbacks** (for example CoreProtect restoring stolen items) can create two items with one
  serial. Use `/antidupe pardon` if someone is punished for that.
- **Creative mode is not tracked**: creative players can clone items legitimately. Items get fresh
  serials when a player leaves creative.
- **AuthMe connection** is made through AuthMe's public API at runtime and was not tested against a
  real AuthMe install. Check the startup log for `Connected to AuthMe`.

## What was tested

- 32 automated tests of the logic (registry, duplicate detection, punishments, database).
- On a real Paper 26.3 (build 248) server, a test plugin played these scenarios, all passing:
  new item gets a serial; a copy in another chest is removed and the original kept; moving an item
  is not flagged; two copies in one chest; a copy hidden in a shulker box; a duplicated shulker
  box; an original in an unloaded chunk (nothing removed); a copy of an item lying on the ground;
  quarantine saving and rebuilding items; 5 copies at once = 1 warning; crash detection.
- **Not tested here:** anything that needs a real player: clicking, dropping, picking up, joining,
  AuthMe login, bans at login. Test those on your server before relying on them (see below).

### Checklist for your server

1. Start the server and check the log says `AntiDupe enabled in ENFORCE mode`.
2. In survival, hold an elytra and run `/antidupe inspect`: it should show a serial.
3. As op in creative, middle-click clone it, switch to survival: no alert (clones get new serials).
4. Put an item in a chest and take it out several times: no alerts.
5. Join twice with the same name from two clients: the second is refused.
6. With AuthMe: before `/login`, try to drop an item: nothing happens.

## Building from source

```bash
gradle :core:test :plugin:jar
```

Needs Java 25. The jar is `plugin/build/libs/AntiDupe-0.1.0.jar`. The `selftest` module is a
test-only plugin for a throwaway server; never install it on a real one.

Design: `docs/superpowers/specs/2026-11-10-antidupe-design.md`.
Plan: `docs/superpowers/plans/2026-11-10-antidupe.md`.
