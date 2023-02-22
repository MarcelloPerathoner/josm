#!/usr/bin/python3

"""Formats the JSON output by the scraper into a mapcss stylesheet.

See: https://www.aci.it/i-servizi/normative/codice-della-strada/titolo-ii-della-costruzione-e-tutela-delle-strade/art-39-segnali-verticali/regolamento-art-39.html
"""

import json
import re
import sys
from typing import List, Dict
import urllib.parse

NAME = "Traffic Signs in Italy"
IT_NAME = "Segnaletica stradale in Italia",
""" Name of the style """

DESCRIPTION = "A stylesheet for traffic signs in Italy."
VERSION = "0.1"
AUTHOR = "Marcello Perathoner"

ICON = "https://upload.wikimedia.org/wikipedia/en/0/03/Flag_of_Italy.svg"


items = json.load(sys.stdin)

print(f"meta {{")
print(f'  title: "{NAME}";')
print(f'  description: "{DESCRIPTION}";')
print(f'  version: "{VERSION}";')
print(f'  author: "{AUTHOR}";')
print(f'  icon: "{ICON}";')
print(f'  link: "https://github.com/FIXME";')
print(f"  watch-modified: true;")
print(f"}}\n")

# meta[lang="it"] {}

print("""
setting::icon_size {
    type: double;
    label: tr("Set the icon size...");
    default: 32;
}

/* turn off maxspeed drawing in elemstyle.mapcss */
node[prop(maxspeedclass, default)]::* {
    text: none;
    symbol-shape: none;
}

node[traffic_sign=~/IT:M?II/] {
    set .sign;
}

node[prop(sign, default)][direction=~/[NWSE]+/] {
    text-rotation: eval(cardinal_to_radians(tag("direction")) + 3.14);
    icon-rotation: eval(cardinal_to_radians(tag("direction")) + 3.14);
}
node[prop(sign, default)][direction=~/[.0-9]+/] {
    text-rotation: eval(degree_to_radians(tag("direction")) + 3.14);
    icon-rotation: eval(degree_to_radians(tag("direction")) + 3.14);
}
way[highway] > node[prop(sign, default)][direction=forward]::* {
    text-transform: transform(rotate(heading()));
    icon-transform: transform(rotate(heading()));
}
way[highway] > node[prop(sign, default)][direction=backward]::* {
    text-transform: transform(rotate(heading(0.5turn)));
    icon-transform: transform(rotate(heading(0.5turn)));
}

node[prop(sign, default)]::* {
    major-z-index: 7;
    icon-width: setting("icon_size");
    text-anchor-horizontal: center;
    text-anchor-vertical: center;
    font-size: eval(setting("icon_size") * 0.3);
    font-weight: bold;
    text-color: black;
}
node[prop(sign, default)] {
    major-z-index: 7;
    icon-height: setting("icon_size");

    font-size: eval(setting("icon_size") * 0.85);
    text-offset-y: eval(setting("icon_size") * -0.03);
    text: none;

    yoffsetprop: eval(setting("icon_size") * 0.7);
    ydeltaprop: eval(setting("icon_size") * 0.4);
}

node[prop(sign, default)][distance]::distance {
    yoffsetprop: prop(yoffsetprop, default);

    icon-image: "distance-empty.svg";
    icon-offset-y: prop(yoffsetprop);
    text-offset-y: eval(-prop(yoffsetprop));
    text: tag(distance);
}

node[prop(sign, default)][length]::length {
    yoffsetprop: eval(prop(yoffsetprop, default) + (has_tag_key(distance) ? prop(ydeltaprop, default) : 0));

    icon-image: "length-empty.svg";
    icon-offset-y: prop(yoffsetprop);
    text-offset-y: eval(-prop(yoffsetprop));
    text: tag(length);
}

node[prop(sign, default)][period]::period {
    yoffsetprop: eval(prop(yoffsetprop, default) + (has_tag_key(distance) ? prop(ydeltaprop, default) : 0) + (has_tag_key(length) ? prop(ydeltaprop, default) : 0));

    icon-image: "distance-empty.svg";
    icon-offset-y: prop(yoffsetprop);
    text-offset-y: eval(-prop(yoffsetprop));
    text: tag(period);
}

""")

for item in items:
    if "id" not in item:
        continue
    if len(item["urls"]) == 0:
        continue
    id_ = item["id"]
    url = item["urls"][0]
    if (id_ == "II.46"): # save the sign for the speed limit
        sign_46_url = url
    print(f"node[traffic_sign=~/IT:{id_}(;|,|\\[|$)/] {{")
    if (id_ == "II.50"): # draw the speed limit number
        print(f'    icon-image: "{sign_46_url}";')
        print(f'    text: tag(maxspeed);')
    else:
        print(f'    icon-image: "{url}";')
    print(f"}}\n")

print("""
node|z-16[prop(sign, default)]::* {
    icon-image: none;
    text: none;
}
""")
