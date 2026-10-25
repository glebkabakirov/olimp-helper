"""Печатает строки Google-таблицы олимпиад в JSON вместе со ссылками из ячеек.

Использование:
    curl -sL -o table.xlsx "https://docs.google.com/spreadsheets/d/<ID>/export?format=xlsx"
    python3 routine/read_table.py table.xlsx
"""
import json
import re
import sys
import zipfile
import xml.etree.ElementTree as ET

NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
NS_PKG = "http://schemas.openxmlformats.org/package/2006/relationships"


def cell_ref(ref):
    m = re.match(r"([A-Z]+)(\d+)", ref)
    col = 0
    for ch in m.group(1):
        col = col * 26 + ord(ch) - 64
    return int(m.group(2)) - 1, col - 1


def main(path):
    z = zipfile.ZipFile(path)
    shared = []
    if "xl/sharedStrings.xml" in z.namelist():
        for si in ET.fromstring(z.read("xl/sharedStrings.xml")).iter(f"{{{NS_MAIN}}}si"):
            shared.append("".join(t.text or "" for t in si.iter(f"{{{NS_MAIN}}}t")))

    sheet = ET.fromstring(z.read("xl/worksheets/sheet1.xml"))
    cells = {}
    for c in sheet.iter(f"{{{NS_MAIN}}}c"):
        v = c.find(f"{{{NS_MAIN}}}v")
        if c.get("t") == "s" and v is not None:
            text = shared[int(v.text)]
        elif c.get("t") == "inlineStr":
            text = "".join(t.text or "" for t in c.iter(f"{{{NS_MAIN}}}t"))
        else:
            text = v.text if v is not None else ""
        cells[cell_ref(c.get("r"))] = text

    rels = {}
    rels_path = "xl/worksheets/_rels/sheet1.xml.rels"
    if rels_path in z.namelist():
        for r in ET.fromstring(z.read(rels_path)).iter(f"{{{NS_PKG}}}Relationship"):
            rels[r.get("Id")] = r.get("Target")
    links = {}
    for h in sheet.iter(f"{{{NS_MAIN}}}hyperlink"):
        rid = h.get(f"{{{NS_REL}}}id")
        if rid in rels:
            links[cell_ref(h.get("ref").split(":")[0])] = rels[rid]

    header = {col: text.strip() for (row, col), text in cells.items() if row == 0}
    max_row = max(r for r, _ in cells)
    out = []
    for row in range(1, max_row + 1):
        if not cells.get((row, 0), "").strip():
            continue
        item = {}
        for col, name in sorted(header.items()):
            item[name] = cells.get((row, col), "").strip()
            if (row, col) in links:
                item[name] = links[(row, col)]
        out.append(item)
    sys.stdout.reconfigure(encoding="utf-8")
    json.dump(out, sys.stdout, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main(sys.argv[1])
