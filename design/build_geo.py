"""Builds ReelScore's offline place list from GeoNames (https://www.geonames.org, CC BY 4.0).

Usage: python -I design/build_geo.py <folder with countryInfo.txt, admin1CodesASCII.txt, cities15000.txt>
Writes app/src/main/assets/geo/{countries,states,cities}.tsv:
  countries.tsv  iso2, name, capital
  states.tsv     iso2, admin1 code, name
  cities.tsv     name, iso2, admin1 code, lat, lon   (per country, biggest city first)
"""
import pathlib
import sys

# Neighbourhoods, historical, abandoned and destroyed places would only clutter the picker.
SKIP_FEATURES = {"PPLX", "PPLH", "PPLQ", "PPLW", "PPLCH"}


def main(src: pathlib.Path) -> None:
    out = pathlib.Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "geo"
    out.mkdir(parents=True, exist_ok=True)

    countries = {}
    capitals = {}
    for line in (src / "countryInfo.txt").read_text(encoding="utf-8").splitlines():
        if line.startswith("#") or not line.strip():
            continue
        cols = line.split("\t")
        countries[cols[0]] = cols[4]
        capitals[cols[0]] = cols[5]

    states = {}
    for line in (src / "admin1CodesASCII.txt").read_text(encoding="utf-8").splitlines():
        code, name = line.split("\t")[:2]
        iso2, admin1 = code.split(".", 1)
        states[(iso2, admin1)] = name

    cities = []
    with (src / "cities15000.txt").open(encoding="utf-8") as fh:
        for line in fh:
            c = line.rstrip("\n").split("\t")
            if c[7] in SKIP_FEATURES or c[8] not in countries:
                continue
            cities.append((c[8], -int(c[14] or 0), c[1], c[10], round(float(c[4]), 4), round(float(c[5]), 4)))
    cities.sort()

    used_countries = sorted({c[0] for c in cities}, key=lambda iso: countries[iso])
    used_states = sorted({(c[0], c[3]) for c in cities if (c[0], c[3]) in states}, key=lambda k: (k[0], states[k]))

    def clean(text: str) -> str:
        return text.replace("\t", " ").strip()

    (out / "countries.tsv").write_text(
        "".join(f"{iso}\t{clean(countries[iso])}\t{clean(capitals[iso])}\n" for iso in used_countries), encoding="utf-8")
    (out / "states.tsv").write_text(
        "".join(f"{iso}\t{a1}\t{clean(states[(iso, a1)])}\n" for iso, a1 in used_states), encoding="utf-8")
    (out / "cities.tsv").write_text(
        "".join(f"{clean(name)}\t{iso}\t{a1}\t{lat}\t{lon}\n" for iso, _, name, a1, lat, lon in cities), encoding="utf-8")

    sizes = {p.name: p.stat().st_size // 1024 for p in out.iterdir()}
    print(f"countries {len(used_countries)}, states {len(used_states)}, cities {len(cities)}; KB {sizes}")


if __name__ == "__main__":
    main(pathlib.Path(sys.argv[1]))
