"""Lay out actual Blockbench screenshots; no generated stand-in renders."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

OUT=Path(__file__).resolve().parents[1]
P=OUT/'previews'
W,H=1760,1120
img=Image.new('RGB',(W,H),'#1b201f')
d=ImageDraw.Draw(img)
def font(size,bold=False):
    return ImageFont.truetype('C:/Windows/Fonts/'+('segoeuib.ttf' if bold else 'segoeui.ttf'),size)

d.text((60,38),'ХАРОН',font=font(56,True),fill='#e5dec9')
d.text((63,110),'ВАРИАНТ B  /  КАМЕНЬ И МОХ',font=font(22),fill='#a9b592')
d.text((1192,65),'2 × 2 × 3 блока',font=font(30,True),fill='#d6c08d')
d.line((60,157,1700,157),fill='#444a41',width=2)

def place(name,box):
    im=Image.open(P/(name+'.png')).convert('RGBA')
    im=im.crop(im.getbbox())
    x,y,w,h=box
    ratio=min(w/im.width,h/im.height)
    im=im.resize((round(im.width*ratio),round(im.height*ratio)),Image.Resampling.NEAREST)
    img.paste(im,(x+(w-im.width)//2,y+(h-im.height)//2),im)

place('hero',(60,196,805,817))
d.line((918,191,918,1017),fill='#3b433a')
place('front',(961,215,325,490))
place('back',(1324,215,374,490))
d.text((1032,731),'СПЕРЕДИ',font=font(21),fill='#aeb5aa')
d.text((1455,731),'СЗАДИ',font=font(21),fill='#aeb5aa')
d.line((964,785,1700,785),fill='#3b433a')
token=Image.open(OUT/'textures/charon_token.png').convert('RGBA').resize((160,160),Image.Resampling.NEAREST)
img.paste(token,(974,827),token)
d.text((1160,829),'Оригинальный жетон',font=font(25,True),fill='#d6c08d')
d.text((1160,872),'Текстура из Kingdoms',font=font(22),fill='#d5d8cc')
d.text((1160,907),'Объёмный пиксельный контур',font=font(22),fill='#aeb5aa')
d.text((1160,942),'Камень · трещины · мох',font=font(22),fill='#aeb5aa')
d.line((60,1044,1700,1044),fill='#444a41',width=2)
d.text((61,1068),'Реальные виды модели из Blockbench',font=font(21),fill='#939d91')
d.text((1133,1068),'BBMODEL  /  PNG  /  ASEPRITE',font=font(21),fill='#b0b69f')
img.save(P/'charon_statue_B_sheet.png')
print(P/'charon_statue_B_sheet.png')
