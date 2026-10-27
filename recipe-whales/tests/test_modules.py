#!/usr/bin/env python3
"""Reproduce the reported JVM conflict, then resolve helpers in separate packages."""
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]


class ModulePackagesTest(unittest.TestCase):
    def test_old_layout_fails_and_mod_specific_packages_resolve(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            subprocess.run(['java', 'com.sun.tools.javac.Main', '--release', '17', '-d', str(root),
                            str(ROOT/'tests/ModulePackages.java')], check=True)
            for layout, packages in (
                ('old', ['ua.empirecraft.nativepatch', 'ua.empirecraft.nativepatch']),
                ('fixed', ['com.simibubi.create.empirecraft', 'net.mcreator.wonderoussea.empirecraft']),
            ):
                jars = []
                for module, helper, package in zip(['create', 'wonderoussea'],
                                                   ['JeiCategories', 'WhaleHarvest'], packages):
                    classes = root/layout/module
                    classes.mkdir(parents=True)
                    source = classes/(helper+'.java')
                    source.write_text('package '+package+'; public final class '+helper+' {}\n')
                    subprocess.run(['java', 'com.sun.tools.javac.Main', '--release', '17', '-d', str(classes), str(source)], check=True)
                    jar = root/(module+'.jar')
                    with zipfile.ZipFile(jar, 'w') as archive:
                        for path in classes.rglob('*.class'):
                            archive.write(path, path.relative_to(classes))
                    jars.append(str(jar))
                result = subprocess.run(['java', '-cp', str(root), 'ModulePackages', *jars],
                                        text=True, capture_output=True)
                if layout == 'old':
                    self.assertNotEqual(result.returncode, 0)
                    self.assertIn('java.lang.module.ResolutionException', result.stderr)
                    self.assertIn('ua.empirecraft.nativepatch', result.stderr)
                else:
                    self.assertEqual(result.returncode, 0, result.stderr)
                    self.assertIn('PASS combined module resolution', result.stdout)


if __name__ == '__main__':
    unittest.main()
