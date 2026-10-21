"""Generates the HUI mod icon (src/main/resources/icon.png).

Run with the bundled Python (Pillow is available):
    python tools/make_icon.py
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

SIZE = 128
BACKGROUND = (13, 16, 22, 255)
ACCENT = (63, 199, 255, 255)
TEXT = (230, 237, 243, 255)


def load_font(size: int) -> ImageFont.FreeTypeFont:
    for candidate in ("C:/Windows/Fonts/segoeuib.ttf", "C:/Windows/Fonts/arialbd.ttf"):
        if Path(candidate).exists():
            return ImageFont.truetype(candidate, size)
    return ImageFont.load_default(size)


def main() -> None:
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)

    draw.rounded_rectangle([0, 0, SIZE - 1, SIZE - 1], radius=22, fill=BACKGROUND, outline=ACCENT, width=4)
    draw.rounded_rectangle([18, 20, SIZE - 18, 29], radius=4, fill=ACCENT)

    font = load_font(46)
    text = "HUI"
    bbox = draw.textbbox((0, 0), text, font=font)
    text_width = bbox[2] - bbox[0]
    text_height = bbox[3] - bbox[1]
    position = ((SIZE - text_width) / 2 - bbox[0], (SIZE - text_height) / 2 - bbox[1] + 8)
    draw.text(position, text, font=font, fill=TEXT)

    target = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "icon.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    image.save(target)
    print(f"wrote {target}")


if __name__ == "__main__":
    main()
