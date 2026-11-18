#!/usr/bin/env python3
"""Transform real installed classes and verify resource integration without a running game."""
import importlib.util,json,os,subprocess,zipfile
from pathlib import Path
from build import ROOT,REPO,BUILD,NAME,JDK,classpath
def main():
 java=JDK/'bin/java';javac=java.with_name('javac');classes=BUILD/'tests';classes.mkdir(parents=True,exist_ok=True)
 jars=[ROOT/'dist'/NAME,REPO/'server/mods/empirecraft-locks-compat-1.0.1.jar',*classpath()];cp=os.pathsep.join(map(str,jars))
 fixtures=REPO/'forging-balance/src/test/java/guardtest'
 sources=[fixtures/'OfflineMixinService.java',fixtures/'OfflineProperties.java',ROOT/'tests/WorkshopMixinTest.java']
 subprocess.run([str(javac),'--release','17','-proc:none','-cp',cp,'-d',str(classes),*map(str,sources)],check=True)
 services=classes/'META-INF/services';services.mkdir(parents=True,exist_ok=True)
 (services/'org.spongepowered.asm.service.IMixinService').write_text('guardtest.OfflineMixinService\n');(services/'org.spongepowered.asm.service.IGlobalPropertyService').write_text('guardtest.OfflineProperties\n')
 subprocess.run([str(java),'-ea','-cp',str(classes)+os.pathsep+cp,'guardtest.WorkshopMixinTest'],check=True)
 spec=importlib.util.spec_from_file_location('chinese_policy',REPO/'chinese-weapons/resources.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);policy=module.policy();removed=[k for k in policy['aliases'] if k.startswith('chineseweapons:diamond_') and any(k.endswith('_'+family) for family in ['dagger_axe','glaive','guduo','sky_piercing_halberd','song_javelin','tang_dynasty_heng_saber'])];assert len(removed)==6
 with zipfile.ZipFile(ROOT/'dist/chineseweapons-forge-1.20.1-1.2.1.6-empirecraft.2.jar') as z:
  for name in z.namelist():
   if name.startswith('data/') and name.endswith('.json'):assert not any(item in z.read(name).decode() for item in removed),name
 with zipfile.ZipFile(ROOT/'dist'/NAME) as z:
  assert json.loads(z.read('pack.mcmeta'))['pack']['pack_format']==15
  for weapon in ['yatagan','kilij','topuz','devrish_axe','nakkareci']:assert json.loads(z.read('data/hamzabus_ottoman_mod/weapon_attributes/'+weapon+'.json'))['parent'].startswith('bettercombat:')
 print('WORKSHOP_STATIC_PASS: transformed six real classes; removed six diamond weapons; five Ottoman attack profiles')
if __name__=='__main__':main()
