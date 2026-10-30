from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
root=Path(__file__).resolve().parents[1];base=root/'lor/recipe-diagrams';assets=base/'assets';out=base/'готовые';out.mkdir(parents=True,exist_ok=True)
font='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf';bold='/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
F=lambda size:ImageFont.truetype(font,size);B=lambda size:ImageFont.truetype(bold,size)
def icon(name,size=48):return Image.open(assets/(name+'.png')).convert('RGBA').resize((size,size),Image.Resampling.NEAREST)
def bottle(form,colour,size=48):
 overlay=Image.open(assets/'potion_overlay.png').convert('RGBA');pix=overlay.load()
 for y in range(overlay.height):
  for x in range(overlay.width):
   r,g,b,a=pix[x,y];pix[x,y]=(r*colour[0]//255,g*colour[1]//255,b*colour[2]//255,a)
 glass=Image.open(assets/(form+'.png')).convert('RGBA');overlay.alpha_composite(glass);return overlay.resize((size,size),Image.Resampling.NEAREST)
def draw_text(draw,xy,text,font,fill='#e9e4f6'):draw.text(xy,text,font=font,fill=fill,stroke_width=0)
# Each recipe shows one ingredient and three examples; one item serves all three.
recipes=[
 ('01_awkward','Неловкое зелье','nether_wart','Адский нарост','Бутылочки воды','potion',(64,112,238),'Неловкое зелье','potion',(210,195,175)),
 ('02_levitation','Зелье левитации','shulker_shell','Панцирь шалкера','Неловкие зелья','potion',(210,195,175),'Левитация I • 10 сек.','potion',(198,168,236)),
 ('03_erosion_1','Зелье эрозии I','void_breath','Дыхание войда','Неловкие зелья','potion',(210,195,175),'Эрозия I • 20 сек.','potion',(74,20,94)),
 ('04_erosion_2','Зелье эрозии II','glowstone_dust','Светокаменная пыль','Зелья эрозии I','potion',(74,20,94),'Эрозия II • 20 сек.','potion',(27,8,36)),
 ('05_restoration','Зелье восстановления','ghast_tear','Слеза гаста','Зелья эрозии I','potion',(74,20,94),'Восстановление сердца','potion',(228,184,231)),
 ('06_long_levitation','Продление левитации','redstone','Редстоуновая пыль','Зелья левитации','potion',(198,168,236),'Левитация • 30 сек.*','potion',(198,168,236)),
 ('07_splash','Взрывная форма','gunpowder','Порох','Зелья эрозии I','potion',(74,20,94),'Взрывная эрозия I','splash_potion',(74,20,94)),
 ('08_lingering','Оседающая форма','dragon_breath','Обычное дыхание дракона','Взрывные зелья эрозии I','splash_potion',(74,20,94),'Оседающая эрозия I','lingering_potion',(74,20,94)),
]
for name,title,ingredient,ingredient_label,input_label,input_form,input_colour,output_label,output_form,output_colour in recipes:
 canvas=Image.new('RGBA',(1180,680),'#201e29');d=ImageDraw.Draw(canvas)
 d.rounded_rectangle((20,20,1160,660),18,fill='#2d2a37',outline='#655675',width=2)
 draw_text(d,(42,35),'ЗЕЛЬЕВАРЕНИЕ',B(22),'#c4a0f4');draw_text(d,(42,70),title,B(31),'#ffffff')
 gui=Image.open(assets/'brewing_stand.png').convert('RGBA').crop((0,0,176,166)).resize((528,498),Image.Resampling.NEAREST);canvas.alpha_composite(gui,(40,125))
 canvas.alpha_composite(icon('blaze_powder'),(40+17*3,125+17*3));canvas.alpha_composite(icon(ingredient),(40+79*3,125+17*3))
 for x,y in [(56,51),(79,58),(102,51)]:canvas.alpha_composite(bottle(input_form,input_colour),(40+x*3,125+y*3))
 draw_text(d,(610,142),'ИНГРЕДИЕНТ',B(19),'#c4a0f4');canvas.alpha_composite(icon(ingredient,56),(612,179));draw_text(d,(682,190),ingredient_label,F(22))
 draw_text(d,(610,268),'ИСХОДНЫЕ БУТЫЛОЧКИ',B(19),'#c4a0f4');draw_text(d,(610,300),input_label,F(21))
 d.line((615,365,1080,365),fill='#c4a0f4',width=4);d.polygon([(1080,353),(1100,365),(1080,377)],fill='#c4a0f4')
 draw_text(d,(610,400),'РЕЗУЛЬТАТ',B(19),'#c4a0f4')
 for i in range(3):canvas.alpha_composite(bottle(output_form,output_colour,64),(610+i*83,438))
 draw_text(d,(610,520),output_label,F(21));draw_text(d,(610,572),'Топливо: огненный порошок',F(18),'#ccc2d5')
 if name=='06_long_levitation':draw_text(d,(610,598),'*Длительность настраивается.',F(16),'#ccc2d5')
 canvas.convert('RGB').save(out/(name+'.png'),optimize=True)
print('Создано схем:',len(recipes))
