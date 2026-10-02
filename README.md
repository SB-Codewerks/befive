# BeFive

Commercial, self-hosted API gateway. Milestone 1 is the skeleton:
an Integrant node, clojure.spec settings, numbered SQL migrations,
and `/healthz` plus `/readyz`.

Design notes live in `docs/`. `AGENTS.md` is the build handoff.
`docs/decisions.md` records owner decisions and build assumptions.

```bash
export PATH="$HOME/.local/bin:$PATH"
clojure -M:lint
clojure -M:fmt check
clojure -M:test --focus :unit
clojure -M:test --focus :generative
npx shadow-cljs compile test && node out/test.js
clojure -M:test --focus :integration
clojure -T:build module-deps
clojure -T:build licenses
clojure -T:build uber
docker build -f docker/Dockerfile -t befive:dev .
docker compose -f docker/docker-compose.yml up
```

`java -jar target/befive-0.1.0-SNAPSHOT.jar` starts the node.
`migrate` applies SQL and exits. `healthcheck` probes `/readyz`.
An invalid setting exits 78.
