#!/usr/bin/env python3
import os,subprocess
from pathlib import Path
import build
def main():
 java=Path(os.environ.get('JAVA_HOME','/usr'))/'bin/java';out=build.BUILD;out.mkdir(exist_ok=True)
 subprocess.run([java,'com.sun.tools.javac.Main','--release','17','-proc:none','-d',out,build.ROOT/'RaceTest.java'],check=True)
 libs=sorted((build.REPO/'server/libraries').rglob('*.jar'))
 for label,jar in [('old',build.BASE),('fixed',build.REPO/'mods'/build.NAME)]:
  cp=os.pathsep.join(map(str,[out,jar,*libs,*sorted((build.REPO/'mods').glob('*.jar'))]))
  result=subprocess.run([java,'-Xverify:all','-cp',cp,'RaceTest',label],capture_output=True,text=True)
  (out/(label+'.log')).write_text(result.stdout+result.stderr)
  if result.returncode:raise SystemExit((result.stdout+result.stderr)[-5000:])
  print(next(line for line in result.stdout.splitlines() if 'OELIB_' in line))
if __name__=='__main__':main()
