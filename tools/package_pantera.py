#!/usr/bin/env python3
"""Ship the actual verified plugin, complete merged pack and editable project."""
from pathlib import Path
import hashlib,json,shutil,subprocess,uuid,xml.etree.ElementTree as E,zipfile
import numpy as np
from merge_smplore_pack import read_pack,check_references
from build_pantera_pack import VERSION,OUT
from check_pantera_pack import validate
from prepare_pantera_audio import report as audio_report

ROOT=Path(__file__).resolve().parents[1]
DIST=ROOT/'dist'
MODULE=ROOT/'pantera'
PACK=OUT.name
BASE='https://raw.githubusercontent.com/romagggrt-lgtm/123/main/dist/'

def dump(path,value):path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
def archive(path,files):
    with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED) as out:
        for name,source in sorted(files.items()):
            info=zipfile.ZipInfo(name,date_time=(2026,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
            out.writestr(info,source.read_bytes())
    with zipfile.ZipFile(path) as z:assert z.testzip() is None
def sound_report():
    def probe(path):return json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-show_format','-of','json',str(path)]))
    def pcm(path):return np.frombuffer(subprocess.check_output(['ffmpeg','-v','error','-i',str(path),'-ac','1','-ar','44100','-f','f32le','-']),dtype='<f4')
    a,b=pcm(MODULE/'assets-src/1024.MP3'),pcm(MODULE/'assets-src/pantera.ogg')
    n=min(len(a),len(b));ogg=probe(MODULE/'assets-src/pantera.ogg');source=probe(MODULE/'assets-src/1024.MP3')
    corr=float(np.corrcoef(a[:n],b[:n])[0,1]);assert len(a)==len(b) and corr>.9
    assert ogg['streams'][0]['codec_name']=='vorbis' and ogg['streams'][0]['channels']==1
    return {**audio_report(),'input_codec':source['streams'][0]['codec_name'],'output_codec':'vorbis','output_channels':1,
            'decoded_frames':n,'sample_rate':44100,'duration_seconds':n/44100,'correlation_with_downmixed_original':corr,
            'relative_rmse':float(np.sqrt(np.mean((a-b)**2))/np.sqrt(np.mean(a**2))),
            'trimmed':False,'speed_or_pitch_changed':False}
def main():
    suites=[E.parse(p).getroot() for p in (MODULE/'target/surefire-reports').glob('TEST-*.xml')]
    totals={key:sum(int(s.get(key,0)) for s in suites) for key in ('tests','failures','errors','skipped')}
    assert totals['tests']>=54 and not any(totals[k] for k in ('failures','errors','skipped')),totals
    pack_tests=json.loads((DIST/f'Z_Pantera-pack-tests_{VERSION}.json').read_text())
    assert pack_tests['tests']>=5 and pack_tests['successful'] and not pack_tests['skipped']
    jar=MODULE/'target/Z_Pantera.jar'
    with zipfile.ZipFile(jar) as z:
        assert z.testzip() is None and 'dev/zpantera/ZPanteraPlugin.class' in z.namelist()
        assert int.from_bytes(z.read('dev/zpantera/ZPanteraPlugin.class')[6:8],'big')==65
        assert VERSION in z.read('plugin.yml').decode()
    merged=read_pack(DIST/PACK);old=read_pack(DIST/'Z_Server_Pack_1.05_Gold.zip')
    assert all(merged[n]==data for n,data in old.items() if n!='pack.mcmeta')
    assert not any(n.startswith('assets/smplore/') for n in merged)
    assert all(n in old or n.startswith('assets/z_pantera/') for n in merged)
    assert (DIST/PACK).read_bytes()==(DIST/'Z_Pantera_ResourcePack.zip').read_bytes()
    bb=json.loads((MODULE/'model/Pantera.bbmodel').read_text());assert len(bb['elements'])==34 and len(bb['animations'])==4
    sha1=hashlib.sha1((DIST/PACK).read_bytes()).hexdigest()
    properties=(f'resource-pack={BASE}{PACK}\nresource-pack-sha1={sha1}\n'
                f'resource-pack-id={uuid.uuid5(uuid.NAMESPACE_URL,"z_server_pack/shared")}\n'
                'require-resource-pack=true\nresource-pack-prompt={"text":"Z_Server_Pack: Pantera, Shahed, Verity, Golda"}\n')
    (MODULE/'server-properties.txt').write_text(properties)
    (DIST/f'Z_Pantera-server-properties_{VERSION}.txt').write_text(properties)
    report={'plugin':'Z_Pantera','version':VERSION,'java':'21','paper_api':'1.21.11-R0.1-SNAPSHOT','junit':totals,
            'test_cases':[t.get('name') for s in suites for t in s.findall('testcase')],
            'pack':PACK,'sha1':sha1,'pack_references':check_references(merged),'atlas_validation':validate(merged),
            'pack_regression_tests':pack_tests,'old_pack_files_preserved':len(old)-1,
            'old_plugins_modified':False,'smplore_assets':0,'sound':sound_report(),
            'model':{'bbmodel':'pantera/model/Pantera.bbmodel','cuboids':34,'animated_bones':9,'animations':4},
            'minecraft_client_tested':False,'paper_runtime_tested':False,
            'paper_runtime_unavailable':'Official server binary download blocked by network proxy: CONNECT tunnel failed, response 403'}
    dump(DIST/f'Z_Pantera-tests_{VERSION}.json',report)
    shutil.copyfile(jar,DIST/'Z_Pantera.jar');shutil.copyfile(jar,DIST/f'Z_Pantera_{VERSION}.jar')
    for doc in ('README','TESTING'):shutil.copyfile(MODULE/f'{doc}.md',DIST/f'Z_Pantera_{VERSION}.{doc}.md')
    shutil.copyfile(MODULE/'UPDATE-1.0.1.md',DIST/f'Z_Pantera_{VERSION}.UPDATE.md')
    # Include everything needed to build the standalone Maven module and reproduce the combined RP.
    source={p.relative_to(ROOT).as_posix():p for p in MODULE.rglob('*') if p.is_file() and 'target' not in p.relative_to(MODULE).parts}
    for name in ('build_pantera_pack.py','preview_pantera.py','package_pantera.py','build_pantera.sh','merge_smplore_pack.py',
                 'prepare_pantera_audio.py','check_pantera_pack.py','test_pantera_pack.py'):
        source['tools/'+name]=ROOT/'tools'/name
    for name in ('Z_Server_Pack_1.05_Gold.zip',PACK,f'Z_Pantera-tests_{VERSION}.json',f'Z_Pantera-pack-tests_{VERSION}.json',OUT.with_suffix('.report.json').name):
        source['dist/'+name]=DIST/name
    source['LICENSE']=ROOT/'LICENSE'
    archive(DIST/f'Z_Pantera-Source_{VERSION}.zip',source)
    install={f'plugins/Z_Pantera_{VERSION}.jar':DIST/f'Z_Pantera_{VERSION}.jar',
             PACK:DIST/PACK,'server-properties.txt':MODULE/'server-properties.txt',
             'README.md':MODULE/'README.md','TESTING.md':MODULE/'TESTING.md','UPDATE-1.0.1.md':MODULE/'UPDATE-1.0.1.md',
             f'Z_Pantera-Source_{VERSION}.zip':DIST/f'Z_Pantera-Source_{VERSION}.zip',
             f'Z_Pantera-tests_{VERSION}.json':DIST/f'Z_Pantera-tests_{VERSION}.json',
             f'Z_Pantera-preview_{VERSION}.png':DIST/f'Z_Pantera-preview_{VERSION}.png',
             f'Z_Pantera-walk_{VERSION}.gif':DIST/f'Z_Pantera-walk_{VERSION}.gif',
             PACK+'.sha1':DIST/(PACK+'.sha1')}
    archive(DIST/f'Z_Pantera_{VERSION}-install.zip',install)
    names=['Z_Pantera.jar',f'Z_Pantera_{VERSION}.jar',PACK,'Z_Pantera_ResourcePack.zip',f'Z_Pantera-Source_{VERSION}.zip',f'Z_Pantera_{VERSION}-install.zip']
    (DIST/'Z_Pantera-SHA256SUMS').write_text(''.join(hashlib.sha256((DIST/n).read_bytes()).hexdigest()+'  '+n+'\n' for n in names))
    for name in names:print(name,(DIST/name).stat().st_size)
    print('Tests:',totals,'; sound correlation:',report['sound']['correlation_with_downmixed_original'])
if __name__=='__main__':main()
