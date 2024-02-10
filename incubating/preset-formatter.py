#!/usr/bin/python3

"""Formats the JSON output by the scraper into an XML preset file.

See: https://www.aci.it/i-servizi/normative/codice-della-strada/titolo-ii-della-costruzione-e-tutela-delle-strade/art-39-segnali-verticali/regolamento-art-39.html
"""
import itertools
import json
import re
import sys
from typing import List, Dict

from lxml import etree
from lxml.builder import E

VERSION = "0.0.1"

NAME = {
    "name"    : "Traffic Signs in Italy",
    "de.name" : "Verkehrszeichen in Italien",
    "en.name" : "Traffic Signs in Italy",
    "it.name" : "Segnaletica stradale in Italia",
}
""" Preset name in various langauges """

DESCRIPTION = {
    "description"    : "A preset to tag traffic signs in Italy.",
    "de.description" : "Eine Vorlage für italienische Verkehrszeichen.",
    "en.description" : "A preset to tag traffic signs in Italy.",
    "it.description" : "Un preset per i segnali stradali italiani.",
}
""" Preset description in various langauges """

COUNTRY_PREFIX = "IT:"
""" The prefix to add to all ids. """

ICON = "flag.svg"
""" The root icon for the preset menu. """

ICON_BASE = "svgs/"
"""The base url for all relative icons."""

THUMBS = [
    "II.35", "II.37", "II.50", "II.88", "II.272", "II.275", "II.285", "II.294",
    "II.303", "II.356", "MII.7", "MII.8b"
]
"""The icons to use to represent their groups."""

TAGS : Dict[str,str|List[str]] = {
    # "II.0"   : "traffic_sign={id} hazard=road_works",
    "II.1"   : "traffic_sign={id} hazard=damaged_road", # "unapproved"
    "II.2"   : "traffic_sign={id} hazard=bump",
    "II.3"   : "traffic_sign={id} hazard=dip",
    "II.4"   : "traffic_sign={id} hazard=turn turn=right",
    "II.5"   : "traffic_sign={id} hazard=turn turn=left",
    "II.6"   : "traffic_sign={id} hazard=turns turns=right",
    "II.7"   : "traffic_sign={id} hazard=turns turns=left",
    "II.8"   : "traffic_sign={id} hazard=level_crossing crossing:barrier=yes",
    "II.9"   : "traffic_sign={id} hazard=level_crossing crossing:barrier=no",
    "II.12"  : "traffic_sign={id} hazard=level_crossing crossing:barrier=no",
    "II.13"  : "traffic_sign={id} hazard=crossing",
    "II.14"  : "traffic_sign={id} hazard=cyclists",
    "II.15"  : "traffic_sign={id}[{{incline}}] hazard=steep_incline steep_incline=down incline=*",
    "II.16"  : "traffic_sign={id}[{{incline}}] hazard=steep_incline steep_incline=up incline=*",
    "II.17"  : "traffic_sign={id} hazard=road_narrows",
    "II.18"  : "traffic_sign={id} hazard=road_narrows",
    "II.19"  : "traffic_sign={id} hazard=road_narrows",
    "II.20"  : "traffic_sign={id} hazard=swing_bridge",    # unofficial swing_bridge
    "II.21"  : "traffic_sign={id} hazard=dangerous_shoulder",
    "II.22"  : "traffic_sign={id} hazard=slippery",
    "II.23"  : "traffic_sign={id} hazard=children",
    "II.24"  : "traffic_sign={id} hazard=animal_crossing",
    "II.25"  : "traffic_sign={id} hazard=animal_crossing",
    "II.26"  : "traffic_sign={id} oneway=no",
    "II.27"  : "traffic_sign={id} hazard=roundabout",      # unapproved
    "II.28"  : "traffic_sign={id} hazard=quay",            # unofficial quay
    "II.29"  : "traffic_sign={id} hazard=loose_gravel",
    "II.30a" : "traffic_sign={id} hazard=falling_rocks",
    "II.30b" : "traffic_sign={id} hazard=falling_rocks",
    "II.31a" : "traffic_sign={id} hazard=traffic_signals", # unapproved
    "II.31b" : "traffic_sign={id} hazard=traffic_signals", # unapproved
    "II.32"  : "traffic_sign={id} hazard=low_flying_aircraft",
    "II.33"  : "traffic_sign={id} hazard=side_winds",
    "II.34"  : "traffic_sign={id} hazard=wildfires",       # unofficial wildfires
    "II.35"  : "traffic_sign={id} hazard=*",

    "II.36"  : "traffic_sign={id}", # stop
    "II.37"  : "traffic_sign={id}", # give way
    "II.38"  : "traffic_sign={id}[{{distance}}]",
    "II.39"  : "traffic_sign={id}[{{distance}}]",
    "II.40"  : "traffic_sign={id} priority_road=implicit",
    "II.41"  : "traffic_sign={id} priority=no",
    "II.42"  : "traffic_sign={id} priority_road=implicit",
    "II.43a" : "traffic_sign={id} hazard=dangerous_junction",
    "II.43b" : "traffic_sign={id} hazard=dangerous_junction dangerous_junction=right",
    "II.43c" : "traffic_sign={id} hazard=dangerous_junction dangerous_junction=left",
    "II.43d" : "traffic_sign={id} hazard=dangerous_junction dangerous_junction=right",
    "II.43e" : "traffic_sign={id} hazard=dangerous_junction dangerous_junction=left",
    "II.44"  : "traffic_sign={id} priority_road=*",
    "II.45"  : "traffic_sign={id} priority=yes",

    "II.46"  : "traffic_sign={id} vehicle=no",
    "II.47"  : "traffic_sign={id} oneway=yes",
    "II.48"  : "traffic_sign={id} overtaking=no",
    "II.49"  : "traffic_sign={id}[{{mindistance}}] mindistance=*",
    "II.50"  : "traffic_sign={id}[{{maxspeed}}] maxspeed=*",
    "II.52"  : "traffic_sign={id} overtaking:hgv=no",
    "II.53"  : "traffic_sign={id} carriage=no",
    "II.54"  : "traffic_sign={id} foot=no",
    "II.55"  : "traffic_sign={id} bicycle=no",
    "II.56"  : "traffic_sign={id} motorcycle=no",
    "II.57"  : "traffic_sign={id} handcart=no",
    "II.58"  : "traffic_sign={id} motorcar=no",
    "II.59"  : "traffic_sign={id} bus=no tourist_bus=no",
    "II.60a" : "traffic_sign={id} hgv=no",
    "II.60b" : "traffic_sign={id}[{{maxweightrating:hgv}}] maxweightrating:hgv=*",
    "II.61"  : "traffic_sign={id} trailer=no",
    "II.62"  : "traffic_sign={id} agricultural=no",
    "II.63"  : "traffic_sign={id} hazmat=no",
    "II.64b" : "traffic_sign={id} hazmat:water=no",
    "II.65"  : "traffic_sign={id}[{{maxwidth}}] maxwidth=*",
    "II.66"  : "traffic_sign={id}[{{maxheight}}] maxheight=*",
    "II.67"  : "traffic_sign={id}[{{maxlength}}] maxlength=*",
    "II.68"  : "traffic_sign={id}[{{maxweight}}] maxweight=*",
    "II.69"  : "traffic_sign={id}[{{maxaxleload}}] maxaxleload=*",
    "II.71"  : "traffic_sign={id}[{{nosave:maxspeed}}] maxspeed=implicit",
    "II.72"  : "traffic_sign={id} overtaking=implicit",
    "II.73"  : "traffic_sign={id} overtaking:hgv=implicit",
    "II.74"  : "traffic_sign={id} parking=no_parking", # ??
    "II.75"  : "traffic_sign={id} parking=no_stopping", # ??
    "II.76"  : "traffic_sign={id} amenity=parking",
    "II.77"  : "traffic_sign={id}[{{distance}}]",

    "II.85"  : "traffic_sign={id}[{{minspeed}}] minspeed=*",
    "II.86"  : "traffic_sign={id}[{{minspeed}}] minspeed=implicit",
    "II.87"  : "traffic_sign={id} snow_chains=required",
    "II.88"  : "traffic_sign={id} foot=designated",
    "II.89"  : "traffic_sign={id} foot=implicit",
    "II.90"  : "traffic_sign={id} bicycle=designated",
    "II.91"  : "traffic_sign={id} bicycle=implicit",
    "II.92a" : "traffic_sign={id} foot=designated bicycle=designated segregated=yes",
    "II.92b" : "traffic_sign={id} foot=designated bicycle=designated segregated=no",
    "II.93a" : "traffic_sign={id} foot=implicit bicycle=implicit segregated=yes",
    "II.93b" : "traffic_sign={id} foot=implicit bicycle=implicit segregated=no",
    "II.94"  : "traffic_sign={id} horse=designated",
    "II.95"  : "traffic_sign={id} horse=implicit",
    "II.273" : "traffic_sign={id} name=*",
    "II.274" : "traffic_sign={id} city_limit=end name=*",

    "II.312"  : "traffic_sign={id}[{{maxspeed:advisory}}] maxspeed:advisory=*",
    "II.313"  : "traffic_sign={id}[{{maxspeed:advisory}}] maxspeed:advisory=*",
    "II.323a" : "traffic_sign={id}[{{maxspeed}}] maxspeed={{maxspeed}} zone:maxspeed=IT:{{maxspeed}}",
    "II.323b" : "traffic_sign={id}[{{nosave:maxspeed}}] maxspeed=implicit",

    "MII.1"  : "traffic_sign={id}[{{nosave:distance}}]",
    "MII.2"  : "traffic_sign={id}[{{nosave:extent}}]",
    "MII.3"  : "traffic_sign={id}[{{nosave:period}}]",
    "MII.6e" : "traffic_sign={id} hazard=flood_prone",
    "MII.6f" : "traffic_sign={id} hazard=queues_likely",
    "MII.6h" : "traffic_sign={id} hazard=ice",
}
"""Override tags for these items."""

def tags(e, id_, tags_: str | List[str]):
    """Add a key.

    - if the tag value is given, just add it
    - if the tag value is '*' display a text box,
    - if a key is given more than once, display a combobox containing the different values.
    """
    tags : List[str] = tags_.split() if isinstance(tags_, str) else tags_

    # group on the key in the tag
    for key, g in itertools.groupby(tags, lambda tag: tag.split("=")[0]):
        try:
            gtags = [t.format(id = COUNTRY_PREFIX + id_) for t in g]
            if len(gtags) > 1:
                # make a combobox for the values
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
                # make a textfield for the value
                e.append(E.text(text=key.capitalize(), key=key))
                continue

            if "{" in value:
                for k in re.findall(r"\{nosave:(\w+)\}", value):
                    e.append(E.text(text=k.capitalize(), key="nosave:" + k))

            # add a fixed value
            params = { "key" : key, "append_with" : ";", "append_regex_search" : "^" + COUNTRY_PREFIX }
            if "{" in value:
                params["value_template"] = value
            else:
                params["value"] = value
            if key == "traffic_sign" and value.startswith(COUNTRY_PREFIX + "M"):
                params["append_with"] = ","
            e.append(E.key(**params))
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

        url = item.get("filename")
        if url is None:
            continue

        id_ = item["id"].strip().replace("/", "")

        # urllib.parse.unquote(item["urls"][0])
        # url = PREFIX.sub("", url)
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
        tags(e, id_, TAGS.get(id_, item["tags"]))  # tags in TAGS override tags in wiki
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
                default="S"
            ),
        ),
        id="t",
    ),
    E.group(
        *groups,
        **NAME,
        icon=ICON,
        sort_menu="false",
    ),
    **DESCRIPTION,
    xmlns="http://josm.openstreetmap.de/tagging-preset-1.0",
    shortdescription=NAME["name"],
    version=VERSION,
    icon_base=ICON_BASE,
    icon=ICON,
)

print(etree.tostring(preset, encoding="unicode", method="xml", pretty_print=True))
