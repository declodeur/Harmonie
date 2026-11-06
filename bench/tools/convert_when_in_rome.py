#!/usr/bin/env python3
"""
Convertit des œuvres du corpus « When in Rome » (partition MusicXML + analyses RomanText)
dans le format simple lu par le banc d'essai d'Harmonie.

Aucune dépendance (pas de music21) : lecteur MusicXML et interprétation des chiffrages romains
écrits ici, selon les conventions de music21 (référence du corpus) :
  - majuscule = accord majeur, minuscule = mineur, o = diminué, ø (ou /o) = demi-diminué, + = augmenté ;
  - en mineur, vi / vii (minuscules) sont construits sur les 6e et 7e degrés hauts, VI / VII sur les
    degrés naturels ;
  - la septième est celle de la gamme (mineur naturel en mineur), sauf o7 (septième diminuée) et ø7 ;
  - X/Y : X dans la tonalité de Y (majeure si Y en majuscules, mineure sinon) ;
  - Cad64 = I64 ; N = ♭II ; It6, Ger65, Fr43 = sixtes augmentées.

Format de sortie (une ligne par élément, champs séparés par des tabulations) :
  # commentaires
  P  titre  identifiant
  N  début  durée  note-midi            (temps en noires)
  M  numéro  début  durée                (mesures de la partition)
  A  analyste  début  fin  mesure  temps  tonique  mode(M/m)  chiffrage  fondamentale  basse  classes  spécial
     (classes = classes de hauteur de l'accord, séparées par des virgules ; spécial = « - » ou It/Ger/Fr/Cad/N)

Usage :
  convert_when_in_rome.py chorales <dossier When-in-Rome> <dossier Chorale-Corpus> <dossier de sortie>
"""
import json
import os
import re
import sys
import zipfile
import xml.etree.ElementTree as ET
from fractions import Fraction

STEP_PC = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}

# ---------------------------------------------------------------- MusicXML


def read_musicxml(path):
    """Renvoie (notes, mesures) ; notes = [(début, durée, midi)], mesures = [(numéro, début, durée, (num, den))]."""
    if path.endswith(".mxl"):
        with zipfile.ZipFile(path) as z:
            container = ET.fromstring(z.read("META-INF/container.xml"))
            root_file = container.find(".//rootfile").get("full-path")
            root = ET.fromstring(z.read(root_file))
    else:
        root = ET.parse(path).getroot()

    notes = []
    measures = {}  # numéro -> [début, durée, signature]
    order = []
    for part in root.findall("part"):
        divisions = 1
        t = Fraction(0)
        time_sig = (4, 4)
        open_ties = {}  # (voix, midi) -> index dans notes
        for measure in part.findall("measure"):
            number = measure.get("number")
            start = t
            cursor = t
            max_cursor = t
            last_onset = t
            for el in measure:
                tag = el.tag
                if tag == "attributes":
                    d = el.find("divisions")
                    if d is not None:
                        divisions = int(d.text)
                    ts = el.find("time")
                    if ts is not None and ts.find("beats") is not None:
                        try:
                            time_sig = (int(ts.find("beats").text.split("+")[0]), int(ts.find("beat-type").text))
                        except ValueError:
                            pass
                elif tag == "backup":
                    cursor -= Fraction(int(el.find("duration").text), divisions)
                elif tag == "forward":
                    cursor += Fraction(int(el.find("duration").text), divisions)
                    max_cursor = max(max_cursor, cursor)
                elif tag == "note":
                    if el.find("grace") is not None:
                        continue
                    dur_el = el.find("duration")
                    dur = Fraction(int(dur_el.text), divisions) if dur_el is not None else Fraction(0)
                    is_chord = el.find("chord") is not None
                    onset = last_onset if is_chord else cursor
                    if not is_chord:
                        last_onset = cursor
                        cursor += dur
                        max_cursor = max(max_cursor, cursor)
                    pitch = el.find("pitch")
                    if pitch is None:
                        continue
                    step = pitch.find("step").text
                    alter = pitch.find("alter")
                    alter = int(float(alter.text)) if alter is not None else 0
                    octave = int(pitch.find("octave").text)
                    midi = 12 * (octave + 1) + STEP_PC[step] + alter
                    voice = el.findtext("voice", "1") + "/" + el.findtext("staff", "1")
                    ties = [x.get("type") for x in el.findall("tie")]
                    key = (part.get("id"), voice, midi)
                    if "stop" in ties and key in open_ties:
                        i = open_ties[key]
                        s, d, m = notes[i]
                        notes[i] = (s, d + dur, m)
                        if "start" not in ties:
                            del open_ties[key]
                        continue
                    notes.append((onset, dur, midi))
                    if "start" in ties:
                        open_ties[key] = len(notes) - 1
            length = max_cursor - start
            if number not in measures:
                measures[number] = [start, length, time_sig]
                order.append(number)
            else:
                measures[number][1] = max(measures[number][1], length)
            t = start + length
    notes.sort()
    return notes, [(n, measures[n][0], measures[n][1], measures[n][2]) for n in order]


def beat_duration(time_sig):
    """Durée d'un temps en noires, selon music21 (6/8, 9/8, 12/8 et 3/8 : noire pointée)."""
    num, den = time_sig
    if den == 8 and num % 3 == 0:
        return Fraction(3, 2)
    return Fraction(4, den)


# ---------------------------------------------------------------- Chiffrages romains

MAJOR = [0, 2, 4, 5, 7, 9, 11]
NAT_MINOR = [0, 2, 3, 5, 7, 8, 10]
ROMAN = {"i": 1, "ii": 2, "iii": 3, "iv": 4, "v": 5, "vi": 6, "vii": 7}


def parse_key(token):
    """« G: » → (7, False) ; « f#: » → (6, True) ; None si ce n'est pas une tonalité."""
    m = re.fullmatch(r"([A-Ga-g])([#b-]*):", token)
    if not m:
        return None
    letter, acc = m.groups()
    pc = STEP_PC[letter.upper()] + acc.count("#") - acc.count("b") - acc.count("-")
    return pc % 12, letter.islower()


def degree_root(key, numeral, lower):
    """Classe de hauteur de la fondamentale d'un degré (1-7) dans une tonalité."""
    tonic, minor = key
    deg = ROMAN[numeral.lower()]
    if not minor:
        iv = MAJOR[deg - 1]
    else:
        iv = NAT_MINOR[deg - 1]
        if deg in (6, 7) and lower:  # vi, vii en mineur : degrés hauts
            iv += 1
    return (tonic + iv) % 12, iv


class Unparsed(Exception):
    pass


SIMPLE = re.compile(
    r"(?P<acc>[b#]*)(?P<num>VII|VI|IV|V|III|II|I|vii|vi|iv|v|iii|ii|i)"
    r"(?P<qual>o|ø|\+|%)?(?P<maj>maj)?(?P<fig>[0-9]*)(?P<add>(?:\[[^\]]*\])*)"
)


def chord_of(figure, key):
    """(fondamentale, basse, classes, spécial) d'un chiffrage dans une tonalité."""
    fig = figure.replace("/o", "ø")
    # Accords spéciaux
    special = re.fullmatch(r"(It|Ger|Fr)([0-9/]*)(?:/(.+))?", fig)
    if special:
        kind, _, sec = special.groups()
        k = secondary_key(sec, key) if sec else key
        t = k[0]
        ivs = {"It": [8, 0, 6], "Ger": [8, 0, 3, 6], "Fr": [8, 0, 2, 6]}[kind]
        pcs = [(t + i) % 12 for i in ivs]
        return pcs[0], pcs[0], pcs, kind
    if fig in ("Cad64", "Cad"):
        t = key[0]
        third = 3 if key[1] else 4
        return t, (t + 7) % 12, [t, (t + third) % 12, (t + 7) % 12], "Cad"
    m = re.fullmatch(r"(b?)N(6?)(?:/(.+))?", fig)
    if m:
        _, six, sec = m.groups()
        k = secondary_key(sec, key) if sec else key
        r = (k[0] + 1) % 12
        pcs = [r, (r + 4) % 12, (r + 7) % 12]
        return r, pcs[1] if six else r, pcs, "N"

    # X/Y (éventuellement X/Y/Z)
    parts = re.split(r"/(?=[b#]*(?:VII|VI|IV|V|III|II|I|vii|vi|iv|v|iii|ii|i)(?![a-z]))", fig)
    main = parts[0]
    k = key
    for sec in reversed(parts[1:]):
        k = secondary_key_single(sec, k)
    return simple_chord(main, k) + ("-",)


def secondary_key(sec, key):
    parts = sec.split("/")
    k = key
    for p in reversed(parts):
        k = secondary_key_single(p, k)
    return k


def secondary_key_single(sec, key):
    m = re.fullmatch(r"([b#]*)(VII|VI|IV|V|III|II|I|vii|vi|iv|v|iii|ii|i)[o+ø]?", sec)
    if not m:
        raise Unparsed(sec)
    acc, num = m.groups()
    lower = num.islower()
    root, _ = degree_root(key, num, lower)
    root = (root + acc.count("#") - acc.count("b")) % 12
    return root, lower


def simple_chord(fig, key):
    m = SIMPLE.fullmatch(fig.replace("/", ""))
    if not m:
        raise Unparsed(fig)
    acc, num, qual, maj, figure, _add = m.groups()
    lower = num.islower()
    root, iv = degree_root(key, num, lower)
    shift = acc.count("#") - acc.count("b")
    root = (root + shift) % 12

    if qual == "o":
        triad = [0, 3, 6]
    elif qual == "ø":
        triad = [0, 3, 6]
    elif qual == "+":
        triad = [0, 4, 8]
    elif lower:
        triad = [0, 3, 7]
    else:
        triad = [0, 4, 7]

    figure = figure or ""
    seventh_figs = {"7": 0, "75": 0, "73": 0, "753": 0, "65": 1, "653": 1, "43": 2, "643": 2, "42": 3, "2": 3, "642": 3}
    triad_figs = {"": 0, "5": 0, "53": 0, "6": 1, "63": 1, "64": 2}
    ivs = list(triad)
    if figure in seventh_figs or figure == "9" or qual == "ø" or maj:
        inversion = seventh_figs.get(figure, 0)
        if qual == "o":
            sev = 9
        elif qual == "ø":
            sev = 10
        elif maj:
            sev = 11
        elif shift == 0:
            # Septième de la gamme (mineur naturel en mineur)
            scale = NAT_MINOR if key[1] else MAJOR
            deg = ROMAN[num.lower()]
            seventh_pc = (key[0] + scale[(deg - 1 + 6) % 7]) % 12
            sev = (seventh_pc - root) % 12
            if sev not in (9, 10, 11):
                sev = 10
        else:
            sev = 10
        ivs.append(sev)
        if figure == "9":
            scale = NAT_MINOR if key[1] else MAJOR
            deg = ROMAN[num.lower()]
            ninth_pc = (key[0] + scale[(deg - 1 + 8) % 7]) % 12
            ivs.append((ninth_pc - root) % 12)
            if "[b9]" in (_add or ""):
                ivs[-1] = 1
    elif figure in triad_figs:
        inversion = triad_figs[figure]
    else:
        raise Unparsed(fig)
    pcs = [(root + i) % 12 for i in ivs]
    bass = pcs[inversion] if inversion < len(pcs) else root
    return root, bass, pcs


# ---------------------------------------------------------------- RomanText


def parse_romantext(path, measures, by_score_numbers=False):
    """
    Liste de (début, mesure, temps, tonalité, chiffrage) d'une analyse RomanText.

    Comme music21, les débuts sont calculés d'après la métrique de l'analyse (et non d'après les
    numéros de mesure de la partition, parfois fantaisistes avec les reprises) : la première mesure
    de l'analyse correspond à la première mesure de la partition (levée éventuelle comprise), puis
    chaque mesure dure ce qu'indique la signature rythmique.

    Avec [by_score_numbers], on se cale au contraire sur les numéros de mesure de la partition
    (utile quand la partition a des mesures irrégulières). Le convertisseur garde la méthode
    qui aligne le mieux les accords des experts sur les notes de la partition.
    """
    by_number = {n: (st, ln) for n, st, ln, _ in measures}
    score_ts = measures[0][3] if measures else (4, 4)
    score_first_len = measures[0][2] if measures else None
    ts = None
    events = []
    key = None
    measure_lines = {}
    starts = {}  # numéro -> (début, signature)
    state = {"prev": None, "prev_start": Fraction(0), "prev_len": Fraction(0)}

    def place(number, cur_ts):
        """Début de la mesure [number] (première mesure : levée d'après la partition)."""
        nominal = Fraction(cur_ts[0] * 4, cur_ts[1])
        if by_score_numbers:
            info = by_number.get(str(number))
            if info is None:
                return None, None
            padding = nominal - info[1] if str(number) == measures[0][0] and info[1] < nominal else Fraction(0)
            state.update(prev=number)
            return info[0], padding
        if state["prev"] is None:
            start = Fraction(0)
            length = score_first_len if score_first_len and score_first_len < nominal else nominal
            padding = nominal - length
        else:
            gap = number - state["prev"] - 1
            start = state["prev_start"] + state["prev_len"] + max(gap, 0) * nominal
            length = nominal
            padding = Fraction(0)
        state.update(prev=number, prev_start=start, prev_len=length)
        return start, padding

    for line in open(path, encoding="utf-8").read().splitlines():
        line = line.strip()
        m = re.match(r"^Time Signature\s*:\s*(\d+)\s*/\s*(\d+)", line)
        if m:
            ts = (int(m.group(1)), int(m.group(2)))
            continue
        cur_ts = ts or score_ts
        m = re.match(r"^m(\d+)(?:-(\d+))?\s*=\s*m(\d+)(?:-(\d+))?\s*$", line)
        if m:
            a, b, c, _ = m.groups()
            a, c = int(a), int(c)
            b = int(b) if b else a
            for i in range(b - a + 1):
                src = measure_lines.get(c + i)
                if src is not None and (state["prev"] is None or a + i > state["prev"]):
                    measure_lines[a + i] = src
                    start, padding = place(a + i, cur_ts)
                    if start is None:
                        continue
                    evs, key = measure_events(a + i, src, start, padding, cur_ts, key)
                    events.extend(evs)
            continue
        m = re.match(r"^m(\d+)\s+(.*)$", line)
        if not m:
            continue
        number, rest = int(m.group(1)), m.group(2)
        if state["prev"] is not None and number <= state["prev"]:
            continue  # variante ou retour en arrière : ignoré
        measure_lines[number] = rest
        start, padding = place(number, cur_ts)
        if start is None:
            continue
        evs, key = measure_events(number, rest, start, padding, cur_ts, key)
        events.extend(evs)
    events.sort(key=lambda e: e[0])
    return events


def measure_events(number, rest, start, padding, ts, key):
    bd = beat_duration(ts)
    beat = Fraction(1)
    evs = []
    for token in rest.split():
        if re.fullmatch(r"b[0-9]+(\.[0-9]+)?", token):
            beat = Fraction(token[1:]).limit_denominator(48)
            continue
        if re.fullmatch(r":?\|+:?", token):
            continue
        k = parse_key(token)
        if k is not None:
            key = k
            continue
        if token.endswith(":") or key is None:
            continue
        onset = start + (beat - 1) * bd - padding
        evs.append((onset, number, float(beat), key, token))
    return evs, key


def alignment(notes, events):
    """Part moyenne des notes qui sonnent au début de chaque accord et qui appartiennent à cet accord."""
    total, count = 0.0, 0
    for i, (onset, _, _, key, figure) in enumerate(events):
        stop = events[i + 1][0] if i + 1 < len(events) else onset + 1
        t = onset + min(Fraction(1, 4), (stop - onset) / 2)
        sounding = {m % 12 for s, d, m in notes if s <= t < s + d}
        if not sounding:
            continue
        try:
            pcs = set(chord_of(figure, key)[2])
        except (Unparsed, KeyError, ValueError):
            continue
        total += len(sounding & pcs) / len(sounding)
        count += 1
    return total / count if count else 0.0


def best_analysis(path, notes, measures):
    """Analyse alignée par la métrique ou par les numéros de mesure : la mieux alignée."""
    a = parse_romantext(path, measures)
    qa = alignment(notes, a)
    b = parse_romantext(path, measures, by_score_numbers=True)
    qb = alignment(notes, b)
    return (b, qb) if qb > qa + 0.01 else (a, qa)


# ---------------------------------------------------------------- Écriture


def fmt(x):
    return ("%.4f" % float(x)).rstrip("0").rstrip(".")


def write_piece(out_path, title, ident, notes, measures, analyses, stats, quality):
    end = max((s + d for s, d, _ in notes), default=Fraction(0))
    with open(out_path, "w", encoding="utf-8") as f:
        f.write("# Converti depuis le corpus When in Rome (CC BY-SA 4.0), voir bench/data/README.md\n")
        f.write("P\t%s\t%s\t%.3f\n" % (title, ident, quality))
        for n, s, l, _ in measures:
            f.write("M\t%s\t%s\t%s\n" % (n, fmt(s), fmt(l)))
        for s, d, m in notes:
            f.write("N\t%s\t%s\t%d\n" % (fmt(s), fmt(d), m))
        for analyst, events in analyses:
            for i, (onset, number, beat, key, figure) in enumerate(events):
                stop = events[i + 1][0] if i + 1 < len(events) else end
                if stop <= onset:
                    continue
                try:
                    root, bass, pcs, special = chord_of(figure, key)
                except (Unparsed, KeyError, ValueError):
                    stats["unparsed"][figure] = stats["unparsed"].get(figure, 0) + 1
                    continue
                stats["parsed"] += 1
                f.write("A\t%s\t%s\t%s\t%d\t%s\t%d\t%s\t%s\t%d\t%d\t%s\t%s\n" % (
                    analyst, fmt(onset), fmt(stop), number, fmt(beat), key[0], "m" if key[1] else "M",
                    figure, root, bass, ",".join(str(p) for p in pcs), special))


def convert_chorales(wir, chorale_corpus, out_dir):
    base = os.path.join(wir, "Corpus", "Early_Choral", "Bach,_Johann_Sebastian", "Chorales")
    scores = os.path.join(chorale_corpus, "Bach,_Johann_Sebastian", "Chorales")
    os.makedirs(out_dir, exist_ok=True)
    stats = {"parsed": 0, "unparsed": {}, "pieces": 0, "missing": []}
    for d in sorted(os.listdir(base)):
        folder = os.path.join(base, d)
        score = os.path.join(scores, d, "short_score.mxl")
        if not os.path.isfile(score):
            stats["missing"].append(d)
            continue
        meta = json.load(open(os.path.join(folder, "remote.json"), encoding="utf-8"))
        notes, measures = read_musicxml(score)
        analyses = []
        quality = 0.0
        for analyst, name in (("A", "analysis.txt"), ("B", "analysis_BCMH.txt")):
            p = os.path.join(folder, name)
            if os.path.isfile(p):
                events, q = best_analysis(p, notes, measures)
                analyses.append((analyst, events))
                if analyst == "A":
                    quality = q
        title = "Choral %s (BWV %s) — %s" % (d, meta.get("BWV", "?"), meta.get("text", meta.get("tune", "")))
        write_piece(os.path.join(out_dir, d + ".tsv"), title, d, notes, measures, analyses, stats, quality)
        stats["pieces"] += 1
    return stats


if __name__ == "__main__":
    if len(sys.argv) != 5 or sys.argv[1] != "chorales":
        print(__doc__)
        sys.exit(1)
    s = convert_chorales(sys.argv[2], sys.argv[3], sys.argv[4])
    print("Pièces : %d, chiffrages lus : %d" % (s["pieces"], s["parsed"]))
    if s["missing"]:
        print("Partitions manquantes :", " ".join(s["missing"]))
    if s["unparsed"]:
        print("Chiffrages non lus :", sorted(s["unparsed"].items(), key=lambda x: -x[1]))
