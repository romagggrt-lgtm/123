#!/usr/bin/env python3
"""Original editable cuboid panther; export the same geometry to vanilla item models.

No entity texture replacements, overrides or shader modifications. Only z_pantera
is added to the exact pre-SmpLore 1.05 + Gold pack. Units: 16 per block, -Z front.
"""
from pathlib import Path
import base64, hashlib, io, json, math, shutil, uuid, zipfile
import xml.etree.ElementTree as E
import numpy as np
from PIL import Image, ImageDraw
from merge_smplore_pack import read_pack, check_references

ROOT = Path(__file__).resolve().parents[1]
MODULE = ROOT / 'pantera'
BASE = ROOT / 'dist/Z_Server_Pack_1.05_Gold.zip'
VERSION=E.parse(MODULE/'pom.xml').getroot().find('{http://maven.apache.org/POM/4.0.0}version').text
# 1.0.2 fixes server-side bone matrices only. Keep the working 1.0.1 RP byte-identical.
RESOURCE_VERSION='1.0.1'
OUT = ROOT / f'dist/Z_Server_Pack_1.05_Pantera_{RESOURCE_VERSION}.zip'
SIZE, TILE = 512, 64
FACES = ('north', 'south', 'east', 'west', 'up', 'down')


def uid(name): return str(uuid.uuid5(uuid.NAMESPACE_URL, 'z_pantera/' + name))
def encoded(im):
    out = io.BytesIO(); im.save(out, 'PNG', optimize=True); return out.getvalue()
def dump(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')
def uv(tile):
    x, y = tile % 8 * TILE, tile // 8 * TILE
    return [x, y, x + TILE, y + TILE]


def texture():
    im = Image.new('RGBA', (SIZE, SIZE), '#17191d')
    palettes = {0: (23, 25, 29), 1: (26, 28, 32), 2: (19, 21, 25), 3: (20, 22, 25),
                4: (29, 30, 35), 5: (7, 8, 11), 6: (43, 35, 45), 7: (114, 48, 137),
                8: (74, 37, 95), 9: (214, 168, 78)}
    rng = np.random.default_rng(1024)
    for t, color in palettes.items():
        # Coarse low-contrast rosettes + fine fur, without a noisy white silhouette.
        a = np.array(Image.new('RGB', (16, 16), color)).astype(float)
        a += rng.normal(0, 1.6, (16, 16, 1))
        for k in range(6):
            x, y = rng.integers(1, 13, 2)
            a[y:y+2, x:x+2] -= 3
        a += np.linspace(2, -2, 16)[None, :, None]
        tile = Image.fromarray(np.clip(a, 0, 255).astype('uint8')).resize((64, 64), Image.Resampling.NEAREST).convert('RGBA')
        im.paste(tile, (t % 8 * 64, t // 8 * 64))
    for t, blink in ((16, False), (17, True)):
        tile = im.crop((64, 0, 128, 64)); d = ImageDraw.Draw(tile)
        # Symmetric feline eyes; the muzzle is an independent 3D volume below.
        for x in (11, 38):
            d.polygon([(x-2, 25), (x+1, 20), (x+14, 22), (x+15, 30), (x+2, 32)], fill='#090b10')
            if not blink:
                d.polygon([(x, 25), (x+2, 23), (x+12, 24), (x+13, 29), (x+2, 30)], fill='#78c85a')
                d.rectangle((x+5, 23, x+7, 30), fill='#111a16')
                d.rectangle((x+2, 24, x+4, 26), fill='#b8ed88')
            else:
                d.line((x, 27, x+12, 28), fill='#486342', width=2)
        d.line((9, 23, 6, 19), fill='#08090c', width=2)
        d.line((53, 23, 56, 19), fill='#08090c', width=2)
        d.line((12, 21, 11, 18), fill='#08090c', width=2)
        d.line((50, 21, 51, 18), fill='#08090c', width=2)
        im.paste(tile, (t % 8 * 64, t // 8 * 64))
    d = ImageDraw.Draw(im)
    # Small highlights on the nose and bow; fur remains charcoal-black.
    d.rectangle((5*64+12, 14, 5*64+50, 19), fill='#14161b')
    d.line((7*64+6, 9, 7*64+54, 9), fill='#9d54b0', width=4)
    return im


def geometry():
    pivots = {'body': [0, 0, 0], 'head': [0, 17, -7],
              'front_left': [4.5, 11, -7], 'front_right': [-4.5, 11, -7],
              'rear_left': [4.5, 11, 8], 'rear_right': [-4.5, 11, 8],
              'tail_1': [0, 13, 11], 'tail_2': [0, 13, 20], 'tail_3': [0, 13, 28]}
    cubes = []
    def cube(bone, name, lo, hi, tile=0, face_tiles=None):
        tiles = {f: tile for f in FACES}; tiles.update(face_tiles or {})
        cubes.append(dict(name=name, bone=bone, start=lo, end=hi, tiles=tiles, uuid=uid(name)))
    cube('body', 'Torso', [-6.4, 9, -8], [6.4, 17.2, 11.8])
    cube('body', 'Shoulders', [-6, 10.5, -9], [6, 18, -1], 1)
    cube('body', 'Haunches', [-6, 9.5, 4], [6, 17, 12], 0)
    cube('body', 'Belly', [-4.8, 8, -5], [4.8, 11, 8], 2)
    cube('head', 'Neck', [-4.6, 12.5, -9.5], [4.6, 18.5, -3.5], 1)
    cube('head', 'Collar', [-5.5, 13.2, -14.65], [5.5, 13.85, -5.8], 8)
    cube('head', 'Charm', [-.65, 11.9, -14.8], [.65, 13.25, -14.4], 9)
    cube('head', 'Head', [-5.3, 13.5, -14.5], [5.3, 20.3, -6], 1, {'north': 16})
    cube('head', 'Muzzle', [-3.9, 13, -17], [3.9, 16.4, -13.8], 4)
    cube('head', 'Chin', [-3.3, 12.7, -16.5], [3.3, 13.6, -13.6], 2)
    cube('head', 'Nose', [-1.65, 15.1, -17.3], [1.65, 16.5, -16.8], 5)
    cube('head', 'Mouth', [-2.2, 13.65, -17.04], [2.2, 13.85, -17.01], 5)
    for sign, label in ((1, 'L'), (-1, 'R')):
        x = sign * 3.9
        cube('head', 'Ear_' + label, [x-1.35, 19.3, -10.5], [x+1.35, 22.2, -7.8], 2)
        cube('head', 'Ear_inner_' + label, [x-.8, 20, -10.56], [x+.8, 21.7, -10.51], 6)
    cube('head', 'Bow_left', [3.7, 20.1, -11], [5.5, 21.5, -10.4], 7)
    cube('head', 'Bow_right', [5.8, 20.1, -11], [7.2, 21.5, -10.4], 7)
    cube('head', 'Bow_knot', [5.2, 20.35, -11.25], [6.15, 21.25, -10.3], 8)
    for bone in ('front_left', 'front_right', 'rear_left', 'rear_right'):
        x, y, z = pivots[bone]
        rear = bone.startswith('rear')
        cube(bone, bone + '_upper', [x-1.85, 5.4, z-1.9], [x+1.85, 12.8, z+2.3], 0)
        cube(bone, bone + '_shin', [x-1.4, 1.4, z-1.5+(1 if rear else 0)], [x+1.4, 7, z+1.45+(1 if rear else 0)], 2)
        cube(bone, bone + '_paw', [x-1.8, 0, z-3], [x+1.8, 2, z+1.8], 2)
    cube('tail_1', 'Tail_base', [-1.25, 11.8, 10.5], [1.25, 14.2, 20.5], 3)
    cube('tail_2', 'Tail_middle', [-1.05, 11.95, 19.5], [1.05, 14.05, 28.5], 3)
    cube('tail_3', 'Tail_tip', [-.85, 12.1, 27.5], [.85, 13.9, 36], 2)
    return pivots, cubes


def animations(pivots):
    out = []
    for name, length, amp in (('idle', 4, 0), ('walk', .8, 25), ('run', .48, 43), ('tame', 1.2, 0)):
        animators = {}
        def keys(bone, axis, values):
            points = []
            for t, v in values:
                vec = dict(x='0', y='0', z='0'); vec[axis] = str(round(v, 4))
                points.append({'channel': 'rotation', 'data_points': [vec], 'uuid': uid(name+bone+str(t)),
                               'time': t, 'color': -1, 'interpolation': 'catmullrom'})
            animators[uid(bone)] = {'name': bone, 'type': 'bone', 'keyframes': points}
        for i, bone in enumerate(('front_left', 'front_right', 'rear_left', 'rear_right')):
            phase = 1 if i in (0, 3) else -1
            if amp: keys(bone, 'x', [(length*k/4, amp*phase*math.sin(k*math.pi/2)) for k in range(5)])
        for i, bone in enumerate(('tail_1', 'tail_2', 'tail_3')):
            keys(bone, 'y', [(length*k/8, (7+i*3)*math.sin(k*math.pi/4-i*.55)) for k in range(9)])
        keys('head', 'y', [(0, 0), (length*.25, 9), (length*.75, -9), (length, 0)])
        out.append({'uuid': uid('animation/'+name), 'name': name, 'loop': 'once' if name=='tame' else 'loop',
                    'override': False, 'length': length, 'snapping': 40, 'animators': animators})
    return out


def main():
    base = read_pack(BASE); files = dict(base)
    assert not any(n.startswith('assets/smplore/') for n in files)
    def write(name, obj): files[name] = (json.dumps(obj, ensure_ascii=False, indent=2)+'\n').encode()
    atlas = texture(); raw = encoded(atlas)
    (MODULE/'model/pantera.png').write_bytes(raw)
    # ItemDisplay models are baked against minecraft:items, whose directory source is item/.
    # entity/ PNGs exist on disk but are NOT automatically stitched into that atlas.
    files['assets/z_pantera/textures/item/pantera.png'] = raw
    pivots, cubes = geometry()
    for bone, pivot in pivots.items():
        elements = []
        for c in cubes:
            if c['bone'] != bone: continue
            elements.append({'name': c['name'], 'from': (np.array(c['start'])-pivot+8).tolist(),
                             'to': (np.array(c['end'])-pivot+8).tolist(),
                             'faces': {face: {'texture': '#pantera', 'uv': [v*16/SIZE for v in uv(tile)]}
                                       for face, tile in c['tiles'].items()}})
        model = {'credit': f'Original Z_Pantera {RESOURCE_VERSION} cuboid model, CC0-1.0',
                 'ambientocclusion': False, 'textures': {'pantera': 'z_pantera:item/pantera', 'particle':'z_pantera:item/pantera'}, 'elements': elements}
        write(f'assets/z_pantera/models/item/{bone}.json', model)
        if bone == 'head':
            blink = json.loads(json.dumps(model))
            next(e for e in blink['elements'] if e['name']=='Head')['faces']['north']['uv'] = [v*16/SIZE for v in uv(17)]
            write('assets/z_pantera/models/item/head_blink.json', blink)
    egg = Image.new('RGBA', (64,64)); d = ImageDraw.Draw(egg)
    d.polygon([(27,3),(37,3),(45,10),(53,27),(55,43),(51,54),(42,60),(22,60),(13,54),(9,43),(11,27),(19,10)], fill='#0e0f17')
    d.polygon([(15,22),(23,6),(29,5),(22,25),(17,44),(12,43)], fill='#373043')
    d.polygon([(35,8),(41,11),(43,18),(37,20),(32,16)], fill='#442753')
    d.polygon([(39,44),(47,39),(52,42),(49,51),(44,54)], fill='#442753')
    d.polygon([(20,32),(21,24),(27,29),(37,29),(43,24),(44,32),(42,45),(32,50),(22,45)], fill='#22242b')
    d.rectangle((23,34,28,37), fill='#82d168'); d.rectangle((36,34,41,37),fill='#82d168')
    d.rectangle((26,34,27,37),fill='#080c0a'); d.rectangle((37,34,38,37),fill='#080c0a')
    d.polygon([(29,41),(35,41),(32,44)],fill='#08090e')
    files['assets/z_pantera/textures/item/spawn_egg.png'] = encoded(egg)
    (MODULE/'model/spawn_egg.png').write_bytes(encoded(egg))
    write('assets/z_pantera/models/item/spawn_egg.json', {'parent':'minecraft:item/generated','textures':{'layer0':'z_pantera:item/spawn_egg'}})
    for item in (*pivots, 'head_blink', 'spawn_egg'):
        write(f'assets/z_pantera/items/{item}.json', {'model': {'type':'minecraft:model','model':f'z_pantera:item/{item}'}})
    files['assets/z_pantera/sounds/pantera.ogg'] = (MODULE/'assets-src/pantera.ogg').read_bytes()
    write('assets/z_pantera/sounds.json', {'pantera': {'sounds':[{'name':'z_pantera:pantera','stream':False,'attenuation_distance':24}]}})
    files['assets/z_pantera/LICENSE.txt'] = b'Original panther geometry, fur atlas, egg icon and animations: CC0-1.0.\n1024.MP3 supplied by the server owner; original sound converted to mono Vorbis, compressed and boosted for positional audio. No additional rights claimed for the supplied recording.\n'
    write('pack.mcmeta', {'pack': {'min_format':[75,0], 'max_format':[75,0],
                                 'description':f'Z_Server_Pack 1.05 + Gold + Pantera {RESOURCE_VERSION} | Shahed + Verity | 1.21.11'}})
    # Editable Blockbench free-model project, including real grouped cubes and UVs.
    elements = [{'name':c['name'],'type':'cube','uuid':c['uuid'],'from':c['start'],'to':c['end'],
                 'origin':pivots[c['bone']], 'rotation':[0,0,0], 'rescale':False, 'autouv':0,
                 'color':0,'visibility':True,'locked':False,'render_order':'default',
                 'faces':{f:{'uv':uv(t),'texture':0} for f,t in c['tiles'].items()}} for c in cubes]
    groups = {b:{'name':b,'origin':p,'rotation':[0,0,0],'uuid':uid(b),'export':True,'visibility':True,
                 'children':[c['uuid'] for c in cubes if c['bone']==b]} for b,p in pivots.items()}
    groups['tail_2']['children'].append(groups['tail_3']); groups['tail_1']['children'].append(groups['tail_2'])
    bb = {'meta':{'format_version':'4.10','model_format':'free','box_uv':False},'name':'Pantera',
          'model_identifier':'z_pantera','visible_box':[5,3,1], 'resolution':{'width':SIZE,'height':SIZE},
          'elements':elements, 'outliner':[g for b,g in groups.items() if b not in ('tail_2','tail_3')],
          'textures':[{'name':'pantera.png','id':'0','uuid':uid('texture'),'width':SIZE,'height':SIZE,
                       'uv_width':SIZE,'uv_height':SIZE,'source':'data:image/png;base64,'+base64.b64encode(raw).decode()}],
          'animations':animations(pivots)}
    dump(MODULE/'model/Pantera.bbmodel', bb)
    dump(MODULE/'model/animations.json', {'animations':bb['animations'],'units':'degrees, seconds','pivots':pivots})
    dump(MODULE/'model/geometry.json', {'pivots':pivots, 'cubes':cubes})
    dump(MODULE/'src/main/resources/rig.json', {'pivots':pivots})
    # No old asset is replaced; in particular no global vanilla paper/spawn egg overrides.
    for name,data in base.items():
        if name!='pack.mcmeta': assert files[name]==data, name
    counts = check_references(files)
    from check_pantera_pack import validate
    atlas_check=validate(files)
    with zipfile.ZipFile(OUT, 'w', zipfile.ZIP_DEFLATED) as archive:
        for name,data in sorted(files.items()):
            info=zipfile.ZipInfo(name,date_time=(2026,1,1,0,0,0)); info.compress_type=zipfile.ZIP_DEFLATED
            archive.writestr(info,data)
    assert read_pack(OUT)==files
    sha1=hashlib.sha1(OUT.read_bytes()).hexdigest()
    OUT.with_suffix('.zip.sha1').write_text(sha1+'\n')
    OUT.with_suffix('.zip.sha256').write_text(hashlib.sha256(OUT.read_bytes()).hexdigest()+'\n')
    shutil.copyfile(OUT, ROOT/'dist/Z_Pantera_ResourcePack.zip')
    report={'pack':OUT.name,'sha1':sha1,'size':OUT.stat().st_size,'base':BASE.name,
            'base_sha256':hashlib.sha256(BASE.read_bytes()).hexdigest(),'base_files_preserved':len(base)-1,
            'smplore_assets':0,'bones':len(pivots),'cubes':len(cubes),'validated':counts,
            'item_atlas':atlas_check,
            'sound_original_sha256':hashlib.sha256((MODULE/'assets-src/1024.MP3').read_bytes()).hexdigest(),
            'sound_ogg_sha256':hashlib.sha256((MODULE/'assets-src/pantera.ogg').read_bytes()).hexdigest(),
            'minecraft_client_tested':False}
    dump(OUT.with_suffix('.report.json'),report)
    print(json.dumps(report,ensure_ascii=False,indent=2))

if __name__=='__main__': main()
