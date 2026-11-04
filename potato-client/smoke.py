#!/usr/bin/env python3
"""Prepare disposable real-client/server fixtures and verify their actual output files."""
import argparse,json,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parent

def prepare():
    client=ROOT/'run-smoke';client.mkdir(exist_ok=True)
    (client/'options.txt').write_text('version:3465\nrenderDistance:12\nsimulationDistance:8\nentityDistanceScaling:1.25\nparticles:0\nlang:en_us\nonboardAccessibility:false\nskipMultiplayerWarning:true\n')
    config=client/'config';config.mkdir(exist_ok=True)
    (config/'empirecraft-potato.json').unlink(missing_ok=True)
    for p in client.glob('potato-*.json'):p.unlink()
    server=ROOT/'run-server';server.mkdir(exist_ok=True)
    (server/'eula.txt').write_text('eula=true\n')
    (server/'server.properties').write_text('server-ip=127.0.0.1\nonline-mode=false\nmax-players=2\nspawn-protection=0\nview-distance=4\nsimulation-distance=4\nlevel-type=minecraft:flat\nspawn-monsters=false\nspawn-animals=false\nmax-tick-time=120000\n')
    print('Disposable offline test instance prepared; installation-default own config is absent')

def verify(phase):
    client=ROOT/'run-smoke'
    report=json.loads((client/('potato-smoke-'+phase+'.json')).read_text())
    assert report['passed'],report
    assert report['checks']>=2,report
    if phase=='multiplayer':
        server=json.loads((ROOT/'run-server/potato-server-result.json').read_text())
        assert server['passed'] and not server['addon_loaded'],server
        assert report['rendered_target_frames']>=2,report
        report['server']=server
    if phase=='compat':assert report['embeddium_loaded'] and report['dynamic_fps_loaded'],report
    print('PASS real client:',json.dumps(report,ensure_ascii=False))
    return report

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('action',choices=['prepare','verify']);parser.add_argument('--phase');args=parser.parse_args()
    if args.action=='prepare':prepare()
    else:verify(args.phase)
