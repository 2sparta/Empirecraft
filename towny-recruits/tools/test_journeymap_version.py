#!/usr/bin/env python3
"""Exercise the shipped descriptor against Forge's real Maven version parser."""
import os,subprocess,tomllib,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];REPO=ROOT.parent
def main():
 descriptor=tomllib.loads((ROOT/'src/main/resources/META-INF/mods.toml').read_text())
 dependency=next(d for d in descriptor['dependencies']['townyrecruits'] if d['modId']=='journeymap')
 jars=list((REPO/'mods').glob('journeymap-forge-*.jar'));assert len(jars)==1
 with zipfile.ZipFile(jars[0]) as z:version=tomllib.loads(z.read('META-INF/mods.toml').decode())['mods'][0]['version']
 for folder in ['mods','server/mods']:
  with zipfile.ZipFile(REPO/folder/'townyrecruits-1.5.0.jar') as z:
   assert z.read('META-INF/mods.toml')==(ROOT/'src/main/resources/META-INF/mods.toml').read_bytes()
 build=ROOT/'build/dependency-test';build.mkdir(parents=True,exist_ok=True)
 source=build/'VersionTest.java';source.write_text('''
import org.apache.maven.artifact.versioning.*;
public final class VersionTest {
 public static void main(String[] args) throws Exception {
  VersionRange range=VersionRange.createFromVersionSpec(args[0]);int checks=0;
  for(String version:new String[]{args[1],"1.20.1-6.0.7","1.20.1-6.0.8","1.20.1-6.9.9","6.0.7","6.1.0"}) {
   if(!range.containsVersion(new DefaultArtifactVersion(version)))throw new AssertionError("Rejected supported "+version);checks++;
  }
  for(String version:new String[]{"1.20.1-6.0.6","1.20.1-7.0.0","6.0.6","7.0.0","1.19.2-6.0.7"}) {
   if(range.containsVersion(new DefaultArtifactVersion(version)))throw new AssertionError("Accepted unsupported "+version);checks++;
  }
  if(VersionRange.createFromVersionSpec("[6.0.7,7)").containsVersion(new DefaultArtifactVersion(args[1])))throw new AssertionError("Did not reproduce original loading failure");
  System.out.println("JOURNEYMAP_VERSION_PASS checks="+(checks+1)+" installed="+args[1]+" range="+args[0]);
 }
}
''')
 java=Path(os.environ.get('JAVA_HOME','/usr'))/'bin/java';libraries=sorted((REPO/'server/libraries/org/apache/maven/maven-artifact').rglob('*.jar'));assert libraries
 libraries+=sorted((REPO/'server/libraries/org/apache/commons/commons-lang3').rglob('*.jar'))
 cp=os.pathsep.join(map(str,libraries));subprocess.run([java,'com.sun.tools.javac.Main','--release','17','-proc:none','-cp',cp,'-d',build,source],check=True)
 subprocess.run([java,'-cp',str(build)+os.pathsep+cp,'VersionTest',dependency['versionRange'],version],check=True)
if __name__=='__main__':main()
