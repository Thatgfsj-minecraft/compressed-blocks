#!/usr/bin/env python3
"""漏斗（压缩铁漏斗 / 木质漏斗）分级定义：三处共用同一份 id 与名称。

用它的地方：
  * gen_hopper_assets.py        生成 1~3 重压缩铁漏斗的资源
  * gen_wooden_hopper_assets.py 生成木质漏斗 + 1~3 重压缩木质漏斗的资源
  * gen_resources.py            写 lang 与采掘标签时补上漏斗条目（保证单独跑它也不丢条目）

每项：(压缩层级, 方块 id, 英文名, 中文名)，id 不带命名空间前缀。
"""
IRON_HOPPERS = [
    (1, "1x_compressed_hopper", "Compressed Hopper", "一重压缩漏斗"),
    (2, "2x_compressed_hopper", "Double Compressed Hopper", "二重压缩漏斗"),
    (3, "3x_compressed_hopper", "Triple Compressed Hopper", "三重压缩漏斗"),
]

WOODEN_HOPPERS = [
    (0, "wooden_hopper", "Wooden Hopper", "木质漏斗"),
    (1, "1x_compressed_wooden_hopper", "Compressed Wooden Hopper", "一重压缩木质漏斗"),
    (2, "2x_compressed_wooden_hopper", "Double Compressed Wooden Hopper", "二重压缩木质漏斗"),
    (3, "3x_compressed_wooden_hopper", "Triple Compressed Wooden Hopper", "三重压缩木质漏斗"),
]

ALL_HOPPERS = IRON_HOPPERS + WOODEN_HOPPERS
