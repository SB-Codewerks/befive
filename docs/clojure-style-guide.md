# BeFive Clojure style guide

**Relevance:** 0.x and every later release. All Clojure and ClojureScript in the `befive` repository follows this guide. clj-kondo and cljfmt enforce what they can in CI.

> **Attribution.** This guide is adapted from **The Clojure Style Guide** by Bozhidar Batsov and contributors: <https://guide.clojure.style>, source <https://github.com/bbatsov/clojure-style-guide>. It is licensed under the [Creative Commons Attribution 3.0 Unported License (CC BY 3.0)](https://creativecommons.org/licenses/by/3.0/deed.en_US). **Changes made:** the guide was condensed and reorganized; rules were selected, reworded and, in places, made stricter; BeFive-specific rules were added, along with a "Simple Made Easy" section, a clojure.spec section and the tension rule. Upstream examples are reproduced or adapted under the same license. This adapted guide is distributed under CC BY 3.0 as well. Upstream's own sections on the guide's licensing and spreading the word are omitted; see the original. Rule IDs in brackets such as `[upstream: naming-predicates]` are anchors in the upstream guide, e.g. <https://guide.clojure.style/#naming-predicates>.

---

## 0. The overriding rule: simplicity

> **If multiple imperatives in this guide are in tension, bias in favor of the one that produces the simplest code, in the way Rich Hickey defines simple: not intertwined. One should be able to reason about each module in isolation unless there is a good reason to mingle its paths with other modules.**

This rule outranks every other rule here, including the formatting rules, if it ever comes to that. Upstream's own guiding principle agrees: "It's better to be consistent with your colleagues than with a style guide; use common sense."

## 1. Simple Made Easy (Rich Hickey, Strange Loop 2011)

Source: Rich Hickey, *Simple Made Easy*, Strange Loop 2011 (<https://www.infoq.com/presentations/Simple-Made-Easy/>). The principles below summarize the talk and apply it to BeFive.

- **Simple is not easy.**
  - *Simple* (from *simplex*, "one fold") means **not intertwined**: one role, one task, one concept, one dimension. Simplicity is objective: you can look at code and see whether things are braided together.
  - *Easy* (from *adjacens*, "lying near") means **near at hand**, familiar, already installed. It is relative to the person.
  - Choose simple even when it is not easy. Familiarity fades; complexity compounds.
- **Complecting** means braiding independent things together, such as state with identity, or what with how and when. It is the root of complexity. Complexity limits understanding, and what you can't understand you can't change or debug safely. Tests and type checks are guard rails. They don't steer.
- **Prefer simple constructs:**

  | Complex (complects) | Simple (separates) |
  |---|---|
  | State, mutable objects | Values |
  | Objects (state + identity + behavior) | Plain data (maps, vectors, sets) plus functions |
  | Methods bound to classes | Functions; namespaces for grouping |
  | Variables | Managed refs (one atom per component) and values |
  | Inheritance, switch on type | Polymorphism à la carte (protocols, multimethods) only where needed |
  | Syntax, ORMs | Data (EDN, maps), declarative queries (next.jdbc + HoneySQL as data) |
  | Imperative loops, folds with hidden state | Set and seq functions (`map`, `filter`, `reduce`, transducers) |
  | Actors, direct calls across components | Queues and channels (manifold streams, the signal shipper) |
  | Conditionals scattered through code | Rules as data (policies, the claims language) |
  | Inconsistency | Consistency (one revision, one snapshot per request) |

- **How BeFive applies it:**
  - **Config is a value.** A compiled snapshot is immutable. A request holds one snapshot from start to end, and a change swaps a single atom (02 §4–§6).
  - **Policies are data**, resolved by pure functions (`befive.policy`). The gateway, control plane, console, CLI and DSL all use the same code. Never re-implement a rule in a second place.
  - **Components are isolated.** An Integrant component receives its dependencies explicitly as config keys. It never reaches into another component's atom, and it is never accessed through a global var.
  - **Effects live at the edges.** I/O sits in the thin shell (handlers, stores, sinks). Logic sits in pure functions that take and return data, so it can be tested without mocks.
  - **Separate the what from the how and when.** Validation (spec), defaults (`apply-defaults`), coercion and storage are distinct steps, not one "smart" transformer.
  - Queues decouple producers from consumers: access-log sinks, the signal shipper, the quota flush.

## 2. Source code layout and organization

- **Line length.** Keep lines to **80 characters**, with a hard limit of 100 for long strings, URLs and tables in docstrings `[upstream: 80-character-limits]`.
- **Spaces, not tabs.** Use 2 spaces for bodies of special forms and macros that have body parameters (`defn`, `let`, `when`, `cond`, `ns` …) `[upstream: body-indentation]`.
- **Align function arguments vertically** when they span lines, or use a single-space indent if the first argument isn't on the first line `[upstream: vertically-align-fn-args]`.
- **Align `let` bindings and map keys vertically** where cljfmt allows it (`:align-associative? false` is acceptable; follow the repository's `cljfmt.edn`).
- **Gather trailing parentheses** on a single line `[upstream: gather-trailing-parens]`:

  ```clojure
  ;; good
  (when something
    (something-else))

  ;; bad
  (when something
    (something-else)
  )
  ```

- **Empty lines.** Use one blank line between top-level forms `[upstream: empty-lines-between-top-level-forms]`. Do not put blank lines in the middle of a function or macro body; an exception is grouping pairwise constructs in `let` and `cond` `[upstream: no-blank-lines-within-def-forms]`.
- **Layout.**
  - Don't use commas between elements of sequential collections. Commas in map literals are optional and discouraged except for readability in long, single-line maps.
  - No trailing whitespace. Unix line endings. Files end in a newline.
- **One file per namespace, one namespace per file** `[upstream: one-file-per-namespace]`.
  - Namespace segments map to directories with `-` → `_`. Example: `befive.gateway.rate-limit` lives at `befive/gateway/rate_limit.clj`.
  - Use `.cljc` for code shared between JVM and browser (`schema`, `policy`, and pure parts of `ui`), with reader conditionals kept to the edges.

## 3. Namespace declarations

- **Every namespace starts with a comprehensive `ns` form** containing `:refer-clojure`, `:require` and `:import`, in that order `[upstream: comprehensive-ns-declaration]`.
- **No single-segment namespaces** `[upstream: no-single-segment-namespaces]`. All BeFive code lives under `befive.*` (AGENTS.md §4). Plugin namespaces are `befive.plugin.<id>.*`, tests `befive.<module>.<ns>-test`.
- **Prefer `:require` over `:use`.** Never use `:use` `[upstream: prefer-require-over-use]`.
- **`:refer` sparingly.** It is fine for `clojure.test` (`deftest is testing are`) and core.async-style DSLs. Otherwise use `:as`. Never use `:refer :all` outside tests, and avoid it there too `[upstream: prefer-require-over-use]`.
- **Use idiomatic aliases** `[upstream: "Use Idiomatic Namespace Aliases"]`:

  | Namespace | Alias |
  |---|---|
  | `clojure.string` | `str` |
  | `clojure.set` | `set` |
  | `clojure.java.io` | `io` |
  | `clojure.edn` | `edn` |
  | `clojure.walk` | `walk` |
  | `clojure.spec.alpha` | `s` |
  | `clojure.spec.gen.alpha` | `gen` |
  | `clojure.spec.test.alpha` | `stest` |
  | `clojure.test.check.properties` | `prop` |
  | `next.jdbc` | `jdbc` |
  | `honey.sql` | `sql` |
  | `honey.sql.helpers` | `h` |
  | `manifold.deferred` | `d` |
  | `manifold.stream` | `ms` |
  | `integrant.core` | `ig` |
  | `jsonista.core` | `j` |
  | `befive.dsl` | `dsl` |
  | `befive.schema.core` | `bs` (or `schema` when clearer) |
  | `befive.policy` | `policy` |
  | `re-frame.core` | `rf` |

  Keep aliases **consistent across the codebase**. Lint with clj-kondo's `:consistent-alias`.
- **Sort requires and imports** alphabetically `[upstream: sort-requirements-and-imports]`.
- **Use list notation for imports**, as in `(:import (java.time Instant Duration))`. No wildcard imports.

## 4. Naming

- **Namespaces** use `befive.<module>.<thing>`, in lower-case kebab-case `[upstream: naming-namespace-composite-segments]`.
- **Functions and vars** use `kebab-case` `[upstream: naming-functions-and-variables]`. Use `CamelCase` only for protocols, records, types and Java interop `[upstream: naming-protocols-records-structs-and-types]`.
- **Predicates end in `?`**, e.g. `expired?`, `public-route?` `[upstream: naming-predicates]`. Don't prefix them with `is-`.
- **Functions that are unsafe in an STM transaction, or have notable side effects, end in `!`**, e.g. `reset-key!`, `publish-revision!` `[upstream: naming-unsafe-functions]`.
- **Conversion functions are named `a->b`**, e.g. `spec->json-schema`, `explain->problems` `[upstream: naming-conversion-functions]`.
- **Dynamic vars use `*earmuffs*`** `[upstream: naming-dynamic-vars]`. Dynamic vars are rare in BeFive; prefer explicit arguments.
- **Constants get no special notation.** No `+plus+` or `ALL_CAPS` `[upstream: naming-constants]`.
- **Use `_` for unused bindings and arguments**, or `_name` when the name documents intent `[upstream: naming-unused-bindings]`.
- **Don't shadow `clojure.core` names** (`name`, `type`, `key`, `val`, `str`, `count` …) except in a tiny local scope where clarity wins. clj-kondo `:shadowed-var` is on.
- **Keywords.** Spec names and domain events use qualified keywords (`:befive.route/id`, `:befive/error`). Wire and storage document keys stay unqualified kebab-case (02 §5.1).

## 5. Functions

- **Optional new line after the function name** `[upstream: optional-new-line-after-fn-name]`. Always put a new line between the arg vector and a non-trivial body.
- **Multi-arity:** indent each arity form, and order arities from fewest to most arguments `[upstream: multiple-arity-indentation, multiple-arity-order]`.
- **Keep functions short.** Aim for under 10 lines and almost never more than 25 `[upstream: function-length]`.
- **Fewer than 4 positional parameters.** Beyond that, take a map `[upstream: function-positional-parameter-limit]`. BeFive functions on domain data take **the map first** (`(defn resolve [policy-tree route] …)`) so they thread.
- **Pre and post conditions** (`:pre`, `:post`) are fine for invariants in internal code. **At boundaries, use spec** (§S).
- **`#()` for short single-argument lambdas only.** Don't nest them `[upstream: single-param-fn-literal]`. Prefer `fn` with named arguments when the lambda is longer than one call.
- **No needless anonymous functions.** Write `(filter even? xs)`, not `(filter #(even? %) xs)` `[upstream: no-useless-anonymous-fns]`.
- **Prefer `comp`, `partial` and `complement`** where they read better than a lambda, but never at the cost of clarity `[upstream: no-useless-anonymous-fns]`.

## 6. Idioms

- **Use `when` instead of `(if … (do …))` or `(if … nil)`.** Use `if-let`, `when-let`, `if-some` and `when-some` instead of a `let` followed by `if` `[upstream: when-instead-of-single-branch-if, if-let, when-let]`.
- **Use `if-not` and `when-not`** instead of `(if (not …))` `[upstream: if-not, when-not]`. Use `not=` instead of `(not (= …))`.
- **`cond` ends with `:else`** `[upstream: else-keyword-in-cond]`. Use `condp` when the predicate and expression are constant, and `case` for compile-time constants (keywords, strings, numbers) `[upstream: case]`.
- **Use threading macros** (`->`, `->>`, `some->`, `cond->`, `as->`) to flatten nesting `[upstream: threading-macros]`. Don't thread a single step.
- **Use sets as predicates** where it reads clearly, e.g. `(filter #{:get :head} methods)` `[upstream: set-as-predicate]`.
- **Use `seq` as the termination check**, e.g. `(when (seq xs) …)`, not `(when-not (empty? xs) …)` `[upstream: nil-punning]`.
- **Prefer `vec` over `into []`** for plain conversion. Use `into` with a transducer for transform-and-collect.
- **Use `inc`/`dec`, `pos?`/`neg?`/`zero?`** instead of comparing with 0 or adding 1 `[upstream: inc-and-dec, pos-and-neg]`.
- **Prefer `run!` over `doseq` over `dorun (map …)`** for side effects over a collection. Never use lazy `map` for side effects.
- **Don't hold lazy sequences across I/O boundaries.** Realize them (`vec`, `doall`) inside `with-open` and transactions.
- **Avoid `ns`-level side effects.** Requiring a namespace must not start threads, open sockets or register mutable state. Lifecycles belong in Integrant `init-key` and `halt-key!`.

## 7. Data structures

- **Avoid lists for data.** Use vectors, unless code is being generated or you need a stack `[upstream: avoid-lists]`.
- **Keywords as map keys** `[upstream: keywords-for-hash-keys]`. Exception: header maps on the hot path use lower-cased strings, as the pipeline already does (02 §7).
- **Use literal syntax** (`[]`, `{}`, `#{}`) rather than constructor functions `[upstream: literal-col-syntax]`.
- **Avoid accessing collections by index** where destructuring or `first`/`second` would do.
- **Use keywords as functions to read maps**, as in `(:id route)`. Use `get` with a default or for non-keyword keys `[upstream: keywords-as-fn-to-get-map-values]`.
- **Avoid Java collections** except in interop and measured hot paths `[upstream: avoid-java-colls]`.
- **Transients and arrays only in measured hot paths** (the route compiler, header parsing), behind a pure function interface `[upstream: avoid-transient-colls]`.
- **Domain entities are maps, not records.** Records are reserved for protocol implementations and measured hot paths (02 §4.3).

## 8. Types, protocols and records

- **Protocols** for real polymorphism across components (sinks, stores, secret providers, cache backends). Keep them small, with one role each (Simple Made Easy).
- **Multimethods** for open dispatch on data, e.g. policy kinds and plugin types. Dispatch on a keyword, not on a class.
- **Construct records with `->Rec` or `map->Rec`**, never the Java constructor `[upstream: record-constructors]`. Don't define custom record constructors that do I/O.
- **Avoid `deftype`** unless you need mutable fields for a measured hot path, and document why.

## 9. Mutation and state

- **One atom per component instance**, owned by its Integrant key. No global atoms. Expose state through functions, not the atom.
- **Prefer `swap!` over `reset!`** `[upstream: atoms-prefer-swap-over-reset]`. Keep `swap!` functions pure and fast; they may retry.
- **Avoid refs and the STM.** No BeFive design requires coordinated refs.
- **Use `volatile!`** only for local, single-threaded accumulation in transducers.
- **Never mutate config snapshots.** A new revision is a new value.

## 10. Strings, math and Java interop

- **Prefer `clojure.string` functions to interop** (`str/upper-case`, not `.toUpperCase`) except on the hot path, where interop with type hints is allowed `[upstream: prefer-clojure-string-over-interop]`.
- **Use `str` for concatenation.** Use `format` for complex output, but never build log lines with `format`. Log structured maps (telemetry-logging.md).
- **Type-hint to avoid reflection.** `(set! *warn-on-reflection* true)` goes at the top of every namespace in `gateway`, `proxy`, `authn`, `ratelimit` and `telemetry`. CI fails on reflection warnings in those modules. Use `*unchecked-math* :warn-on-boxed` in rate-limit and stats code.
- **Prefer the `java.time` API** with `Instant` and `Duration`. Inject a clock (`befive.util.clock`) instead of calling `Instant/now` in logic.

## 11. Exceptions and errors

- **Throw `ex-info` with a data map.** Don't throw raw Java exceptions from BeFive code `[upstream: reuse-existing-exception-types]`. The map always carries `:befive/error`, a namespaced keyword such as `:befive.config/conflict`, plus context. HTTP mapping (RFC 9457 problem types) is a single table in `befive.control.http.problems`.
- **Use `with-open` for closable resources** `[upstream: prefer-with-open-over-finally]`, and `try`/`finally` for anything else that must be released, such as Netty `ByteBuf` `release`.
- **Never swallow exceptions.** Catch narrowly, log once with the request ID and rethrow, or map to a response. Hot-path code returns error results instead of throwing for expected conditions (auth failures, limits).
- **Never put secrets in `ex-data`.** Error data is logged. Use the redaction helpers (security-infosec.md).

## 12. Asynchrony

- **Data plane: never block a Netty event-loop thread.** Use manifold deferreds for anything that may wait. Blocking work, such as KMS, DB and file I/O, goes to virtual threads or a bounded executor (02 §20.4). The test profile's blocking detector enforces this.
- **Don't use core.async in the data plane.** The stack uses manifold (01 §3.2). In the browser, re-frame effects handle async.
- **Every queue is bounded** and has a documented overflow policy: drop with a metric, or apply back-pressure.

## 13. Macros

- **Don't write a macro if a function will do** `[upstream: dont-write-macro-if-fn-will-do]`.
- **Write an example usage first**, then the macro `[upstream: write-macro-usage-before-writing-the-macro]`.
- **Break complicated macros into smaller functions** `[upstream: break-complicated-macros]`. The macro should be a thin syntax layer over functions.
- **Prefer syntax-quoted forms** to building lists by hand.
- **The DSL (`befive.dsl`) is functions returning data**, not macros (02 §16.4). Users' scripts compose with plain Clojure.

## 14. Comments and documentation

- **Comment headers** `[upstream: four-semicolons-for-heading-comments]`:
  - `;;;;` for file sections
  - `;;;` for top-level comments
  - `;;` for line comments above code
  - `;` for end-of-line margin comments (with at least one space before)
- **Write comments as full sentences** with a space after the semicolons. Good code is mostly self-documenting; comment the **why**, not the what `[upstream: refactor-dont-comment]`.
- **Annotations:** `TODO`, `FIXME`, `OPTIMIZE`, `HACK`, `REVIEW`, followed by a colon and a note `[upstream: annotate-keywords]`. A `TODO` must reference an issue or an `A-n` assumption, as in `;; TODO(A-12): …`.
- **Docstrings** on every public var `[upstream: prefer-docstrings]`:
  - The first line is a complete sentence summary; lint with clj-kondo `:docstring-leading-trailing-whitespace` and `:missing-docstring` on public vars in `src`.
  - Mention arguments with backticks, as in `` `route` `` `[upstream: document-pos-arguments]`.
  - Link to design sections with "See 02 §10.5" where the design is the spec.
- **Use `(comment …)` rich comment blocks** for REPL experiments. They may stay in source if they are useful and still compile.

## 15. Testing

- **Test namespaces mirror the source:** `befive.policy.resolve` → `befive.policy.resolve-test` in `test/` `[upstream: test-ns-naming]`.
- **Name tests after the behavior**, e.g. `(deftest locked-policy-cannot-be-overridden …)`. Test one concept per `deftest`, and use `testing` blocks for variations `[upstream: test-naming]`.
- **Use `are` for tables** of inputs and expected outputs.
- **Use `with-redefs` sparingly.** Prefer injecting a dependency, such as a clock, an HTTP client or a store, through the component map `[upstream: use-with-redefs-sparingly]`.
- **Use matcher-combinators `match?`** for asserting on big maps (`(is (match? {:status 403} resp))`), so tests don't break on unrelated keys.
- **Generative tests and instrumentation** follow §S and testing-strategy.md.
- **Tests must not depend on order, wall-clock time or the network**, except integration tests, which use Testcontainers.

## S. clojure.spec

BeFive uses **clojure.spec (`clojure.spec.alpha`, and `cljs.spec.alpha` in the browser) for all data validation** (decision D-OCT1-SPEC). Details are in validation-clojure-spec.md.

1. **Spec the boundaries, not everything.**
   - Write specs for data that crosses a trust or module boundary: Admin API bodies and params, config documents, settings files, plugin configs, CLI input, portal API, DSL arguments, events on queues, and LKG and snapshot files.
   - Don't spec every local map. Internal code trusts validated data.
   - Validate **once**, at the edge, then pass values.
2. **Namespaced spec names, unqualified wire keys.**
   - Register specs under the owning namespace (`:befive.route/paths`, `:befive.plugin.acme/config`).
   - For BeFive documents use `s/keys :req-un/:opt-un` (wire keys are unqualified).
   - For internal event maps prefer qualified keys (`:req`) when the map does not go on the wire.
   - Define each attribute once and reuse it, so attribute specs are the source of truth.
3. **Closed for writing.** `s/keys` is open. Combine it with `befive.schema.core/closed-keys` on write paths, so unknown keys are reported (but `:x-*` keys are tolerated).
4. **`s/fdef` public functions** in `schema`, `policy`, the compiler, the DSL and service boundaries, with `:args`, `:ret` and `:fn` where the relation matters. **Instrument in tests** with `stest/instrument` in fixtures. **Never instrument in production**, and never instrument hot-path functions in performance tests.
5. **Generative testing with test.check.**
   - Run `stest/check` on pure functions and write `prop/for-all` properties for invariants: diff round trip, delta = full, effective policy never wider than locked, apply-defaults idempotent.
   - Every spec used in generative tests must generate. Add `s/with-gen` for slugs, CIDRs, URLs, durations, RE2 patterns and header names.
   - Keep `num-tests` modest in the unit suite. The nightly suite raises it.
6. **Errors come from `explain-data`, mapped to messages.**
   - Never show raw `s/explain` output to users.
   - Use `befive.schema.errors/explain->problems`, which turns `::s/problems` (`:in`, `:pred`, `:via`) into `{:path :code :message}` from the shared message table.
   - The same problems feed 422 responses, `b5ctl validate`, console forms and settings errors.
7. **No hidden transformation.** Specs validate. Defaults (`apply-defaults`) and coercion (`befive.schema.coerce`) are separate, explicit, pure steps. Avoid `s/conformer` that changes data, except for parsing in grammars such as the claims language, where `s/conform` output is used deliberately.
8. **Keep specs and form metadata apart.** UI hints (title, help, widget, secret, default, since) live in `befive.schema.meta`, keyed by spec name, not inside predicates.
9. **Portable predicates.** Specs in `.cljc` must behave the same on the JVM and in JS. Avoid JS-incompatible regexes, and use `int?` rather than `integer?` (or note the edge cases). CLJS parity tests run the same fixtures in both.
10. **Performance.** Call `s/valid?` first and `explain-data` only on failure. Never validate per request on the data plane. The compiler validates once per revision.

## B. BeFive-specific rules

1. **Integrant everywhere.**
   - Each component is an `ig/init-key` / `ig/halt-key!` pair in its own namespace.
   - Its config spec is registered under `:befive.<module>/<component>`.
   - Dependencies are `ig/ref`s, never global lookups.
   - `befive.main` chooses the system by node role.
2. **Module dependency rules** (AGENTS.md §5). `schema` and `policy` depend on nothing BeFive-specific. The gateway never depends on the control plane, and vice versa. Both depend on `schema`, `policy` and `common`. clj-kondo hooks or a namespace-dependency test enforce this.
3. **Hot path:**
   - no reflection
   - no boxed math in counters
   - no lazy sequences, no `str` building of log lines, and no allocation of maps for routes that don't need them
   - no blocking calls
   - criterium benchmarks guard the changes
4. **Logging is structured data.** Use `befive.telemetry.log/event!` with a catalog event keyword. No `println`, and no string interpolation of user data. Never log a secret (canary tests enforce this).
5. **Secrets** are wrapped in a `befive.secret/Secret` type that prints as `#befive/secret "…redacted…"`. Unwrap only at the point of use.
6. **SQL** is HoneySQL data or `.sql` files with parameters. Never concatenate strings. Migrations are numbered (`V0001__…sql`) and append-only.
7. **Configuration of BeFive itself** is Aero EDN plus `BEFIVE_*` environment variables, validated by spec at startup. An invalid config exits with code 78 and the explain->problems output.
8. **Tooling in CI:**
   - `clj-kondo --lint src test` with zero warnings (the configuration lives in `.clj-kondo/config.edn`)
   - `cljfmt check` (kebab-case and indentation in `cljfmt.edn`)
   - Kaocha
   - the reflection check
   - the license check (no GPL or AGPL)
   - `kibit` and `eastwood` are optional and local only
   - `zprint` and `cljstyle` are not used; cljfmt is the formatter of record

## 16. Existential

- **Be functional.** Prefer pure functions and values; push effects to the edges `[upstream: be-functional]`.
- **Be consistent.** Follow this guide and the surrounding code. When they disagree, follow §0 and log the choice in `docs/decisions.md` if it sets a precedent `[upstream: be-consistent]`.
- **Use common sense** `[upstream: common-sense]`.

---

*Adapted from The Clojure Style Guide (© Bozhidar Batsov and contributors), <https://guide.clojure.style>, under CC BY 3.0 (<https://creativecommons.org/licenses/by/3.0/>). Modifications © the BeFive project, also under CC BY 3.0. The upstream guide was fetched on October 1, 2026 from <https://github.com/bbatsov/clojure-style-guide> (README.adoc).*
