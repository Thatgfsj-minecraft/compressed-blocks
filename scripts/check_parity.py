#!/usr/bin/env python3
"""双端一致性检查（纯只读，CI/本地均可跑）。

检查项：
1) 1.21.11/fabric/src/main/resources 与 neoforge 的 assets/、data/ 逐字节一致
   （燃料数据图已由 NeoForge datagen 产出到 src/generated/resources，不再进 main/resources）
2) 双端共享 Java 源（dev/compressedblocks/**.java，排除加载器专属包与 mixin）内容一致

加载器专属/按设计不同的部分不参与比较：
  - src/main/resources/META-INF、fabric.mod.json、*.accesswidener、*.mixins.json
  - src/main/java/dev/compressedblocks/{fabric,neoforge,mixin}
  - src/generated（NeoForge datagen 产物）

用法：python3 scripts/check_parity.py [--verbose]
退出码：0 = 一致；1 = 有差异（打印差异路径）
"""
import filecmp
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FABRIC = os.path.join(ROOT, '1.21.11', 'fabric')
NEOFORGE = os.path.join(ROOT, '1.21.11', 'neoforge')

# 参与比较的资源子树（相对工程根）
RESOURCE_SUBTREES = ('src/main/resources/assets', 'src/main/resources/data')
# 双端按设计不同、不参与比较的 Java 目录前缀
LOADER_ONLY_PREFIXES = ('src/main/java/dev/compressedblocks/fabric/',
                        'src/main/java/dev/compressedblocks/neoforge/',
                        'src/main/java/dev/compressedblocks/mixin/')

def walk_files(base, sub):
    out = []
    root = os.path.join(base, sub)
    for dirpath, _dirnames, filenames in os.walk(root):
        for name in filenames:
            rel = os.path.relpath(os.path.join(dirpath, name), base)
            out.append(rel)
    return sorted(out)

def main():
    verbose = '--verbose' in sys.argv
    problems = []

    for sub in RESOURCE_SUBTREES:
        a = walk_files(FABRIC, sub)
        b = walk_files(NEOFORGE, sub)
        for rel in sorted(set(a) ^ set(b)):
            problems.append('只在一端存在: ' + rel)
        for rel in sorted(set(a) & set(b)):
            if not filecmp.cmp(os.path.join(FABRIC, rel), os.path.join(NEOFORGE, rel), shallow=False):
                problems.append('内容不同: ' + rel)

    shared = walk_files(FABRIC, 'src/main/java/dev/compressedblocks')
    for rel in shared:
        if rel.startswith(LOADER_ONLY_PREFIXES):
            continue
        fb = os.path.join(NEOFORGE, rel)
        if not os.path.exists(fb):
            problems.append('NeoForge 缺少同源文件: ' + rel)
        elif not filecmp.cmp(os.path.join(FABRIC, rel), fb, shallow=False):
            problems.append('同源文件内容不同: ' + rel)

    if verbose:
        print('checked resources(%s) + shared java under %s' % (', '.join(RESOURCE_SUBTREES), ROOT))
    if problems:
        print('PARITY MISMATCH (%d):' % len(problems))
        for p in problems:
            print('  -', p)
        return 1
    print('PARITY OK')
    return 0

if __name__ == '__main__':
    sys.exit(main())