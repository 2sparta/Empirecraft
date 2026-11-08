#!/usr/bin/env python3
"""Run durable-ledger tests with JDK 17 and the real Gson dependency."""
import argparse,hashlib,os,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent
p=argparse.ArgumentParser();p.add_argument('--gson',type=Path,required=True);a=p.parse_args()
assert hashlib.sha256(a.gson.read_bytes()).hexdigest()=='0cdd163ce3598a20fc04eee71b140b24f6f2a3b35f0a499dbbdd9852e83fbfaf','Unexpected Gson build dependency'
out=R/'build/ledger-unit';out.mkdir(parents=True,exist_ok=True)
jdk=Path(os.environ['JAVA_HOME'])/'bin' if os.environ.get('JAVA_HOME') else None
javac=str(jdk/'javac') if jdk else 'javac';java=str(jdk/'java') if jdk else 'java'
subprocess.run([javac,'--release','17','-cp',str(a.gson),'-d',str(out),str(R/'src/main/java/ua/townyrecruits/CommandWords.java'),str(R/'src/main/java/ua/townyrecruits/RecruitmentConfig.java'),str(R/'src/main/java/ua/townyrecruits/RecruitmentState.java'),str(R/'tests/RecruitmentStateTest.java')],check=True)
subprocess.run([java,'-ea','-cp',str(out)+os.pathsep+str(a.gson),'ua.townyrecruits.test.RecruitmentStateTest'],check=True)
