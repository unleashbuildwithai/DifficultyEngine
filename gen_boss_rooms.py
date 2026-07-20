#!/usr/bin/env python3
"""
Generates 6 themed boss room WorldEdit schematics (Sponge Schematic v2).
Output: boss_rooms/<name>.schem

Copy .schem files to:  plugins/WorldEdit/schematics/
Load & paste:          //schem load <name>   then   //paste

Each room is 30 × 13 × 30 blocks (interior arena: 28 × 11 × 28).
"""
import struct, gzip, io, os

# ─────────────────────────────────────────────────────────────────────────────
# Minimal NBT encoder (no external deps)
# ─────────────────────────────────────────────────────────────────────────────

def _nb(n):
    e = n.encode('utf-8')
    return struct.pack('>H', len(e)) + e

def nbt_int   (n, v): return bytes([3])  + _nb(n) + struct.pack('>i', v)
def nbt_short (n, v): return bytes([2])  + _nb(n) + struct.pack('>h', v)
def nbt_intarr(n, v): return bytes([11]) + _nb(n) + struct.pack('>i', len(v)) + b''.join(struct.pack('>i', x) for x in v)
def nbt_bytearr(n,v): return bytes([7])  + _nb(n) + struct.pack('>i', len(v)) + bytes(v)
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

# ─────────────────────────────────────────────────────────────────────────────
# Schematic builder  (WorldEdit Sponge Schematic v2)
# ─────────────────────────────────────────────────────────────────────────────

def build_schem(W, H, L, grid, data_version=3953):
    """
    grid: dict  (x, y, z) -> 'minecraft:block_name'
    Iteration order: Y outer, Z middle, X inner  (WorldEdit standard)
    """
    blocks = sorted(set(grid.values()) | {'minecraft:air'})
    palette = {b: i for i, b in enumerate(blocks)}

    block_data = []
    for y in range(H):
        for z in range(L):
            for x in range(W):
                idx = palette.get(grid.get((x, y, z), 'minecraft:air'), 0)
                block_data += varint(idx)

    pal_nbt = b''.join(nbt_int(k, v) for k, v in palette.items())

    root = nbt_compound('Schematic',
        nbt_int   ('Version',      2),
        nbt_int   ('DataVersion',  data_version),
        nbt_short ('Width',        W),
        nbt_short ('Height',       H),
        nbt_short ('Length',       L),
        nbt_intarr('Offset',       [0, 0, 0]),
        nbt_int   ('PaletteMax',   len(palette)),
        bytes([10]) + _nb('Palette') + pal_nbt + bytes([0]),
        nbt_bytearr('BlockData',   block_data),
    )

    buf = io.BytesIO()
    with gzip.GzipFile(fileobj=buf, mode='wb') as gz:
        gz.write(root)
    return buf.getvalue()

# ─────────────────────────────────────────────────────────────────────────────
# Generic room builder
# ─────────────────────────────────────────────────────────────────────────────

W, H, L = 30, 13, 30   # full schematic dimensions


def make_room(floor, wall, ceil, pillar,
              accent=None, floor_ring=None, light=None, ceil_accent=None):
    """
    Y=0          → floor
    Y=1..H-2     → walls on all 4 edges; interior = air
    Y=H-1        → ceiling
    Corners      → 2×2 pillar columns (full height)
    Accent       → scattered wall detail (every 5th+3rd position)
    Floor ring   → material filling a radius-4 circle in the centre floor
    Light        → replaces ceiling block every 7 blocks (grid pattern)
    Ceil accent  → fills corners of the ceiling for a framed look
    """
    g = {}

    # ── Floor ───────────────────────────────────────────────────────────────
    for x in range(W):
        for z in range(L):
            g[(x, 0, z)] = floor

    # ── Ceiling ─────────────────────────────────────────────────────────────
    for x in range(W):
        for z in range(L):
            mat = ceil_accent if ceil_accent and x < 3 or ceil_accent and x >= W-3 else ceil
            if light and (x % 7 == 3) and (z % 7 == 3):
                mat = light
            g[(x, H-1, z)] = mat

    # ── Walls ────────────────────────────────────────────────────────────────
    for y in range(1, H-1):
        for x in range(W):
            for z in [0, L-1]:
                use_accent = accent and ((x + y) % 6 == 0)
                g[(x, y, z)] = accent if use_accent else wall
        for z in range(1, L-1):
            for x in [0, W-1]:
                use_accent = accent and ((z + y) % 6 == 0)
                g[(x, y, z)] = accent if use_accent else wall

    # ── Corner pillars (2×2, full height) ───────────────────────────────────
    for (cx, cz) in [(1, 1), (1, L-2), (W-2, 1), (W-2, L-2)]:
        for y in range(H):
            for dx in (-1, 0):
                for dz in (-1, 0):
                    px, pz = cx + dx, cz + dz
                    if 0 <= px < W and 0 <= pz < L:
                        g[(px, y, pz)] = pillar

    # ── Central floor ring ───────────────────────────────────────────────────
    if floor_ring:
        cx2, cz2 = W // 2, L // 2
        for x in range(W):
            for z in range(L):
                if (x - cx2) ** 2 + (z - cz2) ** 2 <= 16:
                    g[(x, 0, z)] = floor_ring

    return g


# ─────────────────────────────────────────────────────────────────────────────
# 6 Boss Rooms
# ─────────────────────────────────────────────────────────────────────────────

ROOMS = {

    # ── 1. Crimson Pit — Fire / Nether ──────────────────────────────────────
    'crimson_pit': make_room(
        floor      = 'minecraft:nether_bricks',
        wall       = 'minecraft:nether_bricks',
        ceil       = 'minecraft:blackstone',
        pillar     = 'minecraft:chiseled_nether_bricks',
        accent     = 'minecraft:red_nether_bricks',
        floor_ring = 'minecraft:magma_block',
        light      = 'minecraft:glowstone',
        ceil_accent= 'minecraft:red_nether_bricks',
    ),

    # ── 2. Abyssal Chamber — Water / Ocean ──────────────────────────────────
    'abyssal_chamber': make_room(
        floor      = 'minecraft:dark_prismarine',
        wall       = 'minecraft:prismarine_bricks',
        ceil       = 'minecraft:dark_prismarine',
        pillar     = 'minecraft:prismarine',
        accent     = 'minecraft:sea_lantern',
        floor_ring = 'minecraft:prismarine',
        light      = 'minecraft:sea_lantern',
        ceil_accent= 'minecraft:prismarine_bricks',
    ),

    # ── 3. Verdant Shrine — Earth / Nature ──────────────────────────────────
    'verdant_shrine': make_room(
        floor      = 'minecraft:mossy_cobblestone',
        wall       = 'minecraft:mossy_stone_bricks',
        ceil       = 'minecraft:oak_planks',
        pillar     = 'minecraft:oak_log',
        accent     = 'minecraft:stone_bricks',
        floor_ring = 'minecraft:moss_block',
        light      = 'minecraft:shroomlight',
        ceil_accent= 'minecraft:stripped_oak_log',
    ),

    # ── 4. Tempest Sanctum — Air / Sky ──────────────────────────────────────
    'tempest_sanctum': make_room(
        floor      = 'minecraft:smooth_quartz',
        wall       = 'minecraft:quartz_block',
        ceil       = 'minecraft:glass',
        pillar     = 'minecraft:quartz_pillar',
        accent     = 'minecraft:cyan_stained_glass',
        floor_ring = 'minecraft:white_concrete',
        light      = 'minecraft:shroomlight',
        ceil_accent= 'minecraft:quartz_block',
    ),

    # ── 5. Void Sanctum — Shadow / Dark ─────────────────────────────────────
    'void_sanctum': make_room(
        floor      = 'minecraft:blackstone',
        wall       = 'minecraft:polished_blackstone_bricks',
        ceil       = 'minecraft:crying_obsidian',
        pillar     = 'minecraft:obsidian',
        accent     = 'minecraft:purple_concrete',
        floor_ring = 'minecraft:obsidian',
        light      = 'minecraft:amethyst_block',
        ceil_accent= 'minecraft:polished_blackstone',
    ),

    # ── 6. Gilded Sanctum — Ancient / Gold ──────────────────────────────────
    'gilded_sanctum': make_room(
        floor      = 'minecraft:smooth_sandstone',
        wall       = 'minecraft:sandstone',
        ceil       = 'minecraft:smooth_sandstone',
        pillar     = 'minecraft:chiseled_sandstone',
        accent     = 'minecraft:gold_block',
        floor_ring = 'minecraft:gold_block',
        light      = 'minecraft:glowstone',
        ceil_accent= 'minecraft:chiseled_sandstone',
    ),
}

# ─────────────────────────────────────────────────────────────────────────────
# Write schematics
# ─────────────────────────────────────────────────────────────────────────────

os.makedirs('boss_rooms', exist_ok=True)

print("Generating 6 boss room schematics...\n")
for name, grid in ROOMS.items():
    data = build_schem(W, H, L, grid)
    path = os.path.join('boss_rooms', f'{name}.schem')
    with open(path, 'wb') as f:
        f.write(data)
    size_kb = len(data) / 1024
    print(f"  ✓ {path:<45}  ({size_kb:.1f} KB)")

print(f"""
Done!  6 schematics written to boss_rooms/

Dimensions : {W} × {H} × {L}  (interior arena: {W-2} × {H-2} × {L-2})

To use in-game:
  1. Copy .schem files → plugins/WorldEdit/schematics/
  2. Stand where you want the NW-bottom corner of the room
  3. //schem load crimson_pit
  4. //paste
  (Repeat for each room.)

Room list:
  crimson_pit      – Fire/Nether  (nether-bricks, magma, glowstone)
  abyssal_chamber  – Water/Ocean  (prismarine, sea-lanterns)
  verdant_shrine   – Earth/Nature (mossy stone, oak, shroomlight)
  tempest_sanctum  – Air/Sky      (quartz, glass, cyan accents)
  void_sanctum     – Shadow/Dark  (obsidian, blackstone, amethyst)
  gilded_sanctum   – Ancient/Gold (sandstone, gold blocks)
""")
