# -*- coding: utf-8 -*-
"""Vyāyāma — Hack4SoC 3.0 deck (6 slides). Hand-built light theme, no Office-template look."""
from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.enum.shapes import MSO_SHAPE
from pptx.oxml.ns import qn

PAPER   = RGBColor(0xF7, 0xF8, 0xF4)
INK     = RGBColor(0x15, 0x1A, 0x17)
INK_SOFT= RGBColor(0x37, 0x3F, 0x39)
MUTED   = RGBColor(0x69, 0x73, 0x6C)
VOLT    = RGBColor(0xC8, 0xFF, 0x3C)
VOLT_DK = RGBColor(0x2C, 0x57, 0x21)
HAIR    = RGBColor(0xE2, 0xE6, 0xDC)
WHITE   = RGBColor(0xFF, 0xFF, 0xFF)
WASH    = RGBColor(0xEF, 0xF7, 0xDA)
WASH2   = RGBColor(0xF1, 0xF7, 0xE0)
PAPER2  = RGBColor(0xEF, 0xF1, 0xEA)

HEAD = "Bahnschrift SemiBold"
BODY = "Segoe UI"
MONO = "Consolas"
DEV  = "Nirmala UI"
NSL  = 5  # slide count

prs = Presentation()
prs.slide_width  = Inches(13.333)
prs.slide_height = Inches(7.5)
BLANK = prs.slide_layouts[6]

def slide():
    s = prs.slides.add_slide(BLANK)
    s.background.fill.solid(); s.background.fill.fore_color.rgb = PAPER
    return s

def _spc(run, pts): run.font._rPr.set('spc', str(int(pts*100)))

def tb(s, x, y, w, h, anchor='top'):
    box = s.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h)); tf = box.text_frame
    tf.word_wrap = True
    tf.margin_left = tf.margin_right = tf.margin_top = tf.margin_bottom = 0
    tf.vertical_anchor = {'top':MSO_ANCHOR.TOP,'mid':MSO_ANCHOR.MIDDLE,'bot':MSO_ANCHOR.BOTTOM}[anchor]
    return tf

def run(p, text, size=13, color=INK, font=BODY, bold=False, italic=False, spc=None):
    r = p.add_run(); r.text = text; f = r.font
    f.size = Pt(size); f.bold = bold; f.italic = italic; f.name = font; f.color.rgb = color
    if spc is not None: _spc(r, spc)
    return r

def para(tf, first=False, align=PP_ALIGN.LEFT, before=0.0, after=0.0, line=1.0):
    p = tf.paragraphs[0] if first else tf.add_paragraph()
    p.alignment = align; p.space_before = Pt(before); p.space_after = Pt(after); p.line_spacing = line
    return p

def rect(s, x, y, w, h, fill=None, line=None, lw=1.0, rounded=True, radius=0.12, dash=None):
    shp = s.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE if rounded else MSO_SHAPE.RECTANGLE,
                             Inches(x), Inches(y), Inches(w), Inches(h))
    if fill is None: shp.fill.background()
    else: shp.fill.solid(); shp.fill.fore_color.rgb = fill
    if line is None: shp.line.fill.background()
    else: shp.line.color.rgb = line; shp.line.width = Pt(lw)
    try: shp.shadow.inherit = False
    except Exception: pass
    if rounded:
        try: shp.adjustments[0] = radius
        except Exception: pass
    if dash and line is not None:
        ln = shp.line._get_or_add_ln(); ln.append(ln.makeelement(qn('a:prstDash'), {'val': dash}))
    return shp

def kicker(s, label):
    rect(s, 0.8, 0.62, 0.17, 0.17, fill=VOLT, radius=0.3)
    run(para(tb(s, 1.06, 0.55, 11, 0.32, 'mid'), True), label, size=11, color=MUTED, font=MONO, spc=2.2)

def title(s, text, y=1.12, size=33):
    run(para(tb(s, 0.8, y, 12.2, 1.0), True), text, size=size, color=INK, font=HEAD, bold=True)

def colhead(s, x, y, text, w=4.0):
    run(para(tb(s, x, y, w, 0.3), True), text, size=11.5, color=VOLT_DK, font=MONO, bold=True, spc=1.6)
    rect(s, x+0.005, y+0.32, 0.42, 0.035, fill=VOLT, rounded=False)

def item(s, x, y, w, head, body=None, size=12.5, hcolor=INK):
    rect(s, x, y+0.055, 0.15, 0.15, fill=VOLT, radius=0.3)
    p = para(tb(s, x+0.30, y-0.04, w-0.30, 0.95), True, line=1.04)
    run(p, head, size=size, color=hcolor, font=BODY, bold=True)
    if body: run(p, "  "+body, size=size, color=MUTED, font=BODY)

def footer(s, n):
    rect(s, 0.8, 6.92, 11.73, 0.012, fill=HAIR, rounded=False)
    run(para(tb(s, 0.8, 7.0, 10.5, 0.34, 'mid'), True),
        "Vyāyāma  ·  on-device AI exercise coach  ·  Snapdragon Hexagon NPU  ·  100% offline", size=9, color=MUTED)
    run(para(tb(s, 11.8, 7.0, 0.73, 0.34, 'mid'), True, align=PP_ALIGN.RIGHT),
        "%02d / %02d" % (n, NSL), size=9, color=MUTED, font=MONO)

def factline(s, x, y, w, text, label="DETAIL"):
    rect(s, x, y, w, 0.46, fill=WASH, radius=0.22)
    p = para(tb(s, x+0.16, y, w-0.3, 0.46, 'mid'), True, line=1.0)
    run(p, label+"  ", size=9.5, color=VOLT_DK, font=MONO, bold=True)
    run(p, text, size=10.5, color=INK_SOFT)

# ============================================================ SLIDE 1
s = slide()
run(para(tb(s, 6.3, 0.2, 7.2, 3.2), True, align=PP_ALIGN.RIGHT), "व्यायाम", size=150, color=PAPER2, font=DEV)
kicker(s, "HACK4SOC 3.0  /  ON-DEVICE · EDGE AI (QUALCOMM)")
run(para(tb(s, 0.78, 1.15, 9.5, 1.3), True), "Vyāyāma", size=60, color=INK, font=HEAD, bold=True)
run(para(tb(s, 0.82, 2.35, 10.5, 0.6), True), "An AI exercise coach that runs entirely on your phone.", size=18, color=MUTED)
p = para(tb(s, 0.82, 3.18, 8.6, 1.1), True, line=1.18)
run(p, "Good form is the line between progress and injury — but live feedback usually means a "
       "personal trainer, a wearable, or a cloud vision API. Train at home and you get none of it; "
       "most “AI fitness” apps stream your camera to someone else’s server.", size=13.5, color=INK_SOFT)
rect(s, 0.82, 4.42, 0.07, 1.02, fill=VOLT, rounded=False)
p = para(tb(s, 1.04, 4.40, 8.5, 1.1, 'mid'), True, line=1.16)
run(p, "Vyāyāma puts the coach on-device — ", size=14, color=INK, bold=True)
run(p, "real-time reps + form on the Snapdragon NPU. No trainer, no wearable, no cloud. "
       "Your camera feed never leaves the phone.", size=14, color=INK_SOFT)
rect(s, 0.82, 5.78, 5.3, 0.44, fill=VOLT, radius=0.5)
run(para(tb(s, 0.82, 5.78, 5.3, 0.44, 'mid'), True, align=PP_ALIGN.CENTER),
    "Hack4SoC 3.0  ·  On-Device / Edge AI  ·  Qualcomm", size=11.5, color=INK, font=MONO, bold=True)
tf = tb(s, 0.82, 6.34, 11.6, 0.55)
p = para(tf, True, line=1.05)
run(p, "TEAM VYĀYĀMA   ", size=10.5, color=VOLT_DK, font=MONO, bold=True, spc=1.2)
run(p, "Rayyan Shaikh (lead)  ·  Ashitha Patil  ·  Vaibhav Rathod", size=12.5, color=INK, bold=True)
run(para(tf, line=1.05, before=2), "R.V. College of Engineering (RVCE), Bengaluru", size=11.5, color=MUTED)
run(para(tb(s, 6.5, 5.82, 6.0, 0.4, 'mid'), True, align=PP_ALIGN.RIGHT),
    "व्यायाम  (vyāyāma) — Sanskrit for “exercise.”   ·   0 bytes leave the device.", size=10, color=MUTED)
footer(s, 1)

# ============================================================ SLIDE 2
s = slide()
kicker(s, "02  /  TECH STACK + SOLUTION")
title(s, "On-device pose intelligence, end to end")
colhead(s, 0.82, 1.95, "THE STACK")
stack = [
    ("Android · Java · Camera2", "YUV preview, zero-copy frames"),
    ("Qualcomm SNPE → Hexagon NPU", "INT8 .dlc models, GPU/CPU fallback"),
    ("YOLO-NAS + HRNet", "person box → 17 COCO keypoints"),
    ("VyāyamaCoach engine", "pure-Java, zero-alloc per frame"),
    ("Offline storage", "SharedPreferences · AlarmManager reminders"),
    ("No backend · no network", "the app requests no INTERNET permission"),
]
y = 2.5
for h, b in stack: item(s, 0.95, y, 5.4, h, b); y += 0.52
colhead(s, 7.0, 1.95, "WHAT IT DOES")
does = [
    ("Sees your skeleton in real time", "recognizes the move (7 exercises)"),
    ("Counts reps + scores form 0–100", "live cues: “lower… now drive up!”"),
    ("Manual mode", "pin one exercise so it can’t misread"),
    ("Offline profiles", "personal bests, streaks, daily reminders"),
    ("Coach Vision overlay", "shows exactly what the engine senses"),
]
y = 2.5
for h, b in does: item(s, 7.13, y, 5.3, h, b); y += 0.52
factline(s, 7.0, 5.18, 5.45, "Zero heap allocation per frame — ring buffers reused, so the GC never stutters mid-rep.")
factline(s, 7.0, 5.74, 5.45, "One-Euro filter smooths jitter without adding lag; NPU by default, fallback in one tap.")
factline(s, 0.82, 6.4, 11.63,
         "7 exercises auto-recognized · live NPU pipeline (GPU/CPU fallback) · manual mode · offline profiles, streaks & reminders · 105 self-tests green.",
         "BUILT TODAY")
footer(s, 2)

# ============================================================ SLIDE 3  (detailed architecture)
s = slide()
kicker(s, "03  /  ARCHITECTURE — HOW IT WORKS")
title(s, "Camera to coaching — every stage on-device", size=31)

# on-device boundary + tab
rect(s, 0.5, 1.92, 12.33, 4.34, fill=None, line=HAIR, lw=1.5, radius=0.04, dash='dash')
tabw = 3.55
t = rect(s, 0.78, 1.74, tabw, 0.36, fill=INK, radius=0.5)
tf = t.text_frame; tf.word_wrap=False; tf.vertical_anchor=MSO_ANCHOR.MIDDLE
run(para(tf, True, align=PP_ALIGN.CENTER), "ON-DEVICE · NO INTERNET PERMISSION", size=9, color=VOLT, font=MONO, bold=True)

CX0 = 3.0          # chip band start
def chip(x, y, w, h, text, accent=False):
    rect(s, x, y, w, h, fill=(VOLT if accent else WHITE), line=(None if accent else HAIR), lw=1.1, radius=0.18)
    tfc = tb(s, x+0.05, y, w-0.1, h, 'mid')
    run(para(tfc, True, align=PP_ALIGN.CENTER, line=0.95), text, size=10, color=INK,
        font=(BODY if accent else BODY), bold=accent)

def lane(y, h, label, sub, chips, accent=False):
    if accent:
        rect(s, 0.62, y-0.04, 12.1, h+0.08, fill=WASH2, radius=0.10)
    # left label
    lt = tb(s, 0.78, y, 2.05, h, 'mid')
    run(para(lt, True, line=0.98), label, size=10.5, color=(VOLT_DK if accent else INK), font=MONO, bold=True)
    run(para(lt, line=0.98, before=1), sub, size=8, color=MUTED, font=MONO)
    # chips
    x = CX0
    ch = h - 0.16
    cy = y + 0.08
    for txt in chips:
        w = 0.30 + len(txt)*0.073
        chip(x, cy, w, ch, txt, accent=accent)
        x += w + 0.18

LY = 2.18; LH = 0.62; GAP = 0.235
lanes = [
    ("CAPTURE", "Camera2", ["YUV · 30 fps", "active-athlete lock", "upright rotate"], False),
    ("NPU INFERENCE", "SNPE · Hexagon", ["YOLO-NAS · person box", "HRNet · 17 keypoints", "INT8 .dlc"], True),
    ("SIGNAL → FEATURES", "robust prep", ["One-Euro smoothing", "teleport reject + gap-hold", "13 biomech features"], False),
    ("COACH ENGINE", "pure-Java · 0-alloc", ["sticky recognizer", "rep FSM ±partial/fast", "adaptive ROM", "form 0–100 + cue"], True),
    ("EXPERIENCE", "the app", ["Live HUD", "Coach Vision", "Profiles · PB · streak", "Reminders → notifications"], False),
]
ys = []
yy = LY
for (lab, sub, chips, acc) in lanes:
    lane(yy, LH, lab, sub, chips, acc); ys.append(yy); yy += LH + GAP
# down arrows between lanes (centered in chip band)
for i in range(len(ys)-1):
    ax = 6.7
    run(para(tb(s, ax, ys[i]+LH-0.02, 0.4, GAP+0.04, 'mid'), True, align=PP_ALIGN.CENTER), "↓", size=15, color=MUTED)
# feedback note
run(para(tb(s, 8.9, ys[3]+LH+0.02, 3.7, 0.22, 'mid'), True, align=PP_ALIGN.RIGHT),
    "↻  profiles + calibration personalize each session", size=8.5, color=MUTED, font=MONO)

factline(s, 0.5, 6.40, 12.33,
         "INT8 on the Hexagon NPU · zero heap-alloc per frame · GPU/CPU fallback in one tap · the engine is validated by a 105-assertion offline test harness.",
         "EFFICIENCY")
footer(s, 3)

# ============================================================ SLIDE 4  (social relevance + impact)
s = slide()
kicker(s, "04  /  SOCIAL RELEVANCE + IMPACT")
title(s, "A coach for everyone — private by design")
colhead(s, 0.82, 1.9, "SOCIAL RELEVANCE")
rel = [
    ("Access & equity", "trainer-grade form for the millions with no gym, coach or PT"),
    ("Privacy & dignity", "camera never leaves the phone — safe for women, minors, home & clinical use"),
    ("Rehab & ageing", "guided reps for physiotherapy and elderly home workouts between clinic visits"),
    ("Reach", "fully offline — tier-2/3, rural, low-connectivity; budget phone to flagship"),
]
y = 2.45
for h, b in rel: item(s, 0.95, y, 5.45, h, b, size=12.5); y += 0.78
colhead(s, 7.0, 1.9, "POTENTIAL IMPACT")
imp = [
    ("Fewer injuries, real adherence", "live form turns risky reps into safe ones, so people keep going"),
    ("Remote physiotherapy", "track recovery at home and ease the load on clinics"),
    ("Privacy-first health AI", "a template: genuinely useful AI that collects zero data"),
    ("Scales at ~zero cost", "no servers, no cloud bill, no data-centre energy — millions, on-device"),
]
y = 2.45
for h, b in imp: item(s, 7.13, y, 5.35, h, b, size=12.5); y += 0.78
factline(s, 0.82, 6.2, 11.63,
         "On-device AI makes form-correct exercise a free, private, offline utility — not a subscription that watches you.",
         "THE BIG PICTURE")
footer(s, 4)

# ============================================================ SLIDE 5  (differentiators)
s = slide()
kicker(s, "05  /  WHAT SETS IT APART")
title(s, "Beyond a rep counter — the details that add up", size=31)

def card(x, y, w, h, header, lines):
    rect(s, x, y, w, h, fill=WHITE, line=HAIR, lw=1.2, radius=0.06)
    run(para(tb(s, x+0.22, y+0.16, w-0.4, 0.3), True), header, size=10.5, color=VOLT_DK, font=MONO, bold=True, spc=1.2)
    rect(s, x+0.225, y+0.46, 0.36, 0.03, fill=VOLT, rounded=False)
    ty = y + 0.6
    for ln in lines:
        rect(s, x+0.24, ty+0.05, 0.10, 0.10, fill=VOLT, radius=0.3)
        run(para(tb(s, x+0.46, ty-0.02, w-0.66, 0.5), True, line=0.98), ln, size=10.3, color=INK_SOFT)
        ty += 0.345

cw, chh = 3.86, 2.0
xs = [0.82, 4.78, 8.74]; ys = [1.88, 3.96]
data = [
    ("ROBUST RECOGNITION", [
        "Sticky self-correcting lock — flicker can’t switch it",
        "Positive-evidence gates: a sit-up is never a curl",
        "Catches rep #1 even if recognized mid-move",
        "Manual mode: pin one move, zero misreads"]),
    ("ADAPTIVE COUNTING", [
        "Two-threshold FSM rejects jitter + partials",
        "Learns your range — low-ROM reps count",
        "Hip-drop signal counts foreshortened squats",
        "Fast reps at low frame-rate still register"]),
    ("REAL-TIME COACHING", [
        "Per-rep form score 0–100",
        "One cue, worst issue first (depth, sag, swing…)",
        "Live phase cues: “lower… now drive up!”",
        "PB rewards · streaks · daily reminders"]),
    ("UX TOUCHES", [
        "Animated splash: running athlete + wordmark",
        "Volt HUD: big counter, colour-coded cues",
        "Stat strip (streak / today’s best) over camera",
        "Tap to rotate · engine switch (NPU/GPU/CPU)"]),
    ("EFFICIENT & PRIVATE", [
        "Zero heap allocation per frame — no GC jank",
        "INT8 on the Hexagon NPU — battery-light",
        "GPU / CPU fallback in one tap",
        "No INTERNET permission — provably offline"]),
    ("ENGINEERING RIGOR", [
        "105-assertion pure-Java harness (offline, ms)",
        "Every module mocked — runs in any subset",
        "Hardened via multi-agent adversarial review",
        "Coach Vision = built-in transparency"]),
]
for i, (hdr, lines) in enumerate(data):
    card(xs[i % 3], ys[i // 3], cw, chh, hdr, lines)
# closing band — the mic-drop + the roadmap (from the old status slide, folded in)
rect(s, 0.82, 6.0, 11.63, 0.78, fill=WASH, radius=0.16)
rect(s, 0.82, 6.0, 0.07, 0.78, fill=VOLT, rounded=False)
tfc = tb(s, 1.06, 6.06, 11.3, 0.68, 'mid')
p = para(tfc, True, line=1.04)
run(p, "Your phone already has the hardware — Vyāyāma turns it into a coach: private, offline, real-time.",
    size=13, color=INK, font=HEAD, bold=True)
p2 = para(tfc, before=3, line=1.0)
run(p2, "NEXT   ", size=9.5, color=VOLT_DK, font=MONO, bold=True)
run(p2, "more movements + rehab · voice coaching (eyes-off) · on-device personalization · optional HR fusion — still offline",
    size=10, color=INK_SOFT)
footer(s, 5)

out = r"C:\Users\Rayyan Shaikh\Desktop\Vyayama_Hack4SoC.pptx"
prs.save(out)
print("saved:", out)
