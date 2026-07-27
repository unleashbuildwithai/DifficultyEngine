"""
gen_skill_icon_pngs.py — DifficultyEngine Skill GUI Icon Texture Generator
===========================================================================
Generates 16x16 PNG icon textures + item model JSON overrides for the 8
skill icons shown in the /mystats GUI (SkillGUI.java), which currently
render as plain vanilla icons (IRON_SWORD, BOW, SHIELD, BONE,
BLAZE_POWDER, IRON_AXE, FISHING_ROD, DIAMOND_HOE) with no custom texture.

These are SEPARATE from the existing skill CAPES (cape_melee.png etc,
CMD 1001-1010) — this script targets the small skill icon shown in the
stats GUI panel itself.

Custom Model Data IDs used (must match SkillGUI.java after edit):
  MELEE       -> IRON_SWORD    CMD 4001
  RANGED      -> BOW           CMD 4002
  DEFENCE     -> SHIELD        CMD 4003
  PRAYER      -> BONE          CMD 4004
  MAGIC       -> BLAZE_POWDER  CMD 4005
  WOODCUTTING -> IRON_AXE      CMD 4006
  FISHING     -> FISHING_ROD   CMD 4007
  FARMING     -> DIAMOND_HOE   CMD 4008

Run from the project root, AFTER gen_resourcepack.py has been run at
least once (so DifficultyEngine-RP/ exists):
    python gen_skill_icon_pngs.py
"""

import os, json, zlib, struct, zipfile, hashlib

OUT_DIR  = "DifficultyEngine-RP"
ZIP_NAME = "DifficultyEngine-RP.zip"

# name -> (base_material, cmd, primary_rgb, accent_rgb, symbol_shape)
SKILLS = {
    "melee":       ("iron_sword",   4001, (200, 200, 210), (230, 60, 60),   "sword"),
    "ranged":      ("bow",          4002, (150, 110,  60), (80, 200, 90),   "bow"),
    "defence":     ("shield",       4003, (120, 120, 220), (200, 200, 230), "shield"),
    "prayer":      ("bone",         4004, (235, 235, 225), (255, 255, 255), "cross"),
    "magic":       ("blaze_powder", 4005, (210, 60, 220),  (255, 200, 255), "star"),
    "woodcutting": ("iron_axe",     4006, (90, 150, 60),   (140, 100, 60),  "axe"),
    "fishing":     ("fishing_rod",  4007, (60, 160, 200),  (140, 220, 255), "wave"),
    "farming":     ("diamond_hoe",  4008, (220, 170, 60),  (140, 90, 40),   "wheat"),
}

# ── Shape templates (16x16, 0=transparent 1=body 2=shadow 3=accent) ─────────

SWORD = [
    [0]*16 for _ in range(16)
]
def _fill_sword():
    g = SWORD
    for i in range(10):
        x = 3 + i
        y = 1 + i
        if 0 <= x < 16 and 0 <= y < 16: g[y][x] = 1
        if 0 <= x+1 < 16 and 0 <= y < 16: g[y][x+1] = 2
    for i in range(3):
        g[11+i][2+i] = 3
        g[11+i][3+i] = 3
    g[10][1] = 3; g[10][4] = 3
    return g

SHIELD = [[0]*16 for _ in range(16)]
def _fill_shield():
    g = SHIELD
    for y in range(2, 13):
        for x in range(4, 12):
            g[y][x] = 1
    for y in range(2, 13):
        g[y][4] = 2; g[y][11] = 2
    for x in range(4, 12):
        g[2][x] = 2
    for y in range(13, 15):
        for x in range(6, 10):
            g[y][x] = 2
    for y in range(5, 9):
        for x in range(6, 10):
            g[y][x] = 3
    return g

CROSS = [[0]*16 for _ in range(16)]
def _fill_cross():
    g = CROSS
    for y in range(2, 14):
        g[y][7] = 1; g[y][8] = 1
    for x in range(4, 12):
        g[5][x] = 1; g[5][x] = 1
    for x in range(3, 13):
        g[5][x] = 1
    return g

STAR = [[0]*16 for _ in range(16)]
def _fill_star():
    pts = [(8,1),(9,6),(14,7),(10,9),(11,14),(8,11),(5,14),(6,9),(2,7),(7,6)]
    g = STAR
    # simple filled diamond/star approx
    for y in range(16):
        for x in range(16):
            d = abs(x-8) + abs(y-8)
            if d <= 3: g[y][x] = 1
            elif d <= 5 and (x==8 or y==8 or abs(x-8)==abs(y-8)): g[y][x] = 3
    return g

AXE = [[0]*16 for _ in range(16)]
def _fill_axe():
    g = AXE
    for i in range(11):
        x = 2 + i; y = 13 - i
        if 0 <= x < 16 and 0 <= y < 16: g[y][x] = 2
    for y in range(1, 7):
        for x in range(7, 14):
            if (x-10)**2 + (y-4)**2 <= 12:
                g[y][x] = 1
    for y in range(2, 6):
        for x in range(9, 12):
            g[y][x] = 3
    return g

WAVE = [[0]*16 for _ in range(16)]
def _fill_wave():
    g = WAVE
    for x in range(16):
        y1 = 6 + int(2*((x%8)/8.0))
        y2 = 10 + int(2*(((x+4)%8)/8.0))
        if 0 <= y1 < 16: g[y1][x] = 1
        if 0 <= y2 < 16: g[y2][x] = 3
    for i in range(8):
        g[2+i][2] = 2
        g[1+i][3] = 2
    return g

WHEAT = [[0]*16 for _ in range(16)]
def _fill_wheat():
    g = WHEAT
    for y in range(2, 14):
        g[y][8] = 2
    for i in range(5):
        y = 3 + i*2
        g[y][6+ (i%2)] = 1
        g[y][10-(i%2)] = 1
        g[y][7] = 3
        g[y][9] = 3
    return g

BUILDERS = {
    "sword": _fill_sword, "shield": _fill_shield, "cross": _fill_cross,
    "star": _fill_star, "axe": _fill_axe, "wave": _fill_wave, "wheat": _fill_wheat,
    "bow": _fill_shield,  # placeholder reuse; overridden below with dedicated bow shape
}

def _fill_bow():
    g = [[0]*16 for _ in range(16)]
    for y in range(2, 14):
        x = 5 + int(3 * abs(8 - y) / 6)
        g[y][x] = 1
        g[y][x+1] = 2
    for y in range(3, 13):
        g[y][10] = 3
    return g
BUILDERS["bow"] = _fill_bow

def _make_chunk(tag: bytes, data: bytes) -> bytes:
    crc = zlib.crc32(tag + data) & 0xffffffff
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", crc)

def save_png(path: str, pixels: list):
    W = H = 16
    raw = b""
    for y in range(H):
        raw += b"\x00"
        for x in range(W):
            r, g, b, a = pixels[y * W + x]
            raw += bytes([r, g, b, a])
    ihdr = struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0)
    idat = zlib.compress(raw, 9)
    data = (b"\x89PNG\r\n\x1a\n" + _make_chunk(b"IHDR", ihdr)
            + _make_chunk(b"IDAT", idat) + _make_chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(data)

def build_pixels(shape_grid, primary_rgb, accent_rgb):
    r, g, b = primary_rgb
    ar, ag, ab = accent_rgb
    sr, sg, sb = max(0, r-80), max(0, g-80), max(0, b-80)
    pixels = []
    for row in shape_grid:
        for v in row:
            if   v == 0: pixels.append((0, 0, 0, 0))
            elif v == 1: pixels.append((r, g, b, 255))
            elif v == 2: pixels.append((sr, sg, sb, 255))
            elif v == 3: pixels.append((ar, ag, ab, 255))
            else: pixels.append((0,0,0,0))
    return pixels

def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
    print(f"  {path}")

def merge_override(model_path, base_item_id, predicate_cmd, override_model_id):
    if os.path.exists(model_path):
        with open(model_path, "r", encoding="utf-8") as f:
            data = json.load(f)
    else:
        data = {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"minecraft:item/{base_item_id}"},
        }
    overrides = data.get("overrides", [])
    overrides = [o for o in overrides if o.get("predicate", {}).get("custom_model_data") != predicate_cmd]
    overrides.append({
        "predicate": {"custom_model_data": predicate_cmd},
        "model": override_model_id
    })
    data["overrides"] = overrides
    write_json(model_path, data)

def build():
    print(f"Generating skill icon textures into {OUT_DIR}/ ...\n")
    for name, (base_mat, cmd, primary_rgb, accent_rgb, shape) in SKILLS.items():
        icon_name = f"skill_{name}"
        tex_path = f"{OUT_DIR}/assets/difficultyengine/textures/item/{icon_name}.png"
        grid = BUILDERS[shape]()
        save_png(tex_path, build_pixels(grid, primary_rgb, accent_rgb))

        model_path = f"{OUT_DIR}/assets/difficultyengine/models/item/{icon_name}.json"
        write_json(model_path, {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"difficultyengine:item/{icon_name}"}
        })

        merge_override(
            f"{OUT_DIR}/assets/minecraft/models/item/{base_mat}.json",
            base_mat, cmd, f"difficultyengine:item/{icon_name}"
        )
    print("\nDone generating skill icon textures + model overrides.")

def zip_pack():
    print(f"\nRe-zipping -> {ZIP_NAME}")
    with zipfile.ZipFile(ZIP_NAME, "w", zipfile.ZIP_DEFLATED) as zf:
        for root, dirs, files in os.walk(OUT_DIR):
            for file in files:
                full = os.path.join(root, file)
                arc  = os.path.relpath(full, OUT_DIR)
                zf.write(full, arc)
    sha1 = hashlib.sha1()
    with open(ZIP_NAME, "rb") as f:
        sha1.update(f.read())
    digest = sha1.hexdigest()
    size = os.path.getsize(ZIP_NAME)
    print(f"\n{ZIP_NAME} ({size:,} bytes)")
    print(f"SHA1: {digest}")
    return digest

if __name__ == "__main__":
    build()
    digest = zip_pack()
    print("\nUpdate server.properties resource-pack-sha1 to:")
    print(f"  resource-pack-sha1={digest}")
