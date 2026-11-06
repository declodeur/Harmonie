#!/usr/bin/env python3
"""
Convertit des corpus pop / rock (accords et tonalités notés par des experts, sans partition) dans
le format du banc d'essai d'Harmonie. Les notes sont GÉNÉRÉES à partir des accords des experts :
un accompagnement de piano simple (basse + accord serré, conduite des voix minimale, accord
rejoué environ chaque seconde). Ces corpus testent donc la tonalité et le chiffrage en contexte pop,
pas la reconnaissance des accords (qui joue exactement les notes de l'expert).

Sources (dépôt ChoCo, https://github.com/smashub/choco, licence CC BY 4.0) :
  - rock-corpus : RS 200 de Trevor de Clercq et David Temperley (deux analyses par chanson,
    chiffrage romain, tonique de la tonalité seulement) ; fichiers .clt (temps réel en secondes).
  - isophonics : accords et tonalités (avec le mode) des Beatles, de Queen, Carole King, Zweieck,
    Michael Jackson (Centre for Digital Music, Queen Mary University of London) ; fichiers JAMS.

Usage : convert_pop.py <dossier choco> <dossier de sortie (bench/data)>
"""
import glob
import json
import os
import re
import sys

NOTE_PC = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}

# ------------------------------------------------------------------ Synthèse


def voice(pcs, bass, prev_upper):
    """Basse à l'octave 2, accord serré au-dessus de G3, au plus près de l'accord précédent."""
    bass_note = 36 + bass
    tones = sorted(set(pcs))
    best = None
    for low in range(52, 66):
        upper = []
        n = low
        for pc in sorted(tones, key=lambda p: (p - low) % 12):
            n2 = low + (pc - low) % 12
            upper.append(n2)
        upper.sort()
        if prev_upper:
            cost = sum(min(abs(u - p) for p in prev_upper) for u in upper)
        else:
            cost = abs(sum(upper) / len(upper) - 64)
        if best is None or cost < best[0]:
            best = (cost, upper)
    return bass_note, best[1]


def synthesize(chords):
    """chords : [(début s, fin s, classes, basse)] → notes [(début, durée, midi)]."""
    notes = []
    prev = None
    for start, end, pcs, bass in chords:
        if not pcs or end - start < 0.05:
            continue
        b, upper = voice(pcs, bass, prev)
        prev = upper
        notes.append((start, end - start, b))
        # Accord rejoué environ chaque seconde (accompagnement), sans dépasser la fin
        n = max(1, round((end - start) / 1.0))
        step = (end - start) / n
        for i in range(n):
            s = start + i * step
            for u in upper:
                notes.append((s, step * 0.95, u))
    notes.sort()
    return notes


def fmt(x):
    return ("%.4f" % x).rstrip("0").rstrip(".")


def write(path, title, ident, notes, analyses, tonic_only):
    with open(path, "w", encoding="utf-8") as f:
        f.write("# Converti depuis ChoCo (CC BY 4.0) ; notes générées à partir des accords, voir bench/data/README.md\n")
        # Temps en secondes : tempo 60 à la noire (une « noire » = une seconde)
        f.write("P\t%s\t%s\t1.000\t60\t%s\n" % (title, ident, "tonique" if tonic_only else ""))
        for s, d, m in notes:
            f.write("N\t%s\t%s\t%d\n" % (fmt(s), fmt(d), m))
        for analyst, chords in analyses:
            for c in chords:
                start, end, pcs, bass, root, tonic, minor, label, bar, beat = c
                f.write("A\t%s\t%s\t%s\t%d\t%s\t%d\t%s\t%s\t%d\t%d\t%s\t-\n" % (
                    analyst, fmt(start), fmt(end), bar, beat, tonic, "m" if minor else "M", label, root, bass,
                    ",".join(str(p) for p in sorted(set(pcs)))))


# ------------------------------------------------------------------ RS 200 (rock corpus)


def rock_quality(numeral):
    """(intervalles, intervalle de basse) d'un chiffrage du rock corpus (« IVd7 », « V64 », « iih7 »)."""
    main = numeral.split("/")[0]
    m = re.fullmatch(r"([b#]*)(VII|VI|IV|V|III|II|I|vii|vi|iv|v|iii|ii|i)(.*)", main)
    if not m:
        return None
    _, num, rest = m.groups()
    lower = num.islower()
    up = num.upper()
    ivs = [0, 3, 7] if lower else [0, 4, 7]
    if rest.startswith("o"):
        ivs = [0, 3, 6]
        rest = rest[1:]
    elif rest.startswith("x"):
        ivs = [0, 3, 6, 9]
        rest = rest[1:]
    elif rest.startswith("h"):
        ivs = [0, 3, 6, 10]
        rest = rest[1:]
    elif rest.startswith("a") or rest.startswith("+"):
        ivs = [0, 4, 8]
        rest = rest[1:]
    if "s4" in rest:
        ivs = [0, 5, 7] + ([10] if "7" in rest else [])
        rest = rest.replace("s4", "")
    if rest.startswith("d"):
        ivs = [0, 4, 7, 10]
        rest = rest[1:]
    fig = re.match(r"[0-9]*", rest).group(0)
    tail = rest[len(fig):]
    seventh_figs = {"7": 0, "65": 1, "43": 2, "42": 3}
    triad_figs = {"": 0, "6": 1, "64": 2}
    inversion = 0
    if fig == "11":
        ivs = [0, 5, 7, 10]  # V11 = IV/V
    elif fig == "9":
        if len(ivs) == 3:
            ivs = ivs + [10 if not lower and up != "I" and up != "IV" else (11 if not lower else 10)]
        ivs = ivs + [2]
    elif fig in seventh_figs:
        inversion = seventh_figs[fig]
        if len(ivs) == 3:
            if lower:
                ivs = ivs + [10]
            elif up in ("I", "IV"):
                ivs = ivs + [11]
            else:
                ivs = ivs + [10]
    elif fig in triad_figs:
        inversion = triad_figs[fig]
    if "b5" in tail:
        ivs = [i if i != 7 else 6 for i in ivs]
    if "#9" in tail:
        ivs = ivs + [3]
    if "b9" in tail:
        ivs = ivs + [1]
    # Notes de l'accord dans l'ordre fondamentale, tierce (ou quarte / seconde), quinte, septième
    def first(cands):
        return next((x for x in cands if x in ivs), None)
    chord_tones = [0] + [x for x in (first((4, 3, 5, 2)), first((7, 6, 8)), first((10, 11, 9))) if x is not None]
    bass_iv = chord_tones[inversion] if inversion < len(chord_tones) else 0
    return sorted(set(ivs)), bass_iv


def convert_rock(choco, out_dir):
    raw = os.path.join(choco, "partitions", "rock-corpus", "raw")
    songs = json.load(open(os.path.join(raw, "songs.json"), encoding="utf-8"))
    by_file = {}
    for title in songs:
        key = re.sub(r"[^a-z0-9]+", "_", title.lower()).strip("_")
        by_file[key] = title
    os.makedirs(out_dir, exist_ok=True)
    stats = {"pieces": 0, "chords": 0, "skipped": 0}
    files = sorted(glob.glob(os.path.join(raw, "rs200_harmony_clt", "*_dt.clt")))
    for dt in files:
        base = os.path.basename(dt)[:-len("_dt.clt")]
        analyses = []
        for analyst, suffix in (("A", "_dt.clt"), ("B", "_tdc.clt")):
            p = os.path.join(raw, "rs200_harmony_clt", base + suffix)
            if not os.path.isfile(p):
                continue
            rows = []
            for line in open(p, encoding="utf-8", errors="ignore"):
                f = line.split()
                if len(f) < 7:
                    continue
                try:
                    t, bars, numeral, key_pc, abs_root = float(f[0]), float(f[1]), f[2], int(f[5]), int(f[6])
                except ValueError:
                    continue
                rows.append((t, bars, numeral, key_pc, abs_root))
            chords = []
            for i, (t, bars, numeral, key_pc, root) in enumerate(rows):
                end = rows[i + 1][0] if i + 1 < len(rows) else None
                if numeral == "End" or end is None:
                    continue
                q = rock_quality(numeral)
                if q is None:
                    stats["skipped"] += 1
                    continue
                ivs, bass_iv = q
                pcs = [(root + i) % 12 for i in ivs]
                bar = int(bars)
                beat = int(round((bars - bar) * 4)) + 1
                chords.append((t, end, pcs, (root + bass_iv) % 12, root, key_pc, False, numeral, bar, beat))
            analyses.append((analyst, chords))
        if not analyses or not analyses[0][1]:
            continue
        a = analyses[0][1]
        notes = synthesize([(c[0], c[1], c[2], c[3]) for c in a])
        title = by_file.get(base, base.replace("_", " ").title())
        write(os.path.join(out_dir, base + ".tsv"), title, base, notes, analyses, True)
        stats["pieces"] += 1
        stats["chords"] += len(a)
    return stats


# ------------------------------------------------------------------ Isophonics (JAMS, notation de Harte)

SHORTHAND = {
    "maj": [0, 4, 7], "min": [0, 3, 7], "dim": [0, 3, 6], "aug": [0, 4, 8], "maj7": [0, 4, 7, 11],
    "min7": [0, 3, 7, 10], "7": [0, 4, 7, 10], "dim7": [0, 3, 6, 9], "hdim7": [0, 3, 6, 10],
    "minmaj7": [0, 3, 7, 11], "maj6": [0, 4, 7, 9], "min6": [0, 3, 7, 9], "9": [0, 4, 7, 10, 2],
    "maj9": [0, 4, 7, 11, 2], "min9": [0, 3, 7, 10, 2], "sus4": [0, 5, 7], "sus2": [0, 2, 7],
    "11": [0, 4, 7, 10, 2, 5], "13": [0, 4, 7, 10, 2, 9], "min11": [0, 3, 7, 10, 2, 5],
}
DEGREE = {"1": 0, "b2": 1, "2": 2, "#2": 3, "b3": 3, "3": 4, "4": 5, "#4": 6, "b5": 6, "5": 7, "#5": 8,
          "b6": 8, "6": 9, "bb7": 9, "b7": 10, "7": 11, "b9": 1, "9": 2, "#9": 3, "11": 5, "#11": 6,
          "b13": 8, "13": 9}


def note_pc(name):
    pc = NOTE_PC[name[0]]
    for ch in name[1:]:
        pc += 1 if ch == "#" else -1 if ch == "b" else 0
    return pc % 12


def harte(label):
    """(fondamentale, classes, basse) d'un accord « F#:min7/b7 », ou None (N, X)."""
    if label in ("N", "X") or not label:
        return None
    m = re.fullmatch(r"([A-G][#b]*)(?::([^/(]*)(\(([^)]*)\))?)?(?:/(.+))?", label)
    if not m:
        return None
    root_name, short, _, ext, bass = m.groups()
    root = note_pc(root_name)
    if short in (None, ""):
        ivs = [] if ext is not None and short == "" and ":" in label else [0, 4, 7]
        if ext is None:
            ivs = [0, 4, 7]
    else:
        if short not in SHORTHAND:
            return None
        ivs = list(SHORTHAND[short])
    if ext:
        for e in ext.split(","):
            e = e.strip()
            if e.startswith("*"):
                d = DEGREE.get(e[1:])
                if d is not None and d in ivs:
                    ivs.remove(d)
            elif e in DEGREE:
                ivs.append(DEGREE[e])
    if not ivs:
        return None
    bass_pc = root if bass is None else (root + DEGREE.get(bass, 0)) % 12
    return root, sorted({(root + i) % 12 for i in ivs}), bass_pc


def harte_key(value):
    """« A » → (9, False) ; « A:minor » → (9, True) ; modes : dorien, éolien, phrygien = mineur."""
    if value in ("N", "X", ""):
        return None
    name, _, mode = value.partition(":")
    try:
        tonic = note_pc(name)
    except KeyError:
        return None
    minor = mode in ("minor", "aeolian", "dorian", "phrygian", "locrian")
    return tonic, minor


def convert_isophonics(choco, out_dir):
    base = os.path.join(choco, "partitions", "isophonics", "choco")
    meta = {}
    for line in open(os.path.join(base, "meta.csv"), encoding="utf-8").read().splitlines()[1:]:
        parts = line.split(",")
        if len(parts) >= 3:
            meta[parts[0]] = (parts[1], parts[2])
    os.makedirs(out_dir, exist_ok=True)
    stats = {"pieces": 0, "chords": 0, "skipped": 0}
    for path in sorted(glob.glob(os.path.join(base, "jams", "*.jams"))):
        ident = os.path.basename(path)[:-5]
        d = json.load(open(path, encoding="utf-8"))
        chord_ann = next((a for a in d["annotations"] if a["namespace"] == "chord"), None)
        key_ann = next((a for a in d["annotations"] if a["namespace"] == "key_mode"), None)
        if chord_ann is None or key_ann is None:
            continue
        keys = [(o["time"], o["time"] + o["duration"], harte_key(o["value"])) for o in key_ann["data"]]

        def key_at(t):
            for s, e, k in keys:
                if s <= t < e:
                    return k
            return None
        chords = []
        for o in chord_ann["data"]:
            c = harte(o["value"])
            start, end = o["time"], o["time"] + o["duration"]
            if c is None:
                if o["value"] not in ("N", "X"):
                    stats["skipped"] += 1
                continue
            k = key_at(start + min(0.1, o["duration"] / 2))
            if k is None:
                continue
            root, pcs, bass = c
            chords.append((start, end, pcs, bass, root, k[0], k[1], o["value"], -1, fmt(start)))
        if not chords:
            continue
        notes = synthesize([(c[0], c[1], c[2], c[3]) for c in chords])
        artist, title = meta.get(ident, ("", ident))
        write(os.path.join(out_dir, ident + ".tsv"), "%s — %s" % (artist, title) if artist else title, ident,
              notes, [("A", chords)], False)
        stats["pieces"] += 1
        stats["chords"] += len(chords)
    return stats


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(1)
    print("rock-corpus :", convert_rock(sys.argv[1], os.path.join(sys.argv[2], "rock-corpus")))
    print("isophonics :", convert_isophonics(sys.argv[1], os.path.join(sys.argv[2], "isophonics")))
