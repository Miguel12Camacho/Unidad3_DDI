#!/usr/bin/env python3
"""Copy the shared SmartClass TV web client into webOS and Tizen app projects."""

from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "platforms" / "web-shared"
ASSETS = ("index.html", "tv.css", "tv.js", "icon.png")


def main() -> None:
    for platform in ("webos", "tizen"):
        target = ROOT / "platforms" / platform
        target.mkdir(parents=True, exist_ok=True)
        for filename in ASSETS:
            shutil.copy2(SOURCE / filename, target / filename)
        print(f"Prepared {platform} app files in {target}")


if __name__ == "__main__":
    main()
