"""Real Tk widget interaction on a display / xvfb-run; no mocked Tk APIs."""
import os
from pathlib import Path
import sys
import tempfile
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
os.environ["XDG_DATA_HOME"] = tempfile.mkdtemp(prefix="empirecraft-gui-")
os.environ["LOCALAPPDATA"] = os.environ["XDG_DATA_HOME"]
from empirecraft_launcher.app import Launcher
app = Launcher()
app.update()
for key in ("high", "balanced", "potato"):
    app.choose(key)
    app.update()
    assert app.selected.get() == key
    assert app.cards[key][1].cget("text") == "● Обрано"
assert app.distance.get() == 5
assert app.reduced.get()
assert app.play.winfo_rooty() + app.play.winfo_height() <= app.winfo_rooty() + app.winfo_height()
app.geometry("980x740")
app.update()
assert app.play.winfo_rooty() + app.play.winfo_height() <= app.winfo_rooty() + app.winfo_height()
app.geometry("1120x780")
app.update()
target = Path(sys.argv[1]) if len(sys.argv) > 1 else None
if target:
    from PIL import ImageGrab
    image = ImageGrab.grab(bbox=(app.winfo_rootx(), app.winfo_rooty(), app.winfo_rootx()+app.winfo_width(), app.winfo_rooty()+app.winfo_height()))
    image.save(target)
app.destroy()
print("GUI_SMOKE_PASS")
