#!/usr/bin/env python3
"""Structural asset checks only: no claim about rendering in a Minecraft client."""
from pathlib import Path
import json, zipfile, struct
p=Path(__file__).resolve().parents[1]/'dist/Z_Server_Pack_1.04.zip'
checks=0
with zipfile.ZipFile(p) as z:
    assert z.testzip() is None; checks+=1
    names=set(z.namelist())
    meta=json.loads(z.read('pack.mcmeta'))
    assert meta['pack']['min_format']==75 and meta['pack']['max_format']==75; checks+=1
    for name in names:
        if name.endswith('.json') or name=='pack.mcmeta': json.loads(z.read(name))
    checks+=1
    for item in ('shahed','egg'):
        j=json.loads(z.read(f'assets/shah/items/{item}.json'))
        assert j['model']['type']=='minecraft:model'
        ns,model=j['model']['model'].split(':')
        if ns!='minecraft': assert f'assets/{ns}/models/{model}.json' in names
    checks+=1
    for item in ('verity_egg','verity_box','verity_sphere'):
        definition=json.loads(z.read(f'assets/z_verity/items/{item}.json'))
        assert definition['model']['type']=='minecraft:model'
        ns,model=definition['model']['model'].split(':')
        assert f'assets/{ns}/models/{model}.json' in names
    checks+=1
    for model_name in ('verity_egg','verity_box','verity_sphere'):
        assert f'assets/z_verity/models/item/{model_name}.json' in names
    checks+=1
    for model_name in ('verity_box','verity_sphere'):
        model=json.loads(z.read(f'assets/z_verity/models/item/{model_name}.json'))
        for tex in model['textures'].values():
            ns,path=tex.split(':'); assert f'assets/{ns}/textures/{path}.png' in names
    checks+=1
    model=json.loads(z.read('assets/shah/models/item/shahed.json'))
    assert len(model['elements'])>10
    for cube in model['elements']:
        assert all(-16<=a<b<=32 for a,b in zip(cube['from'],cube['to']))
        assert len(cube['faces'])==6
    checks+=1
    for tex in model['textures'].values():
        ns,path=tex.split(':'); assert f'assets/{ns}/textures/{path}.png' in names
    checks+=1
    png=z.read('assets/shah/textures/entity/shahed.png')
    assert png[:8]==b'\x89PNG\r\n\x1a\n' and struct.unpack('>II',png[16:24])==(16,16); checks+=1
    sounds=json.loads(z.read('assets/shah/sounds.json'))
    assert len(sounds)==1024
    for sound in sounds.values():
        for resource in sound['sounds']:
            ns,path=resource['name'].split(':'); assert f'assets/{ns}/sounds/{path}.ogg' in names
    assert z.read('assets/shah/sounds/engine.ogg').startswith(b'OggS'); checks+=1
    assert not any(n.startswith('assets/minecraft/') for n in names); checks+=1
print(f'{checks} pack structure checks passed. Client rendering and audible quality remain untested.')
