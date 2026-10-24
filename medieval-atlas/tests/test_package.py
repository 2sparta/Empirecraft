#!/usr/bin/env python3
"""Exercise the distributed PowerShell installer in isolated client instances."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import zipfile
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("--pwsh", required=True)
parser.add_argument("--journeymap-jar", type=Path, required=True)
args, remaining = parser.parse_known_args()
POWERSHELL = str(Path(args.pwsh).resolve())
JAR = args.journeymap_jar.resolve()


class PackageTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="atlas test ")
        self.addCleanup(self.temp.cleanup)
        self.work = Path(self.temp.name)
        self.package = self.work / "package"
        with zipfile.ZipFile(ROOT / "dist/EmpireCraft-Medieval-Atlas-1.0.1.zip") as z: z.extractall(self.package)
        self.client = self.work / "Клієнт EmpireCraft"
        (self.client / "mods").mkdir(parents=True)
        shutil.copyfile(JAR, self.client / "mods" / JAR.name)

    def invoke(self, *extra):
        return subprocess.run([POWERSHELL,"-NoProfile","-File",str(self.package / "Apply-Atlas.ps1"),"-MinecraftDirectory",str(self.client),*extra],capture_output=True,text=True)

    def config(self, name, data, world=False):
        base = self.client / ("journeymap/data/mp/EmpireCraft/config" if world else "journeymap/config/6.0")
        base.mkdir(parents=True, exist_ok=True)
        p = base / ("journeymap."+name+".config")
        p.write_text("// JourneyMap header https://journeymap.info\n"+json.dumps(data,ensure_ascii=False),encoding="utf-8")
        return p

    def test_installs_preserves_preferences_and_restores_exact_originals(self):
        core = self.config("core", {"themeName":"Purist","fontScale":2.5,"radar":False,"myURL":"https://example.org/a//b"})
        world = self.config("fullmap", {"preferredMapType":{"value":"day","comment":"keep metadata"},"zoomLevel":1024,"showMobs":False},True)
        waypoints = self.client / "journeymap/data/mp/EmpireCraft/waypoints/WaypointData.dat"
        waypoints.parent.mkdir(parents=True); waypoints.write_bytes(b"existing waypoint binary\x00\xff")
        terrain = self.client / "journeymap/data/mp/EmpireCraft/region/r.0.0.png"
        terrain.parent.mkdir(parents=True); terrain.write_bytes(b"existing mapped tiles\x00\xff")
        options = self.client / "options.txt"
        options.write_bytes(b'fov:0.7\r\nresourcePacks:["vanilla","file/MyPack.zip"]\r\nkey_key.jump:key.keyboard.space\r\n')
        originals = {str(p.relative_to(self.client)):p.read_bytes() for p in [core,world,waypoints,terrain,options]}
        result = self.invoke()
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        current=json.loads(core.read_text()); self.assertEqual(current["themeName"],"EmpireCraft Atlas")
        for key in ["fontScale","radar","myURL"]: self.assertEqual(current[key],json.loads(originals[str(core.relative_to(self.client))].decode().split('\n',1)[1])[key])
        w=json.loads(world.read_text());self.assertEqual(w["preferredMapType"],{"value":"topo","comment":"keep metadata"});self.assertEqual(w["zoomLevel"],1024);self.assertFalse(w["showMobs"])
        self.assertIn('"file/MyPack.zip"',options.read_text());self.assertIn('fov:0.7',options.read_text());self.assertIn('key_key.jump:key.keyboard.space',options.read_text())
        backup = next((self.client/"journeymap/empirecraft-atlas-backups").iterdir())
        restored=self.invoke("-Restore","-BackupDirectory",str(backup));self.assertEqual(restored.returncode,0,restored.stderr)
        for relative, data in originals.items():self.assertEqual((self.client/relative).read_bytes(),data,relative)
        self.assertFalse((self.client/"journeymap/icon/theme/EmpireCraftAtlas/EmpireCraftAtlas.theme2.json").exists())
        self.assertFalse((self.client/"resourcepacks/EmpireCraft-Medieval-Icons-1.0.0.zip").exists())

    def test_fresh_install_and_repeat_do_not_duplicate_resource_pack(self):
        for _ in range(2):
            result=self.invoke();self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        options=(self.client/"options.txt").read_text();self.assertEqual(options.count("file/EmpireCraft-Medieval-Icons-1.0.0.zip"),1)
        cfg=json.loads((self.client/"journeymap/config/6.0/journeymap.minimap.config").read_text());self.assertEqual(cfg["shape"],"Circle")
        self.assertEqual(cfg["preferredMapType"],"topo")

    def test_bad_existing_config_causes_no_partial_install(self):
        bad=self.config("core",{})
        bad.write_text("// broken user configuration\n{this is not valid",encoding="utf-8")
        before=bad.read_bytes();result=self.invoke()
        self.assertNotEqual(result.returncode,0)
        self.assertEqual(bad.read_bytes(),before)
        self.assertFalse((self.client/"journeymap/icon/theme/EmpireCraftAtlas").exists())
        self.assertFalse((self.client/"options.txt").exists())

    def test_server_folder_rejected(self):
        (self.client/"plugins/Towny").mkdir(parents=True)
        result=self.invoke();self.assertNotEqual(result.returncode,0);self.assertIn("server folder",result.stderr)

    @unittest.skipUnless(os.name == "nt", "Requires native Windows cmd.exe")
    def test_windows_launcher_with_empty_path(self):
        env=os.environ.copy();env["PATH"]=""
        command='"{}" /d /s /c ""{}" "{}""'.format(os.environ["COMSPEC"], self.package/"Install-Windows.cmd", self.client)
        result=subprocess.run(command,env=env,input="\n",capture_output=True,text=True,timeout=60)
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        config=self.client/"journeymap/config/6.0/journeymap.core.config"
        self.assertEqual(json.loads(config.read_text(encoding="utf-8-sig"))["themeName"],"EmpireCraft Atlas")

    @unittest.skipUnless(os.name == "nt", "Requires native Windows cmd.exe")
    def test_windows_launcher_preserves_script_error_code(self):
        env=os.environ.copy();env["PATH"]=""
        (self.client/"plugins/Towny").mkdir(parents=True)
        result=subprocess.run('"{}" /d /s /c ""{}" "{}""'.format(os.environ["COMSPEC"],self.package/"Install-Windows.cmd",self.client),env=env,input="\n",capture_output=True,text=True,timeout=60)
        self.assertNotEqual(result.returncode,0,result.stdout+result.stderr)
        self.assertFalse((self.client/"journeymap/icon/theme/EmpireCraftAtlas").exists())

    def test_write_failure_rolls_back_previously_changed_files(self):
        core=self.config("core",{"themeName":"Purist","fontScale":3})
        before=core.read_bytes()
        # Force a failure after the payload and configs were written.
        (self.client/"options.txt").mkdir()
        result=self.invoke();self.assertNotEqual(result.returncode,0)
        self.assertEqual(core.read_bytes(),before)
        self.assertFalse((self.client/"journeymap/icon/theme/EmpireCraftAtlas/EmpireCraftAtlas.theme2.json").exists())
        self.assertTrue((self.client/"options.txt").is_dir())

    def test_bundle_matches_native_asset_names_and_resource_format(self):
        base=self.package/"payload/journeymap/icon/theme/EmpireCraftAtlas"
        with zipfile.ZipFile(JAR) as z:
            expected=[n.rsplit('/',1)[1] for n in z.namelist() if n.startswith("assets/journeymap/theme/flat/icon/") and n.endswith('.png')]
        for filename in expected:self.assertTrue((base/'icon'/filename).exists(),filename)
        for p in base.rglob('*.png'):
            with Image.open(p) as im:self.assertIn(im.mode,('RGBA','LA','P'))
        with zipfile.ZipFile(self.package/"payload/resourcepacks/EmpireCraft-Medieval-Icons-1.0.0.zip") as z:
            self.assertEqual(json.loads(z.read('pack.mcmeta'))['pack']['pack_format'],15)
            icons=[n for n in z.namelist() if n.endswith('.png')];self.assertEqual(len(icons),12)
            for n in icons:
                with Image.open(io.BytesIO(z.read(n))) as im:self.assertEqual(im.size,(16,16))


if __name__=="__main__":unittest.main(argv=[__file__,*remaining],verbosity=2)
