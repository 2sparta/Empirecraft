from build import *
JUNIT=Path('/workspace/work/foraging-wildlife/junit.jar')
classpath=[B/'named.jar',B/'bridge-named.jar',JUNIT,*cp()]
out=B/'tests';shutil.rmtree(out,ignore_errors=True);out.mkdir()
sources=list((R/'tests').glob('*Test.java'))
# Existing optical history, page assembly, political overlay and UI bounds remain regression coverage.
sources += [REPO/'strategy-map/tests'/name for name in ['StrategyMapTest.java','MapCardSelectionTest.java','StrategyPacketTest.java']]
suites=REPO/'bridge/src/test/java';sources += [suites/'ua/empirecraft/townymap'/(n+'.java') for n in ['ClaimKnowledgeTest','KnowledgeStoreTest','OverlaySyncTest','ClaimGeometryTest','TownyAccessTest','BanditTerritoriesTest']]
sources += list((suites/'org').rglob('*.java'))+list((suites/'com').rglob('*.java'))
mapped=[]
for source in sources:
 if source.name=='StrategyPacketTest.java':
  target=B/'generated-tests'/source.name;target.parent.mkdir(exist_ok=True);text=source.read_text().replace('.m_130070_(','.writeUtf(').replace('.m_130068_(','.writeEnum(').replace('.m_130130_(','.writeVarInt(');target.write_text(text);source=target
 mapped.append(source)
sources=mapped
run(JDK/'javac','-J-Xmx512m','--release','17','-proc:none','-cp',os.pathsep.join(map(str,classpath)),'-d',out,*sources)
result=subprocess.run([str(JDK/'java'),'-Xmx1G','-jar',str(JUNIT),'execute','--class-path',os.pathsep.join(map(str,[out,*classpath])),'--scan-class-path='+str(out),'--details=summary','--reports-dir',str(B/'junit')],capture_output=True,text=True)
print(result.stdout);print(result.stderr);assert result.returncode==0
import xml.etree.ElementTree as ET
reports=[ET.parse(p).getroot() for p in (B/'junit').glob('TEST-*.xml')];assert reports and all(int(r.get('failures','0'))==0 and int(r.get('errors','0'))==0 for r in reports)
(R/'tests.json').write_text(json.dumps(dict(status='passed',tests=sum(int(r.get('tests','0')) for r in reports),artifact_sha256=json.loads((R/'build.json').read_text())['sha256']),indent=2)+'\n');print('SHIP_MAP_TESTS_PASS')
