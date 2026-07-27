"""
gen_element_pngs.py — DifficultyEngine Element Item Texture Generator
=====================================================================
Generates 16x16 PNG icon textures + item model JSON overrides for the
4 Magic Element Staffs and 4 Magic Element Runes (Fire/Water/Earth/Air),
which currently render as plain vanilla item icons with ZERO custom
texture (see MagicElement.java staffCMD/runeCMD fields — CMDs 2001-2004
for staffs, 3001-3004 for runes — were defined but never had matching
resource-pack textures/models).

This script is additive to the existing DifficultyEngine-RP/ folder
(built by gen_resourcepack.py) — it does NOT touch the existing cape or
boss assets, and it PRESERVES the existing custom_model_data overrides
already present on minecraft:item/blaze_rod.json (CMD 3001 -> the
Crimson Boss ItemDisplay visual) by merging into that file's overrides
array rather than overwriting it.

Run from the project root, AFTER gen_resourcepack.py has been run at
least once (so DifficultyEngine-RP/ exists):
    python gen_element_pngs.py

Then re-zip with the existing helper in gen_resourcepack.py's zip_pack()
logic (this script re-zips itself at the end, recomputing SHA1).

── Base material -> CMD mapping (must match MagicElement.java) ──────────────
  Staffs (2001-2004):
    FIRE  -> BLAZE_ROD            CMD 2001
    WATER -> PRISMARINE_CRYSTALS  CMD 2002
    EARTH -> EMERALD              CMD 2003
    AIR   -> FEATHER              CMD 2004
  Runes (3001-3004):
    FIRE  -> NETHER_BRICK         CMD 3001   (blaze_rod.json already uses
                                              3001 for the unrelated Crimson
                                              Boss carrier display — no
                                              conflict since it's a
                                              DIFFERENT base material/model
                                              file)
    WATER -> ICE                  CMD 3002
    EARTH -> CLAY_BALL            CMD 3003
    AIR   -> PAPER                CMD 3004
"""

import os, json, zlib, struct, zipfile, hashlib

OUT_DIR  = "DifficultyEngine-RP"
ZIP_NAME = "DifficultyEngine-RP.zip"

# ── Element definitions: name -> (base_rgb, accent_rgb) ──────────────────────
ELEMENTS = {
    "fire":  ((220,  60,  20), (255, 200,  60)),   # red-orange body, gold accent
    "water": (( 30, 120, 220), (140, 220, 255)),   # blue body, cyan accent
    "earth": (( 60, 140,  40), (150, 100,  50)),   # green body, brown accent
    "air":   ((225, 225, 235), (255, 255, 255)),   # pale grey-white body, white accent
}

# Base material + CMD for each staff/rune, matching MagicElement.java exactly
STAFF_TARGETS = {
    "fire":  ("blaze_rod",            2001),
    "water": ("prismarine_crystals",  2002),
    "earth": ("emerald",              2003),
    "air":   ("feather",              2004),
}
RUNE_TARGETS = {
    "fire":  ("nether_brick", 3001),
    "water": ("ice",          3002),
    "earth": ("clay_ball",    3003),
    "air":   ("paper",        3004),
}

# ── 16x16 STAFF pixel template (diagonal rod with glowing orb tip) ──────────
# 0=transparent 1=rod body 2=rod shadow 3=orb accent 4=orb core(white-ish)
STAFF_PIXELS = [
    [0,0,0,0,0,0,0,0,0,0,0,4,3,0,0,0],
    [0,0,0,0,0,0,0,0,0,0,4,3,3,4,0,0],
    [0,0,0,0,0,0,0,0,0,3,3,3,3,0,0,0],
    [0,0,0,0,0,0,0,0,1,3,3,0,0,0,0,0],
    [0,0,0,0,0,0,0,1,2,1,0,0,0,0,0,0],
    [0,0,0,0,0,0,1,2,1,0,0,0,0,0,0,0],
    [0,0,0,0,0,1,2,1,0,0,0,0,0,0,0,0],
    [0,0,0,0,1,2,1,0,0,0,0,0,0,0,0,0],
    [0,0,0,1,2,1,0,0,0,0,0,0,0,0,0,0],
    [0,0,1,2,1,0,0,0,0,0,0,0,0,0,0,0],
    [0,1,2,1,0,0,0,0,0,0,0,0,0,0,0,0],
    [1,2,1,0,0,0,0,0,0,0,0,0,0,0,0,0],
    [2,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0],
    [1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],
    [0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],
    [0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],
]

# ── 16x16 RUNE pixel template (diamond rune symbol) ─────────────────────────
# 0=transparent 1=rune body 2=rune shadow/edge 3=rune glow accent
RUNE_PIXELS = [
    [0,0,0,0,0,0,0,1,1,0,0,0,0,0,0,0],
    [0,0,0,0,0,0,1,3,3,1,0,0,0,0,0,0],
    [0,0,0,0,0,1,3,3,3,3,1,0,0,0,0,0],
    [0,0,0,0,1,3,3,2,2,3,3,1,0,0,0,0],
    [0,0,0,1,3,3,2,1,1,2,3,3,1,0,0,0],
    [0,0,1,3,3,2,1,1,1,1,2,3,3,1,0,0],
    [0,1,3,3,2,1,1,1,1,1,1,2,3,3,1,0],
    [1,3,3,2,1,1,1,1,1,1,1,1,2,3,3,1],
    [0,1,3,3,2,1,1,1,1,1,1,2,3,3,1,0],
    [0,0,1,3,3,2,1,1,1,1,2,3,3,1,0,0],
    [0,0,0,1,3,3,2,1,1,2,3,3,1,0,0,0],
    [0,0,0,0,1,3,3,2,2,3,3,1,0,0,0,0],
    [0,0,0,0,0,1,3,3,3,3,1,0,0,0,0,0],
    [0,0,0,0,0,0,1,3,3,1,0,0,0,0,0,0],
    [0,0,0,0,0,0,0,1,1,0,0,0,0,0,0,0],
    [0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],
]

# ── PNG writer (stdlib only — mirrors gen_resourcepack.py) ──────────────────

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

def build_pixels(template, base_rgb, accent_rgb, kind):
    r, g, b = base_rgb
    ar, ag, ab = accent_rgb
    sr, sg, sb = max(0, r - 70), max(0, g - 70), max(0, b - 70)  # shadow
    pixels = []
    if kind == "staff":
        for row in template:
            for v in row:
                if   v == 0: pixels.append((0, 0, 0, 0))
                elif v == 1: pixels.append((160, 110, 70, 255))   # wood-brown rod
                elif v == 2: pixels.append((110, 75, 45, 255))    # rod shadow
                elif v == 3: pixels.append((r, g, b, 255))        # orb accent = element color
                elif v == 4: pixels.append((min(255, r+30), min(255, g+30), min(255, b+30), 255))
    else:  # rune
        for row in template:
            for v in row:
                if   v == 0: pixels.append((0, 0, 0, 0))
                elif v == 1: pixels.append((sr, sg, sb, 255))     # dark edge
                elif v == 2: pixels.append((r, g, b, 255))        # body
                elif v == 3: pixels.append((ar, ag, ab, 255))     # glow accent
    return pixels

def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
    print(f"  {path}")

def merge_override(model_path, base_item_id, predicate_cmd, override_model_id):
    """
    Loads (or creates) a minecraft:item/<name>.json model file and adds/updates
    a custom_model_data override entry, preserving any existing overrides
    already in the file (critical for blaze_rod.json which already has the
    Crimson Boss carrier-display override at CMD 3001).
    """
    if os.path.exists(model_path):
        with open(model_path, "r", encoding="utf-8") as f:
            data = json.load(f)
    else:
        data = {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"minecraft:item/{base_item_id}"},
        }
    overrides = data.get("overrides", [])
    # Remove any existing entry for this exact CMD (idempotent re-run safety)
    overrides = [o for o in overrides if o.get("predicate", {}).get("custom_model_data") != predicate_cmd]
    overrides.append({
        "predicate": {"custom_model_data": predicate_cmd},
        "model": override_model_id
    })
    data["overrides"] = overrides
    write_json(model_path, data)

def build():
    print(f"Generating element item textures into {OUT_DIR}/ ...\n")

    for elem, (base_rgb, accent_rgb) in ELEMENTS.items():
        # ── Staff texture + model ──────────────────────────────────────────
        staff_name = f"{elem}_staff"
        staff_tex_path = f"{OUT_DIR}/assets/difficultyengine/textures/item/{staff_name}.png"
        save_png(staff_tex_path, build_pixels(STAFF_PIXELS, base_rgb, accent_rgb, "staff"))

        staff_model_path = f"{OUT_DIR}/assets/difficultyengine/models/item/{staff_name}.json"
        write_json(staff_model_path, {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"difficultyengine:item/{staff_name}"}
        })

        base_mat, cmd = STAFF_TARGETS[elem]
        merge_override(
            f"{OUT_DIR}/assets/minecraft/models/item/{base_mat}.json",
            base_mat, cmd, f"difficultyengine:item/{staff_name}"
        )

        # ── Rune texture + model ────────────────────────────────────────────
        rune_name = f"{elem}_rune"
        rune_tex_path = f"{OUT_DIR}/assets/difficultyengine/textures/item/{rune_name}.png"
        save_png(rune_tex_path, build_pixels(RUNE_PIXELS, base_rgb, accent_rgb, "rune"))

        rune_model_path = f"{OUT_DIR}/assets/difficultyengine/models/item/{rune_name}.json"
        write_json(rune_model_path, {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"difficultyengine:item/{rune_name}"}
        })

        base_mat_r, cmd_r = RUNE_TARGETS[elem]
        merge_override(
            f"{OUT_DIR}/assets/minecraft/models/item/{base_mat_r}.json",
            base_mat_r, cmd_r, f"difficultyengine:item/{rune_name}"
        )

    print("\nDone generating element textures + model overrides.")

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
