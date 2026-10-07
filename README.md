# Pride Studio

An in-game world editor for Minecraft 1.12.2, in the style of Axiom. It has an orthographic build camera, a free-flying Editor Mode that sculpts terrain under the mouse, a button panel with WorldEdit- and VoxelSniper-style tools, and 37 hand tools with their own models. All of it was written from scratch for 1.12.2.

> **Status: beta (0.6.0).** Every edit can be undone, but back up your worlds before large edits.

## Features

### Orthographic build camera
- Switch between orthographic and perspective projection, with zoom and adjustable near/far clipping planes.
- **Fixed angle** mode: you keep playing under a still, isometric-style camera, which you can turn and tilt with keys.
- Options to remember the on/off state, set the turn speed and hide your hand.

### Editor Mode (Axiom-style)
- The camera leaves your body and orbits a pivot point like in Blender:
  - Right-drag orbits, Shift + right-drag pans and the mouse wheel zooms.
  - WASD/Space/Shift move the pivot.
- The mouse is free. Brushes paint the land under the cursor at any distance, and the HUD is hidden.
- A menu bar, a **Tools** window (40+ tools), a **Tool Options** window and a status bar.
- Sculpt engine: raise, lower, Gaussian smooth, flatten, weld, melt, rock, roughen, shatter, distort, extrude, flood fill, pour, elevation with falloff, noise and gradient paint, biome brush, tree placement, magic select and a ruler.
- Each brush stroke is a single undo step.

### Pride Studio panel (WorldEdit + VoxelSniper tools as buttons)
Nine tabs:
- **Fill & Water**: a `//fill`-style flood that follows the ground (plus a deep/recursive fill), drain, fix water/lava, remove near/above/below, replace near, snow/thaw, green and extinguish.
- **Selection**: two corners, chunk select, expand/contract/shift, full height and magic select.
- **Region**: set, replace, clear, walls, outline, hollow, overlay, naturalize, smooth, stack, move, line, center, count and set biome.
- **Shapes**: sphere, dome, cylinder, cone, pyramid, torus, cube and more, solid or hollow.
- **Clipboard**: copy, cut, paste, rotate and flip.
- **Brushes** (VoxelSniper-style): ball, disc, cube, cylinder, snipe, paint, overlay, splatter, blend, erode, raise, lower, flatten, fill-in, fill-down, jagged, line, drain, snow and tree.
- **Nature**: forests, flora, butcher mobs and remove dropped items.
- **History**: undo/redo (50 steps per player). Machine and chest contents are restored too.
- **Settings**.

### More tools
- **Live preview**: a tool can be armed instead of run. A see-through ghost shows every block it would place. Press Enter to build or Backspace to cancel.
- **Block patterns**: `stone`, `minecraft:wool:3`, `stone,dirt` (even mix) or `70%stone,30%cobblestone` (weighted), with optional masks.
- **Builder menu** (hold Left Alt): saved hotbars (rows of 9, 8 rows per page), builder switches, flight speed and an Edit Block screen for block properties.
- **37 hand tools**, each with its own Blockbench model, in the *Pride Studio* creative tab. They include select/magic/chunk wands, copy tool, cut shears, paste stamp, rotate tool, flip mirror, move arrows, stack tool, shape tools, fill and flood buckets, replace/walls/hollow tools, drain sponge, naturalizer, raise trowel, smoothing iron, paint brush, erode chisel, rock hammer, tree planter, ruler, undo wand, toolboxes and a guide book.
  - Right-click does the main action and left-click does the second one (each tooltip shows both).
  - Sneak + right-click opens the panel.

## Controls

All keys can be rebound under *Controls → Pride Studio*.

| Key | Action |
|---|---|
| `\` | Open the Pride Studio panel |
| `` ` `` (grave) | Editor Mode |
| Left Alt (hold) | Builder menu |
| `[` / `]` | Corner 1 / corner 2 at the crosshair |
| Ctrl+Z / Ctrl+Y | Undo / redo |
| Ctrl+C / Ctrl+V | Copy selection / paste |
| `=` / `-` | Brush bigger / smaller |
| Enter / Backspace | Build / cancel the live preview |
| Numpad 4 | Orthographic camera on/off |
| Numpad + / − | Camera zoom |
| Numpad * | Fix the camera angle |
| Numpad 7 / 9, 8 / 2 | Turn / tilt the fixed camera |
| Numpad / | Camera settings |

### Commands
- `/pridestudio` opens the panel. `/pridestudio camera` opens camera settings (client command).
- `/ps <tool> [key=value ...]` runs any panel tool from chat. Examples:
  - `/ps fill block=water radius=20 depth=6`
  - `/ps shape shape=sphere radius=8 hollow=true`
  - `/ps undo`
  - `/ps panel`, `/ps menu`, `/ps editor`

### Permissions
Editing requires **creative mode or operator rights** (permission level 2). This is checked on the server for every operation.

## Configuration

These client-side files are written by the in-game settings screens:
- `config/pridestudio-camera.json`: camera settings
- `config/pridestudio-editor.json`: panel state, brush/tool options, reach, preview and menu behaviour
- `config/pridestudio-hotbars.dat`: saved builder hotbars

Menu colours follow optional `pride.theme.*` JVM system properties, so a modpack can theme every Pride mod at once.

## Requirements

- Minecraft **1.12.2**
- **Forge** 14.23.5.2860+ or **Cleanroom**
- **[MixinBooter](https://github.com/CleanroomMC/MixinBooter)** (Cleanroom already includes it)
- Install it on **both client and server** to use the editing tools. The orthographic camera only needs the client.

## Building from source

```sh
./gradlew build
```

The jar is written to `build/libs/`. The `blockbench/` folder holds the source `.bbmodel` files for the hand tools.

## License

[MIT](LICENSE.txt).

- The camera feature set follows **[OrthoCamera](https://github.com/DimasKama/OrthoCamera)** by DimasKama (MIT), reimplemented for 1.12.2.
- The editor design is inspired by **Axiom**, **VoxelSniper** and **[WorldEdit](https://github.com/EngineHub/WorldEdit)**. No code from those projects is used.

## Credits

Made with [Claude Code](https://claude.com/claude-code) and [Blockbench](https://www.blockbench.net).

- **[OrthoCamera](https://github.com/DimasKama/OrthoCamera)** by DimasKama (MIT): the orthographic camera feature set.
- **Axiom**, **VoxelSniper** and **WorldEdit**: design inspiration.
- **[MixinBooter](https://github.com/CleanroomMC/MixinBooter)** and **[Mixin](https://github.com/SpongePowered/Mixin)**: mixin loading.


## Compile-only jars

The build compiles against these jars in `libs/` (other authors' mods / APIs). They are not included in this repo — get them from their official pages and drop them in `libs/` before building:

- `mixinbooter-api.jar`
- `sponge-mixin.jar`
