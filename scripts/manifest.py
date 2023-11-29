#!/usr/bin/env python3

"""
Build a manifest from a directory of plugins

Examples:

  manifest.py *.jar > MANIFEST
  manifest.py build/**.jar > MANIFEST
  manifest.py --root-dir /path/to/dist *.jar > MANIFEST
  manifest.py --base-url "https://example.org/plugins" *.jar > MANIFEST

"""

import argparse
import base64
import fnmatch
import glob
import io
import os.path
import sys
from urllib.parse import urljoin
import zipfile


args = argparse.Namespace()

def build_parser(description: str) -> argparse.ArgumentParser:
    """Build the commandline parser."""
    parser = argparse.ArgumentParser(
        description=description,
        formatter_class=argparse.RawDescriptionHelpFormatter,  # don't wrap my description
        fromfile_prefix_chars="@",
    )

    parser.add_argument(
        "-v",
        "--verbose",
        dest="verbose",
        action="count",
        help="increase output verbosity",
        default=0,
    )

    parser.add_argument(
        "-b",
        "--base-url",
        metavar="URL",
        help="The base URL for the download URL",
        default="",
    )

    parser.add_argument(
        "--root-dir",
        metavar="DIRECTORY",
        help="Use this directory as root dir. Default: the current directory.",
        default=".",
    )

    parser.add_argument(
        "--icons",
        action="store_true",
        help="Convert icon references to inline data URLs",
    )

    parser.add_argument(
        "-x",
        "--exclude",
        metavar="GLOB",
        nargs="+",
        type=str,
        help="Exclude this files. Default: *-sources.jar *-javadoc.jar",
        default=["*-sources.jar", "*-javadoc.jar"]
    )

    parser.add_argument(
        "globs",
        metavar="GLOB",
        nargs="+",
        type=str,
        help="Scan all these files.",
    )

    return parser

def out(s : str):
    sys.stdout.write(s)


def b64_icon(archive, name: str):
    mimetype = ""
    if name.endswith(".svg"):
        mimetype = "image/svg+xml"
    if name.endswith(".png"):
        mimetype = "image/png"
    if name.endswith(".jpg"):
        mimetype = "image/jpeg"
    if name.endswith(".jpeg"):
        mimetype = "image/jpeg"

    icon = archive.open(name).read()
    return f"data:{mimetype};base64,{base64.b64encode(icon).decode('UTF8')}"

def scan_jar(filename : str):
    archive = zipfile.ZipFile(filename, 'r')
    with archive.open('META-INF/MANIFEST.MF') as manifest:
        for line in io.TextIOWrapper(manifest, "UTF8"):
            if line.startswith(" "):
                out(line[1:].rstrip("\r\n"))
            elif line.startswith("Plugin-Icon:") and args.icons:
                out("\n\tPlugin-Icon: " + b64_icon(archive, line[12:].strip()))
            else:
                out("\n\t" + line.rstrip("\r\n"))


def main() -> None:  # noqa: C901
    """Run this."""
    parser = build_parser(__doc__)
    parser.parse_args(namespace=args)

    if not args.globs:
        parser.print_usage()
        sys.exit()

    for g in args.globs:
        for rel_path in glob.glob(g, root_dir=args.root_dir, recursive=True):
            # rel_path is relative to root_dir
            basename = os.path.basename(rel_path)
            if any(fnmatch.fnmatch(basename, exclude) for exclude in args.exclude):
                continue

            out(basename + ";" + urljoin(args.base_url, rel_path))
            scan_jar(os.path.join(args.root_dir, rel_path))
            out("\n")


if __name__ == "__main__":
    main()
