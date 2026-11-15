"""Collect complete installed distribution notices into packaged resources."""
from importlib.metadata import distributions
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]
def collect():
    target = ROOT / "resources/licenses"
    target.mkdir(parents=True, exist_ok=True)
    for distribution in distributions():
        name = distribution.metadata.get("Name", "unknown")
        for file in distribution.files or ():
            if any(part.lower().startswith(("license", "copying", "notice")) for part in file.parts):
                source = Path(distribution.locate_file(file))
                if source.is_file():
                    dest = target / name / file.name
                    dest.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copyfile(source, dest)
    for name in ("python3.13", "libtk8.6", "tcl8.6"):
        source = Path("/usr/share/doc") / name / "copyright"
        if source.is_file():
            shutil.copyfile(source, target / (name + "-copyright.txt"))
if __name__ == "__main__": collect()
