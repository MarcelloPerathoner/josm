#!/usr/bin/python3

""" Scrapes the OSM wiki for italian traffic signs and outputs json to stdout."""

import collections
from io import StringIO
import json
import re
from typing import Dict, Any

from lxml import etree, html
import requests


def text(e, item, name):
    t = e.text_content().strip();
    if t != "":
        item[name] = t


PAGE = "https://wiki.openstreetmap.org/wiki/IT:Road_signs_in_Italy#Segnaletica_verticale_(Vertical_signs)"
r = requests.get(PAGE)
root = html.fromstring(r.text)

section = None
items = []

for table in root.xpath("//table[contains(@class, 'wikitable')]"):
    for header in table.xpath("preceding::h2|preceding::h3|preceding::h4"):
        section = header.text_content().strip()
        continue # only last one in doc order
    for row in table.xpath(".//tr[td]"):
        item : Dict[str, Any] = {"section": section, "urls": [], "tags": [], "names": {}}
        for src in row.xpath("td[1]//img/@src"):
            if m := re.match("^(.*?\.svg)", src):
                item["urls"].append(m.group(1).replace("thumb/", ""))
        for td in row.xpath("td[2]"):
            text(td, item, "id")
        for td in row.xpath("td[3]"):
            text(td, item, "vienna")
        for td in row.xpath("td[4]"):
            text(td, item["names"], "it.name")
        for td in row.xpath("td[5]"):
            text(td, item["names"], "name")
        for td in row.xpath("td[6]"):
            for a in td.xpath("a[@href]"):
                item["wiki"] = a.get("href")
            for tt in td.xpath("tt"):
                item["tags"].append(tt.text_content().strip())
        items.append(item)

print(json.dumps(items, indent=4, ensure_ascii=False))
