#!/usr/bin/env python3
"""Exercise the existing steel/Create/Knights pipeline and late exclusions in Node and shipped Rhino."""
import argparse,hashlib,json,os,re,subprocess,zipfile
from pathlib import Path
from build import ROOT,REPO,classpath,compiler

def main():
    p=argparse.ArgumentParser();p.add_argument('--mods',type=Path,required=True);p.add_argument('--runtime',type=Path,action='append',required=True);args=p.parse_args()
    recipes={}
    for jar in args.mods.glob('*.jar'):
        if jar.name.startswith(('empirecraft-','rhino-')):continue
        with zipfile.ZipFile(jar) as z:
            for name in z.namelist():
                match=re.fullmatch(r'data/([^/]+)/recipes/(.+)\.json',name)
                if match:
                    try:data=json.loads(z.read(name))
                    except ValueError:continue
                    if isinstance(data,dict) and data.get('type'):recipes[match[1]+':'+match[2]]=data
    # Apply the high-priority KubeJS datapack before recipe-event conversion, as the server does.
    for file in (REPO/'server/kubejs/data').rglob('*.json'):
        rel=file.relative_to(REPO/'server/kubejs/data').as_posix();match=re.fullmatch(r'([^/]+)/recipes/(.+)\.json',rel)
        if match and json.loads(file.read_text()).get('conditions')==[{'type':'forge:false'}]:recipes.pop(match[1]+':'+match[2],None)
    audit=json.loads((ROOT/'audit/content.json').read_text())
    policy=json.loads((ROOT/'audit/policy.json').read_text())
    fixtures=dict(recipes=recipes,premium=audit['premium_recipes'],policy=policy,knights=['magistuarmory','magistuarmoryaddon','antiquelegacy','darkagesarmory','slavicarmory','epic_knights__japanese_armory'])
    output=ROOT/'build/recipe-fixtures.json';output.parent.mkdir(exist_ok=True);output.write_text(json.dumps(fixtures))
    scripts=[ROOT/'tests/recipe-fixture.js',*[REPO/'server/kubejs/server_scripts'/name for name in ['1_steel.js','zz_medieval_create.js','zzzz_empirecraft_smithing.js','zzzzzz_empirecraft_progression.js']],REPO/'kubejs/client_scripts/empirecraft_progression.js',REPO/'kubejs/startup_scripts/empirecraft_progression_tabs.js']
    node='const fs=require("fs"),vm=require("vm");vm.runInThisContext("var fixtures="+fs.readFileSync('+json.dumps(str(output))+',"utf8"));\n'
    node+='\n'.join('vm.runInThisContext(fs.readFileSync('+json.dumps(str(p))+',"utf8"));' for p in scripts)+'\nprocess.stdout.write(verifyProgression()+"\\n");'
    subprocess.run(['node','-e',node],check=True)
    rhino=next(args.mods.glob('rhino-*.jar'))
    assert hashlib.sha256(rhino.read_bytes()).hexdigest()=='fed2211429301bf043864183cab9ab8e92d4cc4dbb9e488ce6c75217c54584a6'
    jars=[rhino,*classpath(args.runtime)];cp=os.pathsep.join(str(p.resolve()) for p in jars)
    classes=ROOT/'build/rhino';classes.mkdir(exist_ok=True)
    subprocess.run([*compiler(),'--release','17','-proc:none','-cp',cp,'-d',str(classes),str(ROOT/'tests/RecipeRhinoTest.java')],check=True)
    home=os.environ.get('JAVA_HOME');java=str(Path(home)/'bin/java') if home else 'java'
    subprocess.run([java,'-ea','-cp',str(classes)+os.pathsep+cp,'RecipeRhinoTest',str(output),*map(str,scripts)],check=True)

if __name__=='__main__':main()
