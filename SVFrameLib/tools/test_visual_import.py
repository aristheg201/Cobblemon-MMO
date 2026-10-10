import base64
import copy
import unittest
from import_blockbench_visuals import bake, sample


class VisualImportTest(unittest.TestCase):
    def blueprint(self):
        return {'resolution': {'width': 16, 'height': 16},
                'textures': [{'source': 'data:image/png;base64,' + base64.b64encode(b'fixture').decode()}],
                'elements': [{'uuid': 'cube', 'type': 'cube', 'from': [15, -1, -1], 'to': [17, 1, 1],
                              'origin': [16, 0, 0], 'faces': {'up': {'uv': [0, 0, 16, 16], 'texture': 0}}}],
                'outliner': [{'uuid': 'parent', 'origin': [16, 0, 0], 'children': ['cube']}],
                'animations': [{'name': 'move', 'length': 1, 'animators': {'parent': {'keyframes': [
                    {'channel': 'position', 'time': 0, 'data_points': [{'x': 0, 'y': 0, 'z': 0}]},
                    {'channel': 'position', 'time': 1, 'data_points': [{'x': 16, 'y': 0, 'z': 0}]}]}}}]}

    def test_animated_parent_moves_cube_in_world_units(self):
        models, files, overrides = bake(self.blueprint(), 'test', 'model', 200000)
        frames = models['model@move'][0]['frames']
        self.assertEqual(len(frames), 21)
        self.assertAlmostEqual(frames[0][12], -1)
        self.assertAlmostEqual(frames[10][12], -1.5)
        self.assertAlmostEqual(frames[20][12], -2)
        self.assertEqual(frames[10][15], 1)
        self.assertEqual(overrides[0]['predicate']['custom_model_data'], 200000)
        self.assertIn('assets/test/models/model/cube_0.json', files)
        self.assertIn('assets/test/textures/item/model_0.png', files)

    def test_mesh_rejected_instead_of_losing_geometry(self):
        blueprint = self.blueprint(); blueprint['elements'][0]['type'] = 'mesh'
        with self.assertRaises(ValueError):
            bake(blueprint, 'test', 'model', 200000)

    def test_expression_rejected_instead_of_silent_static_pose(self):
        blueprint = self.blueprint()
        blueprint['animations'][0]['animators']['parent']['keyframes'][1]['data_points'][0]['x'] = 'query.time * 16'
        with self.assertRaises(ValueError):
            bake(blueprint, 'test', 'model', 200000)

    def test_bezier_rejected_instead_of_wrong_animation(self):
        keys = [{'channel': 'position', 'time': 0, 'data_points': [{'x': 0}]},
                {'channel': 'position', 'time': 1, 'interpolation': 'bezier', 'data_points': [{'x': 16}]}]
        with self.assertRaises(ValueError):
            sample(keys, 'position', .5)


if __name__ == '__main__':
    unittest.main()
