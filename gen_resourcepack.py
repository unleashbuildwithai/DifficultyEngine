"""
gen_resourcepack.py — DifficultyEngine Resource Pack Generator
=============================================================
Generates a Minecraft 1.21-compatible resource pack that replaces
the ELYTRA item icon with flat cape-shaped icons for each skill cape.

Run from the project root:
    python gen_resourcepack.py

Output:
    DifficultyEngine-RP/          ← source folder
    DifficultyEngine-RP.zip       ← deployable resource pack

Server setup (server.properties):
    resource-pack=<URL to DifficultyEngine-RP.zip>
    resource-pack-required=false
    resource-pack-prompt=§dClick to load §5DifficultyEngine§d cape textures!

Hosting options (choose one):
  • Upload DifficultyEngine-RP.zip to a web server and paste its direct URL.
  • Use GitHub Releases: create a release and attach the zip as an asset.
  • Use https://mc-packs.net/ for easy Minecraft-optimised hosting.
"""

import os, json, zlib, struct, zipfile, hashlib

# ── Output directory ──────────────────────────────────────────────────────
OUT_DIR  = "DifficultyEngine-RP"
ZIP_NAME = "DifficultyEngine-RP.zip"
PACK_FORMAT = 34   # Minecraft 1.21

# ── Cape definitions (name → custom_model_data, base RGB) ────────────────
CAPES = {
    "melee":       (1001, (220,  40,  40), "⚔ Melee Cape"),
    "ranged":      (1002, ( 40, 200,  80), "🏹 Ranged Cape"),
    "defence":     (1003, ( 60, 120, 220), "🛡 Defence Cape"),
    "prayer":      (1004, (220, 200, 160), "✟ Prayer Cape"),
    "magic":       (1005, (150,  40, 220), "✦ Magic Cape"),
    "woodcutting": (1006, ( 60, 160,  50), "🪓 WC Cape"),
    "fishing":     (1007, ( 40, 180, 180), "🐟 Fishing Cape"),
    "farming":     (1008, (200, 130,  40), "🛒 Farming Cape"),
    "max":         (1009, (220, 180,  20), "★ Max Cape"),
    "boss":        (1010, ( 80,   0, 140), "☠ Boss Cape"),
}

# ── 16×16 Cape silhouette ─────────────────────────────────────────────────
# 0 = transparent  1 = body  2 = shadow/edge  3 = clasp (gold)
# Row 0 = TOP of the texture (row 15 = bottom)
CAPE_PIXELS = [
    [0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],   # row 0  transparent
    [0,0,0,3,3,3,3,3,3,3,3,3,3,0,0,0],   # row 1  clasp top
    [0,0,3,3,3,3,3,3,3,3,3,3,3,3,0,0],   # row 2  clasp body
    [0,1,2,1,1,1,1,1,1,1,1,1,1,2,1,0],   # row 3  shoulder edge
    [0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0],   # row 4
    [0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0],   # row 5
    [0,1,2,1,1,1,1,1,1,1,1,1,1,2,1,0],   # row 6  mid crease
    [0,0,1,1,1,1,1,1,1,1,1,1,1,1,0,0],   # row 7
    [0,0,0,1,1,1,1,1,1,1,1,1,1,0,0,0],   # row 8
    [0,0,0,0,1,2,1,1,1,1,2,1,0,0,0,0],   # row 9  lower crease
    [0,0,0,0,1,1,1,1,1,1,1,1,0,0,0,0],   # row 10
    [0,0,0,0,0,1,1,1,1,1,1,0,0,0,0,0],   # row 11
    [0,0,0,0,0,1,2,1,1,2,1,0,0,0,0,0],   # row 12 lower crease
    [0,0,0,0,0,0,1,1,1,1,0,0,0,0,0,0],   # row 13
    [0,0,0,0,0,0,0,1,1,0,0,0,0,0,0,0],   # row 14 tip
    [0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],   # row 15 transparent
]

# ── PNG writer (stdlib only — no Pillow required) ─────────────────────────

def _make_chunk(tag: bytes, data: bytes) -> bytes:
    crc = zlib.crc32(tag + data) & 0xffffffff
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", crc)

def save_png(path: str, pixels: list):
    """Save a 16×16 RGBA image.  pixels = list of 256 (r,g,b,a) tuples."""
    W = H = 16
    raw = b""
    for y in range(H):
        raw += b"\x00"                               # filter: None
        for x in range(W):
            r, g, b, a = pixels[y * W + x]
            raw += bytes([r, g, b, a])

    ihdr = struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0)  # 8-bit RGBA
    idat = zlib.compress(raw, 9)

    data = (
        b"\x89PNG\r\n\x1a\n"
        + _make_chunk(b"IHDR", ihdr)
        + _make_chunk(b"IDAT", idat)
        + _make_chunk(b"IEND", b"")
    )
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(data)

def build_cape_pixels(base_rgb):
    """Return 256 RGBA tuples for the 16×16 cape sprite."""
    r, g, b = base_rgb
    # Shadow: 40 % darker
    sr, sg, sb = max(0, r - 80), max(0, g - 80), max(0, b - 80)
    # Clasp: gold
    cr, cg, cb = 210, 170, 20

    pixels = []
    for row in CAPE_PIXELS:
        for v in row:
            if   v == 0: pixels.append((0,   0,   0,   0))    # transparent
            elif v == 1: pixels.append((r,   g,   b,   255))  # body
            elif v == 2: pixels.append((sr,  sg,  sb,  255))  # shadow/edge
            elif v == 3: pixels.append((cr,  cg,  cb,  255))  # clasp
    return pixels

# ── JSON helpers ──────────────────────────────────────────────────────────

def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
    print(f"  {path}")

# ── Build the resource pack ───────────────────────────────────────────────

def build():
    print(f"Building resource pack → {OUT_DIR}/")

    # pack.mcmeta
    write_json(f"{OUT_DIR}/pack.mcmeta", {
        "pack": {
            "pack_format": PACK_FORMAT,
            "description": "§5DifficultyEngine §7— Custom Cape Textures"
        }
    })

    # Cape model JSON files (one per cape)
    for key, (cmd, rgb, display) in CAPES.items():
        tex_path   = f"difficultyengine:item/cape_{key}"
        model_path = f"{OUT_DIR}/assets/difficultyengine/models/item/cape_{key}.json"
        write_json(model_path, {
            "parent": "minecraft:item/generated",
            "textures": {
                "layer0": tex_path
            }
        })

    # Elytra override model (replaces minecraft's elytra.json)
    overrides = []
    for key, (cmd, rgb, display) in sorted(CAPES.items(), key=lambda x: x[1][0]):
        overrides.append({
            "predicate": {"custom_model_data": cmd},
            "model": f"difficultyengine:item/cape_{key}"
        })

    write_json(f"{OUT_DIR}/assets/minecraft/models/item/elytra.json", {
        "parent": "minecraft:item/generated",
        "textures": {
            "layer0": "minecraft:item/elytra"
        },
        "overrides": overrides
    })

    # Cape texture PNGs
    for key, (cmd, rgb, display) in CAPES.items():
        tex_path = f"{OUT_DIR}/assets/difficultyengine/textures/item/cape_{key}.png"
        pixels   = build_cape_pixels(rgb)
        save_png(tex_path, pixels)
        print(f"  {tex_path}")

    print("Done.")

# ── Zip the pack ──────────────────────────────────────────────────────────

def zip_pack():
    print(f"\nZipping → {ZIP_NAME}")
    with zipfile.ZipFile(ZIP_NAME, "w", zipfile.ZIP_DEFLATED) as zf:
        for root, dirs, files in os.walk(OUT_DIR):
            for file in files:
                full = os.path.join(root, file)
                arc  = os.path.relpath(full, OUT_DIR)
                zf.write(full, arc)
                print(f"  + {arc}")

    # Print SHA1 for server.properties
    sha1 = hashlib.sha1()
    with open(ZIP_NAME, "rb") as f:
        sha1.update(f.read())
    digest = sha1.hexdigest()

    size = os.path.getsize(ZIP_NAME)
    print(f"\n✅  {ZIP_NAME}  ({size:,} bytes)")
    print(f"   SHA1: {digest}")
    print()
    print("─── Server Setup ────────────────────────────────────────────────")
    print("Add these lines to  server.properties  (replace <URL>):")
    print()
    print("   resource-pack=<URL_TO_DifficultyEngine-RP.zip>")
    print(f"  resource-pack-sha1={digest}")
    print("   resource-pack-required=false")
    print("   resource-pack-prompt=§dLoad §5DifficultyEngine§d cape textures!")
    print()
    print("Hosting options:")
    print("  • Upload to GitHub Releases as a release asset → use the")
    print("    direct download URL (must end in .zip)")
    print("  • Drop on https://mc-packs.net/ for instant hosting")
    print("  • Place in your server's web root if you have a web server")
    print("─────────────────────────────────────────────────────────────────")

# ── Entry point ───────────────────────────────────────────────────────────

if __name__ == "__main__":
    build()
    zip_pack()
