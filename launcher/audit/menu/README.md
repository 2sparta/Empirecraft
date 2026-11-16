# In-game menu verification

Screenshots in this directory are captured from the real Minecraft 1.20.1 / Forge 47.4.23 client, not browser mockups. The fixture installs the signed public Potato pack, replaces only the launcher client addon with 0.2.0, then opens the menu without joining a server.

The fixture exercises skin/cape editors, return to home, settings, model rotation/reset, and window resizing. Separate Java geometry checks cover eight GUI sizes, including 320×240 and ultrawide. This verifies the menu and its dependency integration, not current server login, gameplay, shaderpacks, real Windows GPU drivers or laptop FPS.
