#!/usr/bin/env python3
"""Package the server addon, config and installation instructions with exact hashes."""
import hashlib,json,zipfile
from pathlib import Path
import build
jar=build.REPO/'server/mods/empirecraft-seasonal-leaves-1.1.0.jar'
data=jar.read_bytes()
row={'id':'empirecraft_seasonal_leaves','version':'1.1.0','file':jar.name,'sides':['server'],'source':'local:seasonal-leaves','size':len(data),'sha256':hashlib.sha256(data).hexdigest(),'sha512':hashlib.sha512(data).hexdigest(),'replaces':'empirecraft-seasonal-leaves-1.0.0.jar'}
(build.ROOT/'release.json').write_text(json.dumps([row],indent=2)+'\n')
files={
    'mods/'+jar.name:data,
    'mods/'+jar.name+'.sha256':(jar.with_suffix('.jar.sha256')).read_bytes(),
    'config/empirecraft_seasonal_leaves-common.toml':(build.REPO/'server/config/empirecraft_seasonal_leaves-common.toml').read_bytes(),
    'README.md':(build.ROOT/'README.md').read_bytes(),
    'LICENSE':(build.ROOT/'LICENSE').read_bytes(),
}
out=build.ROOT/'dist/Empirecraft-Seasonal-Leaves-1.1.0.zip'
build.jar(out,files)
out.with_suffix('.zip.sha256').write_text(hashlib.sha256(out.read_bytes()).hexdigest()+'  '+out.name+'\n')
print(out.name,row['sha256'])
