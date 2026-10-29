#!/usr/bin/env python3
"""Retrofit client color guards into current native JARs without rebuilding older inputs."""
import hashlib,json,re,subprocess,tempfile
from pathlib import Path
from build import ROOT,REPO,BUILD,EXPORTS,archive,entries,relocate_helper,write

BUILDERS={"com/tterrag/registrate/builders/"+name+".class" for name in ["BlockBuilder","ItemBuilder"]}
HOOK=re.compile(rb'ua/empirecraft/nativecut/j[0-9a-f]+/PolicyHooks')

def compile_tools():
 BUILD.mkdir(exist_ok=True)
 subprocess.run(['java','com.sun.tools.javac.Main',*EXPORTS,'-proc:none','-d',str(BUILD),str(ROOT/'Patch.java')],check=True)
 subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-proc:none','-d',str(BUILD/'shared'),str(ROOT/'PolicyHooks.java')],check=True)

def retrofit(raw):
 data=entries(raw);changed=[]
 for name in list(data):
  if name.endswith('.jar'):
   value,sub=retrofit(data[name])
   if value!=data[name]:data[name]=value;changed.extend(name+'!/'+path for path in sub)
 present=BUILDERS&data.keys()
 if present:
  hooks={match.decode() for name in present for match in HOOK.findall(data[name])}
  if len(hooks)!=1:raise AssertionError(('Expected one existing native Registrate hook',hooks))
  hook=hooks.pop();assert hook+'.class' in data
  with tempfile.TemporaryDirectory(dir=BUILD) as directory:
   directory=Path(directory);source=directory/'builders.jar';output=directory/'patched'
   source.write_bytes(archive({name:data[name] for name in present}))
   subprocess.run(['java',*EXPORTS,'-cp',str(BUILD),'Patch',str(source),str(output),hook,'colors-only'],check=True)
   if output.exists():
    for path in output.rglob('*.class'):
     name=path.relative_to(output).as_posix();data[name]=path.read_bytes();changed.append(name)
    data[hook+'.class']=relocate_helper((BUILD/'shared/TEMPLATE/PolicyHooks.class').read_bytes(),hook.rsplit('/',1)[0].replace('/','.'))
 if not changed:return raw,[]
 return archive(data),sorted(changed)

def main():
 compile_tools();report=json.loads((ROOT/'audit/build.json').read_text());cache={};changes={}
 for side in ['mods','server/mods']:
  for path in sorted((REPO/side).glob('*.jar')):
   raw=path.read_bytes();digest=hashlib.sha256(raw).hexdigest()
   if digest not in cache:cache[digest]=retrofit(raw)
   result,patched=cache[digest]
   if result==raw:continue
   relative=path.relative_to(REPO).as_posix()
   assert relative in report,('Missing native audit entry',relative)
   path.write_bytes(result);sha=hashlib.sha256(result).hexdigest()
   path.with_suffix('.jar.sha256').write_text(sha+'  '+path.name+'\n')
   report[relative].update(sha256=sha,size=len(result),client_colors=patched)
   changes[relative]=patched
 write(ROOT/'audit/build.json',report)
 write(ROOT/'audit/client-colors.json',changes)
 assert changes,'No installed Registrate builders were patched'
 for name in {Path(path).name for path in changes}:
  a=REPO/'mods'/name;b=REPO/'server/mods'/name
  if a.exists() and b.exists():assert a.read_bytes()==b.read_bytes(),name
 print('PASS client color guards, ASM verification and side parity:',len(changes),'JARs')

if __name__=='__main__':main()
