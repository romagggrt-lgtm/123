"""Measure the actual vanilla client's OpenAL Wave File Writer output."""
from pathlib import Path
import json,subprocess
import numpy as np

def decode(path):
 args=['ffmpeg','-v','error']
 if Path(path).suffix=='.wav':args+=['-ignore_length','1']
 args+=['-i',str(path),'-f','f32le','-ac','1','-ar','8000','-']
 return np.frombuffer(subprocess.check_output(args),dtype='<f4').astype(np.float64)

def measure(capture,original,report):
 signal=decode(capture);template=decode(original);n=len(signal);m=len(template)
 assert n>m>40000,'Client audio capture is missing or too short'
 size=1<<(n+m-2).bit_length()
 convolution=np.fft.irfft(np.fft.rfft(signal,size)*np.fft.rfft(template[::-1],size),size)[m-1:n]
 energy=np.concatenate(([0.0],np.cumsum(signal*signal)));windows=energy[m:]-energy[:-m]
 correlation=convolution/np.sqrt(np.maximum(windows*np.sum(template*template),1e-20))
 start=int(np.argmax(correlation));segment=signal[start:start+m];rms=float(np.sqrt(np.mean(segment*segment)))
 result={'capture':'Unmodified Minecraft 1.21.11 / OpenAL Wave File Writer','ambient_slider':0.0,'master_slider':1.0,'sample_rate_analysis':8000,'seconds':n/8000,'original_seconds':m/8000,'match_start_seconds':start/8000,'normalised_correlation':float(correlation[start]),'matched_rms_dbfs':float(20*np.log10(max(rms,1e-12))),'matched_peak':float(np.max(np.abs(segment)))}
 assert result['normalised_correlation']>.65,result
 assert rms>.02,'Client decoded the clip too quietly: '+str(result)
 result['status']='PASS';Path(report,'client-audio.json').write_text(json.dumps(result,indent=2)+'\n')
 subprocess.run(['ffmpeg','-y','-v','error','-f','f32le','-ac','1','-ar','8000','-i','-','-c:a','libvorbis','-q:a','4',str(Path(report,'client-ambient-captured.ogg'))],input=segment.astype('<f4').tobytes(),check=True)
 return result
