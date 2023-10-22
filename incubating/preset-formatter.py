#!/usr/bin/python3

"""Formats the JSON output by the scraper into an XML preset file.

See: https://www.aci.it/i-servizi/normative/codice-della-strada/titolo-ii-della-costruzione-e-tutela-delle-strade/art-39-segnali-verticali/regolamento-art-39.html
"""
import itertools
import json
import re
import sys
from typing import List, Dict
import urllib.parse

from lxml import etree
from lxml.builder import E

NAME = {
    "name": "Traffic Signs in Italy",
    "it.name": "Segnaletica stradale in Italia",
}
""" Name of the preset """

DESCRIPTION = "A preset to tag traffic signs in Italy."
ICON = "https://upload.wikimedia.org/wikipedia/en/0/03/Flag_of_Italy.svg"

ICON_BASE = "https://upload.wikimedia.org/wikipedia/commons/"
"""The base url for all relative icons."""

THUMBS = [
    "II.35", "II.37", "II.50", "II.88", "II.272", "II.275", "II.285", "II.294",
    "II.303", "II.356", "MII.7", "MII.8b"
]
"""The icons to use to represent their groups."""

TAGS : Dict[str,str|List[str]] = {
    # "II.0"   : "traffic_sign={id};hazard hazard=road_works",
    "II.1"   : "traffic_sign={id};hazard hazard=damaged_road", # "unapproved"
    "II.2"   : "traffic_sign={id};hazard hazard=bump",
    "II.3"   : "traffic_sign={id};hazard hazard=dip",
    "II.4"   : "traffic_sign={id};hazard hazard=turn turn=right",
    "II.5"   : "traffic_sign={id};hazard hazard=turn turn=left",
    "II.6"   : "traffic_sign={id};hazard hazard=turns turns=right",
    "II.7"   : "traffic_sign={id};hazard hazard=turns turns=left",
    "II.8"   : "traffic_sign={id};hazard hazard=level_crossing crossing:barrier=yes",
    "II.9"   : "traffic_sign={id};hazard hazard=level_crossing crossing:barrier=no",
    "II.12"  : "traffic_sign={id};hazard hazard=level_crossing crossing:barrier=no",
    "II.13"  : "traffic_sign={id};hazard hazard=crossing",
    "II.14"  : "traffic_sign={id};hazard hazard=cyclists",
    "II.15"  : "traffic_sign={id};hazard hazard=steep_incline steep_incline=down incline=*",
    "II.16"  : "traffic_sign={id};hazard hazard=steep_incline steep_incline=up incline=*",
    "II.17"  : "traffic_sign={id};hazard hazard=road_narrows",
    "II.18"  : "traffic_sign={id};hazard hazard=road_narrows",
    "II.19"  : "traffic_sign={id};hazard hazard=road_narrows",
    "II.20"  : "traffic_sign={id};hazard hazard=swing_bridge",    # unofficial swing_bridge
    "II.21"  : "traffic_sign={id};hazard hazard=dangerous_shoulder",
    "II.22"  : "traffic_sign={id};hazard hazard=slippery",
    "II.23"  : "traffic_sign={id};hazard hazard=children",
    "II.24"  : "traffic_sign={id};hazard hazard=animal_crossing",
    "II.25"  : "traffic_sign={id};hazard hazard=animal_crossing",
    "II.26"  : "traffic_sign={id};oneway oneway=no",
    "II.27"  : "traffic_sign={id};hazard hazard=roundabout",      # unapproved
    "II.28"  : "traffic_sign={id};hazard hazard=quay",            # unofficial quay
    "II.29"  : "traffic_sign={id};hazard hazard=loose_gravel",
    "II.30a" : "traffic_sign={id};hazard hazard=falling_rocks",
    "II.30b" : "traffic_sign={id};hazard hazard=falling_rocks",
    "II.31a" : "traffic_sign={id};hazard hazard=traffic_signals", # unapproved
    "II.31b" : "traffic_sign={id};hazard hazard=traffic_signals", # unapproved
    "II.32"  : "traffic_sign={id};hazard hazard=low_flying_aircraft",
    "II.33"  : "traffic_sign={id};hazard hazard=side_winds",
    "II.34"  : "traffic_sign={id};hazard hazard=wildfires",       # unofficial wildfires
    "II.35"  : "traffic_sign={id};hazard",

    "II.36"  : "traffic_sign={id};give_way",
    "II.37"  : "traffic_sign={id};stop",
    "II.38"  : "traffic_sign={id};yield_ahead distance=*",
    "II.39"  : "traffic_sign={id};stop_ahead distance=*",
    "II.40"  : "traffic_sign={id};priority_road priority_road=implicit",
    "II.41"  : "traffic_sign={id};priority priority=no",
    "II.42"  : "traffic_sign={id};priority_road priority_road=implicit",
    "II.43a" : "traffic_sign={id}",
    "II.43b" : "traffic_sign={id}",
    "II.43c" : "traffic_sign={id}",
    "II.43d" : "traffic_sign={id}",
    "II.43e" : "traffic_sign={id}",
    "II.44"  : "traffic_sign={id};priority_road priority_road=*",
    "II.45"  : "traffic_sign={id};priority priority=yes",

    "II.46"  : "traffic_sign={id};vehicle vehicle=no",
    "II.47"  : "traffic_sign={id};oneway oneway=yes",
    "II.48"  : "traffic_sign={id};overtaking overtaking=no",
    "II.49"  : "traffic_sign={id};mindistance mindistance=*",
    "II.50"  : "traffic_sign={id};maxspeed maxspeed=*",
    "II.52"  : "traffic_sign={id};overtaking:hgv overtaking:hgv=no",
    "II.53"  : "traffic_sign={id};carriage carriage=no",
    "II.54"  : "traffic_sign={id};foot foot=no",
    "II.55"  : "traffic_sign={id};bicycle bicycle=no",
    "II.56"  : "traffic_sign={id};motorcycle motorcycle=no",
    "II.57"  : "traffic_sign={id};handcart handcart=no",
    "II.58"  : "traffic_sign={id};motorcar motorcar=no",
    "II.59"  : "traffic_sign={id};bus bus=no tourist_bus=no",
    "II.60a" : "traffic_sign={id};hgv hgv=no",
    "II.60b" : "traffic_sign={id};maxweightrating:hgv maxweightrating:hgv=*",
    "II.61"  : "traffic_sign={id};trailer trailer=no",
    "II.62"  : "traffic_sign={id};agricultural agricultural=no",
    "II.63"  : "traffic_sign={id};hazmat hazmat=no",
    "II.64b" : "traffic_sign={id};hazmat:water hazmat:water=no",
    "II.65"  : "traffic_sign={id};maxwidth maxwidth=*",
    "II.66"  : "traffic_sign={id};maxwidth maxheight=*",
    "II.67"  : "traffic_sign={id};maxwidth maxlength=*",
    "II.68"  : "traffic_sign={id};maxweight maxweight=*",
    "II.69"  : "traffic_sign={id};maxaxleload maxaxleload=*",
    "II.71"  : "traffic_sign={id};maxspeed maxspeed=implicit",
    "II.72"  : "traffic_sign={id};overtaking overtaking=implicit",
    "II.73"  : "traffic_sign={id};overtaking:hgv overtaking:hgv=implicit",
    "II.74"  : "traffic_sign={id};parking parking=no_parking", # ??
    "II.75"  : "traffic_sign={id};parking parking=no_stopping", # ??
    "II.76"  : "traffic_sign={id};amenity amenity=parking",

    "II.85"  : "traffic_sign={id};minspeed minspeed=*",
    "II.86"  : "traffic_sign={id};minspeed minspeed=implicit",
    "II.88"  : "traffic_sign={id};foot foot=designated",
    "II.89"  : "traffic_sign={id};foot foot=implicit",
    "II.90"  : "traffic_sign={id};bicycle bicycle=designated",
    "II.91"  : "traffic_sign={id};bicycle bicycle=implicit",
    "II.92a" : "traffic_sign={id};bicycle;foot foot=designated bicycle=designated segregated=yes",
    "II.92b" : "traffic_sign={id};bicycle;foot foot=designated bicycle=designated segregated=no",
    "II.93a" : "traffic_sign={id};bicycle;foot foot=implicit bicycle=implicit segregated=yes",
    "II.93b" : "traffic_sign={id};bicycle;foot foot=implicit bicycle=implicit segregated=no",
    "II.94"  : "traffic_sign={id};horse horse=designated",
    "II.95"  : "traffic_sign={id};horse horse=implicit",
    "II.273" : "traffic_sign={id};city_limit name=*",
    "II.274" : "traffic_sign={id};city_limit city_limit=end name=*",

    "MII.6e" : "traffic_sign={id};hazard hazard=flood_prone",
    "MII.6f" : "traffic_sign={id};hazard hazard=queues_likely",
    "MII.6h" : "traffic_sign={id};hazard hazard=ice",
}
"""Override tags for these items."""

def tags(e, id_, tags_: str | List[str]):
    """Add a key,
    if the tag value is '*' display a text box instead,
    if there are more of the same key, display a combobox instead.
    """
    tags : List[str] = tags_.split() if isinstance(tags_, str) else tags_

    for key, g in itertools.groupby(tags, lambda tag: tag.split("=")[0]):
        try:
            gtags = [t.format(id = "IT:" + id_) for t in g]
            if len(gtags) > 1:
                e.append(
                    E.combo(
                        text=key.capitalize(),
                        key=key,
                        values=",".join([t.split("=")[1] for t in gtags]),
                    )
                )
                continue
            value = gtags[0].split("=")[1]
            if value == "*":
                e.append(E.text(text=key.capitalize(), key=key))
                continue
            e.append(E.key(key=key, value=value))
        except IndexError:
            print(id_, gtags)
            sys.exit()


items = json.load(sys.stdin)
groups = []

for section, group in itertools.groupby(items, lambda s: s.get("section", "unknown")):
    g = E.group(
        icon_size="48",
    )
    if m := re.match(r"^(.*?)\s+\((.*?)\)$", section):
        g.attrib["it.name"] = m.group(1)
        g.attrib["name"] = m.group(2)
    else:
        g.attrib["it.name"] = section

    for item in group:
        if "id" not in item:
            continue
        if len(item["urls"]) == 0:
            continue

        id_ = item["id"].strip().replace("/", "")

        url = urllib.parse.unquote(item["urls"][0])
        if url.startswith(ICON_BASE):
            url = url[len(ICON_BASE) :]
        if id_ in THUMBS:
            g.attrib["icon"] = url

        e = E.item(
            name=id_,
            icon=url,
            type="node",
            preset_name_label="true",
        )
        for key, value in item["names"].items():
            key = key.replace("name", "tooltip")
            e.attrib[key] = value

        if "wiki" in item:
            e.append(E.link(wiki=item["wiki"].replace("/wiki/", "")))

        item["tags"].append("traffic_sign={id}")
        tags(e, id_, TAGS.get(id_, item["tags"]))  # override tags
        e.append(E.reference(ref="t"))

        g.append(e)

    groups.append(g)

preset = E.presets(
    E.chunk(
        E.optional(
            E.combo(
                key="direction",
                text="Direction",
                values="N,NE,E,SE,S,SW,W,NW,0,45,90,135,180,225,270,315,forward,backward",
                values_sort="false",
            ),
            E.combo(
                key="distance",
                text="Distance",
                values="100 m,250 m,500 m",
                values_sort="false",
            ),
            E.combo(
                key="length",
                text="Length",
                values="1 km,2 km,5 km",
                values_sort="false",
            ),
        ),
        id="t",
    ),
    E.group(
        *groups,
        **NAME,
        icon=ICON,
        icon_base=ICON_BASE,
        sort_menu="false",
    ),
    xmlns="http://josm.openstreetmap.de/tagging-preset-1.0",
    shortdescription=NAME["name"],
    description="A preset to tag traffic signs in Italy.",
    version="0.0.1",
    icon=ICON,
)

print(etree.tostring(preset, encoding="unicode", method="xml", pretty_print=True))
