"""Vanilla composite item models: a closed 642-cell 3D honeycomb, no shaders."""
import io, json, math
from collections import defaultdict
from PIL import Image, ImageDraw, ImageFilter

def encode(value):return (json.dumps(value,separators=(',',':'))+'\n').encode()
def png(image):
    output=io.BytesIO();image.save(output,format='PNG');return output.getvalue()
def unit(v):
    length=math.sqrt(sum(x*x for x in v));return tuple(x/length for x in v)
def dot(a,b):return sum(x*y for x,y in zip(a,b))
def geodesic():
    t=(1+math.sqrt(5))/2
    vertices=[unit(v) for v in [(-1,t,0),(1,t,0),(-1,-t,0),(1,-t,0),(0,-1,t),(0,1,t),(0,-1,-t),(0,1,-t),(t,0,-1),(t,0,1),(-t,0,-1),(-t,0,1)]]
    faces=[(0,11,5),(0,5,1),(0,1,7),(0,7,10),(0,10,11),(1,5,9),(5,11,4),(11,10,2),(10,7,6),(7,1,8),(3,9,4),(3,4,2),(3,2,6),(3,6,8),(3,8,9),(4,9,5),(2,4,11),(6,2,10),(8,6,7),(9,8,1)]
    for _ in range(3):
        mids={};next_faces=[]
        def middle(a,b):
            key=tuple(sorted((a,b)))
            if key not in mids:mids[key]=len(vertices);vertices.append(unit(tuple(vertices[a][i]+vertices[b][i] for i in range(3))))
            return mids[key]
        for x,y,z in faces:
            a,b,c=middle(x,y),middle(y,z),middle(z,x)
            next_faces.extend([(x,a,c),(y,b,a),(z,c,b),(a,b,c)])
        faces=next_faces
    cells=defaultdict(list)
    for face in faces:
        centre=unit(tuple(sum(vertices[i][axis] for i in face) for axis in range(3)))
        for i in face:cells[i].append(centre)
    return vertices,cells

def quad(texture):
    return {'ambientocclusion':False,'textures':{'surface':texture,'particle':texture},'elements':[{'from':[0,0,8],'to':[16,16,8],'shade':False,'light_emission':15,'faces':{'north':{'uv':[16,0,0,16],'texture':'#surface','tintindex':0},'south':{'uv':[0,0,16,16],'texture':'#surface','tintindex':0}}}], 'display':{'fixed':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]}}}

def generate(added):
    vertices,cells=geodesic();assert len(cells)==642
    assert sum(len(cell)==5 for cell in cells.values())==12
    model_ids=[];thresholds=[]
    for index,normal in enumerate(vertices):
        # ItemTransform uses quaternion.rotationXYZ: derive that exact local basis.
        ry=math.asin(max(-1,min(1,normal[0])));rx=math.atan2(-normal[1],normal[2])
        u=(math.cos(ry),math.sin(rx)*math.sin(ry),-math.cos(rx)*math.sin(ry));v=(0,math.cos(rx),math.sin(rx))
        points=sorted([(dot(p,u),dot(p,v)) for p in cells[index]],key=lambda p:math.atan2(p[1],p[0]))
        lo=[min(p[i] for p in points) for i in range(2)];hi=[max(p[i] for p in points) for i in range(2)];span=[hi[i]-lo[i] for i in range(2)]
        centre=tuple(normal[a]*sum(dot(p,normal) for p in cells[index])/len(cells[index])+u[a]*(lo[0]+hi[0])/2+v[a]*(lo[1]+hi[1])/2 for a in range(3))
        size=128;image=Image.new('RGBA',(size,size),(255,255,255,0));draw=ImageDraw.Draw(image)
        polygon=[((p[0]-lo[0])/span[0]*(size-5)+2,(1-(p[1]-lo[1])/span[1])*(size-5)+2) for p in points]
        draw.polygon(polygon,fill=(255,255,255,7));draw.line(polygon+[polygon[0]],fill=(255,255,255,210),width=3,joint='curve')
        name=f'dome/cell_{index:03d}';texture='z_lor:item/'+name
        added['assets/z_lor/textures/item/'+name+'.png']=png(image)
        model=quad(texture);model['display']['fixed']={'rotation':[math.degrees(rx),math.degrees(ry),0],'translation':[x*16 for x in centre],'scale':[span[0],span[1],1]}
        added['assets/z_lor/models/item/'+name+'.json']=encode(model);model_ids.append('z_lor:item/'+name)
        thresholds.append(((1-normal[1])/2,(((index*337+131)%643)+.5)/644))
    for color,value in [('purple',0xB39AF5),('green',0x83F0A2),('yellow',0xFFE395)]:
        children=[]
        for index,model in enumerate(model_ids):
            opening,fade=thresholds[index]
            leaf={'type':'minecraft:model','model':model,'tints':[{'type':'minecraft:constant','value':value}]}
            disappearing={'type':'minecraft:range_dispatch','property':'minecraft:custom_model_data','index':1,'fallback':{'type':'minecraft:empty'},'entries':[{'threshold':fade,'model':leaf}]}
            revealing={'type':'minecraft:range_dispatch','property':'minecraft:custom_model_data','index':0,'fallback':{'type':'minecraft:empty'},'entries':[{'threshold':opening,'model':disappearing}]}
            children.append({'type':'minecraft:condition','property':'minecraft:custom_model_data','index':index,'on_true':revealing,'on_false':{'type':'minecraft:empty'}})
        added['assets/z_lor/items/dome_'+color+'.json']=encode({'model':{'type':'minecraft:composite','models':children}})
    grid=Image.new('RGBA',(128,128),(125,91,210,4));draw=ImageDraw.Draw(grid)
    for axis in range(0,128,16):draw.line((axis,0,axis,127),fill=(169,127,245,125),width=1);draw.line((0,axis,127,axis),fill=(169,127,245,125),width=1)
    added['assets/z_lor/textures/item/void_grid.png']=png(grid)
    added['assets/z_lor/models/item/void_grid.json']=encode(quad('z_lor:item/void_grid'))
    added['assets/z_lor/items/void_grid.json']=encode({'model':{'type':'minecraft:model','model':'z_lor:item/void_grid','tints':[{'type':'minecraft:constant','value':0xFFFFFF}]}})
    ring=Image.new('RGBA',(128,128),(255,255,255,0));draw=ImageDraw.Draw(ring)
    draw.ellipse((3,3,124,124),fill=(184,129,255,10),outline=(211,170,255,230),width=4)
    draw.ellipse((12,12,115,115),outline=(150,89,220,125),width=1)
    draw.line((56,64,72,64),fill=(227,200,255,160),width=2);draw.line((64,56,64,72),fill=(227,200,255,160),width=2)
    added['assets/z_lor/textures/item/beam_warning.png']=png(ring)
    added['assets/z_lor/models/item/beam_warning.json']=encode(quad('z_lor:item/beam_warning'))
    added['assets/z_lor/items/beam_warning.json']=encode({'model':{'type':'minecraft:model','model':'z_lor:item/beam_warning','tints':[{'type':'minecraft:constant','value':0xFFFFFF}]}})
    return {'cells':len(cells),'pentagons':12,'hexagons':630,'centres':vertices,'opening_seconds':3,'dissolve_seconds':30}

def beam_texture(rgb,alpha_level,frame=0):
    # Static item-atlas sprites; the server selects eight flow frames. This
    # avoids animated block-sprite sampling in the 1.21.11 item render layer.
    size=32;image=Image.new('RGBA',(size,size))
    for y in range(size):
        flow=(y-frame*4)%size;streak=math.exp(-((flow-6)/2.2)**2)
        for x in range(size):
            edge=math.exp(-((min(x,size-1-x))/2.3)**2);pulse=.5+.5*math.sin((x*2+y-frame*4)*math.pi/16)
            alpha=int(min(235,28+145*edge+52*streak+10*pulse)*alpha_level)
            bright=.88+.12*streak
            image.putpixel((x,y),tuple(int(min(255,c*bright+28*streak)) for c in rgb)+(alpha,))
    return png(image)
