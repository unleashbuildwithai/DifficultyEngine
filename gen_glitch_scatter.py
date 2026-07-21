#!/usr/bin/env python3
"""
gen_glitch_scatter.py
=====================
Generates a 60 × 20 × 60 WorldEdit schematic filled with a chaotic "glitch"
scatter of purple, blue, green, and black blocks at ~12% density.

Paste it repeatedly across your world, rotating 90° each time, to create a
"what is this" environmental chaos effect that covers caves AND the surface.

  Blocks used:
    Purple  — crying_obsidian, amethyst_block, purpur_block
    Blue    — blue_ice, lapis_block, warped_nylium
    Black   — blackstone, basalt, obsidian
    Green   — emerald_block, slime_block, lime_concrete

USAGE
-----
1. Run:   python gen_glitch_scatter.py
2. Copy   crying_dome/glitch_scatter.schem
          →  plugins/WorldEdit/schematics/glitch_scatter.schem
3. In-game, stand at the centre of the area you want glitched:
     //schem load glitch_scatter
     //paste -a          ← -a skips AIR so existing world is preserved
4. Rotate and paste again:
     //rotate 0 90 0
     //paste -a
   Repeat at 180° and 270° for full coverage.
5. For cave/underground coverage, descend Y levels and paste again.

ADJUSTING DENSITY
-----------------
Change FILL_CHANCE below.  0.12 = 12%, 0.20 = 20%, 0.05 = very sparse.
"""

import struct, gzip, io, os, random

# ---------------------------------------------------------------------------
# Tunables
# ---------------------------------------------------------------------------

SCHEMATIC_W = 60          # X (East↔West)
SCHEMATIC_H = 20          # Y (height — covers surface + shallow caves)
SCHEMATIC_L = 60          # Z (North↔South)
FILL_CHANCE = 0.12         # 12% — "here and there" scatter density
CLUSTER_CHANCE = 0.35      # 35% of filled blocks try to extend into a mini-cluster
SEED = 42                  # Deterministic output; change for a different pattern

# Glitch block palette (evenly weighted)
GLITCH_BLOCKS = [
    # Purple
    "minecraft:crying_obsidian",
    "minecraft:amethyst_block",
    "minecraft:purpur_block",
    # Blue
    "minecraft:blue_ice",
    "minecraft:lapis_block",
    "minecraft:warped_nylium",
    # Black
    "minecraft:blackstone",
    "minecraft:basalt",
    "minecraft:obsidian",
    # Green
    "minecraft:emerald_block",
    "minecraft:slime_block",
    "minecraft:lime_concrete",
]

# ---------------------------------------------------------------------------
# NBT helpers (no external dependencies)
# ---------------------------------------------------------------------------

def _nb(name):
    e = name.encode('utf-8')
    return struct.pack('>H', len(e)) + e

def nbt_short  (n, v): return bytes([2])  + _nb(n) + struct.pack('>h', v)
def nbt_int    (n, v): return bytes([3])  + _nb(n) + struct.pack('>i', v)
def nbt_intarr (n, v): return bytes([11]) + _nb(n) + struct.pack('>i', len(v)) + b''.join(struct.pack('>i', x) for x in v)
def nbt_bytearr(n, v): return bytes([7])  + _nb(n) + struct.pack('>i', len(v)) + bytes(v)

def nbt_compound(n, *children):
    return bytes([10]) + _nb(n) + b''.join(children) + bytes([0])

def varint(v):
    out = []
    while True:
        b = v & 0x7F; v >>= 7
        if v: b |= 0x80
        out.append(b)
        if not v: break
    return out

# ---------------------------------------------------------------------------
# Build schematic
# ---------------------------------------------------------------------------

def build_scatter_schem(W, H, L, grid, offset=None, data_version=3953):
    """Build a Sponge v2 schematic from a coord→blockstate dict."""
    if offset is None:
        offset = [0, 0, 0]

    blocks  = sorted(set(grid.values()) | {'minecraft:air'})
    palette = {b: i for i, b in enumerate(blocks)}

    block_data = []
    for y in range(H):
        for z in range(L):
            for x in range(W):
                idx = palette.get(grid.get((x, y, z), 'minecraft:air'), 0)
                block_data += varint(idx)

    pal_nbt = b''.join(nbt_int(k, v) for k, v in palette.items())

    root = nbt_compound('Schematic',
        nbt_int    ('Version',     2),
        nbt_int    ('DataVersion', data_version),
        nbt_short  ('Width',       W),
        nbt_short  ('Height',      H),
        nbt_short  ('Length',      L),
        nbt_intarr ('Offset',      offset),
        nbt_int    ('PaletteMax',  len(palette)),
        bytes([10]) + _nb('Palette') + pal_nbt + bytes([0]),
        nbt_bytearr('BlockData',   block_data),
        # Empty BlockEntities list
        bytes([9]) + _nb('BlockEntities') + bytes([10]) + struct.pack('>i', 0),
    )

    buf = io.BytesIO()
    with gzip.GzipFile(fileobj=buf, mode='wb') as gz:
        gz.write(root)
    return buf.getvalue()

# ---------------------------------------------------------------------------
# Generate the glitch grid
# ---------------------------------------------------------------------------

rng = random.Random(SEED)
grid = {}

W, H, L = SCHEMATIC_W, SCHEMATIC_H, SCHEMATIC_L

for y in range(H):
    for z in range(L):
        for x in range(W):
            if rng.random() < FILL_CHANCE:
                block = rng.choice(GLITCH_BLOCKS)
                grid[(x, y, z)] = block

                # Occasional cluster: extend 1-2 adjacent blocks with the same material
                if rng.random() < CLUSTER_CHANCE:
                    for _ in range(rng.randint(1, 3)):
                        dx = rng.choice([-1, 0, 0, 0, 1])
                        dy = rng.choice([-1, 0, 0, 1])
                        dz = rng.choice([-1, 0, 0, 0, 1])
                        nx, ny, nz = x + dx, y + dy, z + dz
                        if 0 <= nx < W and 0 <= ny < H and 0 <= nz < L:
                            # 60% chance same block, 40% chance different chaos block
                            grid[(nx, ny, nz)] = block if rng.random() < 0.6 else rng.choice(GLITCH_BLOCKS)

filled = len(grid)
total  = W * H * L
pct    = filled / total * 100

print(f"Generated glitch scatter: {filled}/{total} blocks filled ({pct:.1f}%)")
print(f"Dimensions: {W}W × {H}H × {L}L")
print()

# ---------------------------------------------------------------------------
# Write schematic
# ---------------------------------------------------------------------------

OUT_DIR = 'crying_dome'
os.makedirs(OUT_DIR, exist_ok=True)

data = build_scatter_schem(W, H, L, grid, offset=[0, 0, 0])
path = os.path.join(OUT_DIR, 'glitch_scatter.schem')
with open(path, 'wb') as f:
    f.write(data)

print(f'Written: {path}  ({len(data)} bytes)')
print()
print('BLOCKS USED')
print('-----------')
by_type = {}
for blk in grid.values():
    by_type[blk] = by_type.get(blk, 0) + 1
for blk, count in sorted(by_type.items(), key=lambda x: -x[1]):
    print(f'  {count:5d}  {blk}')
print()
print('HOW TO USE')
print('----------')
print('1. Copy crying_dome/glitch_scatter.schem')
print('   -> plugins/WorldEdit/schematics/glitch_scatter.schem')
print()
print('2. Stand at centre of target area (works surface + underground):')
print('     //schem load glitch_scatter')
print('     //paste -a          (preserves existing world, only places blocks)')
print()
print('3. Rotate 90 degrees and paste again for different coverage:')
print('     //rotate 0 90 0')
print('     //paste -a')
print()
print('4. Repeat at 180, 270, and at different Y levels for caves.')
print()
print('TIP: Paste the same schematic overlapping itself rotated -- the result')
print('     looks like a corrupted reality effect since each rotation uses a')
print('     different subset of the 12% fill, creating ~40% total coverage')
print('     with varied block types at overlapping positions.')


