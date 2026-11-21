from pathlib import Path
import os,subprocess
from build_water import jar
R=Path(__file__).resolve().parent;REPO=R.parent;b=R/'build/frontier';b.mkdir(parents=True,exist_ok=True)
libs=sorted(Path('/workspace/Empirecraft/server/libraries').rglob('*.jar'));cp=[REPO/'town-menu-art/build/refresh/bukkit-api.jar',*libs]
subprocess.run(list(map(str,['java','com.sun.tools.javac.Main','-source','17','-target','17','--system','/workspace/work/tfc/jdk','-proc:none','-cp',os.pathsep.join(map(str,cp)),'-d',b,*sorted((R/'plugin').rglob('*.java'))])),check=True)
files={p.relative_to(b).as_posix():p.read_bytes() for p in b.rglob('*.class')};files['plugin.yml']=b'name: EmpirecraftFrontier\nversion: 1.0.0\nmain: ua.empirecraft.frontier.Frontier\napi-version: "1.20"\ndepend: [Towny]\nloadbefore: [EssentialsSpawn]\ncommands:\n  guides:\n    aliases: [manuals, guidebooks]\n    description: Recover all current guides once per day\n  village:\n    description: Apply for village population approval\npermissions:\n  empirecraft.frontier.approve:\n    default: op\n'
jar(R/'dist/empirecraft-frontier-1.0.0.jar',files)
