"""Package tracked coursework files, excluding local secrets and generated runtimes."""
from pathlib import Path
import hashlib
import json
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def main():
    revision = subprocess.check_output(['git', 'rev-parse', '--short', 'HEAD'], cwd=ROOT).decode().strip()
    names = subprocess.check_output(['git', 'ls-files', '-z'], cwd=ROOT).decode('utf-8').split('\0')
    files = []
    excluded = {'.git', '.dbg', '.verification', '.lifetrack-run', '.delivery', 'node_modules', 'target', 'venv', '__pycache__'}
    for name in sorted(filter(None, names)):
        path = Path(name)
        if excluded.intersection(path.parts) or path.name in {'.env', '.env.local'} or path.suffix in {'.log', '.pyc'}:
            continue
        if (ROOT / path).is_file():
            files.append(name)
    output = ROOT / '.delivery'
    output.mkdir(exist_ok=True)
    archive = output / f'LifeTrack_2411006_{revision}.zip'
    hashes = {name: hashlib.sha256((ROOT / name).read_bytes()).hexdigest() for name in files}
    manifest = {'author': '邓宇轩', 'studentId': '2411006', 'sourceCommit': revision,
                'videoIncluded': False, 'files': hashes}
    with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED) as package:
        for name in files:
            package.write(ROOT / name, 'LifeTrack/' + name)
        package.writestr('LifeTrack/交付文件校验清单.json', json.dumps(manifest, ensure_ascii=False, indent=2))
    checksum = hashlib.sha256(archive.read_bytes()).hexdigest()
    archive.with_suffix('.zip.sha256').write_text(f'{checksum}  {archive.name}\n', encoding='utf-8')
    # Verify every packaged source against its original bytes, not just the archive's existence.
    with zipfile.ZipFile(archive) as package:
        assert package.testzip() is None
        for name, expected in hashes.items():
            assert hashlib.sha256(package.read('LifeTrack/' + name)).hexdigest() == expected
    print(json.dumps({'archive': str(archive), 'files': len(files), 'sha256': checksum}, ensure_ascii=False))

if __name__ == '__main__':
    main()
