#!/usr/bin/env python3
"""
Render the Lesson Log icons.

The mark is the same shape the streak grid draws for a day: one cell split in
two, top half filled (morning logged), bottom half open (evening still to log).

Everything is drawn at 4x and downsampled with LANCZOS, because at 48px on a
launcher the edge quality is most of what you see.

    python3 scripts/make-icons.py
"""
from PIL import Image, ImageDraw

GROUND = (242, 241, 236, 255)   # --ground light
MARK = (26, 122, 83, 255)       # --done light
SCALE = 4

# "any" icons sit edge to edge; maskable ones must survive a circular crop, so
# the mark is kept well inside the safe zone.
COVERAGE = {"any": 0.62, "maskable": 0.44}


def render(size: int, purpose: str = "any", transparent: bool = False) -> Image.Image:
    s = size * SCALE
    ground = (0, 0, 0, 0) if transparent else GROUND
    img = Image.new("RGBA", (s, s), ground)

    side = int(s * COVERAGE[purpose])
    x0 = (s - side) // 2
    y0 = (s - side) // 2
    box = (x0, y0, x0 + side, y0 + side)
    radius = int(side * 0.21)
    stroke = max(2, int(side * 0.085))
    mid = y0 + side // 2

    # Whole cell, filled — later masked down to the top half.
    filled = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    ImageDraw.Draw(filled).rounded_rectangle(box, radius=radius, fill=MARK)

    # Whole cell, outlined — later masked down to the bottom half.
    outlined = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    ImageDraw.Draw(outlined).rounded_rectangle(box, radius=radius, outline=MARK, width=stroke)

    top = Image.new("L", (s, s), 0)
    ImageDraw.Draw(top).rectangle((0, 0, s, mid - stroke // 2), fill=255)

    bottom = Image.new("L", (s, s), 0)
    ImageDraw.Draw(bottom).rectangle((0, mid + stroke // 2, s, s), fill=255)

    img.paste(filled, (0, 0), Image.composite(filled.split()[3], Image.new("L", (s, s), 0), top))
    img.paste(outlined, (0, 0), Image.composite(outlined.split()[3], Image.new("L", (s, s), 0), bottom))

    return img.resize((size, size), Image.LANCZOS)


def main() -> None:
    out = "web"
    render(192).save(f"{out}/icon-192.png")
    render(512).save(f"{out}/icon-512.png")
    render(512, purpose="maskable").save(f"{out}/icon-maskable-512.png")
    render(32, transparent=True).save(f"{out}/favicon.png")
    print("wrote icon-192, icon-512, icon-maskable-512, favicon")


if __name__ == "__main__":
    main()
