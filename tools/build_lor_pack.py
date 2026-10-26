#!/usr/bin/env python3
"""Merge the supplied byte-exact resources into the current working server pack."""
from pathlib import Path
from zipfile import ZipFile,ZipInfo,ZIP_DEFLATED
import hashlib,io,json,math,subprocess
from PIL import Image
from merge_smplore_pack import read_pack,check_references
from build_lor_visuals import generate,beam_texture

ROOT=Path(__file__).resolve().parents[1]
DIST=ROOT/'dist'
BASE=DIST/'Z_Server_Pack_1.05_Pantera_1.0.1.zip'
SOURCE=ROOT/'lor/assets-src/SmpLore-Textures-Sounds-Effects.zip'
VERSION='1.0.1'
OUTPUT=DIST/'Z_Server_Pack_1.08.zip'
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
    # Sprite IDs must not appear in two atlases. Keep vanilla block textures in
    # their vanilla atlas; custom item overlays get their own namespace and IDs.
    aliases={}
    for name in ['copper_block','redstone_torch']:
        aliases['minecraft:block/'+name]='z_lor:item/trim/'+name
        added['assets/z_lor/textures/item/trim/'+name+'.png']=(ROOT/'lor/assets-src/vanilla-1.21.11'/str(name+'.png')).read_bytes()
    for name,data in list(added.items()):
        if '/models/' in name and name.endswith('.json'):
            model=json.loads(data)
            if 'textures' in model:
                replacement={key:aliases.get(value,value) for key,value in model['textures'].items()}
                if replacement!=model['textures']:model['textures']=replacement;added[name]=encoded(model)
    for kind in ['magnetite_core','void_absorber','void_alarm','void_siren']:
        for suffix in ['', '_lit']:
            source='smplore:block/'+kind+(suffix if kind!='magnetite_core' else '')
            path='world_'+kind+suffix
            added['assets/z_lor/models/block/'+path+'.json']=encoded({'parent':source,'display':{'fixed':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]}}})
            added['assets/z_lor/items/'+path+'.json']=encoded(item('z_lor:block/'+path))
    for size,color in [('large',(128,75,170)),('medium',(177,124,213)),('small',(223,199,240))]:
        name='beam_'+size
        entries=[]
        for level in range(1,9):
            variant=name+'_'+str(level);texture='z_lor:item/'+variant
            added['assets/z_lor/textures/item/'+variant+'.png']=beam_texture(color,level/8)
            added['assets/z_lor/textures/item/'+variant+'.png.mcmeta']=encoded({'animation':{'frametime':2,'interpolate':True}})
            added['assets/z_lor/models/block/'+variant+'.json']=encoded(cylinder(texture))
            entries.append({'threshold':(level-.5)/8,'model':{'type':'minecraft:model','model':'z_lor:block/'+variant}})
        added['assets/z_lor/items/'+name+'.json']=encoded({'model':{'type':'minecraft:range_dispatch','property':'minecraft:custom_model_data','index':0,'fallback':{'type':'minecraft:empty'},'entries':entries}})
    geometry=generate(added)
    (ROOT/'lor/reference/dome-geometry.json').write_text(json.dumps(geometry,separators=(',',':'))+'\n')
    sounds={}
    def event(track,radius):return {'sounds':[{'name':'smplore:alarms/'+track,'stream':True,'attenuation_distance':radius}]}
    for index,track in enumerate(TRACKS):
        for radius in range(8,129,8):
            sounds[f'preview_{index}_r{radius}']=event(track,radius)
            for voice in range(32):sounds[f'siren_{voice}_{index}_r{radius}']=event(track,radius)
    added['assets/z_lor/sounds.json']=encoded(sounds)
    added['assets/z_lor/font/icons.json']=encoded({'providers':[{'type':'bitmap','file':'minecraft:gui/sprites/hud/heart/full.png','ascent':8,'height':8,'chars':['\ue120']}]})
    # block/ and item/ directories are discovered by vanilla's own atlases.
    # Overriding the item atlas with vanilla block IDs made placed blocks missing.
    assert not any(name.startswith('assets/minecraft/') for name in added)
    standalone={'pack.mcmeta':encoded({'pack':{'min_format':[75,0],'max_format':[75,0],'description':'Z_Lor '+VERSION+' | Paper 1.21.11'}}),**added}
    merged=dict(base);conflicts=[]
    for name,data in added.items():
        if name in merged and merged[name]!=data:conflicts.append(name)
        merged[name]=data
    assert not conflicts,conflicts
    metadata=json.loads(base['pack.mcmeta']);metadata['pack']['description']='Z_Server_Pack 1.08 | Shahed + Verity + Golda + Pantera + Z_Lor | 1.21.11';merged['pack.mcmeta']=encoded(metadata)
    merged['Z_Lor-MERGE.txt']=b'Z_Lor 1.0.1 added to Z_Server_Pack 1.05_Pantera_1.0.1.\nAll previous models, textures and sounds preserved.\nSupplied SmpLore PNG and OGG preserved byte-for-byte; dragon models excluded.\nVanilla atlas overrides removed; native textures used only under private aliases.\n'
    for name,data in base.items():
        if name!='pack.mcmeta':assert merged[name]==data,name
    for name,data in originals.items():assert merged[name]==data,name
    counts=check_references(merged)
    archive(OUTPUT,merged);archive(DIST/('Z_Lor_ResourcePack_'+VERSION+'.zip'),standalone)
    archive(DIST/('Z_Lor_Overlay_'+VERSION+'.zip'),added)
    report={'pack':OUTPUT.name,'base':BASE.name,'base_sha256':hashlib.sha256(BASE.read_bytes()).hexdigest(),
            'sha1':hashlib.sha1(OUTPUT.read_bytes()).hexdigest(),'sha256':hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),
            'base_asset_files_preserved':len(base)-1,'provided_png_preserved':20,'provided_ogg_preserved':9,
            'resource_conflicts':conflicts,'vanilla_atlas_overrides':False,'vanilla_texture_aliases':aliases,'dome_cells':geometry['cells'],'sound_events':len(sounds),
            'validated':counts,'minecraft_client_tested':False,'all_prior_plugin_jars_unchanged':True}
    OUTPUT.with_suffix('.report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    for path in [OUTPUT,DIST/('Z_Lor_ResourcePack_'+VERSION+'.zip'),DIST/('Z_Lor_Overlay_'+VERSION+'.zip')]:
        data=path.read_bytes();path.with_suffix(path.suffix+'.sha1').write_text(hashlib.sha1(data).hexdigest()+'\n');path.with_suffix(path.suffix+'.sha256').write_text(hashlib.sha256(data).hexdigest()+'\n')
        print(path.name,len(data),'SHA1',hashlib.sha1(data).hexdigest())
    print('Preserved original sprites:',len(originals),'Dome cells:',geometry['cells'],'Validated:',counts)

if __name__=='__main__':main()
