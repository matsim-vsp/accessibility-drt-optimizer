#!/usr/bin/env python
#*-* coding: utf-8 *-*

import argparse
import glob
import re

from os.path import join, basename

from subprocess import call

R = re.compile(r"mae_([0-9.]+)_rmse_([0-9.]+)\.json")

def main(d, jar, network, features, model):

    print(f"Processing {d}")

    pattern = join(d, "**/it*.json")

    results = []

    for f in glob.glob(pattern, recursive=True):
        mae, rmse = R.findall(f)[0]
        results.append((f, float(mae), float(rmse)))

    results = sorted(results, key=lambda x: (x[1], x[2]))
    best = results[0]

    print(f"Best result: {best[0]}, mae: {best[1]}, rmse: {best[2]}")

    out = join(d, "..", "network_%s.xml.gz" % basename(d))

    print(f"Applying network params to {out}")

    call(["java", "-cp", jar, "org.matsim.application.prepare.network.params.ApplyNetworkParams" , "freespeed",
          "--network", network, "--input-features", features, "--model", model,
          "--factor-bounds", "-5,1",
          "--input-params", best[0], "--output", out])


def match_first(pattern):
    matched = glob.glob(pattern)
    if not matched:
        raise ValueError(f"No file matching {pattern} found")

    f = matched[0]
    print(f"Using {f}")
    return f

if __name__ == "__main__":

    parser = argparse.ArgumentParser(description="Create ")
    parser.add_argument("dirs", nargs="+", help="Directories with result data")
    parser.add_argument("--jar", help="Pattern to jar file", default="*.jar")
    parser.add_argument("--network", help="Pattern to jar file", default="*.xml.gz")
    parser.add_argument("--features", help="Pattern to jar file", default="*ft.csv.gz")
    parser.add_argument("--model", help="Pattern to jar file", default="org.matsim.application.prepare.network.params.ref.GermanyNetworkParams")

    args = parser.parse_args()

    jar = match_first(args.jar)
    network = match_first(args.network)
    features = match_first(args.features)

    for d in args.dirs:
        main(d, jar, network, features, args.model)