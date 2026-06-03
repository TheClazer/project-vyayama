"""Render docs/bible.md → docs/bible.pdf with a bespoke, minimalist design.

Pipeline: markdown-it-py (md → HTML) → custom HTML/CSS template → headless Chrome (--print-to-pdf).
No WeasyPrint dependency (Windows-friendly). Run:  python tools/build_bible_pdf.py
"""
from __future__ import annotations
import os, sys, subprocess, tempfile, shutil
from pathlib import Path
from markdown_it import MarkdownIt

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "docs" / "bible.md"
OUT = ROOT / "docs" / "(bible)project-vyayama.pdf"   # the canonical PDF name (regenerations overwrite it)

CHROME_CANDIDATES = [
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
    shutil.which("google-chrome") or "",
    shutil.which("chromium") or "",
]

CSS = r"""
:root{
  --ink:#1c1f26; --body:#262a33; --muted:#6b7280; --hair:#e6e9ef;
  --accent:#0e8c73; --accent-deep:#0a6e5a; --accent-bright:#2fd9b6;
  --code-bg:#f6f7f9; --cover:#14161c; --zebra:#fafbfc; --quote:#f1faf7;
}
@page { size: A4; margin: 18mm 17mm 16mm 17mm; }
@page :first { margin: 0; }
*{ box-sizing:border-box; }
html{ -webkit-print-color-adjust:exact; print-color-adjust:exact; }
body{
  font-family:'Segoe UI','Inter','Helvetica Neue',Arial,sans-serif;
  color:var(--body); font-size:10.1pt; line-height:1.55; margin:0; -webkit-font-smoothing:antialiased;
}

/* ---------- cover ---------- */
.cover{
  background:var(--cover); color:#eef1f5; height:100vh; padding:38mm 26mm 30mm;
  page-break-after:always; display:flex; flex-direction:column; overflow:hidden; position:relative;
}
.cover .eyebrow{ color:var(--accent-bright); letter-spacing:.30em; font-size:8.5pt; font-weight:600; text-transform:uppercase; }
.cover .deva{ font-family:'Nirmala UI','Segoe UI',sans-serif; font-size:62pt; color:var(--accent-bright); font-weight:400; line-height:1; margin:16mm 0 0; opacity:.95; }
.cover .title{ font-size:54pt; font-weight:800; letter-spacing:-.025em; margin:3mm 0 0; color:#fff; }
.cover .subtitle{ font-size:13.5pt; color:#aeb6c2; font-weight:400; margin-top:3mm; letter-spacing:.01em; }
.cover .spacer{ flex:1; }
.cover .rule{ height:2px; width:58mm; background:var(--accent-bright); margin:0 0 7mm; }
.cover .doctype{ font-size:15pt; font-weight:700; letter-spacing:.05em; color:#fff; }
.cover .meta{ color:#99a2b0; font-size:9pt; line-height:1.85; margin-top:5mm; }
.cover .meta b{ color:#e2e7ec; font-weight:600; }
.cover .tag{ position:absolute; top:38mm; right:26mm; color:#7d8694; font-size:8.5pt; letter-spacing:.02em; }

/* ---------- content ---------- */
.content{ padding-top:2mm; }
h2{ font-size:15.5pt; font-weight:800; color:var(--ink); letter-spacing:-.01em;
    margin:20pt 0 7pt; padding-bottom:4pt; border-bottom:1.6px solid var(--accent); page-break-after:avoid; }
h3{ font-size:11.8pt; font-weight:700; color:var(--accent-deep); margin:13pt 0 4pt; page-break-after:avoid; }
h4{ font-size:10.4pt; font-weight:700; color:var(--ink); margin:10pt 0 3pt; }
p{ margin:5pt 0; }
strong{ color:var(--ink); font-weight:700; }
a{ color:var(--accent-deep); text-decoration:none; }
ul,ol{ margin:5pt 0; padding-left:17pt; }
li{ margin:2.5pt 0; }
hr{ border:none; border-top:1px solid var(--hair); margin:15pt 0; }

table{ width:100%; border-collapse:collapse; margin:8pt 0; font-size:8.5pt; page-break-inside:avoid; }
th{ text-align:left; color:var(--accent-deep); font-weight:700; border-bottom:1.6px solid var(--accent);
    padding:5px 8px; vertical-align:bottom; }
td{ padding:4.5px 8px; border-bottom:1px solid var(--hair); vertical-align:top; }
tr:nth-child(even) td{ background:var(--zebra); }

code{ font-family:'Cascadia Code','Consolas','SF Mono',monospace; font-size:8.7pt;
      background:#eef1f4; color:var(--accent-deep); padding:1px 4px; border-radius:3px; }
pre{ background:var(--code-bg); border:1px solid var(--hair); border-left:3px solid var(--accent);
     border-radius:6px; padding:9px 12px; margin:8pt 0; overflow:hidden;
     white-space:pre; font-size:7.6pt; line-height:1.34; color:#2a2f3a; page-break-inside:avoid; }
pre code{ background:none; color:inherit; font-size:inherit; padding:0; border-radius:0; }

blockquote{ border-left:3px solid var(--accent); background:var(--quote); padding:7px 14px;
    margin:9pt 0; color:#1f5045; border-radius:0 6px 6px 0; }
blockquote p{ margin:3pt 0; }
blockquote strong{ color:var(--accent-deep); }
"""

COVER = """
<section class="cover">
  <div class="tag">Skeletons in → coaching out.</div>
  <div class="eyebrow">Hack4SoC 3.0 &nbsp;·&nbsp; Qualcomm Edge AI Track</div>
  <div class="deva">व्यायाम</div>
  <div class="title">Vyāyāma</div>
  <div class="subtitle">On-device AI Form Coach for Snapdragon</div>
  <div class="spacer"></div>
  <div class="rule"></div>
  <div class="doctype">PROJECT BIBLE</div>
  <div class="meta">
    <b>Problem statement</b> &nbsp; FitSense — Real-Time Exercise Detection &amp; Recognition<br>
    <b>Target hardware</b> &nbsp; Qualcomm QIDK · RB3 Gen 2 · QCS6490<br>
    <b>Version</b> &nbsp; 1.0 &nbsp;·&nbsp; <b>Self-audit</b> &nbsp; 94.2 / 100 over 3 iterations
  </div>
</section>
"""

def find_chrome() -> str:
    for c in CHROME_CANDIDATES:
        if c and Path(c).exists():
            return c
    sys.exit("Chrome/Edge not found — install Chrome or edit CHROME_CANDIDATES.")

def main() -> None:
    raw = SRC.read_text(encoding="utf-8")
    # Drop the leading title block (title + tagline + version) up to the first '---'; the cover replaces it.
    parts = raw.split("\n---\n", 1)
    body_md = parts[1] if len(parts) > 1 else raw

    md = MarkdownIt("commonmark", {"html": True}).enable("table").enable("strikethrough")
    body_html = md.render(body_md)

    html = f"""<!doctype html><html lang="en"><head><meta charset="utf-8">
<title>Vyāyāma — Project Bible</title><style>{CSS}</style></head>
<body>{COVER}<main class="content">{body_html}</main></body></html>"""

    tmp = Path(tempfile.gettempdir()) / "vyayama_bible.html"
    tmp.write_text(html, encoding="utf-8")
    file_url = "file:///" + str(tmp).replace("\\", "/")

    chrome = find_chrome()
    OUT.parent.mkdir(parents=True, exist_ok=True)
    cmd = [chrome, "--headless=new", "--disable-gpu", "--no-pdf-header-footer",
           "--no-sandbox", f"--print-to-pdf={OUT}", file_url]
    print("chrome:", chrome)
    r = subprocess.run(cmd, capture_output=True, text=True, timeout=180)
    if not OUT.exists():
        # older Chrome flag fallback
        cmd[3] = "--print-to-pdf-no-header"
        subprocess.run(cmd, capture_output=True, text=True, timeout=180)
    if OUT.exists():
        print(f"OK  wrote {OUT}  ({OUT.stat().st_size//1024} KB)")
    else:
        print("FAILED\nstdout:", r.stdout, "\nstderr:", r.stderr)

if __name__ == "__main__":
    main()
