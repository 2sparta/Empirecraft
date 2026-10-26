#!/usr/bin/env python3
import shutil, subprocess
from pathlib import Path
root=Path(__file__).resolve().parent
out=root/'build/unit';out.mkdir(parents=True,exist_ok=True)
compiler=[shutil.which('javac')] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
subprocess.run([*compiler,'--release','17','-d',str(out),str(root/'src/main/java/ua/empirecraft/leaves/SeasonCurve.java'),str(root/'tests/SeasonCurveTest.java')],check=True)
subprocess.run(['java','-cp',str(out),'SeasonCurveTest'],check=True)
