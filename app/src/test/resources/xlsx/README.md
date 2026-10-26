# Kazan 2026 source regression corpus

This is a privacy-scrubbed copy of the owner-supplied `vremena_namazov_RT_2026_0.xlsx`.
No official approval is inferred. Only document metadata, private paths, printer settings
and unused relationships were removed. Cell values, formula caches and styles are preserved.

- Original SHA-256: `36abdddef346cb3f47317a2651d215a4af03615a68803aa5800b64c0d653b83d`
- Scrubbed corpus SHA-256: `7f57f7251508666d818c159a66c37b279917441f1d8a44876629d46e9006e192`
- Oracle: `kazan-2026.tsv`, independently extracted with openpyxl 3.1.5.
- 365 dates × 8 times, no recalculation, normalization or minute adjustments.
- `scripts/audit-source-xlsx.py` reproduces the comparison against a local original.
- Tests assert every converted minute against this oracle, including Isha 18:50.
- Workbook formulas are NOT executable app logic; only saved results are read.
