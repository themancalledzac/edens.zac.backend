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
| 4 — Comments and docs | MR 12-14 | **mostly complete** — [history](2026-08-22-backend-cleanup-history.md#wave-4--mr-12-and-mr-13-complete) (#177, #178, #180, #181, #183, #184) and MR 14 ([#187](https://github.com/themancalledzac/edens.zac.backend/pull/187)). **Wave 4 removed 500 comments for -1,026 words across seven MRs.** MR 14 taught working rule 12 (superseded by rule 37 **as a comment rule only -- its protected-file list is still live**, and the three counts at the Inline-comments row are re-run every close-out); **two** stale-docblock items still open (was three -- the `filterNonListedChildCollections` docblock closed 2026-08-29 as already rewritten). |
| 5 — Consolidations | MR 15-19 | MR 15 #1, #2, #6 **done** ([#165](https://github.com/themancalledzac/edens.zac.backend/pull/165), [#189](https://github.com/themancalledzac/edens.zac.backend/pull/189), [#191](https://github.com/themancalledzac/edens.zac.backend/pull/191)) and the follow-up closed ([#210](https://github.com/themancalledzac/edens.zac.backend/pull/210)) — **MR 15 is fully done**. MR 19 #16 shipped ([#216](https://github.com/themancalledzac/edens.zac.backend/pull/216)); MR 19 #14 shipped ([#218](https://github.com/themancalledzac/edens.zac.backend/pull/218)) — the first item in seven to need no adjustment at implementation time, which broke the streak the full-board review's case rested on. MR 16 #4 and #5 both shipped ([#261](https://github.com/themancalledzac/edens.zac.backend/pull/261), [#262](https://github.com/themancalledzac/edens.zac.backend/pull/262), 2026-08-31), and **MR 19 #21 shipped the same day** ([#266](https://github.com/themancalledzac/edens.zac.backend/pull/266)) -- the second consecutive item whose prescribed fix needed no adjustment. Prior row text: [history](2026-08-22-backend-cleanup-history.md#board-row-narratives-moved-2026-08-29). |
| 6 — Conventions | MR 20-22 | not started |
| 7 — Structure | MR 23-24 | not started |
| 8 — Tests | MR 25-26 | **MR 25 is half done, MR 26 not started.** Two of MR 25's four positional/arity members shipped 2026-08-31: `FileEntry` ([#267](https://github.com/themancalledzac/edens.zac.backend/pull/267)) and `resolveCollectionDownloadEntries` ([#271](https://github.com/themancalledzac/edens.zac.backend/pull/271)). The two left are the two the guardrails have been parking: `DownloadResolution.extension` (13 edits, 5 files, touches `src/main`, and 4 of its 6 accessor assertions are the only coverage of the collection-ZIP format fallback) and `CollectionRequests.Update` (21 sites, must ride with the `TestFixtures` pass). |

Four sections below are not waves and had no row here until 2026-08-24, which made them invisible
to anyone navigating by this table. **"Decisions needed from the user" was the fourth and was still
missing its row until 2026-08-24's close-out** -- eight open items, invisible to this table, which
is the same failure the paragraph above was written to fix:

| Section | Status |
|---|---|
| [Open security findings](#open-security-findings) | **1 open.** The section refilled 2026-08-31 (third run) with three, and [#265](https://github.com/themancalledzac/edens.zac.backend/pull/265) closed two of them the same day: **S-26, the HIGH** (a deregistered passkey's sessions kept resolving, and their holder could register a replacement from inside one) and **S-27** (a docblock #257 falsified), which rode with it as filed. **S-28 (LOW) is what is left** -- one docblock line naming the redeploy recovery for an admin who deregisters their own last passkey. All three came out of the merged set (#247, #248, #250, #253, #257) attacked as a set; that pass also confirmed **S-16's reachability claim holds**, which is recorded under "Verified sound" so nobody re-derives it. **27 closed**, one ledger line each below; the newest outcomes are in [history](2026-08-22-backend-cleanup-history.md#s-26-outcome-2026-08-31----the-fix-was-one-call-and-three-mutations-were-needed-to-prove-it). Edit gate (rule 36): `grep -c '^- \[ \] \*\*S-'` = **1** — run it and update this row and the estimate cell together. **This gate counts numbered findings only**; the unsettled questions have their own section and their own row, because four of them used to live inside this section where no gate could see them. |
| [Cross-repo findings owed to the frontend](#cross-repo-findings-owed-to-the-frontend--five-open-2026-08-31) | **5 open — re-derived 2026-08-31 (third run) by a full pair scan of both repos.** The GIF row's premise was **wrong and is corrected in place**: the frontend's `/location/[slug]` page never reads `LocationPageResponse.images`, so a location-tagged GIF cannot reach it today at any prop type. Four more were found, all dormant or dev-only. **All five are now filed in `edens.zac`** ([#371](https://github.com/themancalledzac/edens.zac/pull/371), **merged 2026-08-31**, docs-only), which closes the gap the second run declared and could not close: four became new rows (C14, C15, C16, H7) and the fifth was already shipped there as G6 (#351), so it went under that board's "do not re-investigate" list rather than becoming a duplicate. #371 has merged, so these are filed on both boards; the rows stay open here until the frontend acts on them. The same scan found two backend items: an N+1 regression (now **MR 19 #21**) and a serialization question (now a Decisions row). Read the section. |
| [Decisions needed from the user](#decisions-needed-from-the-user) | **3 open, and only ONE is waiting on you.** The three one-word calls were asked in the opening message of the 2026-08-31 third run and all three came back: `cover_image_id` **drop** (V59), the DB-password default **drop the default** (`${POSTGRES_PASSWORD}`), `role.kind` **keep, documented as provenance** (V60). All three shipped together in one MR. What remains: gallery passwords (**parked by decision** pending a design) and the partial-index item C7 (an explicit "not until scale demands it"), neither of which waits on anyone -- plus **one new row added by this run's cross-repo scan**, whether the location endpoint should keep serving an `images` array at all, given the frontend discards it. That one is a real open question. **Batching the three into the opening message is what turned them into a same-session MR** — asked at the end, they would have been the next session's problem (working rule 41's neighbour). Edit gate (rule 36): the count is over the section's own `- [ ] ` lines; re-run it and update this row together. |
| [Tests that cannot fail](2026-08-22-backend-cleanup-history.md#tests-that-cannot-fail--closed-2026-08-30-moved-from-the-tracker) | **0 open of 6 — CLOSED 2026-08-30.** The last three shipped in one session (#239, #240, #241), each mutation-proved against `main` first. Two of the three carried a wrong premise that was corrected while closing: the share-link credential is a `Set-Cookie`, not a response-body token; and the `AdminUserControllerTest` pointer the board suggested names a test that does not redden on that mutation. Write-ups in history. |
| [Rule 37 debt](2026-08-22-backend-cleanup-history.md#rule-37-debt--r-1-closed-2026-08-30-moved-from-the-tracker) | **0 open — R-1 closed 2026-08-30 ([#238](https://github.com/themancalledzac/edens.zac.backend/pull/238)).** Taught working rule 39. The wider per-package sweep is not tracked here; it is the Inline-comments row in the category table below. |
| [Stale side branches](#stale-side-branches) | **New 2026-08-24; branch list re-run 2026-08-31 (third run).** 6 worktrees, unchanged. **"0 open PRs" was wrong and is corrected**: `0359-fe-ma1-collection-patch` carried [#252](https://github.com/themancalledzac/edens.zac.backend/pull/252), which **merged 2026-08-31** after this was written; it had held the only copy of item #22, now folded into the tracker directly. **That branch is therefore safe to delete, making four deletable, not three** -- though it still reports 1 ahead, because #252 was squash-merged (see the section). Three others are genuinely 0 ahead and safe to delete; two hold unique work. `fix/s18-actuator-exclude` holds nothing unique (settled 2026-08-30, see the section). |
| [Unsettled security questions](#unsettled-security-questions) | **7 open — U-4 shipped 2026-08-31 as [#270](https://github.com/themancalledzac/edens.zac.backend/pull/270)** (it was never a question; the fourth run re-classified it as a specified one-block fix filed in the wrong section, and this run shipped it). **Row created 2026-08-31 (third run).** Four of these lived inside "Open security findings" as plain checkboxes, so `grep -c '^- \[ \] \*\*S-'` reported the section empty while it held them; four more existed only as prose inside closed `[x]` ledger lines, with no checkbox at all. They are now one numbered list with its own gate. Edit gate (rule 36): `grep -c '^- \[ \] \*\*U-'` = **7**, re-run 2026-08-31 (fifth close-out) — run it and update this row together. |

Original estimate: roughly 4,500-5,000 lines removed against a few hundred added. The test tree (32.6k lines) is larger than main (27.2k); about 8% of it tests the Java compiler and Lombok.

| Category | Count | Deletable lines (est.) |
|---|---|---|
| Bugs (fix, not delete) | **21** (5 high) — 20 shipped, **1 open** (#18). Bugs #17, #19 and #20 all shipped 2026-08-31 ([#256](https://github.com/themancalledzac/edens.zac.backend/pull/256), [#258](https://github.com/themancalledzac/edens.zac.backend/pull/258), [#255](https://github.com/themancalledzac/edens.zac.backend/pull/255)); #21 earlier the same day ([#249](https://github.com/themancalledzac/edens.zac.backend/pull/249)). Checkbox check: `grep -c '^- \[ \] \*\*Bug #'` = **1**, re-run 2026-08-31 (third close-out, unchanged). **Bug #18 is the only open bug on the board.** Items **#22** and **#23** were filed 2026-08-31 in the same number series but are a feature dependency and a doc bug, not code bugs, so they open with `**#22` / `**#23` and do not move this gate. #23 came out of the fourth run's attempt to settle U-1 by looking, and **shipped 2026-08-31** ([#269](https://github.com/themancalledzac/edens.zac.backend/pull/269)): `ai_ec2.md` had carried a stale second copy of the `.env` template disagreeing with `.env.example` about the Spring profile, and both blocks are gone. **#22 is the only one of the two still open.** | — |
| Security findings | **1 open** (S-28 LOW). The three filed 2026-08-31 by the full-board review are down to one: S-26 (HIGH) and S-27 (LOW) shipped together as [#265](https://github.com/themancalledzac/edens.zac.backend/pull/265) the same day. 27 closed; the seven newest 2026-08-31. Checkbox check: `grep -c '^- \[ \] \*\*S-'` = **1**, re-run 2026-08-31 after #265 (working rule 36: run it and edit this cell and the section-table row together). Numbered findings only — the eight unsettled questions have their own gate. | — |
| Dead code (main) | ~60 methods/fields/files | ~1,000 |
| Inline comments | **Re-measured 2026-08-29.** Old criterion (whole-line `//` at indent >= 4, `src/main`): **73**. Rule-37 criterion (any line whose first non-whitespace is `//`, `src/main` + `src/test`): **1,675** (290 main / 1,385 test), plus **72** trailing `code; //` lines. **Re-run 2026-08-31 (fifth run, post-merge): 1,536 (260 main / 1,276 test)** -- down 97, the largest single-run drop on this board, and **it reconciles exactly** (**rule 42**): main -2 (U-4's two-line slide comment, [#270](https://github.com/themancalledzac/edens.zac.backend/pull/270)); test -98 = 73 (`AdminUserControllerTest`, [#272](https://github.com/themancalledzac/edens.zac.backend/pull/272)) + 16 (`ContentServiceDownloadTest`, [#271](https://github.com/themancalledzac/edens.zac.backend/pull/271)) + 9 (`SessionServiceIntegrationTest`, #270). **Fourth consecutive run that removed some and added none.** **Two corrections to the figures below, and neither was caused by this run.** First: the prior run's recorded **1,371** was wrong; the board's own command at `a9d9e661` returns **1,374**, and has since `41d928b4`, so every absolute in the chain below is 3 low while every recorded delta is right -- see **rule 46**. Second: #271's PR body says 17 comments deleted where this metric moved 16, because one was a trailing `code; //`; both are correct about different things. Prior run, as recorded and 3 low: **1,633 (262 main / 1,371 test)** -- down 4 from 1,637, and the delta reconciles line-for-line against a single file (**working rule 42**): `CollectionServiceTest` lost 4 `// Arrange` / `// Act` / `// Assert` markers in the two tests [#266](https://github.com/themancalledzac/edens.zac.backend/pull/266) rewrote (`git show` on that file: 4 removed, **0 added**). `src/main` did not move. **Third consecutive run where a session removed some and added none.** Prior run: **1,637 (262 main / 1,375 test)** -- down 7 from 1,644 (262/1,382), and that delta also reconciled against a single file: `ReadCacheInvalidatorTest` went **7 -> 0** in [#262](https://github.com/themancalledzac/edens.zac.backend/pull/262), which removed its inline comments and moved the three carrying real reasoning into docblocks. `src/main` did not move at all -- `SesConfig.java` was deleted by [#261](https://github.com/themancalledzac/edens.zac.backend/pull/261) and held zero inline comments. **This is the second consecutive run where a session removed some and none were added.** Prior figure: **1,644 (262 main / 1,382 test)** -- down 31 from 1,675 (290/1,385). The earlier re-run that day was UNCHANGED, which was the first confirmation that a session added none; this one is the first that a session *removed* some. **The delta reconciles exactly against the four MRs' own deletions** (-28 main: 11 in `ImageUploadPipelineService`, 10 in `ContentService`, 7 in `AdminUserController`; -3 test: 1 in `AdminUserControllerTest`, 2 in `WebAuthnCredentialRepositoryIntegrationTest`), which is **working rule 42**. **The trailing figure was wrong and its recorded command did not reproduce it.** As recorded, `grep -vE '^\s*[^:]+:[0-9]+:\s*//'` returns **231**, not 72: BSD `grep -E` does not honour `\s`, so the exclusion under-matches, and nothing excluded URLs -- every `https://` in a javadoc counts as a `//`. **Corrected count: 74**, by this command, which is portable and URL-safe -- **re-run 2026-08-31 (second close-out) and still 74**:
```
grep -rn '//' src/main/java src/test/java | grep -vE '^[^:]+:[0-9]+:[[:space:]]*//' | grep -v 'https\?://' | wc -l
```
Leading form, unchanged and still correct: `grep -rn '^[[:space:]]*//' src/main/java src/test/java | wc -l`. **Both must be run from the repo root against `src/`** -- `.claude/worktrees/` holds whole source trees, and an unscoped `grep -rn` triple-counts (verified 2026-08-31: the scoped commands return 0 worktree hits). The old "~~370~~ 567 measured … is a floor" was the 2026-08-23 wave-4 start under the old criterion and described nothing current — the real rule-37 debt is ~3x it. | ~300 net (also low) |
| ^ **re-scoped 2026-08-28** | Working rule 37 turns this from a debloat nice-to-have into a standing rule: **every** inline comment in `src/main` and `src/test` is now a violation, not just the ones a rule flagged. **Do not sweep this in one MR** — take it per package, and take the files working rule 12 protected first -- **`RoleRepository` (10), `AdminBootstrap` (6, and it is in `services/`, not `config/`), `CollectionControllerProd` (9); all three re-run 2026-08-31 twice and unchanged both times** -- `RoleRepository` held at 10 across #247, which edited it. **`SecurityConfig` is off this list**: #243 swept it from ~27 to **4** as a side effect of removing the authz toggle, so it is nearly done and no longer the priority the row assumed. **One recorded exemption**: the second `coverImage` banner in `CollectionControllerProdTest` stays until its "Carried forward" decision lands. **`AdminUserControllerTest` is DONE** -- it held 73, the single largest concentration found on this board, and [#272](https://github.com/themancalledzac/edens.zac.backend/pull/272) took it to **0** on 2026-08-31 (re-run at the close-out). [#265](https://github.com/themancalledzac/edens.zac.backend/pull/265) had edited that file and deliberately left them, which was the right call and is why it became its own MR. **Its neighbours were explicitly not swept and still hold their own counts** -- `AdminControllerTest` and the other admin tests are each their own MR, unmeasured as of this close-out. (Bug #17's `ContentService` comment is no longer exempt — its board row is the evidence now.) | — |
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
  overload (**DONE** -- [#271](https://github.com/themancalledzac/edens.zac.backend/pull/271), 2026-08-31; all 5 counts reproduced on the day and the arity split held),
  `DownloadResolution.extension` (**0 main / 6 test, CONFIRMED 2026-08-31** -- but see the priority
  flag under MR 25: this is the most expensive of the four, not the cheapest),
  `CollectionRequests.Update`'s 17-arg constructor (**21 test sites, CONFIRMED 2026-08-31**),
  `DiskUploadRequest.FileEntry`'s 3-arg constructor (**DONE** -- [#267](https://github.com/themancalledzac/edens.zac.backend/pull/267), 2026-08-31; 13 sites, re-derived on the day and reproduced exactly). **Two of the four remain** -- `FileEntry` shipped as [#267](https://github.com/themancalledzac/edens.zac.backend/pull/267) and `resolveCollectionDownloadEntries` as [#271](https://github.com/themancalledzac/edens.zac.backend/pull/271) (2026-08-31).
  **All four counts now reproduce. The two UNCHECKED markers are cleared.** The raw greps that could
  not settle them are fully accounted for: `new CollectionRequests.Update(` returns **24** in test =
  21 compat-arity + 3 canonical 22-arg calls (`CollectionProcessingUtilTest:290`, `:491`,
  `CollectionServiceTest:2139` -- **drifted from `:2056` by
  [#266](https://github.com/themancalledzac/edens.zac.backend/pull/266), which added 83 net lines to
  this file (2056 + 83 = 2139), verified with `git show 3c034c94:<file> | sed -n '2056p'`. The
  original ref was correct.** This entry first went in claiming `:2056` had never been right; that
  was wrong and its reasoning is now **working rule 44**); the `FileEntry` equivalent returned
  **28** = 13 three-arg + 15 canonical six-arg, and that member shipped as
  [#267](https://github.com/themancalledzac/edens.zac.backend/pull/267). Both are main-dead: zero `src/main` callers of the 17-arg overload (the one main
  construction is `CollaboratorRequests.java:43`, arity 22) and zero `src/main` constructions of
  `FileEntry` at any arity — it only ever arrives via Jackson, which binds the canonical constructor,
  so deleting the 3-arg one has no wire effect.

  **The method, so the next session gets the same numbers** (single-line grep cannot see a
  google-java-format'ed `new Foo(\n a,\n b)`, which is why this stayed UNCHECKED for a week). Write a
  paren-balanced scanner: from each regex match, walk forward from the opening paren tracking nesting
  depth, skipping string and char literals, text blocks and both comment forms, and count commas at
  depth 1; arity is commas + 1, or 0 for empty parens. Save it as `arity2.py` at the repo root and run
  `python3 arity2.py '<regex>' <root>`:

  ```
  python3 arity2.py 'new\s+CollectionRequests\.Update\s*\(' src/test/java
  python3 arity2.py 'new\s+(DiskUploadRequest\.)?FileEntry\s*\(' src/test/java
  python3 arity2.py 'resolveCollectionDownloadEntries\s*\(' src/test/java
  python3 arity2.py '\.extension\s*\(' src/main/java src/test/java
  ```

  **Root every scan at `src/main/java` and `src/test/java` from the repo root** -- `.claude/worktrees/`
  holds stale copies of these test files and inflates every number. When the regex is a bare method
  name, declarations match alongside calls and must be subtracted by hand. Also listed under MR 25
  below, where the counts and two corrected premises now live.

  **`AuthPrincipal`'s 4-arg constructor was a fifth entry and has been removed from this list**: it
  is **not** main-dead. `SessionService` calls it -- which the old entry admitted two lines below a
  "zero `src/main` callers" heading. Disposition is now a decision, not a deferral: leave it. All **36**
  call sites (re-measured 2026-08-29: 35 test plus `SessionService.java`, **`:181` as of [#270](https://github.com/themancalledzac/edens.zac.backend/pull/270)**, was `:179` -- U-4's docblock addition shifted it; the old 30 was stale)
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
- [x] **`AdminUserControllerTest`'s 73 inline comments** — **DONE** ([#272](https://github.com/themancalledzac/edens.zac.backend/pull/272), 2026-08-31), 73 -> 0, `+118 / -73`, one file. Delete-and-relocate as specified; the substantive comments are now each test's docblock. Taught **rule 46** (the checksum needs its metric named: 17 comments deleted moved the rule-37 line count by 16, because one was a trailing `code; //`). [Write-up](2026-08-22-backend-cleanup-history.md#adminusercontrollertests-73-inline-comments-272).

- [x] **#23 (doc bug)** `ai_ec2.md` carried a stale second copy of the `.env` template — **DONE** ([#269](https://github.com/themancalledzac/edens.zac.backend/pull/269), 2026-08-31). Both env blocks deleted and replaced with a pointer at `.env.example`; `ai_deployment_strategy.md` untouched. **This did not settle U-1** and the PR says so — see U-1, still blocked. [Write-up](2026-08-22-backend-cleanup-history.md#23--the-stale-env-template-in-ai_ec2md-269).

- [ ] **#22 (feature dependency, not a bug) — `PATCH /api/edit/collections/{id}` does not exist,
  and the frontend's largest open item is blocked on it.** *(Filed 2026-08-31 from the frontend
  board's MA1 (`docs/spikes/2026-features.md` in `edens.zac`), whose row said "Task 1 (backend
  `PATCH /collections/{id}`) was assigned to a sibling agent — verify it exists before starting".
  It was verified and it does not. **Folded into the tracker 2026-08-31 (third run) from
  [#252](https://github.com/themancalledzac/edens.zac.backend/pull/252)**, which had been sitting
  open and unmerged since the first run of that day, so this item existed only inside a PR and was
  invisible to anyone reading the board.)*

  Verified against `origin/main` rather than a checkout — this repo's `.claude/worktrees/` copies
  make an unscoped `grep -rn` return convincing false positives for exactly this query:

  ```bash
  git grep -n "PatchMapping(" origin/main -- 'src/main/java/**/controller/**'
  ```

  Five `@PatchMapping`s exist and none is a whole-collection field patch: `/content/images`
  (`AdminController:233`), `/content/gifs/{id}` (`AdminController:341`), `/{id}`
  (`AdminUserController:313`), `/collections/{collectionId}/rating` (`EditController:52`) and
  `/collections/{collectionId}/images` (`EditController:94`). The last two are sub-resource
  patches.

  **What the frontend needs.** MA1 replaces the collection edit sheet's batch-save model with
  per-field optimistic commits, so it needs a partial update accepting an arbitrary subset of
  collection fields — the shape its `buildFieldPatch` derives from the existing
  `buildUpdatePayload`. `PUT`-style whole-object update will not do, because the point is that two
  fields edited in parallel must not clobber each other.

  **Sequencing.** This is MR 1 of MA1; all eleven of its frontend tasks wait on it. Nothing else on
  either board depends on it, so it can land whenever. **COLD.**

## Cross-repo findings owed to the frontend — five open (2026-08-31)

The 2026-08-24 batch closed and lives in
[history](2026-08-22-backend-cleanup-history.md#cross-repo-findings-owed-to-the-frontend). This
section was re-opened by #258 and re-derived 2026-08-31 (third run) by a full pair scan: every
endpoint path literal in `edens.zac`'s `app/lib/api/*.ts` compared against every
`@RequestMapping`/`@*Mapping` pair under `controller/`. **No frontend call site targets a backend
route that no longer exists**, so nothing here is a live 404. The five below are type drift and one
dev-workflow change.

**All five are filed in `edens.zac` as of 2026-08-31 (third run):**
[#371](https://github.com/themancalledzac/edens.zac/pull/371), docs-only, **merged 2026-08-31**. Four are new rows on
that board (C14, C15, C16, H7); the fifth had already shipped there as G6 (#351), so it was recorded
under that board's "do not re-investigate" list instead of being filed twice. **This closes the gap
the second run declared and could not close** -- the row then read "deliberately NOT filed ... so it
is invisible in `edens.zac` until someone files it", because that repo had another session's dirty
branch checked out. It was dirty again this run; the filing was done in a temporary worktree off
`origin/main` without touching that session's tree. **#371 has since merged**, so these rows are
filed on both boards and the gap is closed; they stay open here until the frontend acts on them.

- [ ] **FE-1: the location page's `images` array can now carry GIFs, and the component types it
  `ContentImageModel[]`.** *(Filed 2026-08-31 from [#258](https://github.com/themancalledzac/edens.zac.backend/pull/258); working rule 43's evidence.)*
  `LocationPageResponse.images` widened from `List<ContentModels.Image>` to `List<ContentModel>`, so
  a location-tagged GIF now serializes into that array with `contentType: "GIF"`. Both frontend refs
  confirmed unmoved 2026-08-31: `app/components/LocationPage/LocationPageClient.tsx:29` and
  `app/components/LocationPage/LocationPage.tsx:14` both declare `images: ContentImageModel[]`.

  **PREMISE CORRECTED 2026-08-31 (third run), and the correction matters more than the row.** The
  old text said this was dormant because nothing is location-tagged as a GIF yet — a data claim the
  board could not check. The real reason is structural and checkable: **neither prop is fed by that
  endpoint.** `app/location/[slug]/page.tsx:80-83` fetches two things in parallel.
  `getCollectionsByLocation` hands the location endpoint's body to `parseCollectionArrayResponse`
  (`app/lib/api/collections.ts:55-68`), which returns `data.content ?? data.collections ?? data.items`
  — it takes `.collections` and **discards `.images`, `.location`, `.totalCollections` and
  `.totalImages`**. The `images` prop comes from a second call, `searchImages({ locationId })`, which
  hits `GET /api/read/content/images/search` and returns `ImageSearchResponse`, images only, untouched
  by #258. **So a location-tagged GIF cannot reach the frontend today at any prop type**, and
  widening the props alone changes nothing. Tagging one is reachable from the admin UI
  (`ContentGifModel.locations` exists and the metadata modal accepts a GIF), so the count is zero by
  habit, not by construction — but the discard is what makes this dormant.

  **What would break if a GIF did arrive**: an off-by-one header count, not a crash.
  `LocationPageClient.tsx:55-57` already narrows to `contentType === 'IMAGE'` before rendering, so
  the GIF is dropped from the grid — but the unfiltered array still feeds `count={images.length}`
  (`LocationPage.tsx:42`), `extractFilterOptions`, `computeFilterVisibility` and
  `computeFilterCounts`. "12 photos" over 11 tiles.

  **Fix shape**: widen both props to `ViewableContent[]` (`app/types/Content.ts:443` — exactly
  `ContentImageModel | ContentParallaxImageModel | ContentGifModel`), keep an `isImageContent`
  narrowing for the one `computeFilterVisibility` call (the only image-typed consumer left), and
  delete the `contentType === 'IMAGE'` filter. Leave `coverImage` as `ContentImageModel | null`. The
  precedent to copy is `CollectionPageClient.tsx:317-359`, which holds mixed content and derives an
  image-only list purely to compute filter dimensions. **But this fix only matters once BE-2 in the
  Decisions section is answered** — while the page's images come from `searchImages`, no GIF arrives.
- [ ] **FE-2: `page` and `size` are silently ignored on the location endpoint.** *(Filed 2026-08-31,
  third run.)* `app/lib/api/collections.ts:150` sends `?page=&size=`; `CollectionControllerProd:124-133`
  reads `collectionPage`, `collectionSize`, `imagePage`, `imageSize`, and Spring drops the two unknown
  params. Live but invisible today because both defaults are 35. `getCollectionsByLocation(slug, page,
  size)` therefore accepts two arguments that do nothing, and any caller asking for a second page gets
  page 0. Fix: rename the two query params to `collectionPage` and `collectionSize`.
- [ ] **FE-3: `imageWidth` / `imageHeight` can now be null.** *(Filed 2026-08-31, third run.)*
  [#249](https://github.com/themancalledzac/edens.zac.backend/pull/249) changed the missing-dimension
  default from `0` to `null` (`ImageProcessingService:469-472`). The wire type was already `Integer`,
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

**STILL NOT FILED ON THE FRONTEND BOARD.** The 2026-08-31 second run declined to file because
`edens.zac` had another session's dirty branch checked out; the third run was a backend-only docs MR
by construction and filed nothing either. This is a declared decline both times, not an oversight —
**the next session that touches `edens.zac` must add rows for all five to
`docs/spikes/2026-summer-refactor.md`**. Until it does, five findings are invisible in the repo they
affect, which is the exact failure this section exists to prevent. That is now two consecutive
sessions of declining, which is the point at which a decline stops being a decline.

**Backend routes with no frontend consumer, for the record** — unbuilt features, not drift:
`POST /api/admin/cache/clear`, `GET /api/admin/metrics/requests`, `POST /api/admin/content/images/ingest`,
`POST /api/admin/content/images/{collectionId}/from-disk`, `GET /api/admin/content/images/jobs/{jobId}`,
`GET /api/read/collections/{slug}/download`, `GET /api/read/content/film-metadata`, and FE-4's two.
The `jobs/` and `from-disk` pair is the Appendix C lead about the job-status endpoint being dead;
this scan is independent confirmation of the frontend half of it.

**Item #22 is not listed here.** `PATCH /api/edit/collections/{id}` is backend work the frontend
is blocked on, which is the opposite direction from this section -- these are findings the frontend
must act on. It lives under [Bugs filed after the waves closed](#bugs-filed-after-the-waves-closed-2026-08-29).
**It was duplicated into both sections on 2026-08-31 (third run)** when [#263](https://github.com/themancalledzac/edens.zac.backend/pull/263) folded it in
while [#252](https://github.com/themancalledzac/edens.zac.backend/pull/252) still held its own copy, and both then merged. The copy here was the shorter of the two and
was removed; nothing was lost.

## Open security findings

Consolidated 2026-08-24 by the full-board review; re-attacked as a merged set 2026-08-25, again
2026-08-29 (adversarial -- 0 HIGH, 0 MEDIUM) and again 2026-08-31 (third run, the full-board
review's security slice). **The 2026-08-31 pass refilled the section: 1 HIGH, 0 MEDIUM, 2 LOW.**
It held S-22 (#247), S-23 (#248), S-14/S-24 (#250), S-16 (#253) and the new passkey deregistration
endpoints (#257) together as one set, which is how S-26 was found — it is only a HIGH because #257
removed the compensating control S-15 was measured against. The section was empty for exactly one
session. **Twenty-five closed**: one ledger line each below; bodies, outcomes and
the 2026-08-25 "reopened" context are in the history file
([Security findings -- closed](2026-08-22-backend-cleanup-history.md#security-findings--closed-moved-2026-08-29)).
Per-path limiter mapping context -- which limiter covers which route -- sits in history's
[S-17 outcome](2026-08-22-backend-cleanup-history.md#s-17-outcome-2026-08-28----not-as-specified-and-two-failures-of-the-same-kind).

**The unsettled questions no longer live in this section.** They moved 2026-08-31 to their own
[Unsettled security questions](#unsettled-security-questions) section with their own gate, because
four open checkboxes sat here while this section's row and classification both said "empty" — the
rule-36 gate greps `^- \[ \] \*\*S-` and none of them opened that way.

### Open

- [ ] **S-28 (LOW) an admin deregistering their own last passkey can lock the admin surface out of
  itself.** *(Filed 2026-08-31, third run.)* `AdminUserController.java:453-476` as of #265, was `443-465`. Removing the last
  credential is allowed **by decision** (2026-08-30) and this does not re-litigate that; it records
  the downstream consequence. A sole passkey-only admin who deletes their own last credential loses
  the admin surface, and every recovery route is behind it: issuing a fresh invite is
  `POST /api/admin/users/{id}/invite`, and `POST /api/auth/login` needs a password hash the account
  does not have. **Recovery exists but is narrow.** `AdminBootstrap.init` (`AdminBootstrap.java:50-88`)
  seeds a new ACTIVE admin **only when no `users` row holds the bootstrap email** — it returns early
  for an existing row and, for an existing admin, logs a warning and ignores `ADMIN_BOOTSTRAP_PASSWORD`
  entirely. So recovery means setting `ADMIN_BOOTSTRAP_EMAIL` to a **fresh** address plus
  `ADMIN_BOOTSTRAP_PASSWORD` and redeploying; pointing it at the locked-out admin's own address does
  nothing. LOW because a path exists and the trigger is an admin acting on themselves. Fix shape: a
  line in the endpoint's docblock naming the redeploy recovery — the WARN at
  **`AdminUserController.java:467-470` as of #265** (was `455-459`; **the stale range now lands on the
  404 `ResourceNotFoundException` guard** -- real code, right method, wrong statement) is the only
  signal today and it does not say what to do next.

  **Severity premise falsified by [#265](https://github.com/themancalledzac/edens.zac.backend/pull/265), and it moves the wrong way.**
  S-28 is LOW partly because "a path exists": before #265, an admin who deleted their own last passkey
  kept a working session for up to the 60-day sliding TTL and could register a replacement from inside
  it. S-26's fix revokes that session, so **the lockout is now immediate** and the informal escape
  hatch S-28's LOW was priced against is gone. The recovery path is still the redeploy, and it is
  still narrow. **The fix shape also needs re-aiming**: #265 rewrote exactly the docblock this item
  proposed to add a line to, and did not add the recovery line.

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

### Classification of the still-open items

**One open as of 2026-08-31 (after #265), and it is not blocked on the user.** S-28 (LOW) is COLD --
one docblock line naming the redeploy recovery. S-26 and S-27 shipped together in
[#265](https://github.com/themancalledzac/edens.zac.backend/pull/265), which is the pairing this
paragraph predicted.

*(The claim "the section is EMPTY" stood here and in the Progress row for one session while the
section held four open checkboxes. They were the "Unsettled" questions, which do not open with
`**S-` and so were invisible to the rule-36 gate that both claims quoted. They now have their own
section and their own gate. **The lesson is the gate, not the wording**: a summary claim about a
section must be measurable by the command it cites, or it will drift the moment something is filed
in a shape the command cannot see.)*

The table is kept
rather than deleted because the section has refilled three times now, and its shape is the thing that
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

### Verified sound, do not re-open

Attacked 2026-08-24, again 2026-08-29, and again 2026-08-31 (third run); held every time. Index
only -- the full reasoning lives
in the [history file](2026-08-22-backend-cleanup-history.md#security-findings--closed-moved-2026-08-29):

- **S-16's reachability claim HOLDS -- re-tested 2026-08-31 with #257's endpoints in the tree. Do not
  re-derive this.** #253 shipped one gate instead of the two the item specified, resting on the claim
  that `resolveByRawToken` is the only way a token becomes a link. Four checks, all clean.
  **Producers of `AuthPrincipal.shareId`**: exactly one, `FlybySessionFilter.java:64-74`;
  `AuthPrincipal.flyby` has no other `src/main` caller. **Callers of `resolveByRawToken`**: two,
  unchanged — `FlybySessionFilter:65` and `ShareControllerProd:59`; #257 added none. **Consumers of a
  shareId**: `CollectionAccessService:93-94`, `ShareControllerProd:81`, `UserPageAssembler:86`, all
  taking a shareId the filter or the exchange route already produced through the gate. **The bypass
  looked for and not found**: `ShareLinkService.findById` does turn an id into a link without checking
  owner status, but its only caller is `ShareControllerProd.currentView`, which reads
  `principal.shareId()` — and no route accepts a share-link id as input. The principal cannot be
  replayed either: `SecurityConfig:57-58` is `SessionCreationPolicy.STATELESS` and `FlybySessionFilter`
  writes through `SecurityContextHolder.setContext`, not a `SecurityContextRepository`, so every
  request carrying `ezac_flyby` re-enters `resolveByRawToken` and re-reads `users.status`. And no
  shared cache is a back door: `CacheControlInterceptor.PUBLIC_ROUTES` is default-deny and excludes
  both `/api/read/share/**` routes. **#257 does not touch the share path at all** — no new
  `share_link` reader, no new principal producer, no route taking a share-link id.

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

Edit gate (rule 36): `grep -c '^- \[ \] \*\*U-'` = **7**. Run it and update the section-table row
together.

- [ ] **U-1 -- whether prod actually runs the `prod` profile.** The deployment docs contradict each other:
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

  **Narrowed 2026-08-31 (fourth run), and the framing was wrong.** The contradiction is not two docs
  disagreeing about a deployment. It is **two competing copies of the same `.env` template**, and
  the count is 2-1 for `prod` once you include the operational one:

  | Source | Says | What it is |
  |---|---|---|
  | `.env.example:3` | `prod` | the template the deployed `.env` is built from -- the file `docker-compose.yml` actually reads. Carries the comment "Use `dev` for local development, `prod` on EC2/production" |
  | `ai_docs/ai_deployment_strategy.md:289` | `prod` | prose |
  | `ai_docs/ai_ec2.md:145` and `:228` | `default` | prose, and **both sit inside `.env` example blocks** -- a second, older copy of `.env.example` |

  `docker-compose.yml:21` is `SPRING_PROFILES_ACTIVE: "${SPRING_PROFILES_ACTIVE:?must be set: dev,
  prod, or default}"`, so the value is required and cannot silently default; whatever the host's
  `.env` says is what runs.

  **This splits the item in two, and one half is now COLD work rather than a question.**
  `ai_ec2.md` carrying a stale duplicate of the env template is a doc bug fixable today, on the
  project's own rule that a doc describing something that is no longer true gets its wrong version
  deleted rather than annotated. The right fix is for `ai_ec2.md` to stop carrying a second copy at
  all and point at `.env.example`. **Filed as #23.**

  What stays blocked is only the proof: reading `SPRING_PROFILES_ACTIVE` on the live host, or hitting
  the origin without `X-Internal-Secret` and confirming a 403. **BLOCKED on the user** -- it needs
  host access, and the confirming probe is a request against production that should be run or
  authorized by its owner, not fired off by a close-out.

  **ASKED 2026-08-31 (fifth run), before any code, and the answer was that it cannot be checked right
  now.** So it stays BLOCKED, and **U-7 and U-8 stay blocked behind it** -- the twelve-name actuator
  exclude list at `application.properties:67` was not touched. Its docs half shipped the same day as
  [#269](https://github.com/themancalledzac/edens.zac.backend/pull/269), which deletes the stale
  `.env` copy and explicitly does not claim to settle this. **The question is now on the record as
  put and unanswered rather than unasked**, which is the difference between a blocked item and a
  neglected one. Ask it again at the top of the next run; it is still one command and it still gates
  three items.
- [ ] **U-2 -- whether Tomcat surfaces `Transfer-Encoding` to `getHeader()`.** S-5's entire fix depends on
  it, and its only test uses `MockHttpServletRequest`, which returns whatever the test put in. If
  Tomcat consumes the header while installing the chunked input filter, the branch never fires and
  the bypass is still open. Settle with an integration test that POSTs a real chunked body to a
  booted server and asserts 411 -- `ActuatorExposureEndToEndTest` already has the shape.
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

  **This bullet is also where S-11's missing fact was sitting.** It listed all three uses of the
  secret while S-11's own severity paragraph named only `TokenCipher`. See S-11's outcome in the
  [history file](2026-08-22-backend-cleanup-history.md#s-11-outcome-2026-08-25----the-guard-clause-and-a-fact-the-board-already-had).
- [x] **U-4 -- `SessionService.resolve` slid the session window before reading status** — **DONE** ([#270](https://github.com/themancalledzac/edens.zac.backend/pull/270), 2026-08-31). The slide block moved below `mayHoldSession`; `mayHoldSession` and the absolute-ceiling cap untouched. **The item was right that the move costs nothing and did not price that no existing test could catch the bug** — `resolveRejectsSessionWhoseAccountWasDisabled` asserted `expiresAt` is in the future, which is true under the bug too. One new test, mutation-proved (15 run, 1 failure, at the guard). [Write-up](2026-08-22-backend-cleanup-history.md#u-4--the-slide-moved-below-the-status-check-270).
- [x] **`RoleRepository.canView` and `isClient` have zero `src/main` callers -- confirmed 2026-08-25**, after S-6 routed
  everything through `effectiveLevel`. They are the bug S-6 fixed, still sitting in the DAO under the
  right names and still green in tests. Wave 1 deletion candidates, and the names are the hazard.
- [ ] **U-5 -- `ClientIp`'s javadoc still calls the header's presence "the trust signal".**
  *(Promoted 2026-08-31 out of S-19's closed ledger line, where it had sat as an untracked "live
  debt" since 2026-08-25.)* Still true: `config/ClientIp.java:14` says "so its presence is the trust
  signal", and S-19 settled precisely because presence is *not* the trust signal -- the frontend
  strips and re-derives `x-real-ip`. One docblock sentence, to be corrected when next in the file.
  **Ref re-verified exact 2026-08-31 (fourth run)** -- line 14 still carries that clause. **COLD.**
- [ ] **U-6 -- whether `addCollection` should be admin-gated at all.**
  *(Promoted 2026-08-31 out of S-14's closed ledger line, which said "that is a routing question and
  needs its own item" and then did not file one.)* S-14 closed on a principle -- every admin endpoint
  through the same admin gate -- and `addCollection` is not an admin endpoint: it sits on
  `UserShareControllerProd` at `/api/read/user/share`, and the admin sentinel in `canView` is what
  makes it answer yes for everything. So the principle rejects the ownership test S-14 proposed, and
  leaves the routing question open. Settle by deciding where this endpoint belongs, not by adding a
  second gate.
- [ ] **U-7 -- whether to delete the now-redundant actuator exclude list.**
  *(Promoted 2026-08-31 out of S-23's closed ledger line.)* S-23 shipped the resolved-include boot
  check, which refuses a prod boot on any include wider than `health`. The name-based exclude list it
  replaced was left untouched and is now redundant. Deleting it is a disposition nobody has made.

  **ANSWERED 2026-08-31 (fourth run) by reading `ProdActuatorExposureGuard`, and the answer is
  conditional in a way neither U-7 nor U-8 recorded.** The guard requires the resolved include to
  equal exactly `{health}` and throws from `@PostConstruct` otherwise, so under the `prod` profile
  nothing but `health` can be exposed and every one of the twelve names in the exclude list is
  unreachable by construction. Redundant: **yes**.

  **But the guard is `@Profile("prod")`, and whether prod runs the prod profile is U-1.** On a host
  named `default` the guard does not exist, the include is still `health` from
  `application.properties:66` -- but nothing enforces it, and the exclude at `:67` is then the only
  thing standing between a widened include and an exposed endpoint. So the exclude list is redundant
  *exactly where the guard runs*, which is the thing U-1 cannot prove.

  **U-1 is therefore a hard dependency of U-7 and U-8, and the board had all three filed as
  independent questions.** Do not delete the exclude list until U-1 is settled: deleting it is safe
  under `prod` and removes the last line of defence under `default`. That is the whole disposition,
  and it is now a decision with a stated precondition rather than an open question.
- [ ] **U-8 -- whether S-18's criterion-incompleteness is now moot.**
  *(Promoted 2026-08-31 out of S-18's closed ledger line.)* S-18's exclude list still does not name
  `metrics` or `info`, both of which meet its own stated criterion under `include=*`, and both of its
  tests derive from the same enumeration so neither can see the gap. S-23 was named as the fix shape
  and has shipped. Whether that closes this residual or only hides it behind a boot check has not
  been recorded. Answer it, then close U-7 and U-8 together.

  **ANSWERED 2026-08-31 (fourth run), same reading as U-7 and with the same precondition.** It is
  moot **under `prod`**: `ProdActuatorExposureGuard` refuses any resolved include other than exactly
  `{health}`, so `metrics` and `info` cannot be exposed no matter what the exclude list omits, and
  the criterion-incompleteness has nothing left to bite. The distinction the item asked for --
  "closes the residual" versus "hides it behind a boot check" -- resolves to **closes it**, because
  the guard is fail-closed at startup rather than a filter something can slip past.

  Under `default` it is not moot at all, and `metrics` and `info` remain exactly as unnamed as S-18
  left them. **So U-8 has the same dependency on U-1 as U-7**, for the same reason, and the two do
  close together as the board predicted -- just not before U-1 does.

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
    cell without the other. (Returns **1** as of 2026-08-31, fifth close-out. **The stamp itself
    rotted**: it read "Returns 5 as of 2026-08-29" for two runs after #265 took the real figure to 1,
    while both cells the rule governs were correct the whole time. A rule that carries a measured
    number has to re-run it like anything else -- nothing else was checking this one.)
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
    rule 31's warning appearing inside rule 42's own checksum.

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

- [ ] #3. One keyed rate limiter. **Re-derived 2026-08-31 (third run): FOUR copies, not three.** `config/ShareEmailLimiter.java` (106 lines) landed 2026-08-28 with S-17 ([#233](https://github.com/themancalledzac/edens.zac.backend/pull/233)) carrying the same Caffeine + Bucket4j core -- `Caffeine.newBuilder()` at 67, a private `newBucket(int, Duration)` at 97, **plus a global daily bucket at 71**, which makes it structurally the closest thing on the board to the shared `KeyedRateLimiter(capacity, window, idleTtl)` this item imagines *and* the second class carrying the global bucket that signature has no slot for. Its test adds 3 constructor sites and 27 `.allow(` calls, taking the test bill from ~84 to **~114**. **Every 2026-08-24 number below re-verified exact 2026-08-31**: 82 and 81 source lines, 7+24 and 7+32 test sites, one `estimateAbilityToConsume` site (`RateLimitFilter.java:131`), `AuthLoginLimiter` a 59-line Caffeine counter with no `Bucket`. **The verdict gets stronger, not weaker: still not worth doing.** Prior text: **Re-derived 2026-08-24: three copies, not two** -- `RateLimitFilter.newBucket` is a third byte-identical Caffeine+Bucket4j core. **Two halves of the original wording were wrong and are corrected here.** "The same class twice" is false: `ContactMessageLimiter` carries a global daily bucket that a `KeyedRateLimiter(capacity, window, idleTtl)` signature has no slot for, and its own docblock calls that bucket the only limit an attacker cannot pick the key for. "Their TTL policies have already drifted" is also false: `ClientGalleryAccessLimiter`'s `window + 15min` idle TTL is a documented deliberate choice (an attacker must not reset it by pausing), and calling it drift invites someone to "fix" it to 2h and weaken it. **Cost is test-dominated: ~-55 source against ~84 test sites** (7 constructor sites + 24 calls in `ContactMessageLimiterTest`, 7 + 32 in `ClientGalleryAccessLimiterTest`, plus `CollectionControllerProdTest` and `MessagesControllerPublicTest`). Keep `AuthLoginLimiter` separate -- it is a `Cache<String,Integer>` counter, not Bucket4j. Low priority.

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
- [x] #5. One CloudFront invalidation implementation. **DONE 2026-08-31 (third run).**
  `ReadCacheInvalidator` gained a public `invalidatePaths(List<String>)`, and
  `ImageProcessingService.invalidateCloudFrontPaths` is deleted; both delete paths call the delegate.
  `ImageProcessingService` drops `CloudFrontClient` and the `cloudfront.distribution-id` `@Value`
  and gains `ReadCacheInvalidator`, so the constructor goes **arity 10 -> 9** as predicted.
  **The `markChanged()` trap was confirmed by reading both methods, and it is worse than the board's
  wording.** `markChanged()` publishes an event whose listener always invalidates the two constants
  in `READ_SURFACE_PATHS` (`/api/read/collections*`, `/api/read/content*`). Those are API routes, so
  they do not match media keys **at all** — routing image deletes through it would mean deleted
  bytes keep being served from the edge until their own TTL expires. It is not just "wildcards
  instead of specific keys plus a deferral"; two of the three differences are outright wrong.
  `invalidatePaths` therefore runs synchronously with no event and no `@TransactionalEventListener`,
  and `READ_SURFACE_PATHS` is untouched.
  **The guard is a test, not a comment**: `invalidatePathsSendsSpecificKeys` captures the request
  builder and asserts the exact prefixed media keys, so making that swap reddens the suite.
  It was **mutation-proved before shipping** (working rules 15/32/41): dropping the `"/" + k` prefix
  reddened exactly that one test and nothing else. Five tests added, none rewritten; 1450 -> 1455.
  The class docblock's "two wildcards ... which is why this does not take per-slug paths" sentence
  was scoped to `markChanged`, since the class now does take explicit paths. Prior text below.
  **The item undersells itself**: `cloudFrontClient` and `cloudFrontDistributionId` are used only inside `invalidateCloudFrontPaths`, so delegating removes two constructor dependencies (arity 10 -> 9). Test cost is ~4 lines and no mock or verify is rewritten. **Trap**: route through `invalidatePaths(List<String>)` as written -- routing through `markChanged()` swaps specific keys for two wildcards and defers to after-commit, which is a behavior change. `ImageProcessingService.invalidateCloudFrontPaths` (**declaration at `869` as of 2026-08-31**, was 865-885, before that 838-863 -- +4 from #249's docblock; find by name) re-implements what `services/ReadCacheInvalidator.java:~79-106` already owns. Give `ReadCacheInvalidator` an `invalidatePaths(List<String>)` and delegate. ~25 lines.

## MR 17 — Controllers

- [ ] #7. Admin image list duplicates the prod image search — same 12 `@RequestParam`s, same service call, different response wrapper (`AdminController.getAllImages` (**`258-294` as of #218**) vs `ContentControllerProd.searchImages` (**declaration at `46` as of 2026-08-31, drifted +1 from `45`**; find by name)). Bind the filter once with a shared `@ModelAttribute` record, reuse prod's constraints, return one response type. **"Reuse prod's constraints" is an unpriced behavior change**: admin clamps with `Math.min(Math.max(size, 1), 200)` while prod validates with `@Min/@Max`, so admin `size=500` goes from silently returning 200 rows to a 400; defaults also differ (50 vs 30), and two frontend pages that pass no `size` would jump from 30 images to 50. **Do MR 19 #19 first** -- it is the same decision from the other direction, and #7 then shrinks to sharing the filter record. Realistic ~70 with test -- **re-verified 2026-08-31**, including the size handling: admin is `defaultValue = "50"` then `Math.min(Math.max(size, 1), 200)` (`AdminController:271`), prod is `defaultValue = "30"` with `@Min(1) @Max(200)` (`ContentControllerProd:61`), so `size=500` returns 200 rows on admin and 400 on prod. The **do MR 19 #19 first** ordering also re-confirmed: admin already returns `ResponseEntity<PagedResponse<ContentModels.Image>>` and re-wraps at 286-291, and that whole block disappears once #19 lands.
- [ ] #8. Role membership is writable from two endpoint pairs backed by the same repository calls (`PUT`/`DELETE /api/admin/users/{id}/roles/{roleId}` in `AdminUserController` -- `addUserToRole` / `removeUserFromRole` -- **declarations at `388` and `401` as of #257** (were 382/395; #257 added the passkey endpoints and a constructor parameter above them, +6. Before that the old 383/396 pointed at each method's first body line, not its declaration). Find by name -- vs `PUT`/`DELETE /api/admin/roles/{roleId}/members/{userId}` in `AdminRoleController:149-166` -- `addMember` / `removeMember`). Keep the roles-side pair. **Blocker resolved 2026-08-24: the frontend uses BOTH**, driving two different screens (`RoleDetailView.tsx` calls the roles-side route, `UserRolesSection.tsx` the users-side). So this is a coordinated cross-repo change with deploy ordering, not a backend delete -- cheapest path is making the users-side method delegate to the roles-side one, leaving components untouched. **PR #191 lowered its priority**: both pairs now route through the guarded `RoleRepository.addMember`, so this is tidiness, not security. Scope must also include that method's docblock, which says "the two admin endpoints that reach here". **All four refs re-verified exact 2026-08-31 (third run)**: `addUserToRole` 388, `removeUserFromRole` 401, `AdminRoleController.addMember` 150, `.removeMember` 163 -- both inside the recorded `149-166`. **The docblock is at `RoleRepository.java:138-149` and `grep "two admin endpoints" ` returns nothing** because the phrase is line-wrapped between "two" and "admin". Find it by reading the docblock, not by grep.

## MR 18 — Services

- [ ] #9. The from-disk and ingest background loops are ~70 lines of copy-paste (`processFilesFromDiskLoop`, **declaration at `331` as of #255** (range was 316-420), vs `ingestFilesGroupedByDayLoop`, **declaration at `459`** (range was 444-555) -- both +15 from #255; find them by name -- the largest drift on the board, ~38 lines each), including a CREATE/UPDATE switch the ingest loop already merged. One shared loop with a `(fileEntry, prepared) -> collectionId` resolver. **Three copies, not two** -- the CREATE/UPDATE arms inside `processFilesFromDiskLoop` are a third. Net deletion ~110, better than the stated ~85, and all source: **zero forced test churn** -- re-confirmed 2026-08-31, neither loop is named in any test file. **Both declarations re-verified exact 2026-08-31 (331 and 459); no drift since #255.** Confirming context: the ingest loop's own docblock at 449 says "Same shape as {@link #processFilesFromDiskLoop}", so the duplication is documented in the source.
- [ ] #10. `updateGif` reimplements the tag/people/location merge blocks that `ContentMutationUtil` already owns as `updateImage*Optimized` (`ContentService.updateGif`, **declaration at `550` as of #256** (was 546-635; +4 from #256's docblock, net of its comment deletions), vs the three `updateImage*Optimized` helpers in `ContentMutationUtil`, **`183-243`**: Tags 183, People 205, Locations 227). **"The helpers only use the content id" is FALSE** -- all three call `setTags`/`setPeople`/`setLocations`, which are declared on subclasses, not `ContentEntity`. The fix needs a return-the-set signature, not a retype, and it converts `ContentServiceTest.updateGif_persistsPeopleAndLocations` into a weaker test. Realistic ~180, not ~40. **All four refs re-verified exact 2026-08-31** (`updateGif` 550; the three `updateImage*Optimized` at 183/205/227), and nothing has made it cheaper. `ContentServiceTest.updateGif_persistsPeopleAndLocations` still exists and is still the test that gets weaker under the return-the-set signature.
- [ ] #11. Four near-identical BFS walks: `RoleGrantPropagationService.java:168-223` (three) plus `CollectionService` `validateNoLinkCycle`/`parentIdsOf` (**`505-538` as of #266**, was `465-495`; find them by name). One `walk(root, neighborsFn)` helper. **Five walks, not four** -- `propagateToVisibleSubtree` is a fifth the line range missed, and it sits at **127**, above the recorded `168-223` range. **Re-derived 2026-08-31 (third run) by reading the bodies, not the names** -- every one is `Set<Long> visited` + `Deque<Long> pending = new ArrayDeque<>(...)` + `while` + `if (!visited.add(current))`. Declarations: `propagateToVisibleSubtree` **127**, `ancestorsOf` **168**, `subtreeOf` **188**, `visiblyLinkedAncestorsOf` **207**, `CollectionService.validateNoLinkCycle` **508** (walk at **513-530**) with `parentIdsOf` at **534** (all +40 from #266; were 468 / 473-489 / 494, and **the stale 468 and 494 both land on blank lines**, so those two fail visibly). `rematerializeSubtreeFromAncestors` (155) is **not** a walk -- it calls `ancestorsOf`. ~95 lines, zero test churn, pinned by 33 integration tests, none of which names any of the five private methods. **Best value in MR 18.**
- [ ] #12. `nextOrderIndex` logic. **"Five places" is PREMISE-CORRECTED 2026-08-31 (third run) -- it mixed two different units, which is working rule 14's exact failure.** There are **three places that compute the index**, all the same two lines over `collectionRepository.getMaxOrderIndexForCollection`: `ContentService.nextOrderIndex` (declaration **467**, body 468-469), `ContentMutationUtil.nextOrderIndex` (declaration **170**, body 171-172, private), and `TagService:124-125` inline and not extracted. The other "places" are **call sites that already delegate**, not copies: `ImageUploadPipelineService` calls `contentService.nextOrderIndex` at 357, 527, 546 and 760. So it is **three copies and four delegating call sites**. There is exactly one `MAX(order_index)` SQL in the repo, at `CollectionRepository:867`. `TagService` also differs -- it seeds a counter it then post-increments (`.orderIndex(orderIndex++)` at 131), so a shared helper replaces its first line only, not its loop. **Yield is ~4 source lines across three files**, not what "five places" suggests. The delegate instruction and its stub count are exact: `ImageUploadPipelineServiceTest` has **15** `when(contentService.nextOrderIndex(...))` stubs (144, 199, 351, 387, 416, 443, 498, 533, 576, 659, 683, 726, 777, 822, 950). **Do it the delegate way or not at all.**
- [ ] #13. Entity-to-Record mapping and case-insensitive sort duplicated across four files (`Records.Tag` mapping at `ContentModelConverter.convertTagsToModels` (**`323` as of 2026-08-29**), `MetadataService.toTagModel` (**`430`** as of 2026-08-29 -- the method is `toTagModel`, not `toTagRecord`; the Location mapping is `toLocationModel` at **`438`**), `SyntheticCollectionResolver:150`, `ContentService`'s newly-created-tags map (**`986` as of #256**, was 994); Location mapping/sort twice). Static `from(entity)` factories on the records. **The 10 tag + 4 location counts DO NOT REPRODUCE (re-derived 2026-08-31, third run).** Grepping the construction shape gives **9 total: 4 Tag and 5 Location** -- Tag at `ContentModelConverter:328`, `ContentService:986`, `MetadataService:431`, `SyntheticCollectionResolver:150`; Location at `ContentModelConverter:657`, `CollectionService:265` as of #266 (was 267) and `269`, `MetadataService:439`, `CollectionProcessingUtil:160`. The numbers are **inverted** relative to the board (more Location than Tag) and the tag count is 4, not 10. Whatever 10+4 counted, it was not `new Records.Tag(` / `new Records.Location(`; working rule 14 applies -- say which unit the number is in. All five declaration refs above re-verified exact. **The estimate is unaffected and still the worst on the board: net ~0 lines**, because every copy and every replacement is one line. The suggested fix also flips the layering -- `Records.java` currently imports nothing from `entity`. **The finding worth keeping is not the dedupe**: `ContentModelConverter` and `CollectionProcessingUtil` sort their output and `MetadataService`/`SyntheticCollectionResolver`/`ContentService` do not, which is a live API-ordering inconsistency. Split that out and drop the rest.

## MR 19 — Query efficiency and data layer

- [x] #14. `convertEntityToModel` loaded the same content row twice. **DONE**
  ([#218](https://github.com/themancalledzac/edens.zac.backend/pull/218), 2026-08-25) — two
  queries to one, and **the first item in seven to need no adjustment at implementation time**,
  which is what taught working rule 27. The method had no test at all; the two added tests are the
  only mutation detectors. Write-up (deletion cost table for the two dead finders included) moved
  2026-08-29 to the [history file](2026-08-22-backend-cleanup-history.md#mr-19-14-outcome-2026-08-25).
- [ ] #15. `getUpdateCollectionData` fetches the collection row twice (`CollectionService.getUpdateCollectionData`, **declaration at `888` as of #266 -- was `848`, and #266's `batchConvertOrphans` shifted everything past old-271 by +40; find it by name**). The double fetch is confirmed: **`891-895` as of #266** calls the service's own `findBySlug` (**declared at `348`**, returns `CollectionModel`), and **`908-912`** calls `collectionRepository.findBySlug` (returns `CollectionEntity`). *(All four were `851-855` / `308` / `868-873` before #266. **The stale numbers all resolved to real repository calls in two different paginated finders**, so following them would have looked like confirming the double fetch while reading unrelated methods -- re-derive by name.)* Both throw the same `ResourceNotFoundException` with the same message.

  **ANSWERED 2026-08-31 (fifth run) by reading the converter, and the answer inverts the item.** The question was whether `findBySlug`'s converter populates password and recipient emails or deliberately strips them. **It never populates them.** `CollectionProcessingUtil.convertToFullModel` -> `convertToModel` -> the shared base sets only `model.setIsPasswordProtected(entity.getGalleryPassword() != null)` (**`CollectionProcessingUtil:186`**); there is no `setGalleryPassword` or `setRecipientEmails` on a `CollectionModel` anywhere in `src/main`. The second entity fetch exists because **`CollectionService:931-932`** copies those two fields off the entity onto the model itself.

  **So this is not a double fetch and it is not a de-duplication.** The two fetches return different data on purpose. Deleting the second returns a null gallery password and empty recipients; widening the converter to avoid that leaks the gallery password onto every read path sharing it, which is exactly the risk the item flagged and could not price. **The fix shape is a two-column projection for this one caller** -- a smaller, different change than the deletion the item describes, and it should be re-titled before it is worked. Working rule 21 again: correct premise, prescribed fix that would have shipped a bug. **The item is COLD now**, and cheaper than its old framing suggested.

  **The "always-true null check" sub-claim is now VERIFIED true** (was UNVERIFIED). It is `if (collection.getContent() != null)` at **`CollectionService:914`**, and both branches of `convertToFullModel` set content -- `Collections.emptyList()` at `CollectionProcessingUtil:352` in the empty case, and via `convertToModel` at `:328` otherwise -- so it is never null on this path. It is dead weight, and removing it is part of this item, not a separate one.
- [x] #16. `findCurrentContentCollections` N+1. **DONE** ([#216](https://github.com/themancalledzac/edens.zac.backend/pull/216)) —
  201 queries to 1. The diagnosis was exact; **the suggested fix was not, and would have shipped a
  silent bug** (its `IN (:ids) OR referenced_collection_id IN (:ids)` clause drops the parent
  scope). [Full write-up](2026-08-22-backend-cleanup-history.md#mr-19-16-outcome-2026-08-25----the-suggested-clause-was-the-bug).
- [ ] #17. Smaller items: `UserInviteService.validate`/`redeem` duplicate token resolution (**`validate` 158-175 and `redeem` 257-274 as of 2026-08-27**; was 140-152 / 220-237, and before that 85-130 -- the file has gone 130 -> 238 -> 275 lines under S-7/S-9/S-15, **so stop quoting ranges for this one and find the two methods by name** -- into `findLiveInvite`); pagination normalization re-inlined in `CollectionService.getCollectionWithPagination` (**`145-147` as of #266** -- `int normalizedPage` / `normalizedSize` / `offset`, and it is three lines not four; was `143-145`, `142-144`, then `127-130`. **The `143-145` reading was anchor-text-verified hours before #266 invalidated it by adding two imports** -- anchor text was checked and the number was not re-derived from it, which is the whole failure mode; call `PaginationUtil`); `toEntity`'s `defaultPageSize` parameter and `applyPaginationDefaults` are redundant with each other (`CollectionProcessingUtil.toEntity` **`566-589`** and `applyPaginationDefaults` **`924-932`** as of 2026-08-25, were `569-596, 939-947` -- **neither file was touched by #213/#214/#216, so this drift predates them**); `uploadToS3`/`streamFileToS3` duplicate key and URL construction (`ImageProcessingService` -- declarations at **`720`** and **`747`** as of 2026-08-31, were 716/743, before that 697-745 -- +4 from #249); EmailService HTML skeleton **three times, not twice** -- `buildHtml`, `buildInviteHtml` and `buildShareLinkHtml`, the third added by [#213](https://github.com/themancalledzac/edens.zac.backend/pull/213) under an explicit guardrail not to fold it in there (optional, **~50-70 lines now, not ~35**). #213's own write-up sent this consolidation to MR 24; that was wrong, it lives here and has always lived here.

  **Every ref in this item re-verified exact 2026-08-31 (third run)** -- unusually good for a list this long. `validate` **158**, `redeem` **257** (note `redeem` also has an internal caller at 211, and no `findLiveInvite` exists yet); `toEntity` **566** and `applyPaginationDefaults` **924**, with the redundancy visible in the body -- `toEntity` sets `setContentPerPage(defaultPageSize)` at 586 and then returns `applyPaginationDefaults(entity)` at 588; `uploadToS3` **720** (6 callers) and `streamFileToS3` **747** (2 callers); `buildHtml` **195**, `buildInviteHtml` **246**, `buildShareLinkHtml` **301**, with the docblock chain self-documenting the duplication (246 says "mirroring {@link #buildHtml}", 301 says "mirroring {@link #buildInviteHtml}").

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
- [ ] #18. `EquipmentRepository` repeats each SELECT column list **3-4 times per list across 3 lists** while sibling repositories hoist constants (`AppUserRepository`, `ShareLinkRepository`, `WebAuthnCredentialRepository`, `CollectionRepository` all do it right -- re-confirmed 2026-08-31). Hoist per-entity constants. **RE-PRICED DOWN 2026-08-31 (third run), because two of the eleven lists cannot share a constant.** There are 11 lists, of which **9 are hoistable into 3 constants** and 2 are one-off variants: `104` carries an extra `body_serial_number` and `198` an extra `lens_serial_number`. The other nine are identical within their group -- cameras `113`/`121`/`183`, lenses `207`/`214`/`258`, film types `270`/`278`/`325`. (The two `SELECT COUNT(*) > 0` at 221 and 285 are not column lists.) Nine one-line replacements plus three constant declarations is roughly **-6 net, not ~-15**. **The value is consistency with the siblings, not line count.** *Blocker*: do not force the two variants into the constant by concatenation -- that changes what the row mapper receives on the two serial-number lookups.
- [ ] #19. `model/ImageSearchResponse.java` is a strict subset of `model/PagedResponse.java`. Replace it with `PagedResponse<ContentModels.Image>`. **Unblocked 2026-08-24**: the frontend reads only `result.content`, never `totalElements`/`totalPages`, and ignores unknown keys, so growing the contract is safe. (That is a 2026-08-24 finding in the other repo and was **not** re-verified on 2026-08-31.) `AdminController` already re-wraps into `PagedResponse`, so those lines vanish. **Do this before MR 17 #7.**

  **RE-PRICED UP 2026-08-31 (third run): the board priced the source and not the tests.** Four source sites -- `ContentService.searchImages` declaration `393` and its return at `404`; `ContentControllerProd.searchImages` return type at `46` and its local at `75`; `AdminController` import `13` and lines 286-291; then delete `model/ImageSearchResponse.java`. **Seven test constructions the board does not mention, across three files**: `ContentControllerProdTest` at `259`, `280`, `312`, `334`, `356`; `AdminControllerTest:751` as of #267 (was 750); `ContentServiceTest:101`. Each becomes `new PagedResponse<>(images, n, m, number, last)` and the implementer has to **pick correct `number` and `last` per test rather than copy them**. This is exactly the shape the board has been burned by twice -- a response-type change that reads as local and lands in tests. The good news: **no assertion needs rewriting.** `ContentControllerProdTest` asserts on `$.content`/`$.totalElements`/`$.totalPages`, `AdminControllerTest:757-759` on `$.content`, `ContentServiceTest:102-104` on `.content()`/`.totalElements()`/`.totalPages()` -- all keys and accessors `PagedResponse` also carries. Only the 7 constructor calls need two more arguments each. **Realistic ~25 lines, not "4 more lines vanish as a bonus".**
- [ ] #20. `Records.FilmFormat` (DTO) shadows the `FilmFormat` enum, forcing a fully-qualified name at `Records.java:23` and duplicating the mapping at `ContentControllerProd:147-149` and `CollectionService`. Rename the record `FilmFormatOption`, import the enum, one static factory. **Refs re-derived 2026-08-31 (third run): `Records.java:23` and `ContentControllerProd:147-149` exact; `CollectionService` DRIFTED +3, from `930-932` to `**973-975 as of #266** (was 933-935)`** (`List<Records.FilmFormat> filmFormats =` at 933, `Arrays.stream(FilmFormat.values())` at 934, `.map(ff -> new Records.FilmFormat(...))` at 935). **One source site the board never named: `model/GeneralMetadataDTO.java:26` declares `List<Records.FilmFormat> filmFormats)`** and the rename touches it -- three source sites, not two. **Test impact is nil**: `ContentControllerProdTest` asserts on `$.filmFormats[0].name` and `.displayName` (394-398, 412), which are component names and do not change under a type rename, and no test constructs `Records.FilmFormat`.
- [x] #21. **DONE** ([#266](https://github.com/themancalledzac/edens.zac.backend/pull/266), 2026-08-31) — the location endpoint's N+1, up to 150 queries where 6 do. Shipped exactly as the item specified, the second consecutive item needing no adjustment. **Taught that a re-merge warning needs its own test**: concatenating the two batches passes a test that only checks which converters were called. Item body, mutation table and reasoning in [history](2026-08-22-backend-cleanup-history.md#mr-19-21-outcome-2026-08-31----the-n1-and-the-reordering-that-would-have-ridden-along).

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
- [ ] Fully qualified names inline: **14 sites, not 6.** The six named (`CollectionService.isGalleryAccessAuthorized`'s parameter -- the doc's `542`, then `533`, `534`, `541`, `539`, **is `582` as of #266 (declaration at `581`) -- the sixth correction to one ref, which is the point: stop writing the number** after S-6's javadoc, which is the **fourth** correction to one ref and the reason this item names symbols and not lines. Read the number as advisory and the symbol as the target; `CollectionProcessingUtil`, `TagViewResolver`, `GalleryAccessCookies`, `ContactMessageLimiter`, `Records.java`) plus eight in the data layer the original scan missed: `BaseDao` (3), `CollectionRepository`, `EquipmentRepository` (3), `PersonRepository`. Import-only, **zero test coupling**. `Records.java` still needs consolidation #20 first (the `FilmFormat` name clash).
- [ ] `Optional.get()` -- **RE-DERIVED 2026-08-31 (fourth run): 58 raw, 47 Optional** (was 57 / 46).
  Command, exactly as run: `grep -rn --include='*.java' '\.get()' src/main/java | wc -l` -> **58**;
  exactly **11** are Atomic, leaving **47**. **The board's recorded exclusion method does not work and
  never did**: none of the 11 Atomic lines contains the string "Atomic" -- the type is on the
  declaration, not the call -- so `grep -i atomic` over the sweep output returns 0 and you have to
  resolve each receiver by hand. The 11: `AdminHomeService:42` (AtomicReference), `JobTrackingService:172-175`,
  `ImageUploadPipelineService:431-433` and `:565-567` (AtomicInteger). **The +1 is not attributable to
  this run** -- none of #265, #266 or #267 added a `.get()`. Every per-file figure the bullet names
  below still holds. Everything after this paragraph predates the re-derivation; read it for the
  pattern, not the numbers.

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

- [ ] `AdminUserController` is a service wearing a controller's clothes: two repositories and **seven** services injected (was six; S-8 added `SessionService`) plus a `frontendBaseUrl`, **601** lines (469 -> 474 -> 481 -> 520 -> 523 -> 601, the last +78 across #257 and #265; **only 12 of those 78 are #265's**, the rest predate this run), entity building, multi-step `@Transactional` orchestration, afterCommit hooks. Extract an `AdminUserService`. **Largest real cost in Wave 7**: ~200 source lines move, but `AdminUserControllerTest` is **1,462** lines (1,015 -> 1,097 -> 1,183 -> 1,308 -> 1,417 -> 1,462; **+45 from [#272](https://github.com/themancalledzac/edens.zac.backend/pull/272)**, which deleted 73 comment lines and added 118 docblock lines. **The hidden half got bigger, not smaller, and that was the right trade** -- the prose is the same prose, relocated into docblocks where rule 37 allows it, so an `AdminUserService` extraction still has to carry it.). **The 1,294 recorded here was already wrong when it was written**: the file last changed in #241 on 2026-08-30, before that day's close-out, and the close-out did not re-measure it. Nothing this session touched the file -- this is a number that rotted on its own, outside the neighbourhood of anything that merged, which is the case a scoped drift sweep cannot catch and is the hidden half.

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
  stronger than when it was written.** The four files are `CollectionService` **1,769** (**re-measured 2026-08-31 after #266**, was 1,726),
  `ContentService` **1,014**, `ImageProcessingService` **1,394** (+4, #249), `CollectionProcessingUtil`
  **933** -- all four quoted numbers were stale. The total went 5,107 -> 5,083 across 24
  MRs of dedicated cleanup, and is **5,065 as of 2026-08-31 (fourth run)** -- and the total is the misleading part: it moved +2 while three of its four components moved (`CollectionService` +43 from #266, `ContentService` 1,014 -> **1,006**, `ImageProcessingService` 1,394 -> **1,357**, `CollectionProcessingUtil` **933** unchanged). Only `CollectionService` is attributable to this run; the other two rotted outside the neighbourhood of anything that merged. Was **5,063 as of #218** -- a net -44 lines, under one percent, and
  `ImageProcessingService` **grew 25**. "Waves 5-7 shrink these" is not what the data shows; the waves have been shrinking
  other files. Decide the split boundaries before the next feature lands in them. **COLD -- this
  needs a decision, not research.**

---

# Wave 8 — Tests

## MR 25 — Shared fixtures and consolidation

- [ ] `new ContentModels.Image(` with 31 positional components appears in **11** test files (not 12; 13 call sites), **7** of which have their own private helper. Same for `CollectionRequests.Update` -- **corrected 2026-08-29: the canonical record has 22 components** (`parents` is the 22nd; the compat docblock's "all five set to null" checks out, 22 - 5 = 17), and the deletion target is the **17**-arg compat constructor at its **21** test call sites -- 25 `Update` constructions in all (21 compat + 4 canonical, one of the canonical in `CollaboratorRequests`). The "Positional constructors" list below already said 21; the two entries disagreed and 21 is right, and **an arity scan on 2026-08-31 (third run) confirmed it and cleared both UNCHECKED markers**. One `TestFixtures` class with builders. **The doc underestimates by ~2x in the good direction**: measured, those sites are **745 lines of positional construction**, replaced by roughly 120, so **~-600 net, 18 test files, and zero main files touched.**
- [ ] `services/CollectionServiceTest.java` (**2,725 lines, re-measured with `wc -l` 2026-08-31 after #266**; was 2,640 as of #218, 2,644 before that, not the baseline 2,412 -- it has grown 313 since baseline, so any estimate below is measured against the wrong denominator): assert/verify twins where the second test re-runs the first's stubbing and re-checks with `verify` — for example `createCollection_happyPath_savesAndReturnsUpdateResponse` (**`:138` as of #266**, was `:136`) versus `createCollection_verifiesEntityCreatedViaUtil` (**`:167`**, was `:165`); the `deleteCollection` plain-verify test (**`:198`**, was `:196`) is a strict subset of the inOrder version (**`:226`**, was `:224`). **All four shifted +2 again in #266** (they had previously shifted +8 together); all four stale numbers land on blank lines, so this set fails visibly rather than plausibly. ~250 lines.
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

- [ ] `model/CollectionRequests.java` -- 17-arg `Update` constructor, **21** test call sites, **RE-DERIVED and reproduced exactly 2026-08-31 (fourth run)**, with the scanner re-run rather than the item re-read: 24 raw in test = 21 at arity 17 + 3 canonical at arity 22, 1 raw in main at arity 22. Seven files; `CollectionServiceTest` carries 8 of the 21, at `:270, :315, :345, :389, :431, :2043, :2421, :2533`. Zero `src/main` callers -- the one main construction, `CollaboratorRequests.java:43`, is the canonical 22-arg. **This is the one that must ride with the `TestFixtures` pass.**
- [x] `model/DiskUploadRequest.java` -- 3-arg `FileEntry` constructor. **DONE** ([#267](https://github.com/themancalledzac/edens.zac.backend/pull/267), 2026-08-31). **Every number re-derived on the day and every one reproduced**: 13 three-arg (10 `ImageUploadPipelineServiceTest`, 3 `AdminControllerTest`) and 15 canonical (13 + 2 in the same two files), 28 total, zero in `src/main` at any arity. The arity-scanner method the board wrote down works and is worth keeping for the remaining three. **One thing the item asserted was untested rather than false**: "no API-contract effect" rests on Jackson binding a record through its canonical constructor, and **no test anywhere deserialized a `FileEntry`**, so the delete rested on an assumption. `DiskUploadRequestWireTest` now pins it from both directions. Write-up in [history](2026-08-22-backend-cleanup-history.md#mr-25-fileentry-outcome-2026-08-31----the-counts-held-and-an-untested-premise-turned-up).
- [x] `model/AuthPrincipal.java` -- 4-arg constructor. **DECIDED 2026-08-24: leave it.** It is not main-dead (`SessionService` calls it), so it never belonged under the old heading. All **36** call sites are one-liners (re-measured 2026-08-29: 35 test plus `SessionService.java:179`); deleting a 3-line convenience constructor to append `, null` at 35 clean sites is not an improvement. Closing this rather than carrying the hedge a third time.
- [x] `services/ContentService.java` — `resolveCollectionDownloadEntries` 2-arg overload. **DONE** ([#271](https://github.com/themancalledzac/edens.zac.backend/pull/271), 2026-08-31). All 5 counts reproduced before the edit; the 5 two-arg sites now pass `null` explicitly and the 4 three-arg sites in the same file were left alone. Selected by arity, per the guardrail. **One cost the item did not name**: the 3-arg docblock cross-referenced "the 2-arg overload", so deleting the overload made that a dangling reference and it went too. [Write-up](2026-08-22-backend-cleanup-history.md#mr-25s-resolvecollectiondownloadentries-2-arg-overload-271).
- [ ] `model/DownloadResolution.java` -- the `extension` component. **PRIORITY FLAG, added 2026-08-31 (third run): this is the most expensive of the four, not the cheapest, and its "0 main / 6 test" headline reads like a free delete.** Deleting the accessor means deleting the record component, which takes the canonical constructor from 4 args to 3, so every construction site changes too. **13 edits across 5 files, 2 of those edits in `src/main`** (both in `ContentService`; it is 2 edits, **1 file** -- the old wording read as 2 files. **Re-derived and reproduced exactly 2026-08-31, fourth run**, with `'\.extension\s*\('` escaped, and the unescaped control returning the same set so the over-match warning does not bite here) -- 6 accessor sites and 7 construction sites. **All refs RE-DERIVED 2026-08-31 (fifth run) after [#271](https://github.com/themancalledzac/edens.zac.backend/pull/271) rewrote both files this item lives in; the counts held and 7 of the 13 refs moved.** Accessors, `ContentServiceDownloadTest`: **88, 102, 201, 217, 237, 239** (were 96, 110, 210, 225, 240, 242 -- all six drifted, #272's sibling sweep and #271's arity edits both shifted this file). Construction: `ContentService:781` (**unchanged**) and `ContentService:835` (**was `:851`, -16 -- #271 deleted the 15-line 2-arg overload plus one docblock line above it**); `ContentDownloadAuthTest:94`, `ContentDownloadControllerProdTest:71` and `:75`, `DownloadUrlServiceTest:100` and `:101` (**all five unchanged** -- #271 did not touch those files). **It is the only one of the four that touches `src/main` at all. If MR 25 needs splitting, split this off.** The component does genuinely carry no main-side behavior: `DownloadUrlService` consumes `List<DownloadResolution>` at 83, 105, 108 and never reads `extension`, and there are zero `.extension()` calls anywhere in `src/main`. Prior text: **5** construction sites in test (not 4), **7 in total** as re-derived 2026-08-25 -- the two in `src/main` are both in `ContentService`
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

*(One row was ADDED 2026-08-31 by the third run's cross-repo scan -- whether the location endpoint
should keep serving an `images` array at all. It is the last bullet in this section, and it raises
this section's open count by one over whatever the Progress row records.)*

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
- [ ] **Whether the location endpoint should keep serving an `images` array at all.**
  *(Filed 2026-08-31, third run, by the cross-repo pair scan, as BE-2. Needs a coordinated decision,
  not a unilateral fix.)* `GET /api/read/collections/location/{slug}` hydrates and serializes up to 50
  orphan content models on every location page load. **Its only caller reads `.collections` and drops
  the rest** -- see FE-1 in the cross-repo section. Two directions, and they are not equivalent:

  1. **Teach the frontend to consume `LocationPageResponse.images`** and drop the second
     `searchImages` round-trip. This is the only path by which #258's GIF work ever becomes visible.
     It **changes what the page shows**: `LocationPageResponse.images` holds only the orphans not
     already held by the listed collections, while `searchImages({ locationId })` returns every image
     at the location. Moving to the location endpoint removes images from the grid that are already
     visible inside a collection card above it. That may well be the intent; it is a product decision.
  2. **Leave the frontend on `searchImages`** and stop paying for the orphan hydration on the backend.

  **Fix MR 19 #21 first either way** -- the N+1 is a pure regression and its fix is correct under both
  options. This row is only about whether the array survives.

# Stale side branches

Returned to the tracker 2026-08-29 alongside "Decisions needed" -- it carries an open worklist.

Found 2026-08-24 by `git worktree list` while resyncing the palace. Six worktrees, all created
before or during this cleanup effort and left behind while 25 MRs landed on `main` underneath them.
Ahead-counts re-run 2026-08-31 (third run); the three zero-ahead branches are still zero ahead.

**"None has an open PR" was WRONG and is corrected 2026-08-31 (third run).**
`0359-fe-ma1-collection-patch` carries [#252](https://github.com/themancalledzac/edens.zac.backend/pull/252),
opened 2026-08-31 (first run) and still open: one commit, `72d59c0`, adding item **#22** -- the missing
`PATCH /api/edit/collections/{id}` that all eleven of the frontend's MA1 tasks wait on. Nothing on the
board mentioned #22, PATCH or MA1, so **a cross-repo blocker a session went to the trouble of writing
up sat invisible inside an unmerged PR for two sessions**. That is the same failure the cross-repo
section exists to prevent, one level up. **Item #22's text is now folded into the tracker** under
"Bugs filed after the waves closed", so #252 can be merged or closed without losing it. **Do not
delete this branch until one of those happens.**

- [ ] **Delete the three that hold nothing.** **Re-run 2026-08-30:** `feat/collection-debloat` is
  **0 ahead, 175 behind** (`git rev-list --left-right --count origin/main...origin/<branch>`) -- the
  "0 ahead" that makes it deletable still holds; the behind-count moves on its own as `main`
  advances and is not worth re-recording. Original text: `feat/collection-debloat` (0 ahead, 117 behind),
  `claude/auth-password-reset` (0 ahead, 38 behind) and `claude/one-way-collection-associations`
  (0 ahead, 38 behind) have **zero unique commits**. They are worktrees holding no work. Per the
  user's standing worktree rule these are theirs to remove, so this is a recommendation, not a
  cleanup to perform unasked. Note `claude/auth-password-reset` is **not** a reason to unpark the
  password decision -- it contains no commits.
- [ ] **`0359-fe-ma1-collection-patch` -- NOT safe to delete, and it was not on this list until
  2026-08-31 (third run).** One commit, `72d59c0`, ahead of `main`, carrying
  [#252](https://github.com/themancalledzac/edens.zac.backend/pull/252) and the only copy of item
  #22. **Resolved 2026-08-31 (third run): #252 merged, so this branch is safe to delete.** Note it
  still reports **1 ahead** of `main` -- `git log origin/main..origin/0359-fe-ma1-collection-patch`
  returns `72d59c0` -- because #252 was **squash**-merged, so the original commit is not an ancestor
  of `main` and `db5d11e` carries its content instead. Do not read "1 ahead" here as unique work; the
  0-ahead test does not apply to any squash-merged branch on this board. The item's text also lives
  in the tracker directly, so nothing depends on the branch.
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
  *(superseded, demoted from an open checkbox 2026-08-31 -- it was a `- [ ] ` for an item settled
  directly above it, and it counted toward the file's open-box total.)* **`fix/s18-actuator-exclude`
  (added 2026-08-28; corrected 2026-08-29).**
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

## Next run (set 2026-08-31, fifth close-out)

**Four MRs shipped, the board is at 83, and for the first time the run ahead is three items that are
COLD on their own evidence rather than on a past session's say-so.** One of them stopped being a
question this run.

**Ask this first, before any code** — it is a routing call, not a fact, so nobody can settle it by
looking:

> **U-6: should `addCollection` be admin-gated at all?** S-14 closed on the principle that every
> admin endpoint goes through the same admin gate, and then `addCollection` turned out not to be an
> admin endpoint: it sits on `UserShareControllerProd` at `/api/read/user/share`, and the admin
> sentinel in `canView` is what makes it answer yes to everything. So the principle rejects the
> ownership test S-14 proposed and leaves the routing open. **The question is where the endpoint
> belongs**, and the answer is an MR either way — move it behind the admin gate, or state in its
> docblock that it is a public share-scope endpoint and the sentinel is deliberate. Do not settle it
> by adding a second gate; that is the shape S-14 already rejected.

**U-1 is still open and still gates U-7 and U-8.** It was asked at the top of this run and came back
"cannot check right now". Ask it again only if the user has host access this time; do not spend the
run on it, and do not probe production to settle it.

Then the run, one MR each:

1. **U-5 — `ClientIp`'s javadoc calls the header's presence "the trust signal".** One sentence at
   `config/ClientIp.java:14`, ref re-verified exact at this close-out. S-19 settled precisely
   because presence is *not* the trust signal: the frontend strips and re-derives `x-real-ip`.
   Cheapest fully-specified item on the board, which is why it goes first — it banks an MR before
   anything can go wrong. *Guardrail:* **the docblock only.** `ClientIp`'s actual resolution
   order is correct and is not what this fixes; if you find yourself editing the method body, stop
   and report what you found instead.
2. **Bug #18 — `updateLocation` misses the create path's slug-uniqueness check.** The only open bug
   on the board, COLD since it was filed and **not re-priced since**, so price it before writing.
   Two names that slugify identically pass the name check and hit `idx_location_slug` inside
   `LocationRepository.save`; `GlobalExceptionHandler` maps that to a generic 409 that never
   mentions the slug. Fix shape: mirror the create path's slug check before the save and return the
   same conflict shape the name check uses. *Guardrail:* **do not "fix" this in
   `GlobalExceptionHandler`** by teaching it to name the constraint. That would improve the message
   for every caller and leave the missing check missing; report the cost if you think otherwise.
3. **MR 19 #15 — the `getUpdateCollectionData` second fetch.** **Re-shaped by this close-out and
   worth re-reading the item before starting**: it is filed as "fetches the row twice" and it is not
   a duplication. The converter never populates `galleryPassword` or `recipientEmails`;
   `CollectionService:931-932` grafts them onto the model from the second entity fetch. So the fix
   is **a two-column projection for this one caller**, plus deleting the now-verified always-true
   `collection.getContent() != null` check at `:914`. *Guardrail:* **do not widen the converter.**
   Populating those fields on `CollectionModel` leaks the gallery password onto every read path
   sharing `convertToFullModel`; if a projection turns out not to be worth it, the correct outcome
   is to re-title the item "not a defect" and close it, not to make the converter carry the secret.

**Not in this run, and why.** **S-28** is COLD and small but its fix shape was re-aimed after #265
rewrote the docblock it targeted, so it wants a fresh read of that method rather than the item's
text. **MR 25's remaining two members** are both specified and both deliberately parked:
`DownloadResolution.extension` needs the collection-ZIP format-fallback coverage written back in
another form before its 4 load-bearing accessor assertions can go, and `CollectionRequests.Update`
must ride with the `TestFixtures` pass or its 21 sites get rewritten twice. **Item #22** is a
feature the frontend is blocked on, not cleanup, and belongs in a feature session.

**No full-board review this run.** The last one ran 2026-08-31 (third run); seven PRs have merged
since, well under a quarter of the board; the scoped drift sweep found nothing outside the
neighbourhood of what merged; and estimates have not blown out. Re-evaluate after the next two runs.

### Classification of the actionable near-term board (stamped 2026-08-31, fifth close-out)

*(Retitled 2026-08-31. It classifies about 25 items; the file holds **83** open checkboxes
(`grep -c '^- \[ \] '`, re-run 2026-08-31 at the **fifth** run's close-out: **87 -> 83**, exactly the four this run ticked, and this close-out filed nothing new -- the first close-out in three that did not. The figure stepped 89 after the third run's five PRs, 87 after [#265](https://github.com/themancalledzac/edens.zac.backend/pull/265), 86 after [#266](https://github.com/themancalledzac/edens.zac.backend/pull/266), 85 after [#267](https://github.com/themancalledzac/edens.zac.backend/pull/267), back to 87 when the fourth close-out filed #23 and the `AdminUserControllerTest` sweep, and to **83** when the fifth run shipped both of those plus U-4 and MR 25's overload.) **The figure first
written here was 94 and it was wrong** -- measured on the close-out's own pre-rebase branch, where the
three decision rows [#260](https://github.com/themancalledzac/edens.zac.backend/pull/260) had already
ticked were still open and item #22 was double-counted. At its own merge commit the file actually held
**90**; deduplicating #22 took it to 89. Reconciliation, oldest first: 80 at
[#259](https://github.com/themancalledzac/edens.zac.backend/pull/259), **-3** when #260 ticked the
three decisions, **-1** when [#261](https://github.com/themancalledzac/edens.zac.backend/pull/261)
ticked MR 16 #4, **+1** when [#252](https://github.com/themancalledzac/edens.zac.backend/pull/252)
filed #22, **-1** when [#262](https://github.com/themancalledzac/edens.zac.backend/pull/262) ticked
MR 16 #5, **+14** filed by [#263](https://github.com/themancalledzac/edens.zac.backend/pull/263), then
**-1** for the #22 duplicate. **The lesson is working rule 42 applied to a board's own metrics: a
count measured on a feature branch is not a count of `main`** -- re-run it after the merge, or do not
write it down. Everything in
MR 21-24 and MR 26, Waves 6 and 7, the eight unsettled security questions, the stale-docblock pair,
the branch worklist and all of Appendices C and D is deliberately outside it. A reader taking the old
heading literally would conclude the board is a quarter its actual size.)*

**COLD** — pick up with no unanswered question: **bug #18** (the only open bug, and it has not been
re-priced since it was filed); MR 17 #8 (cross-repo coordinated, but the coordination question was
answered 2026-08-24); MR 18 #9, #10, #11, #12; **MR 19 #15** -- **no longer COLD-with-a-first-step,
it is plain COLD**: the fifth run answered its gating question by reading the converter, and the
answer re-shapes the item from a de-duplication into a two-column projection (see the item); MR 19
#17, #18, #19; **MR 25's remaining two members** -- both fully specified, both parked behind a
guardrail rather than a question. Also COLD: **S-28** (its fix shape re-aimed after #265 rewrote the
docblock it targeted) and **U-5** (one docblock sentence in `ClientIp`, ref re-verified exact at
`:14` again this run). Item **#22** is COLD as backend work but is a feature the frontend is blocked
on, not cleanup.

**DONE since this list was drafted** -- MR 19 **#21**, **S-26**/**S-27** and MR 25's `FileEntry`
shipped 2026-08-31 (fourth run) as [#266](https://github.com/themancalledzac/edens.zac.backend/pull/266),
[#265](https://github.com/themancalledzac/edens.zac.backend/pull/265) and
[#267](https://github.com/themancalledzac/edens.zac.backend/pull/267). The fifth run then shipped
item **#23** ([#269](https://github.com/themancalledzac/edens.zac.backend/pull/269)), **U-4**
([#270](https://github.com/themancalledzac/edens.zac.backend/pull/270)), **MR 25's
`resolveCollectionDownloadEntries`** ([#271](https://github.com/themancalledzac/edens.zac.backend/pull/271))
and the **`AdminUserControllerTest` sweep** ([#272](https://github.com/themancalledzac/edens.zac.backend/pull/272)).
**MR 25 is two of four.**

**BLOCKED (user)** — **the three one-word calls are ANSWERED and shipped** (2026-08-31 third run;
`cover_image_id` drop, DB-password default dropped, `role.kind` kept and documented). What is left
here is the `coverImage` stripping row, which needs a judgement rather than a word: is the test a
stale record of a reverted fix, or a specification with no implementation? Its section has what
implementing it would break; do not resolve it by reading the comment.

**BLOCKED (other repo)** — the five cross-repo rows FE-1 through FE-5. They wait on someone filing
them in `edens.zac`, not on any backend work. **FE-1's premise was corrected 2026-08-31**: the
location page never reads the field, so widening its props changes nothing until the BE-2 decision is
made. FE-1 is therefore blocked on that decision as well as on the other repo.

**PARKED by decision** — gallery passwords. Nobody opens an MR against this until a design exists.

**BLOCKED (ordering)** — MR 17 #7 waits on MR 19 #19, which is the same decision from the other
direction. **And, found 2026-08-31 (fourth run): U-7 and U-8 both wait on U-1.** Both are ANSWERED
on their merits -- the actuator exclude list is redundant and S-18's criterion-incompleteness is
moot -- but only under the `prod` profile, because `ProdActuatorExposureGuard` is `@Profile("prod")`.
U-1 is the question of whether prod runs that profile. The board had all three filed as independent
questions; they are one chain.

**BLOCKED (user, and it needs host access rather than a decision)** — **U-1**, **asked 2026-08-31
(fifth run) and answered "cannot check right now"**, so it is blocked as put rather than as
neglected. Its docs half shipped as item #23
([#269](https://github.com/themancalledzac/edens.zac.backend/pull/269)); what remains is reading
`SPRING_PROFILES_ACTIVE` on the live host,
or hitting the origin without `X-Internal-Secret` and confirming a 403. That probe is a request
against production and belongs to whoever owns the host. **It now gates three items, not one.**

## Full-board review — RUN 2026-08-31 (third run)

**It ran.** Five read-only agents, one apply agent, one docs MR, zero code changes — the guardrail
the second close-out wrote for it held. Slices: the two unchecked MR 25 arity counts; premise
re-verification across MR 16-19; the merged security set including #257 attacked as a set; the
frontend/backend pair; and board hygiene, counts and internal consistency.

**What it found, in one line each.**

- **The counts are clean.** All eleven recorded counts reproduce exactly — open bugs, open security,
  history open boxes, the rule-37 leading-`//` figure and its main/test split, trailing inline
  comments, the three rule-12 protected files, the worktree count. **Nothing moved.** This is the
  first fully clean count audit the board has had.
- **The security set refilled it**: S-26 HIGH, S-27 and S-28 LOW, and S-16's reachability claim
  verified sound a third time.
- **Both UNCHECKED MR 25 counts are CONFIRMED**, with a reproducible method now written into the item.
- **The cross-repo GIF row's premise was wrong**, and correcting it turned up a live backend N+1
  (MR 19 #21) plus four more frontend divergences.
- **Six MR 16-19 items were re-priced or corrected** — three cost re-prices, two ref drifts, one
  premise correction — and every other ref in that range re-verified exact.
- **The board was lying about itself in five places**, all now fixed: an "empty" security section
  holding four open boxes, "0 open PRs" with #252 open, four live debts with no checkbox, "9 open"
  config-rot findings that exist nowhere, and a classification heading covering a quarter of the
  board.

**The structural lesson, and it is the one worth carrying.** Every claim that had drifted was a
summary claim the cited gate could not measure. `grep -c '^- \[ \] \*\*S-'` genuinely returned 0
while four open questions sat in that section, because they did not open with `**S-`. **A summary
must be measurable by the command it quotes, or it will go wrong the moment something is filed in a
shape the command cannot see.** Two new gates came out of this: `\*\*U-` for the unsettled questions,
and the removal of the config-rot cell's open/closed split, which had no gate at all.

Full agent-by-agent detail, the "checked and clean — do not redo" list from the security slice, and
the reverse-direction endpoint scan are in
[history](2026-08-22-backend-cleanup-history.md#full-board-review--run-2026-08-31-third-run).

## Session log

One line per session -- honoured in spirit, not in width; a review pass gets a paragraph. Three
entries in a row ending `Next: X` means X is being avoided -- say so and either make it real work
or drop it. (Checked 2026-08-24: not currently tripped. Two entries ended `Next: MR 15 #6` and it
then shipped.) **Retention rule (stated 2026-08-29 --
the omission that caused the last lapse): the current session's entries stay here; every close-out
moves the rest to the history file's log archive in the same pass. A close-out MR that grows this
log without moving the older entries is the lapse signal.** The archive has two halves and both are
in the history file: the [pre-split log](2026-08-22-backend-cleanup-history.md#session-log) (entries
from 2026-08-22) and the [newer archive](2026-08-22-backend-cleanup-history.md#session-log-archive--entries-moved-2026-08-31)
(2026-08-30 onward). **Link both** -- the tracker pointed only at the older half for a session, and
the newest entries sat 5,300 lines further down under a heading nothing linked to.

- 2026-08-31 (fifth run, close-out) — **four MRs, every one an item the fourth run had specified,
  and all four merged.** Shipped item **#23**
  ([#269](https://github.com/themancalledzac/edens.zac.backend/pull/269)), **U-4**
  ([#270](https://github.com/themancalledzac/edens.zac.backend/pull/270)), **MR 25's
  `resolveCollectionDownloadEntries` overload**
  ([#271](https://github.com/themancalledzac/edens.zac.backend/pull/271)) and the
  **`AdminUserControllerTest` 73-comment sweep**
  ([#272](https://github.com/themancalledzac/edens.zac.backend/pull/272)), plus this close-out.
  Board 87 -> **83**; **the first close-out in three that filed nothing new**, because the run was
  executing a spec rather than discovering one.
  **U-1 was asked first, as the fourth run instructed, and came back "cannot check right now."**
  That is the ask working: U-7 and U-8 stay blocked behind it and the actuator exclude list was not
  touched, with the question now on the record as put rather than unasked.
  **Every prescribed fix landed unadjusted -- four in a row now, seven across two runs.** What
  needed work was again the test. U-4's item said the reorder "costs nothing" and was right, and did
  not notice that **no existing test could catch the bug**: `resolveRejectsSessionWhoseAccountWasDisabled`
  asserts `expiresAt` is in the future, which holds under the bug too. Rule 45's enumeration gave
  four wrong forms of the move, three already covered and one not; the new test was mutation-proved
  at the guard (15 run, 1 failure), not assumed.
  **Step 3 paid again, and this time it inverted an item.** MR 19 #15's gating question --  does
  `findBySlug`'s converter strip the gallery password -- is **answered by reading**: the converter
  never populates `galleryPassword` or `recipientEmails`, only `isPasswordProtected`, and
  `CollectionService:931-932` grafts them on from the second fetch. So the "double fetch" is not a
  duplication at all, deleting it would return a null password, and widening the converter would
  leak that password onto every read path sharing it. The item is now a two-column projection and
  is plain COLD. Its "always-true null check" sub-claim went UNVERIFIED -> **VERIFIED true**.
  **Two recorded numbers were wrong and neither was this run's doing.** The test-side comment
  absolute was 1,371 where the board's own command returns **1,374** (every delta in the chain was
  right; only the absolute rotted), and **rule 36's own parenthetical still said "Returns 5"** when
  the gate has returned 1 since #265 -- both cells it governs were correct the whole time. Both are
  now **rule 46**, along with the trailing-comment metric mismatch (#271 deleted 17 comments and
  moved the rule-37 line count by 16) and the BSD `grep '^\s*//'` trap that bit this close-out's
  first measurement. Rule-37 checksum **1,633 -> 1,536**, the largest single-run drop on this board,
  reconciling exactly: main -2, test -98 = 73 + 16 + 9 across the three files edited.
  Scoped drift sweep: 13 refs re-derived in the neighbourhood of what merged, **7 drifted, 0 gone** --
  all six of `DownloadResolution.extension`'s accessor refs plus its `ContentService:851` -> `:835`
  construction site, and `SessionService:179` -> `:181`. `CollectionServiceTest:2139` re-checked and
  **exact**, which is rule 44's correction holding a second time. Next: **U-5, bug #18 and MR 19 #15**,
  with **U-6 asked first**.

