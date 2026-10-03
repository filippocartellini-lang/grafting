## AI disclosure

This project was built with the help of Claude. The concept, the three modes and every design
decision listed below came out of my own design. The code itself was done with the help of Claude.

## The three modes


| Mode | What gets grafted | Short version |
|------|-------------------|---------------|
| **Ability Graft** | A target and a bystander | Whatever the grafted player aims at you hits them instead; whatever would have healed them heals you |
| **Time Graft** | Two moments of your own life | Save a checkpoint, and a few seconds later you are pulled back to it. Death inside the window is skipped too |
| **Height Graft** | The bottom and the top of a fall | The target lands, and is sent back above where it stood. It cannot walk and cannot stay airborne |

Note: the server already has a *Location Grafting* ability (persistent portals), so this plugin deliberately has
**no teleport links**. The "beginning and end of a path" idea from the lore is explored vertically instead (Height Graft).

## Controls

Use `/grafting give [player]` (permission `grafting.give`, op by default) to receive the items.

| Item | Left click | Right click |
|------|-----------|-------------|
| **Wand of Grafting** (amethyst shard) | Next mode (shown in the action bar) | Use the current mode |
| **Arcane Strike** (blaze rod) | - | Single-target damage on whoever you look at |
| **Blessing** (heart of the sea) | - | Speed II and Regeneration II on yourself |
| **Arcane Wave** (echo shard) | - | Area damage around you (the caster is not hit) |

Hitting something with the wand never damages it; it only cycles the mode.
The three abilities exist so that Ability Graft has something to graft onto.

### 1. Ability Graft
Look at another player and right click. You (X) graft them (Y).

* If Y casts an ability whose target is **X**, the target becomes **Y**: Y takes the damage/effects.
* If Y casts an ability whose target is **Y** (heal, buff, debuff), the target becomes **X**: X gets the effects.
* Anyone else is untouched. The swap is symmetrical, so the plugin never needs to know whether an ability is
  harmful or beneficial - this keeps the mechanic balanced by construction.
* Area abilities: the same swap is applied to the list of affected creatures. If X is inside the area, Y is hit
  *instead of X* (and only once). If X is outside it, nothing changes. If both are in the list, the list is
  unchanged (the two are already "the same place").
* Y gets no message (a hint of Concealment, as in the lore). Y only sees purple particles around them, and hears
  a sound whenever they cast while grafted. X gets an action bar note when a cast is redirected.
* Ends when: the duration runs out, either player dies or leaves, they change world, or they get too far apart.
* Grafting cannot be grafted. Only abilities that go through the ability system (Arcane Strike, Blessing, Arcane Wave, and any ability added to it) are redirected. The wand's own modes (Time Graft and Height Graft) are never        redirected: in the lore Grafting acts directly on concepts, so grafting a Grafting would make little conceptual sense.

### 2. Time Graft
Right click to save a checkpoint (position, look direction, health, hunger, saturation, air, fire, potion
effects). After the duration you are pulled back to it, as if the time in between had been cut out.

* **Death is skipped**: if a hit would kill you while the checkpoint is active, the graft fires early, cancels the
  death and returns you to the checkpoint (with one second of damage immunity).
* Affects only the caster. Cancelled if you change world, die by other means, or disconnect.

### 3. Height Graft
Look at any creature and right click.

* The target is lifted `loop-height` blocks up its own vertical line and starts falling.
* When it lands (or has fallen `loop-height` blocks) it is sent back up the same line. Direction is preserved.
* It **cannot move horizontally**: it is pinned to its vertical line every tick.
* It **cannot stay in the air**: flight, gliding, Levitation and Slow Falling are suppressed, a minimum downward
  speed is enforced, and every in-air wrap loses `air-pull-back` blocks of altitude so that even a flying target is
  dragged down to the ground.
* Every **ground impact** costs a percentage of the target's maximum health. Vanilla fall damage is replaced by this.
* Ends when: the duration runs out, the target dies, the caster dies/leaves/gets too far, the sky above gets
  blocked, or the target escapes with a teleport (ender pearl, chorus fruit, portal, command).
* The cast fails if there are fewer than `min-clearance` free blocks above the target (no looping through ceilings).

## Design decisions and why

* **Independent cooldowns.** Each mode has its own cooldown, started at the moment of use.
* **Percentage damage in Height Graft.** On Mysterria a seq 2-1 player has around 1200 HP, where vanilla fall damage
  (about 9 per impact) would be meaningless; on a plain 20 HP test server the same number would kill instantly. Damage
  is therefore a percentage of max health (default 1.5% per impact, roughly 10 impacts per cast).
* **Forcing a fall regardless of how flight is implemented.** I do not know how the server implements flight, so
  the plugin does not rely on one trick. Each tick it (1) turns off vanilla flight and gliding, (2) forces a minimum
  downward velocity, (3) if the target still did not descend, moves it down by a short, collision-checked step
  (a tug of war that flight loses as long as the step is larger than its lift per tick). The only thing this cannot beat is another
  plugin that re-teleports the player every tick with higher priority.
* **Time Graft saves from death.** Joining "an instant when you were alive" to "an instant in the future" is the point of
  the ability, so a lethal hit inside the window is cancelled rather than ignored.
* **Time Graft does not restore the inventory.** Restoring it would allow item duplication (drop, trade or store
  items, then rewind) and needs a lot of extra guarding. Blocks are not rewound either (lag and griefing risks).
* **Self only for Time Graft.** Rewinding other players without their consent is too invasive.
* **Modes are classes behind one interface** (`GraftMode`), so adding a fourth mode means adding one class.
* **Left click on the wand cancels block breaking and attacks**, so cycling modes never has side effects.
* **No persistence.** Active effects and cooldowns live in memory and are lost on restart; they are all short.

## Configuration

Everything below is tunable in `config.yml`. Defaults:

| Key | Default |
|-----|---------|
| `ability-graft.cooldown-seconds` / `duration-seconds` | 120 / 20 |
| `ability-graft.max-distance` | 64 |
| `time-graft.cooldown-seconds` / `duration-seconds` | 90 / 10 |
| `height-graft.cooldown-seconds` / `duration-seconds` | 90 / 8 |
| `height-graft.loop-height` / `air-pull-back` / `min-clearance` | 12 / 3 / 6 |
| `height-graft.impact-damage-percent` | 1.5 |
| `abilities.*` (strike damage, blessing duration, wave radius/damage, cast cooldown) | see file |

## Build and install

Requirements: **JDK 25** and **Maven**, targeting **Paper 26.2**.

## Suggested test plan

Ability Graft needs two players (a second account is enough).

1. `/grafting give` to both players. With the wand in hand, left click until the action bar says *Ability Graft*.
2. **Redirect damage:** X right-clicks Y with the wand. Y casts *Arcane Strike* at X -> Y takes the damage, X does not.
3. **Redirect benefit:** Y casts *Blessing* -> X gets Speed/Regeneration, Y does not.
4. **Area:** Y casts *Arcane Wave* with X nearby -> Y is hit, X is not. Move X out of range -> nothing is redirected.
5. **Time Graft:** switch mode, right click, run around or take damage, and watch the pull-back after the duration.
   Take lethal damage during the window to see the early rescue.
6. **Height Graft:** target a player or a mob standing on the ground, then one that is flying (creative flight or elytra).
   Check that they cannot move sideways, keep looping, take impact damage and are dragged down.
7. Check the cooldown message when reusing a mode, and that the graft ends if the caster dies or leaves.

## Known limitations / ideas for later

* Mounts and passengers are not handled specially in Height Graft.
* Area abilities are limited to the demo one; any new ability must return its raw target list to be graftable.

## Project layout

```
src/main/java/dev/grafting/
  GraftingPlugin.java      entry point, mode selection, /grafting give
  GraftMode.java           interface every mode implements
  ItemListener.java        click handling for the wand and ability items
  Items, Cooldowns, Fx, Msg  small helpers
  ability/                 Ability interface, AbilityService, three demo abilities
  mode/                    AbilityGraftMode, TimeGraftMode, HeightGraftMode
```
