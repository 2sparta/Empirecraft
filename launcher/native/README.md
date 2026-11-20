# Empirecraft in-game home, client addon 0.3.0

Replaces Minecraft's title screen with an original voxel castle hall, a dark navigation panel and a live, rotatable character. This is the menu inside Minecraft after launch; the desktop launcher keeps its existing interface.

* **Enter the realm** connects to `p2.foxter.top:25628`. Settings and Quit retain their vanilla behavior.
* **Change skin** opens Quick Skin's existing skin library/import screen. **Cape** opens its cape library. Return with Escape; the selected appearance refreshes on the home screen.
* Drag the character to turn it, use the `<` / `>` buttons, or press `R` / right-click the character to reset. Buttons support normal keyboard navigation, focus, narration and tooltips.
* Ukrainian and English translations follow Minecraft's interface language. Labels shrink to fit small GUI layouts; the portrait remains clipped to the space beside navigation, with independent waist/full-body and armor/skin toggles.
* A static 1536×1024 texture and one preview model are used in all profiles. No background world, video, particles, status polling or new skin-upload service is introduced.
* The skin/cape editor previews are fitted to their available space, including on small windows. This clamps rendered bounds without changing saved Quick Skin size or position preferences.
* Quick Skin **3.1.1** from the pack is required. Its rendering and appearance services are reused unchanged; its code is not bundled or relicensed. CPM-specific appearances are not added to the new preview; ordinary classic/slim skins and capes use Quick Skin's backend.
* The required connection mixin keeps the single-server restriction. It also intercepts initial Quick Play from existing launcher binaries so the menu appears before a player explicitly chooses to join. Reconnection after that choice remains possible.

Install only `empirecraft-launcher-client-0.3.0.jar` into the launcher client instance's `mods` folder, replacing every older `empirecraft-launcher-client-*.jar`. Do not install this client addon on the server. The private source repository keeps the addon under `launcher/native/dist` so ordinary non-launcher clients are not forced into its single-server menu. Signed launcher distributions export this one current build to `mods/`.

Build with Java 17 and the pack's actual SDK:

```sh
python launcher/native/build.py --reference-repo /path/to/Empirecraft --sdk /path/to/toolchain --jdk /path/to/jdk17
```

The build remaps the shipped Quick Skin jar into a **build-only** API dependency, compiles against Minecraft/Forge, then remaps only our own classes back to SRG. Neither the SDK nor Quick Skin classes are included in the output. `mods.toml` is the source of the addon version. The castle-hall image is an original generated asset, not an image extracted from Crusader Kings III.

Layout verification: `pytest launcher/tests/test_home_layout.py` with Java 17+ on PATH. Full graphical verification: `xvfb-run -a -s '-screen 0 1600x1000x24' python launcher/tools/runtime_smoke.py --boot-pack --menu-capture launcher/menu-captures` (requires the launcher dependencies, Pillow and xdotool). This installs a disposable client and never joins the live server. Captures and logs are saved outside the disposable game directory.

## Server equipment portrait

The default framing shows the character from the waist up. The **Waist up / Full body** and **Armor / Skin only** buttons are independent and persist in `config/empirecraft-home.json`. Rotation, reset, skin and cape controls remain available. The lower body is clipped before rendering controls; the full-body mode fits the complete model.

Every second while connected to the allowed Empirecraft endpoint, the client observes its own four worn armor slots. A changed appearance is cached locally at most once per five seconds, with a final flush at disconnect. Cache files in `config/empirecraft-menu/` are keyed by the account name/UUID and server; input size is limited to 64 KiB and writes are atomic. A naked equipment update replaces the previous outfit as well. Unknown/removed item IDs are skipped when reading a cache. First launch has no invented equipment: join the server once to populate it. The menu labels the outfit as the last synced equipment, rather than a live view of an offline server player.

Only a copied outer armor item is rendered for each slot; the `EmpirecraftArmorLayers` inner list is removed from the copied snapshot. The in-game inventory is never edited or restored. No server addon, endpoint for reading player inventories, credential storage or server polling is added.

The home screen deliberately uses Quick Skin's existing manual skin/cape renderer. Our scoped mixin adds Forge armor models to that same pose and transform, using an armor-only mannequin with no world. The menu creates no ClientLevel, loads no chunks, and retains no player/world to implement this feature. Each armor draw receives an isolated pose stack. The skin and cape editors remain outside the scope and retain their existing behavior.

The native output selection in the signed channel follows the source metadata version, so archived 0.2.0 and current 0.3.0 outputs cannot produce duplicate mod IDs. The desktop launcher application remains compatible; the new controls are inside Minecraft.
