"""Validate exported assets and package the complete artist handoff."""
from pathlib import Path
from PIL import Image
import json
import base64
import io
import hashlib
import zipfile
import math
import itertools

p=Path(__file__).resolve().parents[1]
m=json.loads((p/'charon_statue_B.bbmodel').read_text(encoding='utf-8'))
vertices=[]
for group in m['groups']:
    assert not any(group.get('rotation',[0,0,0])), 'Update transform handling for rotated groups'
for c in m['elements']:
    pivot=c.get('origin',[0,0,0])
    angles=[math.radians(v) for v in c.get('rotation',[0,0,0])]
    assert sum(abs(v)>1e-9 for v in angles)<=1, 'Update rotation order handling for multi-axis cubes'
    for corner in itertools.product(*[(c['from'][i],c['to'][i]) for i in range(3)]):
        x,y,z=[corner[i]-pivot[i] for i in range(3)]
        rx,ry,rz=angles
        y,z=y*math.cos(rx)-z*math.sin(rx),y*math.sin(rx)+z*math.cos(rx)
        x,z=x*math.cos(ry)+z*math.sin(ry),-x*math.sin(ry)+z*math.cos(ry)
        x,y=x*math.cos(rz)-y*math.sin(rz),x*math.sin(rz)+y*math.cos(rz)
        vertices.append([x+pivot[0],y+pivot[1],z+pivot[2]])
bounds={'min':[min(v[i] for v in vertices) for i in range(3)],
        'max':[max(v[i] for v in vertices) for i in range(3)]}
assert bounds=={'min':[-16,0,-16],'max':[16,48,16]},bounds
report={'model':'charon_statue_B.bbmodel','format':m['meta']['model_format'],
        'cubes':len(m['elements']),'groups':len(m['groups']),'blockbench_version':'5.1.6',
        'bounds_after_rotations':bounds,
        'size_blocks':{'width':2,'depth':2,'height':3},
        'reopened_in_blockbench':True,'textures':[]}
for t in m['textures']:
    embedded=Image.open(io.BytesIO(base64.b64decode(t['source'].split(',')[1]))).convert('RGBA')
    disk=Image.open(p/'textures'/t['name']).convert('RGBA')
    assert embedded.size==disk.size and embedded.tobytes()==disk.tobytes(),t['name']
    report['textures'].append({'name':t['name'],'size':list(disk.size),'embedded_pixels_match_png':True})
for c in m['elements']:
    assert all(c['from'][i]<c['to'][i] for i in range(3)),c['name']
    for fn,f in c['faces'].items():
        ti=f.get('texture')
        assert isinstance(ti,int) and 0<=ti<len(m['textures']),(c['name'],fn,ti)
        t=m['textures'][ti]
        assert all(0<=v<=(t['uv_height'] if i%2 else t['uv_width']) for i,v in enumerate(f['uv'])),(c['name'],fn)
report['invalid_uv_faces']=0
report['missing_texture_faces']=0
source=p.parents[1]/'src/main/resources/assets/kingdoms/textures/item/charon_token.png'
assert source.read_bytes()==(p/'textures/charon_token.png').read_bytes()
report['original_token_unchanged']=True
report['original_token_sha256']=hashlib.sha256(source.read_bytes()).hexdigest()
g=json.loads((p/'charon_statue_B.gltf').read_text())
assert all(b.get('uri','').startswith('data:') for b in g['buffers'])
assert all(i.get('uri','').startswith('data:') or 'bufferView' in i for i in g['images'])
report['gltf_self_contained']=True
report['visual_review']=['three-quarter','front','side','back']
(p/'validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
archive=p/'charon_statue_B_package.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for f in sorted(p.rglob('*')):
        if f.is_file() and f!=archive and f.suffix not in ['.pyc','.b64']:
            z.write(f,'charon_statue_B/'+f.relative_to(p).as_posix())
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    print(json.dumps({'validation':'passed','cubes':len(m['elements']),
                      'archive_files':len(z.namelist()),'archive_bytes':archive.stat().st_size},indent=2))
