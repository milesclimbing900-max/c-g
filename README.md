# DeepScan

Client-side Fabric mod: animated deepslate-layer scanner with block highlights, a radar/map and a custom settings app.

## Build
1. JDK 21 + Gradle 9.2+ (Minecraft 1.21.11).
2. In `gradle.properties` set `fabric_api_version` (and loader/loom if they differ) from <https://fabricmc.net/develop/> for your Minecraft version.
3. `./gradlew build` -> jar in `build/libs/`. Or push to GitHub: `.github/workflows/build.yml` builds it and uploads the jar as an artifact.

## Use
| Key | Action |
|---|---|
| `R` | Scan: a radius line sweeps outward and highlights non-natural blocks as it passes |
| `Right Shift` | Open the menu (Scanner / Highlight / Blocks / Colors / Radar / Menu) |

Both keys can be rebound in Controls. Settings are saved to `config/deepscan.json`.

## Highlights
Blocks are highlighted by filling the cube faces you can see (shaded for a 3D look, optional outline + glow layers), drawn over the
world so they show through walls. Fresh finds pulse in the "found" colour for a second, then stay in the "kept" colour (or fade out).
Everything is adjustable: colours (HSV picker, palette, 5 theme presets), opacity, glow, range, animation speed, rainbow tint, radar
size/zoom/corner/sweep trail, menu opacity and more.
