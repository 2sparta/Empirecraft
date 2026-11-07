#!/usr/bin/env python3
"""Apply production Mixins to the installed ChatPlus; optional original-JAR regression reproduction."""
import argparse,os,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
def main():
 p=argparse.ArgumentParser();p.add_argument('--bridge-jar',type=Path,default=REPO/'mods/empirecraft-towny-journeymap-1.10.0.jar');p.add_argument('--expect-original-failure',action='store_true');a=p.parse_args()
 out=ROOT/'build/interactive-mixin';out.mkdir(parents=True,exist_ok=True)
 libs=sorted((REPO/'server/libraries').rglob('*.jar'));libs.sort(key=lambda f:(0 if '/org/ow2/asm/' in str(f) and '/9.7/' in str(f) else 1,str(f)))
 cp=os.pathsep.join(map(str,[a.bridge_jar,REPO/'sails/build/client-named.jar',REPO/'sails/build/runtime-srg.jar',*libs,*sorted((REPO/'mods').glob('*.jar'))]))
 sources=[ROOT/'src/mixinSmoke/java/ua/empirecraft/townymap/InteractiveMixinSmoke.java',*[REPO/'towny-recruits/src/test/java/guardtest'/n for n in ['OfflineMixinService.java','OfflineProperties.java']]]
 java=Path(os.environ.get('JAVA_HOME','/usr'))/'bin/java';args=['--release','17','-proc:none','-cp',cp,'-d',out,*sources]
 (out/'javac.args').write_text('\n'.join('"'+str(s)+'"' for s in args));subprocess.run([java,'com.sun.tools.javac.Main','@'+str(out/'javac.args')],check=True)
 services=out/'META-INF/services';services.mkdir(parents=True,exist_ok=True)
 (services/'org.spongepowered.asm.service.IMixinService').write_text('guardtest.OfflineMixinService\n');(services/'org.spongepowered.asm.service.IGlobalPropertyService').write_text('guardtest.OfflineProperties\n')
 result=subprocess.run([java,'-cp',str(out)+os.pathsep+cp,'ua.empirecraft.townymap.InteractiveMixinSmoke'],capture_output=True,text=True)
 log=result.stdout+result.stderr;(out/('original.log' if a.expect_original_failure else 'fixed.log')).write_text(log)
 if a.expect_original_failure:
  assert result.returncode and 'Invalid descriptor' in log and 'InteractiveTabMixin' in log,log[-4000:];print('INTERACTIVE_ORIGINAL_FAILURE_REPRODUCED')
 else:
  if result.returncode:raise SystemExit(log[-6000:])
  assert 'INTERACTIVE_MIXIN_PASS' in log;print(next(s for s in log.splitlines() if 'INTERACTIVE_MIXIN_PASS' in s))
if __name__=='__main__':main()
