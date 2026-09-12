"""Reproducible cuboid geometry and pixel-painted UVs for Charon concept B.

Run with Python and Pillow. The original token is copied byte-for-byte.
Coordinates: 16 units = 1 block; front = -Z; ground = Y0.
"""
from pathlib import Path
from PIL import Image, ImageDraw
import hashlib
import json
import math
import random
import shutil

OUT = Path(__file__).resolve().parents[1]
ROOT = OUT.parents[1]
TEX = OUT / 'textures'
TEX.mkdir(exist_ok=True)
CUBES = []


def cube(group, name, a, b, mat='stone', moss=0, rotation=None, origin=None):
    item = dict(name=name, group=group, **{'from':a, 'to':b}, mat=mat, moss=moss)
    if rotation:
        item.update(rotation=rotation, origin=origin or [(a[i]+b[i])/2 for i in range(3)])
    CUBES.append(item)
    return item


# Broad low 2 x 2 block plinth, divided into four carefully fitted top slabs.
cube('01_Pedestal', 'foundation_lower', [-16,0,-16],[16,1.25,16], 'base', .15)
cube('01_Pedestal', 'foundation_recess', [-15.65,1.25,-15.65],[15.65,3.75,15.65], 'base', .32)
for xi,(x0,x1) in enumerate([(-16,-.12),(.12,16)]):
    for zi,(z0,z1) in enumerate([(-16,-.12),(.12,16)]):
        cube('01_Pedestal',f'top_slab_{xi}_{zi}',[x0,3.75,z0],[x1,5.25,z1],'stone',.22)
for side,z in [('front',-15.8),('back',15.45)]:
    for i,x in enumerate([-11.75,-3.75,4.25,12.25]):
        cube('01_Pedestal',f'{side}_masonry_{i}',[x-3.7,1.45,z],[x+3.7,3.55,z+.35],'base',.25)

# Heavy carved robe: taper, asymmetrical folds, a readable hem and back.
cube('02_Robe', 'robe_hem',[-9.75,5.25,-5.5],[9.25,10.5,9.25],'robe',.38)
cube('02_Robe', 'robe_lower',[-8.7,10.5,-4.7],[8.5,18.5,8.65],'robe',.18)
cube('02_Robe', 'robe_waist',[-7.75,18.5,-4],[7.4,26,7.65],'robe',.12)
cube('02_Robe', 'robe_chest',[-8.1,26,-3.6],[8.0,33.5,7.0],'stone',.10)
for i,(x,w,top,z,lean) in enumerate([(-8.35,2.5,25,-5.9,-6),(-5.0,2.15,30,-6.3,-3),(-1.55,2.25,32,-6.1,1),(2.25,2.1,29,-5.7,3),(5.8,2.35,26,-5.25,7)]):
    cube('02_Robe',f'front_fold_{i}_foot',[x,5.3,z-1.15],[x+w,10.5,z+1.4],'stone',.55 if i in [0,3] else .10)
    cube('02_Robe',f'front_fold_{i}_long',[x+.25,10.2,z-.7],[x+w-.2,top,z+1.15],'stone',.23 if i==0 else .06, [0,0,lean])
    cube('02_Robe',f'fold_chipped_hem_{i}',[x+.3,5.25,z-1.5],[x+w-.4,6.2,z+.1],'edge',.22)
for i,x in enumerate([-7.5,-3.8,.2,4.5,7.1]):
    cube('02_Robe',f'back_fold_{i}',[x,6,7.0],[x+1.65,29-(i%2)*3,9.15],'robe',.18)
for s in [-1,1]:
    x=s*8.6
    cube('02_Robe',f'side_fall_{s}',[x-1,6,0],[x+1,23,6.9],'stone',.25,[0,0,s*4])
cube('02_Robe','lower_belt_shadow',[-7.9,23.5,-4.35],[7.7,25.25,7.8],'darkstone',.08)
cube('02_Robe','belt_stone_front',[-5.75,24,-4.85],[5.9,25.2,-4.3],'stone',.10)

# Mantle, broad shoulders and an open hanging V collar.
cube('03_Mantle','back_hunched_mantle',[-8.9,31,-.5],[8.2,36,8.6],'stone',.28)
cube('03_Mantle','left_shoulder',[-11.45,29.8,-3.8],[-5.3,35.2,6.75],'stone',.42,[0,0,-12])
cube('03_Mantle','right_shoulder',[5.0,29.7,-3.7],[10.9,34.5,6.7],'stone',.14,[0,0,13])
cube('03_Mantle','collar_left_slope',[-6.6,29.4,-6.25],[-3.8,36,-3.25],'edge',.30,[0,0,28])
cube('03_Mantle','collar_right_slope',[3.1,29.4,-6.25],[5.9,36,-3.25],'edge',.10,[0,0,-28])
cube('03_Mantle','collar_lower_point',[-2.8,28,-6.9],[2.2,30.8,-3.8],'stone',.12)
cube('03_Mantle','collar_inner_recess',[-4.4,30.2,-3.75],[3.8,34.5,-1.25],'darkstone')

# Deep hood with a hollow front, pitched stone rim and an irregular crown.
cube('04_Hood','hood_back',[-5.5,36.3,3.5],[5.1,44.3,8.15],'stone',.37)
cube('04_Hood','hood_back_crown_fill',[-4.4,44.3,2.8],[3.8,45.9,6.8],'stone',.35)
cube('04_Hood','hood_back_lower',[-6.2,34.5,2.9],[5.9,39.5,7.8],'stone',.26)
cube('04_Hood','hood_left_wall',[-7.1,35.4,-4.9],[-4.6,42.8,5.8],'stone',.42)
cube('04_Hood','hood_right_wall',[4.0,35.4,-4.9],[6.5,42.8,5.8],'stone',.14)
cube('04_Hood','hood_left_pitch',[-6.65,41.2,-4.8],[-3.7,46.5,5.5],'stone',.52,[0,0,-28])
cube('04_Hood','hood_right_pitch',[3.1,41.2,-4.8],[6.05,46.5,5.5],'stone',.12,[0,0,28])
cube('04_Hood','hood_crown',[-3.9,45.2,-4.6],[3.3,47.35,5.9],'stone',.38)
cube('04_Hood','hood_crown_keystone',[-2.8,47.35,-2.0],[1.5,48,4.0],'edge',.28)
cube('04_Hood','hood_brow_overhang',[-3.9,44.7,-6.3],[3.3,46.35,-3.8],'edge',.26)
cube('04_Hood','hood_rim_left',[-6.85,35.8,-5.8],[-5.1,41.7,-4.6],'edge',.38)
cube('04_Hood','hood_rim_right',[4.6,35.8,-5.8],[6.35,41.7,-4.6],'edge',.14)
cube('04_Hood','hood_inner_darkness',[-4.7,35.1,1.0],[4.0,44.0,3.6],'void')
cube('04_Hood','hidden_face',[-2.8,36.5,-.65],[2.1,42.3,1.05],'face')
cube('04_Hood','subtle_nose',[-.8,37.6,-1.7],[.15,40,-.55],'face')
cube('04_Hood','face_chin',[-1.9,35.6,-.4],[1.4,37.2,1.4],'face')
# Lean the complete hood forward over the mantle, including its rotation pivots.
for part in CUBES:
    if part['group']=='04_Hood':
        part['from'][2]-=1.25
        part['to'][2]-=1.25
        if 'origin' in part:part['origin'][2]-=1.25

# One hand grips the oar, the other presents the existing Charon token.
cube('05_Arms','oar_upper_sleeve',[-11.4,25.5,-3.9],[-6.5,31.7,3.8],'stone',.32,[0,0,-8])
cube('05_Arms','oar_lower_sleeve',[-12.1,22,-6.7],[-7.2,27.6,1.1],'robe',.25)
cube('05_Arms','oar_cuff',[-12.65,24.2,-7.3],[-7.4,27.1,-3.6],'edge',.30)
cube('05_Arms','oar_grip_palm',[-12.9,24.4,-8.65],[-8.8,27.3,-6.5],'stone',.16)
for i in range(3):
    cube('05_Arms',f'oar_grip_finger_{i}',[-12.4+i*1.05,24.35,-9.15],[-11.6+i*1.05,26.85,-8.55],'edge',.10)
cube('05_Arms','oar_grip_thumb',[-9.9,26.5,-8.8],[-8.55,27.8,-6.6],'stone',.12)
cube('05_Arms','offering_upper_sleeve',[6.4,25,-2.8],[11.7,31.6,4.6],'stone',.15,[0,0,12])
cube('05_Arms','offering_elbow',[8.0,23.75,-3.3],[12.2,27.5,1.0],'robe',.12)
cube('05_Arms','offering_forearm',[8.0,25.2,-8.15],[12.3,29.05,-1.6],'stone',.14,[15,0,0])
cube('05_Arms','offering_cuff',[7.55,27.45,-8.95],[12.7,29.9,-6.1],'edge',.15)
cube('05_Arms','offering_palm',[7.0,28.65,-11.65],[14.25,30.15,-6.45],'stone',.12)
for i in range(4):
    cube('05_Arms',f'offering_finger_{i}',[7.3+i*1.7,29.4,-12.1],[8.65+i*1.7,30.55,-10.1],'edge',.05)
cube('05_Arms','offering_thumb',[6.65,29.3,-8.95],[8.15,31.1,-6.6],'stone',.12)

# A single broad paddle blade at the bottom, simple knob at the top.
cube('06_Oar','oar_shaft',[-11.55,8,-8.25],[-9.8,44.6,-6.5],'oar',.18)
cube('06_Oar','oar_handle_cap',[-11.85,43.2,-8.5],[-9.5,46.0,-6.25],'edge',.18)
cube('06_Oar','paddle_blade',[-13.8,6.0,-8.7],[-7.6,14.0,-6.0],'oar',.48)
cube('06_Oar','paddle_blade_shoulder',[-12.7,14.0,-8.6],[-8.7,16.0,-6.1],'oar',.30)
cube('06_Oar','paddle_tip',[-13.15,5.3,-8.6],[-8.25,6.0,-6.1],'edge',.50)
cube('06_Oar','paddle_raised_spine',[-11.35,6.7,-9.0],[-10.0,14.9,-8.65],'stone',.27)

# Sparse actual moss ledges accent the silhouette, with painted moss elsewhere.
for i,(x,y,z,w,d) in enumerate([(-12.8,5.25,-6.3,3,3.2),(-7.8,5.25,8.7,3,2),(-5.7,35.2,5.7,2.1,2),(-5.8,43.6,-2.5,1.5,2.3),(-2.8,47.35,1.1,1.8,1.5),(5.3,5.25,-3.5,2.4,2.7),(10.1,5.25,8.2,2.4,1.7)]):
    cube('07_Moss',f'moss_cushion_{i}',[x,y,z],[x+w,min(y+.3,48),z+d],'moss',.9)

# Original item silhouette extruded by opaque runs, with exact front/back UVs.
source = ROOT/'src/main/resources/assets/kingdoms/textures/item/charon_token.png'
shutil.copyfile(source,TEX/'charon_token.png')
token = Image.open(source).convert('RGBA')
pixel = .75
cx, bottom, zfront, thick = 10.0,30.55,-10.7,1.15
for row in range(16):
    col = 0
    while col < 16:
        if token.getpixel((col,row))[3] < 128:
            col += 1
            continue
        start=col
        while col<16 and token.getpixel((col,row))[3]>=128:
            col+=1
        a=[cx+(start-8.5)*pixel,bottom+(14-row)*pixel,zfront]
        b=[cx+(col-8.5)*pixel,bottom+(15-row)*pixel,zfront+thick]
        c=cube('08_Original_Charon_Token',f'token_row_{row:02d}',a,b,'bronze')
        c['token_uv']=[start,row,col,row+1]

ATLAS=512
layers={name:Image.new('RGBA',(ATLAS,ATLAS)) for name in ['Stone','Cracks','Moss']}
pal={
 'stone':(113,112,104),'robe':(89,91,85),'edge':(131,129,118),
 'base':(99,101,94),'darkstone':(71,75,70),'void':(15,19,18),
 'face':(28,34,30),'oar':(92,92,81),'moss':(69,85,42),'bronze':(111,81,44),
}
rects=[]
for ci,c in enumerate(CUBES):
    dx,dy,dz=[c['to'][i]-c['from'][i] for i in range(3)]
    c['faces']={}
    for face,(w,h) in {'north':(dx,dy),'south':(dx,dy),'east':(dz,dy),'west':(dz,dy),'up':(dx,dz),'down':(dx,dz)}.items():
        if 'token_uv' in c and face in ['north','south']:
            uv=c['token_uv'][:]
            if face=='south':uv=[uv[2],uv[1],uv[0],uv[3]]
            c['faces'][face]={'uv':uv,'tex':'token'}
        else:
            rects.append((max(1,round(w*2)),max(1,round(h*2)),ci,face))

px=py=shelf=0
for w,h,ci,face in sorted(rects,key=lambda r:(-r[1],-r[0])):
    if px+w+2>ATLAS: px=0;py+=shelf;shelf=0
    if py+h+2>ATLAS:raise ValueError('Atlas overflow')
    c=CUBES[ci]; x=px+1;y=py+1;px+=w+2;shelf=max(shelf,h+2)
    c['faces'][face]={'uv':[x,y,x+w,y+h],'tex':'stone'}
    seed=int(hashlib.sha256((c['name']+face).encode()).hexdigest()[:12],16)
    rng=random.Random(seed)
    base=pal[c['mat']]
    shade=rng.randint(-5,5)
    base=tuple(v+shade for v in base)
    patch=Image.new('RGBA',(w,h),(*base,255));draw=ImageDraw.Draw(patch)
    def tone(delta):return tuple(max(0,min(255,v+delta)) for v in base)+(255,)
    # Low contrast clustered pixels, avoiding uniform noise and photoreal grain.
    for _ in range(max(1,w*h//23)):
        xx=rng.randrange(w);yy=rng.randrange(h)
        ww=rng.randint(2,6);hh=rng.randint(1,5)
        draw.rectangle((xx,yy,min(w-1,xx+ww),min(h-1,yy+hh)),fill=tone(rng.choice([-10,-7,-4,4,7,9])))
    for _ in range(w*h//27):
        draw.point((rng.randrange(w),rng.randrange(h)),fill=tone(rng.choice([-14,12])))
    if w>=4 and h>=4 and c['mat'] not in ['void','face','bronze','moss']:
        draw.line((0,0,w-1,0),fill=tone(15))
        draw.line((0,0,0,h-1),fill=tone(7))
        draw.line((0,h-1,w-1,h-1),fill=tone(-13))
        draw.line((w-1,1,w-1,h-1),fill=tone(-8))
        for _ in range(max(1,w//12)):
            ex=rng.randrange(w);draw.point((ex,0),fill=tone(-9))
    layers['Stone'].paste(patch,(x,y))
    # Padding is copied from border pixels for stable sampling at edges.
    layers['Stone'].paste(patch.crop((0,0,w,1)),(x,y-1))
    layers['Stone'].paste(patch.crop((0,h-1,w,h)),(x,y+h))
    layers['Stone'].paste(patch.crop((0,0,1,h)),(x-1,y))
    layers['Stone'].paste(patch.crop((w-1,0,w,h)),(x+w,y))
    if w>=5 and h>=6 and c['mat'] not in ['bronze','face','void','moss'] and rng.random()<.34:
        d=ImageDraw.Draw(layers['Cracks']);xx=rng.randint(1,w-2);yy=rng.randint(0,max(0,h//3))
        for step in range(rng.randint(3,max(4,min(13,h)))):
            xx=max(1,min(w-2,xx+rng.choice([-1,0,0,1])))
            if yy>=h:break
            d.point((x+xx,y+yy),fill=(*tuple(max(0,v-32) for v in base),225))
            if xx+1<w:d.point((x+xx+1,y+yy),fill=(*tuple(min(255,v+8) for v in base),185))
            yy+=1
    density=c['moss']
    if face=='down':density=0
    if face=='up':density=min(1,density*1.3)
    if w>=2 and h>=2 and density>0:
        d=ImageDraw.Draw(layers['Moss'])
        centers=max(1,round(w*h*density/85))
        for _ in range(centers):
            mx=rng.randrange(w);my=rng.choice([rng.randrange(h),h-1,0])
            for _ in range(rng.randint(3,10)):
                mx=max(0,min(w-1,mx+rng.choice([-2,-1,0,1,2])))
                my=max(0,min(h-1,my+rng.choice([-1,0,1,2])))
                color=rng.choice([(47,61,35,255),(59,75,40,255),(73,89,45,255),(89,102,55,255),(106,114,68,255)])
                ww=rng.choice([1,1,2,3]);hh=rng.choice([1,2,3])
                d.rectangle((x+mx,y+my,x+min(w-1,mx+ww-1),y+min(h-1,my+hh-1)),fill=color)

atlas=Image.new('RGBA',(ATLAS,ATLAS))
for name,im in layers.items():
    im.save(TEX/f'charon_{name.lower()}_layer.png')
    atlas=Image.alpha_composite(atlas,im)
atlas.save(TEX/'charon_statue_atlas.png')
(OUT/'source/geometry.json').write_text(json.dumps({'name':'charon_statue_B','resolution':ATLAS,'cubes':CUBES},separators=(',',':')),encoding='utf-8')
print(json.dumps({'cubes':len(CUBES),'faces':len(CUBES)*6,'atlas':[ATLAS,ATLAS],'used_height':py+shelf,'token_sha256':hashlib.sha256(source.read_bytes()).hexdigest()},indent=2))
