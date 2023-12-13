#!/usr/bin/python3

"""Formats the JSON output by the scraper into a mapcss stylesheet.

See: https://www.aci.it/i-servizi/normative/codice-della-strada/titolo-ii-della-costruzione-e-tutela-delle-strade/art-39-segnali-verticali/regolamento-art-39.html
"""

import json
import sys

NAME = "Traffic Signs in Italy"
IT_NAME = "Segnaletica stradale in Italia",
""" Name of the style """

DESCRIPTION = "A stylesheet for traffic signs in Italy."
VERSION = "0.1"
AUTHOR = "Marcello Perathoner"

ICON = "svgs/flag.svg"

items = json.load(sys.stdin)

print( "meta {")
print(f'  title: "{NAME}";')
print(f'  description: "{DESCRIPTION}";')
print(f'  version: "{VERSION}";')
print(f'  author: "{AUTHOR}";')
print(f'  icon: "{ICON}";')
print( '  link: "https://github.com/FIXME";')
print( "  watch-modified: true;")
print( "}\n")

id2url = {}
id2aspect = {}
for item in items:
    if "id" not in item:
        continue
    if len(item["urls"]) == 0:
        continue
    id_ = item["id"]
    url = item["urls"][0]
    filename = item.get("filename")
    if filename:
        id2url[id_] = "svgs/" + filename # url
        id2aspect[id_] = item.get("height", 1.0) / item.get("width", 1.0)

# substitute empty signs for those signs that support custom text
id2url["II.50"]   = "maxspeed-empty.svg"
id2url["II.71"]   = "maxspeed-end-empty.svg"
id2url["II.323a"] = "zone-maxspeed-empty.svg"
id2url["MII.1"]   = "distance-empty.svg"
id2url["MII.2"]   = "length-empty.svg"
id2url["MII.3"]   = "distance-empty.svg"

# meta[lang="it"] {}

quoted = ",\n".join([f'"{k}", "{v}"' for k, v in id2url.items()])
aspects = ",\n".join([f'"{k}", {v:.2}' for k, v in id2aspect.items()])
print(f"""
globals {{
    urls: map_build(\n{quoted}\n);
    aspects: map_build(\n{aspects}\n)
}}
""")

print("""
setting::icon_size {
    type: double;
    label: tr("Set the icon size...");
    default: 32;
}

/* turn off maxspeed drawing in elemstyle.mapcss */
node[prop(maxspeedclass, default)]::core_maxnodebg {
    symbol-shape: none;
    icon-image: none;
}
node[prop(maxspeedclass, default)]::core_maxnodefg {
    text: none;
    symbol-shape: none;
    icon-image: none;
}

/* select nodes to operate on */

node[traffic_sign=~/IT:M?II/] {
    set .sign;
}
node[direction=forward].sign {
    set .sign-forward;
    traffic-sign-forward: tag("traffic_sign");
    maxspeed-forward: tag("maxspeed");
}
node[direction=backward].sign {
    set .sign-backward;
    traffic-sign-backward: tag("traffic_sign");
    maxspeed-backward: tag("maxspeed");
}
node["traffic_sign:forward"=~/IT:M?II/] {
    set .sign;
    set .sign-forward;
    traffic-sign-forward: tag("traffic_sign:forward");
    maxspeed-forward: tag("maxspeed:forward");
}
node["traffic_sign:backward"=~/IT:M?II/] {
    set .sign;
    set .sign-backward;
    traffic-sign-backward: tag("traffic_sign:backward");
    maxspeed-backward: tag("maxspeed:backward");
}

/* set a basic rotation */

node[direction=~/^[NWSE]+$/].sign {
    set .sign-noward;
    traffic-sign: tag("traffic_sign");
    maxspeed: tag("maxspeed");
    transformprop: transform(rotate(cardinal_to_radians(tag("direction")) + 3.14));
}
node[direction=~/^[.0-9]+$/].sign {
    set .sign-noward;
    traffic-sign: tag("traffic_sign");
    maxspeed: tag("maxspeed");
    transformprop: transform(rotate(degree_to_radians(tag("direction")) + 3.14));
}
/* we need the highway selector for the heading to work */
way[highway] > node.sign-forward,
way[highway] > node.sign-backward {
    transformprop: transform(rotate(heading()), translate(metric(5), 0));
    metric: true;
}

node[prop(traffic-sign)] {
    codes-noward: split_traffic_sign(prop(traffic-sign), "IT");
    icon-image: none;
}
node[prop(traffic-sign-forward)].sign {
    codes-forward: split_traffic_sign(prop(traffic-sign-forward), "IT");
    icon-image: none;
}
node[prop(traffic-sign-backward)] {
    codes-backward: split_traffic_sign(prop(traffic-sign-backward), "IT");
    icon-image: none;
}

/* set styles */

node.sign {
    yoffsetprop: eval(setting("icon_size") * 0.7);
    ydeltaprop: eval(setting("icon_size") * 0.4);

    text: none
}
""")

for layer in ("noward", "forward", "backward"):
    transform = "prop(transformprop, default)"
    if layer == "backward":
        transform = "transform(rotate(0.5turn), prop(transformprop, default))"

    prev_layer = -1
    for idx in range(3): # how many signs max. on one pole
        print (f"""
node[prop(sign-{layer}, default)][map_get(get(prop(codes-{layer}, default), {idx}), "country") = "IT"]::{layer}{idx} {{

    icon-transform: {transform};
    metric: true;

    codes: get(prop(codes-{layer}, default), {idx});
    traffic-sign-id:    map_get(prop(codes), "id");
    traffic-sign-texts: map_get(prop(codes), "text");

    icon-image: concat(map_get(prop(urls, globals), prop(traffic-sign-id), "unknown"),
                "?", URL_query_encode("text", prop(traffic-sign-texts)));
    real-height: eval(setting("icon_size") * map_get(prop(aspects, globals), prop(traffic-sign-id), 1.0));

    font-size: eval(prop(real-height) * 0.75);
    """)
        if (idx == 0):
            print("""
    yoffsetprop: prop(real-height) * 0.5;
            """)
        if idx > 0:
            print(f"""
    yoffsetprop: prop(yoffsetprop, {layer}{idx - 1}) + prop(real-height);

    icon-offset-y: prop(yoffsetprop) - prop(real-height) * 0.5;
            """)
        print("}\n\n")

print("""
/* There's a bug in the JOSM mapcss implementation. If you have a ruleset that matches
multiple layers like:

    node[traffic_sign:forward]::forward,
    node[traffic_sign:backward]::backward {
        icon-image: foo;
    }

then on a node with both of the above mentioned tags, only the "forward" level will get
an icon-image. */

node[prop(sign, default)]::* {
    major-z-index: 7;
    icon-width: setting("icon_size");
    icon-height: setting("icon_size");

    text-anchor-horizontal: center;
    text-anchor-vertical: center;
    font-weight: bold;
    text-color: black;
}

node|z-16[prop(sign, default)]::* {
    icon-image: none;
    text: none;
}
""")
