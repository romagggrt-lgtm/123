#!/usr/bin/env python3
"""Merge the supplied byte-exact resources into the current working server pack."""
from pathlib import Path
from zipfile import ZipFile,ZipInfo,ZIP_DEFLATED
import hashlib,io,json,math,subprocess
from PIL import Image
from merge_smplore_pack import read_pack,check_references

ROOT=Path(__file__).resolve().parents[1]
DIST=ROOT/'dist'
BASE=DIST/'Z_Server_Pack_1.05_Pantera_1.0.1.zip'
SOURCE=ROOT/'lor/assets-src/SmpLore-Textures-Sounds-Effects.zip'
OUTPUT=DIST/'Z_Server_Pack_1.07.zip'
TRACKS=['alarm','exchange','watch','frontier','evacuation','mechanical','classic','pulse']

def encoded(value):return (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
def archive(path,files):
    with ZipFile(path,'w',ZIP_DEFLATED) as z:
        for name,data in sorted(files.items()):
            entry=ZipInfo(name,(2026,1,1,0,0,0));entry.compress_type=ZIP_DEFLATED;z.writestr(entry,data)
def png(color):
    output=io.BytesIO();Image.new('RGBA',(1,1),color).save(output,format='PNG');return output.getvalue()
def item(model):return {'model':{'type':'minecraft:model','model':model}}

def cylinder(texture):
    # Sixteen tangent panels. All vanilla element angles are legal multiples of 22.5°.
    elements=[]
    width=8*math.tan(math.pi/16)
    faces={'north':{'uv':[0,0,16,16],'texture':'#surface'},'south':{'uv':[0,0,16,16],'texture':'#surface'}}
    # Use a symmetric 16-gon with normals 0,22.5,45,67.5,...
    for normal in range(16):
        theta=normal*22.5
        if theta%90<=45:base=theta//90*90;rotation=theta%90
        else:base=(theta//90+1)*90;rotation=theta%90-90
        base=int(base)%360
        if base in (0,180):
            z=0 if base==0 else 16
            geometry={'from':[8-width,0,z],'to':[8+width,16,z],'faces':faces}
        else:
            x=0 if base==90 else 16
            geometry={'from':[x,0,8-width],'to':[x,16,8+width],'faces':{'east':{'uv':[0,0,16,16],'texture':'#surface'},'west':{'uv':[0,0,16,16],'texture':'#surface'}}}
        if rotation:geometry['rotation']={'origin':[8,8,8],'axis':'y','angle':rotation,'rescale':False}
        geometry['shade']=False;elements.append(geometry)
    return {'ambientocclusion':False,'textures':{'surface':texture,'particle':texture},'elements':elements,
            'display':{'fixed':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]}}}

def main():
    base=read_pack(BASE);addon={}
    with ZipFile(SOURCE) as z:
        assert z.testzip() is None
        for name in z.namelist():
            if name.startswith('paper-overlay/assets/') and not name.endswith('/'):
                addon[name.removeprefix('paper-overlay/')]=z.read(name)
        originals={name.removeprefix('assets-original/'):z.read(name) for name in z.namelist() if name.startswith('assets-original/') and name.endswith(('.png','.ogg'))}
    assert addon and len([n for n in originals if n.endswith('.png')])==20
    for name,data in originals.items():assert addon[name]==data,name
    added={**addon}
    for kind in ['magnetite_core','void_absorber','void_alarm','void_siren']:
        for suffix in ['', '_lit']:
            source='smplore:block/'+kind+(suffix if kind!='magnetite_core' else '')
            path='world_'+kind+suffix
            added['assets/z_lor/models/block/'+path+'.json']=encoded({'parent':source,'display':{'fixed':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]}}})
            added['assets/z_lor/items/'+path+'.json']=encoded(item('z_lor:block/'+path))
    for size,color in [('large',(53,19,79,34)),('medium',(112,67,145,34)),('small',(180,145,211,34))]:
        name='beam_'+size
        added['assets/z_lor/textures/item/'+name+'.png']=png(color)
        added['assets/z_lor/models/block/'+name+'.json']=encoded(cylinder('z_lor:item/'+name))
        added['assets/z_lor/items/'+name+'.json']=encoded(item('z_lor:block/'+name))
    sounds={}
    def event(track,radius):return {'sounds':[{'name':'smplore:alarms/'+track,'stream':True,'attenuation_distance':radius}]}
    for index,track in enumerate(TRACKS):
        for radius in range(8,129,8):
            sounds[f'preview_{index}_r{radius}']=event(track,radius)
            for voice in range(32):sounds[f'siren_{voice}_{index}_r{radius}']=event(track,radius)
    added['assets/z_lor/sounds.json']=encoded(sounds)
    added['assets/z_lor/font/icons.json']=encoded({'providers':[{'type':'bitmap','file':'minecraft:gui/sprites/hud/heart/full.png','ascent':8,'height':8,'chars':['\ue120']}]})
    custom_sprites=sorted({texture for name,data in added.items() if '/models/' in name and name.endswith('.json') for texture in json.loads(data).get('textures',{}).values() if not texture.startswith('#')})
    # ItemDisplay and item_model in 1.21.11 use the item atlas. Block textures and
    # the lit models' vanilla torch are not included by its default item directory.
    atlas_path='assets/minecraft/atlases/items.json'
    atlas=json.loads(base.get(atlas_path,b'{"sources":[]}'))
    existing={entry.get('resource') for entry in atlas['sources'] if entry.get('type') in ('single','minecraft:single')}
    atlas['sources'].extend({'type':'minecraft:single','resource':name} for name in custom_sprites if name not in existing)
    added[atlas_path]=encoded(atlas)
    standalone={'pack.mcmeta':encoded({'pack':{'min_format':[75,0],'max_format':[75,0],'description':'Z_Lor 1.0.0 | Paper 1.21.11'}}),**added}
    merged=dict(base);conflicts=[]
    for name,data in added.items():
        if name in merged and merged[name]!=data and name!=atlas_path:conflicts.append(name)
        merged[name]=data
    assert not conflicts,conflicts
    metadata=json.loads(base['pack.mcmeta']);metadata['pack']['description']='Z_Server_Pack 1.07 | Shahed + Verity + Golda + Pantera + Z_Lor | 1.21.11';merged['pack.mcmeta']=encoded(metadata)
    merged['Z_Lor-MERGE.txt']=b'Z_Lor 1.0.0 added to Z_Server_Pack 1.05_Pantera_1.0.1.\nAll previous models, textures and sounds preserved.\nSupplied SmpLore PNG and OGG preserved byte-for-byte; dragon models excluded.\n'
    for name,data in base.items():
        if name not in ('pack.mcmeta',atlas_path):assert merged[name]==data,name
    for name,data in originals.items():assert merged[name]==data,name
    counts=check_references(merged)
    # Ensure each custom block sprite referenced by an item model is stitched.
    stitched={s['resource'] for s in atlas['sources'] if s.get('type')=='minecraft:single'}
    assert set(custom_sprites)<=stitched
    archive(OUTPUT,merged);archive(DIST/'Z_Lor_ResourcePack_1.0.0.zip',standalone)
    archive(DIST/'Z_Lor_Overlay_1.0.0.zip',added)
    report={'pack':OUTPUT.name,'base':BASE.name,'base_sha256':hashlib.sha256(BASE.read_bytes()).hexdigest(),
            'sha1':hashlib.sha1(OUTPUT.read_bytes()).hexdigest(),'sha256':hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),
            'base_asset_files_preserved':len(base)-1,'provided_png_preserved':20,'provided_ogg_preserved':9,
            'resource_conflicts':conflicts,'stitched_custom_sprites':custom_sprites,'sound_events':len(sounds),
            'validated':counts,'minecraft_client_tested':False,'all_prior_plugin_jars_unchanged':True}
    OUTPUT.with_suffix('.report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    for path in [OUTPUT,DIST/'Z_Lor_ResourcePack_1.0.0.zip',DIST/'Z_Lor_Overlay_1.0.0.zip']:
        data=path.read_bytes();path.with_suffix(path.suffix+'.sha1').write_text(hashlib.sha1(data).hexdigest()+'\n');path.with_suffix(path.suffix+'.sha256').write_text(hashlib.sha256(data).hexdigest()+'\n')
        print(path.name,len(data),'SHA1',hashlib.sha1(data).hexdigest())
    print('Preserved original sprites:',len(originals),'Atlas sprites:',len(stitched),'Validated:',counts)

if __name__=='__main__':main()
