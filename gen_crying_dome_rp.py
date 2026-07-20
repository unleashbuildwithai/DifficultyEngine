#!/usr/bin/env python3
"""
gen_crying_dome_rp.py
=====================
Generates a Minecraft resource pack that makes the dome blocks look like
they are crying in their respective colors.

  crying_obsidian        -> unchanged purple drip  (native game mechanic)
  blue_glazed_terracotta -> retextured as BLUE crying obsidian
  warped_wart_block      -> retextured as GREEN crying obsidian

The textures use pixel-art drip/tear designs so every block looks like it
is weeping downward in its matching color, complementing the real purple
drip particles that crying_obsidian emits natively.

NOTE ON DRIP PARTICLES
----------------------
In vanilla Minecraft the ONLY block that naturally emits drip particles is
crying_obsidian (purple tears).  There is no blue or green drip particle
block in vanilla without mods.

This resource pack gives the blue and green blocks the VISUAL APPEARANCE of
crying their color (dark base + glowing colored veins + drip-art pixels at
the bottom of the texture).  Combined with the real purple drips from the
crying obsidian blocks scattered throughout the dome, the sky looks like it
is weeping purple, blue, AND green all at once.

If you need ACTUAL blue particle drips on a Spigot/Paper server, add the
companion function file (crying_dome_particles.mcfunction) and schedule it
to run every 10 ticks:
  execute as @a at @s run particle dripping_water ~ ~60 ~ 60 60 60 0.02 200

Run:   python gen_crying_dome_rp.py
Out:   crying_dome_rp/   +   crying_dome_rp.zip
"""

import os, json, zlib, struct, zipfile, hashlib

# ---------------------------------------------------------------------------
# 16x16 pixel pattern shared by all crying variants
# 0 = dark base    1 = mid vein    2 = bright vein    3 = glow / drip highlight
# The bottom 4 rows are the "drip" zone — heavy with 3s for teardrop look
# ---------------------------------------------------------------------------

CRYING_PAT = [
    # row  0  – top edge (sparse veins)
    [0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0],
    # row  1
    [0,1,0,0,2,0,0,0,1,0,0,2,0,0,1,0],
    # row  2
    [1,0,2,0,0,1,0,2,0,1,0,0,2,0,0,1],
    # row  3  – vein cluster
    [0,2,0,0,1,0,2,0,0,2,0,1,0,0,2,0],
    # row  4
    [0,0,1,2,0,0,0,1,2,0,0,0,1,2,0,0],
    # row  5  – glow node
    [2,0,0,0,3,0,1,0,0,3,0,0,0,2,0,1],
    # row  6
    [0,1,0,2,0,1,0,0,2,0,1,0,2,0,0,2],
    # row  7  – mid band
    [0,0,2,0,0,2,1,0,0,2,0,2,0,0,2,0],
    # row  8
    [1,0,0,1,2,0,0,2,1,0,0,1,2,0,0,1],
    # row  9  – glow node
    [0,2,0,0,0,3,0,0,0,2,0,0,0,3,0,0],
    # row 10
    [0,0,2,0,2,0,1,2,0,0,2,0,2,0,1,0],
    # row 11  – transition into drip zone
    [2,0,0,2,0,0,2,0,2,0,0,2,0,0,2,0],
    # row 12  – drip zone start — teardrops gathering
    [0,3,0,0,3,0,0,3,0,3,0,0,3,0,0,3],
    # row 13  – drip stems
    [3,0,3,0,0,3,0,0,3,0,3,0,0,3,0,0],
    # row 14  – drip tips (brightest glow)
    [0,3,0,3,0,0,3,0,0,3,0,3,0,0,3,0],
    # row 15  – bottom glowing edge (the "crying" lip)
    [3,0,3,0,3,0,3,3,0,3,0,3,0,3,0,3],
]

# ---------------------------------------------------------------------------
# Color palettes for each variant
# Each is  (dark_rgb, mid_rgb, bright_rgb, glow_rgb)
# ---------------------------------------------------------------------------

VARIANTS = {
    # block id                  dark              mid               bright            glow
    'blue_glazed_terracotta': (
        (  6,  6, 38),    # near-black navy
        ( 35, 55,160),    # dark blue vein
        ( 75,120,250),    # bright blue vein
        ( 50,190,255),    # cyan-blue glow / drip
    ),
    'warped_wart_block': (
        (  4, 22,  8),    # near-black dark green
        ( 25,110, 45),    # dark green vein
        ( 55,195, 75),    # bright green vein
        ( 90,255,120),    # lime-green glow / drip
    ),
}

# ---------------------------------------------------------------------------
# PNG writer — stdlib only, no Pillow needed
# ---------------------------------------------------------------------------

def _chunk(tag, data):
    crc = zlib.crc32(tag + data) & 0xFFFFFFFF
    return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', crc)

def make_png_16x16(pixels):
    """
    pixels : list of 256 (r, g, b, a) tuples, row-major top-to-bottom.
    Returns raw PNG bytes.
    """
    raw = b''
    for y in range(16):
        raw += b'\x00'                       # filter: None
        for x in range(16):
            r, g, b, a = pixels[y * 16 + x]
            raw += bytes([r, g, b, a])
    ihdr = struct.pack('>IIBBBBB', 16, 16, 8, 6, 0, 0, 0)   # 8-bit RGBA
    return (
        b'\x89PNG\r\n\x1a\n'
        + _chunk(b'IHDR', ihdr)
        + _chunk(b'IDAT', zlib.compress(raw, 9))
        + _chunk(b'IEND', b'')
    )

def build_texture_pixels(dark, mid, bright, glow):
    """Turn CRYING_PAT + 4 colours into 256 RGBA tuples."""
    palette = [dark, mid, bright, glow]
    out = []
    for row in CRYING_PAT:
        for v in row:
            r, g, b = palette[v]
            out.append((r, g, b, 255))
    return out

# ---------------------------------------------------------------------------
# Assemble resource pack
# ---------------------------------------------------------------------------

OUT_DIR  = 'crying_dome_rp'
ZIP_NAME = 'crying_dome_rp.zip'

os.makedirs(OUT_DIR, exist_ok=True)

print('Building crying_dome_rp resource pack ...')
print('')

# pack.mcmeta
mcmeta_path = os.path.join(OUT_DIR, 'pack.mcmeta')
os.makedirs(os.path.dirname(mcmeta_path) or '.', exist_ok=True)
with open(mcmeta_path, 'w') as f:
    json.dump({
        "pack": {
            "pack_format": 34,
            "description": "Crying Dome - Blue and Green Crying Obsidian"
        }
    }, f, indent=2)
print('  ' + mcmeta_path)

# Block textures
tex_dir = os.path.join(OUT_DIR, 'assets', 'minecraft', 'textures', 'block')
os.makedirs(tex_dir, exist_ok=True)

for block_id, (dark, mid, bright, glow) in VARIANTS.items():
    pixels  = build_texture_pixels(dark, mid, bright, glow)
    png     = make_png_16x16(pixels)
    path    = os.path.join(tex_dir, block_id + '.png')
    with open(path, 'wb') as f:
        f.write(png)
    print('  ' + path + '  (' + str(len(png)) + ' bytes)')

# Block model overrides so blue_glazed_terracotta shows same texture on all 6 faces
# (vanilla it has a rotated pattern; we want our crying texture straight on every face)
model_dir = os.path.join(OUT_DIR, 'assets', 'minecraft', 'models', 'block')
os.makedirs(model_dir, exist_ok=True)

for block_id in VARIANTS:
    model = {
        "parent": "minecraft:block/cube_all",
        "textures": {
            "all": "minecraft:block/" + block_id
        }
    }
    path = os.path.join(model_dir, block_id + '.json')
    with open(path, 'w') as f:
        json.dump(model, f, indent=2)
    print('  ' + path)

print('')

# ---------------------------------------------------------------------------
# Zip
# ---------------------------------------------------------------------------

print('Zipping -> ' + ZIP_NAME)
with zipfile.ZipFile(ZIP_NAME, 'w', zipfile.ZIP_DEFLATED) as zf:
    for root, dirs, files in os.walk(OUT_DIR):
        for fn in files:
            full = os.path.join(root, fn)
            arc  = os.path.relpath(full, OUT_DIR)
            zf.write(full, arc)
            print('  + ' + arc)

sha1 = hashlib.sha1()
with open(ZIP_NAME, 'rb') as f:
    sha1.update(f.read())
digest = sha1.hexdigest()
size   = os.path.getsize(ZIP_NAME)

print('')
print('Done!  ' + ZIP_NAME + '  (' + str(size) + ' bytes)')
print('SHA1: ' + digest)
print('')
print('SETUP')
print('-----')
print('1. Host crying_dome_rp.zip on a web server (or GitHub Releases).')
print('2. Add to server.properties:')
print('     resource-pack=<direct URL to crying_dome_rp.zip>')
print('     resource-pack-sha1=' + digest)
print('     resource-pack-required=false')
print('')
print('WHAT PLAYERS SEE')
print('----------------')
print('  crying_obsidian        - dark purple, glowing cracks, REAL purple drip particles')
print('  blue_glazed_terracotta - dark navy, glowing blue cracks, blue teardrop pixel art')
print('  warped_wart_block      - dark forest, glowing green cracks, green teardrop pixel art')
print('')
print('All three block types have the same "crying obsidian" aesthetic in their own color.')
print('The crying_obsidian blocks emit REAL purple particle drips (native Minecraft).')
print('The blue and green blocks have the crying LOOK via texture (bottom rows = teardrop art).')
print('')
print('FOR ACTUAL BLUE/GREEN PARTICLE DRIPS (Paper/Spigot server):')
print('  Schedule this command every 10 ticks to spray blue dripping_water particles:')
print('    particle dripping_water ~ ~55 ~ 55 55 55 0.01 150 force @a')
print('  And this for green (closest vanilla green = spore blossom particles):')
print('    particle spore_blossom_air ~ ~55 ~ 55 55 55 0.01 150 force @a')
