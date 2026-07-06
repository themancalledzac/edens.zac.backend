# ai_docs/ (AI Reference Index)

Living infra references, kept up to date as the deployed system changes:

- `ai_deployment_strategy.md` — Docker Compose deploy pipeline, EC2 setup, backups, BFF proxy security.
- `ai_cicd.md` — GitHub Actions pipeline (lint, test, build, security scan), branch protection, caching.
- `ai_ec2.md` — EC2 instance architecture, PostgreSQL + Spring Boot service layout, local dev setup.

`ml_image_tagging_design.md` is a **design doc only** — 0% implemented. No ML integration exists in
`src/` yet; it's a 5-phase plan (CLIP tagging → face recognition → backfill → BLIP-2/similarity search)
via a future Python sidecar service.

**Forward-work index**: BE forward work is tracked in the FE repo's book, not here —
`edens.zac/docs/009-backend-and-vision.md` is the single source of truth by design; there is no
separate BE-side index.

**Local plans**: the gitignored `docs/` dir (repo root) holds local plans/handoffs-in-progress.
Shipped ones are tarballed under `docs/_archive/` once done.
