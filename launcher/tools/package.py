import argparse
import hashlib
from pathlib import Path
import platform
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--build", action="store_true")
    a = parser.parse_args()
    if a.build:
        subprocess.run([sys.executable, "-m", "PyInstaller", "--noconfirm", "--clean", "--onedir", "--windowed", "--name", "Empirecraft", "--add-data", "resources:resources", "--collect-all", "minecraft_launcher_lib", "--collect-all", "psutil", "run.py"], cwd=ROOT, check=True)
    folder = ROOT / "dist/Empirecraft"
    if not folder.is_dir():
        raise SystemExit("Build first with --build")
    system = "Windows-x64" if sys.platform == "win32" else "Linux-x64"
    target = ROOT / f"dist/Empirecraft-Launcher-0.1.0-{system}.zip"
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as z:
        for p in sorted(folder.rglob("*")):
            if p.is_file():
                z.write(p, "Empirecraft/" + p.relative_to(folder).as_posix())
        z.write(ROOT / "README.md", "Empirecraft/README.md")
        z.write(ROOT / "LICENSE", "Empirecraft/LICENSE")
    target.with_suffix(".zip.sha256").write_text(hashlib.sha256(target.read_bytes()).hexdigest() + "  " + target.name + "\n")
    print(target)
if __name__ == "__main__":
    main()
