#!/usr/bin/env python3
"""Offline preview of actual exported item-model cubes + real UV texture.

This software rasterizer is not a Minecraft client test. Renders the same pivot
hierarchy as Rig.java; no replacement mesh or reference picture is composited.
"""
from pathlib import Path
import io,json,math,zipfile
import numpy as np
from PIL import Image,ImageDraw,ImageFont
from build_pantera_pack import OUT,VERSION

ROOT=Path(__file__).resolve().parents[1]
def translate(v):
    m=np.eye(4);m[:3,3]=v;return m
def rotate(axis,angle):
    a=math.radians(angle);c,s=math.cos(a),math.sin(a);m=np.eye(4)
    if axis=='x': m[1:3,1:3]=[[c,-s],[s,c]]
    else: m[[0,0,2,2],[0,2,0,2]]=[c,s,-s,c]
    return m
def pose(pivots,phase=0,seconds=0,motion=0,run=0):
    root=translate([0,abs(math.sin(phase*2))*.018*16*motion,0])@rotate('x',-4*run)
    out={'body':root,'head':root@translate(pivots['head'])@rotate('y',math.sin(seconds*1.6)*5)}
    for i,b in enumerate(('front_left','front_right','rear_left','rear_right')):
        sign=1 if i in (0,3) else -1
        out[b]=root@translate(pivots[b])@rotate('x',math.sin(phase)*sign*(25+18*run)*motion)
    chain=root@translate(pivots['tail_1'])
    for i in range(3):
        bone=f'tail_{i+1}'
        if i:chain=chain@translate(np.array(pivots[bone])-pivots[f'tail_{i}'])
        chain=chain@rotate('y',math.sin(seconds*2-i*.55)*(7+i*3))@rotate('x',-5-6*run if i==0 else 4+math.sin(seconds*2-i*.5)*2)
        out[bone]=chain.copy()
    return out
def quads(models,poses):
    for bone,model in models.items():
        for e in model['elements']:
            x0,y0,z0=np.array(e['from'])-8;x1,y1,z1=np.array(e['to'])-8
            corners={
                'north':[[x1,y1,z0],[x0,y1,z0],[x0,y0,z0],[x1,y0,z0]],
                'south':[[x0,y1,z1],[x1,y1,z1],[x1,y0,z1],[x0,y0,z1]],
                'east':[[x1,y1,z1],[x1,y1,z0],[x1,y0,z0],[x1,y0,z1]],
                'west':[[x0,y1,z0],[x0,y1,z1],[x0,y0,z1],[x0,y0,z0]],
                'up':[[x0,y1,z0],[x1,y1,z0],[x1,y1,z1],[x0,y1,z1]],
                'down':[[x0,y0,z1],[x1,y0,z1],[x1,y0,z0],[x0,y0,z0]]}
            for face,info in e['faces'].items():
                vertices=np.array(corners[face],float)
                # Reversing the winding supplies outward normals for backface culling.
                vertices=vertices[[1,0,3,2]]
                vertices=(np.c_[vertices,np.ones(4)]@poses[bone].T)[:,:3]
                u0,v0,u1,v1=np.array(info['uv'])/16
                yield vertices,np.array([[u1,v0],[u0,v0],[u0,v1],[u1,v1]])
def render(mesh,texture,yaw=40,pitch=17,width=720,height=420):
    yaw,pitch=np.radians([yaw,pitch])
    eye=np.array([np.sin(yaw)*np.cos(pitch),np.sin(pitch),-np.cos(yaw)*np.cos(pitch)])
    right=np.cross([0,1,0],eye);right/=np.linalg.norm(right);up=np.cross(eye,right)
    camera=np.array([right,up,eye]);scale=width/60
    yy,xx=np.mgrid[:height,:width]
    bg=np.clip(np.array([153,165,181])+(1-yy/height)[:,:,None]*[37,37,36],0,255).astype('uint8')
    depth=np.full((height,width),-np.inf)
    for points,uv in mesh:
        normal=np.cross(points[1]-points[0],points[2]-points[0]);norm=np.linalg.norm(normal)
        if norm<1e-9 or np.dot(normal,eye)<=0:continue
        normal/=norm
        light=.72+.28*max(0,float(np.dot(normal,np.array([-.35,.85,-.4]))))
        p=(points-[0,11,9])@camera.T
        p[:,0]=width/2+p[:,0]*scale;p[:,1]=height*.53-p[:,1]*scale
        for ids in ((0,1,2),(0,2,3)):
            tri,tuv=p[list(ids)],uv[list(ids)]
            xmin,ymin=np.maximum(0,np.floor(tri[:,:2].min(axis=0))).astype(int)
            xmax,ymax=np.minimum([width-1,height-1],np.ceil(tri[:,:2].max(axis=0))).astype(int)
            if xmin>xmax or ymin>ymax:continue
            yy,xx=np.mgrid[ymin:ymax+1,xmin:xmax+1].astype(float);xx+=.5;yy+=.5
            x0,y0=tri[0,:2];x1,y1=tri[1,:2];x2,y2=tri[2,:2]
            den=(y1-y2)*(x0-x2)+(x2-x1)*(y0-y2)
            if abs(den)<1e-10:continue
            w0=((y1-y2)*(xx-x2)+(x2-x1)*(yy-y2))/den
            w1=((y2-y0)*(xx-x2)+(x0-x2)*(yy-y2))/den;w2=1-w0-w1
            zz=w0*tri[0,2]+w1*tri[1,2]+w2*tri[2,2]
            uu=w0*tuv[0,0]+w1*tuv[1,0]+w2*tuv[2,0];vv=w0*tuv[0,1]+w1*tuv[1,1]+w2*tuv[2,1]
            tx=np.clip((uu*texture.shape[1]).astype(int),0,texture.shape[1]-1)
            ty=np.clip((vv*texture.shape[0]).astype(int),0,texture.shape[0]-1)
            mask=(w0>=-1e-8)&(w1>=-1e-8)&(w2>=-1e-8)&(zz>depth[ymin:ymax+1,xmin:xmax+1])&(texture[ty,tx,3]>=128)
            bg[ymin:ymax+1,xmin:xmax+1][mask]=(texture[ty[mask],tx[mask],:3]*light).astype('uint8')
            depth[ymin:ymax+1,xmin:xmax+1][mask]=zz[mask]
    assert np.isfinite(depth).sum()>3000
    return Image.fromarray(bg)
def main():
    pivots=json.loads((ROOT/'pantera/model/geometry.json').read_text())['pivots']
    with zipfile.ZipFile(OUT) as z:
        models={b:json.loads(z.read(f'assets/z_pantera/models/item/{b}.json')) for b in pivots}
        texture=np.array(Image.open(io.BytesIO(z.read('assets/z_pantera/textures/item/pantera.png'))))
    sheet=Image.new('RGB',(1440,928),'#242b38');draw=ImageDraw.Draw(sheet)
    font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',18)
    for i,(label,yaw,pitch,phase,motion,run) in enumerate([
        ('PANTERA / FRONT THREE-QUARTER',42,16,0,0,0),('FRONT / GREEN EYES + BOW',0,8,0,0,0),
        ('SIDE / LONG TAIL',90,10,0,0,0),('RUN / ARTICULATED PARTS',-50,18,math.pi/3,1,1)]):
        x,y=i%2*720,i//2*450+28
        sheet.paste(render(quads(models,pose(pivots,phase,.2,motion,run)),texture,yaw,pitch),(x,y))
        draw.text((x+16,y-24),label,fill='#dfc9e7',font=font)
    draw.text((16,908),f'Z_Pantera {VERSION} | actual exported geometry + UV atlas | offline preview, not a game screenshot',font=font,fill='#dfc9e7')
    sheet.save(ROOT/f'dist/Z_Pantera-preview_{VERSION}.png')
    frames=[]
    for i in range(24):
        t=i/24*.8
        frame=render(quads(models,pose(pivots,2*math.pi*i/24,t,1,0)),texture,42,16,width=600,height=360)
        ImageDraw.Draw(frame).text((12,12),'Z_Pantera / walk / offline model preview',font=font,fill='#343047')
        frames.append(frame)
    frames[0].save(ROOT/f'dist/Z_Pantera-walk_{VERSION}.gif',save_all=True,append_images=frames[1:],duration=33,loop=0)
    print(ROOT/f'dist/Z_Pantera-preview_{VERSION}.png')
if __name__=='__main__':main()
