"""Patch the supplied MIT mod with pinned bytecode, keeping its models and credits intact."""
import hashlib,json,os,shutil,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
SDK=Path('/workspace/work/tfc/toolchain');JDK=Path('/workspace/work/tfc/jdk');REF=Path('/workspace/Empirecraft')
NAME='hamzabus_ottoman_mod-1.0.4-forge-empirecraft.1.jar';NS='hamzabus_ottoman_mod';PREFIX='net/mcreator/hamzabusottomanmod/'
def sha(raw):return hashlib.sha256(raw).hexdigest()
def archive(path,files):
 path.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(path,'w') as z:
  for n,raw in sorted(files.items()):
   i=zipfile.ZipInfo(n,(2026,11,16,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o100644<<16;z.writestr(i,raw)
def canonical(value):
 if isinstance(value,str):return {'hamzabus_ottoman_mod:steel_ingot':'overgeared:steel_ingot','hamzabus_ottoman_mod:steel_nugget':'overgeared:steel_nugget'}.get(value,value)
 if isinstance(value,list):return [canonical(v) for v in value]
 if isinstance(value,dict):return {k:canonical(v) for k,v in value.items()}
 return value
def main():
 pin=json.loads((ROOT/'inputs.json').read_text());source=ROOT/'upstream'/pin['source_file'];assert sha(source.read_bytes())==pin['source_sha256'];b=ROOT/'build';b.mkdir(exist_ok=True)
 def run(args,label):
  r=subprocess.run(list(map(str,args)),capture_output=True,text=True);(b/(label+'.log')).write_text(r.stdout+r.stderr)
  if r.returncode:raise RuntimeError(label+'\n'+r.stderr[-6000:])
 libs=list((REF/'server/libraries').rglob('*.jar'));asm=list((REF/'server/libraries/org/ow2/asm').rglob('*.jar'));remapper=[SDK/'tiny-remapper.jar',SDK/'mapping-io.jar',*asm];cp=[SDK/'runtime-named.jar',SDK/'client-named.jar',*libs]
 classes=b/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
 sources=sorted((ROOT/'src').rglob('*.java')) if pin['standard_shield'] else [ROOT/'src/net/mcreator/hamzabusottomanmod/empirecraft/SteelAliases.java']
 run([JDK/'bin/javac','--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,cp)),'-d',classes,*sources],'compile')
 archive(b/'named.jar',{f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')});srg=b/'srg.jar';srg.unlink(missing_ok=True)
 run([JDK/'bin/java','-cp',os.pathsep.join(map(str,remapper)),'dev.architectury.tinyremapper.Main',b/'named.jar',srg,SDK/'named-srg.tiny','named','srg',*cp,'--ignoreConflicts'],'reobf')
 tools=b/'tools';tools.mkdir(exist_ok=True);run([JDK/'bin/javac','-cp',os.pathsep.join(map(str,asm)),'-d',tools,ROOT/'tools/Patch.java'],'patch-compile')
 with zipfile.ZipFile(source) as z:files={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
 report=[]
 def patch(name,kind):
  input=b/'input.class';output=b/'output.class';input.write_bytes(files[PREFIX+name+'.class']);run([JDK/'bin/java','-cp',os.pathsep.join(map(str,[tools,*asm])),'Patch',kind,input,output],kind+'-'+name.rsplit('/',1)[-1]);files[PREFIX+name+'.class']=output.read_bytes();report.append(name)
 patch('init/HamzabusOttomanModModItems','items');patch('procedures/TopuzLivingEntityIsHitWithToolProcedure','noop');patch('item/TopuzItem','mace')
 if pin['standard_shield']:patch('procedures/WhenShieldAttackedProcedure','noop')
 with zipfile.ZipFile(srg) as z:files.update({n:z.read(n) for n in z.namelist() if n.endswith('.class')})
 removed=[]
 for name in list(files):
  if name.startswith('data/'+NS+'/recipes/') and name.endswith('.json'):
   key=Path(name).stem
   if key in ['steel_ingot_recipe','steelnugget_recipe','reversesteel']:del files[name];removed.append(key);continue
   data=canonical(json.loads(files[name]));result=data.get('result',{});result=result.get('item') if isinstance(result,dict) else result
   if result in [NS+':yatagan',NS+':kilij',NS+':devrish_axe',NS+':topuz',NS+':nakkareci',NS+':ottoman_shield']:
    data.update(type='overgeared:forging',tier='iron',category='misc',hammering=4,has_quality=True,needs_minigame=True,blueprint=['shield'] if 'shield' in result else ['sword'] if result.endswith((':yatagan',':kilij')) else ['axe'],need_quenching=False,requires_blueprint=False,minimum_quality='poor',quality_difficulty='none')
   if key=='pileofrawbeefrecipe':
    data['key']={'a':{'tag':'forge:raw_mutton'},'b':{'tag':'forge:raw_chicken'},'c':{'tag':'forge:raw_beef'}}
   files[name]=(json.dumps(data,ensure_ascii=False,indent=2)+'\n').encode()
  elif name.startswith('data/') and name.endswith('.json'):
   files[name]=(json.dumps(canonical(json.loads(files[name])),ensure_ascii=False,indent=2)+'\n').encode()
 def add(name,data):files[name]=(json.dumps(data,ensure_ascii=False,indent=2)+'\n').encode()
 for kind,items in [('crushing',['topuz','nakkareci']),('slashing',['yatagan','kilij','devrish_axe'])]:add('data/tfc/tags/items/deals_'+kind+'_damage.json',dict(replace=False,values=[NS+':'+s for s in items]))
 for animal in ['beef','mutton','chicken']:add('data/forge/tags/items/raw_'+animal+'.json',dict(replace=False,values=['minecraft:'+animal,dict(id='tfc:food/'+animal,required=False)]))
 armor=['ertugrul_helmet','fez_helmet','turban_helmet','janissary_helmet','janissary_chestplate','janissary_leggings','janissary_boots','yelek_chestplate']
 armor += [c+'_ottoman_yelek_chestplate' for c in ['black','blue','brown','cyan','gray','green','light_blue','light_gray','lime','magenta','orange','pink','purple','red','yellow']]
 add('data/empirecraft_armor/tags/items/padded.json',dict(replace=False,values=[NS+':'+s for s in armor]))
 add('data/empirecraft_armor/tags/items/outer.json',dict(replace=False,values=[NS+':shishak_helmet']))
 uk={'shishak_helmet':'Шишак','ertugrul_helmet':'Шапка Ертугрула','fez_helmet':'Феска','turban_helmet':'Тюрбан','janissary_helmet':'Яничарський бьорк','janissary_chestplate':'Яничарський кафтан','janissary_leggings':'Яничарські шаровари','janissary_boots':'Яничарські ємені','yelek_chestplate':'Білий османський жилет','yatagan':'Ятаган','kilij':'Килич','devrish_axe':'Табар','topuz':'Топуз','nakkareci':'Наккареджі','ottoman_shield':'Османський калкан','ottoman_shield_blocking':'Османський калкан','fabric':'Тканина','felt':'Повсть','pile_of_raw_beef':'Сирий кебаб','kebab':'Кебаб','shish_kebab':'Шиш-кебаб','steel_ingot':'Сталевий злиток','steel_nugget':'Сталевий самородок','bu_bayrak':'Музична платівка','ceddin_deden':'Музична платівка'}
 colors={'black':('Чорна','Чорний'),'blue':('Синя','Синій'),'brown':('Коричнева','Коричневий'),'cyan':('Бірюзова','Бірюзовий'),'gray':('Сіра','Сірий'),'green':('Зелена','Зелений'),'light_blue':('Блакитна','Блакитний'),'light_gray':('Світло-сіра','Світло-сірий'),'lime':('Лаймова','Лаймовий'),'magenta':('Пурпурова','Пурпуровий'),'orange':('Помаранчева','Помаранчевий'),'pink':('Рожева','Рожевий'),'purple':('Фіолетова','Фіолетовий'),'red':('Червона','Червоний'),'yellow':('Жовта','Жовтий')}
 for color,(feminine,masculine) in colors.items():
  uk[color+'_fabric']=feminine+' тканина';uk[color+'_felt']=feminine+' повсть';uk[color+'_ottoman_yelek_chestplate']=masculine+' османський жилет'
 lang={'item.'+NS+'.'+k:v for k,v in uk.items()}
 lang.update({'block.'+NS+'.'+key:text for key,text in [('empty_kebab_machine','Жаровня для кебаба'),('kebab_machine_with_raw_kebab','Жаровня із сирим кебабом'),('kebab_raw_instate_ofgetting_cooked','Жаровня: кебаб готується'),('kebab_machine_with_cooked_kebab','Жаровня з готовим кебабом')]})
 lang['item_group.'+NS+'.ottoman_empire']='Османська імперія'
 add('assets/'+NS+'/lang/uk_ua.json',lang)
 for name in list(files):
  if name.startswith('assets/'+NS+'/lang/') and name.endswith('.json'):
   translated=json.loads(files[name]);translated={k:v for k,v in translated.items() if '.topuz.description_' not in k and '.ottoman_shield.description_' not in k};add(name,translated)
 if pin['standard_shield']:
  name='assets/'+NS+'/models/item/ottoman_shield.json';model=json.loads(files[name]);model['textures'].pop('missing',None);model['overrides']=[dict(predicate={'blocking':1},model=NS+':item/ottoman_shield_blocking')];add(name,model)
 metadata=files['META-INF/mods.toml'].decode().replace('version="1.0.3"','version="'+pin['version']+'"');metadata+='\n[[dependencies.'+NS+']]\nmodId="overgeared"\nmandatory=true\nversionRange="[1.6.36,)"\nordering="AFTER"\nside="BOTH"\n\n[[dependencies.'+NS+']]\nmodId="empirecraft_armory"\nmandatory=true\nversionRange="[1.0.0,)"\nordering="AFTER"\nside="BOTH"\n';files['META-INF/mods.toml']=metadata.encode()
 output=ROOT/'dist'/NAME;archive(output,files);digest=sha(output.read_bytes());output.with_suffix('.jar.sha256').write_text(digest+'  '+NAME+'\n')
 for folder in ['mods','server/mods']:
  for old in (REPO/folder).glob('hamzabus_ottoman_mod-*.jar*'):
   if old.name not in [NAME,NAME+'.sha256']:old.unlink()
  shutil.copy2(output,REPO/folder/NAME);shutil.copy2(output.with_suffix('.jar.sha256'),REPO/folder/(NAME+'.sha256'))
 (ROOT/'audit').mkdir(exist_ok=True);(ROOT/'audit/build.json').write_text(json.dumps(dict(status='passed',source_sha256=pin['source_sha256'],sha256=digest,patched_classes=report,steel_recipes_removed=removed,standard_shield=pin['standard_shield'],original_models_retained=True),indent=2)+'\n');print('OTTOMAN_BUILD_PASS',digest)
if __name__=='__main__':main()
