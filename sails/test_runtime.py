#!/usr/bin/env python3
"""Prepare checksum-verified launch arguments for the installed Mohist test runtime."""
import argparse,hashlib,json,os
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    server=REPO/'server';spec=json.loads((ROOT/'smoke-runtime.json').read_text())
    for name,digest in spec['sha256'].items():assert hashlib.sha256((server/name).read_bytes()).hexdigest()==digest,name
    boot=[(server/p).resolve() for p in spec['module_path']];legacy=[(server/p).resolve() for p in spec['legacy_classpath']]
    launch=['-XX:ActiveProcessorCount=2','--module-path',os.pathsep.join(map(str,boot)),'--add-modules','ALL-MODULE-PATH',
        '--add-opens','java.base/java.util.jar=cpw.mods.securejarhandler','--add-opens','java.base/java.lang.invoke=cpw.mods.securejarhandler',
        '--add-exports','java.base/sun.security.util=cpw.mods.securejarhandler','--add-exports','jdk.naming.dns/com.sun.jndi.dns=java.naming',
        '-DignoreList='+','.join(p.name for p in boot),'-DlibraryDirectory=libraries','-DlegacyClassPath='+os.pathsep.join(map(str,legacy)),
        'cpw.mods.bootstraplauncher.BootstrapLauncher','--launchTarget','forgeserver','--fml.forgeVersion','47.4.23','--fml.mcVersion','1.20.1',
        '--fml.forgeGroup','net.minecraftforge','--fml.mcpVersion','20230612.114412','nogui']
    args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text('\n'.join('"'+str(a)+'"' for a in launch)+'\n');print('PINNED_MOHIST_TEST_RUNTIME',spec['mohist'])
if __name__=='__main__':main()
