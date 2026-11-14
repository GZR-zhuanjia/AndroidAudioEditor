#!/usr/bin/env python3
"""本地读取真实 ELF 信息；也可检查解压 APK 后的 lib/arm64-v8a。"""
import pathlib
import re
import subprocess
import sys

folder = pathlib.Path(sys.argv[1])
required = ('avformat', 'avcodec', 'avutil', 'swresample', 'avfilter', 'mp3lame')
for name in required:
    path = folder / f'lib{name}.so'
    if not path.is_file():
        raise SystemExit(f'缺少真实库 {path}')
for path in sorted(folder.glob('*.so')):
    header = subprocess.check_output(['llvm-readelf', '-h', str(path)], text=True)
    if 'AArch64' not in header:
        raise SystemExit(f'非 arm64 库：{path}')
    program = subprocess.check_output(['llvm-readelf', '-lW', str(path)], text=True)
    loads = [line.split()[-1] for line in program.splitlines() if line.strip().startswith('LOAD ')]
    if not loads or any(int(value, 16) < 16384 for value in loads):
        raise SystemExit(f'ELF LOAD 对齐不足16 KB：{path}')
    dynamic = subprocess.check_output(['llvm-readelf', '-d', str(path)], text=True)
    sonames = re.findall(r'\(SONAME\).*?\[(.*?)\]', dynamic)
    if sonames != [path.name]:
        raise SystemExit(f'SONAME 不符合 Android 打包要求：{path}: {sonames}')
    for dependency in re.findall(r'\(NEEDED\).*?\[(.*?)\]', dynamic):
        if re.search(r'\.so\.', dependency) or '/' in dependency:
            raise SystemExit(f'依赖使用非 Android 库名：{path}: {dependency}')
    print(f'{path.name}: AArch64 / LOAD>=16 KB / SONAME={path.name}')
