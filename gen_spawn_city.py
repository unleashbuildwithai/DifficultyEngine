#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
gen_spawn_city.py
==================
Generates a single WorldEdit Sponge Schematic (v2) for a starter "spawn city".

Output: spawn_city/spawn_city.schem

Layout (viewed from above, +X = east, +Z = south):

    NORTH WALL (stone brick, closed)
    ┌─────────────────────────────────────┐
    │  plot   plot  |  plot   plot        │
    │  plot   plot  |  plot   plot        │  E
  W │ ──────────────┼──────────────────── │  A
  E │  plot   plot  |  [SPAWN BUILDING]   │  S
  S │  plot   plot  |  plot   plot        │  T
  T │                                       │  (wall)
    │  (open / unwalled — wilderness edge) │
    └─────────────────────────────────────┘
       (west side intentionally has NO wall —
        this is the "open wilderness edge")

Features implemented:
  • Perimeter wall on North / East / South (stone bricks, 5 tall,
    with a simple crenellation cap). West side is deliberately left
    completely open — the wilderness edge the user asked for.
  • Two main 3-wide gravel roads (one N-S, one E-W) crossing at the
    city centre, each road flanked by 1-wide stone-brick-slab
    sidewalks on both sides.
  • Dirt/grass terrain fill covering the whole footprint before roads
    and buildings are stamped on top.
  • A grid of house plots (mixed 10x10 / 15x10 / 10x15 footprints)
    filling the four quadrants created by the road cross, each with a
    simple 4-wall placeholder building, a doorway, a couple of window
    openings, a flat/pitched roof, and a short gravel driveway
    connecting the plot to the nearest sidewalk.
  • ONE detailed "spawn/starter building" placed near the main east
    gate (the first thing a new player sees on entry) — two storeys,
    an interior balcony overlooking the ground floor, stairs up to
    the balcony, and candles/lanterns for interior lighting detail.

This script has NO external dependencies (pure standard library) and
follows the exact same minimal Sponge-Schematic-v2 NBT writer used by
gen_boss_rooms.py / gen_crying_dome.py in this repo, so the output can
be dropped straight into plugins/WorldEdit/schematics/ and loaded with
//schem load spawn_city then //paste.
"""

import struct, gzip, io, os, random, sys

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')

# =============================================================================
# CONFIG
# =============================================================================

SEED = 20260725
RNG  = random.Random(SEED)

W = 96   # width  (X)
L = 96   # length (Z)
H = 34   # height (Y) — generous headroom for 2-storey buildings + wall caps

FOUNDATION_DEPTH = 3          # dirt layers below the surface
GROUND_Y         = FOUNDATION_DEPTH          # y-index of the grass/road surface layer
WALL_HEIGHT      = 5           # perimeter wall height above ground
WALL_MATERIAL    = 'minecraft:stone_bricks'
WALL_CAP         = 'minecraft:stone_brick_wall'

ROAD_WIDTH       = 3
ROAD_MATERIAL    = 'minecraft:gravel'
SIDEWALK_MATERIAL= 'minecraft:smooth_stone_slab'
GRASS_MATERIAL   = 'minecraft:grass_block'
DIRT_MATERIAL    = 'minecraft:dirt'
DRIVEWAY_MATERIAL= 'minecraft:coarse_dirt'

# House plot wall/roof material variants (cycled through per plot for variety)
HOUSE_VARIANTS = [
    {'wall': 'minecraft:oak_planks',        'log': 'minecraft:oak_log',        'roof': 'minecraft:oak_stairs',        'floor': 'minecraft:oak_planks'},
    {'wall': 'minecraft:spruce_planks',      'log': 'minecraft:spruce_log',      'roof': 'minecraft:spruce_stairs',      'floor': 'minecraft:spruce_planks'},
    {'wall': 'minecraft:cobblestone',        'log': 'minecraft:oak_log',        'roof': 'minecraft:dark_oak_stairs',    'floor': 'minecraft:oak_planks'},
    {'wall': 'minecraft:birch_planks',       'log': 'minecraft:birch_log',       'roof': 'minecraft:birch_stairs',       'floor': 'minecraft:birch_planks'},
]

# =============================================================================
# NBT HELPERS  (stdlib only — mirrors gen_boss_rooms.py / gen_crying_dome.py)
# =============================================================================

def _nb(name: str) -> bytes:
    e = name.encode('utf-8')
    return struct.pack('>H', len(e)) + e

def nbt_int(n, v):    return bytes([3])  + _nb(n) + struct.pack('>i', v)
def nbt_short(n, v):  return bytes([2])  + _nb(n) + struct.pack('>h', v)
def nbt_intarr(n, v): return bytes([11]) + _nb(n) + struct.pack('>i', len(v)) + b''.join(struct.pack('>i', x) for x in v)
def nbt_bytearr(n, v):return bytes([7])  + _nb(n) + struct.pack('>i', len(v)) + bytes(v)
def nbt_compound(n, *c): return bytes([10]) + _nb(n) + b''.join(c) + bytes([0])

def varint(v: int) -> list:
    out = []
    while True:
        b = v & 0x7F
        v >>= 7
        if v:
            b |= 0x80
        out.append(b)
        if not v:
            break
    return out


def build_schem(width, height, length, grid, data_version=3953):
    """grid: dict (x, y, z) -> 'minecraft:block_name'. Missing = air."""
    blocks = sorted(set(grid.values()) | {'minecraft:air'})
    palette = {b: i for i, b in enumerate(blocks)}

    block_data = []
    for y in range(height):
        for z in range(length):
            for x in range(width):
                idx = palette.get(grid.get((x, y, z), 'minecraft:air'), 0)
                block_data += varint(idx)

    pal_nbt = b''.join(nbt_int(k, v) for k, v in palette.items())

    root = nbt_compound(
        'Schematic',
        nbt_int('Version', 2),
        nbt_int('DataVersion', data_version),
        nbt_short('Width', width),
        nbt_short('Height', height),
        nbt_short('Length', length),
        nbt_intarr('Offset', [0, 0, 0]),
        nbt_int('PaletteMax', len(palette)),
        bytes([10]) + _nb('Palette') + pal_nbt + bytes([0]),
        nbt_bytearr('BlockData', block_data),
    )

    buf = io.BytesIO()
    with gzip.GzipFile(fileobj=buf, mode='wb') as gz:
        gz.write(root)
    return buf.getvalue()


# =============================================================================
# TERRAIN + ROADS
# =============================================================================

def set_block(grid, x, y, z, mat):
    if 0 <= x < W and 0 <= y < H and 0 <= z < L:
        grid[(x, y, z)] = mat


def fill_box(grid, x0, y0, z0, x1, y1, z1, mat):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                set_block(grid, x, y, z, mat)


def add_terrain(grid):
    """Dirt foundation + grass surface across the entire footprint."""
    for x in range(W):
        for z in range(L):
            for y in range(FOUNDATION_DEPTH):
                set_block(grid, x, y, z, DIRT_MATERIAL)
            set_block(grid, x, GROUND_Y, z, GRASS_MATERIAL)


def add_perimeter_wall(grid):
    """
    Stone-brick wall on North (z=0), East (x=W-1) and South (z=L-1).
    West (x=0) is intentionally left OPEN — the wilderness edge.
    A simple wall-block crenellation caps the top course.
    """
    top = GROUND_Y + WALL_HEIGHT

    # North wall (z = 0)
    for x in range(W):
        fill_box(grid, x, GROUND_Y + 1, 0, x, top, 0, WALL_MATERIAL)
        set_block(grid, x, top + 1, 0, WALL_CAP)

    # South wall (z = L-1)
    for x in range(W):
        fill_box(grid, x, GROUND_Y + 1, L - 1, x, top, L - 1, WALL_MATERIAL)
        set_block(grid, x, top + 1, L - 1, WALL_CAP)

    # East wall (x = W-1) — main entrance gate is carved into this wall
    gate_z0 = L // 2 - (ROAD_WIDTH // 2) - 1
    gate_z1 = L // 2 + (ROAD_WIDTH // 2) + 1
    for z in range(L):
        if gate_z0 <= z <= gate_z1:
            continue  # leave a gate opening where the main road exits east
        fill_box(grid, W - 1, GROUND_Y + 1, z, W - 1, top, z, WALL_MATERIAL)
        set_block(grid, W - 1, top + 1, z, WALL_CAP)

    # West side: NO wall placed at all — open wilderness edge.


def add_roads_and_sidewalks(grid):
    """
    One N-S road (constant X band) and one E-W road (constant Z band),
    crossing at the city centre. Both get gravel road surface plus a
    1-wide stone-brick-slab sidewalk on either side.
    """
    half = ROAD_WIDTH // 2
    cx = W // 2
    cz = L // 2

    # N-S road (runs along Z, centred on X = cx)
    for x in range(cx - half, cx + half + 1):
        for z in range(L):
            set_block(grid, x, GROUND_Y, z, ROAD_MATERIAL)
    # sidewalks flanking the N-S road
    for z in range(L):
        set_block(grid, cx - half - 1, GROUND_Y, z, SIDEWALK_MATERIAL)
        set_block(grid, cx + half + 1, GROUND_Y, z, SIDEWALK_MATERIAL)

    # E-W road (runs along X, centred on Z = cz)
    for z in range(cz - half, cz + half + 1):
        for x in range(W):
            set_block(grid, x, GROUND_Y, z, ROAD_MATERIAL)
    # sidewalks flanking the E-W road
    for x in range(W):
        set_block(grid, x, GROUND_Y, cz - half - 1, SIDEWALK_MATERIAL)
        set_block(grid, x, GROUND_Y, cz + half + 1, SIDEWALK_MATERIAL)


# =============================================================================
# HOUSE PLOTS
# =============================================================================

def add_house_plot(grid, x0, z0, w, l, variant_idx, road_side):
    """
    Builds a simple placeholder house occupying the footprint
    (x0..x0+w-1, z0..z0+l-1). Adds:
      • 4 corner posts + plank walls, one door opening, 2 window cuts
      • a flat capped roof (stair-block eave trim for a little detail)
      • a short driveway of coarse dirt connecting the plot to the
        nearest road-facing sidewalk (direction given by road_side:
        'N','S','E','W').
    """
    variant = HOUSE_VARIANTS[variant_idx % len(HOUSE_VARIANTS)]
    wall_mat  = variant['wall']
    log_mat   = variant['log']
    roof_mat  = variant['roof']
    floor_mat = variant['floor']

    base_y = GROUND_Y + 1
    wall_h = 4
    top_y  = base_y + wall_h

    # Floor
    fill_box(grid, x0, base_y - 1, z0, x0 + w - 1, base_y - 1, z0 + l - 1, floor_mat)

    # 4 corner log posts
    for (cx, cz) in [(x0, z0), (x0 + w - 1, z0), (x0, z0 + l - 1), (x0 + w - 1, z0 + l - 1)]:
        fill_box(grid, cx, base_y, cz, cx, top_y - 1, cz, log_mat)

    # Perimeter walls (skip corners — already posts)
    for x in range(x0 + 1, x0 + w - 1):
        fill_box(grid, x, base_y, z0, x, top_y - 1, z0, wall_mat)
        fill_box(grid, x, base_y, z0 + l - 1, x, top_y - 1, z0 + l - 1, wall_mat)
    for z in range(z0 + 1, z0 + l - 1):
        fill_box(grid, x0, base_y, z, x0, top_y - 1, z, wall_mat)
        fill_box(grid, x0 + w - 1, base_y, z, x0 + w - 1, top_y - 1, z, wall_mat)

    # Doorway — centre of the wall facing the road
    door_air_y0 = base_y
    door_air_y1 = base_y + 1
    if road_side == 'S':
        dx = x0 + w // 2
        set_block(grid, dx, door_air_y0, z0 + l - 1, 'minecraft:air')
        set_block(grid, dx, door_air_y1, z0 + l - 1, 'minecraft:air')
    elif road_side == 'N':
        dx = x0 + w // 2
        set_block(grid, dx, door_air_y0, z0, 'minecraft:air')
        set_block(grid, dx, door_air_y1, z0, 'minecraft:air')
    elif road_side == 'E':
        dz = z0 + l // 2
        set_block(grid, x0 + w - 1, door_air_y0, dz, 'minecraft:air')
        set_block(grid, x0 + w - 1, door_air_y1, dz, 'minecraft:air')
    else:  # 'W'
        dz = z0 + l // 2
        set_block(grid, x0, door_air_y0, dz, 'minecraft:air')
        set_block(grid, x0, door_air_y1, dz, 'minecraft:air')

    # A couple of simple window cuts (glass pane) on the two side walls
    win_y = base_y + 2
    if w >= 6:
        set_block(grid, x0 + 2, win_y, z0, 'minecraft:glass_pane')
        set_block(grid, x0 + w - 3, win_y, z0, 'minecraft:glass_pane')
        set_block(grid, x0 + 2, win_y, z0 + l - 1, 'minecraft:glass_pane')
        set_block(grid, x0 + w - 3, win_y, z0 + l - 1, 'minecraft:glass_pane')
    if l >= 6:
        set_block(grid, x0, win_y, z0 + 2, 'minecraft:glass_pane')
        set_block(grid, x0, win_y, z0 + l - 3, 'minecraft:glass_pane')
        set_block(grid, x0 + w - 1, win_y, z0 + 2, 'minecraft:glass_pane')
        set_block(grid, x0 + w - 1, win_y, z0 + l - 3, 'minecraft:glass_pane')

    # Flat roof slab with a stair-trim eave around the border
    fill_box(grid, x0, top_y, z0, x0 + w - 1, top_y, z0 + l - 1, wall_mat)
    for x in range(x0, x0 + w):
        set_block(grid, x, top_y + 1, z0, roof_mat)
        set_block(grid, x, top_y + 1, z0 + l - 1, roof_mat)
    for z in range(z0, z0 + l):
        set_block(grid, x0, top_y + 1, z, roof_mat)
        set_block(grid, x0 + w - 1, top_y + 1, z, roof_mat)

    # Interior hollow
    fill_box(grid, x0 + 1, base_y, z0 + 1, x0 + w - 2, top_y - 1, z0 + l - 2, 'minecraft:air')

    # Driveway stub (3 blocks long) from the door towards the road side
    drive_len = 3
    if road_side == 'S':
        fill_box(grid, x0 + w // 2 - 1, GROUND_Y, z0 + l, x0 + w // 2 + 1, GROUND_Y, z0 + l + drive_len, DRIVEWAY_MATERIAL)
    elif road_side == 'N':
        fill_box(grid, x0 + w // 2 - 1, GROUND_Y, z0 - drive_len - 1, x0 + w // 2 + 1, GROUND_Y, z0 - 1, DRIVEWAY_MATERIAL)
    elif road_side == 'E':
        fill_box(grid, x0 + w, GROUND_Y, z0 + l // 2 - 1, x0 + w + drive_len, GROUND_Y, z0 + l // 2 + 1, DRIVEWAY_MATERIAL)
    else:  # 'W'
        fill_box(grid, x0 - drive_len - 1, GROUND_Y, z0 + l // 2 - 1, x0 - 1, GROUND_Y, z0 + l // 2 + 1, DRIVEWAY_MATERIAL)


def layout_house_plots(grid):
    """
    Fills the 4 quadrants created by the road cross with a patchwork of
    10x10 / 15x10 / 10x15 house plots, each separated by a small gap,
    and facing whichever road/sidewalk is nearest.

    The NE quadrant is reserved (skipped here) for the detailed spawn
    building, added separately by add_spawn_building().
    """
    half = ROAD_WIDTH // 2
    cx, cz = W // 2, L // 2
    margin = 4  # gap from the perimeter wall / sidewalks

    quadrants = [
        # (x_start, x_end, z_start, z_end, road_side_facing, is_spawn_quadrant)
        (margin,          cx - half - 3, margin,          cz - half - 3, 'S', False),  # NW
        (cx + half + 3,   W - margin - 1, margin,         cz - half - 3, 'S', True),   # NE (spawn bldg here)
        (margin,          cx - half - 3, cz + half + 3,  L - margin - 1, 'N', False),  # SW
        (cx + half + 3,   W - margin - 1, cz + half + 3, L - margin - 1, 'N', False),  # SE
    ]

    plot_sizes = [(10, 10), (15, 10), (10, 15)]
    variant_idx = 0

    for (xs, xe, zs, ze, road_side, is_spawn_quad) in quadrants:
        if is_spawn_quad:
            continue  # handled by add_spawn_building()

        x = xs
        while x < xe:
            z = zs
            row_height = 0
            while z < ze:
                w, l = plot_sizes[variant_idx % len(plot_sizes)]
                if x + w > xe or z + l > ze:
                    z += 4
                    continue
                add_house_plot(grid, x, z, w, l, variant_idx, road_side)
                row_height = max(row_height, l)
                variant_idx += 1
                z += l + 3  # gap between plots
            x += 16  # column gap (largest plot width + margin)


# =============================================================================
# DETAILED SPAWN / STARTER BUILDING (near main east gate)
# =============================================================================

def add_spawn_building(grid):
    """
    A larger, two-storey starter building placed in the NE quadrant near
    the main east gate — the first proper structure a new player sees.

    Details:
      • Stone-brick + oak-log frame, bigger footprint (18 x 14)
      • Ground floor open hall + a raised interior balcony overlooking it
      • Oak stairs leading up to the balcony
      • Candles + lanterns for interior lighting detail
      • A small covered porch facing the road with stairs down to the
        sidewalk/driveway
    """
    x0, z0 = W - 26, L // 2 - 24   # NE quadrant, close to the east gate road
    w, l   = 18, 14
    base_y = GROUND_Y + 1
    wall_h = 5
    top_y  = base_y + wall_h
    floor2_y = base_y + 3  # balcony/2nd floor height

    wall_mat  = 'minecraft:stone_bricks'
    log_mat   = 'minecraft:oak_log'
    floor_mat = 'minecraft:oak_planks'
    roof_mat  = 'minecraft:dark_oak_stairs'
    stair_mat = 'minecraft:oak_stairs'

    # Ground floor slab
    fill_box(grid, x0, base_y - 1, z0, x0 + w - 1, base_y - 1, z0 + l - 1, floor_mat)

    # Corner log posts (full height)
    for (cx, cz) in [(x0, z0), (x0 + w - 1, z0), (x0, z0 + l - 1), (x0 + w - 1, z0 + l - 1)]:
        fill_box(grid, cx, base_y, cz, cx, top_y - 1, cz, log_mat)

    # Perimeter walls
    for x in range(x0 + 1, x0 + w - 1):
        fill_box(grid, x, base_y, z0, x, top_y - 1, z0, wall_mat)
        fill_box(grid, x, base_y, z0 + l - 1, x, top_y - 1, z0 + l - 1, wall_mat)
    for z in range(z0 + 1, z0 + l - 1):
        fill_box(grid, x0, base_y, z, x0, top_y - 1, z, wall_mat)
        fill_box(grid, x0 + w - 1, base_y, z, x0 + w - 1, top_y - 1, z, wall_mat)

    # Main double-door entrance facing the east road/gate
    door_z0 = z0 + l // 2 - 1
    door_z1 = z0 + l // 2 + 1
    for dz in range(door_z0, door_z1 + 1):
        set_block(grid, x0 + w - 1, base_y, dz, 'minecraft:air')
        set_block(grid, x0 + w - 1, base_y + 1, dz, 'minecraft:air')

    # Windows along the long walls
    win_y = base_y + 2
    for wx in (x0 + 3, x0 + 7, x0 + 11, x0 + 14):
        set_block(grid, wx, win_y, z0, 'minecraft:glass_pane')
        set_block(grid, wx, win_y, z0 + l - 1, 'minecraft:glass_pane')

    # Roof (flat + eave trim, matching the plain-house style but larger)
    fill_box(grid, x0, top_y, z0, x0 + w - 1, top_y, z0 + l - 1, wall_mat)
    for x in range(x0, x0 + w):
        set_block(grid, x, top_y + 1, z0, roof_mat)
        set_block(grid, x, top_y + 1, z0 + l - 1, roof_mat)
    for z in range(z0, z0 + l):
        set_block(grid, x0, top_y + 1, z, roof_mat)
        set_block(grid, x0 + w - 1, top_y + 1, z, roof_mat)

    # Hollow out the interior (ground floor + upper balcony void)
    fill_box(grid, x0 + 1, base_y, z0 + 1, x0 + w - 2, top_y - 1, z0 + l - 2, 'minecraft:air')

    # ── Interior balcony ────────────────────────────────────────────────
    # A raised platform along the back third of the building (away from
    # the entrance), overlooking the open ground-floor hall below.
    balcony_depth = 4
    balcony_z0 = z0 + 1
    balcony_z1 = z0 + balcony_depth
    fill_box(grid, x0 + 1, floor2_y, balcony_z0, x0 + w - 2, floor2_y, balcony_z1, floor_mat)
    # Balcony railing (fence) facing the open hall
    for x in range(x0 + 1, x0 + w - 1):
        set_block(grid, x, floor2_y + 1, balcony_z1, 'minecraft:oak_fence')

    # Stairs up to the balcony, placed against the north interior wall
    stair_x = x0 + 2
    for i in range(3):
        set_block(grid, stair_x + i, base_y + i, z0 + l - 2, stair_mat)
        # simple solid step block beneath each stair for support
        set_block(grid, stair_x + i, base_y + i - 1, z0 + l - 2, wall_mat)

    # ── Interior lighting detail: candles + lanterns ───────────────────
    # Candles on small table-like blocks scattered on the ground floor
    candle_spots = [
        (x0 + 4, base_y, z0 + 4),
        (x0 + w - 5, base_y, z0 + 4),
        (x0 + 4, base_y, z0 + l - 4),
    ]
    for (cx, cy, czp) in candle_spots:
        set_block(grid, cx, cy, czp, 'minecraft:oak_fence')
        set_block(grid, cx, cy + 1, czp, 'minecraft:candle')

    # Hanging lanterns from the ceiling along the centre aisle
    for x in range(x0 + 3, x0 + w - 3, 4):
        set_block(grid, x, top_y - 1, z0 + l // 2, 'minecraft:lantern')

    # A couple of candles up on the balcony too
    set_block(grid, x0 + w // 2, floor2_y + 1, balcony_z0 + 1, 'minecraft:candle')

    # ── Small covered porch on the entrance side ───────────────────────
    porch_depth = 3
    for pz in range(door_z0 - 1, door_z1 + 2):
        set_block(grid, x0 + w, base_y - 1, pz, floor_mat)
    for pz in (door_z0 - 1, door_z1 + 1):
        fill_box(grid, x0 + w, base_y, pz, x0 + w, base_y + 2, pz, log_mat)
    for px in range(x0 + w, x0 + w + porch_depth):
        set_block(grid, px, base_y + 3, door_z0 - 1, roof_mat)
        set_block(grid, px, base_y + 3, door_z1 + 1, roof_mat)
    fill_box(grid, x0 + w, base_y + 3, door_z0, x0 + w + porch_depth - 1, base_y + 3, door_z1, wall_mat)

    # Steps down from the porch to the driveway/sidewalk
    for i in range(porch_depth):
        set_block(grid, x0 + w + i, base_y - 1 - 0, door_z0 + (door_z1 - door_z0) // 2, stair_mat)

    # Driveway connecting the porch straight to the east sidewalk/road
    drive_z = door_z0 + (door_z1 - door_z0) // 2
    fill_box(grid, x0 + w + porch_depth, GROUND_Y, drive_z - 1,
                    W - 1,               GROUND_Y, drive_z + 1, DRIVEWAY_MATERIAL)


# =============================================================================
# MAIN BUILD
# =============================================================================

def main():
    grid = {}

    print("Building spawn city terrain...")
    add_terrain(grid)

    print("Building perimeter wall (N/E/S closed, W open wilderness edge)...")
    add_perimeter_wall(grid)

    print("Building roads + sidewalks...")
    add_roads_and_sidewalks(grid)

    print("Laying out house plots...")
    layout_house_plots(grid)

    print("Building detailed spawn/starter building near the east gate...")
    add_spawn_building(grid)

    print(f"Serialising schematic ({len(grid)} placed blocks)...")
    data = build_schem(W, H, L, grid)

    os.makedirs('spawn_city', exist_ok=True)
    out_path = os.path.join('spawn_city', 'spawn_city.schem')
    with open(out_path, 'wb') as f:
        f.write(data)

    size_kb = len(data) / 1024
    print(f"\nDone! Wrote {out_path} ({size_kb:.1f} KB)")
    print(f"Dimensions: {W} x {H} x {L}  (X x Y x Z)")
    print("""
To use in-game:
  1. Copy spawn_city/spawn_city.schem -> plugins/WorldEdit/schematics/
  2. Stand at the schematic's origin corner (min X, min Y, min Z)
  3. //schem load spawn_city
  4. //paste
  (The WEST side (-X) of the build has no wall — that's the open
   wilderness edge. The main gate + detailed starter building are on
   the EAST side, near the centre.)
""")


if __name__ == '__main__':
    main()
