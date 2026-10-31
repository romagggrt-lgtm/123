#!/usr/bin/env python3
"""Merge SmpLore+Shahed+Verity 1.06 and Z_Gold+Z_Pantera 1.0.1; fail on conflicts."""
from pathlib import Path, PurePosixPath
from collections import Counter
import hashlib, json, zipfile, struct, zlib

ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'dist/Z_Server_Pack_1.06.zip'
EXTRA=ROOT/'dist/Z_Server_Pack_1.05_Pantera_1.0.1.zip'
OUT=ROOT/'dist/Z_Server_Pack_1.07.zip'

def load(path):
    d={}
    with zipfile.ZipFile(path) as z:
        assert z.testzip() is None, f'Corrupt ZIP: {path}'
        for m in z.infolist():
            if m.is_dir(): continue
            n=m.filename
            q=PurePosixPath(n)
            assert not q.is_absolute() and '..' not in q.parts and '\\' not in n, n
            assert n not in d, f'Duplicate file: {n}'
            d[n]=z.read(m)
    return d

def check_png(name, raw):
    assert raw.startswith(b'\x89PNG\r\n\x1a\n') and len(raw)>=45, f'Invalid PNG {name}'
    assert raw[12:16]==b'IHDR', f'Invalid IHDR {name}'
    w,h=struct.unpack('>II',raw[16:24])
    assert 0<w<=16384 and 0<h<=16384, (name,w,h)
    # Validate every PNG chunk integrity, including the final IEND marker.
    off=8
    while off<len(raw):
        assert off+12<=len(raw), name
        count=struct.unpack('>I',raw[off:off+4])[0]
        assert off+12+count<=len(raw), name
        chunk=raw[off+4:off+8]
        payload=raw[off+8:off+8+count]
        expected=struct.unpack('>I',raw[off+8+count:off+12+count])[0]
        assert (zlib.crc32(chunk+payload)&0xffffffff)==expected, name
        off+=12+count
        if chunk==b'IEND':
            assert off==len(raw), name
            return
    raise AssertionError(f'Missing PNG IEND: {name}')

def main():
    base,extra=load(BASE),load(EXTRA)
    assert any(n.startswith('assets/smplore/') for n in base), 'Missing SmpLore in 1.06'
    assert any(n.startswith('assets/z_pantera/') for n in extra), 'Missing Pantera in addon'
    assert any(n.startswith('assets/z_gold/') for n in extra), 'Missing Gold in addon'
    files=dict(base)
    added=[]
    collisions=[]
    for path,data in extra.items():
        if path=='pack.mcmeta': continue
        if path in files:
            if files[path]!=data: collisions.append(path)
        else:
            files[path]=data;added.append(path)
    if collisions: raise ValueError('Conflicting resources: '+', '.join(collisions))
    assert set(added) and all(x.startswith(('assets/z_pantera/','assets/z_gold/')) for x in added), added
    files['pack.mcmeta']=(json.dumps({'pack':{'min_format':[75,0],'max_format':[75,0],
        'description':'Z_Server_Pack 1.07 | SmpLore + Shahed + Verity + Gold + Pantera | 1.21.11'}},
        ensure_ascii=False,indent=2)+'\n').encode()
    files['MERGED_PACKS.txt']=(
        'Z_Server_Pack 1.07, Minecraft Java 1.21.11.\n'
        'Base: Z_Server_Pack_1.06.zip (SmpLore, Shahed, Z_Verity).\n'
        'Additional resources: Z_Server_Pack_1.05_Pantera_1.0.1.zip (Z_Gold and Z_Pantera).\n'
        'No other plugin textures were available; Z_AP/Z_Check/Z_Morph use vanilla GUI/models.\n'
        'Plugin JAR files are not included or modified.\n'
    ).encode()
    for name,data in base.items():
        if name not in ('pack.mcmeta','MERGED_PACKS.txt'):
            assert files[name]==data, name
    cnt=Counter()
    for path,data in files.items():
        if path.endswith('.json') or path=='pack.mcmeta':
            json.loads(data);cnt['json']+=1
        if path.endswith('.png'):
            check_png(path,data);cnt['png']+=1
        if path.endswith('.ogg'):
            assert data[:4]==b'OggS',path;cnt['ogg']+=1
        if '/models/' in path:cnt['models']+=1
    for prefix in ['smplore','shahed','z_verity','z_gold','z_pantera']:
        assert any(n.startswith('assets/'+prefix+'/') for n in files), 'Missing '+prefix
    OUT.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(OUT,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,data in sorted(files.items()):
            info=zipfile.ZipInfo(name,date_time=(2026,1,1,0,0,0))
            info.compress_type=zipfile.ZIP_DEFLATED
            z.writestr(info,data,compress_type=zipfile.ZIP_DEFLATED,compresslevel=9)
    assert load(OUT)==files
    sha1=hashlib.sha1(OUT.read_bytes()).hexdigest()
    sha256=hashlib.sha256(OUT.read_bytes()).hexdigest()
    OUT.with_suffix('.zip.sha1').write_text(sha1+'\n')
    OUT.with_suffix('.zip.sha256').write_text(sha256+'\n')
    report={
        'pack':OUT.name,'minecraft':'1.21.11','base':BASE.name,'addon':EXTRA.name,
        'base_files_preserved':len(base)-2,'new_assets':len(added),
        'new_asset_paths':added,'resource_collisions':collisions,
        'validated':dict(cnt),'total_files':len(files),'sha1':sha1,'sha256':sha256,
        'minecraft_client_tested':False,'plugin_jars_modified':False
    }
    OUT.with_suffix('.report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({k:v for k,v in report.items() if k!='new_asset_paths'},ensure_ascii=False,indent=2))

if __name__=='__main__': main()
