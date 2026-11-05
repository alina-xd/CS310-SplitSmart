"""Render the Team 19 implementation report and its vector diagrams.

Requires reportlab and Poppler's pdftoppm. Run from any directory:
  python3 docs/scripts/build_implementation_pdf.py --pdftoppm /path/to/pdftoppm
The editable report source is docs/implementation.md.
"""
from pathlib import Path
import argparse
import re
import math
import subprocess
from xml.sax.saxutils import escape
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_JUSTIFY
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.pagesizes import letter
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, PageBreak, Table, TableStyle, KeepTogether
from reportlab.graphics.shapes import Drawing, Rect, Line, String, Polygon, PolyLine
from reportlab.graphics import renderPDF

ROOT = Path(__file__).resolve().parents[2]
FIG = ROOT / 'docs/figures'
TMP = ROOT / 'tmp/pdfs'
OUT = ROOT / 'output/pdf/SplitSmart_Project_2.3_Implementation_Team19.pdf'
W = 504
INK = colors.HexColor('#202020')
GREY = colors.HexColor('#f0f0f0')


def label(d, x, y, text, size=9, bold=False, anchor='middle'):
    d.add(String(x, y, text, fontName='Helvetica-Bold' if bold else 'Helvetica', fontSize=size, textAnchor=anchor, fillColor=INK))


def box(d, x, y, w, h, title, lines=(), title_size=9.5, size=9):
    d.add(Rect(x,y,w,h,strokeColor=INK,fillColor=colors.white,strokeWidth=.7))
    d.add(Rect(x,y+h-25,w,25,strokeColor=INK,fillColor=GREY,strokeWidth=.7))
    label(d,x+w/2,y+h-16,title,title_size,True)
    for i,line in enumerate(lines):
        label(d,x+7,y+h-39-i*12,line,size,anchor='start')


def arrow(d, points, dashed=False):
    d.add(PolyLine([v for point in points for v in point],strokeColor=INK,strokeWidth=.75,strokeDashArray=[3,2] if dashed else None))
    x,y=points[-1];px,py=points[-2];a=math.atan2(y-py,x-px);s=5
    d.add(Polygon([x,y,x-s*math.cos(a-.45),y-s*math.sin(a-.45),x-s*math.cos(a+.45),y-s*math.sin(a+.45)],fillColor=INK,strokeColor=INK,strokeWidth=.5))


def architecture():
    d=Drawing(W,275)
    def layer(y,h,title,items):
        d.add(Rect(0,y,W,h,strokeColor=INK,fillColor=colors.white,strokeWidth=.7))
        label(d,8,y+h-15,title,10,True,anchor='start')
        n=len(items);cw=(W-20-(n-1)*6)/n
        for i,lines in enumerate(items):
            x=10+i*(cw+6)
            d.add(Rect(x,y+10,cw,h-34,strokeColor=INK,fillColor=GREY,strokeWidth=.6))
            first_baseline = y + 10 + (h - 34) / 2 + (len(lines) - 1) * 5.5 - 3
            for j,line in enumerate(lines):label(d,x+cw/2,first_baseline-j*11,line,8.5,j==0)
    layer(198,77,'Presentation layer', [['C1','Account screens'],['C2','Group screens'],['C3','Transaction screens'],['C4','Balance / summary']])
    layer(95,77,'Service layer', [['C5','Account'],['C6','Group'],['C7','Expense'],['C8','Settlement'],['C9','Balance'],['C10','History / summary']])
    layer(0,67,'Data layer', [['C11','Data model'],['C12','Repositories']])
    arrow(d,[(252,198),(252,172)]);arrow(d,[(252,95),(252,67)])
    label(d,359,183,'AuthService -> Firebase Authentication',8,anchor='middle')
    label(d,356,78,'Repositories -> Cloud Firestore',8,anchor='middle')
    return d


def screen_classes():
    d=Drawing(W,280)
    box(d,0,213,240,67,'GroupActivity',['onStart() / onStop()','onResume()'])
    box(d,264,213,240,67,'ExpenseFormActivity',['Loads members and expense fields','Saves through ExpenseService'])
    box(d,0,90,156,91,'GroupMembersSection',['refresh()','Owns member controls'])
    box(d,174,90,156,91,'GroupHistorySection',['implements TransactionListener','start() / stop()','onTransactionsChanged(list)'],size=8.4)
    box(d,348,90,156,91,'ExpenseSplitSection',['setMembers() / setParticipants()','load() / getShares()','getMethod() / getInputs()'],size=8.2)
    box(d,0,0,156,56,'GroupService',['getGroup() / getMembers()'],size=8.5)
    box(d,174,0,156,56,'HistoryService',['listen() / getHistory()'])
    box(d,348,0,156,56,'ExpenseService',['previewShares()'])
    arrow(d,[(70,213),(70,181)]);arrow(d,[(200,213),(252,198),(252,181)])
    arrow(d,[(426,213),(426,181)])
    for x in [78,252,426]:arrow(d,[(x,90),(x,56)])
    return d


def service_classes():
    d=Drawing(W,303)
    box(d,0,206,152,97,'SettlementService',['create / update / delete','getMaxAllowed()','isLocked()'])
    box(d,170,206,152,97,'BalanceService',['getSummary()','getPairBalance()','compute()'])
    box(d,352,206,152,97,'HistoryService',['getHistory() / listen()','getMonthlySummary()','getTransaction()'],size=8.8)
    box(d,0,91,152,71,'GroupRepository',['findById()','Loads group membership'])
    box(d,170,91,152,71,'BalanceSummary',['pay / receive totals','List<PairBalance>'])
    box(d,352,91,152,71,'HistoryFilter',['matches() / apply()','Keyword, filters, order'])
    box(d,0,0,152,59,'TransactionRepository',['Reads / writes / listeners'],title_size=8.8)
    box(d,170,0,152,59,'PairBalance',['otherUserId / amountCents'])
    box(d,352,0,152,59,'MonthlySummaryCalculator',['compute() -> MonthlySummary'],title_size=8.4,size=8.2)
    arrow(d,[(152,256),(170,256)])
    arrow(d,[(70,206),(70,162)])
    arrow(d,[(10,206),(10,177),(160,177),(160,30),(152,30)])
    arrow(d,[(246,206),(246,162)])
    arrow(d,[(315,206),(336,190),(336,72),(75,72),(75,59)])
    arrow(d,[(246,91),(246,59)])
    arrow(d,[(428,206),(428,162)])
    arrow(d,[(497,206),(497,181),(344,181),(344,30),(352,30)])
    return d


def sequence(names, messages, height, notes=()):
    d=Drawing(W,height)
    n=len(names);space=W/n;xs=[space*(i+.5) for i in range(n)]
    for i,name in enumerate(names):
        lines=name.split('\n');x=xs[i]
        d.add(Rect(x-space/2+3,height-37,space-6,37,strokeColor=INK,fillColor=GREY,strokeWidth=.7))
        for j,line in enumerate(lines):label(d,x,height-14-j*11,line,8.5,True)
        d.add(Line(x,height-37,x,8,strokeColor=colors.grey,strokeWidth=.6,strokeDashArray=[3,3]))
    for source,target,y,text,dashed in messages:
        if source==target:
            x=xs[source];arrow(d,[(x,y),(x+22,y),(x+22,y-13),(x,y-13)],dashed)
            label(d,x+27,y-6,text,8,anchor='start')
        else:
            a,b=xs[source],xs[target];arrow(d,[(a,y),(b,y)],dashed)
            label(d,(a+b)/2,y+5,text,8)
    for x,y,text in notes:label(d,x,y,text,8,anchor='start')
    return d


def settlement_sequence():
    names=['SettlementForm\nActivity','Settlement\nService','Group\nRepository','Balance\nService','Transaction\nRepository']
    msgs=[(0,1,345,'createSettlement(s, cb)',False),
          (1,1,321,'validate amount / parties / creator',False),
          (1,2,286,'findById(groupId)',False),(2,1,265,'onSuccess(group)',True),
          (1,1,242,'validate membership',False),
          (1,3,208,'getPairBalance(group, payer, recipient, cb)',False),
          (3,4,186,'findByGroup()',False),(4,3,164,'transactions',True),(3,1,142,'signed pair balance',True),
          (1,1,120,'check amount <= maximum',False),
          (1,4,88,'nextSequenceNo() -> sequence',False),
          (1,4,62,'save(s with date and sequence)',False),(4,1,40,'onSuccess(saved)',True),(1,0,18,'onSuccess(null)',True)]
    return sequence(names,msgs,406)


def history_sequence():
    names=['Group\nActivity','GroupHistory\nSection','History\nService','Transaction\nRepository','History\nFilter']
    msgs=[(0,1,218,'start()',False),(1,2,194,'listen(group, listener)',False),(2,3,170,'addListener()',False),
          (3,1,146,'onTransactionsChanged(snapshot)',True),(1,4,122,'apply(latest snapshot)',False),(4,1,98,'filtered / sorted list',True),
          (1,1,74,'update adapter',False),(0,1,38,'stop()',False),(1,1,18,'remove registration',False)]
    return sequence(names,msgs,276)


DIAGRAMS={
 'implementation-architecture':architecture(),
 'implementation-screen-classes':screen_classes(),
 'implementation-service-classes':service_classes(),
 'implementation-settlement-sequence':settlement_sequence(),
 'implementation-history-sequence':history_sequence(),
}

styles={
 'body':ParagraphStyle('Body',fontName='Times-Roman',fontSize=11,leading=14.2,spaceAfter=7,alignment=TA_JUSTIFY),
 'title':ParagraphStyle('Title',fontName='Times-Bold',fontSize=23,leading=27,alignment=TA_CENTER,spaceAfter=4),
 'subtitle':ParagraphStyle('Subtitle',fontName='Times-Roman',fontSize=13,leading=16,alignment=TA_CENTER,spaceAfter=4),
 'h2':ParagraphStyle('H2',fontName='Times-Bold',fontSize=14,leading=18,spaceBefore=7,spaceAfter=8,keepWithNext=True),
 'h3':ParagraphStyle('H3',fontName='Times-Bold',fontSize=11.5,leading=15,spaceBefore=6,spaceAfter=6,keepWithNext=True),
 'caption':ParagraphStyle('Caption',fontName='Times-Italic',fontSize=9.5,leading=12,spaceBefore=6,spaceAfter=9),
 'table':ParagraphStyle('Table',fontName='Times-Roman',fontSize=10,leading=12),
 'bullet':ParagraphStyle('Bullet',fontName='Times-Roman',fontSize=11,leading=14,spaceAfter=7,leftIndent=12,firstLineIndent=-9),
 'meta':ParagraphStyle('Meta',fontName='Times-Roman',fontSize=11,leading=13,spaceAfter=2),
}


def p(text,style='body'):
    return Paragraph(escape(text),styles[style])


def footer(canvas, doc):
    canvas.saveState()
    canvas.setTitle('SplitSmart - Project 2.3: Implementation - Team 19')
    canvas.setAuthor('Chenyang Wang; Alina Du; Dawi Yoon; William Wang; Yang Chen')
    canvas.setSubject('Architectural, detailed design, and requirements changes for SplitSmart')
    canvas.setCreator('Team 19')
    canvas._doc.info.producer='Team 19'
    canvas.setFont('Times-Roman',9)
    canvas.setFillColor(colors.HexColor('#555555'))
    canvas.drawString(54,768,'SplitSmart | Project 2.3: Implementation')
    canvas.drawRightString(558,768,'Team 19')
    canvas.line(54,760,558,760)
    canvas.drawCentredString(306,26,str(doc.page))
    canvas.restoreState()


def render(pdftoppm):
    FIG.mkdir(parents=True,exist_ok=True);TMP.mkdir(parents=True,exist_ok=True);OUT.parent.mkdir(parents=True,exist_ok=True)
    for key,d in DIAGRAMS.items():
        pdf=TMP/(key+'.pdf');renderPDF.drawToFile(d,str(pdf))
        subprocess.run([pdftoppm,'-r','180','-singlefile','-png',str(pdf),str(FIG/key)],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
    text=(ROOT/'docs/implementation.md').read_text()
    lines=text.splitlines();story=[];i=0;meta=False;first=True
    while i<len(lines):
        line=lines[i].strip();i+=1
        if not line:continue
        if line=='<!-- pagebreak -->':story.append(PageBreak());continue
        if line.startswith('# '):story.append(p(line[2:],'title'));continue
        if line in ('Project 2.3: Implementation','Team 19') and first:
            story.append(p(line,'subtitle'))
            if line=='Team 19':story.append(Spacer(1,7));first=False
            continue
        if line.startswith('## '):
            meta=line.startswith('## 1 ');story.append(p(line[3:],'h2'));continue
        if line.startswith('### '):story.append(p(line[4:],'h3'));continue
        if line.startswith('!['):
            caption,path=re.match(r'!\[(.*?)\]\((.*?)\)',line).groups();key=Path(path).stem
            story.append(KeepTogether([DIAGRAMS[key],p(caption,'caption')]))
            continue
        if line.startswith('|'):
            tablelines=[line]
            while i<len(lines) and lines[i].strip().startswith('|'):tablelines.append(lines[i]);i+=1
            rows=[]
            for row in tablelines:
                if re.match(r'^\|[\s:|\-]+\|$',row):continue
                cells=[c.strip() for c in row.strip().strip('|').split('|')]
                rows.append([p(cell,'table') for cell in cells])
            t=Table(rows,colWidths=[143,361],repeatRows=1,hAlign='LEFT')
            t.setStyle(TableStyle([('VALIGN',(0,0),(-1,-1),'TOP'),('BACKGROUND',(0,0),(-1,0),GREY),('GRID',(0,0),(-1,-1),.4,colors.grey),('TOPPADDING',(0,0),(-1,-1),6),('BOTTOMPADDING',(0,0),(-1,-1),6),('LEFTPADDING',(0,0),(-1,-1),7),('RIGHTPADDING',(0,0),(-1,-1),7)]))
            story.extend([t,Spacer(1,10)]);continue
        if line.startswith('- '):story.append(p('- '+line[2:],'bullet'));continue
        story.append(p(line,'meta' if meta else 'body'))
    doc=SimpleDocTemplate(str(OUT),pagesize=letter,leftMargin=54,rightMargin=54,topMargin=48,bottomMargin=45,allowSplitting=True)
    doc.build(story,onFirstPage=footer,onLaterPages=footer)
    print(OUT)

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--pdftoppm',default='pdftoppm');args=parser.parse_args();render(args.pdftoppm)
