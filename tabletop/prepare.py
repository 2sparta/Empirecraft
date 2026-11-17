"""Install the supplied Block & Board, retire Table Top Craft and generate paired resources."""
import hashlib,json,shutil,subprocess,zipfile,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
NAME='blockandboard-forge-1.20.1-0.6.0.jar';OLD='table_top_craft-1.20.1-6.1.2.jar'
def write(p,d):
 p.parent.mkdir(parents=True,exist_ok=True)
 if p.name=='wood.json' and set(d)=={'replace','values'}:p.write_text('{\n  \"replace\": '+str(d['replace']).lower()+',\n  \"values\": [\n'+',\n'.join('    '+json.dumps(v,ensure_ascii=False) for v in d['values'])+'\n  ]\n}\n')
 else:p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n')
def main():
 source=ROOT/'upstream'/NAME;assert hashlib.sha256(source.read_bytes()).hexdigest()=='878a9c2eb7aa532c7f83877348e3d7479b138595a3a94fc8c53ce8efe2c36540'
 with zipfile.ZipFile(source) as z:
  recipe=json.loads(z.read('data/mctabletop/recipes/go_fish_board.json'));recipe['key']['A']={'tag':'minecraft:fishes'}
  names=json.loads(z.read('assets/mctabletop/lang/en_us.json'))
 games={'chess':'Шахи','checkers':'Шашки','reversi':'Реверсі','connect_four':'Чотири в ряд','go':'Ґо','go_13':'Ґо 13×13','go_19':'Ґо 19×19','morris':'Млин','mancala':'Манкала','backgammon':'Нарди','crazy_eights':'Божевільні вісімки','go_fish':'Go Fish','hearts':'Чирви','dominoes':'Доміно','ludo':'Лудо','gin_rummy':'Джин-рамі','cribbage':'Крибедж','spades':'Піки','gomoku':'Ґомоку','sea_battle':'Морський бій','farkle':'Фаркл','klondike':'Косинка','shogi':'Сьоґі','dots_boxes':'Крапки та квадрати','poker':'Покер','blackjack':'Блекджек','yahtzee':'Яцзи','spider':'Павук','chinese_checkers':'Китайські шашки','president':'Президент','mahjong':'Маджонґ','carrom':'Каром','truco':'Труко'}
 lang={'block.mctabletop.small_game_table':'Малий ігровий стіл','block.mctabletop.large_game_table':'Великий ігровий стіл','block.mctabletop.game_chair':'Ігровий стілець','itemGroup.mctabletop.main':'Block & Board','item.mctabletop.board_insert.saved':'Збережена партія — позицію та гравців збережено','item.mctabletop.board_insert.fit_small':'Для столу 1×1','item.mctabletop.board_insert.fit_large':'Для столу 3×3','item.mctabletop.board_insert.remove':'Shift + використання: зняти дошку','item.mctabletop.board_insert.owner':'Змінити може лише власник або оператор'}
 lang.update({'item.mctabletop.'+game+'_board':'Дошка: '+name for game,name in games.items()});assert set(lang)<=set(names)
 extra={"data/mctabletop/recipes/go_fish_board.json":recipe,"assets/mctabletop/lang/uk_ua.json":lang}
 for side in ['', 'server/']:
  base=REPO/side;p=base/'mods'/NAME;shutil.copy2(source,p);p.with_suffix('.jar.sha256').write_text(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+NAME+'\n')
  for suffix in ['', '.sha256']:
   old=base/'mods'/(OLD+suffix)
   if old.exists():
    archived=REPO/'disabled-mods/replaced-2026-11-01'/side/'mods'/old.name;archived.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(old,archived);old.unlink()
  # Remove obsolete optional wood mass IDs and classify new wooden furniture.
  tag=base/'kubejs/data/empirecraft_mass/tags/blocks/wood.json';d=json.loads(tag.read_text());d['values']=[v for v in d['values'] if not (v if isinstance(v,str) else v['id']).startswith(('table_top_craft:','mctabletop:'))];d['values']+=[{'id':'mctabletop:'+name,'required':False} for name in ['small_game_table','large_game_table','game_chair']];write(tag,d)
  for n,d in extra.items():write(base/'kubejs'/n,d)
  p=base/'kubejs/assets/empirecraft_guidebook/patchouli_books/handbook/uk_ua/entries/leisure/tabletop.json';write(p,dict(name='Настільні ігри',icon='mctabletop:chess_board',category='empirecraft_guidebook:leisure',read_by_default=True,sortnum=20,pages=[dict(type='patchouli:text',text='Block & Board замінює Table Top Craft. Виготовте малий або великий ігровий стіл, стілець і дошку потрібної гри. Рецепти є в JEI. Підказка дошки вказує розмір столу: 1×1 або 3×3.'),dict(type='patchouli:text',text='Установіть дошку на відповідний стіл. Для гри скористайтеся стільцем і керуванням біля столу; мод містить ілюстровані пояснення правил. Shift + використання знімає дошку зі збереженою партією. Для Go Fish підходить риба Starcatcher і TFC із загального тегу риби. Старі дошки Table Top Craft не перетворюються автоматично.')]))
  sys.path.insert(0,str(REPO/'handbook'));from layout import fit
  entry=json.loads(p.read_text());fit({}, {'leisure/tabletop':entry});write(p,entry)
  extra['assets/empirecraft_guidebook/patchouli_books/handbook/uk_ua/entries/leisure/tabletop.json']=json.loads(p.read_text())
 for n in ['native-content/inputs.json','native-content/audit/build.json','native-content/retained-code.json']:
  p=REPO/n;d=json.loads(p.read_text());write(p,{k:v for k,v in d.items() if 'table_top_craft-' not in k})
 write(ROOT/'audit/data.json',dict(upstream_sha256=hashlib.sha256(source.read_bytes()).hexdigest(),file=NAME,retired=OLD,files=['kubejs/'+n for n in extra]+['kubejs/data/empirecraft_mass/tags/blocks/wood.json'],boards=len(games),translations=len(lang)))
if __name__=='__main__':main()
