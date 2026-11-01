#!/usr/bin/env python3
"""Create Z_AP GUI fonts and add them to the complete 1.10 server pack."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import json,zipfile,hashlib
root=Path(__file__).resolve().parents[1]
project=root/'ap' if (root/'ap/src').is_dir() else root
assets=project/'ui-assets';assets.mkdir(parents=True,exist_ok=True)
base=root/'dist/Z_Server_Pack_1.10.zip';output=root/'dist/Z_Server_Pack_1.11.zip'
S=4
font_regular='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
font_bold='/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
def font(size,bold=False):return ImageFont.truetype(font_bold if bold else font_regular,round(size*S))
def rect(draw,xy,fill,outline=None,r=0,width=1):
 box=tuple(round(v*S) for v in xy)
 if r:draw.rounded_rectangle(box,radius=r*S,fill=fill,outline=outline,width=width*S)
 else:draw.rectangle(box,fill=fill,outline=outline,width=width*S)
def text(draw,xy,value,size=4.5,color='#d9cafa',bold=False,anchor=None):draw.text(tuple(round(v*S) for v in xy),value,font=font(size,bold),fill=color,anchor=anchor,stroke_width=0)
def logo(draw,x,y,k=1):
 def poly(points,fill,outline=None):draw.polygon([(round((x+a*k)*S),round((y+b*k)*S)) for a,b in points],fill=fill,outline=outline,width=max(1,round(k*S/2)))
 poly([(0,3),(5,0),(10,3),(5,6)],'#c6a0ff','#f4ddff');poly([(0,3),(5,6),(5,12),(0,9)],'#6932d6','#c8a2ff');poly([(5,6),(10,3),(10,9),(5,12)],'#8c4bff','#e8cdff');poly([(3,4),(5,3),(7,4),(5,5)],'#ffffff')
def backdrop(width,height,title):
 im=Image.new('RGBA',(width*S,height*S),(8,10,27,255));d=ImageDraw.Draw(im)
 for y in range(height*S):
  t=y/(height*S);d.line((0,y,width*S,y),fill=(int(12-5*t),int(16-7*t),int(38-12*t),255))
 rect(d,(0,0,width-1,height-1),'#090b1e','#9467ff',r=3,width=1)
 rect(d,(2,2,width-3,15),'#0b0d21','#29213f',r=2)
 logo(d,6,3,.7);text(d,(17,5),'Z_AP',5.5,'#ede6ff',True);text(d,(34,5),'│  '+title,4.4,'#a59acb')
 return im,d
def slot_outline(d,x,y):rect(d,(x-1,y-1,x+16,y+16),'#11162a','#2f3057',r=2)
def punch_slots(im,slots):
 # Container labels are drawn after item stacks. Transparent windows let the
 # actual items and hover highlights remain visible and clickable.
 alpha=im.getchannel('A');d=ImageDraw.Draw(alpha)
 for x,y in slots:d.rectangle((x*S,y*S,(x+16)*S-1,(y+16)*S-1),fill=0)
 im.putalpha(alpha);return im

def inventory_slots(top):
 return [(8+18*c,top+18*r) for r in range(3) for c in range(9)]+[(8+18*c,top+58) for c in range(9)]

def make_home():
 im,d=backdrop(176,222,'ADMIN PANEL')
 rect(d,(3,17,48,126),'#090c1b','#292142',r=2)
 rect(d,(5,19,46,34),'#3c1cab','#9c70ff',r=2)
 for y,label in [(22,'Главная'),(40,'Игроки'),(58,'Телепорт'),(76,'Мир'),(94,'Настройки'),(112,'Закрыть')]:text(d,(24,y),label,4.1,'#ece4ff' if y==22 else '#aaa4c5',y==22)
 for row in range(3):
  y=18+row*36
  for col in range(3):
   x=51+col*36;rect(d,(x,y,x+35,y+34),'#101329','#504077',r=2)
 labels=[('РЕЖИМ','МОРФЫ','НАБОРЫ'),('НИК','НЕВИДИМКА','БАНЯ'),('ИГРОКИ','ТЕЛЕПОРТ','РЕГИОНЫ')]
 for row,arr in enumerate(labels):
  for col,label in enumerate(arr):text(d,(69+col*36,44+row*36),label,3.7,'#dad2ee',anchor='mm')
 rect(d,(3,129,86,216),'#0b0e20','#393058',r=2);rect(d,(89,129,172,216),'#0b0e20','#393058',r=2)
 text(d,(8,135),'ИНФОРМАЦИЯ',4.6,'#eee4ff',True);text(d,(8,150),'Профиль администратора',4.2,'#c1b3da')
 text(d,(8,161),'Мир, режим и онлайн:',4.1,'#9389ae');text(d,(8,170),'наведите на «Главная»',4.1,'#9389ae')
 text(d,(94,135),'ПОЛЕЗНЫЕ КОМАНДЫ',4.6,'#eee4ff',True);text(d,(94,151),'/ap  — открыть панель',4.1,'#b5a9ce')
 text(d,(94,163),'/ap lock  — закрыть',4.1,'#b5a9ce');text(d,(94,181),'●  Сессия защищена',4.0,'#ad8fff')
 text(d,(8,194),'Инвентарь доступен',3.8,'#807899');text(d,(8,204),'после закрытия меню',3.8,'#807899')
 indexes=[0,3,5,7,9,18,21,23,25,27,36,39,41,43,45]
 return punch_slots(im,[(8+18*(i%9),18+18*(i//9)) for i in indexes])
def make_generic(list_mode=False):
 im,d=backdrop(176,222,'')
 rect(d,(3,17,172,127),'#0c1023','#392d59',r=2)
 for row in range(6):
  for col in range(9):slot_outline(d,8+col*18,18+row*18)
 rect(d,(3,129,172,218),'#0b0e20','#393058',r=2)
 text(d,(9,137),'Z_AP  •  ADMIN PANEL',5.4,'#eee4ff',True)
 text(d,(9,154),'Выберите нужное действие выше.',4.5,'#bcb0d3')
 text(d,(9,166),'Наведите курсор на значок для подробностей.',4.1,'#968caf')
 text(d,(9,190),'Назад  •  Главная  •  Закрыть',4.1,'#aa93d3')
 text(d,(9,204),'Инвентарь доступен после закрытия меню',3.8,'#807899')
 visible=([10+9*r+c for r in range(4) for c in range(7)]+[45,46,47,48,49,50,52,53]) if list_mode else [4,19,20,21,22,23,24,25,29,31,33,45,49,53]
 return punch_slots(im,[(8+18*(i%9),18+18*(i//9)) for i in visible])
def make_modes():
 im,d=backdrop(176,222,'')
 rect(d,(3,17,172,127),'#0c1023','#392d59',r=2)
 for row,label in enumerate(('ВЫЖИВАНИЕ','ТВОРЧЕСКИЙ','ПРИКЛЮЧЕНИЕ','НАБЛЮДАТЕЛЬ')):
  y=35+row*18;rect(d,(8,y,168,y+17),'#12172e','#55427f',r=2);text(d,(48,y+6),label,4.5,'#e9dfff',True)
 for i in (45,49,53):slot_outline(d,8+18*(i%9),18+18*(i//9))
 rect(d,(3,129,172,218),'#0b0e20','#393058',r=2)
 text(d,(9,137),'РЕЖИМ ИГРЫ',5.2,'#eee4ff',True)
 text(d,(9,154),'Нажмите на нужный режим.',4.5,'#bcb0d3')
 text(d,(9,166),'Текущий режим выделен значком.',4.1,'#968caf')
 text(d,(9,202),'Инвентарь доступен после закрытия меню',3.8,'#807899')
 return punch_slots(im,[(8+18*(i%9),18+18*(i//9)) for i in (10,19,28,37,45,49,53)])
def make_anvil():
 im,d=backdrop(176,166,'')
 rect(d,(3,17,172,80),'#0c1023','#59407d',r=3)
 logo(d,10,22,1.6);text(d,(31,22),'Z_AP',7,'#f1e4ff',True);text(d,(31,32),'ADMIN PANEL',3.5,'#b89cdb')
 text(d,(9,43),'ВВЕДИТЕ ТЕКСТ',4.3,'#d5c4f3',True)
 rect(d,(60,21,169,38),'#0e1429','#9262f5',r=2)
 for x in [27,76,134]:slot_outline(d,x,47)
 text(d,(48,67),'НАЖМИТЕ БУМАГУ ДЛЯ ПОДТВЕРЖДЕНИЯ',3.5,'#b7a5d4')
 rect(d,(3,82,172,162),'#0b0e20','#393058',r=2)
 text(d,(10,91),'ЗАЩИЩЁННЫЙ ДОСТУП',5.1,'#e6dbfc',True)
 text(d,(10,108),'Введите текст в поле сверху.',4.5,'#b7a9d0')
 text(d,(10,120),'Затем нажмите бумагу слева.',4.5,'#b7a9d0')
 text(d,(10,144),'Инвентарь сохранён и доступен после закрытия.',3.9,'#817899')
 return punch_slots(im,[(27,47),(76,47),(134,47)])
def make_welcome():
 # User-supplied reference: crop away the reference's numbered heading while
 # keeping the original city, logo and welcome message.
 source=Image.open(assets/'welcome_reference.png').convert('RGB').crop((0,190,1058,850))
 im=source.resize((384,240),Image.Resampling.LANCZOS).convert('RGBA')
 mask=Image.new('L',im.size,0);md=ImageDraw.Draw(mask);md.rounded_rectangle((4,4,379,235),radius=15,fill=255);mask=mask.filter(ImageFilter.GaussianBlur(5));im.putalpha(mask);return im

home=make_home();home.save(assets/'home.png')
for i,opacity in enumerate((.42,.72,1.0)):
 im=home.copy();im.putalpha(im.getchannel('A').point(lambda a:round(a*opacity)));im.save(assets/f'home_{i}.png')
make_generic().save(assets/'generic.png');make_generic(True).save(assets/'generic_list.png');make_modes().save(assets/'modes.png');make_anvil().save(assets/'anvil.png');make_welcome().save(assets/'welcome.png')
# Minecraft stores each baked font glyph in a 256x256 atlas. Split the
# high-resolution artwork into 176x224 cells so it remains sharp and valid.
providers=[{'type':'space','advances':{'\ue000':-8,'\ue001':-136,'\ue002':-60,'\ue003':-136,'\ue004':-176,'\ue005':-1,'\ue006':-96,'\ue007':-50}}]
additions={}
for name,start_code,rows in [('home_0',0xe100,4),('home_1',0xe110,4),('home_2',0xe120,4),('generic',0xe130,4),('anvil',0xe140,3),('generic_list',0xe160,4),('modes',0xe170,4)]:
 im=Image.open(assets/f'{name}.png').convert('RGBA');canvas=Image.new('RGBA',(704,rows*224));canvas.alpha_composite(im,(0,0));im=canvas
 for row in range(rows):
  strip=im.crop((0,row*224,704,(row+1)*224));file=f'{name}_row{row}'
  # Keep the right edge of each cell non-zero so the advance is exactly 45px.
  for col in range(4):
   if strip.getpixel((col*176+175,0))[3]==0:strip.putpixel((col*176+175,0),(0,0,0,1))
  strip.save(assets/f'{file}.png')
  chars=''.join(chr(start_code+row*4+col) for col in range(4))
  providers.append({'type':'bitmap','file':f'z_ap:font/gui/{file}.png','height':56,'ascent':13-row*56,'chars':[chars]})
  additions[f'assets/z_ap/textures/font/gui/{file}.png']=(assets/f'{file}.png').read_bytes()
welcome=Image.open(assets/'welcome.png').convert('RGBA')
for row in range(2):
 strip=welcome.crop((0,row*120,384,(row+1)*120))
 for col in range(2):
  if strip.getpixel((col*192+191,0))[3]==0:strip.putpixel((col*192+191,0),(0,0,0,1))
 file=f'welcome_row{row}';strip.save(assets/f'{file}.png')
 chars=''.join(chr(0xe150+row*2+col) for col in range(2))
 providers.append({'type':'bitmap','file':f'z_ap:font/gui/{file}.png','height':30,'ascent':27-row*30,'chars':[chars]})
 additions[f'assets/z_ap/textures/font/gui/{file}.png']=(assets/f'{file}.png').read_bytes()
additions['assets/z_ap/font/gui.json']=json.dumps({'providers':providers},ensure_ascii=False,separators=(',',':')).encode()
import io
buf=io.BytesIO();Image.new('RGBA',(16,16),(0,0,0,0)).save(buf,format='PNG')
additions['assets/z_ap/textures/item/invisible.png']=buf.getvalue()
additions['assets/z_ap/models/item/invisible.json']=json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':'z_ap:item/invisible'}},separators=(',',':')).encode()
additions['assets/z_ap/items/invisible.json']=json.dumps({'model':{'type':'minecraft:model','model':'z_ap:item/invisible'}},separators=(',',':')).encode()
with zipfile.ZipFile(base) as old,zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as out:
 names=set(old.namelist());assert not names.intersection(additions)
 for info in old.infolist():
  data=old.read(info.filename)
  if info.filename=='pack.mcmeta':
   meta=json.loads(data);meta['pack']['description']='Z_Server_Pack 1.11 | Z_AP GUI + существующие плагины | 1.21.11';data=json.dumps(meta,ensure_ascii=False,separators=(',',':')).encode()
  out.writestr(info,data)
 for name,data in additions.items():
  info=zipfile.ZipInfo(name,(2020,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;out.writestr(info,data)
with zipfile.ZipFile(base) as old,zipfile.ZipFile(output) as new:
 assert all(old.read(n)==new.read(n) for n in old.namelist() if n!='pack.mcmeta');assert new.testzip() is None
for algo in ('sha1','sha256'):
 digest=hashlib.new(algo,output.read_bytes()).hexdigest();(output.parent/(output.name+'.'+algo)).write_text(digest+'\n');print(algo,digest)
