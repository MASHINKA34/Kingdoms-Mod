"""Bake the approved Blockbench export into static NeoForge OBJ cell models.

Requires numpy. Does not re-draw or change the original textures.
Clips triangles/UVs at block boundaries and voxelizes the actual rotated
cuboids at 1/32 block resolution for local, directional collision shapes.
"""
from pathlib import Path
import base64
import itertools
import json
import math
import shutil
import struct
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT/'art/charon_statue'
ASSETS = ROOT/'src/main/resources/assets/kingdoms'
DEST = ASSETS/'models/block/charon_statue'
DEST.mkdir(parents=True,exist_ok=True)
g = json.loads((ART/'charon_statue_B.gltf').read_text())
buffers = [base64.b64decode(b['uri'].split(',')[1]) for b in g['buffers']]

def accessor(index):
    a=g['accessors'][index]; v=g['bufferViews'][a['bufferView']]
    count={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4}[a['type']]
    fmt={5123:'H',5125:'I',5126:'f'}[a['componentType']]
    size=struct.calcsize('<'+fmt*count)
    offset=v.get('byteOffset',0)+a.get('byteOffset',0)
    return np.array([struct.unpack_from('<'+fmt*count,buffers[v['buffer']],offset+i*v.get('byteStride',size)) for i in range(a['count'])])

def node_matrix(node):
    if 'matrix' in node:return np.array(node['matrix']).reshape(4,4).T
    x,y,z,w=node.get('rotation',[0,0,0,1])
    m=np.eye(4)
    m[:3,:3]=np.array([[1-2*y*y-2*z*z,2*x*y-2*z*w,2*x*z+2*y*w],
                         [2*x*y+2*z*w,1-2*x*x-2*z*z,2*y*z-2*x*w],
                         [2*x*z-2*y*w,2*y*z+2*x*w,1-2*x*x-2*y*y]])@np.diag(node.get('scale',[1,1,1]))
    m[:3,3]=node.get('translation',[0,0,0])
    return m

triangles=[]
def walk(index,parent):
    node=g['nodes'][index];m=parent@node_matrix(node)
    if 'mesh' in node:
        for prim in g['meshes'][node['mesh']]['primitives']:
            assert prim['mode']==4
            a=prim['attributes'];p=accessor(a['POSITION']);n=accessor(a['NORMAL']);uv=accessor(a['TEXCOORD_0'])
            p=(np.c_[p,np.ones(len(p))]@m.T)[:,:3]
            n=n@np.linalg.inv(m[:3,:3]);n/=np.linalg.norm(n,axis=1)[:,None]
            data=np.c_[p,n,uv]
            for idx in accessor(prim['indices']).astype(int).flatten().reshape(-1,3):
                tri=data[idx].copy()
                assert np.dot(np.cross(tri[1,:3]-tri[0,:3],tri[2,:3]-tri[0,:3]),tri[0,3:6])>0
                triangles.append((int(prim['material']),tri))
    for child in node.get('children',[]):walk(child,m)
for root in g['scenes'][g.get('scene',0)]['nodes']:walk(root,np.eye(4))

def clip(poly,axis,bound,keep_above):
    result=[]
    for i,a in enumerate(poly):
        b=poly[(i+1)%len(poly)]
        da=(a[axis]-bound)*(1 if keep_above else -1)
        db=(b[axis]-bound)*(1 if keep_above else -1)
        ia=da>=-1e-8; ib=db>=-1e-8
        if ia:result.append(a)
        if ia!=ib:result.append(a+(b-a)*(da/(da-db)))
    return result

def write_obj(path,polygons,offset):
    lines=['# Generated from the approved Charon statue B; positions are in blocks.',
           'mtllib kingdoms:models/block/charon_statue/charon_statue.mtl','s off']
    vertex=0;previous=None;total=0
    for mat,poly in polygons:
        if mat!=previous:lines.append('usemtl '+('stone' if mat==0 else 'token'));previous=mat
        for i in range(1,len(poly)-1):
            tri=np.array([poly[0],poly[i],poly[i+1]])
            # The game bakes float32 vertices. Drop clipping slivers that collapse at
            # that precision instead of sending zero-area faces to the OBJ baker.
            final=(tri[:,:3]+offset).astype(np.float32)
            cross=np.cross(final[1]-final[0],final[2]-final[0])
            if np.dot(cross,tri[0,3:6])<=1e-7:continue
            for row in tri:
                pos=row[:3]+offset
                lines.append('v '+' '.join(f'{v:.8f}' for v in pos))
                lines.append('vt '+' '.join(f'{v:.8f}' for v in row[6:8]))
                lines.append('vn '+' '.join(f'{v:.8f}' for v in row[3:6]))
            lines.append('f '+' '.join(f'{k}/{k}/{k}' for k in range(vertex+1,vertex+4)))
            vertex+=3;total+=1
    path.write_text('\n'.join(lines)+'\n',encoding='utf-8')
    return total

(DEST/'charon_statue.mtl').write_text('''newmtl stone
Ka 0 0 0
Kd 1 1 1
map_Kd kingdoms:block/charon_statue_atlas

newmtl token
Ka 0 0 0
Kd 1 1 1
map_Kd kingdoms:item/charon_token
''',encoding='utf-8')

def dump(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def model(name):
    return {'loader':'neoforge:obj','model':'kingdoms:models/block/charon_statue/'+name+'.obj',
            'automatic_culling':False,'shade_quads':True,'flip_v':False,'emissive_ambient':False,
            'ambientocclusion':False,'render_type':'minecraft:cutout',
            'textures':{'particle':'kingdoms:block/charon_statue_atlas'}}

shift=np.array([1.,0.,1.]);counts={};variants={}
for x,y,z in itertools.product(range(2),range(3),range(2)):
    cell=np.array([x,y,z]);polys=[]
    for mat,tri in triangles:
        t=tri.copy();t[:,:3]+=shift
        mn=t[:,:3].min(axis=0);mx=t[:,:3].max(axis=0)
        if any(mx[a]<cell[a]-1e-8 or mn[a]>cell[a]+1+1e-8 for a in range(3)):continue
        # Coplanar faces belong to the block behind their outward normal.
        if any(abs(mx[a]-mn[a])<1e-8 and abs(mn[a]-round(mn[a]))<1e-8
               and math.floor(mn[a]-t[0,3+a]*1e-6)!=cell[a] for a in range(3)):continue
        p=list(t)
        for a in range(3):
            p=clip(p,a,cell[a],True) if p else []
            p=clip(p,a,cell[a]+1,False) if p else []
        if len(p)>=3:polys.append((mat,p))
    name=f'part_{x}_{y}_{z}'
    counts[name]=write_obj(DEST/(name+'.obj'),polys,-cell)
    assert counts[name]>0,name
    dump(DEST/(name+'.json'),model(name))
    for facing,angle in [('north',0),('east',90),('south',180),('west',270)]:
        variants[f'facing={facing},part_x={x},layer={y},part_z={z}']={'model':'kingdoms:block/charon_statue/'+name,'y':angle}
dump(ASSETS/'blockstates/charon_statue.json',{'variants':variants})

write_obj(DEST/'inventory.obj',triangles,np.array([.5,-1.,.5]))
item=model('inventory');item['gui_light']='side'
def display(rotation,scale,translation=[0,0,0]):return {'rotation':rotation,'translation':translation,'scale':[scale]*3}
item['display']={
    'gui':display([25,145,0],.25,[0,1,0]),
    'ground':display([0,0,0],.22,[0,3,0]),
    'fixed':display([0,180,0],.29),
    'thirdperson_righthand':display([75,45,0],.19,[0,2.5,0]),
    'thirdperson_lefthand':display([75,45,0],.19,[0,2.5,0]),
    'firstperson_righthand':display([0,145,0],.28,[0,2,0]),
    'firstperson_lefthand':display([0,225,0],.28,[0,2,0]),
    'head':display([0,180,0],.35,[0,10,0])}
dump(ASSETS/'models/item/charon_statue.json',item)
shutil.copyfile(ART/'textures/charon_statue_atlas.png',ASSETS/'textures/block/charon_statue_atlas.png')

# Collision uses the very same cuboids, including both X and Z rotations.
bb=json.loads((ART/'charon_statue_B.bbmodel').read_text())
occupied=np.zeros((64,96,64),dtype=bool)
for c in bb['elements']:
    a=np.array(c['from']);b=np.array(c['to']);o=np.array(c.get('origin',[0,0,0]))
    rx,ry,rz=np.radians(c.get('rotation',[0,0,0]))
    assert sum(abs(v)>1e-9 for v in (rx,ry,rz))<=1
    cx,sx,cy,sy,cz,sz=math.cos(rx),math.sin(rx),math.cos(ry),math.sin(ry),math.cos(rz),math.sin(rz)
    R=np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])@np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])@np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])
    corners=(np.array(list(itertools.product(*zip(a,b))))-o)@R.T+o+[16,0,16]
    lo=np.maximum(0,np.floor(corners.min(axis=0)*2).astype(int));hi=np.minimum([64,96,64],np.ceil(corners.max(axis=0)*2).astype(int))
    ix=np.stack(np.meshgrid(*(np.arange(lo[j],hi[j]) for j in range(3)),indexing='ij'),axis=-1).reshape(-1,3)
    points=((ix+.5)/2-[16,0,16]-o)@R+o
    inside=((points>=a-1e-8)&(points<=b+1e-8)).all(axis=1)
    q=ix[inside]
    occupied[q[:,0],q[:,1],q[:,2]]=True

collision={}
for x,y,z in itertools.product(range(2),range(3),range(2)):
    vox=occupied[x*32:(x+1)*32,y*32:(y+1)*32,z*32:(z+1)*32].copy();boxes=[]
    while vox.any():
        a,b,c=map(int,np.argwhere(vox)[0]);a1=a+1;b1=b+1;c1=c+1
        while a1<32 and vox[a1,b,c]:a1+=1
        while c1<32 and vox[a:a1,b,c1].all():c1+=1
        while b1<32 and vox[a:a1,b1,c:c1].all():b1+=1
        vox[a:a1,b:b1,c:c1]=False
        boxes.append([v/2 for v in [a,b,c,a1,b1,c1]])
    assert boxes
    collision[f'{x}_{y}_{z}']=boxes
dump(DEST/'collision.json',collision)
print(json.dumps({'source_triangles':len(triangles),'cell_triangles':counts,'collision_boxes':sum(map(len,collision.values()))},indent=2))
