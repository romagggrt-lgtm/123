#!/usr/bin/env python3
"""Regression coverage for the exact missing-sprite failure from Pantera 1.0.0."""
from pathlib import Path
import io,json,unittest
import numpy as np
from PIL import Image
from build_pantera_pack import ROOT,OUT,VERSION,BASE
from check_pantera_pack import validate
from merge_smplore_pack import read_pack,check_references
from prepare_pantera_audio import report as audio_report

class PanteraPackTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.files=read_pack(OUT)
    def test_every_panther_part_uses_a_stitched_item_sprite(self):
        result=validate(self.files)
        self.assertEqual(11,result['models_checked'])
        self.assertEqual(['z_pantera:item/pantera','z_pantera:item/spawn_egg'],result['custom_sprites'])
    def test_old_entity_folder_bug_is_detected_even_though_png_exists(self):
        broken=dict(self.files)
        broken['assets/z_pantera/textures/entity/pantera.png']=broken.pop('assets/z_pantera/textures/item/pantera.png')
        for name in list(broken):
            if name.startswith('assets/z_pantera/models/item/'):
                broken[name]=broken[name].replace(b'z_pantera:item/pantera',b'z_pantera:entity/pantera')
        check_references(broken) # The old 1.0.0 check accepts exactly this broken configuration.
        with self.assertRaisesRegex(AssertionError,'not stitched in minecraft:items'):
            validate(broken)
    def test_fix_preserves_old_server_assets_and_does_not_replace_global_atlases(self):
        old=read_pack(BASE)
        for name,data in old.items():
            if name!='pack.mcmeta':self.assertEqual(data,self.files[name],name)
        self.assertNotIn('assets/minecraft/atlases/items.json',self.files)
        self.assertFalse(any(n.startswith('assets/smplore/') for n in self.files))
    def test_open_eyes_reference_green_pixels_in_the_actual_atlas(self):
        model=json.loads(self.files['assets/z_pantera/models/item/head.json'])
        uv=next(e for e in model['elements'] if e['name']=='Head')['faces']['north']['uv']
        im=Image.open(io.BytesIO(self.files['assets/z_pantera/textures/item/pantera.png'])).convert('RGB')
        x0,y0,x1,y1=[round(v*im.width/16) for v in uv]
        colors=np.array(im.crop((x0,y0,x1,y1))).astype(float)
        self.assertGreater(np.count_nonzero((colors[:,:,1]>80)&(colors[:,:,1]>colors[:,:,0]*1.3)),30)
    def test_louder_audio_preserves_duration_and_does_not_clip_after_vorbis_decode(self):
        report=audio_report()
        self.assertGreaterEqual(report['gain_db'],5)
        self.assertLess(report['output']['peak_dbfs'],-.5)
        self.assertEqual(84096,report['decoded_frames'])
        self.assertEqual(0,report['clipped_samples'])

if __name__=='__main__':
    suite=unittest.defaultTestLoader.loadTestsFromTestCase(PanteraPackTest)
    cases=[t.id() for t in suite]
    result=unittest.TextTestRunner(verbosity=2).run(suite)
    data={'tests':result.testsRun,'failures':len(result.failures),'errors':len(result.errors),
          'skipped':len(result.skipped),'test_cases':cases,'successful':result.wasSuccessful()}
    (ROOT/f'dist/Z_Pantera-pack-tests_{VERSION}.json').write_text(json.dumps(data,indent=2)+'\n')
    raise SystemExit(0 if result.wasSuccessful() and not result.skipped else 1)
