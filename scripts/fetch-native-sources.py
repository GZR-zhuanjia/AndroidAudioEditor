#!/usr/bin/env python3
"""仅供本地使用。获取锁定的真实源码；不安装工具、不编译。"""
import hashlib
import json
import pathlib
import subprocess
import tarfile
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
LOCK = json.loads((ROOT / "native/sources.lock.json").read_text())
SRC = ROOT / ".native/src"
SRC.mkdir(parents=True, exist_ok=True)


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


ffmpeg = SRC / "ffmpeg"
if not ffmpeg.exists():
    git("init", str(ffmpeg))
if git("-C", str(ffmpeg), "status", "--porcelain"):
    raise SystemExit("FFmpeg 源码有改动，请先保留改动后再继续；脚本不会覆盖。")
revision = LOCK["ffmpeg"]["commit"]
try:
    git("-C", str(ffmpeg), "cat-file", "-e", revision + "^{commit}")
except subprocess.CalledProcessError:
    git("-C", str(ffmpeg), "fetch", "--depth=1", LOCK["ffmpeg"]["repository"], revision)
git("-C", str(ffmpeg), "checkout", "--detach", revision)
assert git("-C", str(ffmpeg), "rev-parse", "HEAD") == revision

lame = SRC / "lame"
archive = SRC / "lame-3.100.tar.gz"
if not archive.exists():
    partial = archive.with_suffix(".part")
    try:
        with urllib.request.urlopen(LOCK["lame"]["url"], timeout=60) as response, partial.open("wb") as output:
            while block := response.read(128 * 1024):
                output.write(block)
        partial.replace(archive)
    finally:
        partial.unlink(missing_ok=True)
if archive.stat().st_size != LOCK["lame"]["bytes"] or hashlib.sha256(archive.read_bytes()).hexdigest() != LOCK["lame"]["sha256"]:
    raise SystemExit("LAME 压缩包大小/校验失败，请删除压缩包后重新获取。")
# 每次检查已有源码是否仍与原始压缩包一致。只解压普通文件/目录，不跟随链接。
with tarfile.open(archive) as package:
    members = package.getmembers()
    for member in members:
        parts = pathlib.PurePosixPath(member.name).parts
        if not parts or parts[0] != "lame-3.100" or ".." in parts or not (member.isfile() or member.isdir()):
            raise SystemExit("LAME 压缩包包含非预期路径/类型。")
        relative = pathlib.Path(*parts[1:])
        target = lame / relative
        if member.isdir():
            target.mkdir(parents=True, exist_ok=True)
            continue
        data = package.extractfile(member).read()
        if target.exists():
            if target.is_symlink() or target.read_bytes() != data:
                raise SystemExit(f"LAME 源码有改动，拒绝覆盖：{relative}")
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            target.chmod(member.mode & 0o777)
print("真实源码已就绪：FFmpeg 7.1.5 / LAME 3.100；未编译。")
