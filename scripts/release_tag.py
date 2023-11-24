#!/usr/bin/env python3

"""
Builds the release tag

Greps the git log for SVN-ish commit ids and returns the most recent one found.
Outputs a string in the form "RELEASE_TAG=12345-abcd". You can insert that string
into the github environment by:

Examples:

  release_tag.py --suffix custom MarcelloPerathoner/josm >> $GITHUB_ENV

"""

import argparse
import re
import requests
import sys


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
        "--suffix",
        metavar="STRING",
        help="The suffix, eg. 12345-suffix",
        default="",
    )

    parser.add_argument(
        "repo",
        metavar="OWNER/REPO",
        help="The owner/repository to query",
        default=None,
    )

    return parser

def out(s : str):
    sys.stdout.write(s)

REGEX = re.compile(r"git-svn-id:.*?@(\d+)")

def main() -> None:  # noqa: C901
    """Run this."""

    parser = build_parser(__doc__)
    parser.parse_args(namespace=args)

    if not args.repo:
        parser.print_usage()
        sys.exit()

    if args.suffix != "":
        args.suffix = "-" + args.suffix

    r = requests.get(f"https://api.github.com/repos/{args.repo}/commits")

    for commit in r.json():
        m = REGEX.search(commit["commit"]["message"])
        if m:
            out(f"RELEASE_TAG={m.group(1)}{args.suffix}\n")
            sys.exit(0)

    sys.exit(1)


if __name__ == "__main__":
    main()
