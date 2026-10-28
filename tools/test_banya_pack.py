"""Verify merged assets, native model geometry and original-audio provenance."""
from pathlib import Path
from zipfile import ZipFile
import hashlib,io,json,unittest
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
class BanyaPackTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  with ZipFile(ROOT/'dist/Z_Server_Pack_1.08.zip') as z:cls.base={n:z.read(n) for n in z.namelist()}
  with ZipFile(ROOT/'dist/Z_Server_Pack_1.09.zip') as z:cls.pack={n:z.read(n) for n in z.namelist()}
  cls.report=json.loads((ROOT/'dist/Z_Server_Pack_1.09.report.json').read_text())
 def test_existing_server_assets_are_byte_identical(self):
  for n,body in self.base.items():
   if n!='pack.mcmeta':self.assertEqual(body,self.pack[n],n)
  self.assertTrue(all(n.startswith('assets/zbanya/') for n in self.pack.keys()-self.base.keys()))
 def test_all_eight_models_have_volume_three_flies_and_native_coordinates(self):
  variants=[]
  for f in range(8):
   m=json.loads(self.pack[f'assets/zbanya/models/item/vkusnyashka_{f}.json']);es=m['elements'];self.assertEqual(138,len(es));self.assertEqual(12,len(es[-12:]));variants.append(es[-12:])
   for e in es:
    self.assertEqual(set(('north','south','east','west','up','down')),set(e['faces']))
    for a,b in zip(e['from'],e['to']):self.assertTrue(-16<=a<b<=32)
   spans=[max(e['to'][i] for e in es)-min(e['from'][i] for e in es) for i in range(3)];self.assertTrue(all(x>10 for x in spans))
  self.assertEqual(8,len({json.dumps(v,sort_keys=True) for v in variants}))
 def test_texture_sound_and_item_dispatch_are_namespaced_and_resolvable(self):
  im=Image.open(io.BytesIO(self.pack['assets/zbanya/textures/item/vkusnyashka.png']));self.assertEqual((64,64),im.size);self.assertEqual('RGBA',im.mode)
  item=json.loads(self.pack['assets/zbanya/items/vkusnyashka.json']);self.assertFalse(item['hand_animation_on_swap']);definition=item['model'];self.assertEqual('minecraft:range_dispatch',definition['type']);self.assertEqual(list(range(8)),[e['threshold'] for e in definition['entries']])
  for e in definition['entries']:
   ns,path=e['model']['model'].split(':');self.assertIn(f'assets/{ns}/models/{path}.json',self.pack)
  sound=json.loads(self.pack['assets/zbanya/sounds.json'])['ambient']['sounds'][0];self.assertTrue(sound['stream']);self.assertEqual(16,sound['attenuation_distance']);self.assertEqual('zbanya:ambient',sound['name'])
 def test_processed_original_audio_is_loud_and_clip_duration_matches_plugin(self):
  source=(ROOT/'banya/assets-src/ambient-original.mp3').read_bytes();self.assertEqual(hashlib.sha256(source).hexdigest(),self.report['source_mp3_sha256']);self.assertEqual((ROOT/'banya/assets-src/ambient.ogg').read_bytes(),self.pack['assets/zbanya/sounds/ambient.ogg'])
  self.assertTrue(self.pack['assets/zbanya/sounds/ambient.ogg'].startswith(b'OggS'));self.assertAlmostEqual(float((ROOT/'banya/src/main/resources/ambient-duration.txt').read_text()),self.report['sound_seconds'],places=4);self.assertTrue(9<self.report['sound_seconds']<11)
  measured=self.report['ogg_loudness'];self.assertTrue(-15.5<float(measured['input_i'])<-12.5);self.assertLessEqual(float(measured['input_tp']),-.5)
 def test_standalone_pack_contains_the_exact_same_banya_assets(self):
  with ZipFile(ROOT/'dist/Z_Banya_ResourcePack.zip') as z:
   self.assertIn('pack.mcmeta',z.namelist())
   for n in z.namelist():
    if n!='pack.mcmeta':self.assertEqual(z.read(n),self.pack[n],n)
  p=ROOT/'dist/Z_Server_Pack_1.09.zip';self.assertEqual(hashlib.sha1(p.read_bytes()).hexdigest(),p.with_suffix('.zip.sha1').read_text().strip())
if __name__=='__main__':unittest.main()
