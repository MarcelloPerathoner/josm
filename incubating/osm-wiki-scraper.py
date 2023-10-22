#!/usr/bin/python3

""" Scrapes the OSM wiki for italian traffic signs and outputs json to stdout."""

import json
import re
from typing import Dict, Any
from urllib.parse import unquote

from lxml import etree, html
import requests
from tqdm import tqdm


def text(e, item, name):
    t = e.text_content().strip()
    if t != "":
        item[name] = t

def flt(s):
    return float(s.replace("mm", "").replace("px", "").replace("pt", "").strip())

def get_valid_filename(name):
    s = str(name).strip().replace(" ", "_")
    s = re.sub(r"(?u)[^-\w.]", "", s)
    return s

PAGE = "https://wiki.openstreetmap.org/wiki/IT:Road_signs_in_Italy#Segnaletica_verticale_(Vertical_signs)"
HEADERS = {'User-Agent': 'OSM Traffic Sign Bot/0.0.1 (marcello@perathoner.de)'}
PREFIX = re.compile("^https://upload.wikimedia.org/wikipedia/commons/./../Italian_traffic_signs?_-_")

r = requests.get(PAGE, headers = HEADERS)
root = html.fromstring(r.text)

section = None
items = []

for table in root.xpath("//table[contains(@class, 'wikitable')]"):
    for header in table.xpath("preceding::h2|preceding::h3|preceding::h4"):
        # get last one in doc order
        section = header.text_content().strip()
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

for item in tqdm(items):
    try:
        urls = item["urls"]
        if len(urls) > 0:
            url = urls[0]
            r = requests.get(url, headers = HEADERS)
            svg_root = etree.fromstring(r.content)

            if PREFIX.match(url):
                filename = get_valid_filename(unquote(PREFIX.sub("", url)).lower())
                with open("svgs/" + filename, "wb") as fp:
                    fp.write(r.content)
                item["filename"] = filename
            else:
                print (f"bogus url: {url}")

            width   = svg_root.get("width")
            height  = svg_root.get("height")
            viewBox = svg_root.get("viewBox")

            if width and height:
                item["width"] = flt(width)
                item["height"] = flt(height)
            elif viewBox:
                vb = viewBox.split()
                item["width"] = float(vb[2].strip())
                item["height"] = float(vb[3].strip())
    except (ValueError, etree.XMLSyntaxError) as e:
        print (url, e)

with open("osm-it-scrape.json", "w") as fp2:
    json.dump(items, fp2, indent=4, ensure_ascii=False)
