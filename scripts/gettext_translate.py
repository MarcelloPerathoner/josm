#!/usr/bin/env python3

"""
Translate a string using a po file.

Examples:

  >>> gettext_translate.py path/to/de_DE.po "Yes"
  Ja

"""

import argparse
import os

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
        "-d",
        "--dir",
        metavar="DIR",
        help="A directory of .po files",
        default=None,
    )

    parser.add_argument(
        "-f"
        "--file",
        metavar="FILE",
        help="The .po file",
        default=None,
    )

    parser.add_argument(
        "input",
        metavar="INPUT",
        help="The string to translate",
        default=None,
    )

    return parser


def unquote(line):
    line = line.strip()
    line = line.strip("\"")
    return line


def parse_po(pofile):
    """ Scan a single po file for the translation. """

    msgid = ""
    msgstr = ""
    mode = ""
    with open(pofile, "r") as fp:
        for line in fp:
            if line.startswith("msgid "):
                if msgid == args.input:
                    return msgstr
                line = line[6:]
                mode ="id"
                msgid = ""
            if line.startswith("msgstr "):
                line = line[7:]
                mode = "str"
                msgstr = ""

            if mode == "id":
                msgid += unquote(line)
            if mode == "str":
                msgstr += unquote(line)

        if msgid == args.input:
            return msgstr
        return None


def main() -> None:  # noqa: C901
    """Run this."""

    parser = build_parser(__doc__)
    parser.parse_args(namespace=args)

    if args.dir:
        for file in os.listdir(args.dir):
            if file.endswith(".po"):
                if msg := parse_po(os.path.join(args.dir, file)):
                    print(file, msg)
    elif args.file:
        if msg := parse_po(args.file):
            print(msg)
    else:
        parser.print_usage()


if __name__ == "__main__":
    main()
