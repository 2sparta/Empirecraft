"""Exercise the real Minecraft window on a disposable X11 display, never a server."""
import json
from pathlib import Path
import subprocess
import time
from PIL import ImageGrab


def capture_menu(destination: Path, game: Path):
    destination.mkdir(parents=True, exist_ok=True)
    def xdo(*args):
        return subprocess.check_output(['xdotool', *map(str, args)], text=True).strip()
    windows = xdo('search', '--name', '^Minecraft').splitlines()
    window = windows[-1]
    xdo('windowfocus', window)
    xdo('windowmove', '--sync', window, 0, 0)
    report = []
    def screen_marker(expected):
        deadline = time.monotonic() + 20
        while time.monotonic() < deadline:
            text = (game/'logs/latest.log').read_text(errors='replace')
            markers = [line for line in text.splitlines() if 'Empirecraft smoke screen:' in line]
            if markers and expected in markers[-1]: return
            time.sleep(.25)
        raise AssertionError('Expected screen '+expected+'; last marker: '+str(markers[-1:]))
    def shot(name):
        time.sleep(3)
        geometry = dict(line.split('=', 1) for line in xdo('getwindowgeometry', '--shell', window).splitlines())
        x,y,w,h = [int(geometry[key]) for key in ('X','Y','WIDTH','HEIGHT')]
        ImageGrab.grab().crop((x,y,x+w,y+h)).save(destination/(name+'.png'))
        report.append({'capture':name, 'x':x, 'y':y, 'width':w, 'height':h})
    def click(x,y):
        xdo('mousemove', '--window', window, x, y)
        xdo('click', '1')
    screen_marker('launcherclient.EmpireHome')
    shot('home-854x480')
    # GUI scale 2: 427x240 logical units. Skin, cape and settings must return home.
    click(482,432)
    screen_marker('PlayerSkinMenuScreen')
    shot('skin-editor')
    xdo('key', 'Escape')
    screen_marker('launcherclient.EmpireHome')
    click(690,432)
    screen_marker('PlayerCapeMenuScreen')
    shot('cape-editor')
    xdo('key', 'Escape')
    screen_marker('launcherclient.EmpireHome')
    click(150,294)
    screen_marker('OptionsScreen')
    shot('options')
    xdo('key', 'Escape')
    screen_marker('launcherclient.EmpireHome')
    # Rotate the actual rendered model and reset it without leaving the screen.
    xdo('mousemove', '--window', window, 586,210)
    xdo('mousedown','1')
    xdo('mousemove', '--window', window, 666,210)
    xdo('mouseup','1')
    shot('home-rotated')
    xdo('key','r')
    for w,h in [(640,480),(1280,720),(1440,600)]:
        xdo('windowsize', '--sync', window, w,h)
        xdo('windowmove', '--sync', window, 0,0)
        shot(f'home-{w}x{h}')
    (destination/'menu-flow.json').write_text(json.dumps(report, indent=2)+'\n')
    print('REAL_MENU_FLOW_PASS: home, skin, cape, settings, rotation, 4 window sizes',flush=True)
