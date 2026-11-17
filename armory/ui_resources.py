"""Workbench-specific translations and original opaque 32px block art."""
import io
from PIL import Image,ImageDraw
LABELS={
 'tab.0':('Складання','Assembly'),'tab.1':('Обробка','Treatment'),'tab.2':('Ремонт','Repair'),
 'tab_hint.0':('Оберіть обладунок, перегляньте чотири етапи й підготуйте деталі.','Choose armor, inspect all four stages and prepare the parts.'),
 'tab_hint.1':('Гартування, відпуск і доковка захисних деталей.','Harden, temper and rework protective components.'),
 'tab_hint.2':('Відновлення обладунку заміною сумісної деталі.','Restore armor by replacing a matching component.'),
 'help':('?','?'),'help_hint':('Як користуватися столом — натисніть для короткої інструкції.','Click for a short workbench guide.'),
 'help_body':('1. Знайдіть обладунок у списку. 2. Натисніть номер етапу: деталі показані з кількостями. 3. Покладіть матеріали у 12 комірок «Запаси». Зелене число — готові деталі. Жовте — потрібна термообробка. Натисніть на деталь, щоб відкрити її рецепт у JEI. «Обробка» й «Ремонт» мають окремі вкладки. Натисніть знову або Esc, щоб повернутись.','1. Find armor in the list. 2. Click a stage to inspect its counted requirements. 3. Put materials in the twelve Supply slots. Green means ready; gold means treatment is needed. Click a component to open its JEI recipe. Treatment and Repair have separate tabs. Click again or press Esc to return.'),
 'previous':('‹','‹'),'next':('›','›'),'supplies':('Запаси','Supplies'),'result_count':('Виробів: %s','Results: %s'),
 'stage_short.0':('1 Осн.','1 Base'),'stage_short.1':('2 Секц.','2 Main'),'stage_short.2':('3 Част.','3 Parts'),'stage_short.3':('4 Фініш','4 Finish'),
 'stage_installed':('Цей етап уже завершений. Тут можна перевірити використані матеріали.','This stage is installed. Inspect its original requirements here.'),
 'stage_preview':('Натисніть для перегляду деталей. Складати потрібно по черзі.','Click to inspect parts. Assemble the stages in order.'),
 'selection_locked':('Заберіть роботу з комірки W, щоб обрати інший виріб.','Remove the workpiece from slot W to choose another armor.'),
 'return_current':('До етапу %s','Return to stage %s'),'return_hint':('Ви переглядаєте інший етап. Натисніть, щоб повернутись до поточного.','You are previewing another stage. Return to the current stage before assembly.'),
 'assemble_stage':('Зібрати етап %s','Assemble stage %s'),
 'select_hint':('Знайдіть і виберіть обладунок у списку ліворуч.','Find and select armor in the list on the left.'),
 'assemble_hint':('Використати лише матеріали поточного етапу із запасів стола. Робота триває 2 секунди.','Use only this stage\'s materials from the table supplies. Work takes 2 seconds.'),
 'missing_hint':('Підготуйте деталі: наведіть на червоні / жовті числа.','Prepare the parts: hover red or gold counts for help.'),
 'ready_hint':('Деталі готові. Можна складати етап.','All parts are ready. Assemble this stage.'),
 'work_hint':('W — робота. На початку залиште порожнім: незавершений обладунок з’явиться сам. Для ремонту покладіть сюди пошкоджену броню.','W — workpiece. Leave empty when starting: unfinished armor appears automatically. Put damaged armor here for repair.'),
 'process_hint':('T — одна деталь для обробки. Оберіть вкладку «Обробка». Молот і креслення покладіть у запаси.','T — one component for treatment. Open the Treatment tab. Put hammer and blueprint in the supply slots.'),
 'output_hint':('Вихід готового виробу. Звільніть цю комірку перед завершенням складання або ремонтом.','Finished output. Empty this slot before the final assembly stage or a repair.'),
 'supply_hint':('Матеріали мають лежати у 12 комірках «Запаси» зліва внизу. Речі в інвентарі гравця не враховуються.','Materials must be in the twelve Supply slots at bottom left. Player inventory items are not counted.'),
 'required_count':('Потрібно для цього етапу: %s','Required for this stage: %s'),'stock_count':('У запасах стола: %s / %s','In table supplies: %s / %s'),'ready_count':('Готові до складання: %s / %s','Ready for assembly: %s / %s'),'missing_count':('Бракує: %s','Missing: %s'),
 'treatment_hint':('Частина деталей не готова. Сталь і чорна сталь: гартування 800–1050 °C, потім відпуск 250–450 °C у вкладці «Обробка».','Some parts need treatment. Steel and black steel: harden at 800–1050 C, then temper at 250–450 C in Treatment.'),
 'alternatives':('Підійде будь-який із варіантів, які змінюються на значку.','Any of the rotating ingredient alternatives is accepted.'),
 'recipe_click':('Натисніть на деталь: як її виготовити (JEI).','Click this component to see how to make it (JEI).'),
 'materials_page':('Коліщатко: матеріали %s/%s','Scroll: materials %s/%s'),
 'instructions.1':('1. Деталь → T. 2. Молот і креслення → запаси, якщо доковуєте. 3. Оберіть дію праворуч.','1. Component → T. 2. Hammer and blueprint → supplies for rework. 3. Choose an action.'),
 'instructions.2':('1. Броня → W. 2. Сумісна деталь + 2 заклепки (м’яка броня: 2 нитки) → запаси. 3. «Ремонт».','1. Armor → W. 2. Matching part + 2 rivets (padded: 2 strings) → supplies. 3. Repair.'),
 'process_title':('Покладіть деталь у T','Put a component in T'),'repair_title':('Покладіть броню у W','Put damaged armor in W'),
 'process_summary':('Гарт: 800–1050 °C, казан води.\nВідпуск: 250–450 °C.','Harden: 800–1050 C + water.\nTemper: 250–450 C.'),
 'repair_hint':('Готова сумісна деталь у запасах + 2 заклепки; для м’якої броні — 2 нитки. Звільніть вихід.','Supplies need a ready matching part + 2 rivets; padded armor uses 2 strings instead. Empty the output slot.'),
 'operation.1':('Сталь / чорна сталь без обробки, 800–1050 °C. Казан із водою має торкатися стола; витрачається один рівень води. Деталь лежить у T.','Untreated steel / black steel at 800–1050 C in T. An adjacent water cauldron supplies one level of water.'),
 'operation.2':('Загартована сталева деталь у T, нагріта до 250–450 °C. Після відпуску деталь готова до складання.','Hardened steel component in T, reheated to 250–450 C. It becomes ready for assembly.'),
 'operation.3':('Деталь у T при робочій температурі TFC. У запасах потрібні придатний ковальський молот і краще креслення броні. Якість зростає на один рівень; термообробку потрібно повторити.','Component in T at TFC working temperature. Supplies need a usable smithing hammer and a better armor blueprint. Gains one quality tier and resets treatment.'),
 'gunsmith.normal':('Звичайне виготовлення','Standard manufacture'),
 'gunsmith.learned':('З багаторазовим кресленням','With reusable blueprint'),
 'gunsmith.tools':('Інструменти по черзі, у другій руці:','Cycle tools in the offhand:'),
 'gunsmith.steps':('Операцій: %s · молот → зубило → абразив','Operations: %s · hammer → chisel → abrasive'),
 'gunsmith.tool_hint':('Не витрачається, але зношується. Тримайте в другій руці.','Not consumed, but loses durability. Hold in the offhand.'),
 'gunsmith.blueprint_hint':('Тримайте креслення в інвентарі; воно не витрачається.','Keep this blueprint in inventory; it is not consumed.'),
 'gunsmith.instruction':('Shift+ПКМ без предметів: вибір виробу.\nМатеріали — в інвентарі.\nОдин папір у руці + ПКМ: почати.\nДалі: заготовка; інструмент у 2-й руці.\nПКМ кожні 1–3 с, інструменти по колу.','Sneak-right-click empty-handed: select.\nMaterials go in your inventory.\nHold one paper; right-click to start.\nThen hold workpiece, tool in offhand.\nRight-click every 1–3 s; cycle tools.'),
 'working':('Триває робота… залишайтеся поряд.','Working… stay near the table.')}

def translations(uk,en):
 for key,(u,e) in LABELS.items():uk['armory.'+key]=u;en['armory.'+key]=e

def block_art():
 files={}
 for kind in ['top','side','front','bottom']:
  im=Image.new('RGB',(32,32),(88,57,36));d=ImageDraw.Draw(im)
  # All faces are opaque, with new oak boards, iron corner straps and warm rivets.
  for y in range(32):
   for x in range(32):
    n=((x*17+y*31+x*y*3)%13)-6;v=6 if y%8==0 else 0;im.putpixel((x,y),(max(0,100+n-v),max(0,67+n-v),max(0,41+n-v)))
  for y in [7,15,23]:d.line((0,y,31,y),fill=(57,37,26))
  if kind=='top':
   d.rectangle((3,4,27,27),fill=(52,68,68),outline=(28,37,38));d.rectangle((5,6,20,24),fill=(202,185,142),outline=(103,89,62))
   d.line((7,9,18,9),fill=(137,120,82));d.polygon([(10,12),(13,10),(16,12),(17,18),(9,18)],outline=(72,90,96));d.line((11,20,16,20),fill=(137,120,82))
   d.rectangle((24,8,25,23),fill=(123,77,42));d.rectangle((22,7,28,10),fill=(127,143,148));d.line((22,7,28,7),fill=(196,204,198));d.rectangle((24,24,28,25),fill=(176,141,78))
  elif kind=='front':
   for y in [6,16]:
    d.rectangle((4,y,27,y+8),fill=(74,48,31),outline=(44,32,26));d.line((5,y+1,26,y+1),fill=(144,102,61));d.rectangle((13,y+3,18,y+5),fill=(143,150,143));d.line((14,y+4,17,y+4),fill=(53,60,60))
  elif kind=='side':
   d.rectangle((5,5,26,26),outline=(49,33,26),width=2);d.line((7,23,24,8),fill=(137,94,54),width=3);d.line((7,24,24,9),fill=(61,43,29))
  for x in [0,29]:
   d.rectangle((x,0,x+2,31),fill=(61,70,71));d.line((x,0,x,31),fill=(107,119,118))
   for y in [3,27]:d.rectangle((x+1,y,x+1,y+1),fill=(202,167,96))
  raw=io.BytesIO();im.save(raw,format='PNG');files['assets/empirecraft_armory/textures/block/workbench_'+kind+'.png']=raw.getvalue()
 return files
