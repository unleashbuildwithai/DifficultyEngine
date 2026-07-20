#!/usr/bin/env python3
"""
gen_crying_dome_particles.py
============================
Generates a tiny (1 x 3 x 1) WorldEdit schematic containing a pre-configured
repeating command block chain that sprays purple, blue, and green drip
particles throughout the crying obsidian dome automatically.

  Block (0,0,0)  Repeating command block  -- always active, no redstone needed
  Block (0,1,0)  Chain command block      -- fires immediately after block 0
  Block (0,2,0)  Chain command block      -- fires immediately after block 1

Particle commands inside:
  Purple  particle minecraft:dripping_obsidian_tear  (matches crying_obsidian)
  Blue    particle minecraft:dripping_water           (blue falling drops)
  Green   particle minecraft:spore_blossom_air        (green drifting flecks)

HOW TO USE
----------
1. Copy crying_dome_particles.schem -> plugins/WorldEdit/schematics/
2. Stand at the EXACT centre of the dome (same spot you pasted the quarters).
3. In-game:
     //schem load crying_dome_particles
     //paste -o
4. Done.  The command blocks start firing automatically every game tick.
   You will see purple, blue, and green particles falling inside the dome.

ADJUSTING PARTICLE DENSITY
---------------------------
Right-click any of the 3 command blocks to open it and change the last
number in the command (currently 15) up or down:
  15  = light drizzle
  40  = moderate rain
  80  = heavy downpour  (may affect performance on large servers)

Output: crying_dome/crying_dome_particles.schem
"""

import struct, gzip, io, os

# ---------------------------------------------------------------------------
# Particle commands
# ~ ~30 ~   = 30 blocks above the command block = mid-dome height
# 55 30 55  = delta-X, delta-Y, delta-Z  (fills the dome interior)
# 0         = particle speed (drip particles have their own gravity)
# 15        = particle count per activation
# force @a  = visible to all players regardless of distance
# ---------------------------------------------------------------------------

CMD_PURPLE = (
    "particle minecraft:dripping_obsidian_tear "
    "~ ~30 ~ 55 30 55 0 15 force @a"
)
CMD_BLUE = (
    "particle minecraft:dripping_water "
    "~ ~30 ~ 55 30 55 0 15 force @a"
)
CMD_GREEN = (
    "particle minecraft:spore_blossom_air "
    "~ ~30 ~ 55 30 55 0 15 force @a"
)

# ---------------------------------------------------------------------------
# NBT helpers (zero external dependencies)
# ---------------------------------------------------------------------------

def _nb(name):
    e = name.encode('utf-8')
    return struct.pack('>H', len(e)) + e

def nbt_byte   (n, v): return bytes([1])  + _nb(n) + struct.pack('>b', v)
def nbt_short  (n, v): return bytes([2])  + _nb(n) + struct.pack('>h', v)
def nbt_int    (n, v): return bytes([3])  + _nb(n) + struct.pack('>i', v)
def nbt_intarr (n, v): return bytes([11]) + _nb(n) + struct.pack('>i', len(v)) + b''.join(struct.pack('>i', x) for x in v)
def nbt_bytearr(n, v): return bytes([7])  + _nb(n) + struct.pack('>i', len(v)) + bytes(v)

def nbt_string(n, v):
    e = v.encode('utf-8')
    return bytes([8]) + _nb(n) + struct.pack('>H', len(e)) + e

def nbt_compound(n, *children):
    return bytes([10]) + _nb(n) + b''.join(children) + bytes([0])

def nbt_list_of_compound(n, items):
    """
    TAG_List (type 9) of TAG_Compound (element type 10).
    items : list of bytes, each being the compound payload (children + 0x00).
    Elements inside a TAG_List have NO type byte or name prefix.
    """
    return bytes([9]) + _nb(n) + bytes([10]) + struct.pack('>i', len(items)) + b''.join(items)

def varint(v):
    out = []
    while True:
        b = v & 0x7F; v >>= 7
        if v: b |= 0x80
        out.append(b)
        if not v: break
    return out

# ---------------------------------------------------------------------------
# Block entity (command block) NBT payload
# These are the *inner* bytes of a TAG_Compound within a TAG_List, so they
# carry no type byte or name — just children + TAG_End.
# ---------------------------------------------------------------------------

def make_cb_entity(x, y, z, command):
    """
    Return the payload bytes for a command-block block entity at (x,y,z).

    WorldEdit 7.4.x Sponge v2 reader requires the position as a TAG_IntArray
    named 'Pos' — NOT as separate x/y/z TAG_Int fields.
    'auto=1' means Always Active — fires without redstone.
    """
    return (
        nbt_intarr ('Pos',          [x, y, z])                 +
        nbt_string ('Id',           'minecraft:command_block') +
        nbt_string ('Command',      command)                   +
        nbt_byte   ('auto',         1)  +   # always active
        nbt_byte   ('powered',      1)  +
        nbt_byte   ('conditionMet', 1)  +
        nbt_byte   ('TrackOutput',  0)  +
        nbt_int    ('SuccessCount', 0)  +
        nbt_string ('LastOutput',   '') +
        bytes([0])                          # TAG_End closes this compound
    )

# ---------------------------------------------------------------------------
# Schematic builder with optional block-entity list
# ---------------------------------------------------------------------------

def build_schem_with_be(W, H, L, grid, block_entities=None,
                         offset=None, data_version=3953):
    """
    grid          : dict (x,y,z) -> 'minecraft:block_name[states]'
    block_entities: list of bytes (each is a make_cb_entity() payload)
    offset        : [ox, oy, oz]  paste origin shift
    """
    if offset is None:
        offset = [0, 0, 0]
    if block_entities is None:
        block_entities = []

    blocks  = sorted(set(grid.values()) | {'minecraft:air'})
    palette = {b: i for i, b in enumerate(blocks)}

    block_data = []
    for y in range(H):
        for z in range(L):
            for x in range(W):
                idx = palette.get(grid.get((x, y, z), 'minecraft:air'), 0)
                block_data += varint(idx)

    pal_nbt = b''.join(nbt_int(k, v) for k, v in palette.items())

    be_nbt = nbt_list_of_compound('BlockEntities', block_entities)

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
        be_nbt,
    )

    buf = io.BytesIO()
    with gzip.GzipFile(fileobj=buf, mode='wb') as gz:
        gz.write(root)
    return buf.getvalue()

# ---------------------------------------------------------------------------
# Build the command block schematic
#
#  Layout (Y axis going up):
#    y=2  Chain repeating CB  (green  – spore_blossom_air)
#    y=1  Chain CB            (blue   – dripping_water)
#    y=0  Repeating CB        (purple – dripping_obsidian_tear)
#
#  All facing UP so each block chains into the one above it.
#  Schematic is 1 wide (X), 3 tall (Y), 1 long (Z).
# ---------------------------------------------------------------------------

CB_REPEATING = 'minecraft:repeating_command_block[conditional=false,facing=up]'
CB_CHAIN     = 'minecraft:chain_command_block[conditional=false,facing=up]'

grid = {
    (0, 0, 0): CB_REPEATING,   # fires first — purple
    (0, 1, 0): CB_CHAIN,       # fires second — blue
    (0, 2, 0): CB_CHAIN,       # fires third  — green
}

block_entities = [
    make_cb_entity(0, 0, 0, CMD_PURPLE),
    make_cb_entity(0, 1, 0, CMD_BLUE),
    make_cb_entity(0, 2, 0, CMD_GREEN),
]

# ---------------------------------------------------------------------------
# Write schematic
# ---------------------------------------------------------------------------

OUT_DIR = 'crying_dome'
os.makedirs(OUT_DIR, exist_ok=True)

data = build_schem_with_be(
    W=1, H=3, L=1,
    grid=grid,
    block_entities=block_entities,
    offset=[0, 0, 0],
)

path = os.path.join(OUT_DIR, 'crying_dome_particles.schem')
with open(path, 'wb') as f:
    f.write(data)

print('Generated: ' + path + '  (' + str(len(data)) + ' bytes)')
print('')
print('CONTENTS')
print('--------')
print('  y=0  Repeating command block (PURPLE dripping_obsidian_tear particles)')
print('  y=1  Chain command block     (BLUE   dripping_water        particles)')
print('  y=2  Chain command block     (GREEN  spore_blossom_air     particles)')
print('')
print('HOW TO USE')
print('----------')
print('1. Copy crying_dome_particles.schem -> plugins/WorldEdit/schematics/')
print('')
print('2. Stand at the EXACT CENTRE of the dome (same spot you pasted q1-q4).')
print('')
print('3. Run these two commands:')
print('     //schem load crying_dome_particles')
print('     //paste -o')
print('')
print('4. Done!  The command blocks start firing immediately, spraying')
print('   purple, blue, and green drip particles inside the dome.')
print('')
print('ADJUSTING DENSITY')
print('-----------------')
print('Right-click any command block to open it.  Change the last number')
print('(currently 15) in the command:')
print('  15  = light drizzle  (default)')
print('  40  = moderate rain')
print('  80  = heavy downpour')
print('')
print('NOTE: enableCommandBlock must be true in server.properties')
print('  enableCommandBlock=true')
