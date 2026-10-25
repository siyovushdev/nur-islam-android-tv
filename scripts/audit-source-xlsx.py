#!/usr/bin/env python3
"""Read-only independent source audit. Requires openpyxl==3.1.5 outside the app.

Creates a privacy-scrubbed test corpus and independent TSV oracle; no app parser is used.
No workbook formulas are evaluated. openpyxl reads their saved displayed results.
"""
import argparse
import datetime as dt
import hashlib
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
from xml.etree import ElementTree as ET
import openpyxl

parser = argparse.ArgumentParser()
parser.add_argument('source', type=Path)
parser.add_argument('--output', type=Path, default=Path('app/src/test/resources/xlsx'))
args = parser.parse_args()
out = args.output
out.mkdir(parents=True, exist_ok=True)
wb = openpyxl.load_workbook(args.source, data_only=True, read_only=True)
ws = wb['2026']
records = []
for row in ws.iter_rows(min_row=3, max_col=10):
    if all(c.value is None for c in row):
        continue
    assert row[0].value == 'Казань', 'Unexpected city'
    day = row[1].value
    assert isinstance(day, dt.datetime) and day.year == 2026
    values = []
    for cell in row[2:10]:
        value = cell.value
        if isinstance(value, (dt.datetime, dt.time)):
            assert value.second == 0 and value.microsecond == 0, (cell.coordinate, value)
            value = value.strftime('%H:%M')
        else:
            assert isinstance(value, str) and len(value) == 5
            dt.datetime.strptime(value, '%H:%M')
        values.append(value)
    records.append((day.date().isoformat(), *values))
assert len(records) == 365
assert [r[0] for r in records] == [(dt.date(2026, 1, 1) + dt.timedelta(days=i)).isoformat() for i in range(365)]
(out / 'kazan-2026.tsv').write_text('\n'.join('\t'.join(r) for r in records) + '\n')
# Keep original styles, shared strings, every cell/formula/cached value. Remove document
# metadata, private absolute paths, printer settings and unused relationships.
ns = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'
with ZipFile(args.source) as source, ZipFile(out / 'kazan-2026.xlsx', 'w', ZIP_DEFLATED) as target:
    kept = ['xl/styles.xml', 'xl/sharedStrings.xml', 'xl/theme/theme1.xml']
    for name in kept:
        target.writestr(name, source.read(name))
    sheet = ET.fromstring(source.read('xl/worksheets/sheet1.xml'))
    for tag in ['pageSetup', 'headerFooter', 'extLst']:
        for el in list(sheet.findall(ns + tag)):
            sheet.remove(el)
    target.writestr('xl/worksheets/sheet1.xml', ET.tostring(sheet, encoding='utf-8', xml_declaration=True))
    target.writestr('xl/workbook.xml', f'<workbook xmlns="{ns[1:-1]}" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="2026" sheetId="1" r:id="rId1"/></sheets></workbook>')
    rel = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships'
    target.writestr('xl/_rels/workbook.xml.rels', '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' + ''.join(f'<Relationship Id="rId{i}" Type="{rel}/{kind}" Target="{path}"/>' for i, kind, path in [(1, 'worksheet', 'worksheets/sheet1.xml'), (2, 'sharedStrings', 'sharedStrings.xml'), (3, 'styles', 'styles.xml'), (4, 'theme', 'theme/theme1.xml')]) + '</Relationships>')
    types = ET.fromstring(source.read('[Content_Types].xml'))
    for el in list(types):
        if el.get('Extension') == 'bin' or el.get('PartName', '').startswith('/docProps/') or el.get('PartName') == '/xl/calcChain.xml':
            types.remove(el)
    target.writestr('[Content_Types].xml', ET.tostring(types, encoding='utf-8', xml_declaration=True))
    target.writestr('_rels/.rels', f'<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="{rel}/officeDocument" Target="xl/workbook.xml"/></Relationships>')
# Prove scrubbing preserved independently read values, including all cached formula results.
scrubbed = openpyxl.load_workbook(out / 'kazan-2026.xlsx', data_only=True, read_only=True)
assert list(wb['2026'].values) == list(scrubbed['2026'].values)
source_sha = hashlib.sha256(args.source.read_bytes()).hexdigest()
corpus_sha = hashlib.sha256((out / 'kazan-2026.xlsx').read_bytes()).hexdigest()
(out / 'README.md').write_text(f'''# Kazan 2026 source regression corpus

This is a privacy-scrubbed copy of the owner-supplied `vremena_namazov_RT_2026_0.xlsx`.
No official approval is inferred. Only document metadata, private paths, printer settings
and unused relationships were removed. Cell values, formula caches and styles are preserved.

- Original SHA-256: `{source_sha}`
- Scrubbed corpus SHA-256: `{corpus_sha}`
- Oracle: `kazan-2026.tsv`, independently extracted with openpyxl {openpyxl.__version__}.
- 365 dates × 8 times, no recalculation, normalization or minute adjustments.
- `scripts/audit-source-xlsx.py` reproduces the comparison against a local original.
- Tests assert every converted minute against this oracle, including Isha 18:50.
- Workbook formulas are NOT executable app logic; only saved results are read.
''')
print('PASS: original vs scrubbed cells identical; 365 x 8 independent source values extracted')
print('Isha18:50 days:', sum(r[-1] == '18:50' for r in records))
