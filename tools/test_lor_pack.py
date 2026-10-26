#!/usr/bin/env python3
"""Regressions for the two broken vanilla sprites and the shared server pack."""
from pathlib import Path
from zipfile import ZipFile
import io,json,math,unittest,hashlib
from datetime import datetime,timezone
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]

class PackRegressions(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with ZipFile(ROOT/'dist/Z_Server_Pack_1.08.zip') as zip:cls.pack={name:zip.read(name) for name in zip.namelist()}
        with ZipFile(ROOT/'dist/Z_Server_Pack_1.05_Pantera_1.0.1.zip') as zip:cls.base={name:zip.read(name) for name in zip.namelist()}
    def value(self,name):return json.loads(self.pack[name])
    def test_old_plugins_resources_remain_byte_exact(self):
        for name,data in self.base.items():
            if name!='pack.mcmeta':self.assertEqual(data,self.pack[name],name)
    def test_no_new_vanilla_atlas_or_model_overrides(self):
        self.assertFalse([name for name in self.pack.keys()-self.base.keys() if name.startswith('assets/minecraft/')])
        for name in ['copper_block','redstone_torch']:
            self.assertEqual((ROOT/'lor/assets-src/vanilla-1.21.11'/str(name+'.png')).read_bytes(),self.pack[f'assets/z_lor/textures/item/trim/{name}.png'])
        for name,data in self.pack.items():
            if name.startswith('assets/smplore/models/'):
                self.assertNotIn('minecraft:block/copper_block',data.decode());self.assertNotIn('minecraft:block/redstone_torch',data.decode())
    def test_device_models_use_a_single_item_atlas(self):
        # Minecraft 1.21.11 rejects mixed-atlas item layers, including textures
        # inherited by lit variants. All custom device maps must use item IDs.
        models=[(name,json.loads(data)) for name,data in self.pack.items() if name.startswith('assets/smplore/models/block/') and name.endswith('.json')]
        self.assertTrue(models)
        for name,model in models:
            for key,value in model.get('textures',{}).items():
                self.assertFalse(':block/' in value,(name,key,value))
    def test_dome_is_closed_3d_with_642_distinct_facets_and_correct_masks(self):
        geometry=json.loads((ROOT/'lor/reference/dome-geometry.json').read_text());centres=geometry['centres']
        self.assertEqual(642,len(centres));self.assertEqual(642,len({tuple(round(v,8) for v in xyz) for xyz in centres}))
        for color in ['purple','green','yellow']:
            children=self.value(f'assets/z_lor/items/dome_{color}.json')['model']['models'];self.assertEqual(642,len(children))
            for index,child in enumerate(children):
                self.assertEqual(index,child['index']);self.assertEqual('minecraft:empty',child['on_false']['type'])
                opening=child['on_true'];self.assertEqual(0,opening['index']);self.assertAlmostEqual((1-centres[index][1])/2,opening['entries'][0]['threshold'])
                fade=opening['entries'][0]['model'];self.assertEqual(1,fade['index']);self.assertTrue(0<fade['entries'][0]['threshold']<1)
                model=fade['entries'][0]['model']['model'];self.assertEqual(f'z_lor:item/dome/cell_{index:03}',model)
        for index,normal in enumerate(centres):
            transform=self.value(f'assets/z_lor/models/item/dome/cell_{index:03}.json')['display']['fixed'];rx,ry,rz=map(math.radians,transform['rotation'])
            actual=[math.sin(ry),-math.sin(rx)*math.cos(ry),math.cos(rx)*math.cos(ry)]
            for a,b in zip(actual,normal):self.assertAlmostEqual(a,b,places=8)
            self.assertTrue(.9<math.sqrt(sum((v/16)**2 for v in transform['translation']))<1.01)
    def test_beam_warning_has_transparent_background_and_visible_edges(self):
        image=Image.open(io.BytesIO(self.pack['assets/z_lor/textures/item/beam_warning.png'])).convert('RGBA')
        self.assertEqual(0,image.getpixel((0,0))[3]);self.assertGreater(image.getchannel('A').getextrema()[1],200)
    def test_beam_frames_fade_without_modifying_rgb_tone(self):
        for size in ['large','medium','small']:
            model=self.value(f'assets/z_lor/items/beam_{size}.json')['model'];self.assertEqual(8,len(model['entries']))
            peaks=[]
            for level in range(1,9):
                name=f'assets/z_lor/textures/item/beam_{size}_{level}.png';image=Image.open(io.BytesIO(self.pack[name])).convert('RGBA')
                self.assertEqual((32,32),image.size);peaks.append(image.getchannel('A').getextrema()[1])
                self.assertNotIn(name+'.mcmeta',self.pack)
                frames=model['entries'][level-1]['model'];self.assertEqual(1,frames['index']);self.assertEqual(list(range(8)),[entry['threshold'] for entry in frames['entries']])
                for entry in frames['entries']:
                    resource='assets/z_lor/models/'+entry['model']['model'].replace('z_lor:','')+'.json';self.assertIn(resource,self.pack)
            self.assertEqual(peaks,sorted(set(peaks)))
if __name__=='__main__':
    suite=unittest.defaultTestLoader.loadTestsFromTestCase(PackRegressions)
    result=unittest.TextTestRunner(verbosity=2).run(suite)
    report={'status':'PASS' if result.wasSuccessful() else 'FAIL','tests':result.testsRun,'failures':len(result.failures),'errors':len(result.errors),'skipped':len(result.skipped),'pack_sha1':hashlib.sha1((ROOT/'dist/Z_Server_Pack_1.08.zip').read_bytes()).hexdigest(),'cases':unittest.defaultTestLoader.getTestCaseNames(PackRegressions),'generated_at_utc':datetime.now(timezone.utc).isoformat()}
    target=ROOT/'lor/target/pack-regressions.json';target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(report,indent=2)+'\n')
    raise SystemExit(0 if result.wasSuccessful() else 1)
