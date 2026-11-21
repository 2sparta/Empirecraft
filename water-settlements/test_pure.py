from pathlib import Path
import os,subprocess
R=Path(__file__).resolve().parent;REPO=R.parent;JDK=Path('/workspace/work/tfc/jdk/bin');out=R/'build/pure';out.mkdir(parents=True,exist_ok=True)
gson=next(Path('/workspace/Empirecraft/server/libraries/com/google/code/gson').rglob('*.jar'))
sources=[REPO/'towny-recruits/src/main/java/ua/townyrecruits/RecruitmentState.java',R/'plugin/ua/empirecraft/frontier/SpawnMath.java',REPO/'sails/src/main/java/com/quintonc/vs_sails/ship/FloodModel.java',REPO/'sails/src/main/java/com/quintonc/vs_sails/ship/FloodGeometry.java',REPO/'sails/tests/FloodModelTest.java',*sorted((R/'tests').glob('*.java'))]
subprocess.run(list(map(str,[JDK/'javac','--release','17','-proc:none','-cp',gson,'-d',out,*sources])),check=True)
for suite in ['ua.townyrecruits.PopulationTest','SpawnMathTest','FloodModelTest','WaterRegression']:subprocess.run([str(JDK/'java'),'-ea','-cp',str(out)+os.pathsep+str(gson),suite],check=True)
