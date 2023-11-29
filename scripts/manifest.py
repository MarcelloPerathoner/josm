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

def b64_icon(archive, bname: bytes) -> bytes:
    """Base-64-encode the plugin icon"""

    mimetype = ""
    name = bname.strip().decode("UTF-8")
    if name.endswith(".svg"):
        mimetype = b"image/svg+xml"
    if name.endswith(".png"):
        mimetype = b"image/png"
    if name.endswith(".jpg"):
        mimetype = b"image/jpeg"
    if name.endswith(".jpeg"):
        mimetype = b"image/jpeg"

    icon = archive.open(name).read()
    return b"data:" + mimetype + b";base64," + base64.b64encode(icon)


def scan_jar(filename : str):
    archive = zipfile.ZipFile(filename, 'r')
    output = []
    with archive.open('META-INF/MANIFEST.MF') as manifest:
        # this is almost too ridiculous for telling but Sun/Oracle decided to wrap
        # manifest lines after 72 *BYTES* not *CHARACTERS* so that if a multi-byte UTF-8
        # characters happens to start at pos. 71, we end up with the two halves of an
        # UTF-8-character separated by a newline and space.
        #
        # We must be careful to do all our fiddling with bytes until the lines are
        # re-joined.
        for line in manifest:
            if line.startswith(b" "):
                output.append(line[1:].rstrip(b"\r\n"))
            elif line.startswith(b"Plugin-Icon:") and args.icons:
                output.append(b"\n\tPlugin-Icon: " + b64_icon(archive, line[12:]))
            else:
                output.append(b"\n\t" + line.rstrip(b"\r\n"))

    out(b"".join(output).decode("UTF-8"))


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
