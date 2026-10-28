"""Reproducible course verification; stdlib only, no production database fallback."""
from __future__ import annotations
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import uuid
import xml.etree.ElementTree as ET
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
ZONE = dt.timezone(dt.timedelta(hours=8))
SOURCE_PREFIXES = ('backend/src/', 'backend/scripts/', 'frontend/src/', 'frontend/scripts/', 'AI_Server/')
SOURCE_FILES = {'backend/pom.xml', 'frontend/package.json', 'frontend/package-lock.json',
                'verify-course.ps1', 'start-local.ps1', 'stop-local.ps1', 'scripts/package_delivery.py',
                'scripts/verify_course.py', 'scripts/test_verification.py'}
REQUIRED_CHECKS = {'backend','frontend','python-ai','verification-tooling','python-dependencies'}

def digest(data):
    return hashlib.sha256(data).hexdigest()

def file_digest(path):
    data=Path(path).read_bytes()
    if Path(path).suffix.lower() in {'.java','.js','.mjs','.vue','.json','.yml','.yaml','.xml','.properties','.md','.txt','.ps1','.psm1','.factories','.py','.sql'}:
        data=data.replace(b'\r\n',b'\n')
    return digest(data)

def source_manifest(root=ROOT):
    paths = subprocess.check_output(['git', '-c', 'core.quotepath=false', 'ls-files', '--cached', '--others', '--exclude-standard'], cwd=root).decode('utf-8').splitlines()
    selected = sorted(set(p for p in paths if p in SOURCE_FILES or p.startswith(SOURCE_PREFIXES)))
    return {p: file_digest(root/p) for p in selected if (root/p).is_file()}

def junit_cases(folder):
    result = []
    for file in sorted(Path(folder).glob('TEST-*.xml')):
        suite = ET.parse(file).getroot()
        for test in suite.iter('testcase'):
            outcome = next((tag for tag in ('failure','error','skipped') if test.find(tag) is not None), 'passed')
            name=test.get('classname','').rsplit('.',1)[-1]
            layer=('frontend-real-http-mysql' if test.get('name','').startswith('frontendApiModule') else
                   'real-http-mysql' if name in {'UserOperationIntegrationTest','AuthenticationHttpIntegrationTest','DevelopmentLoginIntegrationTest'} else
                   'real-mysql-transactions' if name=='DatabaseEngineeringIntegrationTest' else
                   'spring-context' if name=='LifeTrackApplicationTests' else 'unit')
            result.append({'class':test.get('classname'), 'name':test.get('name'),
                           'seconds':float(test.get('time',0)), 'outcome':outcome, 'layer':layer})
    return result

def passed(commands, cases):
    return len(commands)==len(REQUIRED_CHECKS) and {c['name'] for c in commands}==REQUIRED_CHECKS and all(c['exitCode']==0 and (c['name'] not in {'python-ai','verification-tooling'} or c.get('testCount',0)>0) for c in commands) and bool(cases) and all(c['outcome']=='passed' for c in cases)

def python_test_count(log):
    match=re.search(rb'Ran (\d+) tests?\b',log)
    return int(match[1]) if match else 0

def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n',encoding='utf-8',newline='\n')

def check_evidence(folder, root=ROOT):
    folder=Path(folder)
    hashes=json.loads((folder/'checksums.json').read_text(encoding='utf-8'))
    for name, expected in hashes.items():
        if not re.fullmatch(r'[a-z-]+\.json',name) or file_digest(folder/name)!=expected:
            raise ValueError('Evidence file checksum mismatch: '+name)
    expected=json.loads((folder/'sources.json').read_text(encoding='utf-8'))
    if expected != source_manifest(root):
        raise ValueError('Current source files do not match this verification run; rerun verification.')
    report=json.loads((folder/'summary.json').read_text(encoding='utf-8'))
    cases=json.loads((folder/'cases.json').read_text(encoding='utf-8'))
    if report['passed'] != passed(report['commands'],cases):
        raise ValueError('Summary disagrees with command exits or individual cases.')
    if not report['passed']:
        raise ValueError('This evidence records a FAILED verification run.')
    return report

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--check-evidence')
    parser.add_argument('--keep-database',action='store_true')
    args=parser.parse_args()
    if args.check_evidence:
        report=check_evidence(ROOT/args.check_evidence)
        print('Evidence and source fingerprints match:',report['runId'])
        print('Raw logs are local, excluded from Git; fingerprints are not digital signatures.')
        return 0
    env=os.environ.copy()
    dotenv=ROOT/'.env'
    if dotenv.exists():
        for line in dotenv.read_text(encoding='utf-8-sig').splitlines():
            if '=' in line and not line.lstrip().startswith('#'):
                key,value=line.split('=',1)
                env.setdefault(key.strip(),value.strip().strip('"').strip("'"))
    username=env.get('TEST_DB_USERNAME') or env.get('DB_USERNAME')
    password=env.get('TEST_DB_PASSWORD') or env.get('DB_PASSWORD')
    if not username or not password:
        raise ValueError('Set DB_USERNAME/DB_PASSWORD in .env, or TEST_DB_USERNAME/TEST_DB_PASSWORD.')
    mysql=shutil.which('mysql')
    python=ROOT/'AI_Server/venv/Scripts/python.exe'
    if not mysql or not python.is_file():
        raise ValueError('Install the MySQL client and AI_Server venv dependencies first; see README.')
    uri=urlparse(env.get('DB_URL','jdbc:mysql://localhost:3306/lifetrack')[5:])
    mysql_args=[mysql,'--default-character-set=utf8mb4','--host='+str(uri.hostname or 'localhost'),
                '--port='+str(uri.port or 3306),'--user='+username,'--batch','--skip-column-names']
    env['MYSQL_PWD']=password
    def sql(statement):
        r=subprocess.run(mysql_args,input=statement.encode('utf-8'),capture_output=True,env=env)
        if r.returncode: raise RuntimeError('MySQL verification operation failed; check credentials, server and schema constraints.')
        return r.stdout.decode('utf-8').strip()
    run_id=dt.datetime.now(ZONE).strftime('%Y%m%d-%H%M%S')+'-'+uuid.uuid4().hex[:8]
    database='lifetrack_test_'+uuid.uuid4().hex
    log_dir=ROOT/'.verification'/run_id
    evidence=ROOT/'docs/verification'/run_id
    log_dir.mkdir(parents=True);evidence.mkdir(parents=True)
    started=dt.datetime.now(ZONE).isoformat()
    sources=source_manifest()
    commands=[]
    cases=[]
    cleanup=False
    error=None
    created=False
    versions={}
    schema=ROOT/'backend/sql/init_db.sql'
    try:
        sql('CREATE DATABASE `'+database+'` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci')
        created=True
        sql(re.sub(r'\blifetrack\b',database,schema.read_text(encoding='utf-8')))
        versions['mysql']=sql('SELECT VERSION()')
        for name,command in [('java',['java','-version']),('node',['node','--version']),('python',[str(python),'--version'])]:
            response=subprocess.run(command,capture_output=True,timeout=15)
            versions[name]=(response.stdout+response.stderr).decode(errors='replace').splitlines()[0]
        env.update(TEST_DB_URL=f'jdbc:mysql://{uri.hostname or "localhost"}:{uri.port or 3306}/{database}?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf-8&useSSL=false&allowPublicKeyRetrieval=true',
                   TEST_DB_USERNAME=username,TEST_DB_PASSWORD=password)
        report_dir=ROOT/'backend/target/surefire-reports'
        if report_dir.exists():
            if not report_dir.resolve().is_relative_to((ROOT/'backend/target').resolve()):
                raise ValueError('Unexpected test report path')
            shutil.rmtree(report_dir) # Only stale test reports, never compiled application classes.
        checks=[('backend', [str(ROOT/'backend/mvnw.cmd'),'test'],ROOT/'backend'),
                ('frontend', ['npm.cmd','run','verify'],ROOT/'frontend'),
                ('python-ai',[str(python),'-m','unittest','discover','-s','AI_Server','-p','test_*.py'],ROOT),
                ('verification-tooling',[sys.executable,'-m','unittest','discover','-s','scripts','-p','test_*.py'],ROOT),
                ('python-dependencies',[str(python),'-m','pip','check'],ROOT)]
        for name, command, cwd in checks:
            print('Running '+name+'...',flush=True)
            began=time.monotonic()
            log=log_dir/(name+'.log')
            try:
                with log.open('wb') as stream:
                    result=subprocess.run(command,cwd=cwd,env=env,stdout=stream,stderr=subprocess.STDOUT,timeout=600)
                code=result.returncode
            except subprocess.TimeoutExpired:
                code=124
            entry={'name':name,'exitCode':code,'seconds':round(time.monotonic()-began,3),
                   'log':str(log.relative_to(ROOT)).replace('\\','/'),'logSha256':digest(log.read_bytes())}
            if name in {'python-ai','verification-tooling'}: entry['testCount']=python_test_count(log.read_bytes())
            commands.append(entry)
            ok=code==0 and entry.get('testCount',1)>0
            print(name+': '+('PASS' if ok else 'FAIL'),flush=True)
        cases=junit_cases(report_dir)
    except Exception as e:
        error=type(e).__name__ # Raw failure details stay in local logs, never publish credentials or tokens.
    finally:
        if created and not args.keep_database:
            if not re.fullmatch('lifetrack_test_[a-f0-9]{32}',database):
                raise ValueError('Refusing to clean an unexpected database')
            try:
                sql('DROP DATABASE `'+database+'`')
                cleanup=True
            except Exception:
                error='DatabaseCleanupFailed'
        if sources!=source_manifest():
            error='SourceChangedDuringVerification'
        summary={'runId':run_id,'startedAt':started,'finishedAt':dt.datetime.now(ZONE).isoformat(),
                 'sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT).decode().strip(),
                 'workingTreeDirty':bool(subprocess.check_output(['git','status','--porcelain'],cwd=ROOT)),
                 'sourceTreeSha256':digest(json.dumps(sources,sort_keys=True).encode()),
                 'database':'new isolated lifetrack_test_* schema','databaseRemoved':cleanup,
                 'runtimeVersions':versions,
                 'passed':passed(commands,cases) and not error,'errorType':error,
                 'testCases':len(cases),'commands':commands,
                 'boundaries':{'browser':'not executed by this runner','realModel':'not executed; deterministic tests use unavailable AI or controlled test doubles',
                               'frontendHttpContract':'original frontend API/http modules against real Spring Boot + MySQL; uni transport bridge uses fetch'}}
        write_json(evidence/'summary.json',summary)
        write_json(evidence/'cases.json',cases)
        write_json(evidence/'sources.json',sources)
        write_json(evidence/'checksums.json',{p.name:file_digest(p) for p in sorted(evidence.glob('*.json')) if p.name!='checksums.json'})
        print('Evidence: '+str(evidence.relative_to(ROOT)),flush=True)
        print('Local logs: '+str(log_dir.relative_to(ROOT)),flush=True)
    return 0 if summary['passed'] else 1

if __name__=='__main__':
    try:
        sys.exit(main())
    except Exception as e:
        print(str(e),file=sys.stderr)
        sys.exit(1)
