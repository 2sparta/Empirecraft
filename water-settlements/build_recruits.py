from pathlib import Path
import hashlib,os,subprocess,zipfile,shutil,json
R=Path(__file__).resolve().parent;REPO=R.parent;REF=Path('/workspace/Empirecraft');b=R/'build/recruits';b.mkdir(parents=True,exist_ok=True)
from inputs import materialize
base=materialize('mods/TownyRecruits.jar','f2c2729d7ec0495cf707696bc8922a096e0a09998802d7a98a180be1a39ff30e',b/'base.jar');pin=hashlib.sha256(base.read_bytes()).hexdigest();assert pin=='f2c2729d7ec0495cf707696bc8922a096e0a09998802d7a98a180be1a39ff30e';libs=sorted((REF/'server/libraries').rglob('*.jar'))
libs.sort(key=lambda p:0 if p.name.startswith('forge-1.20.1-') and p.name.endswith('-server.jar') else 1)
cp=[REPO/'town-menu-art/build/refresh/bukkit-api.jar',base,*libs,*sorted((REPO/'mods').glob('*.jar'))]
names=['RecruitmentState','RecruitmentConfig','Recruitment','RecruitmentCommands','TownyRecruitMenu','Militia','MoraleSystem','RegimentExchange','VillageApi'];src=REPO/'towny-recruits/src/main/java/ua/townyrecruits';classes=b/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
subprocess.run(list(map(str,['java','com.sun.tools.javac.Main','-source','17','-target','17','--system','/workspace/work/tfc/jdk','-proc:none','-cp',os.pathsep.join(map(str,cp)),'-d',classes,*[src/(n+'.java') for n in names]])),check=True)
with zipfile.ZipFile(base) as z:files={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
patch={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')};files.update(patch)
files['META-INF/mods.toml']=files['META-INF/mods.toml'].replace(b'version="2.0.3"',b'version="2.0.4"')
from build_water import jar
jar(R/'dist/TownyRecruits.jar',files);(R/'recruits-build.json').write_text(json.dumps({'base_sha256':pin,'overlaid_classes':sorted(patch)},indent=2)+'\n')
print('POPULATION_BUILD_PASS')
