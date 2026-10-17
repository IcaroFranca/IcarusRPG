# Menu preview test

Checks every IcarusRPG menu against the IcarusTexture pack without opening the game.

1. **Capture** - `MenuDump.java` is a test plugin (not part of the IcarusRPG jar). On a throwaway
   Paper 26.2 server with IcarusRPG installed, it joins a fake player, opens `/skills` and clicks
   every button two levels deep, plus the spawner menu, and writes each distinct screen (exact
   title string, size, every item and its `item_model`) to `plugins/MenuDump/menus.jsonl`.
   Compile it against the server's `versions/26.2/paper-26.2.jar` plus every jar in `libraries/`.
2. **Render + check** - in the IcarusTexture repo:

   ```
   python tools/menu_check.py menus.jsonl <client-assets>/assets/minecraft <out-dir>
   ```

   `<client-assets>` is the 26.2 client jar's textures, extracted locally (never committed). It
   draws each menu the way the client does and fails on: visible filler panes, buttons without a
   drawn slot, vanilla chest background showing through, unreadable title text or a title that
   doesn't start at x=8, misaligned player-inventory slots, and private-use characters the pack
   doesn't define. `<out-dir>/_all.png` shows every menu side by side.
