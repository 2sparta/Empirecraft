#!/usr/bin/env python3
"""Run pure regression tests; optionally package the isolated server fixture."""
from pathlib import Path
import argparse,os,shlex,shutil,subprocess,zipfile
ROOT=Path(__file__).resolve().parent

def run(args):subprocess.run(list(map(str,args)),check=True)
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    mode=parser.add_mutually_exclusive_group()
    mode.add_argument('--prepare-fixture',action='store_true')
    mode.add_argument('--prepare-wind-fixture',action='store_true')
    mode.add_argument('--prepare-arcade-fixture',action='store_true')
    mode.add_argument('--prepare-buoy-fixture',action='store_true')
    mode.add_argument('--prepare-rigging-fixture',action='store_true')
    mode.add_argument('--cursor-only',action='store_true',help='Run the dependency-free section traversal regression')
    args=parser.parse_args()
    if args.prepare_wind_fixture:
        print('The old wind probe has been replaced by the arcade fixture.')
        args.prepare_wind_fixture=False;args.prepare_arcade_fixture=True
    build=ROOT/'build'
    java=Path(os.environ['JAVA_HOME'])/'bin/java' if os.environ.get('JAVA_HOME') else 'java'
    javac=Path(os.environ['JAVA_HOME'])/'bin/javac' if os.environ.get('JAVA_HOME') else shutil.which('javac')
    compiler=[javac] if javac else [java,'com.sun.tools.javac.Main']
    if args.cursor_only:
        classes=build/'cursor-tests';classes.mkdir(parents=True,exist_ok=True)
        run([*compiler,'--release','17','-d',classes,
             ROOT/'src/main/java/com/quintonc/vs_sails/ship/SectionScanCursor.java',ROOT/'tests/SectionScanCursorTest.java'])
        run([java,'-cp',classes,'com.quintonc.vs_sails.ship.SectionScanCursorTest'])
        return
    if not (build/'javac.args').exists():raise SystemExit('Run sails/build.py first')
    flags=shlex.split((build/'javac.args').read_text())
    cp=os.pathsep.join([str(build/'classes'),flags[flags.index('-cp')+1]])
    classes=build/'test-classes';classes.mkdir(exist_ok=True)
    sources=[ROOT/'tests/SailDriveTest.java',ROOT/'tests/SmoothSailingTest.java',ROOT/'tests/SailingMathTest.java',ROOT/'tests/SailForceTest.java',ROOT/'tests/ArcadeSailingTest.java',ROOT/'tests/CoastingMassTest.java',ROOT/'tests/DragCompensatorTest.java',ROOT/'tests/MapHeadingTest.java',ROOT/'tests/ConfigTest.java',ROOT/'tests/SectionScanCursorTest.java',ROOT/'tests/BuoyancySafetyTest.java',ROOT/'tests/ShipIsolationTest.java',ROOT/'tests/WaterSupportTest.java']
    if args.prepare_fixture:sources.append(ROOT/'tests/SailsSmokeMod.java')
    if args.prepare_wind_fixture:sources.append(ROOT/'tests/WindDirectionSmokeMod.java')
    if args.prepare_arcade_fixture:sources.append(ROOT/'tests/ArcadeSmokeMod.java')
    if args.prepare_rigging_fixture:sources.append(ROOT/'tests/RiggingSmokeMod.java')
    if args.prepare_buoy_fixture:sources.append(ROOT/'tests/BuoySmokeMod.java')
    argfile=build/'test-javac.args'
    values=['--release','17','-proc:none','-cp',cp,'-d',classes,*sources]
    argfile.write_text('\n'.join('"'+str(x).replace('\\','\\\\').replace('"','\\"')+'"' for x in values))
    run([*compiler,'@'+str(argfile)])
    for name in ['SailDriveTest','SmoothSailingTest','SailingMathTest','SailForceTest','ArcadeSailingTest','CoastingMassTest','DragCompensatorTest','MapHeadingTest','com.quintonc.vs_sails.config.ConfigTest','com.quintonc.vs_sails.ship.SectionScanCursorTest','BuoyancySafetyTest','ShipIsolationTest','WaterSupportTest']:
        run([java,'-DLog4jContextSelector=org.apache.logging.log4j.core.selector.ClassLoaderContextSelector','-cp',os.pathsep.join([str(classes),cp]),name])
    if not (args.prepare_fixture or args.prepare_wind_fixture or args.prepare_arcade_fixture or args.prepare_buoy_fixture or args.prepare_rigging_fixture):return
    named=build/'fixture-named.jar'
    with zipfile.ZipFile(named,'w',zipfile.ZIP_DEFLATED) as z:
        for p in (classes/'com/empirecraft').rglob('*.class'):z.write(p,str(p.relative_to(classes)))
    asm=[Path(p) for p in flags[flags.index('-cp')+1].split(os.pathsep) if Path(p).name.startswith('asm')]
    remapper=os.pathsep.join(map(str,[build/'tiny-remapper.jar',build/'mapping-io.jar',*asm]))
    srg=build/'fixture-srg.jar'
    run([java,'-cp',remapper,'dev.architectury.tinyremapper.Main',named,srg,build/'named-srg.tiny','named','srg',build/'patch-named.jar',build/'runtime-named.jar','--ignoreConflicts'])
    with zipfile.ZipFile(srg) as z:entries={n:z.read(n) for n in z.namelist()}
    fixture_id='empirecraft_wind_smoketest' if args.prepare_wind_fixture else 'empirecraft_sails_smoketest'
    if args.prepare_arcade_fixture:fixture_id='empirecraft_arcade_smoketest'
    if args.prepare_rigging_fixture:fixture_id='empirecraft_rigging_smoketest'
    if args.prepare_buoy_fixture:fixture_id='empirecraft_buoy_smoketest'
    # Include only the selected fixture, even after compiling the other one earlier.
    main_class='WindDirectionSmokeMod' if args.prepare_wind_fixture else 'SailsSmokeMod'
    if args.prepare_arcade_fixture:main_class='ArcadeSmokeMod'
    if args.prepare_rigging_fixture:main_class='RiggingSmokeMod'
    if args.prepare_buoy_fixture:main_class='BuoySmokeMod'
    entries={n:data for n,data in entries.items() if Path(n).name.split('$')[0].split('.')[0]==main_class}
    entries['META-INF/mods.toml']=('modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="'+fixture_id+'"\nversion="1.0.0"\ndisplayName="Sails test fixture"\n').encode()
    output=build/(fixture_id.replace('_','-')+'.jar')
    with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as z:
        for name,data in sorted(entries.items()):z.writestr(name,data)
    print('Test-only fixture:',output)
    print('Use only on an isolated copy: it builds a ship near spawn, changes test blocks and stops the server.')
if __name__=='__main__':main()
