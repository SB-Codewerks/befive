"""Draft 4 mockups: APIs, governance, composites, reports, environments, developer portal.
Sample data only. Imported and run by build.py; rendered by render.sh."""
import os, random, math
from common import *

HERE = os.path.dirname(os.path.abspath(__file__))
def write(name, html):
    with open(os.path.join(HERE, name), 'w') as f: f.write(html)

CLS = lambda c: f'<span class="cls-tag {c.lower()}">{c}</span>'
LOCK = lambda t='locked': f'<span class="lock">{icon("lock",11,"#ad6800")}{t}</span>'
SRC = lambda kind, label: f'<span class="src {kind}">{label}</span>'

# =====================================================================
def api_effective_policy():
    chain = [('global','Global','3 set','1 locked'),('env','Environment &middot; prod','1 set',''),
             ('api','API &middot; Orders API','4 set','Confidential defaults'),('ver','Version &middot; v2','1 set',''),
             ('path','Path &middot; /orders/{id}','0 set',''),('op','Operation &middot; GET','3 set','this page')]
    chips = []
    for i, (k, lab, n, extra) in enumerate(chain):
        sel = 'box-shadow:0 0 0 2px #b7eb8f;' if k == 'op' else ''
        chips.append(f'<div style="flex:1;min-width:0;border:1px solid #f0f0f0;border-radius:8px;padding:7px 10px;{sel}background:#fff">'
                     f'<div>{SRC(k, lab)}</div><div class="mini" style="margin-top:4px;white-space:nowrap">{n}{" &middot; " + extra if extra else ""}</div></div>')
        if i < len(chain)-1: chips.append(f'<div style="align-self:center;color:#bfbfbf">{icon("chevr",14)}</div>')
    rows = [
      ('Authentication', 'Required &middot; <span class="tag blue" style="height:18px">JWT</span> okta-prod, aud <span class="mono" style="font-size:11px">api://orders</span> <b style="font-weight:500">or</b> <span class="tag cyan" style="height:18px">API key</span>', '', SRC('global','Global') + ' ' + SRC('api','API'), LOCK('Global')),
      ('Claims rule', '<span class="mono" style="font-size:11px">scp has "orders:read" and groups any ["orders-readers" "partners-read"]</span>', '<span class="mono strike" style="font-size:11px">scp has "orders:read"</span> <span class="mini">API</span>', SRC('op','Operation'), '<span class="muted">&mdash;</span>'),
      ('IP rules', 'Allow <span class="mono" style="font-size:11px">10.0.0.0/8</span> + list <span class="mono" style="font-size:11px">partner-egress</span> (12 CIDRs)', '', SRC('env','Environment'), LOCK('Environment')),
      ('Rate limit', '50 req/s per application &middot; burst 100', '<span class="strike">100 req/s per application</span> <span class="mini">API</span>', SRC('ver','Version'), '<span class="muted">&mdash;</span>'),
      ('Logging &amp; redaction', 'Bodies not logged &middot; PII fields masked (<span class="mono" style="font-size:11px">customer.email</span>, <span class="mono" style="font-size:11px">customer.phone</span>)', '', SRC('cls','Confidential'), LOCK('API')),
      ('Caching', 'Per application &middot; TTL 30 s &middot; honors Cache-Control', '<span class="strike">off</span> <span class="mini">Global</span>', SRC('op','Operation') + ' ' + SRC('cls','Confidential'), LOCK('partition')),
      ('Size limits', 'Request 1 MiB &middot; response 10 MiB (streamed)', '', SRC('api','API') + ' ' + SRC('global','Global'), '<span class="muted">&mdash;</span>'),
      ('Identity forwarding', 'Headers + internal JWT (Ed25519, 5 min, aud <span class="mono" style="font-size:11px">orders-svc</span>)', '', SRC('api','API'), '<span class="muted">&mdash;</span>'),
      ('Request validation', 'On &middot; OpenAPI schema, reject with 400', '', SRC('op','Operation'), '<span class="muted">&mdash;</span>'),
      ('Deprecation', 'None on v2 &middot; <span class="muted">v1 deprecated, sunset 2027-03-31</span>', '', SRC('ver','Version'), '<span class="muted">&mdash;</span>'),
    ]
    trs = ''.join(f'''<tr><td style="font-weight:500;white-space:nowrap">{k}</td><td><div>{v}</div>{f'<div style="font-size:11.5px;margin-top:2px">overrides {o}</div>' if o else ''}</td>
      <td style="white-space:nowrap">{s}</td><td style="white-space:nowrap">{l}</td></tr>''' for k, v, o, s, l in rows)
    panel = f'''<div class="card" style="align-self:start"><div class="card-head" style="min-height:42px">Edit at this level<span class="extra">{SRC('op','Operation')}</span></div>
  <div style="padding:12px 16px">
   <div class="fi" style="margin-bottom:12px"><label>Claims rule <span class="opt">(set here)</span></label>
     <div class="code" style="padding:6px 10px;white-space:pre-wrap;font-size:11px;line-height:1.6">[<span class="k">:and</span> [<span class="k">:scope</span> <span class="s">"orders:read"</span>]
      [<span class="k">:claim</span> <span class="s">"groups"</span> <span class="k">:any</span> [<span class="s">"orders-readers"</span> <span class="s">"partners-read"</span>]]]</div>
     <div class="help"><a>Open claims-rule editor</a> &middot; tightens the API-level rule</div></div>
   <div class="fi" style="margin-bottom:12px"><label>Cache TTL <span class="opt">(set here)</span></label>
     <div style="display:flex;gap:8px;align-items:center"><div class="input" style="width:110px">30 <span class="muted" style="margin-left:auto">s</span></div><div class="select" style="flex:1;background:#fafafa;color:#6b6b6b">{icon('lock',12,'#ad6800')} Partition: per application</div></div>
     <div class="help">Partition locked by classification Confidential; you may tighten it to per user.</div></div>
   <div class="fi" style="margin-bottom:12px"><label style="display:flex;align-items:center;gap:8px">Log request and response bodies <span style="margin-left:auto" class="sw"></span>{icon('lock',12,'#ad6800')}</label>
     <div class="help">Locked at API Orders (Confidential defaults). Lower levels cannot turn it on.</div></div>
   <div class="alert err" style="font-size:12px;padding:8px 12px"><span class="ic">{icon('xcircle',14,'#cf1322')}</span><div><b>Save rejected.</b> <span class="mono" style="font-size:11px">:logging :bodies :on</span> would weaken <span class="mono" style="font-size:11px">:logging</span>, locked at API <i>Orders API</i> by classification <i>Confidential</i>. <a>Show lock</a></div></div>
   <div style="display:flex;gap:8px;margin-top:12px"><button class="btn primary">Save at this level</button><button class="btn">Discard</button><span class="mini" style="margin-left:auto;align-self:center">new revision</span></div>
  </div></div>'''
    body = f'''<div class="crumbs">APIs<span class="sep">/</span>Orders API<span class="sep">/</span>v2<span class="sep">/</span><span class="cur">GET /orders/{{id}}</span></div>
<div class="page-head"><h1><span class="m get" style="font-size:12px;height:22px;vertical-align:3px">GET</span> <span class="mono" style="font-size:18px">/v2/orders/{{id}}</span></h1><span class="sub">Get order</span>
  <span class="tag green">Published</span>{CLS('Confidential')}<span class="tag">route orders-get</span>
  <div class="actions"><button class="btn">{icon('play',13)}Test</button><button class="btn">{icon('code',14)}View EDN</button><button class="btn">{icon('copy',13)}Copy b5ctl command</button></div></div>
<div class="tabs" style="margin-bottom:12px"><span>Overview</span><span class="on">Effective policy</span><span>Policies at this level (3)</span><span>Schema</span><span>Traffic</span><span>Consumers</span><span>EDN</span></div>
<div class="card" style="margin-bottom:12px"><div style="display:flex;gap:6px;padding:10px 12px;align-items:stretch">{''.join(chips)}</div>
 <div class="mini" style="padding:0 14px 9px;display:flex;gap:14px;white-space:nowrap">{LOCK('locked')}<span>= lower levels may tighten but not weaken</span><span>Most specific level wins &middot; resolved at compile time (revision 1843)</span><span style="margin-left:auto;display:flex;align-items:center;gap:6px"><span class="sw on"></span>Show overridden values</span></div></div>
<div class="grid" style="grid-template-columns:minmax(0,1fr) 372px;gap:12px">
 <div class="card"><div class="card-head" style="min-height:42px">Effective policy<span class="mini" style="font-weight:400">what the gateway enforces for this operation</span><span class="extra"><span class="mini">10 policy kinds</span></span></div>
  <table class="t dense" style="font-size:12.5px"><thead><tr><th style="width:140px">Policy</th><th>Effective value</th><th>Set at</th><th>Lock</th></tr></thead><tbody>{trs}</tbody></table></div>
 {panel}
</div>'''
    write('api-effective-policy.html', page('GET /orders/{id} · Effective policy', 'APIs', body,
          extra_css='.content{padding-top:12px}.page-head{margin-bottom:8px}.tabs span{padding:7px 0} table.t.dense td{padding:5px 10px} table.t th{padding:7px 10px}'))

# =====================================================================
def access_request_approval():
    q = [
      ('AR-1042','Payments API','v2','northwind-tracking','Northwind Logistics','Restricted','Security review','snow','2 h',True),
      ('AR-1041','Orders API','v2','acme-returns','Acme Corp','Confidential','Your approval','built','5 h',False),
      ('AR-1040','Invoices API','v1','globex-erp','Globex Retail','Confidential','Your approval','built','1 d',False),
      ('AR-1038','Customers API','v1','retail-crm','Example Corp Retail','Confidential','Provisioning','built','1 d',False),
      ('AR-1037','Reports API','v1','finance-bi','Example Corp Finance','Internal','Your approval','built','2 d',False),
      ('AR-1035','Payments API','v2','initech-pos','Initech Payments','Restricted','API owner','snow','3 d',False),
    ]
    def stage(s):
        c = {'Your approval':'blue','Security review':'gold','Provisioning':'cyan','API owner':'gold'}[s]
        return f'<span class="tag {c}" style="height:18px">{s}</span>'
    def chan(c):
        return f'<span class="mini" style="white-space:nowrap">{icon("ticket",11,"#6b6b6b")} ServiceNow</span>' if c == 'snow' else f'<span class="mini">Built-in</span>'
    rows = ''.join(f'''<tr class="{'sel' if sel else ''}"><td style="white-space:nowrap"><div style="font-weight:500;color:#1668dc" class="mono">{i}</div><div class="mini">{age} ago</div></td>
      <td><div style="font-weight:500;white-space:nowrap">{api} <span class="muted" style="font-weight:400">{v}</span> {CLS(cl)}</div><div class="mini" style="white-space:nowrap"><span class="mono" style="font-size:11px">{app}</span> &middot; {org}</div></td>
      <td style="white-space:nowrap"><div>{stage(st)}</div><div style="margin-top:2px">{chan(ch)}</div></td></tr>''' for i,api,v,app,org,cl,st,ch,age,sel in q)
    steps = [
      ('done','1','Submitted in the portal','Devon Park &middot; Northwind Logistics &middot; 09:02'),
      ('done','2','API owner &middot; team-payments <span class="mini">(built-in)</span>','Approved by <b style="font-weight:500">Kim Tran</b> 09:40 &middot; &ldquo;OK for cash-on-delivery flow, Silver plan.&rdquo;'),
      ('wait','3','Security review &middot; InfoSec Access <span class="mini">(ServiceNow)</span>','Awaiting approval in ServiceNow &middot; <a class="mono" style="font-size:11.5px">RITM0010423</a>'),
      ('','4','Provision and write back','Entitlement, bound client ID, then RITM closed with the outcome'),
    ]
    st_html = ''.join(f'<div class="vstep {c}"><div class="n">{icon("tick",12,"#389e0d",2.5) if c=="done" else (icon("clock",12,"#d48806") if c=="wait" else n)}</div><div><div class="tt">{t}</div><div class="ds">{d}</div></div></div>' for c,n,t,d in steps)
    detail = f'''<div class="card">
 <div class="card-head" style="min-height:48px;white-space:nowrap"><span class="mono">AR-1042</span> Payments API v2 {CLS('Restricted')}<span class="tag gold">In progress &middot; step 3 of 4</span>
   <span class="extra"><button class="btn sm">Add note</button><button class="btn sm danger">Cancel request</button></span></div>
 <div style="padding:10px 16px 0">
  <div class="desc" style="grid-template-columns:120px 1fr 110px 1fr;font-size:12.5px">
   <div class="k">Requester</div><div>Devon Park <span class="mini">devon.park@northwind.example</span></div><div class="k">Organization</div><div>Northwind Logistics <span class="tag" style="height:18px">partner</span></div>
   <div class="k">Application</div><div><span class="mono" style="font-size:11.5px">northwind-tracking</span> <span class="mini">machine (client credentials)</span></div><div class="k">Plan</div><div>Silver &middot; 20 req/s &middot; 1 M/month</div>
   <div class="k">Scopes</div><div><span class="tag">payments:authorize</span> <span class="tag">payments:read</span></div><div class="k">Environment</div><div>Prod <span class="mini">(Sandbox granted 2026-09-14)</span></div>
   <div class="k">Justification</div><div style="grid-column:span 3">Authorize cash-on-delivery payments at the doorstep for Example Corp orders.</div>
  </div>
 </div>
 <div class="grid" style="grid-template-columns:minmax(0,1fr) minmax(0,1.05fr);gap:14px;padding:12px 16px 14px">
  <div><div style="font-weight:600;margin-bottom:2px">Approval chain</div><div class="mini" style="margin-bottom:10px">Routing rule <span class="mono" style="font-size:11px">restricted-default</span>: classification Restricted &rarr; API owner, then security</div>
   <div class="vsteps">{st_html}</div>
   <div class="alert info" style="font-size:12px;padding:7px 12px;margin-top:2px"><span class="ic">{icon('info',14,'#1668dc')}</span><div>This step is decided in ServiceNow. The console shows its state; Approve and Reject return when a built-in step is active.</div></div>
  </div>
  <div style="display:flex;flex-direction:column;gap:12px">
   <div style="border:1px solid #f0f0f0;border-radius:8px">
    <div style="display:flex;align-items:center;gap:8px;padding:8px 12px;border-bottom:1px solid #f0f0f0;font-weight:600">{icon('ticket',14,'#595959')}ServiceNow <span class="mini" style="font-weight:400">snow-prod</span><span style="margin-left:auto"><button class="btn sm">{icon('refresh',11)}Poll now</button></span></div>
    <div style="display:grid;grid-template-columns:104px 1fr;gap:3px 8px;padding:8px 12px;font-size:12px">
     <span class="muted">Request item</span><span><a class="mono" style="font-size:11.5px;font-weight:600">RITM0010423</a> {icon('ext',11,'#1668dc')} <span class="mini">in REQ0009931</span></span>
     <span class="muted">State</span><span><span class="tag gold" style="height:18px">Pending approval</span></span>
     <span class="muted">Approval</span><span>requested &middot; InfoSec Access</span>
     <span class="muted">Last event</span><span>09:41 webhook <span class="mono" style="font-size:11px">approval.requested</span> {icon('check',11,'#52c41a')} signed</span>
     <span class="muted">Fallback poll</span><span>every 5 min &middot; next 09:51</span>
    </div></div>
   <div style="border:1px solid #f0f0f0;border-radius:8px">
    <div style="padding:8px 12px;border-bottom:1px solid #f0f0f0;font-weight:600">Provisioning on approval</div>
    <div style="padding:8px 12px;font-size:12.5px;display:flex;flex-direction:column;gap:6px">
     <div><span class="radio on"></span>Bind existing client ID <span class="mono" style="font-size:11px">0oa7nwtracking</span> <span class="mini">(verified in okta-prod)</span></div>
     <div style="color:#8c8c8c"><span class="radio"></span>Create Okta OAuth app (client credentials) <span class="mini">off &middot; <a>Settings</a></span></div>
     <div style="color:rgba(0,0,0,.65)"><span class="radio"></span>Issue API key instead</div>
     <div class="mini" style="margin-top:2px">Entitlement: Payments API v2 &middot; Silver &middot; Prod. Outcome written back to RITM0010423 and the audit log.</div>
    </div></div>
  </div>
 </div>
</div>'''
    body = f'''<div class="crumbs">Access &amp; governance<span class="sep">/</span><span class="cur">Access requests</span></div>
<div class="page-head"><h1>Access requests</h1><span class="tag blue">3 need your approval</span><span class="sub">Requests from the developer portal, routed by API classification</span>
  <div class="actions"><div class="seg"><span class="on">Open (6)</span><span>Completed</span><span>All</span></div><button class="btn">{icon('sliders',14)}Routing rules</button></div></div>
<div class="grid" style="grid-template-columns:486px minmax(0,1fr);gap:12px">
 <div class="card"><div style="display:flex;gap:8px;padding:10px 12px;border-bottom:1px solid #f0f0f0"><div class="input ph" style="flex:1;height:30px">{icon('search',13)}Filter by API, app, organization</div><div class="select" style="height:30px;width:120px;white-space:nowrap">Classification</div></div>
  <table class="t dense" style="font-size:12.5px"><thead><tr><th>Request</th><th>API &middot; classification &middot; requester</th><th>Stage</th></tr></thead><tbody>{rows}</tbody></table>
  <div class="mini" style="padding:8px 12px">Default routing: Public auto-approve &middot; Internal: API owner &middot; Confidential: API owner &middot; Restricted: API owner + security.</div></div>
 {detail}
</div>'''
    write('access-request-approval.html', page('Access requests', 'Access requests', body, badges={'Access requests':'3'},
          extra_css='.content{padding-top:12px}.page-head{margin-bottom:10px}.radio{margin-right:6px;vertical-align:-3px}.desc div{padding:6px 10px}'))

# =====================================================================
def composite_builder():
    # canvas coordinates (px) inside a 760 x 400 canvas
    def node(x, y, w, kind, title, sub, color, sel=False, extra=''):
        b = '2px solid #1668dc' if sel else '1px solid #d9d9d9'
        sh = 'box-shadow:0 0 0 3px rgba(22,104,220,.15);' if sel else 'box-shadow:0 1px 3px rgba(0,0,0,.08);'
        return (f'<div style="position:absolute;left:{x}px;top:{y}px;width:{w}px;background:#fff;border:{b};border-radius:8px;{sh}font-size:11.5px;overflow:hidden">'
                f'<div style="background:{color};padding:3px 8px;font-size:10.5px;font-weight:600;color:rgba(0,0,0,.72);display:flex;align-items:center;gap:4px;white-space:nowrap">{kind}</div>'
                f'<div style="padding:5px 8px 6px"><div class="mono" style="font-size:12px;font-weight:600">{title}</div><div class="mini" style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis">{sub}</div>{extra}</div></div>')
    nodes = [
      node(14, 140, 104, f'{icon("globe",11)}Request', 'GET', 'caller identity', '#f0f0f0'),
      node(146, 140, 128, f'{icon("branch",11)}Call route', 'order', 'orders-get', '#e6f4ff'),
      node(328, 26, 150, f'{icon("branch",11)}Call route', 'customer', 'customers-get', '#e6f4ff'),
      node(328, 140, 150, f'{icon("branch",11)}Call route', 'payment', 'payments-status', '#e6f4ff'),
      node(328, 250, 150, f'{icon("lambda",11)}Lambda upstream', 'shipment', 'shipping-tracker', '#fff7e6', sel=True,
           extra='<div style="margin-top:3px"><span class="tag gold" style="height:16px;font-size:10px">when status = shipped</span></div>'),
      node(526, 140, 118, f'{icon("code",11)}Map response', 'respond', '4 output keys', '#f9f0ff'),
      node(670, 140, 78, f'{icon("send",11)}Response', '200', 'JSON', '#f0f0f0'),
    ]
    edges = [((118,170),(146,170)), ((274,170),(328,56)), ((274,170),(328,170)), ((274,170),(328,284)),
             ((478,56),(526,170)), ((478,170),(526,170)), ((478,284),(526,170)), ((644,170),(670,170))]
    paths = ''.join(f'<path d="M{a[0]},{a[1]} C{(a[0]+b[0])/2},{a[1]} {(a[0]+b[0])/2},{b[1]} {b[0]},{b[1]}" fill="none" stroke="#8c8c8c" stroke-width="1.4" marker-end="url(#ah)"/>' for a, b in edges)
    svg = f'<svg width="760" height="340" style="position:absolute;left:0;top:0"><defs><marker id="ah" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 z" fill="#8c8c8c"/></marker></defs>{paths}</svg>'
    par = '<div style="position:absolute;left:312px;top:12px;width:182px;height:318px;border:1.5px dashed #adc6ff;border-radius:10px;background:rgba(240,245,255,.5)"><div style="position:absolute;top:-10px;left:10px;background:#f0f5ff;border:1px solid #adc6ff;border-radius:9px;font-size:10.5px;padding:0 7px;color:#1d39c4;white-space:nowrap">parallel &middot; 3 steps</div></div>'
    palette = ''.join(f'<span class="tag" style="background:#fff">{icon(i,11)}{t}</span>' for i, t in [('branch','Call route'),('server','Call upstream'),('lambda','Lambda'),('layers','Parallel'),('filter','Condition'),('code','Map')])
    canvas = f'''<div style="position:relative;height:340px;background:#fbfcfd;background-image:radial-gradient(#e3e7ec 1px, transparent 1px);background-size:16px 16px;border-top:1px solid #f0f0f0">
      {svg}{par}{''.join(nodes)}
      <div style="position:absolute;right:10px;bottom:10px;display:flex;gap:6px"><span class="btn sm">{icon('minus',11)}</span><span class="btn sm">100%</span><span class="btn sm">{icon('plus',11)}</span><span class="btn sm">Fit</span></div>
    </div>'''
    side = f'''<div class="card" style="align-self:start"><div class="card-head" style="min-height:42px">Step <span class="mono" style="font-size:13px">shipment</span><span class="tag gold">Lambda upstream</span><span class="extra">{icon('trash',14,'#8c8c8c')}</span></div>
  <div style="padding:10px 14px;font-size:12.5px">
   <div class="fi" style="margin-bottom:10px"><label>Target upstream</label><div class="select" style="white-space:nowrap">{icon('lambda',12,'#ad4e00')}shipping-tracker <span class="mini">us-east-1 &middot; payload 2.0</span></div></div>
   <div class="fi" style="margin-bottom:10px"><label>Run when <span class="opt">(condition, data only)</span></label><div class="input mono" style="font-size:11.5px">(= /order/status "shipped")</div></div>
   <div class="fi" style="margin-bottom:10px"><label>Request mapping</label>
     <div class="code" style="padding:5px 10px;font-size:11px;line-height:1.55;white-space:pre">{{<span class="s">"orderId"</span>  <span class="s">"/order/id"</span>
 <span class="s">"carrier"</span>  <span class="s">"/order/shipping/carrier"</span>}}</div></div>
   <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px">
    <div class="fi" style="margin-bottom:10px"><label>Timeout</label><div class="input">800 <span class="muted" style="margin-left:auto">ms</span></div></div>
    <div class="fi" style="margin-bottom:10px"><label>Output key</label><div class="input mono" style="font-size:11.5px">/shipment</div></div></div>
   <div class="fi" style="margin-bottom:8px"><label>On error</label><div style="white-space:nowrap"><span class="radio on" style="margin-right:14px">Omit, continue</span><span class="radio">Fail with 502</span></div></div>
   <div class="mini">{icon('info',11,'#1668dc')} Runs with the caller&rsquo;s identity and the target&rsquo;s policies.</div>
  </div></div>'''
    trace = [
      ('gateway', 'auth + policy', 'okta-prod JWT &middot; allow', '200', 0, 3),
      ('order', 'orders-get', 'allow &middot; cache miss', '200', 3, 41),
      ('customer', 'customers-get', 'allow &middot; PII masked in log', '200', 41, 65),
      ('payment', 'payments-status', 'allow', '200', 41, 98),
      ('shipment', 'Lambda shipping-tracker', 'condition true', '200', 41, 112),
      ('respond', 'map', '4 keys &middot; 2.4 KB', '200', 112, 114),
    ]
    T = 120; W = 300
    tr = ''.join(f'''<tr><td class="mono" style="font-size:11.5px;font-weight:600">{s}</td><td class="mono" style="font-size:11px">{t}</td><td class="mini">{p}</td><td><span class="tag green" style="height:17px">{c}</span></td>
      <td style="width:{W+20}px"><div style="position:relative;height:12px;width:{W}px;background:#f5f5f5;border-radius:3px"><div style="position:absolute;left:{a/T*W:.0f}px;width:{max((b-a)/T*W,3):.0f}px;top:0;bottom:0;background:{'#fa8c16' if s=='shipment' else '#1668dc'};border-radius:3px;opacity:.8"></div></div></td>
      <td class="num mono" style="font-size:11.5px">{b-a} ms</td></tr>''' for s, t, p, c, a, b in trace)
    resp = f'''<div class="code" style="font-size:10.5px;line-height:1.5;padding:6px 10px;white-space:pre;height:150px">{{<span class="s">"order"</span>    {{<span class="s">"id"</span> <span class="s">"ord_8842"</span>
            <span class="s">"status"</span> <span class="s">"shipped"</span>}}
 <span class="s">"customer"</span> {{<span class="s">"tier"</span> <span class="s">"gold"</span>}}
 <span class="s">"payment"</span>  {{<span class="s">"state"</span> <span class="s">"captured"</span>}}
 <span class="s">"shipment"</span> {{<span class="s">"carrier"</span> <span class="s">"UPS"</span>
            <span class="s">"eta"</span> <span class="s">"2026-09-30"</span>}}}}</div>'''
    body = f'''<div class="crumbs">APIs<span class="sep">/</span>Composites<span class="sep">/</span><span class="cur">order-summary</span></div>
<div class="page-head"><h1 class="mono" style="font-size:18px">order-summary</h1><span class="tag purple">Composite</span><span class="sub"><span class="m get">GET</span> <span class="mono">/v2/orders/{{id}}/summary</span> &middot; Orders API v2</span><span class="tag gold">Unsaved changes</span>
  <div class="actions"><button class="btn">{icon('code',14)}View EDN</button><button class="btn">{icon('play',13)}Run test</button><button class="btn primary">Save and apply</button></div></div>
<div class="grid" style="grid-template-columns:minmax(0,1fr) 360px;gap:12px;margin-bottom:12px">
 <div class="card"><div style="display:flex;align-items:center;gap:6px;padding:8px 12px;white-space:nowrap">{palette}<span class="mini" style="margin-left:auto">6/16 steps &middot; 2 s timeout &middot; 1 MiB max</span></div>{canvas}</div>
 {side}
</div>
<div class="card"><div class="card-head" style="min-height:40px">Test trace<span class="mini" style="font-weight:400"><span class="m get">GET</span> <span class="mono">/v2/orders/ord_8842/summary</span> as <span class="mono">acme-order-sync</span> (Acme Corp) &middot; dry run against revision 1843 + draft</span>
  <span class="extra"><span class="tag green">200 OK</span><span class="mini">total 114 ms &middot; gateway overhead 5 ms</span></span></div>
 <div class="grid" style="grid-template-columns:minmax(0,1fr) 300px;gap:0">
  <table class="t dense" style="font-size:12px"><thead><tr><th>Step</th><th>Target</th><th>Policy &middot; notes</th><th>Status</th><th>Timeline (0&ndash;120 ms)</th><th class="num">Time</th></tr></thead><tbody>{tr}</tbody></table>
  <div style="padding:8px 12px;border-left:1px solid #f0f0f0"><div class="mini" style="margin-bottom:4px">Response body (redacted preview)</div>{resp}</div>
 </div></div>'''
    write('composite-builder.html', page('Composite order-summary', 'Composites', body,
          extra_css='.content{padding-top:12px}.page-head{margin-bottom:10px}.radio{font-size:12.5px} table.t.dense td{padding:4px 10px} table.t th{padding:6px 10px} .fi label{margin-bottom:4px}'))

# =====================================================================
def report_builder():
    days = list(range(1, 28))
    r = random.Random(5)
    orgs = [('Acme Corp', '#1668dc', 115.6), ('Northwind Logistics', '#13a8a8', 44.6), ('Globex Retail', '#722ed1', 36.5), ('Initech Payments', '#fa8c16', 15.3), ('Umbrella Travel', '#eb2f96', 8.6), ('Other (7)', '#bfbfbf', 11.2)]
    # stacked bars, thousands of requests per day
    W, H, L, B, T = 760, 230, 46, 22, 10
    pw, ph = W - L - 14, H - T - B
    ymax = 300
    g = [f'<svg width="{W}" height="{H}" style="display:block;font-family:Inter;font-size:10.5px">']
    for t in [0, 100, 200, 300]:
        y = T + ph - t/ymax*ph
        g.append(f'<line x1="{L}" y1="{y:.1f}" x2="{W-14}" y2="{y:.1f}" stroke="#f0f0f0"/><text x="{L-7}" y="{y+3.5:.1f}" text-anchor="end" fill="#6b6b6b">{t}k</text>')
    bw = pw/len(days)*0.66
    for i, d in enumerate(days):
        x = L + i*pw/len(days) + (pw/len(days)-bw)/2; y = T + ph
        wk = 0.72 if (d % 7) in (5, 6) else 1.0   # Sept 5-6, 12-13 ... weekends (Sat/Sun)
        for name, col, base in orgs:
            v = base*wk*(1 + r.uniform(-.08, .08))
            h = v/ymax*ph; y -= h
            g.append(f'<rect x="{x:.1f}" y="{y:.1f}" width="{bw:.1f}" height="{h:.1f}" fill="{col}"/>')
        if d in (1, 7, 14, 21, 27): g.append(f'<text x="{x+bw/2:.1f}" y="{H-6}" text-anchor="middle" fill="#6b6b6b">Sep {d}</text>')
    g.append(f'<line x1="{L}" y1="{T+ph}" x2="{W-14}" y2="{T+ph}" stroke="#d9d9d9"/></svg>')
    chart = ''.join(g)
    legend = ''.join(f'<span style="white-space:nowrap"><span class="dot" style="background:{c}"></span>{n}</span>' for n, c, _ in orgs)
    rows = [('Acme Corp','Gold','3,120,418','1.8%','0.21%','88 ms','62%'),('Northwind Logistics','Gold','1,204,771','2.4%','0.18%','94 ms','24%'),
            ('Globex Retail','Gold','986,240','1.1%','0.09%','71 ms','20%'),('Initech Payments','Silver','412,905','3.9%','0.44%','131 ms','41%'),
            ('Umbrella Travel','Bronze','233,118','0.9%','0.05%','66 ms','47%'),('Other (7 organizations)','&mdash;','301,662','2.0%','0.12%','80 ms','&mdash;')]
    trs = ''.join(f'<tr><td style="font-weight:500">{a}</td><td><span class="tag" style="height:18px">{b}</span></td><td class="num">{c}</td><td class="num">{d}</td><td class="num">{e}</td><td class="num">{f}</td><td class="num">{h}</td></tr>' for a,b,c,d,e,f,h in rows)
    trs += '<tr style="font-weight:600;background:#fafafa"><td>Total</td><td></td><td class="num">6,259,114</td><td class="num">1.9%</td><td class="num">0.19%</td><td class="num">86 ms</td><td></td></tr>'
    lab = 'font-size:12px;color:#6b6b6b;margin:0 0 4px'
    chip = lambda t: f'<span class="chip">{t} <i>&#10005;</i></span>'
    left = f'''<div style="display:flex;flex-direction:column;gap:12px">
 <div class="card"><div class="card-head" style="min-height:40px">Query</div><div style="padding:10px 14px;font-size:12.5px">
  <div style="{lab}">Dataset</div><div class="select" style="margin-bottom:10px;white-space:nowrap">Usage <span class="mini">1-hour rollups &middot; 13 months</span></div>
  <div style="{lab}">Dimensions</div><div class="select multi" style="margin-bottom:10px">{chip('Organization')}{chip('Day')}</div>
  <div style="{lab}">Metrics</div><div class="select multi" style="margin-bottom:10px">{chip('Requests')}{chip('4xx %')}{chip('5xx %')}{chip('p95')}{chip('Quota used')}</div>
  <div style="{lab}">Filters</div>
  <div style="display:flex;flex-direction:column;gap:5px;margin-bottom:10px">
   <div class="input" style="height:28px;font-size:12px">Environment <b style="font-weight:500">= prod</b></div>
   <div class="input" style="height:28px;font-size:12px">Organization type <b style="font-weight:500">= partner</b></div>
   <div class="input" style="height:28px;font-size:12px;white-space:nowrap">API <b style="font-weight:500">in</b> Orders, Payments, Catalog</div>
   <button class="btn link" style="height:22px;align-self:flex-start">{icon('plus',12)}Add filter</button></div>
  <div style="{lab}">Time range</div><div class="select" style="white-space:nowrap">Sep 1&ndash;27, 2026 (UTC) &middot; month to date</div>
 </div></div>
 <div class="card"><div class="card-head" style="min-height:40px">{icon('calendar',14)}Schedule<span class="extra"><span class="sw on"></span></span></div><div style="padding:10px 14px;font-size:12.5px">
  <div class="kv"><span>Runs</span><span>Monthly, day 1, 06:00 UTC</span></div>
  <div class="kv"><span>Covers</span><span>previous calendar month</span></div>
  <div class="kv"><span>Formats</span><span><span class="tag" style="height:18px">XLSX</span> <span class="tag" style="height:18px">CSV</span></span></div>
  <div class="kv"><span>Email</span><span class="mono" style="font-size:11px">partner-managers@example.com</span></div>
  <div class="kv"><span>S3</span><span class="mono" style="font-size:11px">s3://example-b5-reports/partner/</span></div>
  <div class="mini" style="margin-top:4px">Next run Oct 1, 06:00 UTC &middot; last run Sep 1 delivered</div>
 </div></div>
</div>'''
    body = f'''<div class="crumbs">Operations<span class="sep">/</span>Reports<span class="sep">/</span><span class="cur">Partner usage and errors</span></div>
<div class="page-head"><h1>Partner usage and errors</h1><span class="tag">Saved report</span><span class="tag blue">{icon('calendar',11,'#0958d9')}Monthly</span><span class="sub">owner dana.lee &middot; visible to Consumer Managers, Auditors</span>
  <div class="actions"><button class="btn">{icon('download',14)}Export {icon('chev',11)}</button><button class="btn">Save as&hellip;</button><button class="btn primary">Save</button></div></div>
<div class="grid" style="grid-template-columns:318px minmax(0,1fr);gap:12px">
 {left}
 <div style="display:flex;flex-direction:column;gap:12px">
  <div class="card"><div class="card-head" style="min-height:40px">Requests per day by organization<span class="extra"><div class="seg"><span class="on">Bars</span><span>Lines</span><span>Table only</span></div></span></div>
   <div style="padding:8px 12px 2px">{chart}</div><div class="mini" style="display:flex;gap:14px;padding:0 16px 10px;flex-wrap:wrap">{legend}</div></div>
  <div class="card"><div class="card-head" style="min-height:40px">Table preview<span class="mini" style="font-weight:400">6 rows &middot; top 5 organizations, rest grouped</span><span class="extra"><span class="mini">computed in 0.4 s from 1-hour rollups</span></span></div>
   <table class="t dense" style="font-size:12.5px"><thead><tr><th>Organization</th><th>Plan</th><th class="num">Requests</th><th class="num">4xx</th><th class="num">5xx</th><th class="num">p95</th><th class="num">Quota used</th></tr></thead><tbody>{trs}</tbody></table>
   <div class="mini" style="padding:6px 12px">Quota used: against each organization&rsquo;s September plan quota. Counts are exact: rollups count every request, independent of log sampling.</div></div>
 </div>
</div>'''
    write('report-builder.html', page('Report builder', 'Reports', body, user={'user':'DL','name':'Dana Lee','role':'Consumer Manager','avatar_bg':'#13a8a8'},
          extra_css='.content{padding-top:12px}.page-head{margin-bottom:10px}.kv{padding:3px 0}'))

# =====================================================================
def promotion_wizard():
    envs = [('Dev','rev 3120','#1668dc','bundle b-0928-07 built 09:31',''),('Test','rev 2215','#d48806','Sep 27 &middot; approved by omar.haddad',''),
            ('Prod','rev 1846','#cf1322','this cluster &middot; target','sel')]
    pipe = []
    for i, (n, rv, col, d, sel) in enumerate(envs):
        st = 'border:2px solid #1668dc;box-shadow:0 0 0 3px rgba(22,104,220,.12);' if sel else 'border:1px solid #e8e8e8;'
        pipe.append(f'<div style="flex:1;{st}border-radius:8px;padding:8px 12px;background:#fff;min-width:0"><div style="display:flex;align-items:center;gap:6px;font-weight:600"><span class="dot" style="background:{col}"></span>{n}<span class="mono muted" style="font-weight:400;font-size:11.5px">{rv}</span></div><div class="mini" style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis">{d}</div></div>')
        if i < len(envs)-1:
            pipe.append(f'<div style="align-self:center;display:flex;flex-direction:column;align-items:center;color:#8c8c8c;font-size:10.5px;white-space:nowrap">{icon("arrowr",16,"#8c8c8c")}{"promoted" if i==0 else "this promotion"}</div>')
    pipe.append(f'<div style="width:200px;border:1px dashed #d9d9d9;border-radius:8px;padding:8px 12px;background:#fafafa"><div style="font-weight:600;display:flex;align-items:center;gap:6px"><span class="dot" style="background:#13a8a8"></span>Sandbox <span class="mono muted" style="font-weight:400;font-size:11.5px">rev 988</span></div><div class="mini">follows Prod after apply (mocks on)</div></div>')
    steps = [('done','Source &amp; scope','Test &middot; tag team-orders'),('done','Checks','6 passed, 1 warning'),('done','Diff reviewed','by marco.rossi'),('cur','Approval','1 of 1 needed'),('','Apply','revision 1847')]
    st = ''.join(f'<div class="step {c}"><div class="n">{icon("tick",14,"#1668dc",2.5) if c=="done" else i+1}</div><div><div class="tt">{t}</div><div class="ds">{d}</div></div></div>' for i,(c,t,d) in enumerate(steps))
    def tn(lvl, txt, chg, sel=False):
        c = {'+':('green','create'),'~':('blue','update'),'-':('red','delete'),'':('','')}[chg]
        tag = f'<span class="tag {c[0]}" style="height:17px;font-size:10.5px;margin-left:auto">{c[1]}</span>' if chg else ''
        bg = 'background:#e6f4ff;' if sel else ''
        return f'<div style="display:flex;align-items:center;gap:6px;padding:4px 10px 4px {10+lvl*16}px;font-size:12px;{bg}white-space:nowrap">{txt}{tag}</div>'
    tree = ''.join([
      tn(0, f'{icon("book",12,"#595959")}<b style="font-weight:600">APIs</b>', ''),
      tn(1, 'Orders API v2', '~'),
      tn(2, '<span class="m post">POST</span> <span class="mono" style="font-size:11px">/orders/{id}/returns</span>', '+'),
      tn(2, '<span class="m get">GET</span> <span class="mono" style="font-size:11px">/orders/{id}</span>', '~', True),
      tn(1, 'Orders API v1', '~'),
      tn(2, '<span class="m get">GET</span> <span class="mono" style="font-size:11px">/orders/export</span>', '-'),
      tn(0, f'{icon("share",12,"#595959")}<b style="font-weight:600">Composites</b>', ''),
      tn(1, '<span class="mono" style="font-size:11px">order-summary</span>', '+'),
      tn(0, f'{icon("shield",12,"#595959")}<b style="font-weight:600">Policies</b>', ''),
      tn(1, '<span class="mono" style="font-size:11px">orders-write</span>', '~'),
      tn(0, f'{icon("server",12,"#595959")}<b style="font-weight:600">Upstreams</b>', ''),
      tn(1, '<span class="mono" style="font-size:11px">shipping-tracker</span> <span class="mini">Lambda</span>', '+'),
    ])
    L = lambda n, t, cls='': f'<div class="{cls}" style="display:flex"><span class="ln">{n}</span><span>{t}</span></div>'
    K = lambda x: f'<span class="k">{x}</span>'; S = lambda x: f'<span class="s">"{x}"</span>'; N = lambda x: f'<span class="n">{x}</span>'
    common_top = [f'{{{K(":id")} {S("v2-get-order")}', f' {K(":method")} {K(":get")}', f' {K(":path")} {S("/orders/{id}")}']
    lft = common_top + [(f' {K(":cache")} {{{K(":ttl-s")} {N("30")}', 'del'), f'  {K(":partition")} {K(":application")}}}', f' {K(":validate")} {N("true")}',
           (f' {K(":claims")}', 'del'), (f'  [{K(":scope")} {S("orders:read")}]}}', 'del'), ('', 'del'), ('', 'del')]
    rgt = common_top + [(f' {K(":cache")} {{{K(":ttl-s")} {N("60")}', 'add'), f'  {K(":partition")} {K(":application")}}}', f' {K(":validate")} {N("true")}',
           (f' {K(":claims")}', 'add'), (f'  [{K(":and")} [{K(":scope")} {S("orders:read")}]', 'add'), (f'   [{K(":claim")} {S("groups")} {K(":any")}', 'add'), (f'    [{S("orders-writers")}]]]}}', 'add')]
    mk = lambda rows: ''.join(L(i+1, r[0], r[1]) if isinstance(r, tuple) else L(i+1, r) for i, r in enumerate(rows))
    left = mk(lft); right = mk(rgt)
    checks = [('ok','Signature','Ed25519, Test cluster key <span class="mono" style="font-size:11px">b5-test-2026</span>'),
              ('ok','Schema and references','all objects valid'),('ok','Locks','no locked policy weakened'),
              ('ok','Prod overlay','2 values: orders URL, shipping-tracker role'),('ok','Secret references','all exist in Prod'),
              ('ok','Base revision','Prod still at 1846'),
              ('warn','Deletion still in use','<span class="mono" style="font-size:11px">GET /v1/orders/export</span>: called by 2 apps in 7 days')]
    ch = ''.join(f'<div style="display:flex;gap:8px;padding:2px 0;font-size:12px;white-space:nowrap">{icon("check",14,"#52c41a") if k=="ok" else icon("warn",14,"#faad14")}<div><b style="font-weight:500">{t}</b> <span class="muted">{d}</span></div></div>' for k,t,d in checks)
    body = f'''<div class="crumbs">Operations<span class="sep">/</span>Environments<span class="sep">/</span><span class="cur">Promote from Test</span></div>
<div class="page-head"><h1>Promote to Prod</h1><span class="tag red">production</span><span class="sub">Bundle <span class="mono">b-0928-07</span> from Test &middot; signed, versioned, identical across environments</span>
  <div class="actions"><button class="btn">{icon('download',14)}Download bundle</button><button class="btn">Cancel promotion</button></div></div>
<div style="display:flex;gap:10px;margin-bottom:10px">{''.join(pipe)}</div>
<div class="card" style="margin-bottom:10px"><div class="card-body" style="padding:10px 20px"><div class="steps">{st}</div></div></div>
<div class="grid" style="grid-template-columns:282px minmax(0,1fr) 334px;gap:12px">
 <div class="card"><div class="card-head" style="min-height:40px">Changes<span class="extra"><span class="tag green" style="height:18px">3</span><span class="tag blue" style="height:18px">4</span><span class="tag red" style="height:18px">1</span></span></div>
  <div style="padding:6px 0">{tree}</div><div class="mini" style="padding:6px 12px;border-top:1px solid #f0f0f0">212 objects unchanged &middot; routes regenerate from operations</div>
  <div style="padding:8px 12px;font-size:12px;border-top:1px solid #f0f0f0"><b style="font-weight:600">Prod overlay (not in bundle)</b><div class="mini" style="margin-top:2px"><span class="mono" style="font-size:11px">orders</span> URL <span class="mono" style="font-size:11px">https://orders.prod.internal:8443</span><br><span class="mono" style="font-size:11px">shipping-tracker</span> IAM role <span class="mono" style="font-size:11px">b5-prod-lambda</span></div></div></div>
 <div class="card"><div class="card-head" style="min-height:40px;white-space:nowrap"><span class="m get">GET</span><span class="mono" style="font-size:12.5px">/v2/orders/{{id}}</span><span class="extra"><div class="seg"><span class="on">Side by side</span><span>Unified</span></div></span></div>
  <div style="display:grid;grid-template-columns:1fr 1fr;border-bottom:1px solid #f0f0f0;font-size:11.5px"><div style="padding:5px 12px;background:#fafafa;border-right:1px solid #f0f0f0">Prod now (rev 1846)</div><div style="padding:5px 12px;background:#fafafa">Bundle b-0928-07</div></div>
  <div style="display:grid;grid-template-columns:1fr 1fr" class="diff"><div class="code" style="border:none;border-right:1px solid #f0f0f0;border-radius:0;font-size:11px">{left}</div><div class="code" style="border:none;border-radius:0;font-size:11px">{right}</div></div>
  <div style="padding:8px 12px;font-size:12px;border-top:1px solid #f0f0f0"><b style="font-weight:600">2 changed paths:</b> <span class="mono" style="font-size:11px">:cache :ttl-s</span> 30 &rarr; 60, <span class="mono" style="font-size:11px">:claims</span> adds a group rule &middot; <a>Effective policy after apply</a></div>
  <div style="border-top:1px solid #f0f0f0;padding:8px 12px 6px"><div style="font-weight:600;margin-bottom:2px;display:flex">Checks<span class="mini" style="margin-left:auto;font-weight:400">ran 10:12 &middot; 6 passed, 1 warning</span></div>{ch}</div></div>
 <div style="display:flex;flex-direction:column;gap:12px">
  <div class="card"><div class="card-head" style="min-height:40px">Approval<span class="extra"><span class="tag gold">0 of 1</span></span></div><div style="padding:10px 14px;font-size:12.5px">
   <div class="kv"><span>Requested by</span><span>marco.rossi (Operator) 10:12</span></div>
   <div class="kv"><span>Change ticket</span><span class="mono" style="font-size:11.5px">CHG0031187</span></div>
   <div class="kv"><span>Rule</span><span>1 Prod approver, not the requester</span></div>
   <div style="font-size:12px;color:#595959;margin:6px 0 10px;padding:6px 8px;background:#fafafa;border-radius:6px">&ldquo;Returns endpoint and order-summary composite; v1 export retired per deprecation notice.&rdquo;</div>
   <div style="display:flex;gap:8px"><button class="btn primary" style="flex:1;justify-content:center">{icon('approve',14,'#fff')}Approve and apply&hellip;</button><button class="btn danger">Reject</button></div>
   <div class="mini" style="margin-top:6px">Applies as revision 1847 on 6 nodes; typing <span class="mono">production</span> is required.</div></div></div>
  <div class="card"><div class="card-head" style="min-height:40px">History</div><div style="padding:8px 14px 6px;font-size:12px">
   <div class="tl">
    <div class="it ok"><span class="t">Sep 27</span>Dev &rarr; Test <span class="mono" style="font-size:11px">b-0928-07</span>, omar.haddad</div>
    <div class="it ok"><span class="t">Sep 22</span>Test &rarr; Prod <span class="mono" style="font-size:11px">b-0921-03</span>, rev 1839</div>
    <div class="it grey"><span class="t">Sep 21</span>Test &rarr; Prod <span class="mono" style="font-size:11px">b-0920-01</span>, rejected</div>
   </div><a style="font-size:12px">Environments audit trail</a></div></div>
 </div>
</div>'''
    write('promotion-wizard.html', page('Promote to Prod', 'Environments', body, rev='1846',
          extra_css='.content{padding-top:12px}.page-head{margin-bottom:10px}.diff .del{background:#fff1f0}.diff .add{background:#f6ffed}.diff .code{padding:6px 0;line-height:1.7}.step .tt{font-size:13px}.kv{padding:3px 0}'))

# =====================================================================
# Developer portal (separate SPA, own hostname developer.example.com)
def portal_catalog():
    def facet(title, opts, more=''):
        o = ''.join(f'<div class="opt"><span class="cb {"on" if on else ""}"></span>{lab}<span class="n">{n}</span></div>' for lab, n, on in opts)
        return f'<div class="facet"><h4>{title}<span style="margin-left:auto">{icon("chev",11,"#8c8c8c")}</span></h4>{o}{more}</div>'
    facets = ''.join([
      facet('Environment availability', [('Sandbox', 5, False), ('Prod', 4, False)]),
      facet('Domain', [('Commerce', 2, False), ('Logistics', 1, False), ('Payments', 1, False), ('Finance', 1, False)]),
      facet('Lifecycle', [('Published', 5, False), ('Deprecated versions', 1, False)]),
      facet('Classification', [(CLS('Public'), 0, False), (CLS('Confidential'), 3, False), (CLS('Restricted'), 1, False)]),
      facet('Owner', [('team-orders', 2, False), ('team-logistics', 1, False), ('team-payments', 1, False)], '<div class="mini" style="margin-top:2px"><a>Show 2 more</a></div>'),
      facet('Tags', [('partner', 5, False), ('webhooks', 1, False), ('streaming', 1, False)]),
    ])
    envs = lambda sb, pr: f'<span class="envchip {"on" if sb else "off"}">{icon("check" if sb else "minus",10)}Sandbox</span> <span class="envchip {"on" if pr else "off"}">{icon("check" if pr else "minus",10)}Prod</span>'
    M = lambda t: f'<mark style="background:#fff1b8;padding:0 1px;border-radius:2px">{t}</mark>'
    cards = [
      ('Orders API', 'v2 &middot; v1', [('green','v2 Published'),('gold','v1 Deprecated &middot; sunset 2027-03-31')], 'Commerce', 'team-orders', 'Confidential',
       f'Create, track, cancel, and return customer {M("orders")}. Includes the order summary composite and exports.', envs(True, True), 15, ('green', f'{icon("check",11,"#237804")}Subscribed &middot; northwind-tracking')),
      ('Order Events', 'v1', [('green','v1 Published')], 'Commerce', 'team-orders', 'Confidential',
       f'Server-Sent Events stream of {M("order")} status changes, with replay from a cursor.', envs(True, True), 2, ('blue', 'Request access')),
      ('Shipping Tracking API', 'v1', [('green','v1 Published')], 'Logistics', 'team-logistics', 'Confidential',
       f'Carrier tracking and ETA for shipped {M("orders")}; served by AWS Lambda.', envs(True, True), 4, ('green', f'{icon("check",11,"#237804")}Subscribed &middot; northwind-tracking')),
      ('Payments API', 'v2', [('green','v2 Published')], 'Payments', 'team-payments', 'Restricted',
       f'Authorize, capture, and refund payments for {M("orders")}. Requires security review.', envs(True, True), 9, ('gold', f'{icon("clock",11,"#874d00")}Access requested &middot; AR-1042 in security review')),
      ('Invoices API', 'v1', [('green','v1 Published')], 'Finance', 'team-billing', 'Confidential',
       f'Invoices and credit notes, searchable by customer or {M("order")} number.', envs(True, False), 6, ('blue', 'Request access')),
    ]
    ch = ''
    for name, vers, lc, dom, own, cl, desc, env, ops, acc in cards:
        lct = ' '.join(f'<span class="tag {c}" style="height:19px">{t}</span>' for c, t in lc)
        act = f'<span class="tag {acc[0]}" style="height:22px;font-size:12px">{acc[1]}</span>' if acc[0] != 'blue' else f'<button class="btn sm" style="height:26px">{acc[1]}</button>'
        ch += f'''<div class="card" style="padding:12px 16px;margin-bottom:10px">
  <div style="display:flex;align-items:center;gap:8px"><span style="font-size:15px;font-weight:600;color:#0b1f33">{name}</span>{lct}{CLS(cl)}<span style="margin-left:auto">{act}</span></div>
  <div style="font-size:13px;color:rgba(0,0,0,.72);margin:4px 0 7px">{desc}</div>
  <div class="mini" style="display:flex;align-items:center;gap:12px;white-space:nowrap"><span>{env}</span><span>{dom}</span><span>owner {own}</span><span>{ops} operations</span><span>updated Sep {[24,19,11,26,2][cards.index((name, vers, lc, dom, own, cl, desc, env, ops, acc))]}</span></div></div>'''
    rail = f'''<div class="card" style="margin-bottom:12px"><div class="card-head" style="min-height:40px">Your applications</div><div style="padding:8px 14px;font-size:12.5px">
   <div class="kv"><span class="mono" style="font-size:12px;color:rgba(0,0,0,.88)">northwind-tracking</span><span>3 APIs &middot; Prod</span></div>
   <div class="kv"><span class="mono" style="font-size:12px;color:rgba(0,0,0,.88)">northwind-sandbox</span><span>5 APIs &middot; Sandbox</span></div>
   <a style="font-size:12.5px">Manage applications</a></div></div>
 <div class="card"><div class="card-head" style="min-height:40px">Changelog<span class="extra"><a style="font-size:12px">All</a></span></div><div style="padding:8px 14px 4px;font-size:12.5px">
   <div style="padding:5px 0;border-bottom:1px solid #f0f0f0"><div class="mini">Sep 26 &middot; Payments API</div>v2.4: idempotency keys on capture</div>
   <div style="padding:5px 0;border-bottom:1px solid #f0f0f0"><div class="mini">Sep 24 &middot; Orders API</div>v2.3: <span class="mono" style="font-size:11px">POST /orders/{{id}}/returns</span> added</div>
   <div style="padding:5px 0;border-bottom:1px solid #f0f0f0"><div class="mini">Sep 24 &middot; Orders API <span class="tag gold" style="height:16px;font-size:10px">deprecation</span></div>v1 deprecated; sunset 2027-03-31</div>
   <div style="padding:5px 0"><div class="mini">Sep 19 &middot; Order Events</div>Replay cursor retention now 72 h</div></div></div>'''
    body = f'''<div style="display:flex;align-items:flex-end;gap:12px;margin-bottom:14px"><div><h1 style="margin:0;font-size:22px;font-weight:600;color:#0b1f33">API catalog</h1>
  <div class="muted" style="font-size:13px">23 APIs visible to you &middot; Prod and Sandbox</div></div></div>
<div style="display:grid;grid-template-columns:250px minmax(0,1fr) 280px;gap:16px">
 <div class="card" style="align-self:start"><div style="padding:10px 16px;border-bottom:1px solid #f0f0f0;display:flex;align-items:center;font-weight:600">Filters<a style="margin-left:auto;font-weight:400;font-size:12px">Clear</a></div>{facets}</div>
 <div>
  <div style="display:flex;gap:10px;margin-bottom:10px;align-items:center"><div class="input focus" style="flex:1;height:38px;font-size:14px">{icon('search',15,'#8c8c8c')}orders</div><div class="select" style="width:170px;height:38px;white-space:nowrap">Sort: relevance</div></div>
  <div style="display:flex;gap:8px;align-items:center;margin-bottom:10px;font-size:12.5px"><span class="muted">5 results for &ldquo;orders&rdquo;</span><span class="muted" style="margin-left:auto">Matches in names, descriptions, operations, and schema fields</span></div>
  {ch}
 </div>
 <div>{rail}</div>
</div>'''
    write('portal-catalog.html', portal_page('API catalog · Example Corp Developer Portal', 'APIs', body))

# =====================================================================
def portal_api_detail():
    ops = [('Orders', [('GET','/orders','List orders',''),('POST','/orders','Create order',''),('GET','/orders/{id}','Get order','sel'),('DELETE','/orders/{id}','Cancel order',''),
                      ('GET','/orders/{id}/summary','Order summary','new'),('POST','/orders/{id}/returns','Create return','new'),('GET','/orders/export','Export orders','async')]),
           ('Events', [('GET','/orders/events','Order status stream','')])]
    nav = ''
    for grp, items in ops:
        nav += f'<div class="mini" style="text-transform:uppercase;letter-spacing:.5px;padding:10px 14px 4px">{grp}</div>'
        for m, pth, nm, fl in items:
            bg = 'background:#e6f4ff;border-left:2px solid #1668dc;' if fl == 'sel' else 'border-left:2px solid transparent;'
            badge = '<span class="tag geek" style="height:16px;font-size:10px;padding:0 4px">Async 202</span>' if fl == 'async' else ('<span class="tag green" style="height:16px;font-size:10px;padding:0 4px">New</span>' if fl == 'new' else '')
            nav += f'<div style="padding:5px 12px;{bg}"><div style="display:flex;align-items:center;gap:6px;white-space:nowrap"><span class="m {m.lower()}" style="min-width:44px;justify-content:center">{m}</span><span class="mono" style="font-size:11px;overflow:hidden;text-overflow:ellipsis">{pth}</span></div><div class="mini" style="padding-left:50px;display:flex;gap:5px;align-items:center;white-space:nowrap">{nm}{badge}</div></div>'
    sch = [
      (0,'id','string','required','Order ID, for example ord_8842'),
      (0,'status','enum','required','pending &middot; paid &middot; shipped &middot; delivered &middot; cancelled'),
      (0,'customer','object','','Customer reference'),
      (1,'id','string','required',''),
      (1,'email','string (email)','','<span class="tag red" style="height:16px;font-size:10px">PII</span> masked in gateway logs'),
      (0,'items','array&lt;OrderItem&gt;','required','1 to 200 line items'),
      (0,'total','Money','required','amount (decimal string), currency (ISO 4217)'),
      (0,'createdAt','string (date-time)','required','RFC 3339, UTC'),
    ]
    srows = ''.join(f'<div style="padding-left:{8+l*18}px;border-bottom:1px dashed #f0f0f0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">{"&#9492; " if l else ""}<span class="f">{f}</span> <span class="ty">{t}</span>{f"<span class=rq>{r}</span>" if r else ""}<span class="d">{d}</span></div>' for l,f,t,r,d in sch)
    doc = f'''<div class="card" style="padding:14px 18px">
  <div style="display:flex;align-items:center;gap:8px"><span class="m get" style="font-size:12px;height:22px">GET</span><span class="mono" style="font-size:15px;font-weight:600">/v2/orders/{{id}}</span><span style="margin-left:auto" class="mini">operationId <span class="mono">getOrder</span></span></div>
  <div style="font-size:16px;font-weight:600;margin:6px 0 2px;color:#0b1f33">Get an order</div>
  <div style="font-size:13px;color:rgba(0,0,0,.72);margin-bottom:10px">Returns one order with its line items and totals. Responses are cached per application for up to 30 seconds; send <span class="mono">Cache-Control: no-cache</span> to bypass.</div>
  <div style="display:grid;grid-template-columns:120px 1fr;gap:5px 10px;font-size:12.5px;margin-bottom:12px">
   <span class="muted">Authentication</span><span>OAuth 2.0 client credentials (Okta), scope <span class="tag">orders:read</span>, or API key in <span class="mono">X-API-Key</span></span>
   <span class="muted">Your limits</span><span>Gold plan: 100 req/s, 5 M/month &middot; <span class="mono">RateLimit</span> headers on every response</span>
  </div>
  <div style="font-weight:600;font-size:13px;margin-bottom:4px">Path parameters</div>
  <table class="t dense" style="font-size:12px;margin-bottom:12px;border:1px solid #f0f0f0;border-radius:6px"><thead><tr><th>Name</th><th>Type</th><th>Description</th></tr></thead><tbody><tr><td class="mono" style="font-size:11.5px">id <span style="color:#cf1322;font-size:10px">required</span></td><td class="mono" style="font-size:11.5px">string</td><td>Order ID (prefix <span class="mono">ord_</span>)</td></tr></tbody></table>
  <div style="display:flex;align-items:center;gap:6px;margin-bottom:6px"><span style="font-weight:600;font-size:13px">Responses</span><span class="tag green" style="height:19px">200</span><span class="tag" style="height:19px">401</span><span class="tag" style="height:19px">403</span><span class="tag" style="height:19px">404</span><span class="tag" style="height:19px">429</span></div>
  <div class="mini" style="margin-bottom:4px">200 &middot; <span class="mono">application/json</span> &middot; schema <b class="mono" style="font-weight:600;color:#0b1f33">Order</b></div>
  <div class="schema" style="border:1px solid #f0f0f0;border-radius:6px;padding:4px 6px">{srows}</div>
 </div>'''
    tryit = f'''<div class="card" style="align-self:start"><div class="card-head" style="min-height:42px">{icon('play',14)}Try it<span class="extra"><span class="tag cyan">{icon('lock',11,'#006d75')}Sandbox</span></span></div>
  <div style="padding:10px 14px;font-size:12.5px">
   <div class="fi" style="margin-bottom:9px"><label style="font-size:12px">Target</label><div class="select" style="background:#fafafa;white-space:nowrap">https://sandbox.api.example.com</div><div class="help">Production is not offered for this API.</div></div>
   <div class="fi" style="margin-bottom:9px"><label style="font-size:12px">Application</label><div class="select" style="white-space:nowrap"><span class="mono" style="font-size:12px">northwind-sandbox</span> <span class="mini">sandbox credentials</span></div></div>
   <div class="fi" style="margin-bottom:9px"><label style="font-size:12px">Authorization</label><div class="input" style="white-space:nowrap">{icon('check',13,'#52c41a')}<span>Okta token &middot; <span class="mono" style="font-size:11.5px">orders:read</span> &middot; 54 min left</span></div></div>
   <div class="fi" style="margin-bottom:10px"><label style="font-size:12px"><span class="req">*</span>id</label><div class="input mono" style="font-size:12px">ord_8842</div></div>
   <div style="display:flex;gap:8px;align-items:center;margin-bottom:10px"><button class="btn primary">{icon('send',13,'#fff')}Send</button><button class="btn">Copy as curl</button><span class="mini" style="margin-left:auto;display:flex;align-items:center;gap:5px"><span class="sw"></span>Mock</span></div>
   <div style="border-top:1px solid #f0f0f0;padding-top:8px"><div style="display:flex;align-items:center;gap:8px;margin-bottom:5px"><span class="tag green">200 OK</span><span class="mini">43 ms &middot; 1.1 KB</span></div>
   <div class="code" style="font-size:10.5px;line-height:1.5;padding:6px 10px;white-space:pre">RateLimit: limit=100, remaining=99, reset=1
RateLimit-Policy: 100;w=1
X-Request-Id: 01J9S2K4C8V7</div>
   <div class="code" style="font-size:10.5px;line-height:1.5;padding:6px 10px;white-space:pre;margin-top:6px">{{ <span class="s">"id"</span>: <span class="s">"ord_8842"</span>,
  <span class="s">"status"</span>: <span class="s">"shipped"</span>,
  <span class="s">"customer"</span>: {{ <span class="s">"id"</span>: <span class="s">"cus_2291"</span> }},
  <span class="s">"total"</span>: {{ <span class="s">"amount"</span>: <span class="s">"184.20"</span>, <span class="s">"currency"</span>: <span class="s">"USD"</span> }} }}</div></div>
  </div></div>'''
    head = f'''<div class="crumbs" style="margin-bottom:6px">APIs<span class="sep">/</span><span class="cur">Orders API</span></div>
<div style="display:flex;align-items:center;gap:10px;margin-bottom:8px"><h1 style="margin:0;font-size:22px;font-weight:600;color:#0b1f33">Orders API</h1>
 <div class="select" style="height:30px;width:auto;gap:8px;white-space:nowrap">v2 <span class="tag green" style="height:18px">Published</span></div>{CLS('Confidential')}
 <span class="mini">owner team-orders &middot; Commerce</span>
 <span style="margin-left:auto;display:flex;align-items:center;gap:6px"><span class="mini">Available in</span><span class="envchip on">{icon('check',10)}Sandbox</span><span class="envchip on">{icon('check',10)}Prod</span><span class="tag green" style="height:24px">{icon('check',11,'#237804')}Subscribed &middot; northwind-tracking</span></span></div>
<div class="alert warn" style="padding:6px 12px;font-size:12.5px;margin-bottom:10px"><span class="ic">{icon('warn',14,'#d48806')}</span><div><b>v1 is deprecated</b> and stops working on <b>2027-03-31</b> (responses carry <span class="mono">Deprecation</span> and <span class="mono">Sunset</span> headers). Your application <span class="mono">northwind-tracking</span> called v1 412 times this week. <a>Migration guide</a> &middot; <a>Changelog</a></div></div>
<div class="tabs" style="margin-bottom:12px"><span class="on">Documentation</span><span>Changelog</span><span>Guides</span><span>Access</span><span>Download OpenAPI</span></div>'''
    body = f'''{head}
<div style="display:grid;grid-template-columns:250px minmax(0,1fr) 400px;gap:14px">
 <div class="card" style="align-self:start;padding-bottom:6px"><div class="input ph" style="margin:10px 12px 2px;height:30px">{icon('search',13)}Filter operations</div>{nav}</div>
 {doc}
 {tryit}
</div>'''
    write('portal-api-detail.html', portal_page('Orders API · Example Corp Developer Portal', 'APIs', body,
          extra_css='.p-content{padding-top:14px} table.t.dense td{padding:5px 10px} table.t th{padding:6px 10px}'))

if __name__ == '__main__':
    api_effective_policy(); access_request_approval(); composite_builder(); report_builder(); promotion_wizard(); portal_catalog(); portal_api_detail()
    print('built draft 4 screens')
