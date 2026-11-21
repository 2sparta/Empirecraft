from build import R,REPO,SDK,JDK,b,libs,run
import os
old=REPO/'ship-physics/water-fix/build';cp=[b/'named.jar',SDK/'runtime-named.jar',SDK/'client-named.jar',old/'valkyrienskies-120-2.4.11-empirecraft.1-named.jar',*sorted((old/'nested').glob('*.jar')),*libs]
out=b/'frames-tests';out.mkdir(exist_ok=True);run(JDK/'javac','-J-Xmx512m','--release','17','-proc:none','-cp',os.pathsep.join(map(str,cp)),'-d',out,R/'tests/FramesTest.java');run(JDK/'java','-Xmx512m','-ea','-cp',os.pathsep.join(map(str,[out,*cp])),'FramesTest')
