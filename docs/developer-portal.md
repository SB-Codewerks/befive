# Developer portal

**Relevance:** 0.x: partial. The **read-only preview** is in milestone 9, with its security review in milestone 10. The full portal is 1.0. **Sources:** 01 §2.11, decisions 20 and 33. 02 §25 (portal architecture). 03 §5.33–§5.40, §7.9, §8.11. Mockups `portal-catalog.png` and `portal-api-detail.png`. 04 DEV-001..003.

## 1. Shape (01 §2.11, 02 §25.2)

- A **separate ClojureScript SPA** (shadow-cljs build `:portal`) served by the **production control plane** on its own **hostname** and **listener (port 9100)**. It has its own Okta OIDC client, a cookie `__Host-befive_portal`, a CSRF token, a DB role, and a narrower API **`/portal/v1`**. It never calls `/admin/v1`, and a portal cookie is rejected there.
- There is one portal, hosted by production. In 1.0 it lists APIs from all linked environments. In 0.x it shows the local environment only, without the environment-availability facet.

## 2. 0.x preview: what is in it and what is not (02 §25.1, 03 §5.33)

- Turned on with `:portal {:mode :preview}` (Settings > Developer portal: Off / **Preview** / On, where On is 1.0).
- **In the preview:**
  - Okta login (authorization code with PKCE; there are no local portal accounts).
  - Visibility by **Okta group and named user** at API, version and operation level.
  - Home: search, featured APIs, recently updated (computed from version publish dates), guides.
  - Catalog search and facets: domain, lifecycle, classification, owner, tags, version. The environment facet is 1.0.
  - The API page with the documentation tab and deprecation badges.
  - Guides (Markdown managed in the console).
  - **Changelog is not in the preview.** 03 §5.33 lists `/changelog` and an API changelog tab in the preview, but 02 §25.1 places the changelog in 1.0 and 01 §4.1 does not list it. Handoff default H-12 drops it from 0.x. The changelog is cheap to add later, and its route is simply not registered in 0.x.
- **Not in the preview** (absent, not disabled): applications, keys, subscriptions, try-it, access requests, "Your applications", "Needs attention", and notifications. The **mutating routes are not registered at all**, so they return 404, and the build **excludes** the `portal-tryit` and `portal-apps` modules through a compile-time flag.
- Navigation: Home, APIs, Guides.
  - A dismissible banner reads "Preview: browse and read API documentation. Requesting access and keys arrive with BeFive 1.0."
  - The rail shows "Questions? Contact the API team".
- **Partners may reach the preview from outside** (owner decision of Oct 1, 2026), so it is built for internet exposure from day one (§4).

## 3. Visibility and search (02 §25.4–§25.5, 03 §5.34–§5.36)

- Visibility rules are claims-language expressions over Okta groups and users. The default for new APIs is "nobody until published to groups".
- **Hidden items never appear anywhere**: not in search results, counts, facets or the changelog. Deep links to them return the standard 404 page. Tests must prove there is no enumeration and no facet-count leakage.
- Users in no mapped group see "You don't have access to any APIs yet…".
- Okta group → organization mapping (5.14.6) puts a partner's developers into their organization.
- **Search:**
  - Server-side **PostgreSQL full-text search plus pg_trgm** over names, descriptions, operations and schema fields.
  - Facet counts come back in the same response.
  - Visibility is filtered **in SQL**.
  - 20 results per page. The query and facets are in the URL.
- **Docs rendering:**
  - BeFive's own OpenAPI renderer, with no third-party script or CDN.
  - Markdown goes through markdown-it, then DOMPurify (sanitized).
  - Auth requirements are described in plain words, along with the plan limits and the RateLimit headers.
  - The parameters table and the schema tree are shown.

## 4. Security for internet exposure (02 §25.3, §25.8, 03 §5.14.6)

- CSP is `default-src 'self'`, with no inline scripts, `frame-ancestors 'none'` and nonce-based styles. In 1.0, `connect-src` adds the Sandbox origin for try-it.
- Sessions: 60 min idle, 12 h absolute. `SameSite=Lax` is required for the OIDC return. The session survives an Okta outage until it expires, and new sign-ins show a maintenance message.
- A separate listener (9100) with a **host allow-list**: any other `Host` header gets `421`.
- A separate hostname and cookie, and no config-writing routes.
- An optional portal-only control-plane task (`BEFIVE_CP_SURFACES=portal`) that uses DB role `befive_portal`. The role has privileges only on the portal tables plus read access to the catalog tables. Recommended layout: this task in a public subnet.
- Portal API limits: 300 requests per minute per user and 60 login attempts per IP per 10 min. Use the embedded Bucket4j.
- Reference WAF: AWS managed common rules, known-bad-inputs and IP reputation lists, and a rate rule of 2,000 requests per 5 min per IP.
- `Origin` is checked on every non-GET request. markdown-it runs with HTML input disabled, and its output goes through DOMPurify again.
- Turning the portal on shows a **hardening checklist**:
  - a WAF in front of the hostname
  - rate limits, including the portal's own per-IP limits
  - CSP enforced
  - a link to the hardening guide
- Sign-in pages, error pages and empty states never reveal hidden API names.
- Milestone 10 includes a **portal-preview security review and a pen test**: CSP, WAF, session handling, an XSS corpus against the sanitizer, enumeration tests.

## 5. Branding (03 §7.9, 5.14.6). Owner default for 1.0: name, logo, color and footer

- The customer's name comes first: "Example Corp / Developer Portal".
- An optional customer logo (SVG or PNG) replaces the BeFive mark.
- The accent color is used only if it reaches 4.5:1 contrast on white.
- Footer: support contact, terms, and "Powered by BeFive" (which can be removed).
- **No custom CSS or custom pages in 1.0** (owner default).
- 0.x may ship the title and logo only, if time is short. Log that as an assumption if it happens.
- The portal is responsive down to 768 px.

## 6. 1.0 additions (03 §5.35–§5.40, 02 §25.6–§25.7)

- Applications and keys (self-service, at most two keys). Okta client keys follow identity.md §12, including the **24 h / post-download deletion of generated private keys**.
- Access requests (governance-access-requests.md).
- Subscriptions shown on the cards.
- Linked-environment federation and the environment facet.
- **Try-it**, proxied server-side to the **Sandbox** cluster:
  - Production is a target only when the API enables it, which is **off by default** (owner default).
  - Sandbox applications only.
  - Credentials are held in memory, and the response is truncated at 64 KiB.
- Mock mode.
- Deprecation banners and emails.
- "Needs attention".
