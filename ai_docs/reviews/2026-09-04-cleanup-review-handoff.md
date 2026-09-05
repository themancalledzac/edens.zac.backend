# Handoff: critical review of the backend cleanup spike

**Reviewed 2026-09-05; corrections applied in place. Review write-up:
[history](2026-08-22-backend-cleanup-history.md#full-board-review----run-2026-09-05-eleventh-run).**

**Written 2026-09-04.** For an agent doing a full, adversarial review of the cleanup effort: what
shipped, what is still open, and what is probably wrong. Every number here was measured on `main`
at `afa39d6f` on 2026-09-04, not copied from the board.

Read this file, then the two board files. Do not trust the board's numbers without re-running the
commands in [Appendix: gates](#appendix-gates) -- the board's central lesson, learned repeatedly, is
that its own recorded figures rot.

---

## 1. What this effort is

A cleanup spike opened 2026-08-22 against baseline `8c28cf3`, run as a long series of small MRs.
It is tracked in two files:

| File | Role |
|---|---|
| `ai_docs/reviews/2026-08-22-backend-cleanup-spike.md` | **The board.** Open work only. 1,873 lines. |
| `ai_docs/reviews/2026-08-22-backend-cleanup-history.md` | **The history.** Closed detail, write-ups, the working rules' full text. 9,915 lines. |

The split is working rule 11. Rule 53 gives it teeth: **the board must not grow in an MR.** When an
item closes, its body moves to history and the board keeps one status line.

The effort has produced **53+ working rules**, which live in full in the history file. They are not
decoration -- most were written after a specific failure, and several have failed *again* after being
written. A reviewer should treat the working rules as the most valuable artifact here and check
whether the recent MRs actually obeyed them. They frequently did not.

---

## 2. Verified state of `main` (2026-09-04, `afa39d6f`)

```
total open checkboxes    65
S- security findings      3   (S-29 HIGH, S-30 LOW, S-31 LOW)
U- unsettled questions    5   (U-1, U-2, U-3, U-7, U-8) + 1 non-U- deletion row
Bug # ledger              0   (closed)
#NN series                3   (#22, #30, #31)
board / history       1,873 / 9,915 lines
inline comments      203 main / 1,183 test  (leading form, git grep)
tree size          28,190 main / 35,936 test lines
```

**Zero open PRs.** Everything from the recent run is merged.

### Two pieces of live debt nobody has discharged

1. **Rule 42 debt -- the board's own size stamp is stale and self-contradicting.** It currently
   reads *"Measured on branch `refactor/mr19-17-invite-and-s3-put`: tracker 1,862, history 9,891"*
   and then instructs *"Re-run both on `main` after the merge (rule 42) and restamp."* Nobody did.
   `main` is **1,873 / 9,915**. The stamp names a branch that no longer exists.

2. **Rule 47 debt -- inline comments grew 14 on the test side and no cell records it.** The board's
   Inline-comments row says **1,169** test-side, measured at `3a53c0cb`. Actual today is **1,183**.
   Traced: +9 written by #300 (`MessagesControllerAdminTest` +5, `MessageRepositoryTest` +4) and +5
   by #301 (`CollectionServiceTest`); #299 and #302 are docs-only. The main-side count of 203 is still correct.

Both are small. Both are exactly the failure class this board keeps writing rules about, which is
why they belong at the top of a review rather than in a footnote.

---

## 3. What shipped 2026-09-02 -> 2026-09-04

Six PRs. Review each against the claims in its body -- several claims were wrong when written and
corrected later, which is itself worth auditing.

| PR | What it did | Where to be skeptical |
|---|---|---|
| #299 | Tenth-run full-board review, refiled the tracker | -208 board / +2,116 history. Huge. Did anything get lost in the move? |
| #302 | Tenth-run close-out; **widened the `#NN` gate** from `'^- \[ \] \*\*#2'` to `'^- \[ \] \*\*#[0-9]'` | The narrow form hardcoded the first digit and went blind to item #30 within a day. Check no other gate on the board has the same shape. |
| #303 | Deleted the dead `@Validated`; fixed four stale docblocks; recorded three question answers | Shipped a stale count (see section 4). The `PARENT` docblock classification is a judgement call worth re-reading. |
| #304 | Deleted `DownloadResolution.extension` | Record went 4 components -> 3, 13 refs. Coverage was mutation-proved; re-run that proof. |
| #305 | `UserInviteService.findLiveInvite`; shared S3 put in `ImageProcessingService` | Shipped with a **false premise** in its body (see section 4). Verify the extraction preserved `redeem`'s single-use guarantee. |
| #306 | Recovery: landed #304/#305 on `main` after they stranded; fixed #303's stale count | Cherry-pick recovery. Verify nothing was dropped versus the original branches. |
| #301 | Feature: parents on public reads, `is_film` backfill (V62) | Not cleanup. Carries a **real unverified scope limit** -- see section 5. Highest-value target for review. |

### The three questions answered this run

1. **Production Postgres collation is `C`.** Production is `postgres:16-alpine`
   (`scripts/ec2-postgres/docker-compose.yml` -- no `POSTGRES_INITDB_ARGS`, no `LANG` override, no
   `--locale`). musl implements no locale collation, so `initdb` records the image's `LANG` string
   while `strcoll` falls through to `strcmp`. **`SELECT datcollate` reports `en_US.utf8` and is
   wrong** -- it returns the same string on Debian and Alpine images. Measured by sorting a
   mixed-case list in both. This unblocked MR 18 #13, which is now real work (~10 source lines, ~5
   tests). **Worth independently re-verifying** -- it is the load-bearing claim behind reopening
   that item.
2. **S-29 is HIGH** (user's answer): client galleries hold images not published elsewhere.
3. **50 images on `/location/[slug]` and `/tag/[slug]` is wanted** -- closed the #294 page-size debt.

---

## 4. Where I was wrong, explicitly

A reviewer should weight these. All are mine, from this run.

**a) I violated working rule 28, which was written from a prior identical failure.**
Rule 28 says, verbatim: *"Do not stack a docs PR on an open PR"* and *"'the PR is merged' is not
'the change is on `main`'."* I recommended stacking #303 -> #304 -> #305 and told the user GitHub
would retarget each to `main` as its base merged. **That only happens when the base branch is
deleted.** #304 merged into `docs/29-validated-and-mr14-docblocks` and #305 into
`refactor/download-resolution-extension`. Both read MERGED; neither reached `main`. Recovered by
cherry-pick in #306 -- which is the same remedy rule 28 records from the #219 incident on
2026-08-25. **I did not read the history file before recommending the pattern, and the rule was
already there.**
*Review action:* confirm #306 actually carries the full content of both branches. Compare
`origin/main` against the pre-recovery branch tips.

**b) I shipped a wrong premise in #305's body and on the board.**
Claimed both members (a) and (d) have zero `src/test` references. True for (d) -- both methods are
private. **False for (a)**: `validate`/`redeem` have **16 call sites in 2 files**
(`UserInviteServiceIntegrationTest` 13, `InviteControllerTest` 3). I copied "zero" from the board's
stale text instead of running the grep. #302 had already corrected it and I had not read its diff.
The conclusion survived -- the extraction sits behind unchanged public signatures -- but by luck.

**c) I shipped a stale count in #303 and fixed it only in #306.**
#303 closed the #294 page-size debt, ticked the checkbox and moved the Progress row to "4 open",
but left the Cross-repo section's own count line reading *"Five open ... plus the newly filed #294
page-size debt."* That line is the designated authoritative count. Rule 36's exact failure mode,
and the history file shows the board did the same thing before with the S- counts.

**d) My first conflict resolution on #301 misplaced item #31 and duplicated a section heading.**
Caught and fixed before merge, but it was a blind `ours + theirs` concatenation. Worth checking the
final state of the Cross-repo section for any other residue.

**e) The MR 19 #17 line estimate was off by 3.5x.** The board said ~-14 lines; it landed at -4.
The estimate assumed no docblocks on the new helpers. Recorded on the row, but it suggests other
line estimates on the board are similarly optimistic.

---

## 5. Highest-value review targets

Ranked by consequence, not by effort.

### S-29 (HIGH, open) -- anonymous image search leaks private client photos
`GET /api/read/content/images/search` returns every image in the database with no
collection-visibility and no gallery-password filter. Anonymous: no cookie, no header, no session.
`SecurityConfig` matches `/api/read/content/**` against no rule so it falls to
`anyRequest().permitAll()`. The response carries `imageUrl` and `imageUrlRaw`, both unsigned
CloudFront URLs. It walks around three separate gates including `isDownloadAuthorized`, whose whole
purpose is presigning private S3 objects behind a CLIENT check.

The board records the mutation the fix's test must survive. **This is the single most important open
item and it has never been worked.** Verify the finding independently before pricing it.

### #31 / PR #301 -- the scope limit that is not verified
`V62` backfills `is_film` from a flagged film body or a film stock. It does **not** infer film from
a slug. The counts that motivated the item (`chamonix-film` 0/5, `vienna-film` 0/5,
`gorge-50km-film` 0/7 against `dolomites-film` 33/33) are repaired **only if** those images carry a
flagged body or stock. V23 flags exactly two bodies, which is the likely reason dolomites reads
33/33 and the rest read zero.

**Nobody has verified this against a live database.** The counts are a 2026-08-30 measurement. This
shipped to `main` on 2026-09-04 with the guardrail written but unresolved. If a third body is
involved, flagging it is a data call for the owner, not something a migration can guess.

Also review the visibility gating #301 added: public reads now apply both `c.visibility = 'LISTED'`
and `cc.visible = true` on the parent join, while admin and three internal callers (cycle detection,
delete-time parent recount, role-grant propagation) pass `listedOnly = false`. **Confirm all four
internal callers genuinely need every parent** -- a missed one is a silent authorization change.

### U-1 -- whether prod actually runs the `prod` profile
Under `dev`, `SecurityConfig` falls open. This blocks U-7 and U-8 and is the oldest unanswered
question on the board. Answered 2026-09-05; see U-1.

### MR 18 #13 -- now unblocked, never worked
The collation answer reopened it. SQL `ORDER BY <name> ASC` and Java `compareToIgnoreCase` disagree
under `C`: every uppercase name sorts before every lowercase one. Three public list endpoints
(`/api/read/content/tags`, `/people`, `/locations`) and the `people` field of every `CollectionModel`
are `C`-ordered; image chips and `CollectionModel.locations` are case-insensitive. The price depends
on the direction chosen; see the MR 18 #13 row.

---

## 6. Full open inventory (65 boxes)

| Section | Open | Notes |
|---|---|---|
| Carried forward | 1 | the `coverImage` stripping that does not exist |
| `#NN` series | 3 | #22 (PATCH endpoint), #30 (FE half owed), #31 (see section 5) |
| Cross-repo owed to frontend | 4 | FE-2..FE-5, all filed on the frontend board |
| Security findings | 3 | **S-29 HIGH**, S-30, S-31 |
| Unsettled security questions | 5 + 1 | U-1, U-2, U-3, U-7, U-8, plus a `RoleRepository` deletion row that opens `**Delete` and so moves no gate |
| MR 18 -- Services | 2 | #10 (`updateGif` duplication), #13 (unblocked) |
| MR 19 -- Query/data | 3 | #17 members (b)(c)(e), orphan `images` array, GIFs in `searchImages` |
| MR 21 -- Map bodies | 1 | 19 controller sites; **test-side sizing has no gate and never had one** |
| MR 22 -- Conventions | 9 | `ResponseEntity<?>`, try-catch in controllers, `@Value` x6, FQNs x11, `Optional.get()`, magic 2500, `JobStatus.status`, verb routes, gallery-access boolean |
| MR 23 -- Package moves | 3 | rename-only |
| MR 24 -- Service extraction | 5 | incl. `AdminUserController`'s twelve injected fields |
| MR 25 -- Fixtures | 4 + 1 | `ContentModels.Image` 31-arg, 4 typeless ITs, `V54Fold`, verify ratio; `CollectionRequests.Update` blocked on the `TestFixtures` pass |
| MR 26 -- Coverage gaps | 7 | `TokenUtil`, `SlugUtil`, `PaginationUtil`, `UserFollowsService`, validators, `UserRatingOverrideControllerProd`, **MR 11's headline security fix is untested** |
| Parked by decision | 2 | gallery passwords, partial indexes -- waiting on nobody |
| Stale side branches | 3 | worktree hygiene |
| Appendix C -- unverified leads | 8 | S3-before-DB deletes, possibly-dead job-status endpoint, `contentDisposition`, temp-slug race, `throws Exception`, missing ORDER BY, ... |

---

## 7. Traps that must not be lost

These are guardrails written onto the board because someone nearly walked into them. A reviewer
proposing work in these areas should know them first.

- **MR 19 #17 member (b):** do **not** fold `CollectionService.getCollectionWithPagination`'s
  normalization into `PaginationUtil.normalizeCollectionPageable`. That method defaults to
  `default_collection_per_page` = **10**; the call site uses `default_content_per_page` = **30**.
  The method whose name says "collection" is the wrong one for a collection's *content* page -- the
  obvious fold silently cuts every collection page from 30 items to 10. The site also needs the raw
  `offset`, which no `Pageable` helper returns.
- The `ConstraintViolationException` handler at `GlobalExceptionHandler:147` has no live source in
  `src/main`. Hibernate ORM is not a dependency (`mvn dependency:tree` shows only
  `jakarta.validation-api` and `hibernate-validator`), persistence is JDBC, and the 13
  constraint-annotated entity classes are never validated by anything. The only thrower is
  `GlobalExceptionHandlerTest:74`. Keep or delete it as a one-test decision; do not cite flush
  validation as a reason to keep it.
- Three `PARENT` uses at `CollectionService:1544`, `:1548`, `:1549` remain and are stale too: they warn
  against `type == PARENT`, but `collection.type` was dropped in V52 and the enum is gone, so the
  warned-against code cannot compile. Rewrite the sentence to describe the live rule (context is
  client-gallery when the collection `isClient` or contains at least one `isClient` child, mirroring
  `findClientGalleriesAndQualifyingParents`) and drop the `PARENT` vocabulary. Do not protect them.
- **`SEARCH_RESULT_LIMIT` is exactly 200, sitting on an inclusive `@Max(200)`.** One bump to 201
  turns `/search` into a 400. Also #294 deleted admin's clamp, so an admin passing `size > 200` now
  gets a 400 instead of 200 rows (no current caller does).
- **Never answer a collation question with `datcollate`.** Sort a mixed-case list.
- **`markUsedIfUnused` is the single-use gate** in `UserInviteService.redeem`, not the expiry check.
  The `findLiveInvite` extraction deliberately left it in `redeem`. This trap was written only in PR
  #305's body; it is now on the board at MR 19 #17.

---

## 8. Questions worth answering that nobody has asked

Offered as leads, not findings. I did not verify these.

1. Does any *other* gate on the board hardcode a digit or a literal the way the `#2` gate did?
   #302 fixed one instance; the class of bug was never swept. **Answered (D1 2b):** one gate was
   wrong-shape, the trailing-comment command, which counted a `jdbc:postgresql://` literal; fixed.
2. The board's line-count estimates have been wrong in the optimistic direction at least twice
   (MR 19 #17 at -4 vs -14; MR 18 #11 at net zero vs ~95). Is the whole estimate column unreliable?
   **Answered (D2):** the headline is sign-inverted (+4,323 net against "4,500-5,000 removed");
   deletions landed within 20%, extractions at a third to nothing.
3. `MR 21`'s test-side sizing is explicitly recorded as having **no reproducible command**. Every
   other quantified item on this board has a gate. Why does this one still get quoted? **Answered
   (D1 2e):** withdrawn and replaced with a commanded count.
4. Appendix C has 8 leads, several filed 2026-08-24, none promoted or dismissed in ten runs. Is
   Appendix C functioning, or is it where items go to be forgotten? **Answered (D2):** a parking lot
   since 2026-08-24; three dismissed by reading one method each, one promoted to Bug #32, one moved to
   a decision, one to MR 22, one open.
5. The test tree (35,936) is 27% larger than `src/main` (28,190), and the board's own note says
   ~8% of it tests the Java compiler and Lombok. That ratio has not moved across the whole effort.
   **Answered (D2):** the Lombok note described files deleted 2026-08-22; the ratio moved from 1.20x
   to 1.27x.
6. `main`-side inline comments have sat at 203 for several runs while the test side drifts. Is the
   rule-47 sweep only ever applied to files an MR happens to touch? **Answered (D1 2e):** yes; five
   files holding 108 of the 203 had no row, and now do.

---

## Appendix: gates

Run these on `main` before trusting any recorded figure.

```bash
T=ai_docs/reviews/2026-08-22-backend-cleanup-spike.md
grep -c '^- \[ \] ' $T                 # total open boxes
grep -c '^- \[ \] \*\*S-' $T           # security findings
grep -c '^- \[ \] \*\*U-' $T           # unsettled questions
grep -c '^- \[ \] \*\*Bug #' $T        # bug ledger
grep -c '^- \[ \] \*\*#[0-9]' $T       # #NN series -- USE THIS WIDE FORM, not '#2'
wc -l ai_docs/reviews/2026-08-22-backend-cleanup-spike.md \
      ai_docs/reviews/2026-08-22-backend-cleanup-history.md
```

Inline comments -- use `git grep`, not `grep -rn` (working rule 50: `grep -rn` skips a
binary-classified test file and reads 3 low):

```bash
git grep -c '^[[:space:]]*//' -- 'src/main/java' | awk -F: '{s+=$NF} END {print s}'
git grep -c '^[[:space:]]*//' -- 'src/test/java' | awk -F: '{s+=$NF} END {print s}'
```

Verify a PR's content actually reached `main` (working rule 28):

```bash
git log origin/main --grep '<subject>'
git grep '<symbol the PR introduced>' origin/main -- src/main
```

Build: `mvn clean install` (exits 0 on `afa39d6f`).

---

## How to use this document

Do not treat it as authoritative. It was written by the agent that produced five of the six MRs
under review, and section 4 lists five things that agent got wrong in three days. The measurements in section 2
are fresh and reproducible; the judgements are not neutral.

Start with S-29 and #31 -- those are consequence. Then audit whether the recent MRs obeyed the
working rules, because the pattern across this whole effort is that the rules get written, then
broken by the next run, including by the agent that wrote them.
