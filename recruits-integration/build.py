#!/usr/bin/env python3
"""Offline SRG build against the pinned Mohist/Recruits runtime; no world or installed JAR is changed."""
import hashlib,json,os,pathlib,shutil,struct,subprocess,zipfile
ROOT=pathlib.Path(__file__).resolve().parent
REPO=ROOT.parent
RECRUITS=REPO/'server/mods/recruits-1.20.1-1.15.0.jar'
SHA='5c0c9252b332babf7e7cf5fc9a5a6f08018df5b9f86827853db2cb3f39b127ce'
def methods(data):
    # Minimal class-file reader, used only to verify injection method descriptors.
    import io
    f=io.BytesIO(data);assert f.read(4)==b'\xca\xfe\xba\xbe';f.read(4)
    u1=lambda:struct.unpack('>B',f.read(1))[0]
    u2=lambda:struct.unpack('>H',f.read(2))[0]
    u4=lambda:struct.unpack('>I',f.read(4))[0]
    cp={};count=u2();i=1
    while i<count:
        tag=u1()
        if tag==1:cp[i]=f.read(u2()).decode('utf8',errors='replace')
        elif tag in (3,4):f.read(4)
        elif tag in (5,6):f.read(8);i+=1
        elif tag in (7,8,16,19,20):f.read(2)
        elif tag in (9,10,11,12,17,18):f.read(4)
        elif tag==15:f.read(3)
        else:raise ValueError(tag)
        i+=1
    f.read(6);f.read(u2()*2)
    def attributes():
        for _ in range(u2()):f.read(2);f.read(u4())
    for _ in range(u2()):f.read(6);attributes()
    out=[]
    for _ in range(u2()):
        flags=u2();name=u2();desc=u2();out.append((cp[name],cp[desc]));attributes()
    return out

def archive(path,contents):
    path.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(path,'w') as z:
        for n,data in sorted(contents.items()):
            info=zipfile.ZipInfo(n,(2026,10,30,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o644<<16;z.writestr(info,data)
    digest=hashlib.sha256(path.read_bytes()).hexdigest();path.with_suffix(path.suffix+'.sha256').write_text(digest+'  '+path.name+'\n');print(path.name,digest)
def compile(java,cp,output,sources):
    output.mkdir(parents=True,exist_ok=True)
    subprocess.run([java,'--release','17','-proc:none','-encoding','UTF-8','-cp',cp,'-d',str(output),*map(str,sources)],check=True)
def main():
    assert hashlib.sha256(RECRUITS.read_bytes()).hexdigest()==SHA,'Wrong Recruits binary'
    home=pathlib.Path(os.environ.get('JAVA_HOME','/workspace/toolchains/jdk-17.0.16+8'));javac=str(home/'bin/javac')
    libraries=sorted((REPO/'server/libraries').rglob('*.jar'));libraries.sort(key=lambda p:(0 if p.name=='forge-1.20.1-47.4.23-server.jar' else 1,str(p)))
    cp=os.pathsep.join(map(str,[RECRUITS,REPO/'server/plugins/Towny-0.101.2.0.jar',*libraries]))
    build=ROOT/'build';classes=build/'classes';shutil.rmtree(classes,ignore_errors=True)
    with zipfile.ZipFile(RECRUITS) as z:
        packets=[]
        for n in z.namelist():
            if n.startswith('com/talhanation/recruits/network/Message') and '$' not in n and n.endswith('.class') and ('executeServerSide','(Lnet/minecraftforge/network/NetworkEvent$Context;)V') in methods(z.read(n)):packets.append(n[:-6].replace('/','.'))
        assert len(packets)>60
        (build/'packet-targets.json').parent.mkdir(parents=True,exist_ok=True);(build/'packet-targets.json').write_text(json.dumps(packets,indent=2))
        required={'com/talhanation/recruits/RecruitEvents.class':['canAttack','handleSignificantDamage'],'com/talhanation/recruits/entities/AbstractRecruitEntity.class':['needsToGetFood','updateTeam','shouldAttack','isOwnedBy','isEffectedByCommand','assignToPlayer','checkPayment','doNoPaymentAction','hire'],'com/talhanation/recruits/entities/ai/RecruitEatGoal.class':['m_8036_'],'com/talhanation/recruits/world/RecruitsDiplomacyManager.class':['setRelation']}
        for name,expected in required.items():assert set(expected)<=set(n for n,d in methods(z.read(name)))
    generated=build/'generated/ua/empirecraft/recruits/mixin/PacketMixin.java';generated.parent.mkdir(parents=True,exist_ok=True)
    generated.write_text('''package ua.empirecraft.recruits.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraftforge.network.NetworkEvent;
import ua.empirecraft.recruits.ArmyHooks;
@Mixin(targets={'''+','.join('"'+n+'"' for n in packets)+'''},remap=false)
public abstract class PacketMixin {
 @Inject(method="executeServerSide",at=@At("HEAD"),cancellable=true) private void army$sender(NetworkEvent.Context context,CallbackInfo ci){if(!ArmyHooks.packet(this,context)){ArmyHooks.SENDER.remove();ci.cancel();}}
 @Inject(method="executeServerSide",at=@At("RETURN")) private void army$end(NetworkEvent.Context context,CallbackInfo ci){ArmyHooks.SENDER.remove();}
}
''')
    compile(javac,cp,classes,[*sorted((ROOT/'src/main/java').rglob('*.java')),generated])
    files={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')}
    files.update({p.relative_to(ROOT/'src/main/resources').as_posix():p.read_bytes() for p in (ROOT/'src/main/resources').rglob('*') if p.is_file()})
    files['empirecraft_recruits.mixins.json']=json.dumps({'required':True,'minVersion':'0.8','package':'ua.empirecraft.recruits.mixin','compatibilityLevel':'JAVA_17','mixins':['RecruitCombatMixin','RecruitMixin','RecruitFoodMixin','DiplomacyMixin','RecruitHireMixin','RecruitProjectileMixin','PacketMixin'],'injectors':{'defaultRequire':1}}).encode()
    files['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\r\nMixinConfigs: empirecraft_recruits.mixins.json\r\n\r\n'
    archive(ROOT/'dist/empirecraft-recruits-integration-1.0.0.jar',files)
    plugin=build/'plugin-classes';shutil.rmtree(plugin,ignore_errors=True);compile(javac,cp,plugin,sorted((ROOT/'plugin/src/main/java').rglob('*.java')))
    entries={p.relative_to(plugin).as_posix():p.read_bytes() for p in plugin.rglob('*.class')};entries.update({p.relative_to(ROOT/'plugin/src/main/resources').as_posix():p.read_bytes() for p in (ROOT/'plugin/src/main/resources').rglob('*') if p.is_file()})
    archive(ROOT/'dist/TownyRecruitsBridge-2.0.0.jar',entries)
    tests=build/'ration-tests';compile(javac,str(classes),tests,[ROOT/'tests/RationTests.java'])
    subprocess.run([str(home/'bin/java'),'-ea','-cp',str(tests)+os.pathsep+str(classes),'RationTests'],check=True)
    print('Verified packet hooks:',len(packets))
if __name__=='__main__':main()
