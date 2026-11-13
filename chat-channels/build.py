#!/usr/bin/env python3
"""Patch the exact installed ChatPlus and build its Towny/Mohist server counterpart."""
import argparse, hashlib, json, os, shutil, subprocess, zipfile
from pathlib import Path
import yaml
from assets import generate

HERE = Path(__file__).resolve().parent
REPO = HERE.parent
ORIGINAL = 'chatplus-forge-2.8.1 (5).jar'
NAME = 'chatplus-forge-2.8.1-empirecraft.3.jar'
PLUGIN = 'empirecraft-chat-1.1.1.jar'
ORIGINAL_SHA = 'f30736ebfbeb7f422401e780458ce23b0a9f6e97edef63151fa373becf90ea29'

def compiler():
    return [shutil.which('javac')] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
def archive(path,entries):
    path.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name, data in sorted(entries.items()):
            info=zipfile.ZipInfo(name,(2026,10,25,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16
            z.writestr(info,data)
    path.with_suffix(path.suffix+'.sha256').write_text(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+path.name+'\n')
def compile_sources(label,sources,classpath):
    output=HERE/'build'/label
    if output.exists():shutil.rmtree(output)
    output.mkdir(parents=True)
    args=['--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,classpath)),'-d',str(output),*map(str,sources)]
    argfile=HERE/'build'/(label+'.args')
    argfile.write_text('\n'.join('"'+x.replace('\\','\\\\').replace('"','\\"')+'"' for x in args)+'\n')
    subprocess.run([*compiler(),'@'+str(argfile)],check=True)
    return output
def class_entries(path):return{p.relative_to(path).as_posix():p.read_bytes() for p in path.rglob('*.class')}
def client_api(libs):
    sdk=REPO/'sails/build';target=HERE/'build/client-api-srg.jar'
    required=[sdk/'client-named.jar',sdk/'runtime-named.jar',sdk/'named-srg.tiny',sdk/'tiny-remapper.jar',sdk/'mapping-io.jar']
    if not all(p.exists() for p in required):raise ValueError('Prepare the pinned Minecraft client API with python sails/build.py --sails-only')
    stamp=HERE/'build/client-api-inputs.json';identity={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in required}
    if not target.exists() or not stamp.exists() or json.loads(stamp.read_text())!=identity:
        asm=[p for p in libs if '/org/ow2/asm/' in str(p)]
        cp=os.pathsep.join(map(str,[sdk/'tiny-remapper.jar',sdk/'mapping-io.jar',*asm]))
        subprocess.run(['java','-Xmx1G','-cp',cp,'dev.architectury.tinyremapper.Main',str(sdk/'client-named.jar'),str(target),str(sdk/'named-srg.tiny'),'named','srg',str(sdk/'runtime-named.jar'),'--ignoreConflicts'],check=True)
        stamp.write_text(json.dumps(identity,indent=2)+'\n')
    return target
def main():
    (HERE/'build/test-results.txt').unlink(missing_ok=True)
    parser=argparse.ArgumentParser();parser.add_argument('--original',type=Path);parser.add_argument('--feudal-jar',type=Path);parser.add_argument('--client-only',action='store_true');args=parser.parse_args()
    original=args.original or HERE/'upstream'/ORIGINAL
    if not original.exists(): original=REPO/'mods'/ORIGINAL
    if hashlib.sha256(original.read_bytes()).hexdigest()!=ORIGINAL_SHA:raise ValueError('Unexpected ChatPlus binary; refusing to apply a different patch')
    spigot=HERE/'lib/spigot-api.jar'
    if not spigot.exists():
        installed=REPO/'server/libraries/net/minecraftforge/forge/1.20.1-47.4.23/forge-1.20.1-47.4.23-server.jar'
        if installed.exists():
            if hashlib.sha256(installed.read_bytes()).hexdigest()!='28292cfb022ccd8f2235185b529b51a6c6fad13c6dc9edb0cbb4c8a4effe42b1':raise ValueError('Pinned Mohist API checksum')
            spigot=installed
        else:
            spigot.parent.mkdir(exist_ok=True);shutil.copyfile(REPO/'feudal/lib/spigot-api.jar',spigot)
    elif hashlib.sha256(spigot.read_bytes()).hexdigest()!='465c8fce2efa60da5517c312dcc57c957908c608f23f92b14216caecc0e85713':raise ValueError('Spigot API checksum')
    libs=sorted((REPO/'server/libraries').rglob('*.jar'))
    asm=[p for p in libs if p.name in ['asm-9.7.jar','asm-tree-9.7.jar','asm-analysis-9.7.jar']]
    patch=compile_sources('patch',sorted((HERE/'patch').rglob('*.java')),asm)
    patched=HERE/'build/patched';shutil.rmtree(patched,ignore_errors=True);patched.mkdir(exist_ok=True)
    subprocess.run(['java','-cp',os.pathsep.join(map(str,[patch,*asm])),'PatchChatPlus',str(original),str(patched)],check=True)
    shared=sorted((HERE/'src/shared/java').rglob('*.java'))
    client=compile_sources('client',shared+sorted((HERE/'src/client/java').rglob('*.java')),[original,client_api(libs),REPO/'mods/kotlinforforge-4.12.0-all (1).jar',REPO/'mods/cloth-config-11.1.136-forge.jar',*libs])
    art,cosmetics,art_report=generate()
    with zipfile.ZipFile(original) as z:entries={n:z.read(n) for n in z.namelist() if not n.endswith('/') and not n.endswith(('.SF','.DSA','.RSA'))}
    entries.update(class_entries(patched));entries.update(class_entries(client));entries.update(art)
    entries['META-INF/mods.toml']=entries['META-INF/mods.toml'].replace(b'version = "2.8.1"',b'version = "2.8.1-empirecraft.3"').replace(b'displayName = "ChatPlus"',b'displayName = "ChatPlus - Empirecraft"')
    entries['pack.mcmeta']=json.dumps(dict(pack=dict(pack_format=15,description='ChatPlus - Empirecraft channels and cosmetic title images'))).encode()
    for p in (HERE/'src/client/resources').rglob('*'):
        if p.is_file():
            key=p.relative_to(HERE/'src/client/resources').as_posix()
            if key in entries and key.endswith('.json'):
                merged=json.loads(entries[key]);merged.update(json.loads(p.read_bytes()));entries[key]=json.dumps(merged,ensure_ascii=False,indent=2).encode()
            else:entries[key]=p.read_bytes()
    entries['LICENSE-Empirecraft-ChatPlus-patches.txt']=(HERE/'LICENSE-ChatPlus.txt').read_bytes()
    archive(REPO/'mods'/NAME,entries)
    for old in [REPO/'mods/chatplus-forge-2.8.1-empirecraft.1.jar',REPO/'mods/chatplus-forge-2.8.1-empirecraft.2.jar']:
        old.unlink(missing_ok=True);old.with_suffix('.jar.sha256').unlink(missing_ok=True)
    if args.client_only:
        report=dict(chatplus_sha256=hashlib.sha256((REPO/'mods'/NAME).read_bytes()).hexdigest(),plugin_sha256=hashlib.sha256((REPO/'server/plugins'/PLUGIN).read_bytes()).hexdigest(),cosmetic_images=art_report,local_radius=50,channels=['global','local','system','town','kingdom'],graphical_client_tested=False,client_only_update=True)
        (HERE/'build/report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
        return
    server=compile_sources('server',shared+sorted((HERE/'src/server/java').rglob('*.java')),[spigot,REPO/'server/plugins/Towny-0.101.2.0.jar',REPO/'server/plugins/LuckPerms-Bukkit-5.5.36.jar',args.feudal_jar or next((REPO/'server/plugins').glob('empirecraft-feudal-*.jar')),*libs])
    entries=class_entries(server)
    entries['cosmetics.yml']=cosmetics
    entries['plugin.yml']=yaml.safe_dump(dict(name='EmpirecraftChat',version='1.1.1',main='ua.empirecraft.chat.ChatPlugin',**{'api-version':'1.20'},depend=['Towny','EmpirecraftFeudal','LuckPerms','EmpirecraftTitles'],commands=dict(ecchat=dict(description='Канали чату Empirecraft',usage='/ecchat global|local|town|kingdom [повідомлення]',aliases=['gchat','channels']))),allow_unicode=True,sort_keys=False).encode()
    archive(REPO/'server/plugins'/PLUGIN,entries)
    old=REPO/'server/plugins/empirecraft-chat-1.0.0.jar';old.unlink(missing_ok=True);old.with_suffix('.jar.sha256').unlink(missing_ok=True)
    report=dict(chatplus_sha256=hashlib.sha256((REPO/'mods'/NAME).read_bytes()).hexdigest(),plugin_sha256=hashlib.sha256((REPO/'server/plugins'/PLUGIN).read_bytes()).hexdigest(),cosmetic_images=art_report,local_radius=50,channels=['global','local','system','town','kingdom'],graphical_client_tested=False)
    (HERE/'build/report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print('PASS built ChatPlus and server plugin; cosmetic images:',len(art_report))
if __name__=='__main__':main()
