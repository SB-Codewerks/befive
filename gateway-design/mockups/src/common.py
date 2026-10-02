"""Shared pieces for the static console mockups. Generates HTML; sample data only."""
import math, random

ICONS = {
 'grid':'<rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/>',
 'server':'<rect x="2" y="2" width="20" height="8" rx="2"/><rect x="2" y="14" width="20" height="8" rx="2"/><line x1="6" y1="6" x2="6.01" y2="6"/><line x1="6" y1="18" x2="6.01" y2="18"/>',
 'branch':'<line x1="6" y1="3" x2="6" y2="15"/><circle cx="18" cy="6" r="3"/><circle cx="6" cy="18" r="3"/><path d="M18 9a9 9 0 0 1-9 9"/>',
 'shield':'<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>',
 'users':'<path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>',
 'layers':'<polygon points="12 2 2 7 12 12 22 7 12 2"/><polyline points="2 17 12 22 22 17"/><polyline points="2 12 12 17 22 12"/>',
 'key':'<circle cx="7.5" cy="15.5" r="5.5"/><path d="M11.4 11.6 21 2m-4 4 3 3m-6-0 2 2"/>',
 'lock':'<rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/>',
 'play':'<polygon points="6 3 20 12 6 21 6 3"/>',
 'cpu':'<rect x="4" y="4" width="16" height="16" rx="2"/><rect x="9" y="9" width="6" height="6"/><line x1="9" y1="1" x2="9" y2="4"/><line x1="15" y1="1" x2="15" y2="4"/><line x1="9" y1="20" x2="9" y2="23"/><line x1="15" y1="20" x2="15" y2="23"/><line x1="20" y1="9" x2="23" y2="9"/><line x1="20" y1="14" x2="23" y2="14"/><line x1="1" y1="9" x2="4" y2="9"/><line x1="1" y1="14" x2="4" y2="14"/>',
 'file':'<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/>',
 'code':'<polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/>',
 'sliders':'<line x1="4" y1="21" x2="4" y2="14"/><line x1="4" y1="10" x2="4" y2="3"/><line x1="12" y1="21" x2="12" y2="12"/><line x1="12" y1="8" x2="12" y2="3"/><line x1="20" y1="21" x2="20" y2="16"/><line x1="20" y1="12" x2="20" y2="3"/><line x1="1" y1="14" x2="7" y2="14"/><line x1="9" y1="8" x2="15" y2="8"/><line x1="17" y1="16" x2="23" y2="16"/>',
 'search':'<circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>',
 'bell':'<path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/>',
 'help':'<circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/>',
 'plus':'<line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>',
 'check':'<path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/>',
 'tick':'<polyline points="20 6 9 17 4 12"/>',
 'warn':'<path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>',
 'info':'<circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/>',
 'xcircle':'<circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/>',
 'copy':'<rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>',
 'download':'<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/>',
 'upload':'<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/>',
 'more':'<circle cx="12" cy="12" r="1.2"/><circle cx="19" cy="12" r="1.2"/><circle cx="5" cy="12" r="1.2"/>',
 'refresh':'<polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>',
 'eye':'<path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>',
 'trash':'<polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>',
 'activity':'<polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>',
 'ext':'<path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><polyline points="15 3 21 3 21 9"/><line x1="10" y1="14" x2="21" y2="3"/>',
 'filter':'<polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3"/>',
 'chev':'<polyline points="6 9 12 15 18 9"/>',
 'chevr':'<polyline points="9 18 15 12 9 6"/>',
 'drag':'<circle cx="9" cy="6" r="1"/><circle cx="15" cy="6" r="1"/><circle cx="9" cy="12" r="1"/><circle cx="15" cy="12" r="1"/><circle cx="9" cy="18" r="1"/><circle cx="15" cy="18" r="1"/>',
 'globe':'<circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/>',
 'clock':'<circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>',
 'edit':'<path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>',
 'pause':'<rect x="6" y="4" width="4" height="16"/><rect x="14" y="4" width="4" height="16"/>',
 'zap':'<polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>',
 'siren':'<path d="M7 18v-6a5 5 0 0 1 10 0v6"/><path d="M5 21a1 1 0 0 1 1-1h12a1 1 0 0 1 1 1v1H5z"/><path d="M21 12h1M18.5 4.5 18 5M2 12h1M12 2v1M4.9 4.9l.7.7"/>',
 'mute':'<path d="M11 5 6 9H2v6h4l5 4V5z"/><line x1="23" y1="9" x2="17" y2="15"/><line x1="17" y1="9" x2="23" y2="15"/>',
 'approve':'<path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>',
 'sparkle':'<path d="M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z"/><path d="M19 16l.8 2.2L22 19l-2.2.8L19 22l-.8-2.2L16 19l2.2-.8z"/>',
 'ticket':'<path d="M3 7a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v3a2 2 0 0 0 0 4v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-3a2 2 0 0 0 0-4z"/><line x1="13" y1="5" x2="13" y2="19" stroke-dasharray="2 2"/>',
 'msg':'<path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>',
 'mail':'<rect x="2" y="4" width="20" height="16" rx="2"/><polyline points="22 6 12 13 2 6"/>',
 'ban':'<circle cx="12" cy="12" r="10"/><line x1="4.93" y1="4.93" x2="19.07" y2="19.07"/>',
 'git':'<circle cx="12" cy="12" r="3"/><line x1="3" y1="12" x2="9" y2="12"/><line x1="15" y1="12" x2="21" y2="12"/>',
 # Draft 4 navigation and screens
 'book':'<path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>',
 'bookopen':'<path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/>',
 'share':'<circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/>',
 'tag':'<path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/><line x1="7" y1="7" x2="7.01" y2="7"/>',
 'database':'<ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"/><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/>',
 'hourglass':'<path d="M6 2h12M6 22h12M7 2c0 5 5 6 5 10s-5 5-5 10M17 2c0 5-5 6-5 10s5 5 5 10"/>',
 'building':'<rect x="4" y="2" width="16" height="20" rx="1"/><path d="M9 22v-4h6v4M8 6h.01M12 6h.01M16 6h.01M8 10h.01M12 10h.01M16 10h.01M8 14h.01M12 14h.01M16 14h.01"/>',
 'box':'<path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/><polyline points="3.27 6.96 12 12.01 20.73 6.96"/><line x1="12" y1="22.08" x2="12" y2="12"/>',
 'inbox':'<polyline points="22 12 16 12 14 15 10 15 8 12 2 12"/><path d="M5.45 5.11L2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z"/>',
 'barchart':'<line x1="12" y1="20" x2="12" y2="10"/><line x1="18" y1="20" x2="18" y2="4"/><line x1="6" y1="20" x2="6" y2="16"/>',
 'envs':'<rect x="2" y="3" width="6" height="6" rx="1"/><rect x="16" y="3" width="6" height="6" rx="1"/><rect x="9" y="15" width="6" height="6" rx="1"/><path d="M8 6h8M5 9v3a3 3 0 0 0 3 3h1M19 9v3a3 3 0 0 1-3 3h-1"/>',
 'lambda':'<path d="M5 21l6-10.5M8 3h2.5l8 18"/>',
 'arrowr':'<line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/>',
 'calendar':'<rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/>',
 'user':'<path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>',
 'send':'<line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/>',
 'unlock':'<rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 9.9-1"/>',
 'minus':'<line x1="5" y1="12" x2="19" y2="12"/>',
}

# BeFive mark (logo concept 3, "transit"): copied verbatim from /workspace/befive-logo/concept-3/icon-small-dark.svg
# into assets/ and inlined; only the outer width/height are changed so it fits the 56 px sider header.
import re as _re, os as _os
_MARK_SVG = open(_os.path.join(_os.path.dirname(_os.path.abspath(__file__)), 'assets', 'befive-icon-small-dark.svg')).read()
_MARK_LIGHT = open(_os.path.join(_os.path.dirname(_os.path.abspath(__file__)), 'assets', 'befive-icon-small.svg')).read()
LOGO_MARK_LIGHT = '<span class="mark-svg">' + _re.sub(r'width="32" height="32"', 'width="30" height="30"', _MARK_LIGHT, count=1) + '</span>'
LOGO_MARK = '<span class="mark-svg">' + _re.sub(r'width="32" height="32"', 'width="30" height="30"', _MARK_SVG, count=1) + '</span>'

def icon(name, size=16, color='currentColor', sw=2, style=''):
    return (f'<svg width="{size}" height="{size}" viewBox="0 0 24 24" fill="none" stroke="{color}" '
            f'stroke-width="{sw}" stroke-linecap="round" stroke-linejoin="round" style="{style}">{ICONS[name]}</svg>')

# Draft 4 information architecture (03 section 3.1). Groups collapse (Ant Design inline Menu);
# the open/closed state is a per-user preference. Mockups open the active group and fill the
# remaining height greedily in group order, as a returning user's persisted state might look.
NAV = [
 (None, [('grid','Overview',None)]),
 ('APIs', [('book','APIs','14'),('share','Composites','3'),('bookopen','Data dictionary',None),('tag','Classification','4')]),
 ('Traffic', [('server','Services & upstreams',None),('branch','Routes','86'),('shield','Policies','23'),('database','Caching',None),('hourglass','Async jobs',None),('play','Route tester',None)]),
 ('Access & governance', [('building','Organizations','41'),('users','Consumers','312'),('box','Applications','527'),('inbox','Access requests',None),('layers','Plans','6'),('key','Identity providers','3'),('lock','Certificates','9')]),
 ('Anomalies', [('siren','Incidents',None),('zap','Detectors & rules','6'),('mute','Silences',None),('approve','Approvals',None)]),
 ('Operations', [('barchart','Reports','18'),('envs','Environments','4'),('cpu','Gateway nodes','6'),('file','Audit log',None),('code','Config as code',None),('sliders','Settings',None)]),
]
DEFAULT_BADGES = {'Access requests': '3'}
MAX_OPEN_ITEMS = 16

def sider(active, badges=None, open_groups=None):
    b = dict(DEFAULT_BADGES); b.update(badges or {})
    act_group = next((g for g, items in NAV if any(l == active for _, l, _ in items)), None)
    if open_groups is None:
        open_groups = {act_group} if act_group else set(); used = sum(len(i) for g, i in NAV if g in open_groups)
        for g, items in NAV:
            if g and g not in open_groups and used + len(items) <= MAX_OPEN_ITEMS:
                open_groups.add(g); used += len(items)
    out = ['<aside class="sider"><div class="logo">' + LOGO_MARK + '<div>BeFive<small>Console</small></div></div><nav class="menu">']
    for group, items in NAV:
        is_open = group is None or group in open_groups
        if group:
            hidden = [b[l] for _, l, _ in items if l in b] if not is_open else []
            summ = f'<span class="badge" style="margin-left:auto;margin-right:6px">{sum(int(x) for x in hidden)}</span>' if hidden else '<span style="margin-left:auto"></span>'
            chev = icon('chev' if is_open else 'chevr', 11, 'rgba(255,255,255,.38)')
            out.append(f'<div class="group{" closed" if not is_open else ""}"><span>{group}</span>{summ}{chev}</div>')
        if not is_open: continue
        for ic, label, count in items:
            cls = 'item active' if label == active else 'item'
            c = f'<span class="count">{count}</span>' if count else ''
            if label in b: c = f'<span class="badge" style="margin-left:auto">{b[label]}</span>'
            out.append(f'<div class="{cls}">{icon(ic)}<span>{label}</span>{c}</div>')
    out.append('</nav><div class="sider-foot">v1.0.0 &middot; license: Example Corp<br>maintenance through 2027-09-30</div></aside>')
    return ''.join(out)

def header(user='JD', rev='1843', name='Jane Doe', role='Administrator', avatar_bg='#722ed1'):
    return f'''<header class="header">
  <div class="env"><span class="dot"></span>production {icon('chev',12,'#8c8c8c')}</div>
  <div class="cluster"><span class="dot ok"></span>6 of 6 nodes in sync &middot; revision <b style="color:rgba(0,0,0,.88);font-weight:600">{rev}</b></div>
  <div class="search">{icon('search',14)}<span>Search APIs, routes, consumers&hellip;</span><kbd>/</kbd></div>
  <div class="hicon">{icon('help',17)}</div>
  <div class="hicon">{icon('bell',17)}<span class="pip"></span></div>
  <div class="user"><div class="avatar" style="background:{avatar_bg}">{user}</div><div style="line-height:1.2"><div style="font-weight:500">{name}</div><div style="font-size:11px;color:#6b6b6b">{role}</div></div>{icon('chev',12,'#8c8c8c')}</div>
</header>'''

def page(title, active, body, extra_css='', overlay='', badges=None, rev='1843', open_groups=None, user=None):
    u = user or {}
    return f'''<!DOCTYPE html><html lang="en"><head><meta charset="utf-8"><title>{title}</title>
<link rel="stylesheet" href="console.css"><style>{extra_css}</style></head><body>
<div class="layout">{sider(active, badges, open_groups)}<div class="main">{header(rev=rev, **u)}<main class="content">{body}</main></div></div>
<div class="footer-note"><span><b>Sample data</b> &middot; illustrative mockup, not a product screenshot and not measured performance</span><span>BeFive console &middot; UI design doc 03</span></div>
{overlay}
</body></html>'''

# ---------- developer portal frame (Draft 4) ----------
PORTAL_NAV = ['Home', 'APIs', 'Guides', 'Applications', 'Access requests']

def portal_page(title, active, body, extra_css=''):
    nav = ''.join(f'<span class="{"on" if n == active else ""}">{n}</span>' for n in PORTAL_NAV)
    return f'''<!DOCTYPE html><html lang="en"><head><meta charset="utf-8"><title>{title}</title>
<link rel="stylesheet" href="console.css"><style>{extra_css}</style></head><body class="portal">
<header class="p-head"><div class="p-brand">{LOGO_MARK_LIGHT}<div><b>Example Corp</b><small>Developer Portal</small></div></div>
  <nav class="p-nav">{nav}</nav>
  <div class="p-search">{icon('search',14)}<span>Search APIs, operations, fields&hellip;</span><kbd>/</kbd></div>
  <div class="user"><div class="avatar" style="background:#08979c">DP</div><div style="line-height:1.2"><div style="font-weight:500">Devon Park</div><div style="font-size:11px;color:#6b6b6b">Northwind Logistics</div></div>{icon('chev',12,'#8c8c8c')}</div>
</header>
<main class="p-content">{body}</main>
<div class="footer-note" style="left:0"><span><b>Sample data</b> &middot; illustrative mockup, not a product screenshot and not measured performance</span><span>BeFive developer portal &middot; UI design doc 03</span></div>
</body></html>'''

# ---------- charts ----------
def series(n, base, amp, noise, seed, trend=0.0, spikes=()):
    r = random.Random(seed); out = []
    for i in range(n):
        v = base + amp*math.sin(i/n*math.pi*2 - 1.2) + r.uniform(-noise, noise) + trend*i
        for (at, width, h) in spikes:
            if abs(i-at) < width: v += h*(1-abs(i-at)/width)
        out.append(max(v, 0))
    return out

def path(vals, w, h, vmin, vmax, pad=0):
    n = len(vals); pts = []
    for i, v in enumerate(vals):
        x = pad + i*(w-2*pad)/(n-1); y = h - (v-vmin)/(vmax-vmin)*h
        pts.append((x, y))
    d = 'M' + ' L'.join(f'{x:.1f},{y:.1f}' for x, y in pts)
    return d, pts

def sparkline(vals, w=90, h=24, color='#1668dc', fill=True):
    lo, hi = min(vals), max(vals); hi = hi if hi > lo else lo+1
    d, pts = path(vals, w, h-2, lo - (hi-lo)*0.1, hi)
    area = f'<path d="{d} L{w},{h} L0,{h} Z" fill="{color}" opacity=".10"/>' if fill else ''
    return f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" style="display:block">{area}<path d="{d}" fill="none" stroke="{color}" stroke-width="1.5"/></svg>'
