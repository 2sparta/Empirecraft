# Empirecraft in-game home, client addon 0.2.0

Replaces Minecraft's title screen with an original voxel castle hall, a dark navigation panel and a live, rotatable character. This is the menu inside Minecraft after launch; the desktop launcher keeps its existing interface.

* **Enter the realm** connects to `p2.foxter.top:25628`. Settings and Quit retain their vanilla behavior.
* **Change skin** opens Quick Skin's existing skin library/import screen. **Cape** opens its cape library. Return with Escape; the selected appearance refreshes on the home screen.
* Drag the character to turn it, use the `<` / `>` buttons, or press `R` / right-click the character to reset. Buttons support normal keyboard navigation, focus, narration and tooltips.
* Ukrainian and English translations follow Minecraft's interface language. Labels shrink to fit small GUI layouts; the model's full body stays in the space beside navigation.
* A static 1536×1024 texture and one preview model are used in all profiles. No background world, video, particles, status polling or new skin-upload service is introduced.
* The skin/cape editor previews are fitted to their available space, including on small windows. This clamps rendered bounds without changing saved Quick Skin size or position preferences.
* Quick Skin **3.1.1** from the pack is required. Its rendering and appearance services are reused unchanged; its code is not bundled or relicensed. CPM-specific appearances are not added to the new preview; ordinary classic/slim skins and capes use Quick Skin's backend.
* The required connection mixin keeps the single-server restriction. It also intercepts initial Quick Play from existing launcher binaries so the menu appears before a player explicitly chooses to join. Reconnection after that choice remains possible.

Install only `empirecraft-launcher-client-0.2.0.jar` into the launcher client instance's `mods` folder, replacing every older `empirecraft-launcher-client-*.jar`. Do not install this client addon on the server. The private source repository keeps the addon under `launcher/native/dist` so ordinary non-launcher clients are not forced into its single-server menu. Signed launcher distributions export this one current build to `mods/`.

Build with Java 17 and the pack's actual SDK:

```sh
python launcher/native/build.py --reference-repo /path/to/Empirecraft --sdk /path/to/toolchain --jdk /path/to/jdk17
```

The build remaps the shipped Quick Skin jar into a **build-only** API dependency, compiles against Minecraft/Forge, then remaps only our own classes back to SRG. Neither the SDK nor Quick Skin classes are included in the output. `mods.toml` is the source of the addon version. The castle-hall image is an original generated asset, not an image extracted from Crusader Kings III.

Layout verification: `pytest launcher/tests/test_home_layout.py` with Java 17+ on PATH. Full graphical verification: `xvfb-run -a -s '-screen 0 1600x1000x24' python launcher/tools/runtime_smoke.py --boot-pack --menu-capture launcher/menu-captures` (requires the launcher dependencies, Pillow and xdotool). This installs a disposable client and never joins the live server. Captures and logs are saved outside the disposable game directory.
