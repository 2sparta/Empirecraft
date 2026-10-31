#!/usr/bin/env python3
"""Prepare a disposable real-server fixture. Never launches or edits an installed server."""
import argparse, os, pathlib, shlex, shutil, subprocess, tempfile, zipfile
from build import ROOT, REPO


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port',type=int,default=25594)
    options=parser.parse_args()
    if not 1024<=options.port<=65535:parser.error('port must be 1024..65535')
    build=ROOT/'build';build.mkdir(exist_ok=True)
    root=pathlib.Path(tempfile.mkdtemp(prefix='army-smoke-',dir=build))
    (root/'libraries').symlink_to(REPO/'server/libraries',target_is_directory=True)
    for category,files in {
        'mods':[REPO/'server/mods/recruits-1.20.1-1.15.0.jar',ROOT/'dist/empirecraft-recruits-integration-1.0.0.jar'],
        'plugins':[REPO/'server/plugins/Towny-0.101.2.0.jar',REPO/'feudal/dist/empirecraft-feudal-1.11.0.jar',ROOT/'dist/TownyRecruitsBridge-2.0.0.jar'],
    }.items():
        (root/category).mkdir()
        for path in files:shutil.copyfile(path,root/category/path.name)
    (root/'eula.txt').write_text('eula=true\n')
    (root/'mohist-config').mkdir();(root/'mohist-config/mohist.yml').write_text('')
    (root/'server.properties').write_text('level-name=army-smoke\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains","structures":{}}\nonline-mode=false\nserver-ip=127.0.0.1\nserver-port='+str(options.port)+'\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\npvp=true\n')
    libraries=sorted((REPO/'server/libraries').rglob('*.jar'))
    compiler_libraries=sorted(libraries,key=lambda p:(0 if p.name=='forge-1.20.1-47.4.23-server.jar' else 1,str(p)))
    cp=os.pathsep.join(map(str,[REPO/'feudal/dist/empirecraft-feudal-1.11.0.jar',ROOT/'dist/empirecraft-recruits-integration-1.0.0.jar',REPO/'server/plugins/Towny-0.101.2.0.jar',REPO/'server/mods/recruits-1.20.1-1.15.0.jar',*compiler_libraries]))
    compiler=pathlib.Path(os.environ.get('JAVA_HOME','/workspace/toolchains/jdk-17.0.16+8'))/'bin/javac'
    classes=root/'fixture-classes';classes.mkdir()
    subprocess.run([str(compiler) if compiler.exists() else 'javac','--release','17','-proc:none','-encoding','UTF-8','-cp',cp,'-d',str(classes),str(ROOT/'tests/smoke/ArmySmokePlugin.java')],check=True)
    with zipfile.ZipFile(root/'plugins/ArmySmoke.jar','w',zipfile.ZIP_DEFLATED) as archive:
        for path in classes.rglob('*.class'):archive.write(path,path.relative_to(classes))
        archive.writestr('plugin.yml','name: ArmySmoke\nmain: smoke.ArmySmokePlugin\nversion: 1.0\napi-version: "1.20"\ndepend: [EmpirecraftFeudal, TownyRecruitsBridge]\n')
    by_artifact={}
    excluded=('server-','forge-','client-','ForgeAutoRenamingTool-','installertools-','binarypatcher-','jarsplitter-','jline-2.','fmlcore-','javafmllanguage-','lowcodelanguage-','mclanguage-','mohistplugins-')
    for path in libraries:
        if '/org/ow2/asm/' in str(path) and '9.9.1' not in str(path):continue
        if path.name.startswith(excluded):continue
        key=str(path.parent.parent)
        if key not in by_artifact or path.parent.name>by_artifact[key].parent.name:by_artifact[key]=path
    runtime_libs=sorted(by_artifact.values())
    modules=[p for p in runtime_libs if p.name.startswith(('bootstraplauncher-','securejarhandler-','asm-'))]
    rest=[p for p in runtime_libs if p not in modules]
    args=['-Xms512M','-Xmx3G','-XX:ActiveProcessorCount=2','--module-path',os.pathsep.join(map(str,modules)),'--add-modules','ALL-MODULE-PATH','--add-opens','java.base/java.util.jar=cpw.mods.securejarhandler','--add-opens','java.base/java.lang.invoke=cpw.mods.securejarhandler','--add-exports','java.base/sun.security.util=cpw.mods.securejarhandler','--add-exports','jdk.naming.dns/com.sun.jndi.dns=java.naming','-DignoreList='+','.join(p.name for p in modules),'-DlibraryDirectory=libraries','-DlegacyClassPath='+os.pathsep.join(map(str,rest)),'cpw.mods.bootstraplauncher.BootstrapLauncher','--launchTarget','forgeserver','--fml.forgeVersion','47.4.23','--fml.mcVersion','1.20.1','--fml.forgeGroup','net.minecraftforge','--fml.mcpVersion','20230612.114412','nogui']
    (root/'smoke.args').write_text('\n'.join('"'+a.replace('\\','\\\\').replace('"','\\"')+'"' for a in args)+'\n')
    print('Disposable fixture:',root)
    print('Run with Java 21 from that directory:')
    print('cd '+shlex.quote(str(root))+' && /usr/bin/java @smoke.args > runtime.log 2>&1')
    print('Success requires ARMY_SMOKE_PASS in runtime.log. The fixture shuts down automatically.')


if __name__=='__main__':main()
