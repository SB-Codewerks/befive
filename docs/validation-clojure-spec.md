# Validation with clojure.spec (replacing malli)

**Relevance:** 0.x: build (from milestone 1). **Decision:** D-OCT1-SPEC (owner, Oct 1, 2026): **use clojure.spec instead of malli everywhere.** This covers the shared schema module, node settings and config validation, Admin API coercion and validation, b5ctl, plugin config schemas, and the console and portal forms (cljs.spec). **Sources replaced:** 01 §3.1 ("malli"), 02 §1 rule 4, §3.1 (the schema module), §4.2 (`:befive/schema-registry`), §5.3 (schema sketches), §6.2 (write path), §12.4 (secret marking), §15.1/§15.5/§15.7 (coercion, errors, OpenAPI), §16.2/§16.4 (CLI normalization, DSL), §17 (plugin schemas), §21.3 (`{:befive/since n}`), §22 (malli generators), 03 §4.2, §4.3, §8.1, §8.4, §8.5, §8.11.

## 1. Libraries

- `org.clojure/spec.alpha` (CLJ), `cljs.spec.alpha` (bundled with ClojureScript), `clojure.spec.gen.alpha`, `clojure.spec.test.alpha`, and `org.clojure/test.check` (test and dev scope; the CLJS generators also need it at dev time).
- **reitit:** `reitit.coercion.spec` (backed by spec-tools) for Admin API parameter and response coercion and OpenAPI generation. If spec-tools is unacceptable (open item S-2 in decisions.md), write an in-house `befive.schema.coerce` and `befive.schema.json-schema` for the subset of spec forms we use and plug them in as a custom reitit coercion. Either way, **the spec registry stays the single source of truth**.
- Do **not** depend on spec-alpha2 (`clojure.alpha.spec`). It is unreleased.
- **No malli** dependency anywhere. CI greps `deps.edn` and `package.json` for it.

## 2. Registry layout (`modules/schema`, `.cljc`)

```
befive.schema.core        ; slug, http-url, cidr, duration, header-name … (base specs with generators)
befive.schema.route       ; :befive.route/* specs and ::route
befive.schema.api         ; :befive.api/*, :befive.api-version/*, :befive.operation/*
befive.schema.tenancy     ; organizations, consumers, applications
befive.schema.policy      ; policy kinds, attachments, the claims language grammar (also used by befive.policy)
befive.schema.settings    ; node settings (Aero) and settings documents
befive.schema.meta        ; FORM-METADATA REGISTRY (titles, help, widgets, secret?, since, defaults, keyword coercion)
befive.schema.defaults    ; apply-defaults (pure; driven by meta)
befive.schema.errors      ; explain-data → problems → messages
befive.schema.coerce      ; JSON → EDN coercion helpers (keywords, sets, instants) driven by spec forms + meta
befive.schema.validate    ; cross-entity validation (references, conflicts, default deny, locks via befive.policy)
befive.schema.diff        ; normalization + diff engine
befive.schema.json-schema ; spec → JSON Schema for OpenAPI and /plugins (if not spec-tools)
befive.schema.access-log  ; field catalog (data)
```

### Keys

- Spec names are **namespaced** keywords (`:befive.route/paths`).
- Documents on the wire and in storage keep **unqualified kebab-case keys**, as 02 §5.1 requires. Entity specs therefore use `s/keys :req-un [...] :opt-un [...]`.
- The namespace carries ownership and avoids collisions (plugins use `:befive.plugin.<id>/…`).

### Closed maps

`s/keys` is open. Writes must reject unknown keys (02 §5.1: "closed for writing"):

```clojure
(s/def ::route
  (s/and (s/keys :req-un [::id ::service ::match ::access]
                 :opt-un [::authn ::rate-limits ::plugins ::tags ::meta])
         (core/closed-keys #{:id :service :match :access :authn :rate-limits :plugins :tags :meta}
                           {:allow-prefix "x-"})))
```

- `core/closed-keys` is a small predicate that reports each unknown key with its path. Read paths (the compiler) skip the closed check and tolerate `:x-*` keys.
- Derive the key set from the `s/keys` form with `(s/form ::route)` so it is never written twice. Provide a macro-free helper `core/keys-of`.

## 3. Error messages: `explain-data` → problems (replaces `malli.error/humanize`)

```clojure
(defn explain->problems
  "Turns s/explain-data into [{:path [...] :code kw :message str}] for RFC 9457 and forms."
  [spec value]
  (when-let [ed (s/explain-data spec value)]
    (->> (::s/problems ed)
         (map (fn [{:keys [in pred via val] :as p}]
                (let [code (problem-code p)]            ; :required :type :pattern :enum :range :closed :custom
                  {:path    (vec in)
                   :code    code
                   :message (message-for (last via) code p)})))
         (distinct-by-path-and-code)
         vec)))
```

- `problem-code` classifies `pred`:
  - `(contains? % :k)` → `:required`, with the path extended by the missing key
  - a set → `:enum`
  - a regex predicate → `:pattern`
  - a numeric range → `:range`
  - `closed-keys` → `:closed`
  - type predicates → `:type`
- `message-for` looks up, in order:
  1. a message for the spec key and code in the **message table** (`befive.schema.errors/messages`, an EDN map shared by server, CLI and browser and i18n-ready)
  2. a generic message for the code
  3. a fallback
- Example: `{[:befive.route/paths :pattern] "must start with /, params as :name, optional trailing /*"}`.
- **The same function** feeds Admin API `422` responses (`errors[]`), `b5ctl validate` output (with file, line and column from the EDN reader's metadata), console form messages, and Aero settings errors (exit code 78).
- Codes are stable and part of the API contract.

## 4. Defaults applied explicitly (replaces `mt/default-value-transformer`)

```clojure
(meta/register! :befive.upstream/timeout-ms {:default 30000 :title "Timeout (ms)" :widget :number :min 1 :max 600000})

(defaults/apply-defaults ::route doc) ;; walks s/keys forms (incl. nested, coll-of), assoc-es missing optional keys that have :default
```

- The function is pure and idempotent.
- It runs on the server write path **before** validation (02 §6.2 step 2), in `b5ctl` normalization (02 §16.2) and in the console EDN preview, so "users see the stored result".
- Placeholders in forms read the same `:default`.

## 5. Coercion (replaces malli JSON transformers)

- JSON values reach the server as strings, numbers, arrays and maps. `befive.schema.coerce/coerce` walks the spec form:
  - keyword sets (`#{:jwt :api-key}`) and specs marked `:befive/keyword true` turn strings into keywords
  - `s/coll-of … :kind set?` turns arrays into sets
  - `inst?` specs parse RFC 3339
  - integers stay as they are
- EDN and Transit bodies are used as they come, with no coercion.
- reitit route coercion uses `reitit.coercion.spec`, or the in-house coercion with the same rules.

## 6. Forms: spec-driven generation through a form-metadata registry (replaces malli schema walking)

- A form is `{:spec ::route :original doc :draft doc :problems {...}}`. Fields bind by path, as in 03 §8.5.
- **Validation as the user types** (200 ms debounce): `(errors/explain->problems ::route draft)` keyed by path. Cross-entity rules run against app-db entities (`befive.schema.validate`). Server 422 problems merge in by path.
- **Generated forms** (plugin configs and other spec-described maps):
  - The generator reads `(s/form spec)` and the metadata:

    | Spec form | Widget |
    |---|---|
    | `s/keys` | Card section with fields |
    | a keyword set | `Select` (labels from meta `:enum-labels`) |
    | `boolean?` | `Switch` |
    | `int?` with meta `:min`/`:max` | `InputNumber` |
    | `string?` or a regex spec | `Input` |
    | `s/coll-of` | Repeatable rows |
    | meta `:secret true` | Write-only secret field |

  - The registry also carries `:title`, `:help`, `:widget` overrides, `:order` and `:since` (feature level).
  - Anything the generator cannot render falls back to the raw EDN editor.
  - `meta` is plain data in `.cljc`. Plugins register theirs alongside their specs.
- **Secret marking for audit redaction** (02 §12.4) reads `:secret true` from the same registry.

## 7. Function specs, instrumentation and generative tests

- `s/fdef` every public function in `schema`, `policy`, `befive.gateway.compile`, `befive.dsl` and the config service boundary.
- `stest/instrument` in test fixtures. Never instrument in production. Hot-path code is never instrumented, even in tests that measure performance.
- `stest/check` (test.check) for pure functions: diff and normalize, apply-defaults, policy resolve and meet, claims compile against the interpreter, bucket-key derivation.
- **Every spec used in generative tests must generate.** Use `s/with-gen` for slugs (`^[a-z0-9][a-z0-9-]{0,62}$`), CIDRs, URLs, RE2 patterns, durations and header names.
- **Feature levels** (02 §21.3): meta `:since n` tags fields. `validate/feature-level-violations` walks a document and lists the fields newer than the cluster feature level.

## 8. Where the design relied on malli, and what to use instead

| malli feature used in the design | Where (source) | spec-based replacement |
|---|---|---|
| `malli.error/humanize` with custom messages | 02 §5.3, §6.2, §15.5; 03 §4.2, §8.5 | `explain-data` → `befive.schema.errors/explain->problems` plus the shared message table (§3) |
| Schema-walking form generator using properties (`:title`, `:description`, `{:befive/secret true}`) | 03 §8.5 | `s/form` walk plus the **form-metadata registry** `befive.schema.meta` (§6) |
| `mt/default-value-transformer` and `:default` properties | 02 §6.2, §16.2; 03 §4.2, §8.5 | An explicit `befive.schema.defaults/apply-defaults` step driven by the registry (§4) |
| `json-transformer` / `string-transformer` coercion | 02 §15.1, 03 §8.4 | `befive.schema.coerce` or `reitit.coercion.spec` (§5) |
| `malli.json-schema` for OpenAPI and `/plugins` | 02 §15.2, §15.7 | spec-tools JSON Schema, or the in-house `befive.schema.json-schema` for our subset; custom predicates register their JSON Schema in meta (`:json-schema {...}`) |
| Closed maps `{:closed true}` | 02 §5.1, §5.3, §5.6 | `s/keys` plus `core/closed-keys` (§2) |
| Schema properties `{:befive/since n}` | 02 §21.3 | Meta `:since n` (§7) |
| Malli registry plus plugin schemas (`:befive/schema-registry`) | 02 §4.2, §17 | The global spec registry plus `meta`. Plugins `s/def` their config spec under `:befive.plugin.<id>/config` at load time. `:befive/schema-registry` holds the list of registered plugin specs and meta. |
| Malli generators in tests | 02 §22 | `clojure.spec.gen.alpha` plus test.check (§7) |
| Eager DSL argument validation | 02 §16.4 | `s/valid?` plus `ex-info` carrying `explain->problems`, at each DSL fn |
| Query-param coercion in reitit-frontend | 03 §8.4 | `reitit.coercion.spec` in reitit-frontend, or `befive.schema.coerce` |
| Claims language validation at write time | 02 §10.5 | Specs for the grammar (`s/or` / `s/cat` over vectors), plus custom checks (depth, counts, RE2J compile) reported through `explain->problems` |

## 9. Gotchas

- `s/keys` checks **every registered namespaced key present** in a map, even keys the spec doesn't list. With `:req-un` and unqualified data this rarely matters, but keep spec names unique.
- `s/merge` with `closed-keys`: compute the allowed set from all merged forms.
- `cljs.spec` generators need `test.check` on the classpath at dev time only. Keep it out of the release bundles. Do not ship `clojure.spec.gen.alpha` calls in production code paths.
- Regex specs must work on the JVM and in JS. Avoid lookbehind and other JS-incompatible features. Claims `:matches` patterns are RE2J and are validated on the JVM only (the console shows a server-side check result).
- Performance: validation runs on the control plane and in the CLI, never per request. The compiler validates the snapshot once per revision. It uses `s/valid?` first and calls `explain-data` only on failure.
