#!/usr/bin/env python3
"""Raise the supplied recording's level while keeping duration/pitch and preventing clipping."""
from pathlib import Path
import hashlib,json,subprocess
import numpy as np

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'pantera/assets-src/1024.MP3'
OUTPUT=ROOT/'pantera/assets-src/pantera.ogg'
FILTER=('aformat=channel_layouts=mono,'
        'acompressor=threshold=0.28:ratio=2.5:attack=5:release=60:makeup=3,'
        'alimiter=limit=0.75:level=false:latency=true,volume=1.16')

def pcm(path):
    return np.frombuffer(subprocess.check_output(['ffmpeg','-v','error','-i',str(path),'-ac','1','-ar','44100','-f','f32le','-']),dtype='<f4')
def levels(samples):
    return {'rms_dbfs':float(20*np.log10(np.sqrt(np.mean(samples*samples)))),
            'peak_dbfs':float(20*np.log10(np.abs(samples).max()))}
def report():
    source,output=pcm(SOURCE),pcm(OUTPUT)
    before,after=levels(source),levels(output)
    assert len(source)==len(output), 'Audio was trimmed or padded'
    boost=after['rms_dbfs']-before['rms_dbfs']
    correlation=float(np.corrcoef(source,output)[0,1])
    assert 5<=boost<=8,boost
    assert after['peak_dbfs']<-.5,after
    assert np.count_nonzero(np.abs(output)>=1)==0,'Decoded Vorbis clips'
    assert correlation>.9,'Unexpected change to the recording'
    return {'original_sha256':hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
            'ogg_sha256':hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),
            'input':before,'output':after,'gain_db':boost,'decoded_frames':len(output),
            'sample_rate':44100,'duration_seconds':len(output)/44100,'output_channels':1,
            'correlation_with_original':correlation,'clipped_samples':0,'filter':FILTER,
            'trimmed':False,'speed_or_pitch_changed':False}
def main():
    subprocess.check_call(['ffmpeg','-hide_banner','-loglevel','error','-y','-fflags','+bitexact',
        '-i',str(SOURCE),'-fflags','+bitexact','-map_metadata','-1','-ac','1','-ar','44100','-af',FILTER,
        '-c:a','libvorbis','-q:a','6','-flags:a','+bitexact',str(OUTPUT)])
    result=report()
    OUTPUT.with_name('audio-report.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(result,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
