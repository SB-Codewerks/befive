"""Builds the static mockup HTML files. Run: python3 build.py  (then render with render.sh)."""
import os, random, math
from common import *

HERE = os.path.dirname(os.path.abspath(__file__))

def write(name, html):
    with open(os.path.join(HERE, name), 'w') as f: f.write(html)

def axis_chart(w, h, lines, ymax, yticks, xlabels, yfmt=lambda v: f'{v:g}', bars=None, bar_max=None, bar_color='#ff4d4f'):
    """lines: list of (vals, color, width, fill, dash)."""
    L, R, T, B = 44, 22, 10, 24
    pw, ph = w-L-R, h-T-B
    g = [f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" style="display:block;font-family:Inter;font-size:10.5px">']
    for t in yticks:
        y = T + ph - t/ymax*ph
        g.append(f'<line x1="{L}" y1="{y:.1f}" x2="{w-R}" y2="{y:.1f}" stroke="#f0f0f0"/>')
        g.append(f'<text x="{L-8}" y="{y+3.5:.1f}" text-anchor="end" fill="#6b6b6b">{yfmt(t)}</text>')
    for i, lab in enumerate(xlabels):
        x = L + i*pw/(len(xlabels)-1)
        g.append(f'<text x="{x:.1f}" y="{h-6}" text-anchor="middle" fill="#6b6b6b">{lab}</text>')
    if bars:
        n = len(bars); bw = pw/n*0.6
        for i, v in enumerate(bars):
            bh = v/bar_max*ph*0.28; x = L + i*pw/n + (pw/n-bw)/2
            g.append(f'<rect x="{x:.1f}" y="{T+ph-bh:.1f}" width="{bw:.1f}" height="{bh:.1f}" fill="{bar_color}" opacity=".75" rx="1"/>')
    for vals, color, sw, fill, dash in lines:
        d, pts = path(vals, pw, ph, 0, ymax)
        d = 'M' + ' L'.join(f'{x+L:.1f},{y+T:.1f}' for x, y in pts)
        if fill:
            g.append(f'<defs><linearGradient id="g{color[1:]}" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stop-color="{color}" stop-opacity=".22"/><stop offset="1" stop-color="{color}" stop-opacity=".02"/></linearGradient></defs>')
            g.append(f'<path d="{d} L{L+pw},{T+ph} L{L},{T+ph} Z" fill="url(#g{color[1:]})"/>')
        da = f' stroke-dasharray="{dash}"' if dash else ''
        g.append(f'<path d="{d}" fill="none" stroke="{color}" stroke-width="{sw}"{da} stroke-linejoin="round"/>')
    g.append(f'<line x1="{L}" y1="{T+ph}" x2="{w-R}" y2="{T+ph}" stroke="#d9d9d9"/>')
    g.append('</svg>')
    return ''.join(g)

XL = ['02:05','02:15','02:25','02:35','02:45','02:55','03:05']

# =====================================================================
def overview():
    N = 60
    rps = series(N, 8100, 900, 260, 1, spikes=[(41, 3, 1300)])
    err = [max(0, v) for v in series(N, 14, 6, 8, 2, spikes=[(41, 3, 95)])]
    p50 = series(N, 11, 1.5, 1.2, 3); p95 = series(N, 24, 3, 2.5, 4, spikes=[(41,3,14)]); p90 = series(N, 19, 2.4, 2, 8, spikes=[(41,3,10)]); p99 = series(N, 38, 5, 4, 5, spikes=[(41,3,30)])
    ovh = series(N, 1.9, .2, .25, 6)
    traffic = axis_chart(736, 206, [(rps, '#1668dc', 2, True, None)], 12000, [0,3000,6000,9000,12000], XL,
                         yfmt=lambda v: f'{v/1000:g}k' if v else '0', bars=err, bar_max=110)
    lat = axis_chart(352, 206, [(p99, '#722ed1', 1.8, False, None), (p95, '#13a8a8', 1.8, False, None), (p90, '#eb2f96', 1.6, False, None),
                                (p50, '#1668dc', 1.8, False, None), (ovh, '#fa8c16', 1.6, False, '4 3')],
                     80, [0,20,40,60,80], XL[::2], yfmt=lambda v: f'{v:g} ms' if v else '0')
    kpis = [
      ('Requests / s', '8,412', '', '+6.2% vs prev. hour', 'up', series(30, 8000, 500, 250, 11), '#1668dc'),
      ('5xx error rate', '0.21', '%', '+0.08 pts', 'down', series(30, .15, .05, .05, 12, spikes=[(21,2,.5)]), '#cf1322'),
      ('Latency p99 (total)', '38', ' ms', 'gateway overhead p99 1.9 ms', 'neutral', series(30, 38, 4, 3, 13), '#722ed1'),
      ('Auth failures / s', '3.4', '', '401 spike on okta-prod 02:47', 'down', series(30, 1.2, .3, .3, 14, spikes=[(21,3,6)]), '#d48806'),
    ]
    kpi_html = ''.join(f'''<div class="card"><div class="card-body" style="padding:14px 16px">
      <div class="stat"><div class="label">{l}{icon('info',12,'#bfbfbf')}</div>
      <div style="display:flex;align-items:flex-end;justify-content:space-between"><div class="value">{v}<small>{u}</small></div>{sparkline(s,110,34,c)}</div>
      <div class="delta {d}">{dt}</div></div></div></div>''' for l, v, u, dt, d, s, c in kpis)

    routes = [('orders-get','GET','/orders/:id','2,941','0.04%','31 ms','#1668dc',21),
              ('orders-create','POST','/orders','612','0.31%','84 ms','#1668dc',22),
              ('payments-authorize','POST','/payments/authorize','488','1.92%','212 ms','#cf1322',23),
              ('catalog-search','GET','/catalog/search','1,733','0.02%','46 ms','#1668dc',24),
              ]
    rrows = ''.join(f'''<tr><td><div style="font-weight:500">{r}</div><div class="muted mono" style="font-size:11px"><span class="m {m.lower()}">{m}</span> {p}</div></td>
      <td class="num">{rp}</td><td class="num" style="color:{'#cf1322' if float(e[:-1])>1 else 'inherit'}">{e}</td><td class="num">{l}</td><td>{sparkline(series(24,10,3,2,s),96,22,c)}</td></tr>''' for r,m,p,rp,e,l,c,s in routes)

    nodes = [('befive-7f9c2','1a','1843','ok','1,428'),('befive-81d0a','1a','1843','ok','1,391'),
             ('befive-2b6e4','1b','1843','ok','1,405'),('befive-c03f1','1b','1843','ok','1,377'),
             ('befive-9ae57','1c','1843','ok','1,412'),]
    nrows = ''.join(f'''<tr><td><span class="dot {s}"></span><span class="mono">{n}</span></td><td class="muted">{z}</td>
      <td><span class="tag green">rev {rv}</span></td><td class="num">{r}</td></tr>''' for n,z,rv,s,r in nodes)

    changes = [('route.update','orders-get','jane.doe','2 min ago','blue','read timeout 30s &rarr; 10s'),
               ('credential.rotate','acme-corp','p.kim','18 min ago','gold','API key rotated, 7-day grace'),
               ('config.apply','14 objects','ci-pipeline','1 h ago','purple','b5ctl apply from main@4be1c09'),
               ]
    crows = ''.join(f'''<div style="display:flex;gap:10px;padding:8px 0;border-bottom:1px solid #f0f0f0">
      <div style="width:6px;border-radius:3px;background:{ {'blue':'#91caff','gold':'#ffd666','purple':'#d3adf7'}[c]}"></div>
      <div style="flex:1;min-width:0"><div><span class="tag {c}" style="height:18px;font-size:11px">{a}</span> <b style="font-weight:500">{o}</b></div>
      <div class="muted" style="font-size:12px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">{d}</div></div>
      <div class="muted" style="font-size:11.5px;text-align:right;white-space:nowrap">{t}<br>{u}</div></div>''' for a,o,u,t,c,d in changes)

    body = f'''
<div class="page-head"><h1>Overview</h1><span class="sub">Live counters from gateway nodes &middot; updated 4 s ago</span>
  <div class="actions"><div class="seg"><span>15 min</span><span class="on">1 hour</span><span>6 hours</span><span>24 hours</span></div>
  <div class="select" style="width:150px">All routes</div><button class="btn">{icon('ext',14)}CloudWatch</button></div></div>
<div class="grid" style="grid-template-columns:repeat(4,minmax(0,1fr));margin-bottom:16px">{kpi_html}</div>
<div class="grid" style="grid-template-columns:minmax(0,2fr) minmax(0,1fr);margin-bottom:16px">
  <div class="card"><div class="card-head">Traffic<span class="extra"><span><span class="dot" style="background:#1668dc"></span>Requests / s</span><span><span class="dot" style="background:#ff4d4f"></span>5xx / s</span></span></div>
    <div style="padding:10px 12px 4px">{traffic}</div></div>
  <div class="card"><div class="card-head">Latency<span class="extra" style="font-size:12px"><span><span class="dot" style="background:#722ed1"></span>p99</span><span><span class="dot" style="background:#13a8a8"></span>p95</span><span><span class="dot" style="background:#eb2f96"></span>p90</span><span><span class="dot" style="background:#1668dc"></span>p50</span><span><span class="dot" style="background:#fa8c16"></span>overhead</span></span></div>
    <div style="padding:10px 12px 4px">{lat}</div></div>
</div>
<div class="grid" style="grid-template-columns:minmax(0,1.5fr) minmax(0,1fr) minmax(0,1fr)">
  <div class="card"><div class="card-head">Top routes<span class="extra"><a>View all routes {icon('chevr',12)}</a></span></div>
    <table class="t dense nowrap"><thead><tr><th>Route</th><th class="num">Req/s</th><th class="num">5xx</th><th class="num">p99</th><th>Last hour</th></tr></thead><tbody>{rrows}</tbody></table></div>
  <div class="card"><div class="card-head">Gateway nodes<span class="extra"><span class="tag green">6 in sync</span></span></div>
    <table class="t dense nowrap"><thead><tr><th>Node</th><th>AZ</th><th>Config</th><th class="num">Req/s</th></tr></thead><tbody>{nrows}</tbody></table></div>
  <div class="card"><div class="card-head">Recent changes<span class="extra"><a>Audit log {icon('chevr',12)}</a></span></div>
    <div style="padding:2px 16px 0">{crows}</div></div>
</div>'''
    write('overview.html', page('Overview', 'Overview', body))

# =====================================================================
def routes():
    rows = [
     ('orders-get','Get order','GET','/orders/:id','api.example.com','orders',[('JWT','blue','okta-prod'),('API key','cyan','')],'orders-read','gold-rps',True,'2,941',31,False),
     ('orders-create','Create order','POST','/orders','api.example.com','orders',[('JWT','blue','okta-prod')],'orders-write','orders-burst',True,'612',32,True),
     ('orders-cancel','Cancel order','DELETE','/orders/:id','api.example.com','orders',[('JWT','blue','okta-prod')],'orders-write','&mdash;',True,'14',33,False),
     ('payments-authorize','Authorize payment','POST','/payments/authorize','api.example.com','payments',[('mTLS','purple',''),('JWT','blue','')],'payments-partners','pay-10rps',True,'488',34,True),
     ('payments-webhook','Payment webhook','POST','/hooks/payments','hooks.example.com','payments',[('API key','cyan','')],'webhook-senders','&mdash;',True,'57',35,False),
     ('catalog-search','Search catalog','GET','/catalog/search','api.example.com','catalog',[('Public','default','')],'public','anon-ip-20rps',True,'1,733',36,False),
     ('catalog-item','Catalog item','GET','/catalog/items/*','api.example.com','catalog',[('Public','default','')],'public','anon-ip-20rps',True,'1,120',37,False),
     ('invoices-list','List invoices','GET','/invoices','api.example.com','billing',[('JWT','blue','okta-prod'),('API key','cyan','')],'invoices-read','&mdash;',True,'377',39,False),
     ('reports-export','Export report','GET','/reports/:id/export','partners.example.com','reporting',[('Introspection','geek','okta-prod')],'reports-read','quota-monthly',True,'22',40,False),
     ('legacy-soap','Legacy SOAP bridge','POST','/legacy/*','api.example.com','legacy',[('API key','cyan','')],'legacy-clients','&mdash;',False,'0',42,False),
    ]
    trs = []
    for rid, name, m, p, host, svc, auth, pol, rl, en, rps, seed, sel in rows:
        at = ' '.join(f'<span class="tag {c}">{a}</span>' for a, c, x in auth)
        poltag = '<span class="tag gold">public</span>' if pol == 'public' else f'<span class="mono" style="font-size:12px">{pol}</span>'
        spark = sparkline(series(24, 10, 3, 2.5, seed), 64, 22, '#1668dc') if en else '<span class="muted" style="font-size:12px">disabled</span>'
        trs.append(f'''<tr class="{'sel' if sel else ''}"><td style="width:36px"><span class="cb {'on' if sel else ''}"></span></td>
<td><div style="font-weight:500;color:#1668dc">{rid}</div><div class="muted" style="font-size:12px">{name}</div></td>
<td><div><span class="m {m.lower()}">{m}</span> <span class="mono" style="font-size:12px">{p}</span></div><div class="muted" style="font-size:11.5px">{host}</div></td>
<td><span class="tag">{svc}</span></td>
<td>{at}</td><td>{poltag}</td><td class="mono" style="font-size:12px;color:rgba(0,0,0,.65)">{rl}</td>
<td><span class="sw {'on' if en else ''}"></span></td>
<td class="num">{rps}</td><td>{spark}</td>
<td style="width:36px;color:#6b6b6b">{icon('more',16)}</td></tr>''')
    body = f'''
<div class="crumbs">Traffic<span class="sep">/</span><span class="cur">Routes</span></div>
<div class="page-head"><h1>Routes</h1><span class="tag">86 routes</span><span class="sub">Match rules and per-route pipeline: authentication, access policy, rate limits, transforms</span>
 <div class="actions"><button class="btn">{icon('upload',14)}Import</button><button class="btn">{icon('code',14)}Export EDN</button><button class="btn primary">{icon('plus',14)}New route</button></div></div>
<div class="card">
 <div style="display:flex;gap:10px;padding:12px 16px;align-items:center;border-bottom:1px solid #f0f0f0">
   <div class="input" style="width:260px">{icon('search',14,'#bfbfbf')}<span style="color:rgba(0,0,0,.25);white-space:nowrap">Filter by ID, name, path, host</span></div>
   <div class="select" style="width:130px;white-space:nowrap">Service: all</div><div class="select" style="width:130px;white-space:nowrap">Auth: all</div>
   <div class="select" style="width:120px;white-space:nowrap">Host: all</div><div class="select multi" style="width:220px;flex-wrap:nowrap"><span class="chip" style="white-space:nowrap">team-orders <i>&#10005;</i></span><span class="chip">prod <i>&#10005;</i></span></div>
   <div style="margin-left:auto;display:flex;gap:8px;align-items:center"><div class="seg"><span class="on">Compact</span><span>Comfortable</span></div><button class="btn text">{icon('sliders',14)}Columns</button></div>
 </div>
 <div style="display:flex;align-items:center;gap:12px;padding:8px 16px;background:#f0f7ff;border-bottom:1px solid #d6e8ff;font-size:12.5px">
   <span><b style="font-weight:600">2 selected</b></span><a>Enable</a><a>Disable</a><a>Add tag</a><a>Export selected as EDN</a><a style="color:#cf1322">Delete&hellip;</a>
   <span class="muted" style="margin-left:auto">Traffic columns: live, last 15 minutes, all nodes</span></div>
 <table class="t dense nowrap"><thead><tr><th><span class="cb"></span></th><th>Route {icon('chev',10,'#bfbfbf')}</th><th>Match</th><th>Service</th><th>Authentication</th><th>Access policy</th><th>Rate limit</th><th>On</th><th class="num">Req/s</th><th>15 min</th><th></th></tr></thead>
 <tbody>{''.join(trs)}</tbody></table>
 <div class="pager"><span style="margin-right:auto;padding-left:4px" class="muted">1&ndash;10 of 86</span><span class="pg">&lsaquo;</span><span class="pg on">1</span><span class="pg">2</span><span class="pg">3</span><span class="pg">&hellip;</span><span class="pg">8</span><span class="pg">&rsaquo;</span><div class="select" style="width:110px;height:28px;margin-left:8px">10 / page</div></div>
</div>'''
    write('routes-list.html', page('Routes', 'Routes', body))

# =====================================================================
def route_edit():
    edn = [
     ('c',';; routes/orders-get  (live preview)'),
     ('', '{<k>:id</k> <s>"orders-get"</s>'),
     ('', ' <k>:name</k> <s>"Get order"</s>'),
     ('', ' <k>:service</k> <s>"orders"</s>'),
     ('', ' <k>:match</k> {<k>:hosts</k> [<s>"api.example.com"</s>]'),
     ('', '         <k>:paths</k> [<s>"/orders/:id"</s>]'),
     ('', '         <k>:methods</k> #{<k>:get</k> <k>:head</k>}}'),
     ('', ' <k>:upstream-path</k> {<k>:strip-prefix</k> <n>false</n>'),
     ('', '                 <k>:rewrite</k> <s>"/api/orders/{id}"</s>}'),
     ('', ' <k>:authn</k> {<k>:mode</k> <k>:any</k>'),
     ('', '  <k>:methods</k> [{<k>:type</k> <k>:jwt</k>'),
     ('', '             <k>:identity-provider</k> <s>"okta-prod"</s>'),
     ('', '             <k>:audiences</k> #{<s>"api://orders"</s>}}'),
     ('', '            {<k>:type</k> <k>:api-key</k>}]}'),
     ('', ' <k>:access</k> {<k>:policy</k> <s>"orders-read"</s>'),
     ('', '          <k>:methods</k> {<k>:head</k> <k>:public</k>}}'),
     ('', ' <k>:rate-limits</k> [{<k>:id</k> <s>"orders-burst"</s>'),
     ('', '                <k>:limit</k> <n>50</n> <k>:per</k> <k>:second</k>'),
     ('', '                <k>:key</k> <k>:consumer-and-route</k>}]'),
     ('', ' <k>:request-headers</k> {<k>:set</k> {<s>"X-Env"</s> <s>"prod"</s>}'),
     ('', '                     <k>:remove</k> [<s>"X-Debug"</s>]}'),
     ('', ' <k>:timeouts</k> {<k>:read-ms</k> <n>10000</n>}'),
     ('', ' <k>:tags</k> #{<s>"team-orders"</s>}}'),
    ]
    code = ''.join(f'<div><span class="ln">{i+1}</span>' + (f'<span class="c">{t}</span>' if k=='c' else t.replace('<k>','<span class="k">').replace('</k>','</span>').replace('<s>','<span class="s">').replace('</s>','</span>').replace('<n>','<span class="n">').replace('</n>','</span>')) + '</div>' for i,(k,t) in enumerate(edn))
    methods = ''.join(f'<span class="{ "on" if m in ("GET","HEAD") else ""}">{m}</span>' for m in ['GET','HEAD','POST','PUT','PATCH','DELETE','OPTIONS'])
    anchors = ''.join(f'<div style="padding:5px 0 5px 12px;border-left:2px solid {"#1668dc" if a=="Matching" else "#f0f0f0"};color:{"#1668dc" if a=="Matching" else "rgba(0,0,0,.65)"};font-size:13px">{a}{b}</div>'
                      for a, b in [('Basics',''),('Matching',' <span class="badge" style="margin-left:4px">1</span>'),('Authentication',''),('Access policy',''),('Rate limits',''),('Header transforms',''),('Plugins',''),('Timeouts &amp; retries',''),('Advanced','')])
    body = f'''
<div class="crumbs">Traffic<span class="sep">/</span>Routes<span class="sep">/</span>orders-get<span class="sep">/</span><span class="cur">Edit</span></div>
<div class="page-head"><h1>Edit route <span class="mono" style="font-size:17px;font-weight:500;color:rgba(0,0,0,.65)">orders-get</span></h1><span class="tag gold">Unsaved changes</span><span class="sub">version 7 &middot; last changed by jane.doe 2 min ago</span>
  <div class="actions"><button class="btn">{icon('play',13)}Test route</button><button class="btn">Cancel</button><button class="btn primary">Save and apply</button></div></div>
<div class="cols" style="display:grid;grid-template-columns:140px minmax(0,1fr) 400px;gap:16px">
 <div style="padding-top:4px">{anchors}</div>
 <div style="display:flex;flex-direction:column;gap:14px">
  <div class="alert err"><span class="ic">{icon('xcircle',15,'#cf1322')}</span><div><b>1 field needs attention.</b> Fix it before saving. <a>Go to field</a></div></div>
  <div class="card"><div class="card-head">Basics</div><div class="card-body" style="padding-bottom:0">
    <div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:16px">
      <div class="fi"><label><span class="req">*</span>Route ID</label><div class="input" style="background:#fafafa;color:#6b6b6b">orders-get</div><div class="help">Immutable. Used by config-as-code.</div></div>
      <div class="fi"><label>Display name</label><div class="input">Get order</div></div>
      <div class="fi"><label><span class="req">*</span>Service</label><div class="select" style="white-space:nowrap">orders <span class="muted" style="font-size:12px">&rarr; 3 targets</span></div></div>
    </div></div></div>
  <div class="card"><div class="card-head">Matching<span class="extra muted" style="font-size:12px">A request must match host, path, method and headers</span></div><div class="card-body" style="padding-bottom:0">
    <div class="fi"><label>Hosts <span class="opt">(optional; empty = any host)</span></label><div class="select multi"><span class="chip">api.example.com <i>&#10005;</i></span></div></div>
    <div class="fi"><label><span class="req">*</span>Paths</label>
      <div style="display:flex;gap:8px;margin-bottom:8px"><div class="input" style="flex:1"><span class="mono">/orders/:id</span></div><button class="btn text">{icon('trash',14,'#8c8c8c')}</button></div>
      <div style="display:flex;gap:8px"><div class="input err" style="flex:1"><span class="mono">v1/orders/:id</span></div><button class="btn text">{icon('trash',14,'#8c8c8c')}</button></div>
      <div class="errmsg">Must start with <span class="mono">/</span>. Use <span class="mono">:name</span> for parameters and a trailing <span class="mono">/*</span> for prefix match.</div>
      <button class="btn link" style="margin-top:4px">{icon('plus',13)}Add path</button></div>
    <div class="fi"><label>Methods <span class="opt">(none selected = all methods)</span></label><div class="radio-btns">{methods}</div><div><button class="btn link" style="margin-top:8px">{icon('plus',13)}Add header condition</button></div></div></div></div>
  <div class="card"><div class="card-head">Authentication<span class="extra"><div class="radio-btns"><span class="on">Any of</span><span>All of</span></div></span></div><div class="card-body" style="padding:12px 16px">
    <div style="display:flex;align-items:center;gap:10px;padding:8px 10px;border:1px solid #f0f0f0;border-radius:6px;margin-bottom:8px">{icon('drag',14,'#bfbfbf')}<span class="tag blue">JWT</span><span>Okta &middot; <b style="font-weight:500">okta-prod</b></span><span class="muted" style="font-size:12px">audience api://orders &middot; RS256</span><span style="margin-left:auto" class="muted">{icon('edit',14)}&nbsp;&nbsp;{icon('trash',14)}</span></div>
    <div style="display:flex;align-items:center;gap:10px;padding:8px 10px;border:1px solid #f0f0f0;border-radius:6px">{icon('drag',14,'#bfbfbf')}<span class="tag cyan">API key</span><span>Header <span class="mono">X-API-Key</span></span><span class="muted" style="font-size:12px">stripped before proxying</span><span style="margin-left:auto" class="muted">{icon('edit',14)}&nbsp;&nbsp;{icon('trash',14)}</span></div>
  </div></div>
 </div>
 <div class="card" style="align-self:start"><div class="card-head">{icon('code',15)}EDN preview<span class="extra"><div class="seg"><span class="on">EDN</span><span>JSON</span><span>Diff</span></div>{icon('copy',14,'#8c8c8c')}</span></div>
   <div style="padding:10px 12px 12px"><div class="code" style="border:none;background:#fbfcfd">{code}</div>
   <div class="alert warn" style="margin-top:10px;font-size:12px"><span class="ic">{icon('warn',14,'#d48806')}</span><div>Preview shows the last valid state. <span class="mono">:paths</span> entry 2 is invalid and omitted.</div></div></div></div>
</div>'''
    write('route-edit.html', page('Edit route', 'Routes', body))

# =====================================================================
def okta_wizard():
    def res(ok, title, detail, extra=''):
        ic = icon('check',16,'#52c41a') if ok=='ok' else (icon('warn',16,'#faad14') if ok=='warn' else icon('xcircle',16,'#ff4d4f'))
        return f'''<div style="display:flex;gap:12px;padding:8px 0;border-bottom:1px solid #f0f0f0"><div style="margin-top:2px">{ic}</div>
<div style="flex:1"><div style="font-weight:500">{title}</div><div class="muted" style="font-size:12.5px">{detail}</div>{extra}</div></div>'''
    steps = [('done','Provider type','Okta'),('done','Okta tenant','acme.okta.com'),('done','Audience & claims','api://orders'),('cur','Test connection','Verify endpoints'),('','Review & save','')]
    st = ''.join(f'<div class="step {c}"><div class="n">{icon("tick",14,"#1668dc",2.5) if c=="done" else i+1}</div><div><div class="tt">{t}</div><div class="ds">{d}</div></div></div>' for i,(c,t,d) in enumerate(steps))
    jwks = '<div style="display:flex;gap:6px;margin-top:6px"><span class="tag mono" style="font-size:11px">kid 9fQ2&hellip;xk RS256</span><span class="tag mono" style="font-size:11px">kid Hc71&hellip;a0 RS256</span></div>'
    groups_warn = '''<div class="alert warn" style="margin-top:8px;font-size:12.5px"><span class="ic">''' + icon('warn',14,'#d48806') + '''</span><div><b>No <span class="mono">groups</span> claim in the sample token.</b> Group-based policies will not match. In Okta Admin, open Security &rsaquo; API &rsaquo; Authorization Servers &rsaquo; <i>default</i> &rsaquo; Claims and add a <span class="mono">groups</span> claim (access token, filter e.g. matches regex <span class="mono">.*</span>). <a>Okta guide</a></div></div>'''
    body = f'''
<div class="crumbs">Access &amp; governance<span class="sep">/</span>Identity providers<span class="sep">/</span><span class="cur">Connect Okta</span></div>
<div class="page-head"><h1>Connect Okta</h1><span class="sub">Derives discovery, JWKS and introspection endpoints from your Okta domain</span>
  <div class="actions"><button class="btn text">Cancel</button></div></div>
<div class="card" style="margin-bottom:12px"><div class="card-body" style="padding:14px 24px">{'<div class="steps">'+st+'</div>'}</div></div>
<div style="display:grid;grid-template-columns:1fr 1.25fr;gap:16px">
  <div class="card"><div class="card-head">Connection details<span class="extra"><a>Edit</a></span></div><div class="card-body">
    <div class="desc">
      <div class="k">Name</div><div>Okta (production) &nbsp;<span class="mono muted" style="font-size:11.5px">okta-prod</span></div>
      <div class="k">Okta domain</div><div class="mono" style="font-size:12px">acme.okta.com</div>
      <div class="k">Authorization server</div><div class="mono" style="font-size:12px">default</div>
      <div class="k">Issuer <span class="tag" style="height:16px;font-size:10px">derived</span></div><div class="mono" style="font-size:11.5px">https://acme.okta.com/oauth2/default</div>
      <div class="k">Discovery URL</div><div class="mono" style="font-size:11.5px">&hellip;/oauth2/default/.well-known/openid-configuration</div>
      <div class="k">JWKS URL</div><div class="mono" style="font-size:11.5px">&hellip;/oauth2/default/v1/keys</div>
      <div class="k">Introspection</div><div class="mono" style="font-size:11.5px">&hellip;/oauth2/default/v1/introspect</div>
      <div class="k">Audience</div><div class="mono" style="font-size:12px">api://orders</div>
      <div class="k">Allowed algorithms</div><div><span class="tag">RS256</span> <span class="tag">ES256</span> <span class="tag">PS256</span></div>
      <div class="k">Claim mapping</div><div style="font-size:12px">subject <span class="mono">sub</span> &middot; client <span class="mono">cid</span> &middot; scopes <span class="mono">scp</span> &middot; groups <span class="mono">groups</span></div>
      <div class="k">Introspection client</div><div style="font-size:12px"><span class="mono">0oa1introspect</span> &middot; secret stored encrypted {icon('lock',12,'#8c8c8c')}</div>
    </div>
    <div class="fi" style="margin:12px 0 0"><label>Sample access token <span class="opt">(optional, never stored)</span></label>
      <div class="input" style="height:44px;align-items:flex-start;padding-top:4px;overflow:hidden"><span class="mono" style="font-size:11px;color:rgba(0,0,0,.65);word-break:break-all;white-space:normal">eyJraWQiOiI5ZlEyLi4ueGsiLCJhbGciOiJSUzI1NiJ9.eyJ2ZXIiOjEsImp0aSI6IkFULi4uIiwiaXNzIjoiaHR0cHM6Ly9hY21lLm9rdGEuY29tL29hdXRoMi9kZWZhdWx0IiwiYXVkIjoiYXBpOi8vb3JkZXJzIi&hellip;</span></div>
      </div>
  </div></div>
  <div class="card"><div class="card-head">Test results<span class="extra"><span class="tag green">4 passed</span><span class="tag gold">1 warning</span><span class="muted" style="font-size:12px">ran 3 s ago &middot; 412 ms</span><button class="btn sm">{icon('refresh',12)}Run again</button></span></div><div class="card-body" style="padding:4px 16px 8px">
    {res('ok','DNS and TLS','acme.okta.com resolved to a public address; TLS 1.3 handshake OK; certificate valid until 2027-03-14')}
    {res('ok','Discovery document','200 OK in 96 ms; issuer matches https://acme.okta.com/oauth2/default')}
    {res('ok','JSON Web Key Set','200 OK in 88 ms; 2 signing keys, both RS256 (allowed)', jwks)}
    {res('ok','Introspection credentials','Dummy token returned {{"active": false}}: client credentials accepted')}
    {res('warn','Sample token','Signature valid (kid 9fQ2&hellip;xk), issuer and audience match, expires in 54 min', groups_warn)}
    <div style="padding:10px 0 4px"><div style="font-weight:500;margin-bottom:6px">Mapped identity</div>
      <div style="display:grid;grid-template-columns:120px 1fr;row-gap:4px;font-size:12.5px">
        <span class="muted">Subject</span><span class="mono">00u8k2ab9XyZ1pQ4d5d7</span>
        <span class="muted">Client ID</span><span class="mono">0oa9partnerapp &rarr; application <a>acme-order-sync</a></span>
        <span class="muted">Scopes</span><span><span class="tag">orders:read</span> <span class="tag">orders:write</span></span>
        <span class="muted">Groups</span><span class="muted">&mdash; (claim missing)</span></div></div>
  </div></div>
</div>
<div style="display:flex;justify-content:flex-end;gap:8px;margin-top:12px"><span class="muted" style="margin-right:auto;align-self:center;font-size:12px">Nothing is saved until you finish the review step.</span><button class="btn">Back</button><button class="btn primary">Continue to review {icon('chevr',13,'#fff')}</button></div>'''
    write('okta-wizard.html', page('Connect Okta', 'Identity providers', body, extra_css='.desc div{padding:5px 12px}.content{padding-top:12px}.page-head{margin-bottom:10px}'))

# =====================================================================
def consumer_detail():
    creds = [('api-key','b5k_k3j9x2ab_&bull;&bull;&bull;&bull;','Primary','2026-03-02','2026-10-04','2 min ago','gold','In grace period'),
             ('api-key','b5k_p7mq4tzd_&bull;&bull;&bull;&bull;','Primary (new)','2026-09-27','&mdash;','just now','green','Active'),
             ('oauth-client','0oa9partnerapp','okta-prod (bound)','2026-01-15','&mdash;','4 s ago','green','Active'),
             ('mtls','sha256 7c1e&hellip;9a40','CN=acme-payments','2026-02-20','2027-02-20','1 h ago','green','Active'),
             ('api-key','b5k_z2cw8hne_&bull;&bull;&bull;&bull;','Sandbox','2025-11-08','&mdash;','&mdash;','red','Revoked')]
    ct = {'api-key':('API key','cyan'),'oauth-client':('OAuth client','blue'),'mtls':('Client cert','purple')}
    rows = ''.join(f'''<tr><td><span class="tag {ct[t][1]}">{ct[t][0]}</span></td><td class="mono" style="font-size:12px">{v}</td><td>{l}</td><td class="sec">{c}</td><td class="sec">{e}</td><td class="sec">{u}</td>
<td><span class="tag {sc}">{s}</span></td><td style="white-space:nowrap">{'<a>Rotate</a> &nbsp; <a style="color:#cf1322">Revoke</a>' if s!='Revoked' else '<span class="muted">&mdash;</span>'}</td></tr>''' for t,v,l,c,e,u,sc,s in creds)
    body = f'''
<div class="crumbs">Access &amp; governance<span class="sep">/</span>Organizations<span class="sep">/</span>Acme Corp<span class="sep">/</span>Applications<span class="sep">/</span><span class="cur">acme-order-sync</span></div>
<div class="page-head"><h1 class="mono" style="font-size:19px">acme-order-sync</h1><span class="tag green">Active</span><span class="tag gold">Plan: Gold</span><span class="muted">Application of consumer <a>acme-corp</a> &middot; organization <a>Acme Corp</a></span>
  <div class="actions"><button class="btn">{icon('code',14)}View EDN</button><button class="btn">{icon('pause',13)}Suspend</button><button class="btn primary">{icon('edit',13,'#fff')}Edit</button></div></div>
<div style="display:grid;grid-template-columns:minmax(0,1fr) 330px;gap:16px">
 <div class="card"><div style="padding:0 16px"><div class="tabs"><span class="on">Credentials</span><span>Subscriptions (4)</span><span>Usage</span><span>Effective access</span><span>Activity</span><span>EDN</span></div></div>
  <div style="padding:14px 16px;display:flex;align-items:center;gap:8px"><span class="muted" style="white-space:nowrap">API keys: 2 of 2 active</span><div style="margin-left:auto;display:flex;gap:8px"><button class="btn">{icon('key',14)}Map OAuth client</button><button class="btn">{icon('lock',14)}Add certificate</button><button class="btn primary">{icon('plus',14,'#fff')}Issue API key</button></div></div>
  <table class="t nowrap"><thead><tr><th>Type</th><th>Identifier</th><th>Label</th><th>Created</th><th>Expires</th><th>Last used</th><th>Status</th><th></th></tr></thead><tbody>{rows}</tbody></table>
  <div style="padding:14px 16px 6px;font-weight:600;border-top:1px solid #f0f0f0;margin-top:10px">Traffic, last 24 hours <span class="muted" style="font-weight:400;font-size:12px">&middot; all routes &middot; requests/s</span></div>
  <div style="padding:0 12px 12px">{axis_chart(776, 190, [(series(96, 34, 16, 4, 77), '#1668dc', 1.8, True, None)], 80, [0,20,40,60,80], ['04:00','08:00','12:00','16:00','20:00','00:00','04:00'], bars=[max(0,v) for v in series(96, .3, .2, .4, 78, spikes=[(60,3,3)])], bar_max=4)}</div>
 </div>
 <div style="display:flex;flex-direction:column;gap:16px">
  <div class="card"><div class="card-head">Details</div><div class="card-body" style="padding:10px 16px">
    <div class="kv"><span>Organization</span><span>Acme Corp <span class="tag" style="height:18px">partner</span></span></div><div class="kv"><span>Consumer</span><span class="mono" style="font-size:12px">acme-corp</span></div>
    <div class="kv"><span>Contact</span><span>Priya Kim &middot; api@acme.example</span></div>
    <div class="kv"><span>Groups</span><span><span class="tag">tier-1-partners</span></span></div>
    <div class="kv"><span>Created</span><span>2026-01-15 by p.kim</span></div>
    <div class="kv"><span>Tags</span><span><span class="tag">partner</span> <span class="tag">emea</span></span></div></div></div>
  <div class="card"><div class="card-head">Plan limits &middot; Gold</div><div class="card-body" style="padding:12px 16px">
    <div class="kv"><span>Rate limit</span><span>100 req/s &middot; burst 200</span></div>
    <div style="margin:10px 0 4px;display:flex;justify-content:space-between;font-size:12.5px"><span>Monthly quota (September, UTC)</span><span><b style="font-weight:600">3.12 M</b> / 5 M</span></div>
    <div class="progress"><i style="width:62%"></i></div>
    <div class="muted" style="font-size:12px;margin-top:4px">62% used &middot; resets in 3 days</div></div></div>
 </div>
</div>'''
    overlay = f'''<div class="mask"></div>
<div class="modal" style="left:calc(224px + (1216px - 600px)/2);top:170px;width:600px">
 <div class="mh">{icon('check',20,'#52c41a')}API key issued</div>
 <div class="mb">
  <div class="alert warn" style="margin-bottom:14px"><span class="ic">{icon('warn',15,'#d48806')}</span><div><b>Copy this key now.</b> It is stored only as a hash and cannot be shown again. If it is lost, rotate the key.</div></div>
  <div class="fi" style="margin-bottom:12px"><label>API key for <b style="font-weight:600" class="mono">acme-order-sync</b> (Acme Corp) &middot; Primary key (rotated)</label>
   <div style="display:flex;gap:8px"><div class="input" style="flex:1;background:#fafafa"><span class="mono" style="font-size:11.5px;white-space:nowrap">b5k_p7mq4tzd_4fT9vQm2Lx8RbN1cYe7KuWd3HsJ0aZgP6iVt5oXqE2r</span></div><button class="btn primary">{icon('copy',14,'#fff')}Copy</button></div></div>
  <div class="desc" style="grid-template-columns:150px 1fr;font-size:12.5px">
   <div class="k">Send in header</div><div class="mono">X-API-Key</div>
   <div class="k">Previous key</div><div>b5k_k3j9x2ab_&hellip; expires 2026-10-04 (7-day grace)</div>
   <div class="k">Takes effect</div><div>after revision 1844 is applied (seconds)</div></div>
  <div style="display:flex;align-items:center;gap:8px;margin-top:14px"><span class="cb on"></span><span>I have stored this key in a secure location</span></div>
 </div>
 <div class="mf"><span class="muted" style="margin-right:auto;align-self:center;font-size:12px">Done stays disabled until the box is checked.</span><button class="btn primary">Done</button></div>
</div>'''
    write('consumer-detail.html', page('Application acme-order-sync', 'Applications', body, overlay=overlay, user={'user':'DL','name':'Dana Lee','role':'Consumer Manager','avatar_bg':'#13a8a8'}))

# =====================================================================
# Anomaly response screens (draft 3)
def band_chart(w, h, vals, ymax, yticks, xlabels, band=None, hlines=(), shade=None, markers=(), yfmt=lambda v: f'{v:g}', color='#cf1322'):
    """vals: list; band: (lo_list, hi_list); hlines: (y, color, dash, label); shade: (i0, i1); markers: (i, label, color)."""
    L, R, T, B = 46, 14, 16, 22
    pw, ph = w-L-R, h-T-B
    n = len(vals)
    X = lambda i: L + i*pw/(n-1)
    Y = lambda v: T + ph - min(v, ymax)/ymax*ph
    g = [f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" style="display:block;font-family:Inter;font-size:10.5px">']
    for t in yticks:
        g.append(f'<line x1="{L}" y1="{Y(t):.1f}" x2="{w-R}" y2="{Y(t):.1f}" stroke="#f0f0f0"/>')
        g.append(f'<text x="{L-7}" y="{Y(t)+3.5:.1f}" text-anchor="end" fill="#6b6b6b">{yfmt(t)}</text>')
    for i, lab in enumerate(xlabels):
        x = L + i*pw/(len(xlabels)-1)
        g.append(f'<text x="{x:.1f}" y="{h-5}" text-anchor="middle" fill="#6b6b6b">{lab}</text>')
    if shade:
        i0, i1 = shade
        g.append(f'<rect x="{X(i0):.1f}" y="{T}" width="{X(i1)-X(i0):.1f}" height="{ph}" fill="#ff4d4f" opacity=".07"/>')
    if band:
        lo, hi = band
        up = ' L'.join(f'{X(i):.1f},{Y(v):.1f}' for i, v in enumerate(hi))
        dn = ' L'.join(f'{X(i):.1f},{Y(v):.1f}' for i, v in reversed(list(enumerate(lo))))
        g.append(f'<path d="M{up} L{dn} Z" fill="#8c8c8c" opacity=".16"/>')
    for hl in hlines:
        y, c, dash, lab = hl[:4]
        right = len(hl) > 4
        g.append(f'<line x1="{L}" y1="{Y(y):.1f}" x2="{w-R}" y2="{Y(y):.1f}" stroke="{c}" stroke-width="1.2" stroke-dasharray="{dash}"/>')
        tx, anc, ty = (L+110, 'start', Y(y)-3) if right else (L+4, 'start', Y(y)-4)
        g.append(f'<text x="{tx}" y="{ty:.1f}" text-anchor="{anc}" fill="{c}" font-size="10" paint-order="stroke" stroke="#fff" stroke-width="3">{lab}</text>')
    for (i, lab, c) in markers:
        g.append(f'<line x1="{X(i):.1f}" y1="{T-4}" x2="{X(i):.1f}" y2="{T+ph}" stroke="{c}" stroke-width="1.2" stroke-dasharray="3 2"/>')
        lw = len(lab)*5.4+8
        x0 = X(i)-2 if X(i)-2+lw <= w-R else X(i)+2-lw
        g.append(f'<rect x="{x0:.1f}" y="{T-12}" width="{lw:.1f}" height="13" rx="3" fill="#fff" stroke="{c}" stroke-width=".8"/>')
        g.append(f'<text x="{x0+4:.1f}" y="{T-2.5}" fill="{c}" font-size="9.5">{lab}</text>')
    d = 'M' + ' L'.join(f'{X(i):.1f},{Y(v):.1f}' for i, v in enumerate(vals))
    g.append(f'<path d="{d}" fill="none" stroke="{color}" stroke-width="1.8" stroke-linejoin="round"/>')
    g.append(f'<line x1="{L}" y1="{T+ph}" x2="{w-R}" y2="{T+ph}" stroke="#d9d9d9"/>')
    g.append('</svg>')
    return ''.join(g)

def incident_detail():
    N = 58  # 02:30 .. 03:27, one point per minute; index 33 = 03:03, 41 = 03:11, 44 = 03:14, 54 = 03:24
    r = random.Random(7)
    err = []
    for i in range(N):
        if i < 41: v = 0.28 + r.uniform(-.1, .12)
        elif i == 41: v = 3.1
        elif i == 42: v = 5.2
        elif i < 54: v = 7.0 + r.uniform(-.8, 1.0)
        elif i == 54: v = 4.1
        else: v = 0.45 + r.uniform(-.1, .1)
        err.append(max(v, 0))
    lo = [0.12 + 0.03*math.sin(i/9) for i in range(N)]; hi = [0.62 + 0.05*math.sin(i/9) for i in range(N)]
    xl = ['02:30','02:40','02:50','03:00','03:10','03:20','03:27']
    main_chart = band_chart(690, 164, err, 10, [0, 2.5, 5, 7.5, 10], xl, band=(lo, hi),
        hlines=[(2, '#cf1322', '5 3', 'threshold 2%'), (1, '#8c8c8c', '2 3', 'recovery below 1%', 'r')],
        shade=(41, 57), markers=[(33, 'rev 1843', '#1668dc'), (54, 'rev 1846 rollback', '#1668dc')],
        yfmt=lambda v: f'{v:g}%')
    up = []
    for i in range(N):
        if i < 40: v = 2.1 + r.uniform(-.4, .5)
        elif i < 54: v = 11.5 + r.uniform(-1.2, 2.4)
        else: v = 9.8 - (i-54)*1.3 + r.uniform(-.3, .3)
        up.append(max(v, 1.5))
    ulo = [1.4]*N; uhi = [3.0]*N
    up_chart = band_chart(336, 92, up, 16, [0, 8, 16], ['02:30','02:50','03:10','03:27'], band=(ulo, uhi),
        hlines=[(10, '#cf1322', '5 3', 'read timeout 10 s')], shade=(40, 57), yfmt=lambda v: f'{v:g} s', color='#722ed1')
    rq = [2900 + 180*math.sin(i/8) + r.uniform(-90, 90) for i in range(N)]
    rq_chart = band_chart(336, 92, rq, 4000, [0, 2000, 4000], ['02:30','02:50','03:10','03:27'],
        band=([2500]*N, [3300]*N), yfmt=lambda v: f'{v/1000:g}k' if v else '0', color='#1668dc')

    anomalies = [
      ('orders-5xx', 'threshold', 'errors/rate-5xx', 'route orders-get', '<b style="color:#cf1322">7.8%</b> <span class="muted">vs &gt; 2%</span>', '&ndash;', '03:14'),
      ('upstream-p99-baseline', 'baseline', 'latency/upstream-p99', 'upstream orders', '<b style="color:#cf1322">12.4 s</b> <span class="muted">vs 2.1 s normal</span>', '9.3', '03:14'),
      ('env-5xx-baseline', 'baseline', 'errors/rate-5xx', 'environment', '<b style="color:#cf1322">1.9%</b> <span class="muted">vs 0.2% normal</span>', '5.6', '03:16'),
    ]
    arows = ''.join(f'''<tr><td><div style="font-weight:500">{d}</div><div class="mini"><span class="tag" style="height:17px;font-size:10.5px">{k}</span> <span class="mono" style="font-size:11px">{s}</span></div></td>
      <td>{e}</td><td>{v}</td><td class="num">{z}</td><td><span class="tag red">{icon('warn',11,'#cf1322')}ongoing</span></td><td class="num mono" style="font-size:11.5px">{since}</td></tr>''' for d,k,s,e,v,z,since in anomalies)

    facts = f'''<div class="card" style="margin-bottom:12px"><div class="facts">
      <div><div class="k">First breach &middot; detected</div><div class="v"><b style="font-weight:600">03:11</b> &middot; 03:14 UTC<br><span class="mini">3 of 5 minutes over threshold</span></div></div>
      <div><div class="k">Entities</div><div class="v"><span class="tag blue">route orders-get</span> <span class="tag cyan">upstream orders</span><br><span class="mini">service orders &middot; 1 route affected</span></div></div>
      <div><div class="k">Changed shortly before</div><div class="v"><a class="mono" style="font-size:12px">revision 1843</a> &middot; 03:03<br><span class="mini mono" style="font-size:11px">orders-get timeouts.read-ms 30000 &rarr; 10000</span></div></div>
      <div><div class="k">ServiceNow</div><div class="v"><a style="font-weight:600">INC0012345</a> {icon('ext',12,'#1668dc')} <span class="tag gold">In Progress</span><br><span class="mini">P2 &middot; API Platform &middot; snow-prod</span></div></div>
      <div><div class="k">Response</div><div class="v">rule <span class="mono" style="font-size:12px">servicenow-major</span><br><span class="mini">acknowledged by marco.rossi 03:21</span></div></div>
    </div></div>'''

    # Draft 4.1: LLM root-cause analysis moves to 1.1. The 1.0 screen shows a muted placeholder and,
    # in the space the AI panel used, the correlated change with its diff and rollback shortcut.
    ai = f'''<div class="card" style="margin-bottom:12px;border:1px dashed #d9d9d9;background:#fafafa;box-shadow:none">
      <div style="display:flex;align-items:center;gap:8px;padding:8px 14px;font-size:12.5px;color:#595959">{icon('sparkle',14,'#8c8c8c')}<b style="font-weight:600;white-space:nowrap">AI best guess</b><span class="later">Available in 1.1</span>
        <span class="mini" style="margin-left:auto;white-space:nowrap">optional, off by default</span></div></div>
    <div class="card" style="margin-bottom:12px"><div class="card-head" style="min-height:38px">Correlated change<span class="extra"><span class="mini">changed shortly before, not proven cause</span></span></div>
      <div style="padding:8px 14px 10px;font-size:12.5px">
        <div style="display:flex;align-items:center;gap:8px;margin-bottom:6px"><a class="mono" style="font-size:12px;font-weight:600">revision 1843</a><span class="mini">03:03 &middot; jane.doe &middot; console</span><span class="tag" style="height:18px;margin-left:auto">route.update</span></div>
        <div class="code" style="font-size:11px;padding:4px 0;line-height:1.6"><div style="background:#fff1f0"><span class="ln">-</span> <span class="k">:timeouts</span> {{<span class="k">:read-ms</span> <span class="n">30000</span>}}</div><div style="background:#f6ffed"><span class="ln">+</span> <span class="k">:timeouts</span> {{<span class="k">:read-ms</span> <span class="n">10000</span>}}</div></div>
        <div class="mini" style="margin:6px 0 8px">Upstream p99 since 03:11: 11&ndash;14 s, above the new 10 s read timeout. 94% of 5xx are 504 <span class="mono" style="font-size:11px">upstream.timeout</span>.</div>
        <div style="display:flex;gap:8px"><button class="btn sm">View full diff</button><button class="btn sm">Roll back&hellip;</button><span class="mini" style="margin-left:auto;align-self:center">rolled back as 1846 at 03:24</span></div>
      </div></div>'''

    tl = [
      ('grey', '03:11', 'First breach: orders-get 5xx 3.1%'),
      ('err', '03:14', '<b style="font-weight:600">Incident opened</b> &middot; 2 anomalies &middot; rev 1843 correlated'),
      ('', '03:14', 'Rule <span class="mono" style="font-size:11px">servicenow-major</span> matched: 2 actions'),
      ('ok', '03:15', '<a style="font-weight:600">INC0012345</a> created &middot; P2 &middot; API Platform'),
      ('ok', '03:15', 'Slack message posted &middot; slack-api-oncall'),
      ('warn', '03:16', 'Joined: environment 5xx (z 5.6) &middot; work note added'),
      ('', '03:21', 'Acknowledged by marco.rossi'),
      ('', '03:24', 'Revision 1846: rollback of 1843 by marco.rossi'),
      ('ok', '03:25', 'Recovering: 5xx 0.4% &middot; resolves after 10 min below 1%'),
    ]
    tl_html = ''.join(f'<div class="it {c}" style="padding-bottom:6px"><div style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis"><span class="t">{tm}</span>{txt}</div></div>' for c, tm, txt in tl)

    body = f'''<div class="crumbs">Anomalies<span class="sep">/</span>Incidents<span class="sep">/</span><span class="cur">inc-01J9Q4</span></div>
<div class="page-head"><h1>5xx rate 7.8% on orders-get &middot; upstream orders</h1>
  <span class="tag red">{icon('warn',11,'#cf1322')}High</span><span class="tag gold">{icon('clock',11,'#874d00')}Open &middot; 13 min</span>
  <div class="actions"><button class="btn">{icon('mute',14)}Silence similar</button><button class="btn">{icon('tick',14)}Resolve manually</button><button class="btn">{icon('more',14)}</button></div></div>
{facts}
<div class="grid" style="grid-template-columns:1fr 440px;gap:12px">
 <div>
  <div class="card" style="margin-bottom:12px"><div class="card-head" style="min-height:40px">Detected anomalies <span class="muted" style="font-weight:400">3</span><div class="extra"><span class="mini">grouped by upstream orders and revision 1843</span></div></div>
   <table class="t dense" style="font-size:12.5px"><tr><th>Detector &middot; signal</th><th>Entity</th><th>Value</th><th class="num">z</th><th>State</th><th class="num">Since</th></tr>{arows}</table></div>
  <div class="card" style="margin-bottom:12px"><div class="card-head" style="min-height:40px">5xx rate &middot; orders-get<div class="extra" style="white-space:nowrap"><span class="mini" style="display:flex;align-items:center;gap:4px"><span style="display:inline-block;width:14px;height:8px;background:rgba(140,140,140,.3)"></span>normal range</span><span class="mini" style="display:flex;align-items:center;gap:4px"><span style="display:inline-block;width:14px;height:8px;background:rgba(255,77,79,.18)"></span>anomalous</span><a style="font-size:12px">View as table</a></div></div>
   <div style="padding:8px 10px 4px">{main_chart}</div></div>
  <div class="grid" style="grid-template-columns:1fr 1fr;gap:12px">
   <div class="card"><div class="card-head" style="min-height:36px;font-size:13px">Upstream p99 &middot; orders <span class="tag red" style="margin-left:4px">anomalous</span></div><div style="padding:6px 6px 2px">{up_chart}</div></div>
   <div class="card"><div class="card-head" style="min-height:36px;font-size:13px">Requests / s &middot; orders-get <span class="tag green" style="margin-left:4px">normal</span></div><div style="padding:6px 6px 2px">{rq_chart}</div></div>
  </div>
 </div>
 <div>
  {ai}
  <div class="card"><div class="card-head" style="min-height:36px">Actions taken<div class="extra"><a style="font-size:12px">Outbox (0 pending)</a></div></div>
   <div style="padding:10px 14px 0"><div class="tl">{tl_html}</div></div></div>
 </div>
</div>'''
    write('incident-detail.html', page('Incident inc-01J9Q4', 'Incidents', body, badges={'Incidents': '1'}, rev='1846',
          extra_css='.content{padding-top:12px}.page-head{margin-bottom:10px}.page-head h1{font-size:19px}'))

def anomaly_rules():
    rules = [
      ('servicenow-major', 'severity &ge; high &middot; production', [('ticket','ServiceNow'),('msg','Slack')], False, False, 'live', '4', False),
      ('config-change-heads-up', 'after a config change', [('msg','Slack')], False, False, 'live', '2', False),
      ('credential-stuffing', 'detector credential-stuffing', [('git','Webhook')], True, True, 'dry run', '3', True),
      ('route-owner', 'severity &ge; medium &middot; team tags', [('ticket','ServiceNow')], True, False, 'live', '6', False),
      ('partner-surge', 'surge &middot; plan gold', [('msg','Slack')], False, True, 'approval', '1', False),
      ('low-severity-chat', 'severity low', [('msg','Slack')], False, False, 'live', '11', False),
    ]
    def acts(a, script, traffic):
        st = 'height:18px;font-size:10.5px;padding:0 5px;margin-right:3px'
        s = ''.join(f'<span class="tag" style="{st}">{icon(ic,10)}{lab}</span>' for ic, lab in a)
        if script: s += f'<span class="tag geek" style="{st}">{icon("code",10,"#1d39c4")}script</span>'
        if traffic: s += f'<span class="tag red" style="{st}">{icon("ban",10,"#cf1322")}traffic</span>'
        return s
    rrows = ''.join(f"""<tr class="{'sel' if sel else ''}"><td style="width:30px;padding-right:0"><span class="sw on"></span></td>
      <td style="white-space:nowrap"><div style="font-weight:500;display:flex;align-items:center;gap:6px">{n}<span class="tag {'gold' if m=='dry run' else ('blue' if m=='approval' else 'green')}" style="height:17px;font-size:10.5px;padding:0 5px">{m}</span></div><div class="mini">{w}</div><div style="margin-top:3px">{acts(a,sc,tr)}</div></td><td class="num">{c}</td></tr>""" for n,w,a,sc,tr,m,c,sel in rules)

    K = lambda s: f'<span class="k">{s}</span>'
    S = lambda s: f'<span class="s">&quot;{s}&quot;</span>'
    W = lambda s: f'<span class="kw">{s}</span>'
    N_ = lambda s: f'<span class="n">{s}</span>'
    code = [
      f'({W("ns")} scripts.credential-stuffing',
      f'  ({K(":require")} [befive.script {K(":as")} es]))',
      '',
      f'({W("defn")} respond',
      f'  {S("Block one source IP for 30 min if it causes most auth failures.")}',
      f'  [{{{K(":keys")} [event incident anomalies settings]}}]',
      f'  ({W("let")} [burst (es/first-anomaly anomalies {{{K(":signal")} {K(":ip/auth-failures")}}})',
      f'        ip    (get-in burst [{K(":entity")} {K(":ip")}])',
      f'        share ({K(":share")} burst)]',
      f'    ({W("cond->")} [{{{K(":action")} {K(":slack/post")} {K(":integration")} {S("slack-soc")}',
      f'              {K(":text")} (str {S("Auth-failure burst from ")} ip {S(" (")} (es/pct share) {S(")")})}}]',
      f'      ({W("and")} (= event {K(":opened")}) (&gt; share {N_("0.8")})',
      f'           (not (es/protected-ip? settings ip)))',
      f'      (conj {{{K(":action")} {K(":traffic/block-ip")} {K(":cidr")} (str ip {S("/32")})',
      f'             {K(":duration-minutes")} {N_("30")} {K(":reason")} (str {S("incident ")} ({K(":id")} incident))}}))))',
    ]
    code_html = '\n'.join(f'<span class="ln">{i+1}</span>{l}' for i, l in enumerate(code))
    lab = 'font-size:12px;color:#6b6b6b;margin-bottom:5px'

    editor = f"""<div class="card">
  <div class="card-head" style="min-height:44px;white-space:nowrap"><span class="mono" style="font-size:13.5px;font-weight:600">credential-stuffing</span>
   <span class="tag geek">script</span><span class="tag red">{icon('ban',11,'#cf1322')}traffic action</span><span class="tag gold">dry run</span><span class="mini">priority 80 &middot; edited by lee.wong 2 d ago</span>
   <div class="extra"><button class="btn sm">{icon('play',11)}Test</button><button class="btn sm" style="background:#1668dc;color:#fff;border-color:#1668dc">Save</button></div></div>
  <div style="padding:10px 16px 12px">
   <div class="grid" style="grid-template-columns:1fr 1fr;gap:14px;margin-bottom:9px">
    <div><div style="{lab}">When (all must match)</div>
      <div class="select multi" style="min-height:30px;white-space:nowrap"><span class="chip">detector = credential-stuffing <i>&#10005;</i></span><span class="chip">severity &ge; high <i>&#10005;</i></span></div></div>
    <div><div style="{lab};display:flex;align-items:center;gap:8px;white-space:nowrap">Actions on <span class="seg" style="font-size:11.5px"><span class="on">Opened</span><span>Updated</span><span>Resolved</span></span></div>
      <div style="border:1px solid #f0f0f0;border-radius:6px;padding:5px 10px;font-size:12px;display:flex;align-items:center;gap:8px;white-space:nowrap">{icon('git',13,'#595959')}<span class="mono" style="font-size:11.5px">webhook/post</span><span class="muted">soc-siem</span><span style="margin-left:auto;color:#6b6b6b">+ script actions</span></div></div>
   </div>
   <div class="alert warn" style="padding:5px 12px;margin-bottom:9px;font-size:12.5px;white-space:nowrap"><span class="ic">{icon('warn',14,'#d48806')}</span><div>Traffic actions are in <b>Dry run</b> mode: <span class="mono" style="font-size:11.5px">traffic/block-ip</span> is only recorded, never applied. <a>Automation settings</a></div></div>
   <div style="display:flex;align-items:center;gap:6px;margin-bottom:5px;font-size:12px;color:#6b6b6b;white-space:nowrap">Script <span class="mono" style="font-size:11px">scripts/credential_stuffing.clj</span> &middot; Clojure in the SCI sandbox (no I/O, 500 ms, 128 MiB)<span style="margin-left:auto;display:flex;gap:10px"><a>Context</a><a>API reference</a></span></div>
   <div class="code" style="font-size:11px;line-height:1.55;padding:6px 0;font-variant-ligatures:none">{code_html}</div>
   <div style="border:1px solid #d9f7be;background:#fbfff7;border-radius:6px;margin-top:9px;font-size:12px">
    <div style="display:flex;align-items:center;gap:8px;padding:6px 12px;border-bottom:1px solid #eaf7dc;white-space:nowrap">{icon('check',14,'#52c41a')}<b style="font-weight:600">Test passed</b><span class="muted">against recorded incident inc-01J9M2 (credential stuffing, Sat 26 Sep 02:12)</span><span style="margin-left:auto" class="muted">conditions 2/2 &middot; 18 ms</span></div>
    <div style="display:grid;grid-template-columns:18px 118px 190px 1fr;gap:4px 8px;padding:7px 12px;align-items:center;white-space:nowrap">
      <span>{icon('tick',13,'#389e0d')}</span><span class="mono" style="font-size:11.5px">slack/post</span><span>slack-soc</span><span class="muted">would send (not traffic-affecting)</span>
      <span>{icon('tick',13,'#389e0d')}</span><span class="mono" style="font-size:11.5px">traffic/block-ip</span><span class="mono" style="font-size:11.5px">198.51.100.23/32 &middot; 30 min</span><span><span class="tag gold" style="height:18px">dry run</span> would apply &middot; impact 0.02% &middot; limits OK</span>
    </div>
    <div class="mini" style="padding:5px 12px 6px;border-top:1px solid #eaf7dc;white-space:nowrap">{icon('refresh',11,'#8c8c8c')} Replay, last 7 days: 3 incidents matched &middot; 3 Slack posts &middot; 2 IP blocks (dry run) &middot; 0 on protected networks <a style="margin-left:6px">Details</a></div>
   </div>
  </div></div>"""

    body = f"""<div class="crumbs">Anomalies<span class="sep">/</span><span class="cur">Detectors &amp; rules</span></div>
<div class="page-head"><h1>Detectors &amp; rules</h1><span class="sub">What counts as anomalous, and what BeFive does about it</span>
  <div class="actions"><span class="mini">traffic actions</span><span class="tag gold">Dry run</span><span class="tag blue">Approval</span><button class="btn">{icon('refresh',14)}Simulate</button><button class="btn primary">{icon('plus',14,'#fff')}New rule</button></div></div>
<div class="tabs" style="margin-bottom:10px"><span class="on">Response rules (6)</span><span>Detectors (9)</span></div>
<div class="grid" style="grid-template-columns:352px 1fr;gap:12px">
 <div>
  <div class="card" style="margin-bottom:10px"><div class="card-head" style="min-height:40px">Response rules<div class="extra"><div class="input ph" style="height:26px;width:130px;font-size:12px">{icon('search',12)}Filter</div></div></div>
   <table class="t dense" style="font-size:12.5px"><tr><th></th><th>Rule &middot; mode &middot; when &middot; actions</th><th class="num">7 d matches</th></tr>{rrows}</table>
</div>
  <div class="card" style="padding:9px 12px;font-size:12px;margin-bottom:10px"><div style="font-weight:600;margin-bottom:4px;display:flex;align-items:center;gap:6px">{icon('ban',13,'#cf1322')}Traffic action kinds</div>
   <div style="display:grid;grid-template-columns:1fr auto;gap:3px 8px;white-space:nowrap"><span class="mono" style="font-size:11px">traffic/block-ip</span><span class="tag gold" style="height:17px;font-size:10.5px">dry run</span>
   <span class="mono" style="font-size:11px">traffic/tighten-rate-limit</span><span class="tag blue" style="height:17px;font-size:10.5px">approval</span>
   <span class="mono muted" style="font-size:11px">consumer block, route disable</span><span class="later">later</span></div></div>
  <div class="card" style="padding:10px 12px;font-size:12px;display:flex;align-items:center;gap:8px">{icon('zap',14,'#1668dc')}<span style="white-space:nowrap"><b style="font-weight:600">9 detectors</b> &middot; 412 series &middot; 18 warming up</span><a style="margin-left:auto">View</a></div>
 </div>
 {editor}
</div>"""
    write('anomaly-rules.html', page('Detectors & rules', 'Detectors & rules', body, badges={'Incidents': '1'}, user={'user':'LW','name':'Lee Wong','role':'Automation Manager','avatar_bg':'#d46b08'},
          extra_css='.content{padding-top:12px}.page-head{margin-bottom:6px}.tabs span{padding:6px 0}'))

if __name__ == '__main__':
    overview(); routes(); route_edit(); okta_wizard(); consumer_detail(); incident_detail(); anomaly_rules()
    import screens_d4  # Draft 4 screens (APIs, governance, composites, reports, environments, portal)
    screens_d4.api_effective_policy(); screens_d4.access_request_approval(); screens_d4.composite_builder()
    screens_d4.report_builder(); screens_d4.promotion_wizard(); screens_d4.portal_catalog(); screens_d4.portal_api_detail()
    print('built')
