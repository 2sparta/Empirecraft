#!/usr/bin/env python3
"""Load localization and preferences from the actual release JARs, including the embedded H2 driver."""
import argparse,hashlib,json,os,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];REPO=ROOT.parent

def main():
 parser=argparse.ArgumentParser();parser.add_argument('--java21',default='java');a=parser.parse_args()
 work=ROOT/'build';plugin=ROOT/'dist/empirecraft-feudal-1.19.3.jar';bridge=ROOT/'dist/empirecraft-towny-journeymap-1.11.1.jar';libs=sorted((REPO/'server/libraries').rglob('*.jar'))
 cp=os.pathsep.join(map(str,[plugin,work/'tests',work/'bukkit-api.jar',*sorted((REPO/'server/plugins').glob('*.jar')),*libs]));markers=[]
 for suite in ['CommandLanguageTests','LocalizationTests']:
  r=subprocess.run([a.java21,'-ea','-cp',cp,'ua.empirecraft.feudal.'+suite],capture_output=True,text=True);(work/'logs'/('packaged-'+suite+'.log')).write_text(r.stdout+r.stderr)
  if r.returncode:print((r.stdout+r.stderr)[-5000:]);raise SystemExit(suite+' failed')
  markers.append(r.stdout.splitlines()[-1]);print('PACKAGED_'+suite+'_PASS',flush=True)
 r=subprocess.run([a.java21,'-ea','-cp',os.pathsep.join(map(str,[bridge,work/'tests',*libs])),'MenuClientTests',str(bridge)],capture_output=True,text=True);(work/'logs/packaged-client.log').write_text(r.stdout+r.stderr)
 if r.returncode:print((r.stdout+r.stderr)[-5000:]);raise SystemExit('packaged client failed')
 markers+=r.stdout.splitlines();print(r.stdout)
 report=dict(status='passed',markers=markers,sha256={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in [plugin,bridge]})
 (ROOT/'audit/packaged.json').write_text(json.dumps(report,indent=2)+'\n')
if __name__=='__main__':main()
