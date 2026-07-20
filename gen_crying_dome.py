#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
gen_crying_dome.py
==================
Generates 4 interlocking quarter-dome WorldEdit schematics (Sponge v2).
Each quarter has ~30 000 randomised "crying" blocks.
Stand at ONE spot in-game and paste Q1 Q2 Q3 Q4 -> full sphere sky dome.

Palette:
  crying_obsidian         55%  purple drip (standard)
  blue_glazed_terracotta  20%  "blue crying obsidian"
  warped_wart_block       15%  "green crying obsidian"
  obsidian                10%  dark accent

Drip note:
  Radius = 60 blocks.  Vanilla crying obsidian drip particles last ~40 ticks
  which is enough for a 40-block fall.  The dome equator sits at y=0 (player
  feet) so the bottom ring drips hit the floor instantly.  For drips from the
  apex (y=60) to reach the ground paste the dome 20 blocks lower (stand in a
  20-deep pit, paste, fill pit) or use the CryingDripTask.java server plugin
  printed at the end of this script to manually spawn long-lasting particles.
"""

import struct, gzip, io, os, random, sys

# ── Force UTF-8 on Windows terminals ──────────────────────────────────────────
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')

# =============================================================================
# CONFIG
# =============================================================================

RADIUS       = 60           # sphere radius in blocks
SHELL_THICK  = 3            # wall thickness  (outer_r - inner_r)
INNER_R      = RADIUS - SHELL_THICK
SEED         = 20250719     # RNG seed (change for different colour mix)

# Block palette weights
PALETTE_WEIGHTS = [
    ('minecraft:crying_obsidian',        55),
    ('minecraft:blue_glazed_terracotta', 20),
    ('minecraft:warped_wart_block',      15),
    ('minecraft:obsidian',               10),
]

# Build flat weighted pool (O(1) random pick)
_POOL: list = []
for _blk, _w in PALETTE_WEIGHTS:
    _POOL.extend([_blk] * _w)

def pick(rng: random.Random) -> str:
    return rng.choice(_POOL)

# =============================================================================
# NBT HELPERS  (stdlib only)
# =============================================================================

def _nb(name: str) -> bytes:
    e = name.encode('utf-8')
    return struct.pack('>H', len(e)) + e

def nbt_int    (n, v): return bytes([3])  + _nb(n) + struct.pack('>i', v)
def nbt_short  (n, v): return bytes([2])  + _nb(n) + struct.pack('>h', v)
def nbt_intarr (n, v): return bytes([11]) + _nb(n) + struct.pack('>i', len(v)) + b''.join(struct.pack('>i', x) for x in v)
def nbt_bytearr(n, v): return bytes([7])  + _nb(n) + struct.pack('>i', len(v)) + bytes(v)
def nbt_compound(n, *c): return bytes([10]) + _nb(n) + b''.join(c) + bytes([0])

def varint(v: int) -> list:
    out = []
    while True:
        b = v & 0x7F; v >>= 7
        if v: b |= 0x80
        out.append(b)
        if not v: break
    return out

# =============================================================================
# SCHEMATIC BUILDER  (WorldEdit Sponge Schematic v2)
# =============================================================================

def build_schem(W: int, H: int, L: int,
                grid: dict,
                offset: list,
                data_version: int = 3953) -> bytes:
    """
    grid   : {(x,y,z): 'minecraft:block_name'}
    offset : [ox, oy, oz]
        WorldEdit uses this offset when you run //paste -o.
        Block at schematic pos (0,0,0) lands at (player + offset).
        We set offset so the sphere CENTRE always lands at the player.
    Iteration: Y outer, Z middle, X inner  (WorldEdit standard order).
    """
    blocks  = sorted(set(grid.values()) | {'minecraft:air'})
    palette = {b: i for i, b in enumerate(blocks)}

    block_data: list = []
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
    )

    buf = io.BytesIO()
    with gzip.GzipFile(fileobj=buf, mode='wb') as gz:
        gz.write(root)
    return buf.getvalue()

# =============================================================================
# QUARTER-DOME GEOMETRY
# =============================================================================
#
#  The full sphere is cut into 4 vertical wedges (like orange quarters):
#
#   Q1: x >= 0, z >= 0   (North-East)   schematic origin = sphere centre
#   Q2: x <= 0, z >= 0   (North-West)   sphere centre at schem x=R
#   Q3: x <= 0, z <= 0   (South-West)   sphere centre at schem (R, y, R)
#   Q4: x >= 0, z <= 0   (South-East)   sphere centre at schem z=R
#
#  In the schematic the sphere centre sits at Y = RADIUS (mid-height).
#  X and Z of the centre depend on the quadrant (see offsets below).
#
#  A block at schematic (sx, sy, sz) has sphere-local coords:
#    lx = sx * sign_x        ly = sy - RADIUS        lz = sz * sign_z
#  and is in the shell when:
#    INNER_R^2 <= lx^2 + ly^2 + lz^2 <= RADIUS^2
#
#  Offset table (so //paste -o puts sphere centre at player feet):
#
#   Q1  sign_x=+1  sign_z=+1   centre at sch(0, R, 0)   offset=[  0, -R,   0]
#   Q2  sign_x=-1  sign_z=+1   centre at sch(R, R, 0)   offset=[ -R, -R,   0]
#   Q3  sign_x=-1  sign_z=-1   centre at sch(R, R, R)   offset=[ -R, -R,  -R]
#   Q4  sign_x=+1  sign_z=-1   centre at sch(0, R, R)   offset=[  0, -R,  -R]

R  = RADIUS
H  = 2 * R + 1    # full sphere height in schematic  (y: 0..2R, centre=R)
WL = R + 1        # width & length per quarter  (x or z: 0..R)

QUARTERS = [
    # name,           sign_x, sign_z,  offset
    ('crying_dome_q1', +1,    +1,    [  0, -R,   0]),
    ('crying_dome_q2', -1,    +1,    [ -R, -R,   0]),
    ('crying_dome_q3', -1,    -1,    [ -R, -R,  -R]),
    ('crying_dome_q4', +1,    -1,    [  0, -R,  -R]),
]


def make_quarter(sign_x: int, sign_z: int, rng: random.Random) -> dict:
    """Return a block-grid dict for one quarter of the sphere shell."""
    outer2 = R * R
    inner2 = INNER_R * INNER_R
    grid: dict = {}

    for sx in range(WL):
        lx = sign_x * sx
        lx2 = lx * lx
        for sz in range(WL):
            lz = sign_z * sz
            lz2 = lz * lz
            for sy in range(H):
                ly = sy - R
                d2 = lx2 + ly * ly + lz2
                if inner2 <= d2 <= outer2:
                    grid[(sx, sy, sz)] = pick(rng)

    return grid


# =============================================================================
# BUILD + WRITE
# =============================================================================

OUT = 'crying_dome'
os.makedirs(OUT, exist_ok=True)

print('=' * 60)
print(' CRYING OBSIDIAN SKY SPHERE -- generating 4 quarter domes')
print(f' Radius: {R}  Shell: {SHELL_THICK} blocks  Height: {H}  Seed: {SEED}')
print('=' * 60)

rng = random.Random(SEED)
total_blocks = 0

for name, sx, sz, offset in QUARTERS:
    print(f'\n  Building {name} (sign_x={sx:+d}, sign_z={sz:+d}) ...')
    grid = make_quarter(sx, sz, rng)
    filled = len(grid)
    total_blocks += filled
    data = build_schem(WL, H, WL, grid, offset)
    path = os.path.join(OUT, f'{name}.schem')
    with open(path, 'wb') as f:
        f.write(data)
    print(f'    blocks : {filled:,}')
    print(f'    file   : {path}  ({len(data)/1024:.1f} KB)')

# =============================================================================
# INSTRUCTIONS  (ASCII only -- safe for Windows cp1252 terminal)
# =============================================================================

sep = '-' * 60
print()
print('=' * 60)
print(f' DONE!  {total_blocks:,} blocks across 4 schematics in {OUT}/')
print('=' * 60)
print()
print(sep)
print(' IN-GAME PASTE INSTRUCTIONS')
print(sep)
print()
print(' 1. Copy the 4 .schem files to:')
print('      plugins/WorldEdit/schematics/')
print()
print(' 2. Fly to the exact sky point where you want')
print('    the SPHERE CENTRE to be and stand still.')
print()
print(' 3. Run ALL FOUR pastes from the SAME position:')
print()
print('      //schem load crying_dome_q1')
print('      //paste -o')
print()
print('      //schem load crying_dome_q2')
print('      //paste -o')
print()
print('      //schem load crying_dome_q3')
print('      //paste -o')
print()
print('      //schem load crying_dome_q4')
print('      //paste -o')
print()
print('    The -o flag uses the stored offset so all 4 quarters')
print('    snap flush into one seamless complete sphere.')
print()
print(sep)
print(' BLOCK PALETTE')
print(sep)
print()
print('  minecraft:crying_obsidian        55%  purple drip (base)')
print('  minecraft:blue_glazed_terracotta 20%  BLUE crying obsidian')
print('  minecraft:warped_wart_block      15%  GREEN crying obsidian')
print('  minecraft:obsidian               10%  dark accent')
print()
print(sep)
print(' EXTENDED DRIP NOTE')
print(sep)
print()
print(f' Radius = {R} blocks. Vanilla drip particles last ~40 ticks (~40 blocks).')
print(f' Drips from the apex (y=+{R}) fade at y=+{R-40} -- still mid-air.')
print()
print(' To make ALL drips reach the ground:')
print()
print('  Option A -- stand in a 20-block-deep pit before pasting so the')
print(f'              sphere apex is only {R-20} blocks above the surface.')
print()
print('  Option B -- use the CryingDripTask Bukkit plugin below to spawn')
print('              server-side particles that fall the full distance.')
print()
print(sep)
print(' CryingDripTask.java  (drop-in Bukkit/Paper listener)')
print(sep)
print()
print("""  // CryingDripTask.java
  // Schedule: plugin.getServer().getScheduler()
  //               .runTaskTimer(plugin, task, 0L, 1L);
  //
  // import org.bukkit.*;
  // import org.bukkit.scheduler.BukkitRunnable;
  //
  // public class CryingDripTask extends BukkitRunnable {
  //   private final World world;
  //   private final Location center;
  //   private final int radius;
  //   public CryingDripTask(World w, Location c, int r) {
  //     world = w; center = c; radius = r;
  //   }
  //   @Override public void run() {
  //     for (int i = 0; i < 40; i++) {
  //       double phi   = Math.random() * Math.PI * 2;
  //       double theta = Math.random() * Math.PI;
  //       double x = center.getX() + radius * Math.sin(theta) * Math.cos(phi);
  //       double y = center.getY() + radius * Math.cos(theta);
  //       double z = center.getZ() + radius * Math.sin(theta) * Math.sin(phi);
  //       // Spawn at dome surface; particle falls to floor naturally
  //       world.spawnParticle(Particle.DRIPPING_OBSIDIAN_TEAR,
  //                           new Location(world, x, y, z),
  //                           1, 0.3, 0.3, 0.3, 0.0);
  //     }
  //   }
  // }
  //
  // Start it:
  //   new CryingDripTask(player.getWorld(), domeCenter, RADIUS)
  //       .runTaskTimer(plugin, 0L, 1L);
""")
print('=' * 60)
