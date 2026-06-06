# -*- coding: utf-8 -*-
"""Vyāyāma — strictly one-page A4 project report. Light Volt theme. Exports via PowerPoint COM to PDF."""
from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.enum.shapes import MSO_SHAPE
from pptx.oxml.ns import qn

PAPER=RGBColor(0xF7,0xF8,0xF4); INK=RGBColor(0x15,0x1A,0x17); INK_SOFT=RGBColor(0x33,0x3B,0x35)
MUTED=RGBColor(0x66,0x70,0x69); VOLT=RGBColor(0xC8,0xFF,0x3C); VOLT_DK=RGBColor(0x2C,0x57,0x21)
HAIR=RGBColor(0xDD,0xE2,0xD6); WHITE=RGBColor(0xFF,0xFF,0xFF); WASH=RGBColor(0xEF,0xF7,0xDA)
HEAD="Bahnschrift SemiBold"; BODY="Segoe UI"; MONO="Consolas"; DEV="Nirmala UI"

prs=Presentation(); prs.slide_width=Inches(8.27); prs.slide_height=Inches(11.69)
s=prs.slides.add_slide(prs.slide_layouts[6])
s.background.fill.solid(); s.background.fill.fore_color.rgb=PAPER

def tb(x,y,w,h,anchor='top'):
    b=s.shapes.add_textbox(Inches(x),Inches(y),Inches(w),Inches(h)); tf=b.text_frame
    tf.word_wrap=True; tf.margin_left=tf.margin_right=tf.margin_top=tf.margin_bottom=0
    tf.vertical_anchor={'top':MSO_ANCHOR.TOP,'mid':MSO_ANCHOR.MIDDLE,'bot':MSO_ANCHOR.BOTTOM}[anchor]
    return tf
def spc(r,p): r.font._rPr.set('spc',str(int(p*100)))
def run(p,t,size=9.5,color=INK,font=BODY,bold=False,sp=None,italic=False):
    r=p.add_run(); r.text=t; f=r.font; f.size=Pt(size); f.bold=bold; f.italic=italic; f.name=font; f.color.rgb=color
    if sp is not None: spc(r,sp)
    return r
def para(tf,first=False,align=PP_ALIGN.LEFT,before=0,after=0,line=1.0):
    p=tf.paragraphs[0] if first else tf.add_paragraph()
    p.alignment=align; p.space_before=Pt(before); p.space_after=Pt(after); p.line_spacing=line; return p
def rect(x,y,w,h,fill=None,line=None,lw=1.0,rounded=True,radius=0.12,dash=None):
    shp=s.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE if rounded else MSO_SHAPE.RECTANGLE,
                           Inches(x),Inches(y),Inches(w),Inches(h))
    if fill is None: shp.fill.background()
    else: shp.fill.solid(); shp.fill.fore_color.rgb=fill
    if line is None: shp.line.fill.background()
    else: shp.line.color.rgb=line; shp.line.width=Pt(lw)
    try: shp.shadow.inherit=False
    except Exception: pass
    if rounded:
        try: shp.adjustments[0]=radius
        except Exception: pass
    if dash and line is not None:
        ln=shp.line._get_or_add_ln(); ln.append(ln.makeelement(qn('a:prstDash'),{'val':dash}))
    return shp
def kicker(x,y,t):
    rect(x,y+0.02,0.13,0.13,fill=VOLT,radius=0.3)
    run(para(tb(x+0.22,y-0.03,7.0,0.26,'mid'),True),t,size=10,color=VOLT_DK,font=MONO,bold=True,sp=1.4)
def bullet(x,y,w,head,rest,size=9.3):
    rect(x,y+0.05,0.10,0.10,fill=VOLT,radius=0.3)
    p=para(tb(x+0.22,y-0.02,w-0.22,0.5),True,line=1.02)
    run(p,head,size=size,color=INK,bold=True);
    if rest: run(p,"  "+rest,size=size,color=INK_SOFT)

LX=0.5; CW=7.27; RX=LX+CW

# ---------------- HEADER ----------------
run(para(tb(LX,0.40,5.0,0.7),True),"Vyāyāma",size=31,color=INK,font=HEAD,bold=True)
# track pill (right)
pillw=4.2
rect(RX-pillw,0.50,pillw,0.34,fill=VOLT,radius=0.5)
run(para(tb(RX-pillw,0.50,pillw,0.34,'mid'),True,align=PP_ALIGN.CENTER),
    "Hack4SoC 3.0  ·  On-Device / Edge AI  ·  Qualcomm",size=9.5,color=INK,font=MONO,bold=True)
run(para(tb(LX,1.06,7.27,0.3),True),"An on-device AI exercise coach — private, offline, real-time.",size=11.5,color=MUTED)
tf=tb(LX,1.40,7.27,0.3); p=para(tf,True,line=1.0)
run(p,"TEAM VYĀYĀMA   ",size=9.5,color=VOLT_DK,font=MONO,bold=True,sp=1.0)
run(p,"Rayyan Shaikh (lead)  ·  Ashitha Patil  ·  Vaibhav Rathod      ",size=10,color=INK,bold=True)
run(p,"R.V. College of Engineering (RVCE), Bengaluru",size=10,color=MUTED)
rect(LX,1.74,CW,0.013,fill=HAIR,rounded=False)

# ---------------- PROBLEM ----------------
kicker(LX,1.92,"PROBLEM STATEMENT")
p=para(tb(LX,2.18,CW,0.9),True,line=1.12)
run(p,"Form-correct exercise needs real-time feedback — which normally means a personal trainer, a wearable, or a "
      "cloud vision API: expensive, and privacy-invasive (your camera streams to someone else's server). Home, "
      "rural and rehab users get neither safe form correction nor reliable rep counting. Our goal: trainer-grade "
      "coaching that runs ",size=9.6,color=INK_SOFT)
run(p,"entirely on the phone",size=9.6,color=INK,bold=True)
run(p,", with the camera feed never leaving the device.",size=9.6,color=INK_SOFT)

# ---------------- TECH + SOLUTION ----------------
kicker(LX,3.18,"TECH STACK + SOLUTION DEVELOPED")
p=para(tb(LX,3.44,CW,0.5),True,line=1.1)
run(p,"Stack:  ",size=9.4,color=VOLT_DK,font=MONO,bold=True)
run(p,"Android · Java · Camera2 · OpenCV   ·   Qualcomm SNPE → Hexagon NPU (INT8 .dlc)   ·   YOLO-NAS + HRNet "
      "(17 keypoints)   ·   pure-Java analysis engine   ·   on-device TextToSpeech   ·   SharedPreferences + "
      "AlarmManager   ·   no backend, no network, no INTERNET permission.",size=9.4,color=INK_SOFT)
p=para(tb(LX,4.06,CW,0.55),True,line=1.1)
run(p,"Solution:  ",size=9.4,color=VOLT_DK,font=MONO,bold=True)
run(p,"live skeleton tracking → auto-recognises 7 exercises (squat, push-up, curl, shoulder-press, sit-up, "
      "jumping-jack, plank) → counts reps + scores form 0–100 + live cues → an ",size=9.4,color=INK_SOFT)
run(p,"offline voice coach",size=9.4,color=INK,bold=True)
run(p," that speaks pattern-based corrections every few reps → offline profiles: personal bests, streaks, reminders.",
    size=9.4,color=INK_SOFT)

run(para(tb(LX,4.74,CW,0.24),True),"SOLVING THE HARD PARTS",size=9.5,color=VOLT_DK,font=MONO,bold=True,sp=1.2)
hp=[
 ("Recognition under flicker & noise —","a sticky, self-correcting lock + One-Euro keypoint smoothing + teleport/dropout rejection: reps survive jitter, and a wrong first guess corrects itself."),
 ("Counting that fits real bodies —","a two-threshold rep state-machine + adaptive per-user range calibration + peak/valley completion, so partial-range and no-lockout reps still count — and fast reps at low frame-rate are never dropped."),
 ("Camera-angle robustness —","a viewpoint-stable hip-drop signal counts foreshortened, front-on squats that a knee angle alone misses."),
 ("Zero misreads —","positive-evidence gates: a sit-up's trunk-fold can never be mistaken for a bicep curl or a shoulder press."),
 ("Real-time on a phone —","zero heap allocation per frame (pre-allocated ring buffers) so the GC never stutters mid-rep; INT8 on the NPU keeps it fast and battery-light, with a one-tap GPU/CPU fallback."),
 ("Proven, not hand-waved —","the entire engine is pure-Java and validated by a 128-assertion offline test harness that runs in milliseconds."),
]
y=5.02
for h,r in hp:
    bullet(LX,y,CW,h,r,size=9.2); y+=0.345

# ---------------- BLOCK DIAGRAM ----------------
kicker(LX,7.18,"HOW IT WORKS  —  EVERY STAGE ON-DEVICE")
# boundary + tab
rect(LX,7.50,CW,1.18,fill=None,line=HAIR,lw=1.3,radius=0.05,dash='dash')
tab=rect(LX+0.22,7.40,2.7,0.28,fill=INK,radius=0.5)
tf=tab.text_frame; tf.word_wrap=False; tf.vertical_anchor=MSO_ANCHOR.MIDDLE
run(para(tf,True,align=PP_ALIGN.CENTER),"ON-DEVICE · NO INTERNET",size=8,color=VOLT,font=MONO,bold=True)
def node(x,y,w,h,t,sub,accent=False):
    rect(x,y,w,h,fill=(VOLT if accent else WHITE),line=(None if accent else HAIR),lw=1.1,radius=0.16)
    tf=tb(x+0.04,y,w-0.08,h,'mid')
    run(para(tf,True,align=PP_ALIGN.CENTER,line=0.95),t,size=9,color=INK,font=HEAD,bold=True)
    run(para(tf,align=PP_ALIGN.CENTER,line=0.92,before=1),sub,size=7,color=(INK_SOFT if accent else MUTED),font=MONO)
def arrow(x,y,w):
    run(para(tb(x,y,w,0.8,'mid'),True,align=PP_ALIGN.CENTER),"→",size=15,color=MUTED,font=BODY)
nw=1.30; nh=0.8; ny=7.74; gap=0.155
xs=[LX+0.16+i*(nw+gap) for i in range(5)]
nodes=[("Camera","YUV · 30fps",False),("Hexagon NPU","YOLO-NAS→HRNet",True),
       ("Smoothing","One-Euro · 13 feat",False),("Coach engine","count · form · cue",True),
       ("Voice · HUD","profiles · PB",False)]
for x,(t,sub,acc) in zip(xs,nodes): node(x,ny,nw,nh,t,sub,acc)
for x in xs[:-1]: arrow(x+nw,ny,gap)

# ---------------- SOCIAL RELEVANCE + IMPACT ----------------
kicker(LX,8.92,"SOCIAL RELEVANCE + POTENTIAL IMPACT")
colw=3.55
run(para(tb(LX,9.18,colw,0.22),True),"WHO IT REACHES",size=8.5,color=VOLT_DK,font=MONO,bold=True,sp=1.0)
rel=[("Access & equity —","trainer-grade form for the millions with no gym, coach or PT."),
     ("Privacy & dignity —","camera never leaves the phone — safe for women, minors, clinical & rehab use."),
     ("Rehab & ageing —","guided reps between physiotherapy visits; elderly home workouts."),
     ("Reach —","fully offline — tier-2/3, rural, low-connectivity; budget phone to flagship.")]
y=9.42
for h,r in rel: bullet(LX,y,colw,h,r,size=8.8); y+=0.40
run(para(tb(RX-colw,9.18,colw,0.22),True),"POTENTIAL IMPACT",size=8.5,color=VOLT_DK,font=MONO,bold=True,sp=1.0)
imp=[("Fewer injuries, real adherence —","live form turns risky reps into safe ones."),
     ("Remote physiotherapy —","track recovery at home; ease the load on clinics."),
     ("Privacy-first health AI —","a template: useful AI that collects zero data."),
     ("~Zero marginal cost —","no servers, no cloud bill, no data-centre energy; millions, on-device.")]
y=9.42
for h,r in imp: bullet(RX-colw,y,colw,h,r,size=8.8); y+=0.40

rect(LX,11.12,CW,0.012,fill=HAIR,rounded=False)
run(para(tb(LX,11.18,CW,0.3,'mid'),True),
    "Vyāyāma  ·  on-device AI exercise coach  ·  Snapdragon Hexagon NPU  ·  100% offline  ·  128 self-tests green",
    size=8,color=MUTED)

prs.save(r"C:\Users\Rayyan Shaikh\Desktop\Vyayama_Report.pptx")
print("saved pptx")
