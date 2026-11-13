#!/usr/bin/env python3
"""Export only a safe whitelist of timing facts from a locally decoded spark recording."""
import argparse,collections,datetime,hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parent
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--decoded',type=Path,required=True);a=parser.parse_args()
    raw=json.loads(a.decoded.read_text());metadata=raw['metadata'];thread=next(t for t in raw['threads'] if t['name']=='Server thread')
    frames=collections.Counter()
    for node in thread['children']:frames[node['class_name']+'.'+node['method_name']]+=sum(node['times'])
    nodes=thread['children'];parents={child:index for index,node in enumerate(nodes) for child in node['children_refs']}
    def ancestor(index,class_name,method_name):
        while index in parents:
            index=parents[index];node=nodes[index]
            if node['class_name']==class_name and node['method_name']==method_name:return True
        return False
    def tick_samples(class_name,method_name,palette_only=False):
        return sum(sum(node['times']) for index,node in enumerate(nodes)
            if node['class_name']==class_name and node['method_name']==method_name
            and ancestor(index,'net.minecraft.server.MinecraftServer','m_5705_')
            and (not palette_only or ancestor(index,'net.minecraft.world.level.chunk.HashMapPalette','m_187917_')))
    duration=metadata['end_time']-metadata['start_time'];active=frames['net.minecraft.server.MinecraftServer.m_5705_']
    selected={
        'server_tick':active,
        'wait_until_next_tick':frames['net.minecraft.server.MinecraftServer.m_130012_'],
        'chunk_saving':tick_samples('net.minecraft.server.level.ChunkMap','m_140258_'),
        'chunk_serializer':tick_samples('net.minecraft.world.level.chunk.storage.ChunkSerializer','m_63454_'),
        'palette_iterator':tick_samples('com.google.common.collect.Iterators$5','computeNext',True),
    }
    # Match known methods by their named SRG identities; reject stale mappings.
    assert selected['server_tick']>0 and selected['chunk_serializer']>0 and selected['palette_iterator']>0,selected
    windows=[]
    for key,window in sorted(raw['time_window_statistics'].items(),key=lambda row:int(row[0])):
        windows.append({k:window[k] for k in ['tps','mspt_median','mspt_max','chunks','entities','players']})
    properties=json.loads(metadata.get('server_configurations',{}).get('server.properties','{}'))
    report={'capture_start_utc':datetime.datetime.fromtimestamp(metadata['start_time']/1000,datetime.timezone.utc).isoformat(),
        'capture_end_utc':datetime.datetime.fromtimestamp(metadata['end_time']/1000,datetime.timezone.utc).isoformat(),
        'duration_seconds':duration/1000,'ticks':metadata['number_of_ticks'],'profiled_threads':['Server thread'],'client_profile_provided':False,
        'sampled_frame_milliseconds':selected,'chunk_serializer_percent_of_tick_samples':round(100*selected['chunk_serializer']/active,2),'palette_iterator_percent_of_tick_samples':round(100*selected['palette_iterator']/active,2),
        'windows':windows,'tps_min':min(w['tps'] for w in windows),'tps_max':max(w['tps'] for w in windows),
        'median_mspt_range':[min(w['mspt_median'] for w in windows),max(w['mspt_median'] for w in windows)],'worst_mspt':max(w['mspt_max'] for w in windows),
        'deployed_settings':{k:properties.get(k) for k in ['view-distance','simulation-distance','sync-chunk-writes']},
        'observed_stall_first_log_time':'16:56:35','last_server_command_before_stall':'/back at 16:56:22','profile_ends_before_stall':True,
        'client_log_evidence':{'cpu':'Intel Core i5-8400, 6 logical CPUs','gpu':'Radeon RX 580','maximum_heap_mib':8192,'chunk_builder_workers':2,'out_of_memory_crash':False,'fatal':'Ponder emptyBlazeBurner / flame_type on minecraft:stone'},
        'limitations':['Recording ends approximately 50 seconds before the first physics-frame queue warning; it cannot identify the subsequent blocked main-thread stack.','Frame percentages describe sampled server-thread work, not client FPS or an end-to-end speedup.'],
        'export_policy':'Only explicit timing/settings/hardware fields; no raw profile, server configuration, player names or locations are published.'}
    target=ROOT/'audit/server-profile-summary.json';target.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print('PROFILE_SUMMARY_PASS',report['duration_seconds'],report['median_mspt_range'],report['chunk_serializer_percent_of_tick_samples'])
if __name__=='__main__':main()
