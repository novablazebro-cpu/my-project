# Glossary

Terms from building AntiDupe, in plain words.

| Term | Plain meaning | Where it shows up here |
|---|---|---|
| Plugin | An add-on that a Minecraft server loads to change how the game behaves, like an app on a phone. | The whole project; the jar in `release/`. |
| Paper / Paper API | Paper is the server software. Its API is the list of things a plugin is allowed to ask the server to do, like a restaurant menu. | `plugin/build.gradle.kts` (`paper-api`). |
| `api-version` | A line telling Paper which version a plugin was made for. | `plugin/src/main/resources/plugin.yml`. |
| Dependency | Someone else's code your code needs, downloaded by the build tool. | `paper-api`, `sqlite-jdbc`, JUnit in the `build.gradle.kts` files. |
| Gradle / build | The tool that turns source code into a runnable jar, and runs the tests. Like a kitchen turning a recipe into a meal. | `gradle :plugin:jar`. |
| Jar | A single file holding a compiled Java program. | `release/AntiDupe-0.1.0.jar`. |
| Module | A separate part of the project with its own job. Here `core` (pure logic) and `plugin` (talks to the game). | `core/`, `plugin/`, `selftest/`. |
| Serial number | A random unique ID written inside an item, like the number on a banknote. | `SerialService.java`. |
| Persistent data container (PDC) | A hidden storage area inside an item or block where plugins keep their own data. It travels with the item. | `SerialService.java`, `ShulkerGuard.java`. |
| Entity / entity ID | Any object in the world that is not a block: mobs, players, dropped items. Each has its own ID, which is new every time an item is dropped. | `Holder.ground(...)`, `Holder.entity(...)`. |
| Registry | AntiDupe's notebook of "serial X was last seen in place Y". | `core/.../Registry.java`. |
| Holder | One place items can be: a player's inventory, a chest, the ground, inside a shulker box. | `core/.../Holder.java`. |
| Verify before delete | Only call something a duplicate after checking the original is really still there. | `core/.../DupeDetector.java`. |
| Quarantine | A safe box for removed items so an admin can give them back if the plugin was wrong. | `Storage.quarantine(...)`, `/antidupe restore`. |
| Event / listener | The server announces things that happen ("a player clicked", "a block broke"); a listener is code that reacts. Like a doorbell and someone who answers it. | `plugin/.../listeners/`. |
| Tick | The server's heartbeat: 20 per second. Checks run one tick after a move, once it has finished. | `Bukkit.getScheduler().runTask(...)`. |
| Main thread / async | The main thread runs the game; if it waits, the game lags. Async work runs on a helper thread at the same time. | Database writes in `Enforcer.java`. |
| Race condition | Two threads change the same thing at the same moment and get the wrong answer. Like two people editing one paper at once. | The ladder bug found on the test server; fixed with `synchronized` in `PunishmentLadder.java`. |
| SQLite | A small database that lives in one file. | `plugins/AntiDupe/antidupe.db`, `Storage.java`. |
| Unit test | A small program that checks one piece of code gives the right answer. | `core/src/test/...`. |
| TDD (test-driven development) | Write the test first, watch it fail, then write the code that makes it pass. Proves the test can catch the bug. | Every core class. |
| Offline mode | The server does not check accounts with Mojang, so names are not proven. Why AuthMe exists, and why AntiDupe tracks names, not account IDs. | Your server; `SessionListener.java`. |
| Reflection | Calling another plugin's code found while running, without needing it at build time. | `AuthMeHook.java`. |
| Commit / push / branch | A commit is a saved snapshot with a note; push uploads it to GitHub; a branch is a separate line of work. | Branch `claude/hopeful-knuth-yennkv`. |
