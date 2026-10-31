# STFC In-Game Database Overlay (`stfc.space`)

An Android floating HUD add-on for **Star Trek Fleet Command** (`com.scopely.startrek`) built with **Kotlin** and **Jetpack Compose**.

It attaches a draggable **Starfleet Delta Bubble** over the game that expands into a resizable, LCARS-styled tactical database window powered by **stfc.space** and **Spock's Club** — allowing you to look up ships, officers, hostiles, systems, missions, and research without leaving or reloading STFC.

## Features

- **Floating Starfleet Delta Bubble**:
  - Draggable anywhere along the screen edge.
  - Passes all outside touches directly through to Star Trek Fleet Command (`FLAG_NOT_FOCUSABLE`).
- **Resizable LCARS Tactical Database Window**:
  - One-tap category strip for **Home / Search**, **Ships**, **Officers**, **Hostiles**, **Systems**, **Research**, **Missions**, **Buildings**, and **Spock's Club**.
  - **Auto-Fit Window & Manual Font Scaling (`A-` / `Aa AUTO` / `A+`)**: Automatically scales webpage font size (`75%–115%`) based on the current overlay window width, or lets you manually pick presets (`75%`, `90%`, `100%`, `115%`, `130%`) or slide between `55%–160%`.
  - **Automatic Portrait & Landscape Rotation**: Dynamically adapts window dimensions and remembers separate bounds for Portrait and Landscape orientations.
  - **HUD Opacity Control**: Adjust transparency (`35%–100%`) so you can monitor your ships/armadas in-game while reading stats.
  - **Smart Keyboard Focus Toggle (`⌨ ON/OFF`)**: Enables soft-keyboard input when searching inside the overlay and releases focus so back-button presses return to the game.
  - **Pinned Bookmarks (`★`)**: Pin frequently visited ships, officers, or calculators.
- **Quick Settings Tile**:
  - Includes an Android `TileService` (**STFC DB**) to toggle the overlay directly from the notification shade.

## Building & Installing

```bash
# Build Debug APK
./gradlew assembleDebug

# Install onto a connected Android phone or emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
