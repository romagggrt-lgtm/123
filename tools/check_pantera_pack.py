#!/usr/bin/env python3
"""Validate item textures against Minecraft's actual atlas directory rules.

A PNG merely existing under textures/entity does not put it into the item atlas.
That missing check was the 1.0.0 bug. This reads the vanilla 1.21.11 atlas definition.
"""
from pathlib import Path
import json,math
from merge_smplore_pack import read_pack,check_references

ROOT=Path(__file__).resolve().parents[1]
ATLAS=ROOT/'pantera/verification/vanilla-1.21.11-items-atlas.json'

def item_sprites(files):
    sources=json.loads(ATLAS.read_text())['sources']
    if 'assets/minecraft/atlases/items.json' in files:
        sources=sources+json.loads(files['assets/minecraft/atlases/items.json'])['sources']
    sprites=set()
    for source in sources:
        kind=source['type'].removeprefix('minecraft:')
        if kind=='directory':
            for name in files:
                parts=name.split('/')
                if len(parts)<5 or parts[0]!='assets' or parts[2]!='textures' or not name.endswith('.png'):continue
                prefix='/'.join(parts[:3])+'/'+source['source']+'/'
                if name.startswith(prefix):sprites.add(parts[1]+':'+source['prefix']+name[len(prefix):-4])
        elif kind=='single':
            sprites.add(source.get('sprite',source['resource']))
    return sprites

def validate(files):
    references=check_references(files);sprites=item_sprites(files);textures=set();models=0
    for name,data in files.items():
        if not name.startswith('assets/z_pantera/models/item/') or not name.endswith('.json'):continue
        model=json.loads(data);models+=1;mapping=model.get('textures',{})
        def resolve(value):
            seen=set()
            while value.startswith('#'):
                assert value not in seen,'Cyclic texture alias';seen.add(value);value=mapping[value[1:]]
            return value if ':' in value else 'minecraft:'+value
        for value in mapping.values():
            value=resolve(value)
            assert value in sprites, f'{name}: {value} is not stitched in minecraft:items atlas'
            textures.add(value)
        for element in model.get('elements',[]):
            for face in element['faces'].values():
                assert resolve(face['texture']) in sprites
                box=face.get('uv',[])
                assert len(box)==4 and all(math.isfinite(x) and 0<=x<=16 for x in box),f'Invalid UV: {name}'
    assert models>=11,'Pantera model parts missing'
    return {'models_checked':models,'custom_sprites':sorted(textures),'item_atlas':str(ATLAS.relative_to(ROOT)),
            'all_model_textures_stitched':True,'references':references}

if __name__=='__main__':
    from build_pantera_pack import OUT
    print(json.dumps(validate(read_pack(OUT)),ensure_ascii=False,indent=2))
