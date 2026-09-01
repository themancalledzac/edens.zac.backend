# Backend cleanup tracker

Living checklist of what is still open. Check items off as they land; when an MR closes, its
detail moves to the history file (working rule 11) rather than staying here ticked.

Completed detail lives in [`2026-08-22-backend-cleanup-history.md`](2026-08-22-backend-cleanup-history.md).
This file carries the open work and what steers it: the Progress tables, the items carried forward,
the open security findings, the distilled working rules, the open MR waves, decisions needed from
the user, stale side branches, Appendices C and D (open leads), and the current session's log.
Closed outcomes, the working rules' full narratives and the log archive live in the history file.
Keep it that way (working rule 11). **This file must not grow in an MR** -- working rule 53 gives
that a command, and the two counts it compares are stamped in the Progress metrics below.

Source review: 2026-08-22, baseline `main` @ `8c28cf3`, six parallel passes (controllers/API, core collection and content services, media/upload pipeline, security/auth/config, data layer, tests/build). Every finding was verified against the code -- caller greps for dead-code claims, line-level reads for bugs. Unverified suspicions are quarantined in [Appendix C](#appendix-c--unverified-leads).

Line numbers are from the `8c28cf3` baseline. Find symbols by name, not by line, once earlier MRs have shifted the files.

## Progress

| Wave | MRs | Status |
|---|---|---|
| 1 — Deletions | MR 1a-4 | **complete** — [history](2026-08-22-backend-cleanup-history.md#wave-1--deletions) (#159, #160, #161, #162, #164). Two residuals carried forward, below. |
| 2 — Bugs | MR 5-9 | **complete, and its residual is now closed too** — [history](2026-08-22-backend-cleanup-history.md#wave-2--bugs) (#165, #166, #168, #169, #170, #172, #173). Bug #17, carried forward since 2026-08-24, shipped 2026-08-31 ([#256](https://github.com/themancalledzac/edens.zac.backend/pull/256)). |
| 3 — Security hardening | MR 10-11 | **complete** — [history](2026-08-22-backend-cleanup-history.md#wave-3--security-hardening) (#175, #176). Superseded by the 2026-08-24 review; see the security row. |
| 4 — Comments and docs | MR 12-14 | **mostly complete** — [history](2026-08-22-backend-cleanup-history.md#wave-4--mr-12-and-mr-13-complete) (#177, #178, #180, #181, #183, #184) and MR 14 ([#187](https://github.com/themancalledzac/edens.zac.backend/pull/187)). **Wave 4 removed 500 comments for -1,026 words across seven MRs.** MR 14 taught working rule 12 (superseded by rule 37 **as a comment rule only -- its protected-file list is still live**; the three counts at the Inline-comments row were re-run at the ninth close-out, and one of the three had been stale since [#285](https://github.com/themancalledzac/edens.zac.backend/pull/285) -- see the row itself); **two** stale-docblock items still open (was three -- the `filterNonListedChildCollections` docblock closed 2026-08-29 as already rewritten). |
| 5 — Consolidations | MR 15-19 | **Open: MR 18 #10, MR 18 #13's sort split, MR 19 #17.** MR 15, MR 16 and MR 17 are complete. **MR 16 #3 was ticked closed as decided 2026-09-01 (tenth-run review)** -- every number in it has reproduced across three re-derivations and the answer has been "not worth doing" every time. **MR 18 #13's sort split is re-scoped and now BLOCKED**: two of the three producers it named as unsorted are ordered in SQL, and what is left is a Java-versus-SQL collation question nobody can answer without reading the production database's collation. Shipped-MR narrative: [history](2026-08-22-backend-cleanup-history.md#progress-row-narratives-wave-5-chain-moved-2026-09-01). |
| 6 — Conventions | MR 20-22 | **MR 20 closed 2026-08-30 by user decision** -- bare arrays are blessed and no endpoint changed ([history](2026-08-22-backend-cleanup-history.md#mr-20--the-bare-array-decision-closed-2026-08-30-moved-from-the-tracker)). MR 21 and MR 22 not started. |
| 7 — Structure | MR 23-24 | not started |
| 8 — Tests | MR 25-26 | **MR 25 is half done; MR 26 is 2 of 10.** #27 shipped 2026-09-01 ([#297](https://github.com/themancalledzac/edens.zac.backend/pull/297)) and the two guard tests closed 2026-08-24 ([#195](https://github.com/themancalledzac/edens.zac.backend/pull/195), [#196](https://github.com/themancalledzac/edens.zac.backend/pull/196)); the Progress row said "not started" through both. Two of MR 25's four positional/arity members shipped 2026-08-31: `FileEntry` ([#267](https://github.com/themancalledzac/edens.zac.backend/pull/267)) and `resolveCollectionDownloadEntries` ([#271](https://github.com/themancalledzac/edens.zac.backend/pull/271)). The two left are the two the guardrails have been parking: `DownloadResolution.extension` (13 edits, 5 files, touches `src/main`, and 4 of its 6 accessor assertions are the only coverage of the collection-ZIP format fallback) and `CollectionRequests.Update` (**22 sites as of 2026-09-01**, was 21; must ride with the `TestFixtures` pass). |

Four sections below are not waves and had no row here until 2026-08-24, which made them invisible
to anyone navigating by this table. **"Decisions needed from the user" was the fourth and was still
missing its row until 2026-08-24's close-out** -- eight open items, invisible to this table, which
is the same failure the paragraph above was written to fix:

| Section | Status |
|---|---|
| [Open security findings](#open-security-findings) | **3 open — the section refilled 2026-09-01 (tenth run, full-board review): S-29 (MED), S-30 (LOW), S-31 (LOW).** All three sit on the anonymous public read surface, which the twenty-seven closed findings never attacked. Edit gate (rule 36): `grep -c '^- \[ \] \*\*S-'` = **3**, measured on the review branch `docs/full-board-review-2026-09-01-tenth-run`; **re-run it on `main` after the merge (rule 42)**. **The recorded command was broken from the sixth close-out until this review**: it read `'^- [ ] \*\*S-'`, whose unescaped `[ ]` is a bracket expression matching one space, so it returned 0 against any input. **27 closed**, one ledger line each below; **the highest number issued is S-28 and S-25 was never assigned**, which is why three cells once recorded 27, 28 and twenty-five. **This gate counts numbered findings only**; the unsettled questions have their own section and their own row. Prior states: [history](2026-08-22-backend-cleanup-history.md#open-security-findings-row-prior-states-moved-2026-09-01). |
| [Cross-repo findings owed to the frontend](#cross-repo-findings-owed-to-the-frontend) | **5 open as of 2026-09-01 (tenth run): FE-2 through FE-5 plus the newly filed [#294](https://github.com/themancalledzac/edens.zac.backend/pull/294) page-size debt.** **FE-1 is CLOSED as won't-do**: BE-2 was answered "drop the array". **The count lives in the section, not the heading**, so correcting it cannot break this link. **Every FE row was re-verified live against `edens.zac` `origin/main` at `f4e8e25` on 2026-09-01** -- the clone exists on this machine and the board's two claims that it does not are deleted. All five are filed on the frontend board ([#371](https://github.com/themancalledzac/edens.zac/pull/371), merged 2026-08-31) and stay open here until the frontend acts. Prior states: [history](2026-08-22-backend-cleanup-history.md#cross-repo-row-prior-states-moved-2026-09-01). |
| [Decisions needed from the user](#decisions-needed-from-the-user) | **2 open as of 2026-09-01 (tenth run), and NEITHER is waiting on you.** Both sit under [Parked by decision](#parked-by-decision--waiting-on-nobody): gallery passwords (pending a design) and C7's partial indexes (an explicit "not until scale demands it"). **BE-2 was answered: drop the array**, which closes FE-1 as won't-do and turns the removal into a COLD backend item. Edit gate (rule 36): the count is over the section's own `- [ ] ` lines; re-run it and update this row together. **Batching a one-word question into the opening message is what turns it into a same-session MR** -- it has now done so twice (#28, BE-2). Prior states: [history](2026-08-22-backend-cleanup-history.md#decisions-row-prior-states-moved-2026-09-01). |
| [Tests that cannot fail](2026-08-22-backend-cleanup-history.md#tests-that-cannot-fail--closed-2026-08-30-moved-from-the-tracker) | **0 open of 6 — CLOSED 2026-08-30.** The last three shipped in one session (#239, #240, #241), each mutation-proved against `main` first. Two of the three carried a wrong premise that was corrected while closing: the share-link credential is a `Set-Cookie`, not a response-body token; and the `AdminUserControllerTest` pointer the board suggested names a test that does not redden on that mutation. Write-ups in history. |
| [Rule 37 debt](2026-08-22-backend-cleanup-history.md#rule-37-debt--r-1-closed-2026-08-30-moved-from-the-tracker) | **0 open — R-1 closed 2026-08-30 ([#238](https://github.com/themancalledzac/edens.zac.backend/pull/238)).** Taught working rule 39. The wider per-package sweep is not tracked here; it is the Inline-comments row in the category table below. |
| [Stale side branches](#stale-side-branches) | **Branch and worktree list re-run 2026-09-01 (tenth run).** **Ten worktrees, not six** -- five under `edens.zac.backend.worktrees/` and five under `.claude/worktrees/`; `git worktree list` returns eleven rows, the eleventh being the main checkout. Four were created after 2026-08-24 for work that has since merged and none reached this board. **Zero open PRs in the repo**, and **four of the branches this section tracks have no `origin` ref at all**, so the measuring command the section recorded fails on half its rows. Prior state: [history](2026-08-22-backend-cleanup-history.md#stale-side-branches-row-prior-state-moved-2026-09-01). |
| [Unsettled security questions](#unsettled-security-questions) | **5 open: U-1, U-2, U-3, U-7, U-8.** Edit gate (rule 36): `grep -c '^- \[ \] \*\*U-'` = **5**, re-run 2026-09-01 on `main` at `43c6f2c6` -- run it and update this row together. **The section also holds one non-`U-` open box** -- the `RoleRepository.canView`/`isClient` deletion, which is work rather than a question and opens `**Delete` so it cannot move this gate. **The stamp read 7 for two close-outs after U-5 and U-6 shipped**, which is rule 36's failure mode inside the very cell that carries rule 36's instruction: the lead was edited, the gate was not. Prior state: [history](2026-08-22-backend-cleanup-history.md#unsettled-security-questions-row-prior-state-moved-2026-09-01). |

**Board file sizes, the rule-53 gate.** Measured with

```
wc -l ai_docs/reviews/2026-08-22-backend-cleanup-spike.md ai_docs/reviews/2026-08-22-backend-cleanup-history.md
```

tracker **1,867**, history **9,708**, on the review branch
`docs/full-board-review-2026-09-01-tenth-run`. `main` at `43c6f2c6` held tracker **2,064**, history
**7,620**, so this MR is **-197 on the tracker and +2,088 on history**. **Re-run both on `main` after
the merge (rule 42) and restamp this pair** -- a docs branch changes its own count. The tracker's
delta must be <= 0 in every MR that touches it; see **working rule 53**.

Original estimate: roughly 4,500-5,000 lines removed against a few hundred added. The test tree is larger than main -- **35,697 test lines against 28,071 main, re-measured 2026-09-01 on `main` at `43c6f2c6`** with `git ls-files 'src/test/java/**/*.java' | xargs wc -l | tail -1` and the same for `src/main`. The recorded 32.6k / 27.2k were the 2026-08-22 baseline and read as current. About 8% of the test tree tests the Java compiler and Lombok.

| Category | Count | Deletable lines (est.) |
|---|---|---|
| Bugs (fix, not delete) | **21** (5 high) — **21 shipped, 0 open. The bug ledger is closed.** Bug #18, the last one, shipped 2026-08-31 as [#276](https://github.com/themancalledzac/edens.zac.backend/pull/276). Checkbox check: `grep -c '^- \[ \] \*\*Bug #'` = **0**, re-run 2026-09-01 on `main` at `43c6f2c6`. **The recorded command had its `[ ]` unescaped from the sixth close-out until 2026-09-01, which made it return 0 against any input.** Items **#22 through #29** are filed in the same number series but are feature dependencies, doc bugs and coverage items, so they open with `**#NN` and have their own gate: `grep -c '^- \[ \] \*\*#2'` = **2** (#22 and #29), re-run 2026-09-01 on `main` at `43c6f2c6`. The series was invented after rule 36 and had gone eight items with no command behind it. Prior state: [history](2026-08-22-backend-cleanup-history.md#bugs-category-row-prior-state-moved-2026-09-01). | — |
| Security findings | **3 open: S-29 (MED), S-30 (LOW), S-31 (LOW), all filed 2026-09-01 by the tenth-run review.** Checkbox check: `grep -c '^- \[ \] \*\*S-'` = **3**, measured on the review branch; **re-run on `main` after the merge (rule 42)** and edit this cell and the section-table row together (working rule 36). **27 closed** -- the ledger runs S-1..S-24, S-26, S-27, S-28, and **S-25 was never assigned**. **The recorded command was broken from the sixth close-out until 2026-09-01**: `[ ]` unescaped is a bracket expression matching one space and returns 0 on any input. Numbered findings only — the unsettled questions have their own gate. Prior state: [history](2026-08-22-backend-cleanup-history.md#security-findings-category-row-prior-state-moved-2026-09-01). | — |
| Dead code (main) | ~60 methods/fields/files | ~1,000 |
| Inline comments | **RE-RUN 2026-09-01 (ninth close-out) on `main` at `3a53c0cb`, all four ninth-run PRs merged. Leading form: **1,372** (203 main / 1,169 test). Trailing form: **68**, unmoved.** **Both deltas reconcile line-for-line (rule 42):** main `215 -> 203` is -12, all `CollectionRepository`; test `1,192 -> 1,169` is -23 = 21 (`CollectionRepositoryTest`, [#295](https://github.com/themancalledzac/edens.zac.backend/pull/295)) + 2 (`AdminRoleControllerTest`, a rule-47 sweep riding with [#297](https://github.com/themancalledzac/edens.zac.backend/pull/297)). **The command itself was found wrong -- see working rule 50**: `grep -rn` skips a binary-classified test file and returns 1,189 where `git grep` returns 1,192 at the same commit, so the recorded number and the recorded command had never agreed. Use the `git grep` form below. `git grep` is tracked-files-only and cannot see `.claude/worktrees/` at all, which removes that hazard rather than re-checking it. Six close-outs of measurement history: [history](2026-08-22-backend-cleanup-history.md#inline-comment-count-measurement-history). | ~300 net (also low) |

**The two commands, exactly as run** (**rule 31**) -- they carry pipes and cannot live inside a
table cell. Leading form, **`1,372`** (203 main / 1,169 test) at `3a53c0cb`:
```
git grep -c '^[[:space:]]*//' -- 'src/main/java' | awk -F: '{s+=$NF} END {print s}'
git grep -c '^[[:space:]]*//' -- 'src/test/java' | awk -F: '{s+=$NF} END {print s}'
```
**Do not use the old form** -- it is 3 low on the test side and always has been (**rule 50**):
```
grep -rn '^[[:space:]]*//' src/main/java src/test/java | wc -l   # WRONG: skips one binary-classified file
```
Trailing form, **`68`** at `3a53c0cb`:
```
grep -rn '//' src/main/java src/test/java | grep -vE '^[^:]+:[0-9]+:[[:space:]]*//' | grep -v 'https\?://' | wc -l
```
The main/test split is the two leading-form lines above, run separately. **Run them from the repo root against `src/`**; the trailing form is a `grep -rn`, so an unscoped run would triple-count `.claude/worktrees/` (the leading form is `git grep`, which is tracked-files-only and cannot see them at all). The old "~~370~~ 567 measured ... is a floor" was the 2026-08-23 wave-4 start under the old criterion and described nothing current -- the real rule-37 debt is ~3x it. | ~300 net (also low) |
| ^ **re-scoped 2026-08-28** | Working rule 37 turns this from a debloat nice-to-have into a standing rule: **every** inline comment in `src/main` and `src/test` is a violation, not just the ones a rule flagged. **Do not sweep this in one MR** -- take it per package, and take the files working rule 12 protected first: **`AdminBootstrap` (6, and it is in `services/`, not `config/`) and `CollectionControllerProd` (9); both re-run 2026-09-01 on `main` at `43c6f2c6` and unchanged.** `RoleRepository`, `SecurityConfig` and `AdminUserControllerTest` are done. **One recorded exemption**: the second `coverImage` banner in `CollectionControllerProdTest` stays until its "Carried forward" decision lands. **The admin tests neighbouring `AdminUserControllerTest` were explicitly not swept and are each their own MR, unmeasured.** Closed-file chain: [history](2026-08-22-backend-cleanup-history.md#rule-37-per-file-sweep-closed-file-chain-moved-2026-09-01). | — |
| Duplication consolidations (main) | 20 findings | ~500 |
| Dead/boilerplate tests | **10 findings** | ~2,700 (+700 optional) |
| Build/config rot | **Open/closed split dropped 2026-08-31 (third close-out) because it had no backing.** The cell said "9 open" and `grep -n 'C-[0-9]'` across this file returned only the cell itself — no section, no checkboxes, no list. The original findings closed in Wave 1 as **MR 2** ([history](2026-08-22-backend-cleanup-history.md#wave-1--deletions), seven ticked items); the only C-numbered entry ever written down is **C-1, filed and closed 2026-08-30** ([#245](https://github.com/themancalledzac/edens.zac.backend/pull/245)). If config rot is worth tracking again, file items with checkboxes and give the section a gate; do not restore a count nothing can verify. Note two `C` schemes run at once: `C-1` here, and `C7`/`C8` for the Appendix C leads, which are unnumbered bullets. | ~150 |

## Carried forward out of closed waves

Reconciled 2026-08-23 during the history split, re-reviewed 2026-08-24. Waves 1-3 read "complete"
but held **eight live items**, collapsed into five entries. Since then: the `PersonRepository` entry
was closed by MR 15 #6 (decided, not deferred), and the chunked-body residual moved to **S-5** under
"Open security findings". What is left is below -- plus one bug that never had a
row at all (#17), one item found while costing #209's guardrail (the coverImage banner), and three
bugs filed 2026-08-29 (#18-#20, at the end of this section).

- [ ] **The `coverImage` stripping that does not exist, and the test that cannot fail.** *(New row
  2026-08-24, found while writing #209's cost report; taught **working rule 22**.)*
  `CollectionControllerProdTest` has a section headed "Fix 1: coverImage stripped for protected
  CLIENT_GALLERY on list endpoints" whose test asserts only controller pass-through. **No such
  stripping exists** -- `CollectionProcessingUtil.buildBasicModel` sets `coverImage`
  unconditionally from `coverImagesById`, verified by read (`:182`, with `isPasswordProtected`
  computed four lines below). Strip nothing, change nothing, and the test stays green.

  **Recorded exemption (working rule 37): this second banner is excluded from the rule-37 comment
  sweep until this decision lands** -- a sweep deleting it would erase the record of the open
  question. **The frontend already strips** on the public card path (FE #327), so "list-endpoint
  stripping is genuinely wanted" would duplicate an enforcement that already exists.

  **What to decide.** Either delete the banner and rename the test to what it asserts, or treat it
  as a specification with no implementation and do the work. Do not resolve it by reading the
  comment; #209's cost report has what implementing it would break. Background:
  [history](2026-08-22-backend-cleanup-history.md#the-coverimage-stripping-row-background-moved-2026-09-01).

- [x] **Bug #17** (medium) `updateImages` claimed a batch save it did not do — [#256](https://github.com/themancalledzac/edens.zac.backend/pull/256),
  2026-08-31. **Fixed by correcting the log line, not by building a `batchUpdate`** — the loop
  already writes per image through `saveContentTags` and `saveContentPeople`, so batching only the
  `saveImage` calls leaves the endpoint O(N) in statements. Reasoning now lives in `updateImages`'s
  docblock. Write-up in
  [history](2026-08-22-backend-cleanup-history.md#2026-08-31-second-close-out--bugs-17-19-20-and-passkey-deregistration).
- [x] **Four main-dead, test-live members owed to MR 25** -- **CLOSED as an umbrella row 2026-09-01
  (tenth run).** Two shipped ([#267](https://github.com/themancalledzac/edens.zac.backend/pull/267),
  [#271](https://github.com/themancalledzac/edens.zac.backend/pull/271)); the two that remain are
  tracked as their own rows under [Positional constructors that block the `TestFixtures`
  pass](#positional-constructors-that-block-the-testfixtures-pass). **Do not re-file them here.**
  Body and the arity-scanner method:
  [history](2026-08-22-backend-cleanup-history.md#four-main-dead-test-live-members-body-moved-2026-09-01).
- [x] **V19's `admin_home_tile.cover_image_id`** -- **ANSWERED and DROPPED 2026-08-31**, shipped as
  `V59__drop_admin_home_tile_cover_image_id.sql` and verified in the tree 2026-09-01. **This box
  stayed open through five close-outs after the decision landed** and inflated the board's headline
  count by one. Research body and the premise correction it records:
  [history](2026-08-22-backend-cleanup-history.md#v19s-admin_home_tilecover_image_id-research-body-moved-2026-09-01).

### Bugs filed after the waves closed (2026-08-29)

- [x] **Bug #18** (low-medium) `updateLocation` missed the create path's slug-uniqueness check --
  **DONE** ([#276](https://github.com/themancalledzac/edens.zac.backend/pull/276), 2026-08-31). Shipped as specified; the caller-visible 409 is
  byte-identical before and after, because `GlobalExceptionHandler.handleDataIntegrity` discards
  the message. **This closed the last open bug on the board.**
  [Write-up](2026-08-22-backend-cleanup-history.md#bug-18--the-slug-check-and-an-item-that-did-not-price-its-own-payoff-276).
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
- [x] **`AdminUserControllerTest`'s 73 inline comments** — **DONE** ([#272](https://github.com/themancalledzac/edens.zac.backend/pull/272), 2026-08-31), 73 -> 0, `+118 / -73`, one file. Delete-and-relocate as specified; the substantive comments are now each test's docblock. Taught **rule 46** (the checksum needs its metric named: 17 comments deleted moved the rule-37 line count by 16, because one was a trailing `code; //`). [Write-up](2026-08-22-backend-cleanup-history.md#adminusercontrollertests-73-inline-comments-272).

- [x] **#23 (doc bug)** `ai_ec2.md` carried a stale second copy of the `.env` template — **DONE** ([#269](https://github.com/themancalledzac/edens.zac.backend/pull/269), 2026-08-31). Both env blocks deleted and replaced with a pointer at `.env.example`; `ai_deployment_strategy.md` untouched. **This did not settle U-1** and the PR says so — see U-1, still blocked. [Write-up](2026-08-22-backend-cleanup-history.md#23--the-stale-env-template-in-ai_ec2md-269).

- [ ] **#22 (feature dependency, not a bug) — `PATCH /api/edit/collections/{id}` does not exist,
  and the frontend's largest open item is blocked on it.** *(Filed 2026-08-31 from the frontend
  board's MA1.)*

  **PREMISE CORRECTED 2026-09-01 (tenth-run review). The route is genuinely missing and the
  behavior the frontend needs already exists.**

  **What still holds.** Five `@PatchMapping`s exist and none is a whole-collection field patch;
  the two on `EditController` are sub-resource patches. Re-derive them with
  `git grep -n "PatchMapping(" origin/main -- 'src/main/java/**/controller/**'` rather than a
  checkout -- `.claude/worktrees/` copies make an unscoped `grep -rn` return false positives here.
  A live scan of `edens.zac` `origin/main` at `f4e8e25` confirms no bare
  `PATCH /api/edit/collections/{collectionId}` exists.

  **What is false.** The item said a `PUT`-style whole-object update "will not do, because two
  fields edited in parallel must not clobber each other". **Both existing PUT routes already behave
  as partial updates** -- every field on the shared write is null-guarded across
  `CollectionProcessingUtil` and `CollectionService`, so a body of `{id, title}` updates title
  alone. What is actually missing is two small things: the `PATCH` verb itself, if the frontend
  needs the verb rather than the behavior; and a way to clear a nullable field, since null already
  means "unchanged".

  **Sequencing changed with the premise.** **Ask the frontend board first whether pointing
  `buildFieldPatch` at the existing null-guarded `PUT` unblocks MA1**, and whether it needs any
  admin-only field. If yes, this closes here as a frontend documentation fix. **COLD, and it should
  not be picked up as backend work before that question is asked.** It remains the
  highest-consequence open item on either board. Ref detail:
  [history](2026-08-22-backend-cleanup-history.md#22-patch-route-ref-detail-moved-2026-09-01).


- [x] **#24 (feature dependency, not a bug) — `COLLECTION` content blocks carried no `locations`,
  so the frontend's shipped `/collections` location filter matched nothing.** — **DONE**
  ([#277](https://github.com/themancalledzac/edens.zac.backend/pull/277), 2026-08-31). **The frontend board's spec was wrong about the size, and in the
  cheap direction**: the locations batch query already ran and `SyntheticCollectionResolver` simply
  never read it, so the fix was one record component on `ContentModels.Collection` plus a copy in
  `fromCollectionModel` -- no new repository method, no new query, no migration, no added N+1.
  Additive public API change: the synthetic list views, the tag view and the `/user` page all gain a
  `locations` array on each `COLLECTION` block. [Write-up](2026-08-22-backend-cleanup-history.md#24--the-locations-component-the-resolver-never-read-277).

- [x] **#25 (same gap as #24) — `people` on `COLLECTION` content blocks was inert for exactly the
  reason `locations` was.** — **DONE**
  ([#293](https://github.com/themancalledzac/edens.zac.backend/pull/293), 2026-08-31). One record
  component on `ContentModels.Collection` plus a copy in `fromCollectionModel`, following #277
  exactly. [Write-up](2026-08-22-backend-cleanup-history.md#25-the-people-component-moved-2026-09-01).

- [x] **#26 (feature dependency, not a bug) — contact messages had no retention TTL, so PII
  accumulated forever.** — **DONE** ([#281](https://github.com/themancalledzac/edens.zac.backend/pull/281), 2026-08-31). **Shipped off, and the first
  opt-in only reports**: `app.messages.retention.days` defaults to `0` and
  `app.messages.retention.dry-run` defaults to `true`, so deploying the MR changes no behaviour at all.
  Both guards mutation-proved. **No frontend half exists or is needed** -- a retention TTL is
  configuration, not a control. [Write-up](2026-08-22-backend-cleanup-history.md#26--a-retention-ttl-shipped-off-281).

- [ ] **#30 (feature dependency, not a bug) -- `messages` had no read marker, so read state lived
  in whichever browser set it.** -- **Backend DONE**
  ([#300](https://github.com/themancalledzac/edens.zac.backend/pull/300), merged 2026-09-01). The
  row stays open because the FE half is owed to the frontend board. `V61` adds
  `read_at TIMESTAMP NULL` plus a partial index; `PATCH /api/admin/messages/{id}/read` sets or
  clears it, 204/404, matching the delete that already ships. `readAt` joins `AdminMessageView`
  additively, so the frontend keeps working until it opts in. `?unread=` and `?q=` shipped in the
  same MR because they are one WHERE clause; `?q=` is what
  [edens.zac#384](https://github.com/themancalledzac/edens.zac/pull/384)'s client-side filter needs
  to stop searching only the rows already loaded. **Still open under MA4:** the notify channel. The
  retention TTL closed as #26.
  [Write-up](2026-08-22-backend-cleanup-history.md#30-the-read-marker-and-the-filters-that-share-its-where-clause-300).

## Cross-repo findings owed to the frontend

**Five open as of 2026-09-01 (tenth run): FE-2 through FE-5, plus the newly filed #294 page-size
debt.** FE-1 closed as won't-do when BE-2 was decided. The count lives here rather than in the
heading, so correcting it cannot break the Progress row's link.

The 2026-08-24 batch closed and lives in
[history](2026-08-22-backend-cleanup-history.md#cross-repo-findings-owed-to-the-frontend). This
section was re-opened by #258 and re-derived 2026-08-31 by a full pair scan: every endpoint path
literal in `edens.zac`'s `app/lib/api/*.ts` against every `@RequestMapping`/`@*Mapping` pair under
`controller/`. **No frontend call site targets a backend route that no longer exists**, so nothing
here is a live 404. The five below are type drift and one dev-workflow change.

**All five are filed in `edens.zac`** ([#371](https://github.com/themancalledzac/edens.zac/pull/371),
docs-only, **merged 2026-08-31**) as C14, C15, C16, H7 and G6, **verified line by line 2026-09-01**.
They stay open here until the frontend acts on them. Filing history:
[history](2026-08-22-backend-cleanup-history.md#cross-repo-section-filing-history-moved-2026-09-01).

- [x] **FE-1: the location page's `images` array can now carry GIFs, and the component types it
  `ContentImageModel[]`.** **CLOSED as won't-do 2026-09-01 (tenth run), by the BE-2 answer.** The
  array is being dropped, so the location page stays on `searchImages({ locationId })` and no GIF
  ever arrives through `LocationPageResponse.images`. The "never reads the field" premise was
  re-verified live against `edens.zac` `origin/main` at `f4e8e25`. The GIF goal has a cheaper home:
  teach `searchImages` to return GIFs, filed as a backend item under MR 19. Premise chain and the
  fix shape that is no longer needed:
  [history](2026-08-22-backend-cleanup-history.md#fe-1-the-location-page-gif-chain-moved-2026-09-01).
- [ ] **FE-2: `page` and `size` are silently ignored on the location endpoint.** *(Filed 2026-08-31,
  third run; refs re-verified live 2026-09-01 against `edens.zac` `origin/main` at `f4e8e25`.)*
  **`app/lib/api/collections.ts:157`** builds `/collections/location/${slug}?page=&size=` -- the
  recorded `:150` is the function declaration, not the URL literal. `CollectionControllerProd:124-133`
  reads `collectionPage`, `collectionSize`, `imagePage`, `imageSize` (the four parameters are at
  127-130), and Spring drops the two unknown params. **Live but invisible today because the
  frontend's `PAGINATION.collectionPageSize` is 35 (`app/constants/index.ts:175`) and the backend's
  `collectionSize` default is also 35.** `imageSize` defaults to **50** and is a separate parameter;
  the recorded "both defaults are 35" did not say which two numbers it meant (rule 14). **This is the
  only FE row that was never contingent on BE-2, and the frontend board calls it the cheapest item it
  has (C14).** `getCollectionsByLocation(slug, page,
  size)` therefore accepts two arguments that do nothing, and any caller asking for a second page gets
  page 0. Fix: rename the two query params to `collectionPage` and `collectionSize`.
- [ ] **FE-3: `imageWidth` / `imageHeight` can now be null.** *(Filed 2026-08-31, third run.)*
  [#249](https://github.com/themancalledzac/edens.zac.backend/pull/249) changed the missing-dimension
  default from `0` to `null` (**`ImageProcessingService:464-467`**, re-derived 2026-09-01; the recorded
  `469-472` lands on `setIso`). The wire type was already `Integer`,
  so only the value moved. `app/types/Content.ts:156-157` declares them `number | undefined`. **No
  runtime change** — `getContentDimensions` gates on `if (block.imageWidth && block.imageHeight)` and
  both `0` and `null` are falsy, so both land on the same fallback. Type accuracy only:
  `imageWidth?: number | null`.
- [ ] **FE-4: two admin passkey endpoints exist with no frontend consumer.** *(Filed 2026-08-31, third
  run.)* [#257](https://github.com/themancalledzac/edens.zac.backend/pull/257) added `GET` and `DELETE
  /api/admin/users/{id}/passkeys[/{credentialId}]`. The frontend has `registerPasskey` but no list and
  no deregister, and `/admin/users/[id]` has nowhere to show or revoke an authenticator. **Additive,
  not drift** — an unbuilt feature, not a defect. Note this interacts with **S-28**: the admin UI is
  where a self-lockout would happen.
- [ ] **FE-5: admin and edit routes are now auth-gated in dev too.** *(Filed 2026-08-31, third run.)*
  [#243](https://github.com/themancalledzac/edens.zac.backend/pull/243) removed `app.admin.enforce-authz`
  rather than pinning it true, so `/api/admin/**` and `/api/edit/**` are gated in every profile. Any
  frontend dev workflow pointing at a dev backend and calling those routes without a session now gets
  401/403. **No frontend code change needed; developers need to be told.** This is the same change
  that invalidated a Critical Rule in the frontend's `CLAUDE.md`, already recorded in the log archive.

### The two cross-repo debts this board declared and never filed

- [x] **MR 19 #19's widened response -- CLOSED as verified-no-action 2026-09-01, not filed.**
  [#283](https://github.com/themancalledzac/edens.zac.backend/pull/283) made `ImageSearchResponse` a
  `PagedResponse<ContentModels.Image>`. Both `edens.zac` consumers were re-verified live at
  `f4e8e25` and read exactly the keys `PagedResponse` pins. Nothing is owed to the other board.
  [Detail](2026-08-22-backend-cleanup-history.md#mr-19-19s-widened-response-verified-no-action-2026-09-01).
- [ ] **[#294](https://github.com/themancalledzac/edens.zac.backend/pull/294)'s page-size default, 30 -> 50 -- REAL, and owed to `edens.zac`.**
  *(Filed here 2026-09-01, tenth run.)* `GET /api/read/content/images/search` now defaults `size`
  to 50 (`ImageSearchFilter.DEFAULT_SIZE`, `@Min(1) @Max(200)`). **Two public pages pass no `size`
  and now silently show 67% more photos**: `app/location/[slug]/page.tsx:82` and
  `app/tag/[slug]/page.tsx:48`. Nothing crashes; it is a visible product change on public routes,
  shipped without the frontend being told. **What the frontend has to decide:** whether 50 is the
  wanted grid size there. Either answer is fine; the debt is that nobody was told. Two riders:
  `SEARCH_RESULT_LIMIT` is exactly **200**, sitting on the new inclusive `@Max(200)`, so one bump
  to 201 turns `/search` into a 400; and #294 deleted admin's clamp, so an admin caller passing
  `size > 200` now gets a 400 instead of 200 rows (no current caller does).

**Backend routes with no frontend consumer, for the record** — unbuilt features, not drift.
**Re-scanned 2026-09-01 in both directions against a live `edens.zac` clone, the first time this has
been possible:** all 110 backend routes matched against every `.ts`/`.tsx` under `app/`, plus
`proxy.ts` and `tests/`.

Already recorded and all confirmed dead: `POST /api/admin/cache/clear`,
`GET /api/admin/metrics/requests`, `POST /api/admin/content/images/ingest`,
`POST /api/admin/content/images/{collectionId}/from-disk`,
`GET /api/admin/content/images/jobs/{jobId}`, `GET /api/read/content/film-metadata`, and FE-4's two
passkey routes.

**Nine more found dead, none previously on this list:** `GET /api/read/content/cameras`,
`GET /api/read/content/lenses`, `GET /api/read/content/people`, `GET`/`PUT /api/read/user/ratings`,
`GET /api/read/collections/{slug}/meta`, `POST /api/admin/content/tags`,
`POST /api/admin/content/people`, `POST /api/admin/content/images/create-collection`.
**`UserRatingOverrideControllerProd` is the interesting one: both of its routes are dead, so the whole
controller and its service path are unreachable from the UI.** That class already has three rows
against it -- MR 23's package move, MR 23's `*Prod` rename and MR 26's missing controller test -- and
this is a fourth reason to deal with it as one piece.

**`GET /api/read/collections/{slug}/download` is NOT dead and has been struck from this list.**
`app/lib/api/downloads.ts:28` builds it as a navigation URL rather than a `fetch`, which is why a
path-literal grep missed it; `downloadCollectionSelectionUrl` at `:41` adds the `imageIds` subset.
`GET /api/read/content/images/{id}/download` is live the same way (`downloads.ts:25`). Do not delete
either.

The `jobs/` and `from-disk` pair is the Appendix C lead about the job-status endpoint being dead;
this scan confirms the frontend half of it and settles that lead.

**Item #22 is not listed here.** `PATCH /api/edit/collections/{id}` is backend work the frontend
is blocked on, which is the opposite direction from this section -- these are findings the frontend
must act on. It lives under [Bugs filed after the waves closed](#bugs-filed-after-the-waves-closed-2026-08-29).
**It was duplicated into both sections on 2026-08-31 (third run)** when [#263](https://github.com/themancalledzac/edens.zac.backend/pull/263) folded it in
while [#252](https://github.com/themancalledzac/edens.zac.backend/pull/252) still held its own copy, and both then merged. The copy here was the shorter of the two and
was removed; nothing was lost.

## Open security findings

Consolidated 2026-08-24 by the full-board review; re-attacked as a merged set 2026-08-25, again
2026-08-29 (adversarial -- 0 HIGH, 0 MEDIUM), again 2026-08-31 (third run) and again 2026-09-01
(tenth run). **Twenty-seven closed** (S-1..S-24, S-26, S-27, S-28; **S-25 was never assigned** and
appears nowhere in either file, which is the gap behind the "28 findings" this board has quoted):
one ledger line each below, with bodies and outcomes in
[history](2026-08-22-backend-cleanup-history.md#security-findings--closed-moved-2026-08-29).
Per-path limiter mapping -- which limiter covers which route -- sits in history's
[S-17 outcome](2026-08-22-backend-cleanup-history.md#s-17-outcome-2026-08-28----not-as-specified-and-two-failures-of-the-same-kind).

**The unsettled questions no longer live in this section.** They moved 2026-08-31 to
[Unsettled security questions](#unsettled-security-questions) with their own gate, because four
open checkboxes sat here while this section's row and classification both said "empty" -- the
rule-36 gate greps `^- \[ \] \*\*S-` and none of them opened that way.

### Open

**Three, all filed 2026-09-01 by the tenth-run review, and all three on the anonymous public read
surface.** Every one of the twenty-seven closed findings lives in auth, session, role-membership,
share or actuator code. `/api/read/content/**` was never attacked as an authorization surface, and
two of its routes have no authorization at all.

- [ ] **S-29** (MED, possibly HIGH) **`GET /api/read/content/images/search` returns every image in
  the database, with no collection-visibility and no gallery-password filter.** Anonymous: no
  cookie, no header, no session. `SecurityConfig:79-80` matches `/api/read/content/**` against no
  rule, so it falls to `anyRequest().permitAll()`; `ContentControllerProd.searchImages` (`:44`)
  passes through `ContentService.searchImages` (`:393`) to `ContentRepository.searchImages`
  (`:767`), whose `SELECT_CONTENT_IMAGE` (`:158`) is `FROM content c JOIN content_image ci ON
  c.id = ci.id` plus three LEFT JOINs. **There is no join to `collection_content` or `collection`
  and no predicate on `collection.visibility` or `collection.gallery_password`.** What comes back
  is `ContentModels.Image` carrying `imageUrl` and `imageUrlRaw`, both unsigned CloudFront URLs
  anyone can fetch.

  **This walks around three gates**: `enforceVisibility` (`CollectionService:1523`), the
  content-stripping at `CollectionControllerProd:80-84`, and `isDownloadAuthorized`
  (`ContentDownloadControllerProd:196-204`). The third is the sharpest contradiction -- that
  controller exists to presign private S3 objects behind a CLIENT-or-cookie check, and this
  endpoint hands out the CloudFront URL for the same image to an anonymous caller. Enumeration is
  easy: `size` is capped at `@Max(200)` with paging, and `personIds` is a filter, so with S-30 an
  attacker picks any user id off the public people list.

  **Severity, stated honestly:** MED as filed. HIGH if any client gallery holds images not also
  published elsewhere, which is a data question this repo cannot answer. Closer to LOW if every
  image is public anyway. **Ask before pricing.**

  **Mutation the test must survive:** seed one image whose only `collection_content` row points at
  a CLIENT_GALLERY with a non-null `gallery_password`, assert an anonymous search response does not
  contain that id, then delete the new visibility predicate from `appendSearchConditions` and watch
  it redden. A test that only counts rows, or that seeds a LISTED image and asserts it is present,
  stays green under that mutation and does not count.
- [ ] **S-30** (LOW) **`GET /api/read/content/people` lists every row in `users`, not every tagged
  person.** `MetadataService.getAllPeople` (`:112`) calls
  `PersonRepository.findAllByOrderByPersonNameAsc` (`:49-52`), which is literally
  `SELECT id, name, created_at FROM users ORDER BY name ASC` -- no `status` predicate and no
  "is tagged in anything" predicate. Since V35 merged people and accounts into one table, this
  returns the id and display name of every account: admins, collaborators, clients, INVITED
  accounts that never onboarded, DISABLED accounts, alongside the tag-only PERSON rows the route
  was built for. The route (`ContentControllerProd.getAllPeople:67`) is anonymous under
  `permitAll` and is in `CacheControlInterceptor.PUBLIC_ROUTES`, so it is shared-cacheable too.
  Email is not exposed; the account roster by name is, plus each `users.id` -- the same id that is
  the `{id}` path variable on `/api/admin/users/**` and the `personIds` filter on S-29. **It is
  also a functional bug**: an account never tagged in a photo appears in the tag-filter dropdown
  and returns zero results.

  **Mutation:** seed a DISABLED account with no `collection_people` and no `content_image_people`
  row, assert it is absent from the response, then drop the new `WHERE` clause and watch it redden.
  A test that only asserts a tagged PERSON is present stays green and does not count.
- [ ] **S-31** (LOW) **a share opt-in is checked when it is added and never again.**
  `UserShareControllerProd.addCollection` (`:167-178`) gates the opt-in on
  `collectionAccessService.canView(principal, collectionId)`. That is the only check.
  `ShareLinkRepository.isCollectionInScope` (`:170-194`) and `findScopeCollectionIds` (`:145-162`)
  both resolve scope as `collection_people` UNION `share_link_collection`, with no join back to
  `role_member` / `role_collection`. So a revoked role grant leaves the collection's tile on the
  link. The removal side already anticipates revocation: `removeCollection`'s docblock says the
  delete is "deliberately NOT gated on the owner's current grant".

  **Why LOW.** Every consumer of the flyby's GENERAL was traced and it is inert -- each screens
  with `isRealUser` or a higher level first. The exposure is the tile metadata on the share page,
  not content access. **This may close on S-14's reasoning rather than be patched**: S-14 answered
  the neighbouring question with "answered, not patched: no second gate". It has not been asked,
  which is the only reason it is a row.

  **Mutation if patched:** grant, opt in, revoke the grant, assert the collection is absent from
  `findScopeCollectionIds` and false from `isCollectionInScope`, then drop the new `role_member`
  join from the `share_link_collection` arm of both queries.

*(S-28's full body sat under this heading, ticked, from the sixth close-out until 2026-09-01, with
its outcome paragraph orphaned under the Closed heading and attached to no bullet. Both are now one
ledger line.)*

### Closed, one ledger line each

Bodies and outcomes in the [history file](2026-08-22-backend-cleanup-history.md#security-findings--closed-moved-2026-08-29).
- [x] **S-28** (LOW) an admin deregistering their own last passkey can lock the admin surface out of itself — [#278](https://github.com/themancalledzac/edens.zac.backend/pull/278), 2026-08-31, grouped with U-6. One docblock paragraph naming the redeploy recovery, plus the WARN. **The re-aiming the item called for was real** -- #265 had rewritten exactly the docblock the item proposed to amend, so the current text was read before the paragraph was written. [Write-up](2026-08-22-backend-cleanup-history.md#s-28--the-recovery-line-re-aimed-278-grouped-with-u-6).
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
- [x] **S-18** (MED) the actuator exclude missed four endpoints meeting its own criterion — #232, 2026-08-28; taught rule 34. **The exclude list is still criterion-incomplete** (2026-08-29): `metrics` (and `info`) meet the stated criterion under `include=*` and sit in neither the exclude nor `MUST_BE_EXCLUDED`, and both tests derive from the same enumeration, so neither can see it. **S-23 above was the chosen fix shape** — the resolved-include boot check, not another name-chase. S-23 shipped; whether this residual is now moot is **U-8**.
- [x] **S-19** settled 2026-08-25, not live — the FE strips and re-derives `x-real-ip`. **Live debt, now tracked as U-5** (it had sat inside this closed line with no checkbox since 2026-08-25, so no gate could see it): `ClientIp`'s javadoc still calls the header's presence "the trust signal".
- [x] **S-20** (MED) "may hold a session" was inlined in two files beside the predicate — #230, 2026-08-28; taught rules 31 and 33.
- [x] **S-21** (LOW) `regenerateInvite` minted links for accounts that can never redeem — #228, 2026-08-27.
- [x] **S-14** (MED) an admin could put any collection, including another client's protected gallery, into their own share scope — **answered, not patched**: no second gate ([#250](https://github.com/themancalledzac/edens.zac.backend/pull/250), 2026-08-31). Leaves open whether `addCollection` should be admin-gated at all; that is a routing question and **it now has its own item, U-6** — the board wrote "needs its own item" on 2026-08-31 and did not write one until the third run.
- [x] **S-16** (MED) disabling an account did not stop its share link serving — #253, 2026-08-31. **Suspend, not revoke.** Shipped as one gate at `resolveByRawToken`, not the two the item specified; the item had also missed `isCollectionInScope`.
- [x] **S-22** (LOW) the two role-membership status guards were separate SQL denylists — #247, 2026-08-31. Shipped as one bound list, **not** the named predicate the item prescribed; taught working rule 40.
- [x] **S-23** (LOW) nothing refused a prod boot with a wider actuator include — #248, 2026-08-31. Exclude list untouched and now redundant; **whether to delete it is an open disposition, tracked as U-7**.
- [x] **S-24** (LOW) two admin mail-send paths were covered by no limiter — **accepted as admin-trusted and documented** ([#250](https://github.com/themancalledzac/edens.zac.backend/pull/250), 2026-08-31).
- [x] **S-26** (HIGH) deregistering a passkey left the sessions that credential minted alive, and the holder could register a replacement from inside the surviving session — [#265](https://github.com/themancalledzac/edens.zac.backend/pull/265), 2026-08-31. One call, as specified; the work was the test. **Hardening `register/**` was left out of scope** and stays an open question. Mutation table in [history](2026-08-22-backend-cleanup-history.md#s-26-outcome-2026-08-31----the-fix-was-one-call-and-three-mutations-were-needed-to-prove-it).
- [x] **S-27** (LOW) `resolveByRawToken`'s docblock claimed a biconditional #257 made false — [#265](https://github.com/themancalledzac/edens.zac.backend/pull/265), 2026-08-31, rode with S-26 as the item said it should.

### What the closed set is worth carrying forward

**The gate, not the wording.** A summary claim about a section must be measurable by the command it
cites, or it drifts the moment something is filed in a shape the command cannot see. This section's
row read "the section is EMPTY" for a session while it held four open checkboxes that did not open
with `**S-`. Those questions now have their own section and their own gate.

**The blocked-item table shape is worth copying.** Each row named the question and who answers it,
in the form the user could act on; both blocked items were answered within a day of being written
that way.

**S-14's answer did not fit either option the question offered** -- it was a principle about a
different endpoint class. It was recorded as closed **with the gap named**, rather than forced into
"allow, documented". A question that comes back with an answer to a slightly different question is
still an answer; write down which question it answered.

*(Tests that cannot fail closed 2026-08-30 -- all six. Write-ups, mutation results and the two
premise corrections:
[history](2026-08-22-backend-cleanup-history.md#tests-that-cannot-fail--closed-2026-08-30-moved-from-the-tracker).)*

### Verified sound, do not re-open

Attacked 2026-08-24, again 2026-08-29, and again 2026-08-31 (third run); held every time. Index
only -- the full reasoning lives
in the [history file](2026-08-22-backend-cleanup-history.md#security-findings--closed-moved-2026-08-29):

- **S-16's reachability claim HOLDS -- re-tested 2026-08-31 with #257's endpoints in the tree. Do
  not re-derive this.** #253 shipped one gate instead of the two the item specified, resting on the
  claim that `resolveByRawToken` is the only way a token becomes a link. Four checks -- producers of
  `AuthPrincipal.shareId`, callers of `resolveByRawToken`, consumers of a shareId, and the bypass
  looked for and not found -- all came back clean, and #257 does not touch the share path at all.
  The four checks in full:
  [history](2026-08-22-backend-cleanup-history.md#s-16s-reachability-claim-the-four-checks-moved-2026-09-01).

- **`addMember`'s `<> 'PERSON'` denylist: do not tighten it to an ACTIVE allowlist** -- S-1's
  shipping falsified the live-grant argument for one. S-22 pins the rule and names the predicate
  without changing membership.
- **The #189 `/api/read/user/**` matcher** and **the flyby-principal invariant** hold as recorded.
- **Do not unify `mayHoldSession` and `mayAcceptInvite`** -- re-verified against code 2026-08-29:
  `finishLogin` reads status fresh through the predicate, and the only non-breaking unification
  direction widens `mayHoldSession` to admit INVITED, under which the passkey door is a live hole.
- **Nothing lets a session or passkey outlive a status change today** -- `resolve` re-reads status
  and `isAdmin` on every request; deactivation sweeps then backstops; no path hard-deletes an
  ACTIVE account. (The resolve-slides-the-window-first latent is **U-4** under "Unsettled security questions".)
- **`ShareEmailLimiter` keying and placement are correct** -- session-derived `userId`, checked
  before the token lookup, disjoint key space from the other four limiters.
- **The S-set mutation pins are resistant** -- S-20's enum pin, S-3's #235 parameterization plus
  literal pin, S-17's 429 pin with the refill-observability fix, S-18's containsExactly both ways.

## Unsettled security questions

Open security questions that are not numbered findings. **Promoted out of "Open security findings"
2026-08-31 (third run)**, where the first four sat as plain checkboxes that the rule-36 gate
(`grep -c '^- \[ \] \*\*S-'`) could not see, while that section's row and its classification both
said "0 open -- the section is EMPTY". U-5 through U-8 are worse: they existed only as prose inside
closed `[x]` ledger lines, so no gate anywhere could see them and one of them (U-6) is a row the
board explicitly wrote "needs its own item" about and then never filed.

Edit gate (rule 36): `grep -c '^- \[ \] \*\*U-'` = **5**, re-run 2026-09-01 on `main` at `43c6f2c6`
(tenth run) and unchanged. Run it and update the section-table row together. **U-2 and U-3 had no
bucket in the classification section until 2026-09-01**: U-2 is COLD and answerable in-tree today,
U-3 is BLOCKED (user) and needs a real judgement. **It read 7 for two runs after U-5 and U-6 shipped**: both
were ticked here and neither this stamp nor the Progress row was edited with them. Open: U-1, U-2,
U-3, U-7, U-8.

- [ ] **U-1 -- whether prod actually runs the `prod` profile.** Under `dev`, `SecurityConfig` falls
  through to `permitAll` on `/api/admin/**` and `/api/edit/**`, and neither `ProdSecretGuard` nor
  `InternalSecretFilter` exists to object. S-4 proved the `@Profile("prod")` wiring works;
  **nothing proves prod is named prod.**

  **The evidence in the tree is 2-0 for `prod` and it is not proof.** `.env.example:3` says `prod`
  and is the file `docker-compose.yml` reads; `ai_deployment_strategy.md:289` says `prod` in prose.
  The two `ai_ec2.md` blocks that said `default` were deleted by [#269](https://github.com/themancalledzac/edens.zac.backend/pull/269) (filed as #23).
  `docker-compose.yml:21` makes `SPRING_PROFILES_ACTIVE` required, so whatever the host's `.env`
  says is what runs.

  **Settle it with one command:** read `SPRING_PROFILES_ACTIVE` on the live host, or hit the origin
  without `X-Internal-Secret` and confirm a 403. **BLOCKED on the user** -- it needs host access,
  and the probe runs against production. **ASKED 2026-08-31 and the answer was that it cannot be
  checked right now**, so it is on the record as put and unanswered rather than neglected. **U-7 and
  U-8 stay blocked behind it.** Narrowing chain:
  [history](2026-08-22-backend-cleanup-history.md#u-1-the-profile-question-chain-moved-2026-09-01).
- [ ] **U-2 -- whether Tomcat surfaces `Transfer-Encoding` to `getHeader()`.** S-5's entire fix depends on
  it, and its only test uses `MockHttpServletRequest`, which returns whatever the test put in. If
  Tomcat consumes the header while installing the chunked input filter, the branch never fires and
  the bypass is still open. Settle with an integration test that POSTs a real chunked body to a
  booted server and asserts 411 -- `ActuatorExposureEndToEndTest`
  (`src/test/java/edens/zac/portfolio/backend/config/ActuatorExposureEndToEndTest.java`) already has
  the shape. **CLASSIFIED 2026-09-01 (tenth run): COLD, and it does NOT belong in the blocked pile
  next to U-1.** `RateLimitFilter:112` is
  `if (declaredBodyBytes < 0 && request.getHeader("Transfer-Encoding") != null)` and its only
  coverage is `RateLimitFilterTest:91` and `:121`, both `MockHttpServletRequest`, both returning
  whatever the test put in -- exactly as this item says. **It is answerable today, in-tree, with no
  credentials and no host access.** It had no bucket in the classification section at all until now.
- [ ] **U-3 -- `ACCESS_TOKEN_SECRET` has no rotation story.** It keys gallery HMAC tokens, gallery password
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

  **CLASSIFIED 2026-09-01 (tenth run): BLOCKED (user), and it needs a real judgement rather than a
  word.** The secret has three consumers, re-verified this pass: `TokenCipher:45` (the AES-GCM key
  for share-token confidentiality), `ClientGalleryAuthService:48` (gallery password fingerprints and
  HMAC tokens) and `ProdSecretGuard:33` (the boot check). Rotating it makes every stored
  `share_link.token_cipher` undecryptable and invalidates every live gallery unlock cookie. Nothing
  in the tree makes the key rotatable and no amount of reading it will settle this. It had no bucket
  in the classification section until now.

  **This bullet is also where S-11's missing fact was sitting.** It listed all three uses of the
  secret while S-11's own severity paragraph named only `TokenCipher`. See S-11's outcome in the
  [history file](2026-08-22-backend-cleanup-history.md#s-11-outcome-2026-08-25----the-guard-clause-and-a-fact-the-board-already-had).
- [x] **U-4 -- `SessionService.resolve` slid the session window before reading status** -- **DONE**
  ([#270](https://github.com/themancalledzac/edens.zac.backend/pull/270), 2026-08-31).
  [Write-up](2026-08-22-backend-cleanup-history.md#u-4--the-slide-moved-below-the-status-check-270).
- [x] **U-5 -- `ClientIp`'s javadoc called the header's presence "the trust signal"** -- **DONE**
  ([#274](https://github.com/themancalledzac/edens.zac.backend/pull/274), 2026-08-31). One docblock sentence.
  [Write-up](2026-08-22-backend-cleanup-history.md#u-5--the-trust-signal-sentence-274).
- [x] **U-6 -- whether `addCollection` should be admin-gated at all** -- **ANSWERED and DONE**
  ([#278](https://github.com/themancalledzac/edens.zac.backend/pull/278), 2026-08-31): keep it and document the routing.
  [Write-up](2026-08-22-backend-cleanup-history.md#u-6--a-routing-question-whose-first-answer-rested-on-a-false-premise-278).
- [x] **`RoleRepository.canView` and `isClient` have zero `src/main` callers -- confirmed
  2026-08-25.** The deletion it recommends had no checkbox anywhere on the board until 2026-09-01.
  It is the row below.
- [ ] **Delete `RoleRepository.canView` and `isClient`.** *(Filed 2026-09-01, tenth run. The finding
  had been written down since 2026-08-25 and lived only inside the ticked bullet above, where no
  gate could see it.)* Both methods still exist (`RoleRepository.java:419` and `:439`) and still have
  **zero `src/main` callers** -- every `canView` / `isClient` hit in `src/main` is
  `collectionAccessService.*` -- re-verified 2026-09-01 on `main` at `43c6f2c6`. Six integration test
  classes assert through them, so deleting means rewriting those assertions against
  `CollectionAccessService.effectiveLevel`. **The names are the hazard**: they read like the live
  authorization check and are not. This row opens `**Delete` rather than `**U-` deliberately -- it is
  work, not a question, and it must not move the U- gate.
- [x] **U-5 -- `ClientIp`'s javadoc still calls the header's presence "the trust signal".**
  *(Promoted 2026-08-31 out of S-19's closed ledger line, where it had sat as an untracked "live
  debt" since 2026-08-25.)* Still true: `config/ClientIp.java:14` says "so its presence is the trust
  signal", and S-19 settled precisely because presence is *not* the trust signal -- the frontend
  strips and re-derives `x-real-ip`. One docblock sentence, to be corrected when next in the file.
  **Ref re-verified exact 2026-08-31 (fourth run)** -- line 14 still carries that clause. **COLD.** **DONE** ([#274](https://github.com/themancalledzac/edens.zac.backend/pull/274), 2026-08-31, sixth run). One sentence, docblock only, method body untouched as the guardrail required. The clause now names what actually makes the value trustworthy: the BFF strips any client-supplied `x-real-ip` before re-injecting its own, and `InternalSecretFilter` rejects direct hits under the `prod` profile.
- [x] **U-6 -- whether `addCollection` should be admin-gated at all.**
  *(Promoted 2026-08-31 out of S-14's closed ledger line, which said "that is a routing question and
  needs its own item" and then did not file one.)* S-14 closed on a principle -- every admin endpoint
  through the same admin gate -- and `addCollection` is not an admin endpoint: it sits on
  `UserShareControllerProd` at `/api/read/user/share`, and the admin sentinel in `canView` is what
  makes it answer yes for everything. So the principle rejects the ownership test S-14 proposed, and
  leaves the routing question open. Settle by deciding where this endpoint belongs, not by adding a
  second gate. **ANSWERED and DONE** ([#278](https://github.com/themancalledzac/edens.zac.backend/pull/278), 2026-08-31, sixth run). Asked first; the user's first answer rested on a false premise (`/api/read/user/**` is `hasRole("USER")`, not ADMIN), was re-asked with the evidence, and the answer was **keep it and document the routing**. **[#275](https://github.com/themancalledzac/edens.zac.backend/pull/275) is a dead reference** -- #278 carries the same commit. [Write-up](2026-08-22-backend-cleanup-history.md#u-6--a-routing-question-whose-first-answer-rested-on-a-false-premise-278).
- [ ] **U-7 -- whether to delete the now-redundant actuator exclude list.** *(Promoted 2026-08-31
  out of S-23's closed ledger line.)* **ANSWERED 2026-08-31 by reading `ProdActuatorExposureGuard`,
  with a precondition neither U-7 nor U-8 recorded.** The guard requires the resolved include to
  equal exactly `{health}` and throws from `@PostConstruct` otherwise, so the twelve names in the
  exclude list are unreachable. Redundant: **yes -- exactly where the guard runs.** The guard is
  `@Profile("prod")`. **Do not delete the list until U-1 is settled**: deleting it is safe under
  `prod` and removes the last line of defence under `default`.
- [ ] **U-8 -- whether S-18's criterion-incompleteness is now moot.** *(Promoted 2026-08-31 out of
  S-18's closed ledger line.)* **ANSWERED 2026-08-31, same reading and same precondition.** Moot
  under `prod`, because the guard is fail-closed at startup rather than a filter something can slip
  past. Under `default` `metrics` and `info` remain as unnamed as S-18 left them. **Same dependency
  on U-1 as U-7**, so the two do close together, just not before U-1 does. Derivation:
  [history](2026-08-22-backend-cleanup-history.md#u-7-and-u-8-the-actuator-derivation-moved-2026-09-01).

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
12. **SUPERSEDED by rule 37 (2026-08-28) -- do not follow; full text in history.** Of the four files
    it protected, two are done: `RoleRepository` went to 0 in #285 and `SecurityConfig` to 4 in #243.
    `AdminBootstrap` (6) and `CollectionControllerProd` (9) still carry theirs and take the first
    rule-37 sweeps (re-run 2026-09-01).
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
    cell without the other. **Write the command escaped: `'^- \[ \] \*\*S-'`. An unescaped `[ ]` is a
    bracket expression matching one space and returns 0 against any input** -- three summary cells
    carried the unescaped form from the sixth close-out to 2026-09-01 and agreed with reality only
    because the sections they measured happened to be empty. (Returns **3** on the tenth-run review
    branch, up from **0** on `main` at `43c6f2c6`, where the three findings had not yet been filed.
    **The stamp itself has now rotted twice**: it read "Returns 5 as of 2026-08-29" for two runs after
    #265 took the real figure to 1, then read "Returns 1" for four close-outs after
    [#278](https://github.com/themancalledzac/edens.zac.backend/pull/278) took it to 0. **The rule
    that exists to stop a recorded number rotting has now rotted twice itself**, and both times the
    cells it governs were fine. A rule carrying a measured number has to re-run it like anything
    else.)
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
44. **"The file did not change" is a claim about a base commit, and picking the wrong base turns
    ordinary drift into a fabricated transcription error.** This close-out very nearly shipped a
    working rule built on one. A re-derivation found the canonical `CollectionRequests.Update` site
    at `CollectionServiceTest:2139`, not the recorded `:2056`, checked `git diff 41d928b4..HEAD` on
    that file, got an empty diff, and concluded the file was unchanged since the board last measured
    it -- therefore `:2056` had never been right. **`41d928b4` is #266's own merge commit.** The
    board measured at `3c034c94`, one commit earlier, and `git diff 3c034c94..41d928b4` on that file
    is +91/-8. At `3c034c94` line 2056 held `return new CollectionRequests.Update(` exactly as
    recorded; #266 added 83 net lines above it and 2056 + 83 = 2139. The ref was right, it drifted,
    and the drift was caused by this very session.
    **What makes this dangerous is that the wrong answer is more interesting than the right one.**
    "A transcription error survived three passes" is a finding; "a ref moved because we moved it" is
    bookkeeping, and the first one is what gets written into a rule. Two guards: **diff from the
    commit the number was recorded at, never from the most recent merge**, and when a re-derivation
    concludes a past session was careless rather than that the code moved, check the boring
    explanation first. `git show <base>:<file> | sed -n '<line>p'` settles it in one command.
45. **A fix specified as "one call" is a claim about the diff, not about the test.** S-26's fix was
    one line and its guardrail said so; what that meant in practice was that the one line had three
    distinct wrong forms -- omitted, hoisted above the guard it must follow, or correct but resting
    on a scope predicate nothing pinned -- each needing its own test at its own assertion. Before
    accepting a one-line item as cheap, enumerate the ways that line can be written wrong and count
    a test per way. Evidence: #265 shipped 1 changed line of logic and 6 tests.

46. **A comment count is only reconcilable if you name which metric moved.** The rule-37 checksum
    counts lines whose first non-whitespace is `//`. "Comments deleted" counts comments, and the two
    differ by every trailing `code; //` in the diff. Evidence, 2026-08-31 (fifth run):
    [#271](https://github.com/themancalledzac/edens.zac.backend/pull/271) deleted **17** comments
    from `ContentServiceDownloadTest` and moved the rule-37 line count by **16**, because one was
    trailing. Both numbers are correct about different things, and a close-out that writes one into
    a PR body and reconciles against the other will read as an off-by-one defect that is not there.
    State the metric beside the number.

    **Second half, same run: a recorded absolute rots even while its deltas stay right.** The board
    carried the test-side figure as 1,371 for two runs; the real figure at those same commits was
    **1,374**, and every recorded delta in between was correct. Deltas get re-derived because rule 42
    makes someone check them; the absolute they are subtracted from does not. Re-run the absolute,
    not just the arithmetic. **And use the bracket class** -- `grep '^\s*//'` under BSD grep does not
    honour `\s` and silently returns a different number than `grep '^[[:space:]]*//'`, which is
    rule 31's warning appearing inside rule 42's own checksum. **(The command in this rule was later
    replaced outright -- see rule 50. A reader landing here and stopping gets the superseded form.)**

47. **Rule 37 applies to the region you touched, plus any comment your change makes stale. A
    pre-existing bulk concentration in the same file is a separate MR.** Rule 37 says "delete the
    inline comments already in it" and the rule-37 tracker row says "do not sweep this in one MR --
    take it per package". Read literally the two contradict, and **four agents in one run split on
    it**: [#280](https://github.com/themancalledzac/edens.zac.backend/pull/280) left ~107 lines in
    `CollectionRepository`/`CollectionRepositoryTest` to keep its diff scoped, while
    [#282](https://github.com/themancalledzac/edens.zac.backend/pull/282),
    [#283](https://github.com/themancalledzac/edens.zac.backend/pull/283) and
    [#285](https://github.com/themancalledzac/edens.zac.backend/pull/285) each swept the banners in
    the files they edited. All four were defensible, which is the tell that the rule was
    underspecified rather than that anyone was careless. The boundary above is the ruling; when you
    leave a concentration, **file it as its own item** rather than leaving it silent -- that is what
    turns a skipped sweep into a tracked one.

48. **A line estimate on an "extract a shared helper" item counts the deletions and forgets the file
    the extracted code lands in.** The saving is one copy instead of N, not fewer lines, and the net
    is usually near zero once the new file's own body and docblocks are counted. Evidence:
    MR 18 #11 was estimated at "~95 lines" and shipped at **+80/-80, net zero**, because the 47-line
    `CollectionGraphUtil` is the single copy plus its docblocks; MR 18 #9 was estimated at ~110 net
    deleted and shipped at **-51**, because the two callers cannot collapse to nothing. Apply this to
    every remaining extraction item -- MR 18 #13's "net ~0" is already right about it, the others are
    not. Price the destination file, then subtract.

49. **Before pricing an item as expensive to cover, check whether an integration test already drives
    the path.** The board has mis-priced coverage cost this way twice. #12b was parked because the
    second copy "sits inside a large update flow that is expensive to cover", and
    `CollectionLinkSecurityIntegrationTest` already drove both writers against real Postgres and
    already had a `linkViaStructureTab` helper sending exactly what the admin Structure tab sends --
    the new tests were the same shape as tests already in that file. Grep the writer for existing
    integration coverage before writing "the blocker is coverage"; the expensive-looking half is often
    already paid for.


50. **The board's own recorded comment-count command undercounts the test side by 3, and has since
    the day that file was written.** `grep -rn '^[[:space:]]*//' src/main/java src/test/java` silently
    skips `ImageMetadataExtractorKeywordFlagTest.java` entirely: its `XMP_HEADER` literal ends in a
    NUL byte, which the XMP packet format requires, so BSD grep classifies the file as binary and
    emits nothing for its 3 comments. `git grep -c '^[[:space:]]*//'` reads it as text. **The file is
    correct; the command is not.** Measured 2026-09-01 (ninth close-out) at `b02520b1`: the recorded
    command returns 215 / **1,189**, `git grep` returns 215 / **1,192**, and the board had recorded
    **1,192** -- so the recorded number and the recorded command have never agreed, by exactly 3.
    **Use `git grep -c` for both endpoints from now on**, and treat every test-side absolute in the
    chain below as 3 low if it was taken with the `grep -rn` form. This is **rule 31 appearing inside
    rule 42's checksum for the second time** -- rule 46's first half was `\s` under BSD grep, this is
    binary detection -- which is enough repetition to state the general form: *a checksum command is
    itself a recorded number and rots the same way.*

51. **`inOrder.verify` and plain `verify` are not the same assertion, so "X is a strict subset of Y"
    across the two is a claim to test, not to read.** Plain `verify(mock).f()` fails on a *second*
    matching call; an `inOrder` chain consuming one invocation per position has no obvious reason to.
    A test-deletion MR that deletes the plain-verify twin on the strength of the word "subset" is
    therefore deleting cardinality coverage it never checked for. **Settle it by mutation: duplicate
    the call and see whether the survivor reddens.** Evidence, 2026-09-01
    ([#296](https://github.com/themancalledzac/edens.zac.backend/pull/296)): duplicating
    `collectionRepository.deleteById(id)` **does** fail the inOrder test, so the subset claim held --
    but it held as a fact about Mockito, not as a consequence of the word. Generalizes to every
    remaining "second test is redundant" item on this board.

52. **"The `@Validated` proxy is missing under `standaloneSetup`" is a claim about method parameters
    only. Do not generalize it to `@Valid @RequestBody` DTOs.** Constraints on a `@RequestParam` or
    `@PathVariable` need the AOP proxy and are silently unenforced without it, which is the real gap
    #290 found. Constraints on a record component of a `@Valid @RequestBody` go through the
    `WebDataBinder`, which `standaloneSetup` **does** build, and have been enforced in these tests all
    along. #27 was filed as a repo-wide gap on exactly this conflation and the audit found the true
    population was **one method** (2026-09-01,
    [#297](https://github.com/themancalledzac/edens.zac.backend/pull/297)). **Corollary, and it is the
    cheap half:** before scheduling any "this whole category is untested" item, enumerate the category
    first. This one was a single `git grep` for constraint annotations under `controller/`.

53. **The tracker must not grow in an MR. If it did, narrative that belongs in the history file is
    still sitting in a table cell.** Rule 11 says outcome write-ups go to history; it never said how
    to tell when they had not. The failure mode is mechanical: a close-out prepends its new finding
    to a cell and pushes the old text down with a "Prior text" marker, because prepending is one
    edit and moving is two. Across the last fourteen commits to `main` that touched this file, ten
    grew it, one left it unchanged and two shrank it, taking it 1,605 -> 2,064. The tenth-run review
    then added 530 lines in one commit, more than all that drift combined, while the history file
    moved 347. **Every MR that touches the tracker runs this and reports both numbers:**

    ```
    wc -l ai_docs/reviews/2026-08-22-backend-cleanup-spike.md ai_docs/reviews/2026-08-22-backend-cleanup-history.md
    ```

    **The tracker's delta must be <= 0.** If it is positive, the MR is not finished: find the cells
    that grew, move their superseded halves to a named history heading, and link the heading. Run it
    on `main` before and after the merge, not on the branch (**rule 42**) -- a docs branch changes
    its own count. A cell over ~1,200 characters is the tell; a "Prior text" marker is proof.


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
- [ ] "PARENT-shaped" vocabulary at `CollectionService:114` and `UserPageAssembler:26` -- dead since the enum deletion; both refs re-verified exact 2026-09-01. **`TagViewResolver` does not contain that phrase** (2026-08-25); it says "synthetic PARENT model" and "tag-view PARENT model". The vocabulary point survives, the grep target does not. **A grep for `PARENT` in those files finds five more docblock uses the row never listed: `CollectionService:563`, `:1553`, `:1557`, `:1558` and `UserPageAssembler:38`. Two of them (`:1557`, `:1558`) are the deliberate "do not key on `type == PARENT`" warning the closed `filterNonListedChildCollections` row decided to keep -- do not sweep those.** Whoever does this rewrite should work the list of seven and mark which are warnings, rather than leaving a sweep to guess.
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

- [x] #3. One keyed rate limiter -- **CLOSED AS DECIDED 2026-09-01 (tenth-run review): not worth
  doing.** Four copies, not three; every number reproduced exactly at `43c6f2c6` for the third
  consecutive run and the answer has been "no" every time. It was closed as a decision, not a
  deferral, the way `AuthPrincipal`'s constructor was. **Stop re-deriving it.** The four structural
  reasons the merge does not work:
  [history](2026-08-22-backend-cleanup-history.md#mr-16-3-one-keyed-rate-limiter-body-moved-2026-09-01).
- [x] #4. One AWS config class. **DONE 2026-08-31 (third run).** `S3Config` and `SesConfig` are one `AwsClientConfig` with a shared `AwsCredentialsProvider` bean; 127 source lines became 89, and all four `@Bean` method names are unchanged. **The zero-test-coupling claim held but was incomplete**: 51 test classes load the full context and start only because `application-test.properties` supplies three AWS property keys, so renaming `aws.s3.region` to a neutral `aws.region` would fail all 51 at context load. The key was left as `aws.s3.region` deliberately and the class docblock says why. [Write-up](2026-08-22-backend-cleanup-history.md#mr-16-4--one-aws-config-class-and-the-property-key-that-had-to-stay).
- [x] #5. One CloudFront invalidation implementation. **DONE 2026-08-31 (third run).** `ReadCacheInvalidator` gained a public `invalidatePaths(List<String>)`; `ImageProcessingService.invalidateCloudFrontPaths` is deleted and that constructor went arity 10 -> 9. **The `markChanged()` trap was worse than the item's wording** -- its two `READ_SURFACE_PATHS` constants are API routes that match no media key at all, so routing image deletes through it would leave deleted bytes served from the edge until their own TTL expired. Mutation-proved before shipping. [Write-up](2026-08-22-backend-cleanup-history.md#mr-16-5--one-cloudfront-invalidation-and-a-trap-worse-than-the-items-wording).

## MR 17 — Controllers

- [x] #7. Admin image list duplicates the prod image search -- **DONE** ([#290](https://github.com/themancalledzac/edens.zac.backend/pull/290),
  2026-09-01, eighth run). One shared `ImageSearchFilter` `@ModelAttribute`; it also found four
  request-body constraints that were never enforced, filed and closed as #27. Taught rule 52.
  [Write-up](2026-08-22-backend-cleanup-history.md#mr-17-7--the-filter-record-and-the-constraints-that-were-never-enforced-290).
  Body: [history](2026-08-22-backend-cleanup-history.md#mr-17-7-tracker-body-moved-2026-09-01).
- [x] #8. Role membership is writable from two endpoint pairs backed by the same repository calls
  -- **DONE** ([#285](https://github.com/themancalledzac/edens.zac.backend/pull/285), 2026-08-31). The users-side pair delegates to the roles-side
  service; both pairs stay, because the frontend drives two different screens from them.
  [Write-up](2026-08-22-backend-cleanup-history.md#mr-17-8--delegation-with-a-shape-worth-a-second-look-285).
  Body: [history](2026-08-22-backend-cleanup-history.md#mr-17-8-tracker-body-moved-2026-09-01).

## MR 18 — Services

- [x] #9. The from-disk and ingest background loops were ~70 lines of copy-paste -- **DONE**
  ([#279](https://github.com/themancalledzac/edens.zac.backend/pull/279), 2026-08-31), at half the advertised saving.
  [Write-up](2026-08-22-backend-cleanup-history.md#mr-18-9--the-shared-upload-loop-at-half-the-advertised-saving-279).
  Body: [history](2026-08-22-backend-cleanup-history.md#mr-18-9-tracker-body-moved-2026-09-01).
- [ ] #10. `updateGif` reimplements the tag/people/location merge blocks that `ContentMutationUtil` already owns as `updateImage*Optimized`. **Find both by name.** `ContentService.updateGif` spans **550-639**; the three helpers sit at `ContentMutationUtil` **177** (Tags), **199** (People), **221** (Locations), re-derived 2026-09-01 on `main` at `43c6f2c6`. **"The helpers only use the content id" is FALSE** -- all three call `setTags`/`setPeople`/`setLocations`, declared on subclasses rather than `ContentEntity`, so the fix needs a return-the-set signature, not a retype, and it weakens `ContentServiceTest.updateGif_persistsPeopleAndLocations` (`ContentServiceTest.java:144`). **One thing that makes it cheaper than described**: `updateGif` never calls those setters at all -- it computes the merged set and persists ids through `saveContentTags`, `saveContentPeople` and `saveContentLocations`, so a return-the-set helper serves the gif path directly and only the image call sites gain a `setX` line. Realistic ~180, not ~40, dominated by the test rewrite. **COLD and unworked for four close-outs, and absent from every run's `Next:` since the sixth**, which makes it invisible to the leak detector. Either work it or take it off the COLD list with a reason. Ref drift chain: [history](2026-08-22-backend-cleanup-history.md#mr-18-10-ref-drift-chain-moved-2026-09-01).
- [x] #11. Four near-identical BFS walks -- **DONE** ([#288](https://github.com/themancalledzac/edens.zac.backend/pull/288), 2026-09-01, eighth
  run). Five walks, one visitor; the estimate forgot the new file.
  [Write-up](2026-08-22-backend-cleanup-history.md#mr-18-11--five-walks-one-visitor-and-an-estimate-that-forgot-the-new-file-288).
  Body: [history](2026-08-22-backend-cleanup-history.md#mr-18-11-tracker-body-moved-2026-09-01).
- [x] #12. `nextOrderIndex` logic -- **DONE** ([#284](https://github.com/themancalledzac/edens.zac.backend/pull/284), 2026-08-31). "Five places"
  was premise-corrected to three that compute the index; the item was wrong twice, in opposite
  directions. Two copies were deliberately left and closed separately as #12b.
  [Write-up](2026-08-22-backend-cleanup-history.md#mr-18-12--the-item-was-wrong-twice-in-opposite-directions-284).
  Body: [history](2026-08-22-backend-cleanup-history.md#mr-18-12-tracker-body-moved-2026-09-01).
- [x] **`CollectionRepositoryTest`'s 21 inline comments and `CollectionRepository`'s 12** --
  **DONE** ([#295](https://github.com/themancalledzac/edens.zac.backend/pull/295), 2026-09-01, ninth run). 33 -> 0.
  [Write-up](2026-08-22-backend-cleanup-history.md#collectionrepository-comment-concentration-295).
  Body: [history](2026-08-22-backend-cleanup-history.md#collectionrepositorytests-21-comments-tracker-body-moved-2026-09-01).
- [x] **#12b. The two `nextOrderIndex` copies #284 deliberately left in `CollectionService`** --
  **DONE** ([#291](https://github.com/themancalledzac/edens.zac.backend/pull/291), 2026-09-01, eighth run), at a coverage price the item had wrong.
  [Write-up](2026-08-22-backend-cleanup-history.md#12b--the-last-two-nextorderindex-copies-and-a-coverage-price-that-was-wrong-291).
  Body: [history](2026-08-22-backend-cleanup-history.md#12b-tracker-body-moved-2026-09-01).
- [ ] #13. **Re-scoped 2026-09-01 (tenth-run review). The dedupe half is confirmed dead; the sort half is not the finding the board recorded, and what is left is BLOCKED on a question about the production database.**

  **Dedupe half -- DROP IT, both grounds verified at `43c6f2c6`.** Nine `Records` construction sites, 4 Tag and 5 Location; **the count holds exactly**. Tag at `ContentModelConverter:328`, `MetadataService:431`, `SyntheticCollectionResolver:152`, `ContentService:970`; Location at `ContentModelConverter:665`, `MetadataService:439`, `CollectionService:265` and `:267`, `CollectionProcessingUtil:160`. Declarations `convertTagsToModels:323`, `toTagModel:430`, `toLocationModel:438`, all exact. **Three refs drifted, and all three sit in the group the eighth close-out had flagged under rule 23 as not re-derived** -- `ContentModelConverter` Location 657 -> **665**, `SyntheticCollectionResolver` 150 -> **152**, `ContentService` 986 -> **970**. The board's own hedge was correct. Net ~0 lines, because every copy and every replacement is one line, and **the layering flip is verified rather than asserted**: `Records.java` imports only `JsonProperty`, `types.FilmFormat` and `LocalDate`, and no file anywhere under `model/` imports from `entity/`, so a static `from(entity)` factory would be the repo's first `model -> entity` import. Closed on the merits.

  **Sort half -- the recorded finding is a category error, and two of its three members are wrong.** The old text said `MetadataService`, `SyntheticCollectionResolver` and `ContentService` "do not sort". `MetadataService.getAllTags` (`:49`) and `getAllLocations` (`:368`) are ordered **in SQL** by `TagRepository.findAllByOrderByTagNameAsc` and `LocationRepository.findAllByOrderByLocationNameAsc`, both `ORDER BY <name> ASC`. `SyntheticCollectionResolver.toTagRecords` (`:148`) gets its list from `TagRepository.findTagsByCollectionIds`, which ends `ORDER BY t.tag_name ASC` (`:231`). `LocationRepository.findLocationsByContentIds` (`:180`) and `findLocationsByCollectionIds` (`:262`) order by name too, so the Java sorts in `ContentModelConverter.resolveLocations` and `CollectionProcessingUtil` are re-sorting already-sorted rows. **`toTagModel` and `toLocationModel` are single-entity mappers -- they map one row and cannot sort. Naming them as the unsorted producers was the category error.** **No endpoint returns an unordered tag or location list.** The one genuinely unordered site is `ContentService.buildUpdateResponse` (`:970`), which maps five `Set`s through `mapOrNull` over `HashSet` iteration order -- and it is the "what did we just create" echo on a mutation response, not a listing.

  **What is left is a collation question, not a code question, and it BLOCKS this item.** The real difference is SQL `ORDER BY <name> ASC` versus Java `compareToIgnoreCase`. Those agree under a locale collation and disagree under `C`/`C.UTF-8`, where uppercase sorts before lowercase. **Nothing in this repo pins it**: no `LC_COLLATE`, no `initdb` argument, no `COLLATE` clause anywhere, and the test container is `postgres:16-alpine`, whose musl default is not a typical EC2 `en_US.UTF-8`. **Do not open an MR until someone reads the production collation.** If it is a locale collation this item closes entirely; if it is `C`, two endpoints return differently-cased orderings and the split is worth ~10 source lines plus ~5 tests. Ask it the way #28 was asked -- at the top of a session.

  **One slice recommended deleting this row outright rather than re-listing it a third time.** It is kept because the collation question is a real, cheap, answerable thing and deleting the row loses the record of why the original finding was wrong. **If the collation comes back as a locale collation, close it that day.** Prior text and the original 10+4 correction: [history](2026-08-22-backend-cleanup-history.md#full-board-review--run-2026-09-01-tenth-run).

## MR 19 — Query efficiency and data layer

- [x] #14. `convertEntityToModel` loaded the same content row twice. **DONE**
  ([#218](https://github.com/themancalledzac/edens.zac.backend/pull/218), 2026-08-25) — two
  queries to one, and **the first item in seven to need no adjustment at implementation time**,
  which is what taught working rule 27. The method had no test at all; the two added tests are the
  only mutation detectors. Write-up (deletion cost table for the two dead finders included) moved
  2026-08-29 to the [history file](2026-08-22-backend-cleanup-history.md#mr-19-14-outcome-2026-08-25).
- [x] #15. `getUpdateCollectionData` fetched the collection row twice -- **DONE**
  ([#280](https://github.com/themancalledzac/edens.zac.backend/pull/280), 2026-08-31). The projection landed; the fixture churn was not predicted,
  and it left ~107 comment lines behind, filed separately (rule 47).
  [Write-up](2026-08-22-backend-cleanup-history.md#mr-19-15--the-projection-and-the-fixture-churn-nobody-predicted-280).
  Body: [history](2026-08-22-backend-cleanup-history.md#mr-19-15-tracker-body-moved-2026-09-01).
- [x] #16. `findCurrentContentCollections` N+1. **DONE** ([#216](https://github.com/themancalledzac/edens.zac.backend/pull/216)) —
  201 queries to 1. The diagnosis was exact; **the suggested fix was not, and would have shipped a
  silent bug** (its `IN (:ids) OR referenced_collection_id IN (:ids)` clause drops the parent
  scope). [Full write-up](2026-08-22-backend-cleanup-history.md#mr-19-16-outcome-2026-08-25----the-suggested-clause-was-the-bug).
- [ ] #17. Smaller items, **all four to be found by name -- this row has carried the single most-drifted ref on the board**: (a) `UserInviteService.validate`/`redeem` duplicate token resolution, into `findLiveInvite`; (b) pagination normalization re-inlined in `CollectionService.getCollectionWithPagination` -- find `int normalizedPage`, three lines, currently `145-147` on `main` at `43c6f2c6`, and **do not record a number for it**; (c) `CollectionProcessingUtil.toEntity`'s `defaultPageSize` parameter and `applyPaginationDefaults` are redundant with each other; (d) `ImageProcessingService.uploadToS3`/`streamFileToS3` duplicate key and URL construction; (e) the EmailService HTML skeleton **three times, not twice** -- `buildHtml`, `buildInviteHtml` and `buildShareLinkHtml`, the third added by [#213](https://github.com/themancalledzac/edens.zac.backend/pull/213) under an explicit guardrail not to fold it in there (optional, ~50-70 lines). **Members (a) and (d) have zero `src/test` references and are scheduled next.** #213's write-up sent this consolidation to MR 24; that was wrong, it lives here. Ref drift chain, including the `143-145` reading that was anchor-text-verified hours before #266 invalidated it: [history](2026-08-22-backend-cleanup-history.md#mr-19-17-ref-drift-chain-moved-2026-09-01).

  **RE-DERIVED 2026-09-01 (tenth run) on `main` at `43c6f2c6`, and 13 of 17 refs hold.** `validate` **158**, `redeem` **257**, `redeem`'s internal caller at **211**, and there is still no `findLiveInvite`; `toEntity` **566** with `setContentPerPage(defaultPageSize)` at **586** and `return applyPaginationDefaults(entity)` at **588**, `applyPaginationDefaults` **924**; `uploadToS3` **715** and `streamFileToS3` **742**; `buildHtml` **195**, `buildInviteHtml` **246**, `buildShareLinkHtml` **301**. **Four drifted.** The pagination normalization is **`145-147`**, not `147-149`. **`uploadToS3` has 7 callers, not 6** (`ImageProcessingService` 176, 202, 283, 633, 640, 647, 671); `streamFileToS3`'s 2 is correct (270, 566). And the "mirroring" docblock lines are at **243** and **298**, not 246 and 301 -- those are the method declarations. **The `720`/`747` pair this paragraph carried was already superseded by the bullet above it and is deleted; `715`/`742` are right.**

  **The guardrail "pick the members that share a file" is unactionable and is replaced.** The five members live in five separate files -- `UserInviteService`, `CollectionService`, `CollectionProcessingUtil`, `ImageProcessingService`, `EmailService` -- one member each, and no two share one. **This is five independent MRs with zero merge contention. Take (a) the `UserInviteService` token resolution and (d) the shared S3 put: both are private-helper extractions with zero references in `src/test`, so nothing in the suite is forced to change and the tests stay a genuine check. Combined net ~-14 lines.**

  **Member (b) carries a trap the item never named. Do not take it without this.** `DEFAULT_PAGE_SIZE` at `CollectionService:106` is `default_content_per_page` = **30**. `PaginationUtil.normalizeCollectionPageable` uses `default_collection_per_page` = **10**. Reaching for the obviously-named helper silently drops the main collection read endpoint from 30 items a page to 10. **The safe call is `normalizePage(page)` plus `normalizeSize(size, DEFAULT_PAGE_SIZE)`**, which are byte-equivalent to the inlined expressions. 45 test references to `getCollectionWithPagination` are the tripwire, not the edit count.

  **Member (c) is the expensive one and the item prices it at nothing.** Dropping `toEntity`'s `defaultPageSize` parameter changes the method's arity: **11 call-site edits across 4 files** -- 8 test `toEntity(` sites (7 passing a literal `30`, plus `CollectionServiceTest:169`'s `eq(request), anyInt()` stub), `CollectionListReadRepositoryIntegrationTest:143`, and 2 in `CollectionService` (`:361`, `:384`). It is safe only because all three defaults are 30; say that in the change or someone will check.

  **Two sub-items struck 2026-08-24, both premises dead:**
  - *`ensureDimensions` twins* -- already refactored. The shared work is hoisted into
    `putDimensionsFromHeader`; what remains is two 6-line wrappers differing only by log message.
  - *EXIF-versus-ISO format detection duplicated between the two date parsers* -- **the premise is
    false**. `parseImageDate` does no format detection at all: it splits on `[: T-]` and takes
    numeric runs. There is no second copy and nothing to fold. What IS real, and was noted in the
    history file but never given a row: `parseImageDate` returns **month 13** for a nonsense date
    and builds an S3 path from it. That is a robustness bug, not a consolidation. **The pointer said "see the row in 'Decisions needed'" and that row is no longer there** -- `parseImageDate` was one of the three answered and shipped 2026-08-30 in #243, and its reasoning moved to
    [history](2026-08-22-backend-cleanup-history.md#decisions-answered-2026-08-30-moved-from-the-tracker).
    Corrected 2026-08-31 (third run). The pointer was prose rather than a link, which is why the
    anchor check did not catch it.
- [x] #18. `EquipmentRepository` repeated each SELECT column list 3-4 times -- **DONE**
  ([#282](https://github.com/themancalledzac/edens.zac.backend/pull/282), 2026-08-31, grouped with #20). Re-priced down first: 9 of 11 lists are
  hoistable. [Write-up](2026-08-22-backend-cleanup-history.md#mr-19-18-and-20--grouped-and-20-miscounted-again-282).
  Body: [history](2026-08-22-backend-cleanup-history.md#mr-19-18-tracker-body-moved-2026-09-01).
- [x] #19. `ImageSearchResponse` was a strict subset of `PagedResponse` -- **DONE**
  ([#283](https://github.com/themancalledzac/edens.zac.backend/pull/283), 2026-08-31). The widened response was re-verified live against the
  frontend 2026-09-01 and owes the other board nothing.
  [Write-up](2026-08-22-backend-cleanup-history.md#mr-19-19--pagedresponse-and-a-premise-that-is-still-soft-283).
  Body: [history](2026-08-22-backend-cleanup-history.md#mr-19-19-tracker-body-moved-2026-09-01).
- [x] #20. `Records.FilmFormat` shadowed the `FilmFormat` enum -- **DONE** ([#282](https://github.com/themancalledzac/edens.zac.backend/pull/282),
  2026-08-31, grouped with #18). Miscounted again on the way in.
  [Write-up](2026-08-22-backend-cleanup-history.md#mr-19-18-and-20--grouped-and-20-miscounted-again-282).
  Body: [history](2026-08-22-backend-cleanup-history.md#mr-19-20-tracker-body-moved-2026-09-01).
- [x] #21. **DONE** ([#266](https://github.com/themancalledzac/edens.zac.backend/pull/266), 2026-08-31) — the location endpoint's N+1, up to 150 queries where 6 do. Shipped exactly as the item specified, the second consecutive item needing no adjustment. **Taught that a re-merge warning needs its own test**: concatenating the two batches passes a test that only checks which converters were called. Item body, mutation table and reasoning in [history](2026-08-22-backend-cleanup-history.md#mr-19-21-outcome-2026-08-31----the-n1-and-the-reordering-that-would-have-ridden-along).
- [ ] **Drop the orphan `images` array from `GET /api/read/collections/location/{slug}`.**
  *(Filed 2026-09-01, tenth run, out of the answered BE-2 decision. The decision is recorded under
  [Decisions needed from the user](#decisions-needed-from-the-user); this row is the work.)*

  **Scope.** Delete `images` from `LocationPageResponse`, and with it `batchConvertOrphans`,
  `ContentRepository.findOrphanContentByLocationName` (`:440`),
  `countOrphanContentByLocationName` (`:461`) and their tests. **Keep `totalImages`** -- one cheap
  COUNT with a plausible use. `imagePage` and `imageSize` become dead request parameters and go too.

  **Measured cost of leaving it, on `main` at `43c6f2c6`:** roughly 7 of ~15 SQL queries per
  location page load exist only to build this array (11 of ~19 if any content there is a GIF), plus
  30-60 KB of JSON generated and dropped on the floor. **The caveat:** the frontend ISR-caches that
  fetch, so the cost is paid per revalidation, not per visitor, and the performance win is modest.
  **The stronger argument is contract hygiene** -- a public response field no client reads is a
  field nobody can safely change later, and this one has already caused two rounds of cross-repo
  confusion. **Breaking for any client outside this repo; there are none**, verified by a live scan
  of `edens.zac` `origin/main` on 2026-09-01. **COLD.**
- [ ] **Teach `searchImages` to return GIFs.** *(Filed 2026-09-01, tenth run.)* This is the
  cheaper home for the GIF visibility that [#258](https://github.com/themancalledzac/edens.zac.backend/pull/258)
  was reaching for and that BE-2's option 1 would have bought. `GET /api/read/content/images/search`
  is where every image on the location and tag grids already comes from, so widening it puts GIFs on
  both without rebuilding either page's data source. **Not yet scoped** -- price it before scheduling,
  and read the response record the query feeds first (rule 43; #258 is that rule's own evidence).
  **COLD.**

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
  behavior, which is the point.

  **RE-DERIVED MECHANICALLY 2026-09-01 (tenth run) and every figure holds** -- the best-maintained row
  on the board, and it needs no edit beyond this stamp. `AdminController` Map responses, 12:
  `123, 236, 264, 321, 372, 381, 390, 407, 434, 473, 480, 487`. Map bodies, 4: `265` (`deleteImages`,
  no `@Valid`) and the three rename endpoints at `447, 456, 465`. Four more elsewhere:
  `WebAuthnController:145` (body), `EditController:95`, `CollectionControllerProd:174`,
  `ContentControllerProd:115` (responses). **20 distinct lines across 19 endpoints**, `deleteImages`
  still the one endpoint contributing two. The eight raw-`Map` service methods are
  `ContentService.updateImages` **124**, `.deleteImages` **336**,
  `CollectionService.applyCollaboratorImageEdits` **721**, and `MetadataService` **57, 120, 199, 263,
  312**. Command:
  `git grep -nE 'ResponseEntity<Map<|@RequestBody.*Map<' -- 'src/main/java/edens/zac/portfolio/backend/controller'`.

  **The test-side sizing has no gate and never had one**: "~27 source sites against 59 Map-shaped
  lines in 17 test files; ten of the affected endpoints have zero test references" is not reproducible
  by any command on this board. Give it one before quoting it to schedule the work.

## MR 22 — Remaining convention sweeps

- [ ] **#29 (dead annotation) — `ContentControllerProd`'s `@Validated` now has nothing to enforce.**
  *(Filed 2026-09-01, ninth run, out of #27's audit and [#294](https://github.com/themancalledzac/edens.zac.backend/pull/294)'s landing. It opens
  `**#29` and so moves neither ledger gate.)*

  `@Validated` builds the AOP proxy that enforces constraints on **method parameters**. #294 moved
  `searchImages`'s `page` and `size` into `ImageSearchFilter`, where `@Valid` on the
  `@ModelAttribute` enforces them through the `WebDataBinder` instead, so the class now carries
  **zero** constraint-annotated method parameters and the annotation builds a proxy for nothing.
  This is the "a controller whose constraints turn out to be unreachable is a finding, not a test
  to write" case #27's guardrail anticipated, arriving from the other direction.

  **RE-VERIFIED EXACT 2026-09-01 by two slices independently.** `@Validated` at
  `ContentControllerProd.java:29`, its import at `:18`;
  `git grep -rn '@Validated' -- src/main src/test` returns exactly two hits repo-wide, that
  annotation and a docblock mention at `GlobalExceptionHandler.java:142`. Across the whole
  `controller/` package, zero method parameters carry a constraint annotation.

  **Three lines, zero test churn, and the cheapest open item on the board.** `GlobalExceptionHandler`
  `:142`'s docblock names a source that will no longer exist, so it is the third line.
  **Guardrail:** this is the only `@Validated` in the repo, so there are no siblings to sweep, and
  **do not delete the handler** -- `GlobalExceptionHandlerTest:74` throws the exception directly.

- [ ] `ResponseEntity<?>` twice: `UserSelectsControllerProd.list` (**`:55`, was `:59`** -- re-verified 2026-08-24; serves two different shapes from one GET — split or wrap) and `MessagesControllerPublic:43` (throw a `RateLimitedException` handled globally, which also unifies the 429 handling -- **five sites in three body shapes, re-derived 2026-09-01; the row listed three sites and a session working its list would leave two behind**: empty at `AuthController:74`, `UserShareControllerProd:131` and `WebAuthnController:152`, Map at `CollectionControllerProd:183-184`, `ErrorResponse` at `MessagesControllerPublic:48-52`, correct). Note `MessagesControllerPublic` is in `controller/pub/`, not `controller/prod/`, which is worth writing down because the two refs beside it are `controller/prod`.
- [ ] Try-catch in controllers, **two catching sites** (not three -- the third went with bug #15 in MR 7, [#168](https://github.com/themancalledzac/edens.zac.backend/pull/168), confirmed gone by grep): `AdminUserController.mergePreview` (try at **`541`**) and `.merge` (try at **`567`**), three catch clauses between them (`546`, `569`, `571`); map via `ResourceNotFoundException` plus a new `ConflictException` handler. **Both methods have zero tests** -- the 1,510-line `AdminUserControllerTest` never names either -- so this is an untested behavior change on two admin endpoints. A risk, not a saving, and the one row in MR 22 that needs a decision rather than a sweep: write the tests first or accept the change. **`git grep -n 'try {' -- '.../controller'` returns a third hit, `WebAuthnController:195`. It is a `try/finally` with no catch, clearing the attempt cookie. It is not a third site** -- said here so the next reader does not "find" it and widen scope.
- [ ] `@Value` field injection: **6 sites, re-derived mechanically 2026-09-01; the recorded 9 is dead.** `AwsClientConfig:29`, `:32`, `:35` (feeding `@Bean` methods), `CollectionControllerProd:56`, `ShareControllerProd:45`, `DownloadUrlService:54`. Every other `@Value` in `src/main` is already a constructor parameter. **The "six in `S3Config` and `SesConfig`" half of the old count no longer exists and the "they fold into MR 16 #4" instruction is spent** -- both classes were merged into `AwsClientConfig` by [#261](https://github.com/themancalledzac/edens.zac.backend/pull/261) / [#262](https://github.com/themancalledzac/edens.zac.backend/pull/262). Move to constructor parameters, following the `WebAuthnController` pattern. Test coupling is exactly **five** `ReflectionTestUtils.setField` calls. Also `@Autowired` on constructors at **five** classes: `AuthLoginLimiter`, `ClientGalleryAccessLimiter`, `ShareEmailLimiter`, `WebAuthnChallengeStore`, `WebAuthnService`. **The real size is 1 deletion and 4 javadoc notes** -- only `AuthLoginLimiter` has a single constructor; the other four have a package-private test constructor, so `@Autowired` is load-bearing. Fifteen minutes.
- [ ] Fully qualified names inline: **11 sites, re-derived mechanically 2026-09-01 over `src/main/java` excluding imports, package lines and javadoc; was 14.** `ContactMessageLimiter:68`, `GalleryAccessCookies:33` and `:34`, `BaseDao:87`, `:106` and `:201`, `CollectionRepository:785`, `PersonRepository:76`, `CollectionProcessingUtil:819`, `CollectionService:576` (the `isGalleryAccessAuthorized` parameter), `TagViewResolver:115`. **Three dropped off and both drops are [#282](https://github.com/themancalledzac/edens.zac.backend/pull/282)**: `EquipmentRepository`'s three went when it hoisted its column constants, and `Records.java`'s one went when `Records.FilmFormat` was renamed `FilmFormatOption`. **So the recorded blocker -- "`Records.java` still needs consolidation #20 first" -- is spent. #20 shipped as #282 and this item is unblocked.** `isGalleryAccessAuthorized` is declared at **`575`**; the recorded `581`/`582` drifted -6, **the seventh correction to that one ref, so the number is deleted for good -- find it by name.** Import-only, **zero test coupling**.
- [ ] `Optional.get()` -- **CLASSIFIED LINE BY LINE 2026-09-01 (tenth run), which closes the
  arithmetic this item had been re-deriving for a week.** Raw sweep, exactly as run:
  `grep -rn --include='*.java' '\.get()' src/main/java | wc -l` -> **59** on `main` at `43c6f2c6`.
  Exactly **11** are Atomic -- `JobTrackingService:172-175` (four `AtomicInteger` accessors, fields
  declared at `:30-33`), `AdminHomeService:42` (`AtomicReference`), and
  `ImageUploadPipelineService:457-459` and `:491-493` (six, reading `JobStatus`'s `AtomicInteger`
  fields through record accessors without importing the type, which is exactly why an import-based
  exclusion undercounts). **That leaves 48 `Optional.get()`, and none of the 48 is a `Supplier`,
  `ThreadLocal` or `Future`** -- the caveat this item carried about those is discharged. Largest
  holders: `MetadataService` 6, `ContentMutationUtil` 5, `ImageProcessingService` 5, `AuthController`
  4, `AdminUserController` 4. Zero test coupling. **This is not an MR** -- the disposition is
  "rewrite opportunistically when touching these methods", now with a real denominator. **Do not
  re-derive the arithmetic; re-run the sweep only if you intend to fix lines**, and do not restamp
  the `ImageUploadPipelineService` refs, which move constantly.


- [ ] Magic number 2500 at both `resizeImage` call sites in `ImageProcessingService` (**`186` and `277` on `main` at `43c6f2c6`; a third copy sits in a docblock at `140`**). **Both drifted -5 from the recorded `191`/`282`, and they drifted after the "re-verified unchanged 2026-08-31" stamp that this row carried** -- [#279](https://github.com/themancalledzac/edens.zac.backend/pull/279)'s shared upload loop moved them. **Find them by the literal, not the line: these two refs have now drifted twice under a stamp saying they had not.** Name it.
- [ ] `JobStatus.status` is a stringly-typed field with its states in a trailing comment
  (`JobTrackingService`). **Split the item.** Making it an enum is COLD and non-breaking (Jackson
  serializes an enum to the same string) but costs **39 test references across three files,
  re-counted 2026-09-01** -- `ImageUploadPipelineServiceTest` 28, `AdminControllerTest` 6 and
  **`JobTrackingServiceTest` 5, which the recorded file list misses**; the recorded "~45 across two
  files" was wrong on both halves. The trailing `// PENDING, PROCESSING, COMPLETED, FAILED` comment
  at `JobTrackingService:29` is a rule-37 violation on its own and a one-line fix that does not
  need the enum decision. Adding `COMPLETED_WITH_ERRORS` is **UNBLOCKED** -- there is no frontend
  job-status poller at all, so the new enum value breaks no consumer. **The more interesting
  finding is that the whole job-status endpoint may be dead**, which is the Appendix C lead.
- [ ] Verb-style routes `POST /collections/createCollection` and `POST /content/content` (plus a third the item missed, `GET /api/admin/collections/{slug}/update`). **Both confirmed live in the frontend** (`app/lib/api/collections.ts`, `app/lib/api/content.ts`, one caller each), with **62** backend test references (re-counted 2026-09-01; was 61). All three routes re-verified exact: `AdminController:104`, `:220`, `:140`. The alias half is COLD; the retire half needs a frontend release.
- [ ] Route the gallery-access save failure through an exception instead of a `saved()` boolean
  with a hand-built 400 (`CollectionAdminController`). **This is an undeclared wire change**: today
  a failure returns 400 with a `GalleryAccessResponse` body; through an exception it returns 400
  with `GlobalExceptionHandler.ErrorResponse`. 30 test references across 4 files. **BLOCKED, and
  precisely specified**: the frontend's `saveGalleryAccess` reads `result.saved` and `result.reason`
  straight off the 400 body, so both would come back undefined and the admin UI would silently
  degrade to a generic message. **The blocker is a small frontend change** -- have it read the
  `ErrorResponse` shape first, then land the backend change.

---

# Wave 7 — Structure

## MR 23 — Package moves (rename-only)

- [ ] `controller/user/` is a one-class package (`UserRatingOverrideControllerProd`) that belongs with its **nine** siblings in `controller/prod/` (**re-counted 2026-09-01; the recorded "five" is stale** -- `CollectionControllerProd`, `ContentControllerProd`, `ContentDownloadControllerProd`, `ShareControllerProd`, `UserControllerProd`, `UserFollowsControllerProd`, `UserSavesControllerProd`, `UserSelectsControllerProd`, `UserShareControllerProd`). **This class is named in four separate rows across three sections** -- this one, MR 23's `*Prod` rename below, MR 26's missing controller test, and the cross-repo scan's finding that both its routes are dead. Nothing cross-references them. Deal with it as one piece.
- [ ] Request records have two homes: `RoleRequests`/`UserRequests` (`controller/admin/`) and
  `InviteRequests` (`controller/auth/`) versus `MessageRequests`/`CollectionRequests`/
  `ContentRequests`/`CollaboratorRequests` (`model/`). Move the three strays into `model/`.
  **Thirteen files, zero net lines** (re-counted 2026-08-27). **The strongest argument is not in
  the item**: `UserMergeService` imports `UserRequests` from a controller package, so a service
  reaches up into the controller layer. **`controller/admin/` also holds two constraint/validator
  pairs** -- `GrantableLevel` and `AccountStatus` -- so bean-validation types live there by
  precedent rather than by decision. **Decide that explicitly when this MR runs**: either a
  `validation/` package for all four, or say in the doc that constraints live beside the requests
  they constrain. Leaving it undecided is how a third pair gets added the same way. Detail:
  [history](2026-08-22-backend-cleanup-history.md#request-record-homes-long-form-moved-2026-09-01).
- [ ] Optional: drop the `*Prod` suffix now that no controller carries `@Profile` (verified: the only two `@Profile` hits under `controller/` are javadoc text saying there is no gating). **10 main classes and 9 test classes -- 19 files renamed, re-counted 2026-09-01.** The recorded "10 test classes, 23 files" was wrong: there is no `UserRatingOverrideControllerProdTest` (that gap is MR 26's own row), and nobody could re-derive 23 from any counting rule the row states. `grep -rl ControllerProd src/main src/test` does return 23, but that is files *mentioning* a `*Prod` name, not files renamed -- say which unit the number is in (rule 14). **Do not sweep `ProdSecretGuardTest` or `ProdActuatorExposureGuardTest` in; neither is a controller.** Much the largest item in MR 23, and it should not share an MR with the two cheap moves above.

## MR 24 — Service extraction and remaining design items

- [ ] `AdminUserController` is a service wearing a controller's clothes: **twelve injected fields** -- three repositories (`AppUserRepository`, `RoleRepository`, `WebAuthnCredentialRepository`), seven services, one sibling controller (`AdminRoleController`) and a `frontendBaseUrl` -- across **614** lines, with a **1,510**-line test. Re-measured 2026-09-01 on `main` at `43c6f2c6` by `wc -l` and by counting `final` fields. **Three of its four recorded numbers had rotted**, all of them sitting outside the neighbourhood of anything that merged. Extract the invite and passkey flows into services and leave the controller as routing. Prior figures: [history](2026-08-22-backend-cleanup-history.md#adminusercontroller-row-prior-figures-moved-2026-09-01).

  **Find these by name; the numbers are gone for good** -- this item's ref list drifted three times
  in four days, which is working rule 26 happening inside the item that recorded the lesson. The
  `@Transactional` orchestration blocks are `createUser`, `regenerateInvite`, `upgradeUser`,
  `updateUser` and `merge`; the afterCommit hook is `sendInviteEmailAfterCommit`, called from the
  first three.

- [ ] Same shape, smaller: `UserShareControllerProd` computes grant and candidate sets inline with
  a repository. Move it into `ShareLinkService`. **Find the two methods by name; this row has
  carried no line numbers since 2026-08-29 and that is deliberate** -- three separate
  "de-positionalized" passes wrote fresh numbers and each was invalidated within a day (working
  rule 26). They are `buildSettings`, called from two places, and `candidateCollections`, which
  holds the `memberCollectionIdsForUser` call. The file is **231** lines on `main` at `43c6f2c6`;
  the row's old "227 lines today" clause was the same mistake it documents twice and is deleted.
  Ref chain: [history](2026-08-22-backend-cleanup-history.md#usersharecontrollerprod-row-ref-chain-moved-2026-09-01).
*(`Synthetic.blogsOnly` -- **row DELETED 2026-09-01, tenth-run review.** Its premise was FALSE, its
two refs had since drifted, and the false premise was the only argument for the prescribed fold.
Nothing survived. The catalog claim itself still holds: `spec.blogsOnly()` is passed through to
`CollectionRepository.findNonEmptyOrderedByVisibilityIn`, which branches on it at `:484`. Full
reasoning: [history](2026-08-22-backend-cleanup-history.md#syntheticblogsonly-deletion-note-moved-2026-09-01).)*
- [ ] `MessageService` is a pure pass-through with a speculative docblock. Keep it for layering or delete it, but drop the justification.
- [ ] The validator components (`MetadataValidator` repeats its 3-line blank-string guard **five** times -- `21-23`, `33-35`, `45-47`, `59-61`, `62-64`; **the sixth block at `65-67` is `defaultIso == null || defaultIso <= 0`, a numeric check and not a copy, so the recorded "six, not four" was six only by counting it. Re-read 2026-09-01 -- say five identical string guards plus one numeric, so nobody hunts a sixth string guard**; `ContentValidator` is similar) are the "unnecessary utility classes" CLAUDE.md bans. Replace with bean validation on the DTOs when next touched. **~199 source lines across 3 files, not ~60**, plus `@Mock` removal in **5** test files (**re-derived 2026-08-25**, was 6: `ImageProcessingServiceTest`, `ContentServiceTest`, `ImageUploadPipelineServiceTest`, `ContentServiceDownloadTest`, `MetadataServiceTest`) and a constructor arg off 4 services, which is exact -- a 9-file change, so "when next touched" is right.
- Executor handling in `ImageUploadPipelineService` -- **promoted 2026-08-29 to bug #20 under
  "Carried forward"**: a real bug (an unwaited executor on shutdown), not a design note. This list
  had carried the promotion instruction unexecuted since 2026-08-24. The misnaming half
  (`rawUploadExecutor` runs whole disk and ingest jobs) rides with the bug fix.
*(Note, not a checkbox -- demoted 2026-09-01. `AdminHomeService:33-34` declares an
`AtomicReference<List<...>>` cache, read at `:42`, written at `:47`, cleared only by `evictAll()`.
No TTL, per-instance. Fine single-node. The row prescribed nothing -- "note it for any multi-instance
future" is not work -- so it was a checkbox that could never be ticked, counting against the board's
open total forever. Premise re-verified; it is recorded here and off the gate.)*
- [ ] Service decomposition, the standing item. The four files are `CollectionService`,
  `ContentService`, `ImageProcessingService` and `CollectionProcessingUtil` -- **roughly 5,000 lines,
  and that figure has moved under one percent across 26 MRs of dedicated cleanup.** "Waves 5-7 shrink
  these" is not what the data shows; the waves have been shrinking other files. Decide the split
  boundaries before the next feature lands in them. **COLD -- this needs a decision, not research.**

  **Do not re-record the per-file line counts. They rot every run and the argument does not depend on
  them.** For the record, measured once on `main` at `43c6f2c6`: 1,756 / 990 / 1,357 / 933, total
  **5,036**. The board had carried 1,769 / 1,006 / 1,357 / 933 and a total of 5,065 -- two of four
  components and the total wrong, none of the movement attributable to anything in the recorded
  history. **Third consecutive run in which this row's numbers rotted outside the neighbourhood of
  what merged**, which is research nobody asked for on a row that is explicitly a decision.

---

# Wave 8 — Tests

## MR 25 — Shared fixtures and consolidation

- [ ] `new ContentModels.Image(` with 31 positional components: **14 test call sites across 11 test files**, by `git grep -o 'new ContentModels.Image(' -- 'src/test' | wc -l` on `main` at `43c6f2c6`. Two more sites in `src/main` are outside this item's scope and are not in the 14 -- say which figure you mean (**rule 31**). The "7 of which have their own private helper" figure is **unreproducible and should be dropped or given a command**. `CollectionRequests.Update` is the same shape: the canonical record has **22** components and the deletion target is the **17**-arg compat constructor at its **22** test call sites; **quote the [Positional constructors](#positional-constructors-that-block-the-testfixtures-pass) row, which is the maintained copy.** **Re-measured 2026-09-01 by walking each construction to its balanced closing paren: 767 lines of positional construction across 17 test files** -- `ContentModels.Image` 364 over 14 sites, `CollectionRequests.Update` 403 over 25. **The recorded 745 had no stated method.** One `TestFixtures` class with builders. **Net ~-370, not ~-600**, and **price it by rule 48**: the win is one place that knows the 31-component shape instead of 39 sites, not the line delta.
- [x] `services/CollectionServiceTest.java` assert/verify twins -- **DONE** ([#296](https://github.com/themancalledzac/edens.zac.backend/pull/296),
  2026-09-01, ninth run). Both deletions were mutation-settled first, which is where rule 51 came
  from. [Write-up](2026-08-22-backend-cleanup-history.md#collectionservicetest-assertverify-twins-296).
  Body: [history](2026-08-22-backend-cleanup-history.md#collectionservicetest-twins-tracker-body-moved-2026-09-01).
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
- [ ] `ImageUploadPipelineServiceTest`'s 1:1 verify ratio suggests some verify-only tests worth a pass. **The ratio holds exactly -- 33 `@Test` and 33 `verify(` over 1,283 lines -- but the file also has 42 `assertThat(` calls, so the file-level ratio does not by itself identify a verify-only test. Count per test method, not per file (rule 14).**

### Positional constructors that block the `TestFixtures` pass

**Sizing note added 2026-08-24 from #209, and it applies to every item that adds a field to a
record.** Adding a component costs the component, **every `with*` copy method**, and **every
positional construction site in test**. None of that is visible from an item's wording. So when
sizing any "add a field to X" item, run `grep -rn "new <Record>(" src/main src/test` first and
count. That number, not the record edit, is the size -- and it is the argument for the
`TestFixtures` pass below, because each future record change pays the toll again until it lands.

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
sites are precisely the sites a builder collapses -- doing them separately rewrites the same 22 sites
twice. It does **not** hold for `FileEntry`, `resolveCollectionDownloadEntries` or
`DownloadResolution.extension`: none has a builder proposed, and none shares a call site with either
fixture target. Bundling them makes the MR bigger for no reason.

- [ ] `model/CollectionRequests.java` -- 17-arg `Update` constructor, **22** test call sites, re-derived 2026-09-01 on `main` at `43c6f2c6` with a paren-balanced arity scanner over `-- 'src/test'`: 25 raw = 22 at arity 17 plus 3 at arity 22. `CollectionServiceTest` carries **8 of the 22**. **This row records no per-site line numbers, deliberately** -- the nine it used to carry all moved within one run, which is the tenth review's second lesson. Re-derive by running the scanner, not by trusting a number. **The row's own figure has held across four re-derivations**; [#296](https://github.com/themancalledzac/edens.zac.backend/pull/296)'s body filed it as drifted only because it counted `src/main` and `src/test` against a test-only figure (**rule 31**). Prior text: [history](2026-08-22-backend-cleanup-history.md#collectionrequestsupdate-row-prior-text-moved-2026-09-01).

  **STOP RECORDING PER-SITE LINE NUMBERS FOR THIS FILE (rule 5, applied 2026-09-01).** All nine
  refs this row carried drifted -43 to -46 within one run, and it is the worst-drifting ref set on
  the board. Derive them by name:

  ```
  git grep -n 'new CollectionRequests.Update(' -- src/test/java/edens/zac/portfolio/backend/services/CollectionServiceTest.java
  ```

  **Its size is a moving target that grows with test coverage** -- the 22nd site arrived with a new
  file from [#291](https://github.com/themancalledzac/edens.zac.backend/pull/291). Re-run the scanner rather than quoting the number, and record
  the method beside whatever you measure. **The scanner the board quotes, `arity2.py`, is not in
  this repo**, so the recorded command is not runnable as written (rule 31's spirit). Either commit
  it or stop quoting it.

  **CLASSIFIED 2026-09-01: BLOCKED (ordering) on the `TestFixtures` pass, not COLD.** Deleting the
  compat constructor pushes all 22 sites to the 22-arg form with five explicit nulls each, and the
  builder pass then rewrites the same 22 again. **The dependency is narrower than "the `TestFixtures`
  pass"**: `CollectionServiceTest` is the only file in both target lists, at unrelated call sites,
  so if `TestFixtures` is ever split this rides with the `Update` half only. It also owns the two
  `/* collections */` and `/* siblings */` positional labels in `updateWithSiblings`, which
  shortening the constructor removes. Per-site numbers as last measured:
  [history](2026-08-22-backend-cleanup-history.md#collectionrequestsupdate-per-site-refs-moved-2026-09-01).

- [x] `model/DiskUploadRequest.java` -- 3-arg `FileEntry` constructor. **DONE** ([#267](https://github.com/themancalledzac/edens.zac.backend/pull/267),
  2026-08-31). Every number re-derived on the day and every one reproduced; the arity-scanner
  method works and is worth keeping for the remaining two. Body:
  [history](2026-08-22-backend-cleanup-history.md#fileentry-3-arg-constructor-tracker-body-moved-2026-09-01).
- [x] `model/AuthPrincipal.java` -- 4-arg constructor. **DECIDED 2026-08-24: leave it.** It is not main-dead (`SessionService` calls it), so it never belonged under the old heading. All **36** call sites are one-liners (35 test plus one in `SessionService` -- **find it by name; the ref has drifted twice and the carried-forward copy of this same fact was corrected to `:181` while this one was left at `:179`**); deleting a 3-line convenience constructor to append `, null` at 35 clean sites is not an improvement. Closing this rather than carrying the hedge a third time.
- [x] `services/ContentService.java` — `resolveCollectionDownloadEntries` 2-arg overload. **DONE**
  ([#271](https://github.com/themancalledzac/edens.zac.backend/pull/271), 2026-08-31). Body:
  [history](2026-08-22-backend-cleanup-history.md#resolvecollectiondownloadentries-overload-tracker-body-moved-2026-09-01).
- [ ] `model/DownloadResolution.java` -- the `extension` component. **PRIORITY FLAG: this is the most expensive of the four, not the cheapest, and its "0 main / 6 test" headline reads like a free delete.** Deleting the accessor means deleting the record component, which takes the canonical constructor from 4 args to 3, so every construction site changes: **13 edits across 5 files, 2 of them in `src/main`** (both in `ContentService`, one file) -- 6 accessor sites and 7 construction sites. **All 13 reproduced exactly 2026-09-01, the only near-term item on the board with zero ref drift**, so it can be picked up with no re-derivation pass. **UNPARKED 2026-09-01**: the coverage guardrail did not survive reading the tests -- every accessor assertion it named has a `.contentType()` assertion on the adjacent line. The component carries no main-side behavior: `DownloadUrlService` consumes `List<DownloadResolution>` and never reads `extension`, and there are zero `.extension()` calls in `src/main`. **If MR 25 needs splitting, split this off.** Prior text: [history](2026-08-22-backend-cleanup-history.md#downloadresolutionextension-row-prior-text-moved-2026-09-01).
  and 6 assertions in test. **"Written, never read" is misleading and the phrasing invites a
  mistake.** The record *component* is never read in main, true -- but the local `extension`
  variable in `ContentService` is load-bearing: it feeds `sanitizeFilename` and decides the download
  filename's extension. Removing the component is a 2-line change and does **not** let you delete
  the extension logic. Worse on the test side: **UNPARKED 2026-09-01 (tenth-run review): the guardrail was stale.** The row claimed the four ZIP
  `.extension()` assertions are the only coverage of the original-to-web format fallback and must be
  rewritten before the component can go. **They are not.** Each of the four sits beside a
  `.contentType()` assertion proving the same branch -- `ContentServiceDownloadTest` 201/202, 217/218,
  237/238, 239/240 -- and in `ContentService` `extension` and `contentType` are assigned on
  consecutive lines inside the same branch in both `resolveImageDownload` (`767-768`, `771-772`) and
  the `resolveCollectionDownloadEntries` loop (`814-815`, `820-821`), with `filename` derived from
  `extension` via `sanitizeFilename`. The fallback is covered twice over after the component goes.
  **Delete the six accessor assertions; nothing needs writing back.** Adding one `.filename()`
  assertion per ZIP test is a reasonable belt-and-braces, not a prerequisite.

  **All 13 refs re-verified exact 2026-09-01 on `main` at `43c6f2c6` -- the only near-term item on
  this board with zero ref drift.** Accessors `ContentServiceDownloadTest` 88, 102, 201, 217, 237,
  239; constructions `ContentService` 781, 835, `ContentDownloadAuthTest:94`,
  `ContentDownloadControllerProdTest` 71, 75, `DownloadUrlServiceTest` 100, 101. Also holding: zero
  `.extension()` anywhere in `src/main`, and `DownloadUrlService` consumes
  `List<DownloadResolution>` at 83, 105, 108 without reading it. **Do not re-derive this list again
  until something edits `ContentService` or `ContentServiceDownloadTest`. This is now the most ready
  item in MR 25** and it takes the section from two open members to one. The stale docblock claim holds -- downloads are presigned
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
- [x] **#27 (coverage gap) -- constraint annotations on controller parameters were untested
  repo-wide** -- **CLOSED 2026-09-01** ([#297](https://github.com/themancalledzac/edens.zac.backend/pull/297), ninth run). The audit emptied the
  item: the true population was one method, not a repo-wide gap. Taught rule 52.
  [Write-up](2026-08-22-backend-cleanup-history.md#27-outcome-2026-09-01--the-audit-emptied-the-item-297).
  Body: [history](2026-08-22-backend-cleanup-history.md#27-tracker-body-moved-2026-09-01).
*(**#29 moved to [MR 22](#mr-22--remaining-convention-sweeps) 2026-09-01.** It came out of #27's audit and was filed beside it, but deleting a dead class annotation is a two-line convention cleanup, not a
coverage gap. The heading here says "Coverage gaps" and #29 was never one.)*
- [ ] **MR 11's headline security fix is untested.** Moving **five** throw sites to bare
  `RuntimeException` -- 3 in `JdbcUserCredentialRepository` and 2 in `WebAuthnService` -- has zero
  coverage. **Re-derived 2026-09-01: five, not the eight this row claimed, and
  `JdbcUserCredentialRepository` lives in `config/`, not `dao/`.** There is no
  `JdbcUserCredentialRepositoryTest`, and `WebAuthnServiceTest` never touches those messages.
  **The protection is the exception type, not the message text** -- the ids are still in the
  messages and still reach the log. **Test the status and the body, not the message.** The
  regression to catch is anyone re-typing these as `IllegalArgumentException` or
  `IllegalStateException`, both of which map to 4xx with the message on the wire (rule 3).

Verified good, for the record: `AdminUserControllerTest` is real behavior testing; the auth-table truncation fix landed in `AbstractPostgresIntegrationTest`; no tests mock the deleted `collection.type` shape.

---

# Decisions needed from the user

Returned to the tracker 2026-08-29: the #236 re-split (`32d2168`) had moved this section into the
history file, breaking the Progress links and the history file's "nothing here is open" rule.

**Two rows are open and neither is a question for the user.** Both sit under
[Parked by decision](#parked-by-decision--waiting-on-nobody) at the end of this section, so this
section's open count cannot be read as a queue of things waiting on you.

*(Three decisions -- `enforce-authz`, `parseImageDate`, bare-array responses -- were answered and
shipped 2026-08-30 in [#243](https://github.com/themancalledzac/edens.zac.backend/pull/243). Answers and reasoning:
[history](2026-08-22-backend-cleanup-history.md#decisions-answered-2026-08-30-moved-from-the-tracker).)*

**Closed decisions, one ledger line each.** Bodies, options and reasoning are in
[history](2026-08-22-backend-cleanup-history.md#closed-decisions-bodies-moved-2026-09-01).

- [x] **Passkey revocation** -- admin endpoints only, shipped 2026-08-31 ([#257](https://github.com/themancalledzac/edens.zac.backend/pull/257)).
- [x] **SpotBugs** -- delete all four artifacts. Done in MR 2.
- [x] **`admin_home_tile.cover_image_id`** -- drop it. Shipped 2026-08-31 as
  `V59__drop_admin_home_tile_cover_image_id.sql`.
- [x] **Whether to ship a default DB password** -- drop the default. `spring.datasource.password`
  is `${POSTGRES_PASSWORD}` with no fallback, and `.env.example` marks the variable required.
- [x] **`role.kind`** -- keep it, documented as provenance. Shipped as `V60__comment_role_kind.sql`.
- [x] **Unknown-JSON-key policy (C8)** -- ignore unknown keys. Already Boot's default and already
  pinned by `CollectionTypeAbsentFromWireTest`; flipping it would be a breaking wire change.
- [x] **#28 -- prod's 30 or admin's 50 for image search?** -- **50**, answered at the top of the
  session 2026-09-01 and shipped the same day ([#294](https://github.com/themancalledzac/edens.zac.backend/pull/294)). It widened the public default
  30 -> 50, which is the page-size debt now filed in the cross-repo section.
- [x] **BE-2 -- should the location endpoint keep serving an `images` array?** -- **drop the
  array**, answered 2026-09-01. Closes FE-1 as won't-do; the removal is a COLD item under MR 19,
  and GIFs on location pages are filed separately as a `searchImages` widening.

### Parked by decision — waiting on nobody

*(Subsection added 2026-09-01, tenth-run review. Both rows below sat in the flat list above, which
made this section read as a queue of live questions for the user when neither of them is one. Neither
waits on anybody; both are open only because nobody has decided to do the work.)*


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

- [ ] Partial indexes on `is_blog`/`is_client` (C7, "if scale demands"). **PARKED by decision, not a
  question for the user -- moved into this subsection 2026-09-01.** The row's own text calls it an
  explicit "not until scale demands it" and gives the measurement recipe; nothing and nobody is
  waiting on an answer, and filing it as a decision needed from the user inflated that count.
  **Re-verified 2026-09-01: no `CREATE INDEX` in any migration mentions `is_blog` or `is_client`.**
  **The item names the wrong measurement.** "Check request metrics" points at the `request_metric` table, which is readable
  (via `GET /api/admin/metrics/requests` or one SELECT) but counts HTTP requests per route per day
  and says nothing about whether an index helps -- and V44's own header admits those counts
  undercount because of ISR and CloudFront caching. What decides this is table size and selectivity:
  `SELECT count(*), count(*) FILTER (WHERE is_blog), count(*) FILTER (WHERE is_client) FROM
  collection;` plus `EXPLAIN ANALYZE` on the six `CollectionRepository` queries that filter on those
  flags. Confirmed: **no index on either column exists** -- V50 adds them as plain `BOOLEAN NOT NULL
  DEFAULT FALSE` with only a CHECK constraint.

# Stale side branches

Returned to the tracker 2026-08-29 alongside "Decisions needed" -- it carries an open worklist.

**Re-run 2026-09-01 (tenth run), and three things about it were wrong.** **Ten worktrees, not six**
-- `git worktree list` returns eleven rows, the main checkout plus five under
`edens.zac.backend.worktrees/` and five under `.claude/worktrees/`; four were created after
2026-08-24 for work that has since merged and none reached this board. **Zero open PRs**
(`gh pr list --state open` returns nothing). **The recorded measuring command fails on half its
rows**: four of the eight branches here have no `origin` ref, so
`git rev-list --left-right --count origin/main...origin/<branch>` errors out on them. Measure
against local refs instead:

```
git rev-list --left-right --count main...<branch>
```

At `43c6f2c6` (behind / ahead): `feat/collection-debloat` **221 / 0**, `claude/auth-password-reset`
**142 / 0**, `claude/one-way-collection-associations` **142 / 0**, `0359-fe-ma1-collection-patch`
**49 / 1**, `0257-backend-security-bugs` **133 / 1**, `0217-user-upgrade-be` **288 / 1**,
`chore/log-review-followups` **251 / 1**, `fix/s18-actuator-exclude` **65 / 3**. **Every "0 ahead"
verdict holds**; the behind-counts are not worth restamping. Worktree inventory:
[history](2026-08-22-backend-cleanup-history.md#stale-side-branches-tenth-run-re-run-long-form-moved-2026-09-01).

- [ ] **Four merged-work worktrees to remove, all clean, none previously on this list.**
  `0392-sd7-people` ([#293](https://github.com/themancalledzac/edens.zac.backend/pull/293)), `pr281`
  ([#281](https://github.com/themancalledzac/edens.zac.backend/pull/281)),
  `agent-a6284b71d0e38254c` ([#285](https://github.com/themancalledzac/edens.zac.backend/pull/285))
  and `agent-af43dc86deca4305f` ([#284](https://github.com/themancalledzac/edens.zac.backend/pull/284)).
  All four are 1-2 ahead by squash artefact only. `git worktree remove` each. Per the user's standing
  worktree rule these are theirs to remove, so this is a recommendation, not a cleanup to perform
  unasked.
- [ ] **Two dirty worktrees on zero-commit branches -- look before removing.**
  `.claude/worktrees/auth-password-reset` has 2 modified files and
  `.claude/worktrees/one-way-siblings` has 4, both 142 behind `main`, both on branches with **zero
  unique commits and no remote branch**. Their only content is uncommitted working-tree changes in
  directories nobody has opened in over a week -- unreviewed work with no commit and no PR. **The
  board described both as "worktrees holding no work", which is true of the commits and false of the
  working tree.** Read them or discard them deliberately; do not `worktree remove` blind. Note
  `claude/auth-password-reset` is still **not** a reason to unpark the gallery-password decision.
- [x] **Settled and safe to delete, kept for the traps they document:** `feat/collection-debloat`
  (0 ahead), `0359-fe-ma1-collection-patch` ([#252](https://github.com/themancalledzac/edens.zac.backend/pull/252)
  merged 2026-08-31, and item #22's text is folded into the tracker so nothing depends on the
  branch -- it still reports 1 ahead because #252 was squash-merged, and **the 0-ahead test does not
  apply to any squash-merged branch on this board**), plus the two below.
- [x] **`0257-backend-security-bugs` is fully superseded -- verified, safe to delete.** Its single
  commit is a parallel implementation of MR 5, which shipped as [#165](https://github.com/themancalledzac/edens.zac.backend/pull/165).
  **And it is where the S-4 gap came from** -- its `ProdSecretGuardTest` additions are the
  reflective tests mutation later proved cannot fail. **Do not "rescue" it into an MR.** Detail and
  the one nuance worth carrying into the S-3 fix:
  [history](2026-08-22-backend-cleanup-history.md#0257-backend-security-bugs-verification-moved-2026-09-01).
- [ ] **Two July "wip" snapshots, both local-only.** **No remote branch, so `origin/...` commands
  fail on both -- measure against local refs.** `0217-user-upgrade-be` (1 commit,
  2026-07-28, `AdminUserController` + `UserRequests` + a 136-line `UserUpgradeIntegrationTest`) and
  `chore/log-review-followups` (1 commit, 2026-07-28, `AdminController`/`TagRepository`/
  `CollectionService`/`ContentService`). Both predate Waves 1-5, which rewrote every file they touch.
  Decide per branch: salvage the test, or delete. `0217`'s integration test is the only part likely
  to still be worth anything.
- [x] **`fix/s18-actuator-exclude` -- SETTLED 2026-08-30, safe to delete; it holds nothing unique.**
  R-1 landed its two stranded commits via [#238](https://github.com/themancalledzac/edens.zac.backend/pull/238) and `44a9d81`'s content was already
  on `main` via #232's squash. Verified by content, not by commit count. **Trap worth keeping:**
  `git log origin/main..origin/fix/s18-actuator-exclude --oneline` still returns **3** and always
  will, because a squash merge never makes a branch commit an ancestor of `main`. Diff the content
  of the files it touched instead. Superseded original text:
  [history](2026-08-22-backend-cleanup-history.md#fixs18-actuator-exclude-original-text-moved-2026-09-01).

# Appendix C — Unverified leads

Worth a targeted check; not asserted as findings.

**Four leads are resolved and struck.** Bodies in
[history](2026-08-22-backend-cleanup-history.md#appendix-c-resolved-leads-moved-2026-09-01).

- [x] Possibly-dead endpoints -- **all three confirmed ALIVE 2026-08-24. Do not delete any.**
- [x] `role.kind` -- **premise disproved 2026-08-24**; V45 also writes `'PERSONAL'`.
- [x] `PersonRepository.findAccountUserIdsByIds` -- **resolved in MR 15 #6**, deleted and the rule
  enforced at `RoleRepository.addMember` instead.
- [x] `collection.rows_wide` -- **premise FALSE**; the frontend reads it as the row-packer chunk
  size, so dropping the column changes public rendering.

- [ ] `deleteImages`/`deleteGif` delete from S3 before the DB write inside the transaction:
  `ContentService.deleteImageFromS3` runs before `deleteImageById`, and `deleteGifFromS3` before
  `deleteGifById`. **Find them by name -- the recorded `354-356` and `518-519` had both drifted +4
  (they are at 358/360 and 522/523 on `main` at `43c6f2c6`) and every one of these four method names
  greps uniquely.** A DB failure orphans the row's URLs; consider afterCommit S3 deletes.
- [ ] **The image-upload job-status endpoint may be entirely dead.** *(New lead 2026-08-24.)*
  `POST /content/images/{id}/from-disk` returns 202 with a `jobId` "for polling" and
  `GET /api/admin/content/images/jobs/{jobId}` serves the status. **The frontend half is confirmed
  against a live clone (2026-09-01): neither has a caller anywhere in `edens.zac` `origin/main`.**
  What remains unanswered is how disk import is actually triggered -- the kind of "nobody calls it"
  claim that is wrong when a human uses curl. If it is admin-CLI-only, the whole `JobTrackingService`
  surface plus **39** test references across three files (`ImageUploadPipelineServiceTest` 28,
  `AdminControllerTest` 6, `JobTrackingServiceTest` 5) may be dead weight; the recorded "~45 across
  two files" was wrong on both halves.
- [ ] `updateImages` and duplicate image ids in one request. **PREMISE CORRECTED 2026-09-01: the
  recorded finding cannot happen, and the real one is different.** `Collectors.toMap` at
  `ContentService:151` cannot throw on a duplicate key, because `:148` fetches through
  `... WHERE c.id IN (:ids)` (`ContentRepository:290`), which returns one row per distinct id.
  **What actually happens is that both updates apply, in request order, last write winning,
  silently.** That may be worth filing; it is a different finding. **Re-scope it or strike it -- do
  not schedule it as recorded.** It belongs with bug #17 (same method) whenever that MR happens.
  Derivation:
  [history](2026-08-22-backend-cleanup-history.md#appendix-c-duplicate-image-ids-derivation-moved-2026-09-01).
- [ ] `updateImages` reports per-item errors inside one transaction. Confirm a mid-item `DataAccessException` cannot leave an item half-applied (needs a test).
- [ ] `contentDisposition` (`DownloadUrlService.contentDisposition`, at **`127`**, called from `:67`)
  does not escape quotes in filenames. Depends on what `sanitizeFilename` strips.
- [ ] `TagService.convertTagToCollection` briefly persists under a temp slug, visible to a concurrent reader, and may burn a `-1` suffix.
- [ ] `WebAuthnController.registerStart`/`loginStart` declare `throws Exception`; serialization failures become generic 500s.
- [ ] ID-list DAO fetches have no ORDER BY. Spot-checked callers re-order, but not all 7+ call sites were traced.
*(A `CollectionServiceTest` "read it line by line" lead was DROPPED 2026-09-01 under working rule
5 -- its ranges named nothing after [#289](https://github.com/themancalledzac/edens.zac.backend/pull/289)
rewrote the file, and anything still wanted from it is covered by the assert/verify twins item
under MR 25. Detail:
[history](2026-08-22-backend-cleanup-history.md#appendix-c-collectionservicetest-lead-drop-note-moved-2026-09-01).)*

*(**Appendix D deleted 2026-09-01, tenth-run review.** It held one row, `ml_image_tagging`, a design
doc with 0% implemented and no stubs in `src/` -- re-verified, `grep -rn "ml_image_tagging" src/`
returns nothing. It tracked no work, no decision and no decay while holding an open checkbox on a
board whose open count is a tracked metric. In one sentence instead: the largest unstarted feature
is still `ml_image_tagging`, and it costs nothing until someone starts it.)*

---

## Next run (set 2026-09-01, tenth close-out)

**The full-board review was the whole run**: eight read-only slices, one apply agent, one docs MR,
zero code changes. **The run after this one, in order. This supersedes the ninth close-out's list.**

1. **#29 -- delete `ContentControllerProd`'s `@Validated` and its import.** Three lines, every premise
   verified twice this review, zero test churn. Fix `GlobalExceptionHandler:142`'s docblock in the
   same change -- it names a source that will no longer exist. *Guardrail:* it is the only
   `@Validated` in the repo, so there are no siblings to sweep, and **do not delete the handler** --
   `GlobalExceptionHandlerTest:74` throws the exception directly.
2. **The two MR 14 stale docblocks.** Two lines each, zero test coupling, four exact refs, carried
   since MR 14. Riding them with #29 as one docs MR is reasonable. *Guardrail:* the "PARENT-shaped"
   row now lists seven docblock uses, and two of them are the deliberate "do not key on
   `type == PARENT`" warning -- do not sweep those.
3. **MR 19 #17, members (a) and (d) only** -- `UserInviteService.findLiveInvite` and the shared S3 put
   in `ImageProcessingService`. Both are private-helper extractions with **zero** references in
   `src/test`, so nothing in the suite is forced to change. Combined net ~-14 lines. *Guardrail:*
   write the 30-versus-10 `PaginationUtil` trap into member (b) as you go and leave (b), (c) and (e)
   on the board.
4. **MR 25's `DownloadResolution.extension`.** Unparked this review -- its coverage guardrail did not
   survive reading the tests. All 13 refs reproduce exactly, the only near-term item on the board with
   zero ref drift, so it can be picked up with no re-derivation pass. Takes MR 25 from two open members
   to one.
5. **#22 -- `PATCH /api/edit/collections/{id}`.** Not cleanup, which is why it keeps sliding, but it is
   the highest-consequence open item on either board. **Its premise changed this review**: the existing
   `PUT` routes already behave as partial updates, field by field. *Ask the frontend board whether
   pointing `buildFieldPatch` at that `PUT` unblocks MA1 before scheduling any backend work.* Either
   schedule it or say plainly that the frontend stays blocked.

**Three questions to ask at the top of the session, all cheap.**

1. **What collation does the production Postgres use?** It is the only thing standing between MR 18
   #13 closing entirely and being a ~10-line MR. Nothing in the repo pins it.
2. **How severe is S-29?** MED as filed, HIGH if any client gallery holds images not published
   elsewhere, closer to LOW if every image is public anyway. It is a data question this repo cannot
   answer, and it decides how the finding gets scheduled.
3. **Does the frontend want 50 images on `/location/[slug]` and `/tag/[slug]`?** That is the #294
   page-size debt. Either answer is fine; the debt is that nobody was told.

**Not in this run, and why.** **MR 18 #13** is BLOCKED on question 1. **MR 25's
`CollectionRequests.Update`** is BLOCKED (ordering) on the `Update` half of the `TestFixtures` pass --
taking it alone rewrites the same 22 sites twice. **MR 16 #3** was closed as decided this review.
**U-1** is blocked on host access and deliberately stays off `Next:`; **U-7 and U-8** are behind it.
**The `coverImage` row** and **`V54FoldMigrationIntegrationTest`** wait on judgements. **MR 18 #10**
has been COLD and unworked for four close-outs without appearing under any run's `Next:`, which makes
it invisible to the leak detector -- it needs working or a reason, and it is named here so it stops
sliding silently.

**Carried forward.** MR 19 #17 is scheduled above and MR 18 #13 is now genuinely blocked, so
neither is being avoided. The leak detector's reading of them is corrected in the session log.

### Classification of the open board (stamped 2026-09-01, tenth close-out)

**The open-checkbox count is deliberately not written here.** This close-out is a docs MR on a
branch and the count changes with its own edits; **rule 42 says re-run it on `main` after the
merge**. The parent session runs
`grep -c '^- \[ \] ' ai_docs/reviews/2026-08-22-backend-cleanup-spike.md` on `main` after the merge
and writes the number here. For reference, `main` at `43c6f2c6` held **69**.

**This is the first classification to cover the whole open board.** Every prior version covered
about 25 items against a board of 69.

- **COLD -- pick up with no unanswered question:** #29; the two MR 14 stale docblocks; MR 18 #10;
  MR 19 #17 members (a), (b), (c), (e); MR 25's `DownloadResolution.extension` (unparked), its
  `TestFixtures` / `ContentModels.Image` pass, its four typeless-migration ITs and the
  `ImageUploadPipelineServiceTest` verify ratio; U-2; MR 21's whole Map inventory; eight of MR 22's
  nine rows; MR 23's three package moves; six of MR 24's rows; MR 26's eight coverage gaps; the two
  new cross-repo backend items; the `RoleRepository.canView`/`isClient` deletion; Appendix C's leads.
- **BLOCKED (ordering):** MR 25's `CollectionRequests.Update`, on the `Update` half of the
  `TestFixtures` pass -- **re-classified from COLD this review**. MR 18 #13's sort split, on reading
  the production collation. U-7 and U-8, both on U-1.
- **BLOCKED (user):** U-1 (host access); U-3 (the `ACCESS_TOKEN_SECRET` rotation story); the
  `coverImage` stripping row (a judgement, premise re-verified mechanically);
  `V54FoldMigrationIntegrationTest` (join the consolidation or stay exempt); MR 22's try-catch row
  (an untested behavior change on two uncovered admin endpoints); S-29's severity.
- **BLOCKED (other repo):** FE-2 through FE-5 and the #294 page-size debt. **They wait on the
  frontend acting, not on someone filing them** -- [#371](https://github.com/themancalledzac/edens.zac/pull/371)
  filed all five and merged 2026-08-31, verified live this review.
- **PARKED by decision:** gallery passwords and C7's partial indexes, both under their own
  subheading in the Decisions section. **MR 16 #3 joined them as closed-by-decision** this review.

The board arithmetic behind the post-merge count, and the DONE-since-last-list roll-up, are in
[history](2026-08-22-backend-cleanup-history.md#classification-of-the-open-board-arithmetic-moved-2026-09-01).

## Full-board review — RUN 2026-09-01 (tenth run)

**It ran, and it was the whole run.** Eight read-only agents, one apply agent, one docs MR, zero
code changes. Slices: recorded numbers; code references; near-term premises; the far set; security;
board self-consistency; the cross-repo pair; estimates and classification.

**What it found, one line each.**

- **Three summary cells recorded a gate command that can never match anything.** Lines 37, 49 and
  50 carried `grep -c '^- [ ] \*\*S-'` with the brackets unescaped, so it returns 0 on any input.
- **Rule 36's own stamp read 1 and the value was 0** -- the rule against rotting numbers, rotted.
- **Thirty-eight recorded numbers were wrong or stale against 63 correct**, and eleven more have no
  command behind them at all.
- **Three new security findings, all on the anonymous read surface**: S-29 (MED, possibly HIGH),
  S-30 (LOW), S-31 (LOW). Bodies in the security section.
- **S-25 never existed.** The ledger runs S-1..S-24, S-26, S-27, S-28; the closed count is 27.
- **113 code references checked, 74 exact and 39 drifted.** Every item last re-derived by name came
  back clean; the one item recording per-site line numbers came back 0 of 9.
- **The frontend clone exists** at `~/Code/edens.zac`, `origin/main` at `f4e8e25`. The board said
  otherwise in two places, and every cross-repo claim is now checked live.
- **Seven premises did not survive checking**, including item #22's "a PUT will not do" and MR 18
  #13's two SQL-ordered producers.
- **The board contradicted itself in eight places**, all fixed.

**The structural lesson.** The third run taught that a summary must be measurable by the command it
quotes. This one is narrower and worse: **a gate command is itself a recorded value, and it rots
without ever having been right.** Lines 37, 49 and 50 did not drift -- they were wrong the day they
were written, and their output happened to match the truth. **A gate whose output never changes is
not evidence that nothing changed.** When you write a gate, prove it can return non-zero:
`printf -- '- [ ] **S-99 x\n' | grep -c '^- \[ \] \*\*S-'` must return 1.

**The second lesson, from the ref audit.** Re-deriving by name comes back clean; re-deriving by
number comes back wrong within a run. **Stop writing per-site line numbers for files that churn.**

Agent-by-agent detail, the merged "checked and clean -- do not redo" list, the two places where
slices disagreed, and the full findings prose are in
[history](2026-08-22-backend-cleanup-history.md#full-board-review--run-2026-09-01-tenth-run).


## Full-board review — RUN 2026-08-31 (third run)

**It ran.** Five read-only agents, one apply agent, one docs MR, zero code changes. It found the
first fully clean count audit the board had had, refilled the security section (S-26 HIGH, S-27 and
S-28 LOW), confirmed both unchecked MR 25 arity counts, corrected the cross-repo GIF row's premise,
re-priced six MR 16-19 items, and found the board lying about itself in five places.

**The structural lesson:** a summary must be measurable by the command it quotes, or it goes wrong
the moment something is filed in a shape the command cannot see. Two gates came out of it.

Summary prose and agent-by-agent detail:
[history](2026-08-22-backend-cleanup-history.md#full-board-review--run-2026-08-31-third-run).

## Session log

One line per session -- honoured in spirit, not in width; a review pass gets a paragraph.

**Leak detector.** Three entries in a row ending `Next: X` means X is being avoided -- say so and
either make it real work or drop it. **Checked 2026-09-01 (tenth run): TRIPPED on U-1**, which
needs host access nobody has had on three runs, so U-1 stops appearing under `Next:` until the user
says they have it. Two corrections to the detector, both recorded this run: MR 18 #13's split and
MR 19 #17 appeared in three of the last four `Next:` lines but not three consecutive, and **an item
that stops appearing under `Next:` looks resolved to the detector** -- MR 18 #10 sat COLD through
four close-outs that way. Reasoning:
[history](2026-08-22-backend-cleanup-history.md#session-log-leak-detector-reading-moved-2026-09-01).

**Retention rule (stated 2026-08-29).** The current session's entries stay here; every close-out
moves the rest to the history file's log archive in the same pass. A close-out MR that grows this
log without moving the older entries is the lapse signal. The archive has two halves, both in the
history file: the [pre-split log](2026-08-22-backend-cleanup-history.md#session-log) (from 2026-08-22) and the
[newer archive](2026-08-22-backend-cleanup-history.md#session-log-archive--entries-moved-2026-08-31) (2026-08-30 onward). **Link both.**

### 2026-09-01 -- tenth run. The full-board review, three security findings, and two gate commands that never worked

**The full-board review ran and it was the whole run.** Eight read-only slices, one apply agent,
one docs MR, **zero code changes**. Three slices proposed code fixes; all three became board rows.

**The headline is not a stale number. It is three gate commands that were never right.** Lines 37,
49 and 50 recorded `grep -c '^- [ ] \*\*S-'`, whose unescaped `[ ]` is a bracket expression matching
one space, so it returns 0 against any input. Introduced by the sixth close-out, it survived the
seventh, eighth and ninth. All three cells now carry the escaped form. **Rule 36's own stamp read
`1` while the value was `0`**, its second rot.

**Three security findings, all on the anonymous public read surface, which no prior pass had
attacked.** S-29 (MED, possibly HIGH), S-30 (LOW), S-31 (LOW); bodies and evidence in the security
section. **S-25 never existed** -- the ledger is 27 lines, which is the gap behind the "28
findings" three cells disagreed about.

**Thirty-eight recorded numbers were wrong or stale against 63 correct**, eleven more have no
command behind them, and **113 code references came back 74 exact / 39 drifted** -- clean wherever
the last re-derivation was by name, 0 of 9 where it recorded per-site line numbers.

**Seven premises did not survive checking**, and **the frontend clone is on this machine** at
`origin/main` `f4e8e25` where the board said twice that it was not. **BE-2 was answered: drop the
array**, which closes FE-1 by closing it. One cross-repo debt closed (MR 19 #19) and one filed
(#294's 30 -> 50 page size).

**The structural lesson:** a gate command is itself a recorded value, and it can rot without ever
having been right. **A gate whose output never changes is not evidence that nothing changed.**

**Filed:** S-29, S-30, S-31, the `RoleRepository.canView`/`isClient` deletion, the #294 page-size
debt, the location `images` removal, the `searchImages` GIF widening. **Closed:** MR 16 #3 as
decided, BE-2 as answered, FE-1 as won't-do, MR 19 #19's debt, the V19 `cover_image_id` box and the
four-main-dead-members umbrella. **Deleted:** `Synthetic.blogsOnly`, Appendix D,
`AdminHomeService`'s cache row. **Taught working rule 53.** Full entry:
[history](2026-08-22-backend-cleanup-history.md#session-log-tenth-run-entry-full-text-moved-2026-09-01).

**Next:** **#29** and the two MR 14 stale docblocks as one docs MR; **MR 19 #17 members (a) and
(d)**; **MR 25's `DownloadResolution.extension`**, now unparked. **Three questions at the top of the
session:** the production Postgres collation, how severe S-29 actually is, and whether the frontend
wants 50 images on `/location/[slug]` and `/tag/[slug]`.
