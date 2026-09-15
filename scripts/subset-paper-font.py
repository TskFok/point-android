"""Create the bundled heading font from the upstream Noto Serif SC variable font.

Usage: python subset-paper-font.py /path/to/NotoSerifSC.ttf
Requires fonttools==4.60.1. Does not download files or run during Android builds.
"""

from pathlib import Path
import sys

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont


def main():
    root = Path(__file__).resolve().parents[1]
    font = TTFont(sys.argv[1])
    # Keep common Chinese/Japanese plus Latin accents, kana and punctuation.
    characters = set(range(0x20, 0x250)) | set(range(0x2000, 0x2070))
    characters |= set(range(0x3000, 0x3100)) | set(range(0xFF00, 0xFFF0))
    for encoding in ("gb2312", "euc_jp"):
        for lead in range(0xA1, 0xFF):
            for trail in range(0xA1, 0xFF):
                try:
                    characters.update(map(ord, bytes([lead, trail]).decode(encoding)))
                except UnicodeDecodeError:
                    pass
    for resource in (root / "app/src/main/res/values").glob("*.xml"):
        characters.update(map(ord, resource.read_text()))
    options = subset.Options()
    options.name_IDs = [0, 1, 2, 3, 4, 5, 6, 13, 14]
    subsetter = subset.Subsetter(options=options)
    subsetter.populate(unicodes=characters)
    subsetter.subset(font)
    instantiateVariableFont(font, {"wght": 600}, inplace=True)
    names = {1: "Point Quest Serif", 2: "SemiBold", 3: "PointQuestSerif-SemiBold",
             4: "Point Quest Serif SemiBold", 6: "PointQuestSerif-SemiBold"}
    for name_id, value in names.items():
        for platform, encoding, language in [(3, 1, 0x409), (1, 0, 0)]:
            font["name"].setName(value, name_id, platform, encoding, language)
    output = root / "app/src/main/res/font/paper_serif_semibold.ttf"
    font.save(output)
    print(f"{output}: {output.stat().st_size:,} bytes; {len(font.getBestCmap()):,} characters")


if __name__ == "__main__":
    main()
