import json,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
def encoded(value):return (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
def generate():
 files={}
 def add(path,value):files[path]=encoded(value)
 language={
  'diameter':('Діаметр: 5–490 мм, кратний 5','Diameter: 5–490 mm, multiples of 5'),
  'start':('Виплавити · паливо в інвентарі','Melt · fuel in inventory'),
  'progress':('Виплавлення: %s%%','Melting: %s%%'),
  'collect':('Забрати готову партію','Collect finished batch'),
  'need_fuel':('Потрібні метал і достатньо палива в інвентарі. Діаметр — 5–490 мм, кратний 5.','Metal and enough furnace fuel are required in inventory. Diameter must be 5–490 mm, a multiple of 5.')
 }
 for index,locale in enumerate(['uk_ua','en_us']):add('assets/empirecraft_workshop/lang/'+locale+'.json',{'workshop.casting.'+key:value[index] for key,value in language.items()})
 # Explicit layers for Thirty Years War's clothing names, which lack gambeson/mail suffixes.
 with zipfile.ZipFile(next((REPO/'mods').glob('epic-knights-thirty*.jar'))) as z:
  names=[key.removeprefix('item.thirtyyearswar.') for key in json.loads(z.read('assets/thirtyyearswar/lang/en_us.json')) if key.startswith('item.thirtyyearswar.')]
 cloth=['apron_skirt','bodice','breeches','buff_coat','buff_waistcoat','cavalier_hat','coat','doublet','dutch_cap','flat_cap','monmouth_cap','officer_doublet','riders_breeches','shirt','skirt','skull_cap','slouch_hat','waistcoat','apron_skirt_boots','breeches_boots','riders_breeches_boots','skirt_boots']
 padded=['thirtyyearswar:baroque_'+name for name in cloth]+['thirtyyearswar:renaissance_cap','thirtyyearswar:rich_breeches','thirtyyearswar:rich_doublet']
 outer=['thirtyyearswar:'+name for name in names if any(part in name for part in ['armour','cuirass','helmet','burgonet'])]
 for rank,values in [('padded',padded),('outer',outer)]:add('data/empirecraft_armor/tags/items/'+rank+'.json',{'replace':False,'values':[{'id':value,'required':False} for value in values]})
 # All Ottoman melee weapons were missing Better Combat data in the installed jar.
 for item,parent in [('yatagan','cutlass'),('kilij','cutlass'),('topuz','mace'),('devrish_axe','axe'),('nakkareci','mace')]:add('data/hamzabus_ottoman_mod/weapon_attributes/'+item+'.json',{'parent':'bettercombat:'+parent})
 # Additional gaps found in the real server registry, including secondary melee attacks
 # on thrown weapons. Their normal right-click throwing behavior is retained.
 extra={'alexsmobs:tendon_whip':'sword','medieval_boomsticks:war_dart':'dagger','epic_knights__japanese_armory:iron_shuriken':'dagger','darkagesarmory:steel_magyar_sabre':'cutlass'}
 extra.update({'darkagesarmory:'+name:'dagger' for name in ['steel_langseax','steel_scramasax','steel_sittingbourne_seax','steel_small_seax']})
 for item,parent in extra.items():
  namespace,name=item.split(':');add('data/'+namespace+'/weapon_attributes/'+name+'.json',{'parent':'bettercombat:'+parent})
 # Preserve a readable in-game description alongside the native gunsmith and Siegeworks guides.
 for locale,title,text in [
  ('uk_ua','Лиття куль','У столі Siegeworks можна вписати діаметр від 5 до 490 мм, кратний 5. Метал і звичайне паливо печі тримай в інвентарі. Натисни «Виплавити»: матеріали й паливо оплачуються один раз. Партія плавиться щонайменше 10 секунд; великі партії довше. Закриття меню не зупиняє роботу, але незавантажений чанк ставить її на паузу. Повернись і забери готову партію кнопкою. Якщо інвентар заповнений, вона залишиться у столі. Ламання стола до завершення повертає метал; витрачене паливо не повертається.'),
  ('en_us','Casting shot','Enter a diameter from 5 to 490 mm, a multiple of five, in the Siegeworks Casting Table. Keep metal and furnace fuel in inventory. Starting a batch pays both once; melting takes at least ten seconds, longer for large batches. Work continues after closing the menu and pauses in unloaded chunks. Collect the finished batch with the button; a full inventory leaves it stored in the table. Breaking the table before completion returns the metal; spent fuel is not refunded.')
 ]:
  import sys
  sys.path.insert(0,str(REPO/'handbook'));import layout
  entry={'name':title,'icon':'siegeworks:casting_table','category':'empirecraft_guidebook:metals','sortnum':33,'read_by_default':True,'pages':[{'type':'patchouli:text','text':text}]};layout.fit({}, {'shot_casting':entry})
  add('assets/empirecraft_guidebook/patchouli_books/handbook/'+locale+'/entries/metals/shot_casting.json',entry)
 return files
