#!/usr/bin/env python3
"""Merge Z_Book's safe default paper model into the previous server pack.
Replace with book/pack-template after providing the user's own texture.
"""
from pathlib import Path
from zipfile import ZipFile,ZipInfo,ZIP_DEFLATED
import json,hashlib
root=Path(__file__).resolve().parents[1];old=root/'dist/Z_Server_Pack_1.11.zip';out=root/'dist/Z_Server_Pack_1.12.zip'
with ZipFile(old) as z: files={name:z.read(name) for name in z.namelist() if not name.endswith('/')}
metadata=json.loads(files['pack.mcmeta']);metadata['pack']['description']='Z_Server Pack 1.12 • Z_Book';files['pack.mcmeta']=(json.dumps(metadata,ensure_ascii=False,indent=2)+'\n').encode()
files['assets/zbook/items/ekspanor_fanfiction.json']=b'{"model":{"type":"minecraft:model","model":"zbook:item/ekspanor_fanfiction"}}\n'
files['assets/zbook/models/item/ekspanor_fanfiction.json']=b'{"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/paper"}}\n'
with ZipFile(out,'w',compression=ZIP_DEFLATED,compresslevel=9) as z:
 for name,data in sorted(files.items()):
  info=ZipInfo(name,(2020,1,1,0,0,0));info.compress_type=ZIP_DEFLATED;info.external_attr=0o644<<16;z.writestr(info,data)
for alg in ('sha1','sha256'):
 value=hashlib.new(alg,out.read_bytes()).hexdigest();Path(str(out)+'.'+alg).write_text(value+'\n');print(alg,value)
