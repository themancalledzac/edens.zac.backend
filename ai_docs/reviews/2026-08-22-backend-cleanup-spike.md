# Backend cleanup tracker

Living checklist of what is still open. Check items off as they land; when an MR closes, its
detail moves to the history file (working rule 11) rather than staying here ticked.

Completed detail lives in [`2026-08-22-backend-cleanup-history.md`](2026-08-22-backend-cleanup-history.md).
This file carries the open work and what steers it: the Progress tables, the items carried forward,
the open security findings, the distilled working rules, the open MR waves, decisions needed from
the user, stale side branches, Appendices C and D (open leads), and the current session's log.
Closed outcomes, the working rules' full narratives and the log archive live in the history file.
Keep it that way (working rule 11).

Source review: 2026-08-22, baseline `main` @ `8c28cf3`, six parallel passes (controllers/API, core collection and content services, media/upload pipeline, security/auth/config, data layer, tests/build). Every finding was verified against the code -- caller greps for dead-code claims, line-level reads for bugs. Unverified suspicions are quarantined in [Appendix C](#appendix-c--unverified-leads).

Line numbers are from the `8c28cf3` baseline. Find symbols by name, not by line, once earlier MRs have shifted the files.

## Progress

| Wave | MRs | Status |
|---|---|---|
| 1 — Deletions | MR 1a-4 | **complete** — [history](2026-08-22-backend-cleanup-history.md#wave-1--deletions) (#159, #160, #161, #162, #164). Two residuals carried forward, below. |
| 2 — Bugs | MR 5-9 | **complete, and its residual is now closed too** — [history](2026-08-22-backend-cleanup-history.md#wave-2--bugs) (#165, #166, #168, #169, #170, #172, #173). Bug #17, carried forward since 2026-08-24, shipped 2026-08-31 ([#256](https://github.com/themancalledzac/edens.zac.backend/pull/256)). |
| 3 — Security hardening | MR 10-11 | **complete** — [history](2026-08-22-backend-cleanup-history.md#wave-3--security-hardening) (#175, #176). Superseded by the 2026-08-24 review; see the security row. |
| 4 — Comments and docs | MR 12-14 | **mostly complete** — [history](2026-08-22-backend-cleanup-history.md#wave-4--mr-12-and-mr-13-complete) (#177, #178, #180, #181, #183, #184) and MR 14 ([#187](https://github.com/themancalledzac/edens.zac.backend/pull/187)). **Wave 4 removed 500 comments for -1,026 words across seven MRs.** MR 14 taught working rule 12 (since superseded by 37); **two** stale-docblock items still open (was three -- the `filterNonListedChildCollections` docblock closed 2026-08-29 as already rewritten). |
| 5 — Consolidations | MR 15-19 | MR 15 #1, #2, #6 **done** ([#165](https://github.com/themancalledzac/edens.zac.backend/pull/165), [#189](https://github.com/themancalledzac/edens.zac.backend/pull/189), [#191](https://github.com/themancalledzac/edens.zac.backend/pull/191)) and the follow-up closed ([#210](https://github.com/themancalledzac/edens.zac.backend/pull/210)) — **MR 15 is fully done**. MR 19 #16 shipped ([#216](https://github.com/themancalledzac/edens.zac.backend/pull/216)); MR 19 #14 shipped ([#218](https://github.com/themancalledzac/edens.zac.backend/pull/218)) — the first item in seven to need no adjustment at implementation time, which broke the streak the full-board review's case rested on. **next for this wave: MR 16 #4/#5 (zero test coupling); board-wide next lives in the session log.** Prior row text: [history](2026-08-22-backend-cleanup-history.md#board-row-narratives-moved-2026-08-29). |
| 6 — Conventions | MR 20-22 | not started |
| 7 — Structure | MR 23-24 | not started |
| 8 — Tests | MR 25-26 | not started |

Four sections below are not waves and had no row here until 2026-08-24, which made them invisible
to anyone navigating by this table. **"Decisions needed from the user" was the fourth and was still
missing its row until 2026-08-24's close-out** -- eight open items, invisible to this table, which
is the same failure the paragraph above was written to fix:

| Section | Status |
|---|---|
| [Open security findings](#open-security-findings) | **0 open — the section is EMPTY as of 2026-08-31**, for the first time since it was created 2026-08-24. The last five closed in one session and none closed on a deferral: S-22 ([#247](https://github.com/themancalledzac/edens.zac.backend/pull/247)) and S-23 ([#248](https://github.com/themancalledzac/edens.zac.backend/pull/248)) shipped, S-16 was answered then built ([#253](https://github.com/themancalledzac/edens.zac.backend/pull/253)), S-14 and S-24 were answered and closed with two docblocks ([#250](https://github.com/themancalledzac/edens.zac.backend/pull/250)). **25 closed**, one ledger line each below; the six newest have outcomes in [history](2026-08-22-backend-cleanup-history.md#2026-08-31-close-out--s-14-s-16-s-22-s-23-s-24-and-bug-21). The 2026-08-29 adversarial re-review returned **0 HIGH, 0 MEDIUM** against the merged set — and five more security changes have merged since, which is one of the triggers for the next full-board review. Edit gate (rule 36): `grep -c '^- \[ \] \*\*S-'` = **0** — run it and update this row and the estimate cell together. |
| [Cross-repo findings owed to the frontend](#cross-repo-findings-owed-to-the-frontend--one-open-2026-08-31) | **1 open — RE-OPENED 2026-08-31 by [#258](https://github.com/themancalledzac/edens.zac.backend/pull/258)**, after being closed since 2026-08-24 (#157, #209, #213, #214; outcomes in [history](2026-08-22-backend-cleanup-history.md#cross-repo-findings-owed-to-the-frontend)). The open one is the location page's `images` array now carrying GIFs against a `ContentImageModel[]` prop. **It is deliberately NOT filed on the frontend board** — that repo had another session's dirty branch checked out — so it is invisible in `edens.zac` until someone files it. Read the section. |
| [Decisions needed from the user](#decisions-needed-from-the-user) | **2 open, and NEITHER is waiting on anyone — for the first time since this section was created.** The three one-word calls were asked in the opening message of the 2026-08-31 third run and all three came back: `cover_image_id` **drop** (V59), the DB-password default **drop the default** (`${POSTGRES_PASSWORD}`), `role.kind` **keep, documented as provenance** (V60). All three shipped together in one MR. What remains is gallery passwords (**parked by decision** pending a design) and the partial-index item C7 (an explicit "not until scale demands it"). **Batching the three into the opening message is what turned them into a same-session MR** — asked at the end, they would have been the next session's problem (working rule 41's neighbour). Edit gate (rule 36): the count is over the section's own `- [ ] ` lines; re-run it and update this row together. |
| [Tests that cannot fail](2026-08-22-backend-cleanup-history.md#tests-that-cannot-fail--closed-2026-08-30-moved-from-the-tracker) | **0 open of 6 — CLOSED 2026-08-30.** The last three shipped in one session (#239, #240, #241), each mutation-proved against `main` first. Two of the three carried a wrong premise that was corrected while closing: the share-link credential is a `Set-Cookie`, not a response-body token; and the `AdminUserControllerTest` pointer the board suggested names a test that does not redden on that mutation. Write-ups in history. |
| [Rule 37 debt](2026-08-22-backend-cleanup-history.md#rule-37-debt--r-1-closed-2026-08-30-moved-from-the-tracker) | **0 open — R-1 closed 2026-08-30 ([#238](https://github.com/themancalledzac/edens.zac.backend/pull/238)).** Taught working rule 39. The wider per-package sweep is not tracked here; it is the Inline-comments row in the category table below. |
| [Stale side branches](#stale-side-branches) | **New 2026-08-24.** 6 worktrees, 0 open PRs (as of 2026-08-24), all superseded. `fix/s18-actuator-exclude` added 2026-08-28 — **now holds nothing unique and is safe to delete** (settled 2026-08-30, see the section). |

Original estimate: roughly 4,500-5,000 lines removed against a few hundred added. The test tree (32.6k lines) is larger than main (27.2k); about 8% of it tests the Java compiler and Lombok.

| Category | Count | Deletable lines (est.) |
|---|---|---|
| Bugs (fix, not delete) | **21** (5 high) — 20 shipped, **1 open** (#18). Bugs #17, #19 and #20 all shipped 2026-08-31 ([#256](https://github.com/themancalledzac/edens.zac.backend/pull/256), [#258](https://github.com/themancalledzac/edens.zac.backend/pull/258), [#255](https://github.com/themancalledzac/edens.zac.backend/pull/255)); #21 earlier the same day ([#249](https://github.com/themancalledzac/edens.zac.backend/pull/249)). Checkbox check: `grep -c '^- \[ \] \*\*Bug #'` = **1**, re-run 2026-08-31 (second close-out). **Bug #18 is the only open bug on the board.** | — |
| Security findings | **0 open.** 25 closed; the five newest 2026-08-31. Checkbox check: `grep -c '^- \[ \] \*\*S-'` = **0**, re-run 2026-08-31 (working rule 36: run it and edit this cell and the section-table row together). | — |
| Dead code (main) | ~60 methods/fields/files | ~1,000 |
| Inline comments | **Re-measured 2026-08-29.** Old criterion (whole-line `//` at indent >= 4, `src/main`): **73**. Rule-37 criterion (any line whose first non-whitespace is `//`, `src/main` + `src/test`): **1,675** (290 main / 1,385 test), plus **72** trailing `code; //` lines. **Re-run 2026-08-31 (second close-out): 1,644 (262 main / 1,382 test)** -- down 31 from 1,675 (290/1,385). The earlier re-run that day was UNCHANGED, which was the first confirmation that a session added none; this one is the first that a session *removed* some. **The delta reconciles exactly against the four MRs' own deletions** (-28 main: 11 in `ImageUploadPipelineService`, 10 in `ContentService`, 7 in `AdminUserController`; -3 test: 1 in `AdminUserControllerTest`, 2 in `WebAuthnCredentialRepositoryIntegrationTest`), which is **working rule 42**. **The trailing figure was wrong and its recorded command did not reproduce it.** As recorded, `grep -vE '^\s*[^:]+:[0-9]+:\s*//'` returns **231**, not 72: BSD `grep -E` does not honour `\s`, so the exclusion under-matches, and nothing excluded URLs -- every `https://` in a javadoc counts as a `//`. **Corrected count: 74**, by this command, which is portable and URL-safe -- **re-run 2026-08-31 (second close-out) and still 74**:
```
grep -rn '//' src/main/java src/test/java | grep -vE '^[^:]+:[0-9]+:[[:space:]]*//' | grep -v 'https\?://' | wc -l
```
Leading form, unchanged and still correct: `grep -rn '^[[:space:]]*//' src/main/java src/test/java | wc -l`. **Both must be run from the repo root against `src/`** -- `.claude/worktrees/` holds whole source trees, and an unscoped `grep -rn` triple-counts (verified 2026-08-31: the scoped commands return 0 worktree hits). The old "~~370~~ 567 measured … is a floor" was the 2026-08-23 wave-4 start under the old criterion and described nothing current — the real rule-37 debt is ~3x it. | ~300 net (also low) |
| ^ **re-scoped 2026-08-28** | Working rule 37 turns this from a debloat nice-to-have into a standing rule: **every** inline comment in `src/main` and `src/test` is now a violation, not just the ones a rule flagged. **Do not sweep this in one MR** — take it per package, and take the files working rule 12 protected first -- **`RoleRepository` (10), `AdminBootstrap` (6, and it is in `services/`, not `config/`), `CollectionControllerProd` (9); all three re-run 2026-08-31 twice and unchanged both times** -- `RoleRepository` held at 10 across #247, which edited it. **`SecurityConfig` is off this list**: #243 swept it from ~27 to **4** as a side effect of removing the authz toggle, so it is nearly done and no longer the priority the row assumed. **One recorded exemption**: the second `coverImage` banner in `CollectionControllerProdTest` stays until its "Carried forward" decision lands. (Bug #17's `ContentService` comment is no longer exempt — its board row is the evidence now.) | — |
| Duplication consolidations (main) | 20 findings | ~500 |
| Dead/boilerplate tests | **10 findings** | ~2,700 (+700 optional) |
| Build/config rot | **10 findings** — 9 open, **C-1 filed and closed 2026-08-30** ([#245](https://github.com/themancalledzac/edens.zac.backend/pull/245)) | ~150 |

## Carried forward out of closed waves

Reconciled 2026-08-23 during the history split, re-reviewed 2026-08-24. Waves 1-3 read "complete"
but held **eight live items**, collapsed into five entries. Since then: the `PersonRepository` entry
was closed by MR 15 #6 (decided, not deferred), and the chunked-body residual moved to **S-5** under
"Open security findings". What is left is below -- plus one bug that never had a
row at all (#17), one item found while costing #209's guardrail (the coverImage banner), and three
bugs filed 2026-08-29 (#18-#20, at the end of this section).

- [ ] **The `coverImage` stripping that does not exist, and the test that cannot fail.** *(New row
  2026-08-24, found while writing #209's cost report; taught **working rule 22**.)*
  `CollectionControllerProdTest` has a section headed **"Fix 1: coverImage stripped for protected
  CLIENT_GALLERY on list endpoints"**, and its test
  `getAllCollections_protectedClientGallery_returnNullCoverImage` carries the comment "stripped by
  `CollectionProcessingUtil.buildBasicModel`". **No such stripping exists** --
  `buildBasicModel` sets `coverImage` unconditionally from `coverImagesById`, verified by read.
  The test hand-builds a `CollectionModel` with `coverImage(null)` and mocks `CollectionService`, so
  it asserts only that the controller passes through what the service handed it. Strip nothing,
  change nothing, and it stays green: working rule 15's shape exactly, reporting coverage for a
  security behavior that was never written.

  **This is the second of the two comments in that file** that claim stripping. The first -- the
  BE-H5 banner -- was corrected in #209, because it is the one that crossed the repo boundary and
  produced the frontend's false premise. This one was left alone deliberately: it sits inside the
  area #209's guardrail said not to touch, and changing it is a judgement about what the tests
  should assert, not a comment fix. **Recorded exemption (2026-08-29, working rule 37): this second
  banner is excluded from the rule-37 comment sweep until this decision lands** -- a sweep deleting
  it would erase the record of the open question without anyone seeing this row.

  **The frontend already strips (added 2026-08-29 by the cross-repo review).** FE #327 (2026-08-25)
  shipped a client-side strip on the public card path keyed on `isPasswordProtected === true`,
  deliberately diverging from #209's render-hint intent (cover retained for a locked tile; that tile
  UI is unbuilt). So "list-endpoint stripping is genuinely wanted" would now *duplicate* an
  enforcement the frontend already has -- and #209's cost report already shows a query-level filter
  would empty `all-client-galleries`. Without this line a future session could re-derive "the
  frontend needs the strip", which is the exact C6-shaped failure.

  **What to decide, and it is not obvious.** Either the section is a stale record of a fix that was
  reverted or never landed, in which case delete the banner and rename the test to what it actually
  asserts (controller pass-through); or list-endpoint stripping is genuinely wanted, in which case
  the test is a specification with no implementation and the work is real. Do not resolve this by
  reading the comment. #209's cost report has what implementing it would break.

- [x] **Bug #17** (medium) `updateImages` claimed a batch save it did not do — [#256](https://github.com/themancalledzac/edens.zac.backend/pull/256),
  2026-08-31. **Fixed by correcting the log line, not by building a `batchUpdate`** — the loop
  already writes per image through `saveContentTags` and `saveContentPeople`, so batching only the
  `saveImage` calls leaves the endpoint O(N) in statements. Reasoning now lives in `updateImages`'s
  docblock. Write-up in
  [history](2026-08-22-backend-cleanup-history.md#2026-08-31-second-close-out--bugs-17-19-20-and-passkey-deregistration).
- [ ] **Four main-dead, test-live members owed to MR 25** (deleting them means editing test call
  sites, which is why MR 1a deferred them): `ContentService.resolveCollectionDownloadEntries` 2-arg
  overload (**5 test sites, re-verified 2026-08-31** -- all five in `ContentServiceDownloadTest`,
  by `grep -rn 'resolveCollectionDownloadEntries(1L,' src/test/java`; the 3-arg calls in
  `ContentDownloadControllerProdTest` and `ContentDownloadAuthTest` are the other overload and must
  not be counted), `DownloadResolution.extension` (**0 main / 6 test, re-verified 2026-08-31** by
  `grep -rn '\.extension()' src/main/java src/test/java`),
  `CollectionRequests.Update`'s 17-arg constructor (21 test sites, not 23),
  `DiskUploadRequest.FileEntry`'s 3-arg constructor (13 sites, not ~20).
  **Those last two are UNCHECKED as of 2026-08-31 and remain the 2026-08-24 figures.** Raw
  `grep -rn 'new CollectionRequests.Update(' src/test/java | wc -l` returns **24** and the
  `FileEntry` equivalent **28**, but neither discriminates arity, so neither confirms nor refutes 21
  and 13 -- running a different command than the item's is exactly how a count gets disputed across
  passes (working rule 31). #256 edited `ContentService`, but its diff touches none of these three
  symbols, so they sit outside the merge neighbourhood. Re-derive by arity before scheduling MR 25. Also listed under MR 25
  below, where the counts and two corrected premises now live.

  **`AuthPrincipal`'s 4-arg constructor was a fifth entry and has been removed from this list**: it
  is **not** main-dead. `SessionService` calls it -- which the old entry admitted two lines below a
  "zero `src/main` callers" heading. Disposition is now a decision, not a deferral: leave it. All **36**
  call sites (re-measured 2026-08-29: 35 test plus `SessionService.java:179`; the old 30 was stale)
  are one-liners, and deleting a 3-line convenience constructor to append `, null` at 35 clean
  sites is not an improvement.
- [ ] **V19's `admin_home_tile.cover_image_id`** -- **research COLD, disposition still a decision.**
  Verified 2026-08-24: `AdminHomeTileRepository` is the only Java that touches `admin_home_tile`,
  its sole statement is `SELECT tile_key, display_order`, and its `TileRow` record carries those two
  fields only. There is no INSERT or UPDATE of the table from Java anywhere. V19 names the column in
  its seed INSERT but every one of the ten seeded values is an explicit `NULL`, so "written by
  nothing" is substantively right -- the only write the column has ever had is a NULL.

  *(A first pass at this entry claimed "real values sit there and are never read". That was wrong --
  reading V19's `VALUES` list corrects it, and the "Decisions needed" row had it right all along.
  Recorded because the doc's existing row was more accurate than the fresh check, which is the
  reverse of the usual failure and worth not forgetting.)*

  Consequence: the column cannot have received a value through the application at all, so the
  confirmation query the decision row suggests only guards against a manual DB edit. Disposition is
  one migration either way -- `ALTER TABLE admin_home_tile DROP COLUMN cover_image_id;` or a comment
  marking it reserved. Recommend dropping. Still listed under "Decisions needed" because it is a
  schema call, not because anything is unresearched.

### Bugs filed after the waves closed (2026-08-29)

- [ ] **Bug #18 (low-medium) — `updateLocation` misses the slug-uniqueness check the create path
  has, so a slug collision dies at the DB with a misleading generic 409.** *(Filed 2026-08-29 from
  the frontend board's E16 archive (`docs/spikes/2026-summer-refactor/group-e-consolidations.md` in
  `edens.zac`), which found it in passing -- "That is a backend 500, and it belongs on the backend's
  board, not this one" -- and it was never filed here until now.)* Verified in source:
  `MetadataService.updateLocation` checks `findByLocationNameIgnoreCase` only, then writes
  `SlugUtil.generateSlug(locationName)`; V8 has `CREATE UNIQUE INDEX idx_location_slug`; the
  find-or-create path in `LocationRepository` consults `findBySlug` first and the admin update path
  does not. Two distinct names that slugify identically ("St. Moritz" / "St Moritz") pass the name
  check and hit the unique index inside `LocationRepository.save`. **One premise correction to the
  archive's account: this is not a 500 today.** `LocationRepository` is a JdbcTemplate DAO, so the
  violation surfaces as `DataIntegrityViolationException`, which `GlobalExceptionHandler` maps to a
  generic **409** ("Data integrity violation: duplicate or invalid data") that never mentions the
  slug. Fix shape: mirror the create path's slug check before the save and return the same conflict
  shape the name check uses. **COLD.**
- [x] **Bug #19** (low) location-tagged GIFs could never surface on `/location/{slug}` — [#258](https://github.com/themancalledzac/edens.zac.backend/pull/258),
  2026-08-31. **Direction answered by the user: surface, not refuse.** The orphan queries are now
  predicated on `content_type IN ('IMAGE', 'GIF')` instead of joining `content_image`, and
  `LocationPageResponse.images` widened to `List<ContentModel>`. **This is a cross-repo wire change
  — see the cross-repo row below.**
- [x] **Bug #20** (low) `shutdown()` stopped both executors and awaited one — [#255](https://github.com/themancalledzac/edens.zac.backend/pull/255),
  2026-08-31. Both are stopped before either is waited on, so the grace periods overlap. Executor
  construction and the virtual-thread choice untouched; the cost report on unifying them is in the
  PR and summarised in history — **the answer was do not unify**.
- [x] **Bug #21** (low) the dimension fallback failed soft and wrote `0` — [#249](https://github.com/themancalledzac/edens.zac.backend/pull/249),
  2026-08-31. Both defaults are `null`; frontend fallbacks untouched. Write-up, and the
  mock-default trap that made the first version of its test unable to fail, in
  [history](2026-08-22-backend-cleanup-history.md#2026-08-31-close-out--s-14-s-16-s-22-s-23-s-24-and-bug-21).

## Cross-repo findings owed to the frontend — one open (2026-08-31)

The 2026-08-24 batch closed and lives in
[history](2026-08-22-backend-cleanup-history.md#cross-repo-findings-owed-to-the-frontend). This
section was re-opened by #258.

- [ ] **FE: the location page's `images` array can now carry GIFs, and the component types it
  `ContentImageModel[]`.** *(Filed 2026-08-31 from [#258](https://github.com/themancalledzac/edens.zac.backend/pull/258); working rule 43's evidence.)*
  `LocationPageResponse.images` widened from `List<ContentModels.Image>` to `List<ContentModel>`, so
  a location-tagged GIF now serializes into that array with `contentType: "GIF"`. Verified in
  `edens.zac`: `app/components/LocationPage/LocationPageClient.tsx:29` declares
  `images: ContentImageModel[]`, and `app/components/LocationPage/LocationPage.tsx:14` the same, so
  the array is narrowed at both levels and a GIF entry is a type mismatch that TypeScript will not
  catch at runtime.

  **Not yet live**: nothing is location-tagged as a GIF today, so there is no active breakage to
  race. Responses are byte-identical until an admin tags one.

  **Fix shape**: widen both props to the mixed content model and switch on `contentType`, the way
  the collection content renderer already does — `LocationPageClient` already imports
  `ContentBlockWithFullScreen` and `processContentBlocks`, which handle mixed blocks, so the change
  is at the type boundary rather than in the rendering.

  **NOT FILED ON THE FRONTEND BOARD.** `edens.zac` was on branch
  `0368-board-closeout-pf9-pf11-pf8` with a dirty working tree when this close-out ran, and filing
  would have meant committing into another session's in-progress branch. This is a declared
  decline, not an oversight — **the next session that touches `edens.zac` must add a row to
  `docs/spikes/2026-summer-refactor.md`**, and until it does this finding is invisible in the repo
  it affects, which is the exact failure this section exists to prevent.

## Open security findings

Consolidated 2026-08-24 by the full-board review; re-attacked as a merged set 2026-08-25 and again
2026-08-29 (adversarial -- 0 HIGH, 0 MEDIUM returned; the set holds as a set). **ZERO open as of
2026-08-31**, and none of the last five closed on a deferral: S-14 and S-24 were answered, S-16 was
answered and then built, S-22 and S-23 shipped. **This is the first time this section has been
empty since it was created on 2026-08-24.** The six most recent closes have their outcomes in
[history](2026-08-22-backend-cleanup-history.md#2026-08-31-close-out--s-14-s-16-s-22-s-23-s-24-and-bug-21). **Twenty-five closed**: one ledger line each below; bodies, outcomes and
the 2026-08-25 "reopened" context are in the history file
([Security findings -- closed](2026-08-22-backend-cleanup-history.md#security-findings--closed-moved-2026-08-29)).
Per-path limiter mapping context -- which limiter covers which route -- sits in history's
[S-17 outcome](2026-08-22-backend-cleanup-history.md#s-17-outcome-2026-08-28----not-as-specified-and-two-failures-of-the-same-kind).

### Closed, one ledger line each

Bodies and outcomes in the [history file](2026-08-22-backend-cleanup-history.md#security-findings--closed-moved-2026-08-29).

- [x] **S-1** (HIGH) `UserStatus.DISABLED` enforced nowhere in the auth path — #192, 2026-08-24. Taught rule 16.
- [x] **S-2** (MED) `repointMemberships` bypassed the `addMember` rule — #193, 2026-08-24. Taught rule 17.
- [x] **S-3** (HIGH) the delete-person guard had no test that can fail — #195, 2026-08-24; the surviving-side gap closed by #235, 2026-08-28.
- [x] **S-4** (HIGH) `ProdSecretGuard` could be unwired silently — #196, 2026-08-24.
- [x] **S-5** (LOW) chunked bodies bypassed the public body cap — #206, 2026-08-24. **Live caveat**: the `length < 0 && Transfer-Encoding present` conjunct stops being exact if http2 is ever enabled (a DATA-frame body needs no `Transfer-Encoding`); http2 is off today.
- [x] **S-6** (LOW) `effectiveLevel` overclaimed and an admin got bounced — #207, 2026-08-24. Rule 20's origin.
- [x] **S-7** (MED) two more session-minting paths read no status — #199, 2026-08-24. Taught rules 18-19.
- [x] **S-8** (LOW) `updateStatus` did not revoke live sessions — #204, 2026-08-24.
- [x] **S-9** (LOW) disabling did not invalidate outstanding invites — #200, 2026-08-24.
- [x] **S-10** (HIGH) an admin-issued reset invite survived an email change; redeeming it was takeover — #221, 2026-08-25.
- [x] **S-11** (HIGH) `ACCESS_TOKEN_SECRET` had a public default and no startup guard — #222, 2026-08-25.
- [x] **S-12** (MED-HIGH) dormant `role_member` rows on a PERSON became live grants on upgrade — #225, 2026-08-26.
- [x] **S-13** (MED) the admin update endpoint accepted `status: PERSON` — #227, 2026-08-27.
- [x] **S-15** (MED) completing a password reset did not revoke the account's other sessions — #224, 2026-08-26.
- [x] **S-17** (MED) `share/email` was an authenticated open mail relay — #233, 2026-08-28, **not** as specified (dedicated `ShareEmailLimiter`); taught rule 35.
- [x] **S-18** (MED) the actuator exclude missed four endpoints meeting its own criterion — #232, 2026-08-28; taught rule 34. **The exclude list is still criterion-incomplete** (2026-08-29): `metrics` (and `info`) meet the stated criterion under `include=*` and sit in neither the exclude nor `MUST_BE_EXCLUDED`, and both tests derive from the same enumeration, so neither can see it. **S-23 above is the chosen fix shape** — the resolved-include boot check, not another name-chase.
- [x] **S-19** settled 2026-08-25, not live — the FE strips and re-derives `x-real-ip`. **Live debt**: `ClientIp`'s javadoc still calls the header's presence "the trust signal"; correct that docblock when next in the file.
- [x] **S-20** (MED) "may hold a session" was inlined in two files beside the predicate — #230, 2026-08-28; taught rules 31 and 33.
- [x] **S-21** (LOW) `regenerateInvite` minted links for accounts that can never redeem — #228, 2026-08-27.
- [x] **S-14** (MED) an admin could put any collection, including another client's protected gallery, into their own share scope — **answered, not patched**: no second gate ([#250](https://github.com/themancalledzac/edens.zac.backend/pull/250), 2026-08-31). Leaves open whether `addCollection` should be admin-gated at all; that is a routing question and needs its own item.
- [x] **S-16** (MED) disabling an account did not stop its share link serving — #253, 2026-08-31. **Suspend, not revoke.** Shipped as one gate at `resolveByRawToken`, not the two the item specified; the item had also missed `isCollectionInScope`.
- [x] **S-22** (LOW) the two role-membership status guards were separate SQL denylists — #247, 2026-08-31. Shipped as one bound list, **not** the named predicate the item prescribed; taught working rule 40.
- [x] **S-23** (LOW) nothing refused a prod boot with a wider actuator include — #248, 2026-08-31. Exclude list untouched and now redundant; **whether to delete it is an open disposition**.
- [x] **S-24** (LOW) two admin mail-send paths were covered by no limiter — **accepted as admin-trusted and documented** ([#250](https://github.com/themancalledzac/edens.zac.backend/pull/250), 2026-08-31).

### Classification of the still-open items

**Empty as of 2026-08-31 -- there are no open security findings to classify.** The table is kept
rather than deleted because the section has refilled twice before, and its shape is the thing that
made the two blocked items answerable: each row named the question and who answers it, in the form
the user could act on. Both were answered within a day of being written that way.

The last five rows, and what each closed as, are the ledger lines above. The DONE rows of the
2026-08-25 table moved to history 2026-08-29 with the bodies; the 2026-08-31 rows moved with theirs.

**One thing worth carrying forward past the section being empty.** S-14's answer did not fit either
option the question offered -- it was a principle ("all admin endpoints through the same gate")
about a different endpoint class than the one the item was about. It was recorded as closed **with
the gap named**, rather than forced into "allow, documented". A question that comes back with an
answer to a slightly different question is still an answer; write down which question it answered.

*(Tests that cannot fail closed 2026-08-30 -- all six. Full write-ups, mutation results and the two
premise corrections:
[history](2026-08-22-backend-cleanup-history.md#tests-that-cannot-fail--closed-2026-08-30-moved-from-the-tracker).)*

### Unsettled, and how to settle each (2026-08-25)

- [ ] **Whether prod actually runs the `prod` profile.** The deployment docs contradict each other:
  `ai_deployment_strategy.md` says `SPRING_PROFILES_ACTIVE=prod`, `ai_ec2.md` says `default` in two
  places. Under `dev`, `enforce-authz=false` and `SecurityConfig` falls through to `permitAll` on
  `/api/admin/**` and `/api/edit/**`, and neither `ProdSecretGuard` nor `InternalSecretFilter`
  exists to object. S-4 proved the `@Profile("prod")` wiring works; **nothing proves prod is named
  prod.** Settle by reading `SPRING_PROFILES_ACTIVE` on the live host, or by hitting the origin
  without `X-Internal-Secret` and confirming a 403. **Re-scoped 2026-08-25:** S-19 settled and S-11's
  fix shipped, so this no longer gates either fix. What it now gates is whether S-11's guard *runs* --
  `ProdSecretGuard` is `@Profile("prod")`, so on a host named `default` the new
  `app.access-token.secret` clause is as absent as the rest of it. It is a live question about a
  live host and the only one on this board that can be answered with a single request; it survives
  only because it needs credentials or network access this session did not have.
- [ ] **Whether Tomcat surfaces `Transfer-Encoding` to `getHeader()`.** S-5's entire fix depends on
  it, and its only test uses `MockHttpServletRequest`, which returns whatever the test put in. If
  Tomcat consumes the header while installing the chunked input filter, the branch never fires and
  the bypass is still open. Settle with an integration test that POSTs a real chunked body to a
  booted server and asserts 411 -- `ActuatorExposureEndToEndTest` already has the shape.
- [ ] **`ACCESS_TOKEN_SECRET` has no rotation story.** It keys gallery HMAC tokens, gallery password
  fingerprints, and now share-token confidentiality. Rotating it makes every stored ciphertext
  unreadable, so the key now guarding at-rest confidentiality is one nobody can rotate -- a suspected
  compromise cannot be remediated without every owner resetting their link. Related and worth stating
  plainly: the deployment docs put the `.env` and the nightly `pg_dump` backups in the same home
  directory on the same host, so the encryption buys real protection against an S3-only leak and
  roughly one `cat` against host compromise.

  **Partly addressed 2026-08-25 by #222, and worth being precise about which part.** `.env.example`
  now states the rotation cost at the point of configuration -- rotating invalidates every stored
  share link and every live gallery unlock cookie. That makes the cost visible; it does not make the
  key rotatable. The question stays open, and it is now the last unaddressed half of S-11.

  **This bullet is also where S-11's missing fact was sitting.** It listed all three uses of the
  secret while S-11's own severity paragraph named only `TokenCipher`. See S-11's outcome in the
  [history file](2026-08-22-backend-cleanup-history.md#s-11-outcome-2026-08-25----the-guard-clause-and-a-fact-the-board-already-had).
- [ ] **`SessionService.resolve` slides the session window before reading status**, so a non-ACTIVE
  account's session gets its expiry pushed forward before rejection. Latent only because S-8 revokes
  on every path that reaches it. Reordering the two blocks costs nothing.
- [x] **`RoleRepository.canView` and `isClient` have zero `src/main` callers -- confirmed 2026-08-25**, after S-6 routed
  everything through `effectiveLevel`. They are the bug S-6 fixed, still sitting in the DAO under the
  right names and still green in tests. Wave 1 deletion candidates, and the names are the hazard.

### Verified sound, do not re-open

Attacked 2026-08-24 and again 2026-08-29; held both times. Index only -- the full reasoning lives
in the [history file](2026-08-22-backend-cleanup-history.md#security-findings--closed-moved-2026-08-29):

- **`addMember`'s `<> 'PERSON'` denylist: do not tighten it to an ACTIVE allowlist** -- S-1's
  shipping falsified the live-grant argument for one. S-22 pins the rule and names the predicate
  without changing membership.
- **The #189 `/api/read/user/**` matcher** and **the flyby-principal invariant** hold as recorded.
- **Do not unify `mayHoldSession` and `mayAcceptInvite`** -- re-verified against code 2026-08-29:
  `finishLogin` reads status fresh through the predicate, and the only non-breaking unification
  direction widens `mayHoldSession` to admit INVITED, under which the passkey door is a live hole.
- **Nothing lets a session or passkey outlive a status change today** -- `resolve` re-reads status
  and `isAdmin` on every request; deactivation sweeps then backstops; no path hard-deletes an
  ACTIVE account. (The resolve-slides-the-window-first latent stays under "Unsettled".)
- **`ShareEmailLimiter` keying and placement are correct** -- session-derived `userId`, checked
  before the token lookup, disjoint key space from the other four limiters.
- **The S-set mutation pins are resistant** -- S-20's enum pin, S-3's #235 parameterization plus
  literal pin, S-17's 429 pin with the refill-observability fix, S-18's containsExactly both ways.

## Working rules

Learned while doing the MRs; most apply to every item still open. **Distilled 2026-08-29** -- the
full narratives, evidence and corollaries moved to the history file under
[Working rules -- original narratives](2026-08-22-backend-cleanup-history.md#working-rules--original-narratives-moved-2026-08-29).
**Rule 37 governs all comments** -- it supersedes rules 6 and 12, kept below as tombstones; rule 4
was absorbed by rule 5. Numbering is stable: items and history entries cite rules by number.

1. **A property default in `application.properties` is probably dead.** `docker-compose.yml`
   injects the same keys unconditionally, and an injected env var outranks the property. Grep
   `docker-compose.yml` for the key before treating any config default as live. (MR 9, bug #9.)
2. **`src/test/resources/application.properties` shadows the shipped file on the test classpath.**
   A test asserting on shipped config must read `src/main/resources` directly or it passes
   vacuously against the stub.
3. **`GlobalExceptionHandler` maps `IllegalArgumentException` AND `IllegalStateException` to 400.**
   Only the catch-all produces a 500; a bare `RuntimeException` is the only route to one without a
   new exception type.
4. **RETIRED 2026-08-29 -- absorbed by rule 5.** (It said: line numbers are from `8c28cf3` and
   drift; find symbols by name; re-verify a `file:line` before quoting it.)
5. **An item that is nothing but a list of line numbers is already dead.** Line refs are from the
   `8c28cf3` baseline and drift -- find symbols by name and re-verify any `file:line` before
   quoting it as evidence (absorbs rule 4). For any list-of-refs item, re-derive the list
   mechanically from the current tree and treat the doc's list as a sample of intent, not a
   worklist. (Of ~130 refs in open items on 2026-08-24, only 38 were exact.)
6. **SUPERSEDED 2026-08-28 by working rule 37 -- do not follow.** It said leave trailing
   `code; // note` comments alone in comments-only MRs; under rule 37 they are deleted whenever
   the file is edited for any reason. Full text in history.
7. **A stale comment is not automatically a bug report.** Across MR 12's three files, 349 comments
   produced exactly one real bug. Verify the claim against the code before filing; "none found" is
   a correct outcome for a file, not evidence of a shallow pass.
8. **A `P:` judgment note decays the same way a `D:` line number does -- and an item's own landing
   can falsify its side-arguments.** Verify a note's claim against current code before acting on
   it, and re-check any bundled "this also settles X" *after* the item lands.
9. **Commit with explicit paths, never `git add -A`.** This repo carries untracked docs in
   `ai_docs/reviews/`; MR 12c's `-A` swept one into #180. Do not delete
   `ai_docs/reviews/2026-07-25-open-pr-review.md` --
   [Appendix B](2026-08-22-backend-cleanup-history.md#appendix-b--prior-review-scorecard) is scored against it.
10. **Measure a comment MR in words, not lines.** Before promoting a comment, read the docblock
    you are promoting into and the one on its nearest public caller; if either already says it,
    the comment is a delete. State a rule once, where it is enforced.
11. **Outcome write-ups go in the history file, not the tracker.** When an MR closes, the tracker
    gets one line -- status, PR link, any working rule taught; everything else goes to
    [`2026-08-22-backend-cleanup-history.md`](2026-08-22-backend-cleanup-history.md). Reconcile
    unticked boxes on the way out, not on the way in.
12. **SUPERSEDED by rule 37 (2026-08-28) -- do not follow; full text in history.** The files it
    protected (`RoleRepository`, `AdminBootstrap`, `CollectionControllerProd`, `SecurityConfig`)
    still carry its comments and take the first rule-37 sweeps.
13. **A guardrail decays like a line number, and a re-derivation is not self-verifying.** Check a
    guardrail's premises against the code before obeying it; confirm a re-derived count by making
    the change and watching it come out even. The tell for a wrong sweep is one that reports zero
    corrections.
14. **Re-derive a duplication item by its code shape, not its helper name.** A renamed or inlined
    copy never surfaces in a name-grep -- grep the body. "Four copies" and "four helpers named X"
    are different claims; say which one the number is.
15. **A regression test that cannot fail is worse than none -- it reports coverage.** Prove a
    guard test by mutating the guarded thing and reading the red; a fix in SQL, an annotation, or
    wiring cannot be guarded through a mock of that layer. Mutate with
    `-Dspotless.check.skip=true`; `touch` restored sources so stale `.class` files don't lie.
    Evidence: S-3/S-4 -- 1,304 green with the delete guard stripped and `@PostConstruct` gone
    (closed #195/#196).
16. **Count the callers of the thing being guarded, not the callers the item named.** Grep the
    operation (`sessionService.create`), not the endpoint the reviewer happened to read. A guard
    at the read chokepoint covers entry points you failed to enumerate; a guard at an entry point
    covers only itself. (S-1's scoping found S-7.)
17. **Put the guard in the statement, not in the caller's precondition.** A statement-level guard
    can express "do this much of it"; a caller precondition must be restated by every future
    caller. Check what the schema does unaided (CASCADE) before inventing a policy. (S-2.)
18. **An allowlist's form and its membership are two claims -- the item usually checked one.**
    Enumerate every path that legitimately reaches the guard and read what those callers say about
    who they serve. A wrong allowlist fails *closed* and surfaces weeks later as a broken feature,
    far from its MR -- test the legitimate case. (S-7: "require INVITED" would have killed
    admin-issued password reset.)
19. **Controllers map results to status codes; everything else is a service -- and no `//` inside
    either.** A controller test that has to mock a `PasswordEncoder` is telling you the logic is
    in the wrong file. Prefer naming the rule (a predicate like `mayAcceptInvite`) over explaining
    it.
20. **Admin means owner** (user ruling, 2026-08-24): an admin bounced by a password prompt or a
    permission check is a bug, not a safe default. **This does not generalize to account status**
    -- ACTIVE-only allowlists answer "is this account alive at all", not "what may a live admin
    reach", and stay right.
21. **An item's premise is evidence. Its prescribed fix is a hypothesis.** S-7, S-8, S-5 and S-6
    each had a correct premise and a fix that would have shipped a bug verbatim -- usually via an
    input or principal the item never named. Ask what the item did not enumerate before typing its
    fix in.
22. **A comment claiming a protection exists is a claim to check, not documentation to trust.** A
    stale behavior comment gets corrected by the next reader; a stale *protection* comment
    survives until someone needs it to be true -- and the test named for the protection may be the
    one that cannot fail. Find the line that does the stripping/gating; a test whose own fixture
    supplies the result is not evidence.
23. **"My merge did not move this ref" is not "this ref is correct."** The scoped sweep selects
    which refs to verify; it does not certify the ones your diff left alone. Open the file and
    match the anchor text every time. A sweep reporting zero corrections likelier asked the wrong
    question than found the board finally accurate.
24. **A specified fix can be impossible, not merely imprecise.** Name the inputs the fix consumes
    and confirm each is reachable from where the fix will run before scheduling it as next.
    (`share/email`: verified from both sides of the repo boundary, and the endpoint would still
    have had nothing to put in the email.)
25. **When a hardening rests on a framework ordering guarantee, test the guarantee, not the config
    string.** Boot with the adverse input (`include=*`) and mutate the config to prove the
    assertion distinguishes the two worlds. (#214.)
26. **Replacing a drifted line range with a fresher line range is not de-positionalizing it.** The
    output of re-deriving a ref is a name. Where a number genuinely helps a reader, stamp it --
    "`465-495` as of #216" -- so the next session can tell a fact from an artifact. The tell is a
    bullet that says "de-positionalized" and still contains a colon followed by digits.
27. **An item specified while its own open question is still open is the one that needs
    adjusting.** "Verify X first" is the whole item until it is done. Discharge the question first
    -- on every branch the code has, not just the one the question named -- then specify. (MR 19
    #14 broke the six-item adjustment streak exactly this way.)
28. **A stacked PR whose base merges first strands the work on a dead branch.** Do not stack a
    docs PR on an open PR. And "the PR is merged" is not "the change is on `main`" -- verify with
    `git log origin/main --grep` or by grepping `origin/main`'s copy of the file.
29. **A cost report enumerates every consumer of the thing being quarantined: grep the thing (the
    predicate, the property key), not the class the item discusses -- and grep this board for the
    symbol first.** Evidence: S-10's report missed `invalidateInvitesForStatus`; S-11 missed
    `ClientGalleryAuthService`, already on the board's own "Unsettled" bullet.
30. **A sweep keyed on the row's current state guards one direction of a transition, and its
    placement picks the direction.** Say out loud which transition a state-reading guard covers,
    and check whether the uncovered direction is somebody else's open item; an input-end
    constraint often beats either placement. A shipped item does not subsume a neighbour that
    names the same method. (S-12/S-13.)
31. **Record a count's command exactly as run, escaping included** -- an unescaped `.` matches `#`
    and every separator a Java ref uses; comments add phantom hits. State counts "N raw, M code".
    Evidence: three passes disputed S-20's count while running different commands; the 2026-08-27
    correction was itself wrong.
32. **A mutation that reddens the test is not evidence until you check *why* it reddened.** The
    mutant must fail at the guard and then do the wrong thing; a mutant dying on a fixture gap
    proves the fixture is thin. Restore with `touch` (stale `.class` files lie), and mutate with
    `-Dspotless.check.skip=true` -- `mvn test` runs spotless before the tests.
33. **A test deriving its cases from the thing under test cannot see that thing widen** -- pair
    every derivation with one literal pin where the definition lives, and watch the case count
    under mutation: green with fewer cases is a failure wearing a pass. Evidence: `mayHoldSession`
    -> `!= DISABLED` left both login suites green at 13->11 and 12->10 cases (#230).
34. **An allowlist is not defence in depth when the allowlist is the thing an attacker
    overwrites.** An injected `INCLUDE=*` replaces the include value outright; the exclude applies
    after it and survives. Before replacing a denylist with an allowlist, ask what overrides the
    allowlist. **The follow-up it named is now filed as S-23** (the resolved-include boot check).
35. **A green unit-test run is not evidence a wiring change works.** Mutate the test you just
    wrote -- a test observing the system only in a state where every variant behaves identically
    cannot fail -- and run the full build: a bean's own unit tests cannot see a context-start
    failure by construction. (#233 hit both in one MR.)
36. **The two security-count cells drift because they are edited one at a time.** The check is
    `grep -c '^- \[ \] \*\*S-'` -- run it, put the number in both cells, and never edit one
    cell without the other. (Returns **5** as of 2026-08-29.)
37. **Never write inline comments. This supersedes rules 6 and 12.** Standing instruction from the
    user, 2026-08-28. No `//` inside method bodies, constructors, test methods, or against fields
    -- no threshold of importance earns one. Javadoc is the only prose in a source file; anything
    that does not fit a docblock belongs in the PR description or this document. When editing any
    file for any reason, delete the inline comments already in it. The comments that feel
    load-bearing are the signal, not an exemption.
38. **A close-out is two files: it is not done until the history file has the outcome.** A
    close-out MR touching only one of the two files is wrong on its face -- `git show --stat`
    must list both; if there is genuinely no outcome to record, say so in the commit message.
39. **A commit pushed to a branch after its PR merged goes nowhere, silently.** After pushing to a
    branch whose PR you did not just open, confirm the PR is still OPEN -- `gh pr view <N> --json
    state`. A green build on a dead branch proves nothing about `main`. (Filed as R-1.)

    **Broken again 2026-08-31, by a session that had this rule in its context.** Docblock trims were
    pushed to the S-22 and S-23 branches 15 and 20 minutes after both PRs squash-merged; the commits
    stranded, `main` kept the bad docblocks, and the status report said "pushed to all four
    branches", which was true and worthless. Two things make this rule easy to skip, and both are
    now part of it. **First: after a squash merge, `git log main..branch` still lists the branch's
    original commits as "ahead", so the branch looks unmerged. Only the PR state tells the truth.**
    Second, the user's instruction on the recurrence, which is the real trigger: *"especially if
    they're part of a list of MRs that were previously provided, the assumption is I'm MERGING
    THEM."* When a run was handed over as a list, assume each item merges as it lands -- the window
    is minutes. The fix when it happens is a fresh branch off current `main` and a new PR
    ([#251](https://github.com/themancalledzac/edens.zac.backend/pull/251)), never a force-push to
    the dead branch.

40. **A named predicate earns its name from call sites, not from a convention.** Extract a
    `boolean f(X)` when code calls it during a request. Do not extract one because a sibling rule
    has one, or because a board item used the word "predicate" -- that is API surface with nothing
    behind it. **Evidence:** S-22's item prescribed a named `mayHoldRoleMembership`; it was written,
    and removed on review with the objection *"why would we need a boolean for this? ... this seems
    pointless and likely adding bloat"*. `SessionService.mayHoldSession` earns its shape from four
    Java call sites; the role-membership rule has **zero**, because it is only ever asked in SQL
    over the whole set. The derived constant both SQL sites bind is the same single definition with
    one less name in the API. Corollary on naming: a modal like `may-` reads as *maybe* to a
    reader who did not write it. Where the rule is a set, name the set.

41. **A stale `target/surefire-reports` file reads exactly like a passing run.** When a mutation
    breaks the build before the tests -- an unused import after deleting an annotation, a
    checkstyle failure, a spotless reflow -- surefire writes nothing and the previous run's report
    is still on disk, with its old counts *and its old `Time elapsed`*. Grepping it returns a clean
    green. **Delete the report first** (`find target/surefire-reports -name '*TheTest*' -delete` --
    not `rm` with a glob, which aborts the whole command line under zsh when it matches nothing),
    **and check for a `Tests run:` line and the `BUILD` line, not just the counts.** Cost this
    rule twice on 2026-08-31: once reading 10/10 green from a build that never compiled, once on
    S-16. Extends rules 15 and 32, which cover mutations that redden for the wrong reason; this is
    the mutation that *greens* for the wrong reason.

42. **The rule-37 inline-comment count is a checksum -- reconcile its delta against your own
    diff.** A session that deleted comments should move the count by exactly the number of lines it
    deleted, and the arithmetic is worth doing because it verifies the count and the deletion at
    once. Evidence, 2026-08-31 (second close-out): main went 290 -> 262, and -28 is exactly 11
    (`ImageUploadPipelineService`) + 10 (`ContentService`) + 7 (`AdminUserController`); test went
    1,385 -> 1,382, and -3 is exactly 1 + 2 across the two test files edited. A delta that does
    *not* reconcile means either the count's command is wrong or a file changed that you did not
    think you touched -- both worth knowing before the number is written down as measured.

43. **An item scoped at the query layer has not priced the DTO the query feeds.** Bug #19 read
    "widen the orphan queries"; widening them changed `LocationPageResponse.images` from
    `List<ContentModels.Image>` to `List<ContentModel>` and broke a frontend component that types
    the field `ContentImageModel[]`. The SQL was the smallest part of the change. Before scheduling
    any item whose verb is "widen", "generalize" or "include X too", read the response record the
    query feeds and the consumer that destructures it, and price both.

---

# Wave 4 — Comments and docs

Closed except the stale-docblock items below. The wave rule, the measured preamble, MR 14's
disposition and the retro moved to the history file 2026-08-29
([Wave 4 detail](2026-08-22-backend-cleanup-history.md#wave-4-and-wave-5-tracker-detail-moved-2026-08-29); outcomes at
[Wave 4](2026-08-22-backend-cleanup-history.md#wave-4--mr-12-and-mr-13-complete),
[retro](2026-08-22-backend-cleanup-history.md#wave-4-retro--measured-in-words-2026-08-23) and
[MR 14](2026-08-22-backend-cleanup-history.md#mr-14-outcome-2026-08-23)). The wave removed 500 in-method comments for -1,026
words across seven MRs (#177-#187); its old "567 at indent >= 4" start-of-wave measure is
superseded by the 2026-08-29 re-measure in the Progress estimate table.

### Still open from MR 14 — stale docblocks

Out of scope here by design: this MR was in-method comment lines only. These are docblock rewrites,
and each needs its claim verified before acting (working rule 8).

- [x] `filterNonListedChildCollections` (`CollectionService`) describes a context-detection mode that no longer exists. **Premise flagged as possibly stale, 2026-08-25**; **CLOSED 2026-08-29 by reading**: the docblock describes the current flag-keyed derivation, names `findClientGalleriesAndQualifyingParents`, and explicitly warns against keying on `type == PARENT`. The 2026-08-25 flag was right -- nothing to rewrite.
- [ ] The "previously spread across ContentProcessingUtil" rename-history at `ContentModelConverter` and `ContentMutationUtil` -- that class is gone.
- [ ] "PARENT-shaped" vocabulary at `CollectionService`, `UserPageAssembler` -- dead since the enum deletion. **`TagViewResolver` does not contain that phrase** (2026-08-25); it says "synthetic PARENT model" and "tag-view PARENT model". The vocabulary point survives, the grep target does not.
- Moved 2026-08-24: `CollectionAccessService.effectiveLevel` is now **S-6** under "Open security findings" -- it is an access-control item, not a docblock rewrite, and the re-review found it fails closed rather than leaking.

---

# Wave 5 — Consolidations

## MR 15 — Cross-cutting

- [x] #1. One client-IP resolver. **DONE** -- shipped with bug #3 in MR 5 ([#165](https://github.com/themancalledzac/edens.zac.backend/pull/165)).

- [x] #2. One SecurityConfig matcher instead of the copy-pasted `isRealUser` guards. **DONE** ([#189](https://github.com/themancalledzac/edens.zac.backend/pull/189)) — 17 guards (not 18; the re-derivation had counted a javadoc line) became one matcher, placed outside the enforce-authz toggle as the only behavior-preserving option. [Full write-up](2026-08-22-backend-cleanup-history.md#mr-15-2-outcome-2026-08-23); tracker detail moved to history 2026-08-29.
- [x] #6. `currentUserId` is duplicated. **DONE** ([#191](https://github.com/themancalledzac/edens.zac.backend/pull/191)) — four copies became `config/CurrentUser.userId()`; "move it onto `AuthPrincipal`" was rejected with a reason; taught working rule 14. It also closed the `PersonRepository` carry, whose guard's bypass later became S-2 (#193). [Full write-up](2026-08-22-backend-cleanup-history.md#mr-15-6-outcome-2026-08-24); tracker detail moved to history 2026-08-29.

### The MR 15 #6 follow-up — closed 2026-08-24

- [x] Fold the last two copies of the same static read into `CurrentUser`. **DONE**
  ([#210](https://github.com/themancalledzac/edens.zac.backend/pull/210), squash `c1f482e`) — the
  MR 15 #6 thread is fully closed, four sessions after it opened; **the
  `getContext().getAuthentication()` grep returning four `src/main` sites is MR 15's completion
  condition and is satisfied**. Coverage was proven by mutation, not assumed.
  [Full write-up](2026-08-22-backend-cleanup-history.md#currentuser-fold-outcome-2026-08-24----the-mr-15-6-thread-closes-four-sessions-later);
  tracker detail moved to history 2026-08-29.

## MR 16 — Infrastructure classes

- [ ] #3. One keyed rate limiter. **Re-derived 2026-08-24: three copies, not two** -- `RateLimitFilter.newBucket` is a third byte-identical Caffeine+Bucket4j core. **Two halves of the original wording were wrong and are corrected here.** "The same class twice" is false: `ContactMessageLimiter` carries a global daily bucket that a `KeyedRateLimiter(capacity, window, idleTtl)` signature has no slot for, and its own docblock calls that bucket the only limit an attacker cannot pick the key for. "Their TTL policies have already drifted" is also false: `ClientGalleryAccessLimiter`'s `window + 15min` idle TTL is a documented deliberate choice (an attacker must not reset it by pausing), and calling it drift invites someone to "fix" it to 2h and weaken it. **Cost is test-dominated: ~-55 source against ~84 test sites** (7 constructor sites + 24 calls in `ContactMessageLimiterTest`, 7 + 32 in `ClientGalleryAccessLimiterTest`, plus `CollectionControllerProdTest` and `MessagesControllerPublicTest`). Keep `AuthLoginLimiter` separate -- it is a `Cache<String,Integer>` counter, not Bucket4j. Low priority.

  **Cost re-measured 2026-08-24 while doing S-5, which was told to leave these cores alone. Every number above held, and one new blocker turned up.** The test-site counts are exact, not approximate: `ContactMessageLimiterTest` has 7 constructor sites and 24 `tryConsume` calls, `ClientGalleryAccessLimiterTest` has 7 and 32 `.allow(` calls -- 70 in the two dedicated tests, plus `CollectionControllerProdTest` and `MessagesControllerPublicTest`. Source is 82 + 81 lines across the two classes, and the shared part of them is small: the bucket shape (`Bandwidth.builder().capacity(n).refillIntervally(n, window)` wrapped in `Bucket.builder().addLimit(...)`) and the `Caffeine.newBuilder().maximumSize(10_000)` cache. Everything around it differs.

  **The new blocker is `Retry-After`.** `RateLimitFilter` does not just ask its bucket a yes/no question -- it calls `bucket.estimateAbilityToConsume(1).getNanosToWaitForRefill()` to build the header (the only such call in the codebase). A `boolean allow(String key)` signature, which is the shape the other two callers want, cannot serve it. A merged class has to expose the `Bucket` or a nanos-to-refill accessor, and that is a wider API than the item's framing implies.

  **Four more things that do not merge**, all found by reading the three call sites rather than the class list. (1) Three different key functions: `email.trim().toLowerCase(Locale.ROOT)`, `ip.trim() + "|" + GalleryAccessCookies.normalizeSlug(slug)`, and `ClientIp.resolve(request)` -- so the shared class takes a pre-computed key and each caller keeps its own normalization, which is most of what looked like the duplication. (2) Three different blank-key policies: `ContactMessageLimiter` skips the per-email bucket but has already spent a global token, `ClientGalleryAccessLimiter` returns true, `RateLimitFilter` has no blank case. (3) The idle TTL cannot have a default -- `ClientGalleryAccessLimiter`'s `window + 15min` is deliberate and the other two are a fixed 2h, so it must be an explicit constructor parameter, which is the parameter most likely to be got wrong later. (4) `ClientGalleryAccessLimiter`'s package-private `Duration` constructor exists so refill-timing tests can use sub-second windows instead of sleeping for minutes; it has to survive the merge intact.

  **Verdict unchanged, with more confidence behind it: not worth doing.** The merge saves roughly 50 source lines, needs a wider API than a boolean, and rewrites ~70 test call sites -- and the four items above are each a way to quietly weaken a live limiter while the suite stays green. S-5 no longer collides with it; that file is settled.
- [x] #4. One AWS config class. **DONE 2026-08-31 (third run).** `S3Config` and `SesConfig` are one
  `AwsClientConfig` with a shared `AwsCredentialsProvider` bean; 127 source lines became 89, and both
  catch-log-rethrow blocks are gone. All four `@Bean` method names are unchanged, so every by-type
  injection is untouched. **The zero-test-coupling claim was re-verified before starting, as the
  board asked, and it held — but it was incomplete in a way that mattered.** No test imports,
  constructs, `@Import`s or `@MockBean`s either class; the only `src/test` mention was a comment in
  `application-test.properties`, updated in the same commit. What the claim left out: **51 test
  classes load the full context and instantiate both configs**, and they start only because
  `application-test.properties` supplies `aws.access.key.id`, `aws.secret.access.key` and
  `aws.s3.region`. So the merge is free *only while those three property keys are unchanged*.
  Renaming `aws.s3.region` to a neutral `aws.region` — the tidy-looking move, since SES borrowing an
  S3 key is the thing this item complained about — fails all 51 at context load. **The key was
  deliberately left as `aws.s3.region` and the class docblock says why**, so the next reader does not
  "finish" the job. Test churn was zero Java lines, exactly as promised; 1450 tests, unchanged count.
  Prior text: zero test coupling -- nothing in `src/test` references `S3Config` or `SesConfig`, and there is no `@Import`, so the rename to `AwsClientConfig` is free. Premise verified intact 2026-08-24. `config/SesConfig.java` duplicates S3Config's credentials plumbing and borrows `aws.s3.region` for a non-S3 client. Merge the SesV2Client bean into S3Config (rename it `AwsClientConfig`), share one `AwsCredentialsProvider` bean across the four clients, and delete the catch-log-rethrow blocks. ~40 lines.
- [ ] #5. One CloudFront invalidation implementation. **The item undersells itself**: `cloudFrontClient` and `cloudFrontDistributionId` are used only inside `invalidateCloudFrontPaths`, so delegating removes two constructor dependencies (arity 10 -> 9). Test cost is ~4 lines and no mock or verify is rewritten. **Trap**: route through `invalidatePaths(List<String>)` as written -- routing through `markChanged()` swaps specific keys for two wildcards and defers to after-commit, which is a behavior change. `ImageProcessingService.invalidateCloudFrontPaths` (**declaration at `869` as of 2026-08-31**, was 865-885, before that 838-863 -- +4 from #249's docblock; find by name) re-implements what `services/ReadCacheInvalidator.java:~79-106` already owns. Give `ReadCacheInvalidator` an `invalidatePaths(List<String>)` and delegate. ~25 lines.

## MR 17 — Controllers

- [ ] #7. Admin image list duplicates the prod image search — same 12 `@RequestParam`s, same service call, different response wrapper (`AdminController.getAllImages` (**`258-294` as of #218**) vs `ContentControllerProd.searchImages` (`45-77`, correct)). Bind the filter once with a shared `@ModelAttribute` record, reuse prod's constraints, return one response type. **"Reuse prod's constraints" is an unpriced behavior change**: admin clamps with `Math.min(Math.max(size, 1), 200)` while prod validates with `@Min/@Max`, so admin `size=500` goes from silently returning 200 rows to a 400; defaults also differ (50 vs 30), and two frontend pages that pass no `size` would jump from 30 images to 50. **Do MR 19 #19 first** -- it is the same decision from the other direction, and #7 then shrinks to sharing the filter record. Realistic ~70 with test.
- [ ] #8. Role membership is writable from two endpoint pairs backed by the same repository calls (`PUT`/`DELETE /api/admin/users/{id}/roles/{roleId}` in `AdminUserController` -- `addUserToRole` / `removeUserFromRole` -- **declarations at `388` and `401` as of #257** (were 382/395; #257 added the passkey endpoints and a constructor parameter above them, +6. Before that the old 383/396 pointed at each method's first body line, not its declaration). Find by name -- vs `PUT`/`DELETE /api/admin/roles/{roleId}/members/{userId}` in `AdminRoleController:149-166` -- `addMember` / `removeMember`). Keep the roles-side pair. **Blocker resolved 2026-08-24: the frontend uses BOTH**, driving two different screens (`RoleDetailView.tsx` calls the roles-side route, `UserRolesSection.tsx` the users-side). So this is a coordinated cross-repo change with deploy ordering, not a backend delete -- cheapest path is making the users-side method delegate to the roles-side one, leaving components untouched. **PR #191 lowered its priority**: both pairs now route through the guarded `RoleRepository.addMember`, so this is tidiness, not security. Scope must also include that method's docblock, which says "the two admin endpoints that reach here".

## MR 18 — Services

- [ ] #9. The from-disk and ingest background loops are ~70 lines of copy-paste (`processFilesFromDiskLoop`, **declaration at `331` as of #255** (range was 316-420), vs `ingestFilesGroupedByDayLoop`, **declaration at `459`** (range was 444-555) -- both +15 from #255; find them by name -- the largest drift on the board, ~38 lines each), including a CREATE/UPDATE switch the ingest loop already merged. One shared loop with a `(fileEntry, prepared) -> collectionId` resolver. **Three copies, not two** -- the CREATE/UPDATE arms inside `processFilesFromDiskLoop` are a third. Net deletion ~110, better than the stated ~85, and all source: **zero forced test churn**.
- [ ] #10. `updateGif` reimplements the tag/people/location merge blocks that `ContentMutationUtil` already owns as `updateImage*Optimized` (`ContentService.updateGif`, **declaration at `550` as of #256** (was 546-635; +4 from #256's docblock, net of its comment deletions), vs the three `updateImage*Optimized` helpers in `ContentMutationUtil`, **`183-243`**: Tags 183, People 205, Locations 227). **"The helpers only use the content id" is FALSE** -- all three call `setTags`/`setPeople`/`setLocations`, which are declared on subclasses, not `ContentEntity`. The fix needs a return-the-set signature, not a retype, and it converts `ContentServiceTest.updateGif_persistsPeopleAndLocations` into a weaker test. Realistic ~180, not ~40.
- [ ] #11. Four near-identical BFS walks: `RoleGrantPropagationService.java:168-223` (three) plus `CollectionService` `validateNoLinkCycle`/`parentIdsOf` (`465-495` as of #216; find them by name). One `walk(root, neighborsFn)` helper. **Five walks, not four** -- `propagateToVisibleSubtree` is a fifth the line range missed. ~95 lines, zero test churn, pinned by 33 integration tests. **Best value in MR 18.**
- [ ] #12. `nextOrderIndex` logic. **Five places, not four** -- `TagService` is the fifth. Do it by keeping `ContentService.nextOrderIndex` as a one-line delegate, which makes test churn zero; the naive version costs 15 stub edits in `ImageUploadPipelineServiceTest` for 5 lines of dedupe. **Do it the delegate way or not at all.**
- [ ] #13. Entity-to-Record mapping and case-insensitive sort duplicated across four files (`Records.Tag` mapping at `ContentModelConverter.convertTagsToModels` (**`323` as of 2026-08-29**), `MetadataService.toTagModel` (**`430`** as of 2026-08-29 -- the method is `toTagModel`, not `toTagRecord`; the Location mapping is `toLocationModel` at **`438`**), `SyntheticCollectionResolver:150`, `ContentService`'s newly-created-tags map (**`986` as of #256**, was 994); Location mapping/sort twice). Static `from(entity)` factories on the records. **Counts are 10 tag + 4 location sites, not 6+2, and the estimate is the worst on the board: net ~0 lines**, because every copy and every replacement is one line. The suggested fix also flips the layering -- `Records.java` currently imports nothing from `entity`. **The finding worth keeping is not the dedupe**: `ContentModelConverter` and `CollectionProcessingUtil` sort their output and `MetadataService`/`SyntheticCollectionResolver`/`ContentService` do not, which is a live API-ordering inconsistency. Split that out and drop the rest.

## MR 19 — Query efficiency and data layer

- [x] #14. `convertEntityToModel` loaded the same content row twice. **DONE**
  ([#218](https://github.com/themancalledzac/edens.zac.backend/pull/218), 2026-08-25) — two
  queries to one, and **the first item in seven to need no adjustment at implementation time**,
  which is what taught working rule 27. The method had no test at all; the two added tests are the
  only mutation detectors. Write-up (deletion cost table for the two dead finders included) moved
  2026-08-29 to the [history file](2026-08-22-backend-cleanup-history.md#mr-19-14-outcome-2026-08-25).
- [ ] #15. `getUpdateCollectionData` fetches the collection row twice and has an always-true null check (`CollectionService.getUpdateCollectionData`, **declaration at `848` as of #258**, was 845-914 as of #216 and 846-915 before that -- +3 from #258's orphan block; find it by name).
- [x] #16. `findCurrentContentCollections` N+1. **DONE** ([#216](https://github.com/themancalledzac/edens.zac.backend/pull/216)) —
  201 queries to 1. The diagnosis was exact; **the suggested fix was not, and would have shipped a
  silent bug** (its `IN (:ids) OR referenced_collection_id IN (:ids)` clause drops the parent
  scope). [Full write-up](2026-08-22-backend-cleanup-history.md#mr-19-16-outcome-2026-08-25----the-suggested-clause-was-the-bug).
- [ ] #17. Smaller items: `UserInviteService.validate`/`redeem` duplicate token resolution (**`validate` 158-175 and `redeem` 257-274 as of 2026-08-27**; was 140-152 / 220-237, and before that 85-130 -- the file has gone 130 -> 238 -> 275 lines under S-7/S-9/S-15, **so stop quoting ranges for this one and find the two methods by name** -- into `findLiveInvite`); pagination normalization re-inlined in `CollectionService.getCollectionWithPagination` (**`143-145`, unchanged and re-verified by anchor text 2026-08-31 after #258 edited this file; was `142-144` then `127-130`, and it is three lines not four**; call `PaginationUtil`); `toEntity`'s `defaultPageSize` parameter and `applyPaginationDefaults` are redundant with each other (`CollectionProcessingUtil.toEntity` **`566-589`** and `applyPaginationDefaults` **`924-932`** as of 2026-08-25, were `569-596, 939-947` -- **neither file was touched by #213/#214/#216, so this drift predates them**); `uploadToS3`/`streamFileToS3` duplicate key and URL construction (`ImageProcessingService` -- declarations at **`720`** and **`747`** as of 2026-08-31, were 716/743, before that 697-745 -- +4 from #249); EmailService HTML skeleton **three times, not twice** -- `buildHtml`, `buildInviteHtml` and `buildShareLinkHtml`, the third added by [#213](https://github.com/themancalledzac/edens.zac.backend/pull/213) under an explicit guardrail not to fold it in there (optional, **~50-70 lines now, not ~35**). #213's own write-up sent this consolidation to MR 24; that was wrong, it lives here and has always lived here.

  **Two sub-items struck 2026-08-24, both premises dead:**
  - *`ensureDimensions` twins* -- already refactored. The shared work is hoisted into
    `putDimensionsFromHeader`; what remains is two 6-line wrappers differing only by log message.
  - *EXIF-versus-ISO format detection duplicated between the two date parsers* -- **the premise is
    false**. `parseImageDate` does no format detection at all: it splits on `[: T-]` and takes
    numeric runs. There is no second copy and nothing to fold. What IS real, and was noted in the
    history file but never given a row: `parseImageDate` returns **month 13** for a nonsense date
    and builds an S3 path from it. That is a robustness bug, not a consolidation -- see the row in
    "Decisions needed".
- [ ] #18. `EquipmentRepository` repeats each SELECT column list **3-4 times per list across 3 lists** (not "6+ times" -- cameras 4x, lenses 4x, film types 3x, so ~15-20 lines not ~25) while sibling repositories hoist constants (`AppUserRepository`, `ShareLinkRepository`, `WebAuthnCredentialRepository`, `CollectionRepository` all do it right). Hoist per-entity constants. ~25 lines.
- [ ] #19. `model/ImageSearchResponse.java` is a strict subset of `model/PagedResponse.java`. Replace it with `PagedResponse<ContentModels.Image>`. **Unblocked 2026-08-24**: the frontend reads only `result.content`, never `totalElements`/`totalPages`, and ignores unknown keys, so growing the contract is safe. `AdminController` already re-wraps into `PagedResponse`, so 4 more lines vanish as a bonus. **Do this before MR 17 #7.**
- [ ] #20. `Records.FilmFormat` (DTO) shadows the `FilmFormat` enum, forcing a fully-qualified name at `Records.java:23` and duplicating the mapping at `ContentControllerProd:147-149` and `CollectionService` `930-932` (as of #216, was `931-933`). Rename the record `FilmFormatOption`, import the enum, one static factory.

---

# Wave 6 — Conventions

*(MR 20, the bare-array decision, closed 2026-08-30 by user decision -- bare arrays are blessed and
no endpoint changed. Inventory and reasoning:
[history](2026-08-22-backend-cleanup-history.md#mr-20--the-bare-array-decision-closed-2026-08-30-moved-from-the-tracker).)*

## MR 21 — Untyped Map bodies and responses

- [ ] Admin write surface. **Re-derived 2026-08-24: 19 controller sites, not 15** -- and re-checked 2026-08-25: 19 is right *as endpoints*, but it is **20 distinct lines**, because `deleteImages` has both a Map body and a Map response. Say which unit the number is in before estimating against it -- the original
  list missed five `AdminController` Map responses that existed at the `8c28cf3` baseline
  (`deleteCollection`, `createCamera`, `deleteTag`, `deletePerson`, `deleteLocation`). Current shape:
  `AdminController` has 4 Map bodies (`deleteImages` with no `@Valid`, plus the three rename
  endpoints where a body without `"name"` passes null into the service) and 12 Map responses, one
  needing `@SuppressWarnings` to cast its own service's map back; plus `WebAuthnController`,
  `EditController` (the untyped part is the **response**, not the body -- the body is already
  typed), `ContentControllerProd` and `CollectionControllerProd`. **Eight service methods return raw
  Maps too** and are not in the doc at all (`ContentService.updateImages`/`.deleteImages`,
  `CollectionService.applyCollaboratorImageEdits`, and five `MetadataService.create*`).

  Introduce small records (`RenameRequest(@NotBlank String name)`,
  `DeleteImagesRequest(@NotEmpty List<Long>)`, and so on). **Mostly NOT a wire change**, which the
  item never says: `RenameRequest` deserializes the same `{"name":"x"}`, and a record with the same
  field names serializes identically to `Map.of("success", true)`. Only the added validation changes
  behavior, which is the point. ~27 source sites against 59 Map-shaped lines in 17 test files; ten of
  the affected endpoints have zero test references at all.

## MR 22 — Remaining convention sweeps

- [ ] `ResponseEntity<?>` twice: `UserSelectsControllerProd.list` (**`:55`, was `:59`** -- re-verified 2026-08-24; serves two different shapes from one GET — split or wrap) and `MessagesControllerPublic:43` (throw a `RateLimitedException` handled globally, which also unifies the three different 429 body shapes currently in play: empty at `AuthController` (**`74` as of 2026-08-29**), Map at `CollectionControllerProd` (**`183-184`**), ErrorResponse at `MessagesControllerPublic:48-52`, correct).
- [ ] Try-catch in controllers, **two sites** (not three -- the third went with bug #15 in MR 7, [#168](https://github.com/themancalledzac/edens.zac.backend/pull/168), confirmed gone by grep): `AdminUserController.mergePreview` and `.merge` (map via `ResourceNotFoundException` plus a new `ConflictException` handler). **Both methods have zero tests**, so this is an untested behavior change on two admin endpoints -- a risk, not a saving.
- [ ] `@Value` field injection: **9 sites, not 3.** The three named (`CollectionControllerProd`, `ShareControllerProd`, `DownloadUrlService`) plus six in `S3Config` and `SesConfig` that feed `@Bean` methods -- same rule, same fix, and they fold into MR 16 #4. Move to constructor parameters, following the `WebAuthnController` pattern. Test coupling is exactly **five** `ReflectionTestUtils.setField` calls (re-measured 2026-08-29; `CollectionControllerProdTest` has two). Also `@Autowired` on constructors at **five** classes now: `AuthLoginLimiter`, `ClientGalleryAccessLimiter`, `ShareEmailLimiter` (added by #233, same two-ctor shape), `WebAuthnChallengeStore`, `WebAuthnService`. **The real size is 1 deletion and 4 javadoc notes**: only `AuthLoginLimiter` has a single constructor; the other four genuinely have two, where the second is the package-private test constructor, so `@Autowired` is load-bearing. Fifteen minutes.
- [ ] Fully qualified names inline: **14 sites, not 6.** The six named (`CollectionService.isGalleryAccessAuthorized`'s parameter -- the doc's `542`, then `533`, then `534`, then `541`, **is `539` as of #218 -- the fifth correction to one ref, so stop writing the number** after S-6's javadoc, which is the **fourth** correction to one ref and the reason this item names symbols and not lines. Read the number as advisory and the symbol as the target; `CollectionProcessingUtil`, `TagViewResolver`, `GalleryAccessCookies`, `ContactMessageLimiter`, `Records.java`) plus eight in the data layer the original scan missed: `BaseDao` (3), `CollectionRepository`, `EquipmentRepository` (3), `PersonRepository`. Import-only, **zero test coupling**. `Records.java` still needs consolidation #20 first (the `FilmFormat` name clash).
- [ ] `Optional.get()` -- **numbers corrected 2026-08-28; see the correction immediately below
  before trusting anything in this bullet.**

  **Re-derived 2026-08-28 during the S-20 close-out, and two of the three recorded numbers are
  wrong.** This was found *outside* the neighborhood of what merged, which is itself the signal.
  (1) The raw sweep is **58 on `main`**, not 57. It has been 58 at every commit back through #221 --
  checked at `cc31113`, `98e8a40`, `6154a86`, `ad9cac3`, `12807b8`, `c899d6e`, `dd0d7d0`, `a105b6b`
  -- so "58 on the branch, 57 on `main`" stopped describing `main` at least seven merges ago and
  survived a full-board review plus three close-outs. (2) **The Atomic exclusion count is wrong by
  more than half: five, not eleven.** Only two files in `src/main` import
  `java.util.concurrent.atomic` at all -- `JobTrackingService` (four `.get()` on
  `job.processed/created/updated/skipped`) and `AdminHomeService` (one, `cache.get()`). (3) So the
  Optional subset is **~53, not 46** -- the problem is materially *larger* than recorded, and the
  item has been re-derived four times without anyone re-checking the subtrahend.

  Do not treat 53 as verified either. It is `58 - 5` and the remainder still needs per-line
  classification: `.get()` with empty parens also covers `Supplier`, `ThreadLocal` and `Future`, and
  nobody has walked the 53. **The next session to touch this bullet should classify the lines rather
  than adjust the arithmetic**, and record the command with its escaping per working rule 31 --
  filtering by the literal word "atomic" on the matched line returns zero, because the variables are
  named `job` and `cache`, which is how the 11 survived this long.

  *Historical claim, kept for the pattern:* **47 sites as of #218, 46 on `main`; not the 17
  originally named.** *(Re-derived a fourth time 2026-08-25 by the unscoped sweep, and this time a component moved without the total holding: `UserShareControllerProd` is **3, not 2** -- #213 added `buildShareUrl(token.get())`. #218 adds one in `ContentModelConverter`, exactly attributable. Raw sweep 58 on the branch, 57 on `main`; the 11 Atomic exclusions still check out.)* **The claim "the 17 named are all still present" cannot be checked and arithmetic says it is wrong**: the originally-named files now hold 14 between them, and the 17 were never enumerated, so the sentence is unverifiable by construction. Drop it rather than carry it. *(Earlier re-derivations, kept for the pattern they show -- 45 -> 46 on 2026-08-24: S-1 added `maybeUser.get().getStatus()` to `AuthController.login`, taking that file 3 -> 4. Re-derived after the merge, not estimated -- the raw sweep went 56 -> 57 and the one new line is S-1's. This is the inventory rot working rule 5 warns about, caught by the scoped sweep rather than a full pass.)* The 17 named are all still present; 29 more sit in twelve files the original scan never covered (`AdminUserController` 4, `AuthController` 4, `InviteController` 3, `ImageProcessingService` 5, `UserMergeService` 3, `UserShareControllerProd` 2, `ClientGalleryAuthService` 2, `SessionService` 2, and one each in `LocationRepository`, `TagRepository`, `AdminBootstrap`, `ImageUploadPipelineService`). A raw `.get()` sweep returns 56 lines; 11 are `AtomicInteger`/`AtomicReference`, not `Optional`. **Re-derived again 2026-08-24 after S-7/S-9, and the headline number survived for the wrong reason.** The raw sweep is still 57 and the Optional subset still 46 -- but two files moved and cancelled out: `InviteController` went **3 -> 2** (S-7 moved the accept body into the service) and `UserInviteService` went **2 -> 3** (`accept` added its own `maybeInvite.get()`). A total that holds while its components move is the most misleading state an inventory can be in, so trust the per-file breakdown here over the headline. *Re-derived a third time 2026-08-24 after S-8: raw sweep **still 57**, Optional subset **still 46**, and this time for the right reason -- S-8 added no `.get()` at all (`AdminUserController` holds at 4, `SessionService` at 2). Two consecutive checks now agree on both the total and the breakdown.* Zero test coupling. **This is not an MR** -- the doc's own "rewrite opportunistically when touching these methods" is the right disposition, now with the real denominator.
- [ ] Magic number 2500 at both resize call sites (`ImageProcessingService`, **`191` and `282`, re-verified unchanged 2026-08-31** -- #249 edited below both). Name it.
- [ ] `JobStatus.status` is a stringly-typed field with its states in a trailing comment (`JobTrackingService`). **Split the item**: making it an enum is COLD and non-breaking (Jackson serializes an enum to the same string), but costs ~45 test references across `AdminControllerTest` and `ImageUploadPipelineServiceTest`. Adding `COMPLETED_WITH_ERRORS` instead of flipping a 500-file job to FAILED over one error is
  **UNBLOCKED as of 2026-08-24** -- the check was run and there is no frontend job-status poller at
  all. `jobId`, `JobStatus`, `job.status`, `jobStatus`, `/jobs/` and `from-disk` return zero hits
  across the whole frontend `app/` tree, and no code compares against `'COMPLETED'`/`'FAILED'`/
  `'PROCESSING'`/`'PENDING'`. The backend returns a `jobId` for polling that nobody polls. So the new
  enum value breaks no consumer -- **and the more interesting finding is that the whole job-status
  endpoint may be dead**, which belongs in Appendix C rather than being fixed here.
- [ ] Verb-style routes `POST /collections/createCollection` and `POST /content/content` (plus a third the item missed, `GET /api/admin/collections/{slug}/update`). **Both confirmed live in the frontend** (`app/lib/api/collections.ts`, `app/lib/api/content.ts`, one caller each), with 61 backend test references. The alias half is COLD; the retire half needs a frontend release.
- [ ] Route the gallery-access save failure through an exception instead of a `saved()` boolean with a hand-built 400 (`CollectionAdminController`). **This is an undeclared wire change**: today a failure returns 400 with a `GalleryAccessResponse` body; through an exception it returns 400 with `GlobalExceptionHandler.ErrorResponse`. 30 test references across 4 files. **Checked 2026-08-24 and the answer is yes, so this stays
  BLOCKED and is now precisely specified.** `saveGalleryAccess` in the frontend's
  `app/lib/api/collections.ts` reads `result.saved` and `result.reason` straight off the 400 body and
  rethrows as `ApiError(result.reason ?? <fallback>)`. Routing through `GlobalExceptionHandler` would
  return `ErrorResponse` instead, so `saved` and `reason` both come back undefined and the admin UI
  silently degrades to the generic fallback message. **The blocker is a frontend change, and it is
  small**: have the frontend read the `ErrorResponse` shape first, then land the backend change.

---

# Wave 7 — Structure

## MR 23 — Package moves (rename-only)

- [ ] `controller/user/` is a one-class package (`UserRatingOverrideControllerProd`) that belongs with its five siblings in `controller/prod/`.
- [ ] Request records have two homes: `RoleRequests`/`UserRequests` (`controller/admin/`) and
  `InviteRequests` (`controller/auth/`) versus `MessageRequests`/`CollectionRequests`/`ContentRequests`/`CollaboratorRequests`
  (`model/`). Move the three strays into `model/`. **The strongest argument for this is not in the
  item**: `UserMergeService` imports `UserRequests` from a controller package, so a service currently
  reaches up into the controller layer. Also unlisted and the same smell: `GrantableLevel` and
  `GrantableLevelValidator` are non-controller types sitting in `controller/admin/`. Eleven files,
  zero net lines.

  **Re-counted 2026-08-27: thirteen files, and the validator smell doubled.** S-13
  ([#227](https://github.com/themancalledzac/edens.zac.backend/pull/227)) added `AccountStatus` and
  `AccountStatusValidator` to `controller/admin/` -- deliberately, because mirroring the
  `GrantableLevel` pair was the right call for that fix and this item is not blocking. But it means
  the package now holds **two** constraint/validator pairs, so what was an aside is a small pattern:
  `controller/admin/` is where bean-validation types live in this codebase, by precedent rather than
  by decision. **Decide that explicitly when this MR runs** -- either a `validation/` package for all
  four, or say in the doc that constraints live beside the requests they constrain. Leaving it
  undecided is how a third pair gets added the same way.
- [ ] Optional: drop the `*Prod` suffix now that no controller carries `@Profile` (verified: the only two `@Profile` hits under `controller/` are javadoc text saying there is no gating). **10 main classes plus 10 test classes, 23 files touched -- much the largest item in MR 23, and it should not share an MR with the two cheap moves above.**

## MR 24 — Service extraction and remaining design items

- [ ] `AdminUserController` is a service wearing a controller's clothes: two repositories and **seven** services injected (was six; S-8 added `SessionService`) plus a `frontendBaseUrl`, **523** lines (469 -> 474 -> 481 -> 520 -> 523, the last +3 from #250's S-24 docblock), entity building, multi-step `@Transactional` orchestration, afterCommit hooks. Extract an `AdminUserService`. **Largest real cost in Wave 7**: ~200 source lines move, but `AdminUserControllerTest` is **1,308** lines (1,015 -> 1,097 -> 1,183 -> 1,308). **The 1,294 recorded here was already wrong when it was written**: the file last changed in #241 on 2026-08-30, before that day's close-out, and the close-out did not re-measure it. Nothing this session touched the file -- this is a number that rotted on its own, outside the neighbourhood of anything that merged, which is the case a scoped drift sweep cannot catch and is the hidden half.

  *Positional refs replaced with names 2026-08-24, per working rule 5 -- this item's range list had drifted twice in two days.* **They were re-added as fresh line numbers anyway, and drifted a third time on 2026-08-27** when #227/#228 landed; that is working rule 26 happening inside the very item that recorded the lesson. **The numbers are now gone for good. Find these by name.** The `@Transactional` orchestration blocks are `createUser`, `regenerateInvite`, `upgradeUser`, `updateUser` and `merge`; the afterCommit hook itself is `sendInviteEmailAfterCommit`, called from the first three.

  **The item is growing faster than it is being done, and the rate is increasing** -- 469 -> 520 main and 1,015 -> 1,294 test across **four** security MRs, all of which edited the exact class this proposes to split. The test file has grown **279 lines, 27%, in four days**. Every one of those MRs was small and correct; the point is that the extraction's cost is set by how often this class is touched, and it is touched constantly. **This is now the strongest do-it-sooner argument on the board.**
- [ ] Same shape, smaller: `UserShareControllerProd` computes grant and candidate sets inline with a repository. Move it into `ShareLinkService`. **Re-derived 2026-08-24 and de-positionalized**: the old range `124-152` overran the end of a 145-line file. The work is two private methods -- `buildSettings` and `candidateCollections`, the latter
  holding the `memberCollectionIdsForUser` call. **Find them by name.** The 2026-08-24 pass
  "de-positionalized" this by writing fresher numbers (`:116-128`, `:135-144`, `:137`), and
  [#213](https://github.com/themancalledzac/edens.zac.backend/pull/213) invalidated all three the
  same day (working rule 26). **The stamps are gone for good (2026-08-29)** -- the file has moved
  again since (227 lines today). Find the two methods by name.
- [ ] `Synthetic.blogsOnly` is a constant at its only reachable call site (`SyntheticCollectionResolver:42-49, 97`, both refs correct). **Premise flagged as FALSE, 2026-08-25**: the catalog has three entries -- false, true, false -- and `:97` is reached by both `ALL_BLOGS` (true) and `ALL_CLIENT_GALLERIES` (false), so it is not constant at that site. The fold may still be right; the stated reason is not, a transitional shape from the type-keyed catalog. Fold it out.
- [ ] `MessageService` is a pure pass-through with a speculative docblock. Keep it for layering or delete it, but drop the justification.
- [ ] The validator components (`MetadataValidator` repeats its 3-line null check **six** times, not four; `ContentValidator` is similar) are the "unnecessary utility classes" CLAUDE.md bans. Replace with bean validation on the DTOs when next touched. **~199 source lines across 3 files, not ~60**, plus `@Mock` removal in **5** test files (**re-derived 2026-08-25**, was 6: `ImageProcessingServiceTest`, `ContentServiceTest`, `ImageUploadPipelineServiceTest`, `ContentServiceDownloadTest`, `MetadataServiceTest`) and a constructor arg off 4 services, which is exact -- a 9-file change, so "when next touched" is right.
- Executor handling in `ImageUploadPipelineService` -- **promoted 2026-08-29 to bug #20 under
  "Carried forward"**: a real bug (an unwaited executor on shutdown), not a design note. This list
  had carried the promotion instruction unexecuted since 2026-08-24. The misnaming half
  (`rawUploadExecutor` runs whole disk and ingest jobs) rides with the bug fix.
- [ ] `AdminHomeService`'s AtomicReference cache has no TTL and is per-instance. Fine single-node; note it for any multi-instance future.
- [ ] Service decomposition, the standing item. **Recounted 2026-08-24 by `wc`, and the argument is
  stronger than when it was written.** The four files are `CollectionService` **1,726** (**re-measured 2026-08-25**; #216 took 20 lines out of the 1,746 recorded here),
  `ContentService` **1,014**, `ImageProcessingService` **1,394** (+4, #249), `CollectionProcessingUtil`
  **933** -- all four quoted numbers were stale. The total went 5,107 -> 5,083 across 24
  MRs of dedicated cleanup, and is **5,063 as of #218** -- a net -44 lines, under one percent, and
  `ImageProcessingService` **grew 25**. "Waves 5-7 shrink these" is not what the data shows; the waves have been shrinking
  other files. Decide the split boundaries before the next feature lands in them. **COLD -- this
  needs a decision, not research.**

---

# Wave 8 — Tests

## MR 25 — Shared fixtures and consolidation

- [ ] `new ContentModels.Image(` with 31 positional components appears in **11** test files (not 12; 13 call sites), **7** of which have their own private helper. Same for `CollectionRequests.Update` -- **corrected 2026-08-29: the canonical record has 22 components** (`parents` is the 22nd; the compat docblock's "all five set to null" checks out, 22 - 5 = 17), and the deletion target is the **17**-arg compat constructor at its **21** test call sites -- 25 `Update` constructions in all (21 compat + 4 canonical, one of the canonical in `CollaboratorRequests`). The "Positional constructors" list below already said 21; the two entries disagreed and 21 is right. One `TestFixtures` class with builders. **The doc underestimates by ~2x in the good direction**: measured, those sites are **745 lines of positional construction**, replaced by roughly 120, so **~-600 net, 18 test files, and zero main files touched.**
- [ ] `services/CollectionServiceTest.java` (**2,640 lines as of #218**, was 2,644, not the baseline 2,412 -- it grew 232 since baseline, so the estimate below is measured against the wrong denominator): assert/verify twins where the second test re-runs the first's stubbing and re-checks with `verify` — for example `createCollection_happyPath_savesAndReturnsUpdateResponse` (**`:136` as of #218**) versus `createCollection_verifiesEntityCreatedViaUtil` (**`:165`**); the `deleteCollection` plain-verify test (**`:196`**) is a strict subset of the inOrder version (**`:224`**). All four shifted by exactly +8, which is one edit near the top of the file. ~250 lines.
- [ ] The four typeless-migration integration tests (V50Backfill 188, V51Prep 282, V52Drop 72,
  TypelessRead **164** not 113 -- 706 lines total, not 655). **Two corrections.** Only V50 and V51
  boot their own `PostgreSQLContainer`; V52 and TypelessRead share the per-JVM container and cost
  almost nothing, so the real prize is ~470 lines **and two dedicated container boots**, which the
  line count hides. And "consolidate into one end-state IT" is over-broad --
  `CollectionTypelessReadIntegrationTest` **is** the end-state IT. The move is: delete V50 and V51,
  fold V52's four assertions into it, keep `CollectionTypeAbsentFromWireTest` as the wire guard.
- [ ] **`V54FoldMigrationIntegrationTest` (181 lines) is the same shape and is not on this board.**
  *(New row 2026-08-24.)* It boots its own container to exercise the V54 fold against real data,
  and its own docblock says the shared harness migrates an empty `users` table so "a fold that
  destroyed every tag would still pass it" -- which is why it exists separately. Neither this file
  nor the history mentions V53-V57 anywhere. Decide whether it joins the consolidation above or is
  deliberately exempt, and record which.
- [ ] `ImageUploadPipelineServiceTest`'s 1:1 verify ratio suggests some verify-only tests worth a pass.

### Positional constructors that block the `TestFixtures` pass

**Sizing note added 2026-08-24 from #209, and it applies to every item that adds a field to a
record.** The `isPasswordProtected` item read as "one component plus the two-line frontend change".
The record change was indeed one line. The MR was four files and five test edits, because adding a
component to a record costs: the component, **every `with*` copy method** (two here, and the one on
the hot path was the one the item never named), and **every positional construction site in test**
(four here, across two files). None of that is visible from the item's wording.

So when sizing any "add a field to X" item on this board, run
`grep -rn "new <Record>(" src/main src/test` first and count. That number, not the record edit, is
the size. It is also the argument for the `TestFixtures` pass below: the four positional sites #209
had to touch are the same shape this section exists to remove, and each future record change pays
that toll again until it lands.

*(Retitled 2026-08-24. The old title said "Main-dead, test-live (carried from MR 1a)" and neither half held: `AuthPrincipal`'s 4-arg constructor has a main caller, and MR 1a's own history records it as not-dead rather than deferred.)*

These have many test callers, so deleting them rewrites working call sites to pass explicit nulls.

**The "do them in the SAME pass as the `TestFixtures` builders" claim is true for exactly one of
them** (verified 2026-08-24), not for the set. It holds for `CollectionRequests.Update`, whose 17-arg
sites are precisely the sites a builder collapses -- doing them separately rewrites the same 21 sites
twice. It does **not** hold for `FileEntry`, `resolveCollectionDownloadEntries` or
`DownloadResolution.extension`: none has a builder proposed, and none shares a call site with either
fixture target. Bundling them makes the MR bigger for no reason.

- [ ] `model/CollectionRequests.java` -- 17-arg `Update` constructor, **21** test call sites (not 23). **This is the one that must ride with the `TestFixtures` pass.**
- [ ] `model/DiskUploadRequest.java` -- 3-arg `FileEntry` constructor, **13** test call sites (not ~20; 28 `FileEntry` constructions across all arities).
- [x] `model/AuthPrincipal.java` -- 4-arg constructor. **DECIDED 2026-08-24: leave it.** It is not main-dead (`SessionService` calls it), so it never belonged under the old heading. All **36** call sites are one-liners (re-measured 2026-08-29: 35 test plus `SessionService.java:179`); deleting a 3-line convenience constructor to append `, null` at 35 clean sites is not an improvement. Closing this rather than carrying the hedge a third time.
- [ ] `services/ContentService.java` — `resolveCollectionDownloadEntries` 2-arg overload, 5 test call sites.
- [ ] `model/DownloadResolution.java` -- the `extension` component: **5** construction sites in test (not 4), **7 in total** as re-derived 2026-08-25 -- the two in `src/main` are both in `ContentService`
  and 6 assertions in test. **"Written, never read" is misleading and the phrasing invites a
  mistake.** The record *component* is never read in main, true -- but the local `extension`
  variable in `ContentService` is load-bearing: it feeds `sanitizeFilename` and decides the download
  filename's extension. Removing the component is a 2-line change and does **not** let you delete
  the extension logic. Worse on the test side: **4 of the 6 `.extension()` assertions are the only
  coverage of the collection-ZIP original-to-web format fallback** (the two single-image ones are
  backed up by `.filename()` assertions; the ZIP ones are not). They must be rewritten as
  `.filename()` assertions, not deleted. The stale docblock claim holds -- downloads are presigned
  302 redirects, they do not "stream the response".

## MR 26 — Coverage gaps

These are worth more than the bloat they replace.

- [ ] `TokenUtil` — zero direct tests for the CSPRNG/SHA-256 code underlying every invite and share link.
- [ ] `SlugUtil` — zero tests; collisions and normalization are user-facing.
- [ ] `PaginationUtil` — zero tests; an off-by-one corrupts every paged read.
- [ ] `UserFollowsService` — mocked in its controller test, uncovered itself.
- [ ] The validators (`MetadataValidator`, `ContentImageUpdateValidator`) -- the "1-2 incidental
  references" undercounts (8-16 each), but **every one is a `@Mock` declaration or its import**, so
  the conclusion stands and is stronger: zero direct coverage on components that gate admin writes.
- [ ] **`UserRatingOverrideControllerProd` has no controller test at all** -- only a service test.
  *(New row 2026-08-24; noted in the history file after MR 15 #2 and never given one. It is also
  the endpoint whose bare-array response has no test either.)* **Re-verified 2026-08-24: still
  true, and now worth more.** There is no `src/test/.../controller/user/` directory at all. S-6
  changed this controller's call into the service to pass the whole `AuthPrincipal`, so a
  controller test would now also be the only thing pinning that an admin is not 403'd here.
- [x] **Two guard tests that cannot fail, proven by mutation. DONE 2026-08-24** -- S-3
  ([#195](https://github.com/themancalledzac/edens.zac.backend/pull/195)) and S-4
  ([#196](https://github.com/themancalledzac/edens.zac.backend/pull/196)). Bug #1's delete-person
  guard reddens one test when its SQL predicate is stripped; `ProdSecretGuard` reddens two when
  `@PostConstruct` is deleted and a third when `@Profile("prod")` is. The mutations and their
  results are recorded with the security items and in working rule 15.
- [ ] **MR 11's headline security fix is untested.** Moving eight throw sites to bare
  `RuntimeException` -- so `WebAuthnService` and `JdbcUserCredentialRepository` stop echoing
  `app_user` ids and WebAuthn handles to unauthenticated callers in a 400 body -- has zero coverage.
  There is no `JdbcUserCredentialRepositoryTest`, and `WebAuthnServiceTest` never touches those
  messages.

Verified good, for the record: `AdminUserControllerTest` is real behavior testing; the auth-table truncation fix landed in `AbstractPostgresIntegrationTest`; no tests mock the deleted `collection.type` shape.

---

# Decisions needed from the user

Returned to the tracker 2026-08-29: the #236 re-split (`32d2168`) had moved this section into the
history file, breaking the Progress links and the history file's "nothing here is open" rule.

*(Three decisions -- `enforce-authz`, `parseImageDate`, bare-array responses -- were answered and
shipped 2026-08-30 in [#243](https://github.com/themancalledzac/edens.zac.backend/pull/243).
Answers and reasoning:
[history](2026-08-22-backend-cleanup-history.md#decisions-answered-2026-08-30-moved-from-the-tracker).)*

- [x] **Passkey revocation** — **SHIPPED 2026-08-31** ([#257](https://github.com/themancalledzac/edens.zac.backend/pull/257)).
  Admin endpoint only, as decided 2026-08-30: `GET` and `DELETE
  /api/admin/users/{id}/passkeys[/{credentialId}]`, both on the existing `/api/admin/**` gate. No
  user-facing list-and-remove. **Removing an account's last credential is allowed** — refusing it
  would block the one case the endpoint exists for — and the DELETE reports `{remainingPasskeys,
  passwordLoginAvailable}` so the admin sees when an account has been left unable to log in.
  Reasoning, the no-op `JdbcUserCredentialRepository.delete` finding, and the test that proves a
  deregistered credential cannot complete `finishLogin` are in
  [history](2026-08-22-backend-cleanup-history.md#2026-08-31-second-close-out--bugs-17-19-20-and-passkey-deregistration).
- [ ] **Gallery passwords — PARKED 2026-08-24 by decision. Do not act on this yet.** The item framed
  it as a binary (accept plaintext-at-rest formally, or redesign the fingerprint feature). Neither
  is being chosen, because the framing skips the question that has to come first: **what do we
  actually want these passwords to do?** Stated direction, to be designed rather than assumed: we
  will likely move to protected (hashed) passwords for user protection.

  Nobody should open an MR against this until that design exists. Recording what a future design
  pass has to reconcile, so the constraints are not re-derived from scratch:

  - Hashing at rest breaks admin re-share, which is the reason the plaintext exists. Today the admin
    manage page can display the password to re-send it. Any hashed design needs a different answer
    for re-share (regenerate-and-resend, a one-time reveal at set time, or a separate sharable
    token) and that is a product decision, not a storage one.
  - `ClientGalleryAuthService` derives the shared-unlock fingerprint cookie from the password value,
    which is what lets a parent password unlock propagated children. Hashing changes what that
    fingerprint can be derived from.
  - The per-slug cookie validates against the gallery it was issued for, and changing a password
    revokes issued cookies. Whatever replaces plaintext has to preserve that revocation property.
  - **S-1 is a separate problem and is NOT parked.** DISABLED accounts authenticating is about
    account status enforcement in the auth path, not about how gallery passwords are stored. It
    should be fixed on its own timeline.
  - The `isPasswordProtected` wire field (under this same section) is also independent: it serializes
    a boolean about whether protection exists, and never touches the password value or its storage.
    Parking the storage question does not block it.
- [x] SpotBugs: decided — delete all four artifacts. Done in MR 2. If static analysis is wanted
  later, introduce it fresh at a current version with a filter written from scratch.
- [x] `admin_home_tile.cover_image_id` — **ANSWERED 2026-08-31 (third run): drop it.** Shipped as
  `V59__drop_admin_home_tile_cover_image_id.sql`. The premise was re-verified before the migration
  was written and holds exactly: V19 creates the column and seeds all ten tile rows with an explicit
  NULL, and `AdminHomeTileRepository:17` is the only Java that touches `admin_home_tile` at all. The
  other sixteen `cover_image_id` hits in `src/` are the unrelated column on the `collection` table
  and are untouched. No confirmation query was run against prod: the column cannot have received a
  value through the application, so the query only guards a manual DB edit, and the migration's
  header carries the one-line restore recipe if one turns up.
- [x] **Whether to ship a default DB password at all — ANSWERED 2026-08-31 (third run): drop the
  default.** `spring.datasource.password` is now `${POSTGRES_PASSWORD}` with no fallback, which
  fails the context at startup when the variable is unset. Option (b), chosen for consistency with
  `ACCESS_TOKEN_SECRET` and the AWS keys in the same file. Three things were checked before
  changing it, because "prod is unaffected" was the claim the decision rested on: `docker-compose.yml:19`
  sets `SPRING_DATASOURCE_PASSWORD` from the env, which shadows the property outright; the test
  classpath's own `src/test/resources/application.properties` shadows the main file entirely, so no
  test context ever resolves this placeholder; and CI's `POSTGRES_PASSWORD` (`.github/workflows/ci-cd.yml:55`)
  is a service-container variable that is never exported to the Maven step, so it was never load-bearing
  either. `.env.example` now marks the variable required. *(Original wording below, kept for the
  options it enumerates.)*
  *(Consolidated here 2026-08-29 — this
  decision previously had three homes: a "Carried forward" bullet, an open checkbox inside the
  history file's closed MR 9 section, and this section's promise of a row that did not exist. This
  row is now the only one.)* MR 9a fixed the separator and preserved the existing default, so
  `spring.datasource.password` falls back to `password` (`application.properties:13`). Options:
  (a) keep `${POSTGRES_PASSWORD:password}` as-is; (b) drop the default entirely with
  `${POSTGRES_PASSWORD}`, which fails the context at startup when unset, matching how
  `ACCESS_TOKEN_SECRET` and the AWS keys already behave in this file; (c) `${POSTGRES_PASSWORD:}`,
  which defers the failure to the first connection attempt. Prod is unaffected either way — compose
  shadows this property on every deploy — so the real question is what a local run should do when
  the variable is missing. *(The 2026-08-22 log line "Decided: keep the default DB password" was
  bug #9's scope call — keep the existing default while fixing the separator — not a disposition
  of this question; the history log entry is annotated to match.)*
- [x] `role.kind` — **ANSWERED 2026-08-31 (third run): keep it, documented as provenance.** Shipped as
  `V60__comment_role_kind.sql`, a `COMMENT ON COLUMN` recording that PERSONAL marks a role the V45
  backfill created and SHARED marks every role made since. The 2026-08-24 correction was re-verified
  against `V45__create_roles.sql` before writing it: line 37 inserts `'PERSONAL'` and lines 44 and 50
  join on `r.kind = 'PERSONAL'`, so both values are real in any database that ran V45 against a
  non-empty `user_collection`. No prod query was needed — the disposition is *keep*, and the grouping
  query only mattered for the drop case. The DB comment is the durable answer to "why is this column
  here", which is the question that made it look droppable twice.
  *(Original row below, kept for the corrected premise it records.)*
- [x] `role.kind`. **Premise FALSE, corrected 2026-08-24.** The item says it is "written as constant
  'SHARED' and read by nothing". `RoleRepository` does write `'SHARED'`, but `V45__create_roles.sql`
  writes `'PERSONAL'` in its backfill and joins on `r.kind = 'PERSONAL'` twice more. So the column
  carries **two** values in any database that ran V45 against a non-empty `user_collection`, and it
  is the only surviving marker of which roles the migration auto-created versus which an admin made
  deliberately. "Read by nothing" is true of the Java layer only. **Changes the disposition from
  "likely droppable" to "check prod first, it may be carrying provenance":**
  `SELECT kind, count(*) FROM role GROUP BY kind;`
- [x] Unknown-JSON-key policy (C8). **Answered 2026-08-24 -- nothing to research, the decision just
  needed writing down.** `FAIL_ON_UNKNOWN_PROPERTIES` is disabled (Boot's default; no
  `spring.jackson.deserialization.*` override anywhere), and it is already **pinned by a test** --
  `CollectionTypeAbsentFromWireTest` asserts it is false. So the de-facto policy is "ignore unknown
  keys", it is enforced, and flipping it would break that test and be a breaking wire change. That
  is the argument for leaving it off. Recorded.
- [ ] Partial indexes on `is_blog`/`is_client` (C7, "if scale demands"). **The item names the wrong
  measurement.** "Check request metrics" points at the `request_metric` table, which is readable
  (via `GET /api/admin/metrics/requests` or one SELECT) but counts HTTP requests per route per day
  and says nothing about whether an index helps -- and V44's own header admits those counts
  undercount because of ISR and CloudFront caching. What decides this is table size and selectivity:
  `SELECT count(*), count(*) FILTER (WHERE is_blog), count(*) FILTER (WHERE is_client) FROM
  collection;` plus `EXPLAIN ANALYZE` on the six `CollectionRepository` queries that filter on those
  flags. Confirmed: **no index on either column exists** -- V50 adds them as plain `BOOLEAN NOT NULL
  DEFAULT FALSE` with only a CHECK constraint.

# Stale side branches

Returned to the tracker 2026-08-29 alongside "Decisions needed" -- it carries an open worklist.

Found 2026-08-24 by `git worktree list` while resyncing the palace. **The board has never mentioned
these and none has an open PR.** Six worktrees, all created before or during this cleanup effort and
left behind while 25 MRs landed on `main` underneath them.

- [ ] **Delete the three that hold nothing.** **Re-run 2026-08-30:** `feat/collection-debloat` is
  **0 ahead, 175 behind** (`git rev-list --left-right --count origin/main...origin/<branch>`) -- the
  "0 ahead" that makes it deletable still holds; the behind-count moves on its own as `main`
  advances and is not worth re-recording. Original text: `feat/collection-debloat` (0 ahead, 117 behind),
  `claude/auth-password-reset` (0 ahead, 38 behind) and `claude/one-way-collection-associations`
  (0 ahead, 38 behind) have **zero unique commits**. They are worktrees holding no work. Per the
  user's standing worktree rule these are theirs to remove, so this is a recommendation, not a
  cleanup to perform unasked. Note `claude/auth-password-reset` is **not** a reason to unpark the
  password decision -- it contains no commits.
- [x] **`0257-backend-security-bugs` is fully superseded -- verified, safe to delete.** Its single
  commit (2026-08-22, "close the admin delete-person and rate-limit holes (cleanup tracker MR 5)")
  is a parallel implementation of MR 5, which shipped as
  [#165](https://github.com/themancalledzac/edens.zac.backend/pull/165). All three of its
  `deletePerson_*` tests already exist on `main` under identical names.

  **And it is where the S-4 gap came from.** Its `ProdSecretGuardTest` additions are the reflective
  `invokeVerify(guard)` tests that mutation later proved cannot fail, including the duplicated
  `enforceAuthzDisabledThrowsEvenWithAGoodSecret`. So this branch is not a fix for S-3/S-4 waiting to
  be salvaged -- it is their origin. **Do not "rescue" it into an MR**; that was the tempting wrong
  move and it would re-land the untestable tests.

  One nuance worth carrying into the S-3 fix: its `deletePerson_refusesAnAccountId` test stubs
  `deletePersonById` to return 0 and asserts a 404. That covers the service's zero-rows handling,
  which is real, and leaves the SQL predicate untested, which is S-3. Both statements are true at
  once, and that is exactly why the gap survived review.
- [ ] **Two July "wip" snapshots, 147-184 commits behind.** `0217-user-upgrade-be` (1 commit,
  2026-07-28, `AdminUserController` + `UserRequests` + a 136-line `UserUpgradeIntegrationTest`) and
  `chore/log-review-followups` (1 commit, 2026-07-28, `AdminController`/`TagRepository`/
  `CollectionService`/`ContentService`). Both predate Waves 1-5, which rewrote every file they touch.
  Decide per branch: salvage the test, or delete. `0217`'s integration test is the only part likely
  to still be worth anything.
- [x] **SETTLED 2026-08-30 -- safe to delete; it now holds nothing unique.** R-1 landed its two
  stranded commits on `main` via [#238](https://github.com/themancalledzac/edens.zac.backend/pull/238),
  and `44a9d81`'s content was already there via #232's squash. Verified by content, not by commit
  count: `git diff origin/main origin/fix/s18-actuator-exclude -- <the two files it touched>` shows
  the actuator block byte-identical, and `ActuatorExposureTest.java` does not appear in the diff at
  all. **Trap worth keeping:** `git log origin/main..origin/fix/s18-actuator-exclude --oneline`
  still returns **3**, and always will -- a squash merge never makes a branch commit an ancestor of
  `main`, so that command can never reach 0 and is the wrong test for "is this branch safe to
  delete". Diff the content of the files it touched instead. Original text:
- [ ] *(superseded)* **`fix/s18-actuator-exclude` (added 2026-08-28; corrected 2026-08-29).**
  `git log origin/main..origin/fix/s18-actuator-exclude --oneline` returns **three** commits, not
  the two first recorded: `44a9d81` (the S-18 fix itself — its *content* is on `main` via squash
  `d6ff6a8`, so the SHA is unreachable by construction), plus `d42d24d`/`665bd7d`, the rule-37
  sweep pushed after #232 merged — those two are content-stranded. `git apply --check` passes
  clean for both diffs today. See R-1; do not merge the branch.

# Appendix C — Unverified leads

Worth a targeted check; not asserted as findings.

- [x] Possibly-dead endpoints -- **all three confirmed ALIVE 2026-08-24 by grepping the frontend.
  Do not delete any of them.** `GET /api/admin/collections/metadata` has 5 call sites
  (`app/lib/api/collections.ts` into the explore page, collections panel, collection-edit hook and
  two admin pages). Both role-membership pairs are live, driving two different screens (see MR 17
  #8). Ids-only `GET /api/read/user/saves` is called from `app/lib/api/personal.ts` into
  `CollectionPageWrapper`, and the frontend types it as a bare `number[]`.
- [x] `role.kind` -- **premise disproved 2026-08-24**, V45 also writes `'PERSONAL'`. Full correction under "Decisions needed"; it may be carrying provenance.
- [x] `PersonRepository.findAccountUserIdsByIds` -- **resolved in MR 15 #6.** It had zero callers in main and test, so the only-accounts-get-grants rule was documented and unenforced. The method was deleted and the rule enforced at `RoleRepository.addMember` instead. Low severity: admin-only endpoints, and a PERSON row cannot log in; the risk was a dormant grant surviving an upgrade to an account.
- [x] `collection.rows_wide` -- **premise FALSE, confirmed 2026-08-24. The frontend DOES read it**: `CollectionPageWrapper` uses `collection.rowsWide ?? LAYOUT.defaultChunkSize` as the row-packer chunk size, so dropping the column changes public rendering. Struck as a lead.
- [ ] `deleteImages`/`deleteGif` delete from S3 before the DB write inside the transaction (`ContentService.deleteImageFromS3` before `deleteImageById`, **`354-356` as of #218**; `deleteGifFromS3` before `deleteGifById`, **`518-519`**). A DB failure orphans the row's URLs; consider afterCommit S3 deletes.
- [ ] **The image-upload job-status endpoint may be entirely dead.** *(New lead 2026-08-24, found
  while answering the `JobStatus` question.)* `POST /content/images/{id}/from-disk` returns 202 with
  a `jobId` "for polling", and `GET /api/admin/content/images/jobs/{jobId}` serves the status -- but
  the frontend never calls either. Zero hits for `jobId`, `jobs/` or `from-disk` across its `app/`
  tree. If the disk-import flow is admin-CLI-only, the whole `JobTrackingService` surface plus its
  ~45 test references may be dead weight. Confirm how disk import is actually triggered before
  acting; this is the kind of "nobody calls it" claim that is wrong when a human uses curl.
- [ ] `updateImages` builds `imageMap` with `Collectors.toMap`, which throws on a duplicate key, so two updates for the same image id in one request fail before any work happens. **Note this is a verified finding sitting in an "unverified leads" appendix** -- it was traced when proving the MR 1b guards unreachable. It belongs with bug #17 (same method) whenever that MR happens; left here with a pointer rather than moved, so the trail survives. Correction to the original wording: `GlobalExceptionHandler` maps `IllegalStateException` to **400**, not 500 (working rule 3).
- [ ] `updateImages` reports per-item errors inside one transaction. Confirm a mid-item `DataAccessException` cannot leave an item half-applied (needs a test).
- [ ] `contentDisposition` (`DownloadUrlService.contentDisposition`, **`127-128` as of #218**) does not escape quotes in filenames. Depends on what `sanitizeFilename` strips.
- [ ] `TagService.convertTagToCollection` briefly persists under a temp slug, visible to a concurrent reader, and may burn a `-1` suffix.
- [ ] `WebAuthnController.registerStart`/`loginStart` declare `throws Exception`; serialization failures become generic 500s.
- [ ] ID-list DAO fetches have no ORDER BY. Spot-checked callers re-order, but not all 7+ call sites were traced.
- [ ] `CollectionServiceTest` was profiled in parts, not read line-by-line. **The original line ranges (937-1385, 1555-2017) are dead** -- pure positions in a file that has grown to 2,644 lines, with no symbol to recover them by. Re-derive from the current tree or drop the lead (working rule 5).

# Appendix D — Not yet started

- [ ] `ml_image_tagging` (design doc, 0% implemented) is still the largest unstarted feature. No stubs in `src/` to maintain, so it costs nothing until you start.

---

## Next run (set 2026-08-31, second close-out)

**The three one-word decisions go in the opening message, batched.** They are `cover_image_id`
(drop in a migration, or document as reserved), the DB-password default (keep `:password`, drop the
default, or `${POSTGRES_PASSWORD:}`), and `role.kind` (its premise was corrected 2026-08-24 and it
needs a disposition, not more research). Their answers are item 2. Asked at the end instead, they
are the session after next's problem — that is the mistake the first 2026-08-31 run avoided by
asking bug #19's direction up front, and it is what turned that into a fourth MR.

1. **The full-board review.** It is on its second restatement and the section above explains why it
   is no longer a recommendation. Read-only fan-out, then one apply agent per repo working from
   written report files — not from the parent's retelling. *Guardrail:* **it produces exactly one
   docs MR; do not let a review agent's finding become a code change in the same session.** Give
   every agent the "do not re-investigate" list in history's second-close-out write-up, or they
   will re-derive the `JdbcUserCredentialRepository.delete()` no-op and the rule-12 file counts.
   **This may consume the session, and that is an acceptable outcome.**
2. **The three answered decisions**, one MR. *Guardrail:* `cover_image_id` is a migration; the DB
   password is one line in `application.properties`. **Do not bundle a fourth decision in** — #243
   was scoped as one decision and touched 11 files, and that is the recorded warning on any item
   that removes a config toggle rather than a code path.
3. **MR 16 #4 — one AWS config class.** ~40 lines, and the board's premise that it has **zero test
   coupling** was verified 2026-08-24. *Guardrail:* re-verify that zero-coupling claim before
   starting — it is the entire reason this item is cheap, and nothing has re-checked it in a week.
4. **MR 16 #5 — CloudFront invalidation delegate.** ~25 lines. *Guardrail:* route through
   `invalidatePaths(List<String>)` as written. **Routing through `markChanged()` swaps specific
   keys for two wildcards and defers to after-commit, which is a behavior change, not a
   consolidation.**

Items 3 and 4 both sit in `config/` and `ImageProcessingService`; neither is in the neighbourhood of
anything #255-#258 touched, so their refs are as good as their last verification and no
re-derivation is owed between them. Item 1 may re-price both, which is why they are last.

### Classification of the open board (stamped 2026-08-31, second close-out)

**COLD** — pick up with no unanswered question: bug #18; MR 16 #4, #5; MR 17 #8 (cross-repo
coordinated, but the coordination question was answered 2026-08-24); MR 18 #9, #10, #11, #12;
MR 19 #15, #17, #18, #19; MR 25's four members **except** that two of their counts are now marked
UNCHECKED and must be re-derived by arity first.

**BLOCKED (user)** — **the three one-word calls are ANSWERED and shipped** (2026-08-31 third run;
`cover_image_id` drop, DB-password default dropped, `role.kind` kept and documented). What is left
here is the `coverImage` stripping row, which needs a judgement rather than a word: is the test a
stale record of a reverted fix, or a specification with no implementation? Its section has what
implementing it would break; do not resolve it by reading the comment.

**BLOCKED (other repo)** — the cross-repo GIF row. It waits on `edens.zac` having a clean tree so
it can be filed there, not on any backend work.

**PARKED by decision** — gallery passwords. Nobody opens an MR against this until a design exists.

**BLOCKED (ordering)** — MR 17 #7 waits on MR 19 #19, which is the same decision from the other
direction.

## Full-board review — DUE, and this is its second restatement

**Recommended 2026-08-31 (first close-out) and not run. Restated here, which by the board's own
rule is the point where it stops being a recommendation.** The rule it was written under: *"If this
recommendation appears in a second close-out, run it or delete it -- a restated recommendation is
the `Next: X` leak one level up."* It is not being deleted, because more triggers hold now than
did then. **It is item 1 of the next run.**

Re-checked 2026-08-31 (second close-out), three of six triggers hold:

- **Roughly a quarter of the board has shipped since the last full review** (2026-08-29). #238
  through #258 — twenty-one PRs, four of them today.
- **Security-relevant work has merged as a set and nothing has re-reviewed it as one.** Since the
  2026-08-29 adversarial pass (0 HIGH, 0 MEDIUM): S-22, S-23, S-16, and now **a brand-new admin
  endpoint pair with a delete on `webauthn_credential`** (#257). A new endpoint is exactly the kind
  of change that falsifies a reachability claim made about the old set — and S-16 shipped resting on
  one (`resolveByRawToken` is the only door).
- **The recommendation has now been restated once.** That is a trigger in its own right.

Three do **not** hold, and saying so is the point of re-checking: this close-out's scoped ref sweep
found five drifted refs and **all five were inside the neighbourhood of what merged** (working
rule 23's cheap check still holds); estimates did not blow out (three of four items matched, and
the one that did not is written up as working rule 43); and no item is blocked on an unwritten
question.

Slices worth giving agents, given what is actually stale: the two **unchecked MR 25 arity counts**;
premise re-verification across MR 16-19, which have not been touched in a week; the merged security
set including #257 attacked as a set; and the frontend/backend pair, since #258 created a cross-repo
break that is filed on one board only.

## Session log

One line per session -- honoured in spirit, not in width; a review pass gets a paragraph. Three
entries in a row ending `Next: X` means X is being avoided -- say so and either make it real work
or drop it. (Checked 2026-08-24: not currently tripped. Two entries ended `Next: MR 15 #6` and it
then shipped.) **Retention rule (stated 2026-08-29 --
the omission that caused the last lapse): the current session's entries stay here; every close-out
moves the rest to the history file's log archive in the same pass. A close-out MR that grows this
log without moving the older entries is the lapse signal.** The archive, pre-split log included,
is in the [history file](2026-08-22-backend-cleanup-history.md#session-log).

- 2026-08-31 (second run) — **four MRs, three bugs closed, and the board's oldest carried bug went
  with them.** Shipped bug #20 ([#255](https://github.com/themancalledzac/edens.zac.backend/pull/255)),
  bug #17 ([#256](https://github.com/themancalledzac/edens.zac.backend/pull/256)), passkey admin
  deregistration ([#257](https://github.com/themancalledzac/edens.zac.backend/pull/257)) and bug #19
  ([#258](https://github.com/themancalledzac/edens.zac.backend/pull/258)), plus this close-out.
  **Bug #17 had been carried since 2026-08-24 with no checkbox anywhere; the bug category goes
  4 open -> 1**, and #18 is now the only open bug on the board.
  **The one question was asked in the opening message and came back**: bug #19 *surface, not
  refuse*, which is what made it a fourth MR instead of the next session's problem. Both items that
  left their fix open were decided with evidence rather than by size — #17 by finding the loop's
  *other* per-image writes, the passkey last-credential case by asking what refusing it would
  block.
  **Two items shipped larger than their board text implied, and in both cases the board stopped one
  layer too early.** Bug #19's "widen the orphan queries" hid a response-type change and a
  cross-repo break (**working rule 43**). Bug #20's "~10 lines" was right about the fix and had not
  priced the test it named.
  **Reconciliation moved a count for the first time.** The rule-37 leading-`//` figure went
  1,675 -> **1,644** (290/1,385 -> 262/1,382), and the delta reconciles line-for-line against this
  session's own deletions — **working rule 42**. Trailing held at 74, the rule-12 files held at
  10/6/9, and `grep -c '^- \[ \] ' <history>` is still **0**.
  **Five refs drifted and all five were inside the neighbourhood of what merged**, which is the
  cheap check working as designed: MR 17 #8 (382/395 -> **388/401**), MR 18 #9 (loop declarations
  -> **331**/**459**), MR 18 #10 (`updateGif` -> **550**), MR 19 #13 (-> **986**), MR 19 #15
  (-> **848**). MR 19 #17's `143-145` was re-verified by anchor text and had *not* moved.
  **Two MR 25 counts are now marked UNCHECKED rather than carried as verified** — the raw greps
  available do not discriminate constructor arity, and claiming them would have repeated the
  S-20 dispute (working rule 31).
  **Working rule 39 fired again and was caught**: #255 and #256 squash-merged within minutes, before
  item 3 started; checking PR state before branching item 4 kept it off a stale `main`.
  **One finding is deliberately unfiled**: #258's cross-repo break is not on the frontend board,
  because `edens.zac` had another session's dirty branch checked out. It is declared in the
  cross-repo section rather than silently dropped.
  Next: **the full-board review, which is now on its second restatement and is item 1** — see the
  section above. Three of its six triggers hold, including a new admin endpoint in the unreviewed
  security set.

