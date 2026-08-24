# Backend cleanup tracker

Living checklist of what is still open. Check items off as they land; do not delete them.

Completed detail lives in [`2026-08-22-backend-cleanup-history.md`](2026-08-22-backend-cleanup-history.md)
-- Waves 1-3 in full, and MR 12-13. This file carries only the working rules, the open MRs, and the
items carried forward out of closed waves. Keep it that way (working rule 11).

Source review: 2026-08-22, baseline `main` @ `8c28cf3`, six parallel passes (controllers/API, core collection and content services, media/upload pipeline, security/auth/config, data layer, tests/build). Every finding was verified against the code -- caller greps for dead-code claims, line-level reads for bugs. Unverified suspicions are quarantined in Appendix C.

Line numbers are from the `8c28cf3` baseline. Find symbols by name, not by line, once earlier MRs have shifted the files.

## Progress

| Wave | MRs | Status |
|---|---|---|
| 1 — Deletions | MR 1a-4 | **complete** — [history](2026-08-22-backend-cleanup-history.md#wave-1--deletions) (#159, #160, #161, #162, #164) |
| 2 — Bugs | MR 5-9 | **complete** — [history](2026-08-22-backend-cleanup-history.md#wave-2--bugs) (#165, #166, #168, #169, #170, #172, #173) |
| 3 — Security hardening | MR 10-11 | **complete** — [history](2026-08-22-backend-cleanup-history.md#wave-3--security-hardening) (#175, #176). One residual carried forward, below. |
| 4 — Comments and docs | MR 12-14 | **complete** — [history](2026-08-22-backend-cleanup-history.md#wave-4--mr-12-and-mr-13-complete) (#177, #178, #180, #181, #183, #184) and MR 14 ([#187](https://github.com/themancalledzac/edens.zac.backend/pull/187)) below. **Wave 4 removed 500 comments for -1,026 words across seven MRs.** MR 14 found the wave rule does not fit hardened files and produced working rule 12; its stale-docblock item is still open. |
| 5 — Consolidations | MR 15-19 | MR 15 #2 and #6 **done** ([#189](https://github.com/themancalledzac/edens.zac.backend/pull/189), #6 below). #6 also closed the `PersonRepository` carry and taught working rule 14. **next: MR 16** (rate limiters, AWS config, CloudFront invalidation). |
| 6 — Conventions | MR 20-22 | not started |
| 7 — Structure | MR 23-24 | not started |
| 8 — Tests | MR 25-26 | not started |

Original estimate: roughly 4,500-5,000 lines removed against a few hundred added. The test tree (32.6k lines) is larger than main (27.2k); about 8% of it tests the Java compiler and Lombok.

| Category | Count | Deletable lines (est.) |
|---|---|---|
| Bugs (fix, not delete) | 16 (5 high) | — |
| Security findings | 8 (1 high) | — |
| Dead code (main) | ~60 methods/fields/files | ~1,000 |
| Inline comments (main, rule violations) | ~~370~~ **567 measured** | ~300 net (also low) |
| Duplication consolidations (main) | 20 findings | ~500 |
| Dead/boilerplate tests | 7 findings | ~2,700 (+700 optional) |
| Build/config rot | 6 findings | ~150 |

## Carried forward out of closed waves

Reconciled 2026-08-23 during the history split. Waves 1-3 read "complete" but held five unticked
items that were still real. Four remain: the `PersonRepository` entry was closed by MR 15 #6 on
2026-08-24 (decided, not deleted -- see the history file). Everything else that was unticked there
was verified done and ticked off in the history file.

- [ ] **Wave 3 residual — chunked bodies bypass the public body cap.** `RateLimitFilter` reads
  `getContentLengthLong()`, which is -1 for `Transfer-Encoding: chunked`, so a chunked request
  reaches Jackson uncapped. Options: reject chunked on `/api/public/**` outright (complete, small
  risk of breaking a proxy that chunks), or wrap the input stream in a counting guard (complete, no
  client-visible behavior change, more code). Verify first whether anything in front -- CloudFront
  or the BFF -- already normalizes chunked to a fixed length, which would close this for free.
  Decide before adding code.
- [ ] **Four main-dead, test-live members owed to MR 25** (deleting them means editing test call
  sites, which is why MR 1a deferred them): `ContentService.resolveCollectionDownloadEntries` 2-arg
  overload (5 test sites), `DownloadResolution.extension` -- written, never read, docblock also
  stale (10 test sites), `CollectionRequests.Update`'s 17-arg constructor,
  `DiskUploadRequest.FileEntry`'s 3-arg constructor. Also listed under MR 25 below.
- [ ] **V19's `admin_home_tile.cover_image_id`** is written by nothing and read by nothing
  (`AdminHomeService` resolves covers by strategy). A schema change did not belong in a
  pure-deletion MR. Drop it in a migration or document it as reserved. Also in "Decisions needed".
- [ ] **Whether to ship a default DB password at all** is still undecided. MR 9a fixed the separator
  and preserved the existing default, so `spring.datasource.password` now falls back to `password`
  instead of `-password` -- the one line where that fix made a default more usable rather than less.
  Also in "Decisions needed".

## Working rules

Learned while doing the MRs; they apply to every item still open, not just the one that taught them.

1. **A property default in `application.properties` is probably dead.** `docker-compose.yml` injects
   `SPRING_DATASOURCE_*`, `EMAIL_*`, `WEBAUTHN_*`, `ADMIN_*` and more unconditionally, and an
   unconditionally-injected env var outranks the Spring property via relaxed binding. Before treating
   any config default as live, grep `docker-compose.yml` for the same key. MR 9's bug #9 looked like
   a risky behavior change until this was checked; it turned out the six placeholders are never
   consulted in any deployed path. `docker-compose.yml`'s own comment at the `EMAIL_*` block spells
   the rule out.
2. **`src/test/resources/application.properties` shadows the shipped file on the test classpath.**
   Any test asserting on shipped config must read `src/main/resources` directly. A
   `ClassPathResource("application.properties")` lookup silently reads the test stub and passes
   vacuously. The first draft of `ApplicationPropertiesPlaceholderTest` did exactly that and went
   green against the unfixed bug.
3. **`GlobalExceptionHandler` maps `IllegalArgumentException` AND `IllegalStateException` to 400.**
   Only the catch-all `@ExceptionHandler(Exception.class)` produces a 500. So for any remaining
   "wrong status" item: to make something a 400 use `IllegalStateException`, and to make something a
   500 a bare `RuntimeException` is the only route without adding a new exception type. Do not assume
   `IllegalStateException` means "server broke" here.
4. **Line numbers in this doc are from the `8c28cf3` baseline and drift as MRs land.** Find symbols
   by name. Re-verify a `file:line` before quoting it as evidence in a PR description.
5. **An item that is nothing but a list of line numbers is already dead.** Rule 4 says line refs
   drift; the corollary is that any item whose entire content is line refs cannot be recovered by
   "find it by name", because there is no name -- only a position. Wave 4 is built this way and was
   measured 64% stale before it started (see MR 12's prep note). For any such item, re-derive the
   list mechanically from the current tree and treat the doc's list as a sample of intent, not a
   worklist. Check this before estimating any remaining Wave 4 MR.
6. **In a comments-only MR, leave trailing comments (`code; // note`) alone.** Learned twice in MR 12,
   for two different reasons, which is why it is a rule and not a preference. In 12a, removing the
   trailing comment from `updateTags(currentTags, tagUpdate, null // ...)` would let
   google-java-format collapse the call onto one line -- a genuine code reflow. In 12b the code text
   would have stayed byte-identical, but the line still shows in `git diff` as a modified code line,
   which defeats the one thing that makes this MR class cheap to review: `git diff | grep` for
   anything that is not a comment. Trailing comments belong to whichever MR touches that code for
   real. Corollary: removing a comment block that sits at the END of a method body forces removal of
   the blank line above it, because spotless drops the dangling blank before the closing brace. That
   is unavoidable -- name it in the PR rather than claiming a perfectly pure diff.
7. **A stale comment is not automatically a bug report.** The Wave 4 guardrail says a comment
   contradicting its code is a bug someone wrote down and forgot, and that is sometimes true -- bug
   #16 was found exactly that way. But across MR 12's three files, 349 comments produced exactly one
   real bug. `CollectionService` produced none: every checkable claim held under verification.
   Reporting "none found" is the correct outcome for a file, not evidence of a shallow pass. Verify
   the claim against the code before filing; do not manufacture a finding to satisfy the guardrail.

8. **A `P:` judgment note decays the same way a `D:` line number does.** Rule 5 warns about
   coordinates; MR 13a found the *instructions* rotting too. Two of its `P:` entries told a future
   session to fix things that had since been fixed -- "229 (fix wrongness with bug #10)" when bug
   #10 was already closed and the comment already correct, and "218-219 (fix staleness: RAW
   scheduling moved)" when the comment already said exactly that. Following either would have meant
   editing correct text to reintroduce a problem. Before acting on a `P:` note that asserts
   something is stale or wrong, verify the claim against the current code -- the note is as old as
   the line number next to it.
9. **Commit with explicit paths, never `git add -A`.** This repo carries untracked review docs in
   `ai_docs/reviews/`. MR 12c's commit used `git add -A` and swept
   `ai_docs/reviews/2026-07-25-open-pr-review.md` (321 lines, untracked since before the session)
   into PR #180, which merged with it. The PR's diff stopped matching its description, which is
   exactly the property that makes a comments-only MR cheap to review. Stage the files the MR names
   and nothing else.

   **Do not "fix" this by deleting that file.** Appendix B is a scorecard written against it, and
   that citation predates the sweep -- so the tracker had been depending on a doc that existed only
   on one machine. Committing it was an accident that corrected a real fragility. The defect was the
   undisclosed diff, not the file's presence, and the rule above is about staging discipline, not
   about this file.

10. **Promotion inflates. Measure a comment MR in words, not lines, and diff it against what the
    docblock already says.** MR 13b removed 37 inline comments (422 words of prose) and added 600
    words of javadoc -- **+42% prose in an MR called "debloat"** -- and nobody noticed from the diff
    stat, because `+65/-45` looks near-neutral once javadoc's `/**`, ` * ` and `<p>` overhead is
    counted as content. Line count hides prose growth; word count does not. Three causes, all
    avoidable, all caught in review (MR 13c):
    - **The same rule written into three docblocks.** The EXIF-over-XMP precedence went into
      `extractFromStream`, `extractFromExifTag` AND `extractFromXmpDirectory`. This is exactly the
      failure MR 12a already caught with the CDN invalidation comment -- three copies, fixed by
      putting it in one class-level sentence. State a rule once, where it is enforced.
    - **A dense existing docblock deleted and re-expanded.** `extractTagsAndPeopleFromXmp` had an
      accurate 3-line summary; it was replaced with 11 lines across three paragraphs saying the same
      thing plus examples. Promoting into a docblock does not mean rewriting the docblock.
    - **A fact promoted that the caller already documented.** `writeZipEntries` got the `.error.txt`
      placeholder rationale, which `zipToS3AndPresign`'s docblock one method above already stated.

    So before promoting: read the docblock you are promoting INTO, and the one on its nearest public
    caller. If either already says it, the comment is a delete, not a promote. Then check the MR in
    words. 13b+13c combined is -4 lines and -14 words with all 37 comments gone, which is what a
    debloat should look like; 13b alone was not.

11. **Outcome write-ups go in the history file, not the tracker.** The tracker grew from the
    original review to 1,729 lines, and 1,312 of those (76%) were closed-out detail: Waves 1-3 in
    full, MR 12's and MR 13's worklists, outcomes, guardrails and costed write-ups, and a 107-line
    session log. Each doc MR was adding around 110 lines to a file whose live content is about 400.
    A tracker you have to skim past three finished waves to reach the open item is not a tracker.

    So: when an MR closes, the tracker gets one line -- status, PR link, and any working rule the MR
    taught. Everything else (the worklist it re-derived, the measurements, the guardrail it wrote,
    the report a guardrail asked for) goes to
    [`2026-08-22-backend-cleanup-history.md`](2026-08-22-backend-cleanup-history.md). Nothing is
    deleted; it is one link away when the evidence is actually wanted.

    The split also caught what the bloat was hiding. Waves 1-3 read "complete" but held fourteen
    unticked boxes. Six were done and never ticked (now ticked in the history file); **eight were
    live work**, including a Wave 3 security residual (the chunked-body cap bypass) and a deletion
    whose stated precondition turned out to be false. They are in "Carried forward" above, as five
    entries -- four of the eight are the same MR 25 deferral. Reconcile on the way out, not on the
    way in.

12. **Promote a fact about the method; keep a warning about a line.** Rule 10 said measure before
    promoting. MR 14 measured, and the answer changed what the wave rule should be. Promoting
    `SecurityConfig`'s 24 comments into one `filterChain` docblock costs **+24 words (+9%)** even in
    a careful draft that keeps every fact and states each rule once -- and the entire overhead is
    anchor-naming, because a docblock has to write "on `/api/auth/me`, `/api/auth/logout`, ..." for
    what an inline comment gets free from its position.

    Worse, the comments that most deserve to survive are the ones a docblock cannot hold:
    `RoleRepository`'s `\s` notes sit against the text block whose trailing `\s` they protect;
    `AdminBootstrap`'s "do not fix this into a single statement" has to be next to the two
    statements; `CollectionControllerProd` explains a `Cache-Control` call that is deliberately
    *absent*. Move any of those to a docblock and the next editor changing the line never reads it.

    So the test is not "is this inside a method body" but **what is this comment about**. A fact
    about the whole method promotes cleanly -- a no-op override, a result ordering, a swallowed
    failure. A warning attached to one line stays on that line. A comment that restates its code, or
    that the docblock already carries, is a delete. MR 14's 93 split 19 / 66 / 7 that way.

    This also explains why MR 12 and MR 13 swept so cleanly and MR 14 did not. Those files were
    narrated; these were hardened by Waves 1-3, so their comments were written on purpose. **Check
    which kind of file you are in before assuming the wave rule applies.**

    **Corollary, learned the hard way in MR 15 #2: this rule licenses keeping a comment, not
    writing a long one.** That MR added an 8-line block to `SecurityConfig` and was called out for
    it -- fairly. The rule permitted a comment there (a line-anchored warning: do not move this
    matcher inside the toggle), but only about three lines of the eight carried it. The rest
    enumerated the routes, which restates the `/api/read/user/**` pattern sitting on the next line,
    and re-explained the `hasRole` versus `authenticated()` flyby rationale that the comment FOUR
    LINES ABOVE already gave at length. That is working rule 10's "a fact the caller already
    documented", inside a single method. So when adding a comment to a hardened file: read the
    neighbouring comments first, delete anything they already say, and write only the one fact that
    cannot be recovered from the code. In a repo that just removed 500 comments, a new one has to
    earn each line.

13. **A guardrail decays like a line number, and a re-derivation is not self-verifying.** Rule 8
    said a `P:` note rots the same way a `D:` coordinate does. MR 15 #2 found both failure modes at
    once, in the freshest content on the board.

    The guardrail -- written the day before, costing three placements and naming one "the honest
    default" -- rested on a premise that was never checked against the code: that placing the
    matcher outside the dev toggle would cost local dev a login-free convenience. **Those routes
    were never login-free.** The 17 guards it was replacing had no profile check, so dev already
    401'd. Two of the three options it presented were behavior *changes* and only one preserved
    behavior; the decision it framed as a tradeoff did not exist. A guardrail that reasons from
    "SecurityConfig already does X for `/api/edit/**`" has to check whether the thing being replaced
    is conditional in the same way. It also missed that the chain already had the right precedent
    sitting four lines above -- the unconditional `hasRole("USER")` on `/api/auth/me`.

    And the re-derivation, which existed precisely to fix stale refs, introduced one of its own: it
    raised the count 17 -> 18 by grepping `isRealUser` and counting a javadoc line that says the
    word. It fixed two real drifts and added one miscount, so its net accuracy was better but not
    clean. **Re-derived facts need the same verification as the facts they replace** -- a grep is
    evidence of a string, not of a guard. Confirm a count by making the change and watching it come
    out even, not by trusting the tally that motivated it.

14. **Re-derive a duplication item by its code shape, not its helper name.** Rule 13 said a
    re-derivation needs its own verification. MR 15 #6 shows what that verification has to look
    for. The item's re-derived table was accurate -- four declarations, seven call sites, all
    confirmed before editing and even at the end -- and it was still incomplete, because it was
    built by grepping the name `currentUserId`.

    Grepping the *body* instead (`SecurityContextHolder.getContext().getAuthentication()`) finds
    two more copies of the same read under different names:
    `SyntheticCollectionResolver.currentPrincipal` is byte-identical but returns the principal, and
    `CollectionService.viewerMaySeeHidden` inlines it with an extra null check. A copy that was
    renamed, or inlined, or that returns one field more, is still a copy -- and it is the one a
    name-grep will never surface. Consolidation items are about duplicated *code*, so the
    re-derivation has to be keyed on the code.

    The corollary for counts: "four copies" and "four helpers named X" are different claims. Say
    which one the number is.

## Ordering note

The original review put bug fixes first so deletions would rebase cleanly. We inverted that and started with deletions, because they are compiler-verified and carry no behavior change. The bug MRs rebase onto the deletions instead. Only one item actually collided, and it was handled: `PersonRepository.deleteById` was listed under both MR 1 and bug #1, and was held until MR 5 because it had a live caller -- dangerous code, not dead code. It shipped with MR 5 and is gone.

---

# Wave 4 — Comments and docs

Rule: no `//` comments inside method bodies in main code. Delete, or promote to a docblock. D = delete, P = promote then delete. Class-level section banners (`// ====`) are outside method bodies and are out of scope.

**Measured 2026-08-23, not estimated.** At the start of the wave `src/main` held 684 lines beginning
with `//`, of which **567 sat at indent >= 4** -- inside method bodies, which is what this rule
targets. The review's "~370 occurrences" premise was low by about 53%. MR 12 and MR 13 cleared 474
of them. MR 14 dispositioned the last 93 and kept 66 by decision (working rule 12), so 67 remain
in `src/main`. MR 12's and MR 13's worklists, outcomes and guardrails are in
the [history file](2026-08-22-backend-cleanup-history.md#wave-4--mr-12-and-mr-13-complete).

## Wave 4 retro — measured in words, 2026-08-23

Prompted by a fair challenge: the MRs kept being called "debloat" while the diffs looked
net-positive. Both halves of that turned out to matter, and only one was a real problem.

The stats quoted in these PRs were commit-level, which mixes this tracker's running log in with the
code. 13b's headline `+161/-53` was 88 lines of doc and +20 of Java. **Quote Java-only stats for a
code MR.** Then, separately, the Java itself has to be measured in words, because javadoc's `/**`,
` * ` and `<p>` scaffolding counts as content and hides prose growth in the line count:

| MR | Java lines | words of prose | |
|---|---|---|---|
| 12a ([#177](https://github.com/themancalledzac/edens.zac.backend/pull/177)) | -25 | **-378** | |
| 12b ([#178](https://github.com/themancalledzac/edens.zac.backend/pull/178)) | -37 | **-301** | |
| 12c ([#180](https://github.com/themancalledzac/edens.zac.backend/pull/180)) | -1 | **-207** | |
| 13a ([#181](https://github.com/themancalledzac/edens.zac.backend/pull/181)) | +2 | **-75** | line count up, prose down |
| 13b ([#183](https://github.com/themancalledzac/edens.zac.backend/pull/183)) | +20 | **+178** | the only inflation |
| 13c ([#184](https://github.com/themancalledzac/edens.zac.backend/pull/184)) | -24 | **-192** | the correction |
| 14 ([#187](https://github.com/themancalledzac/edens.zac.backend/pull/187)) | +4 | **-51** | lines up, prose down -- the 13a pattern again |
| **Wave 4** | **-61** | **-1,026** | 500 in-method comments removed; 67 remain, 66 by decision |

So the trend was sound and 13b was the outlier, now corrected. Two things worth carrying forward.
13a shows the divergence without the defect -- **+2 lines but -75 words** -- which is proof the line
count is simply the wrong instrument, not just that 13b was sloppy. And 12a/12b, the two biggest
sweeps, produced the two biggest prose reductions, so scale is not what causes inflation. Care is.
Working rule 10 has the check.

## MR 14 — Comment debloat: controllers, config, data layer — DONE ([PR #187](https://github.com/themancalledzac/edens.zac.backend/pull/187))

93 in-method comments re-derived on `51fede9` and dispositioned: **7 deleted, 19 promoted, 66 kept
inline, 1 quarantined.** Java-only **+4 lines / -51 words** -- the 13a pattern, line count up and
prose down. Seven files went to zero; 20 files became 13. Full write-up in the
[history file](2026-08-22-backend-cleanup-history.md#mr-14-outcome-2026-08-23).

The headline: **the wave rule did not fit this population**, and that is now working rule 12. Waves
1-3 hardened exactly the `controller/`, `config/` and `dao/` files MR 14 targets, so most of these
93 were written deliberately by this cleanup effort and guard a specific line rather than narrating
the code. Only 7 restated the code they sat on.

`SecurityConfig`'s 24 were measured as the guardrail asked, and kept: promoting them into one
`filterChain` docblock costs **+24 words (+9%)** even in a careful draft that states each rule once.
The entire overhead is anchor-naming -- a docblock has to write "on `/api/auth/me`,
`/api/auth/logout`, ..." for what position gives an inline comment free.

**Bug #16 is still real.** Verified against current `updateImages`: `ContentRepository.saveImage` is
still a single-row INSERT/UPDATE, the loop still calls it once per image, and the log line still
reads "Batch saved {}". The only `batchUpdate` in `ContentRepository` is in `saveContentPeople`, a
different table. So N image edits still issue N statements. `ContentService:227` stays quarantined
until the MR that adds a real batch save and fixes the log line with it.

Two doc claims shipped, both re-verified first. `CollectionVisibility`'s docblock said
`password_hash`; V18 renamed the column to `gallery_password`. `.claude/CLAUDE.md` claimed
`controller/prod/` is `@Profile("prod")` gated; it is not. On that second one the tracker was right
and a naive grep is not -- the only two `@Profile` hits under `controller/` are javadoc text saying
there is no such gating.

### Still open from MR 14 — stale docblocks

Out of scope here by design: this MR was in-method comment lines only. These are docblock rewrites,
and each needs its claim verified before acting (working rule 8).

- [ ] `filterNonListedChildCollections` (`CollectionService`) describes a context-detection mode that no longer exists.
- [ ] The "previously spread across ContentProcessingUtil" rename-history at `ContentModelConverter` and `ContentMutationUtil` -- that class is gone.
- [ ] "PARENT-shaped" vocabulary at `CollectionService`, `TagViewResolver`, `UserPageAssembler` -- dead since the enum deletion.
- [ ] `CollectionAccessService.effectiveLevel` overclaims: it says `canView`/`isClient`/`hasAtLeast` all resolve through `effectiveLevel`'s GENERAL ceiling. `canView` and `isClient` hit the repository directly and are only safe because flyby principals carry a null userId, which nothing documents or asserts. Five affected call sites and the full cost are in the history file's MR 12c outcome. A Wave 3 follow-up, not a comment sweep.

---

# Wave 5 — Consolidations

## MR 15 — Cross-cutting

Consolidation #1 (one client-IP resolver) ships with bug #3 in MR 5.

- [x] #2. One SecurityConfig matcher instead of the copy-pasted `isRealUser` guards. **DONE** ([#189](https://github.com/themancalledzac/edens.zac.backend/pull/189)). **17 guards, not 18** -- the re-derivation counted a javadoc line in `UserShareControllerProd`. The matcher went OUTSIDE the enforce-authz toggle, next to `/api/auth/me`: the guards it replaced were unconditional, so that is the only behavior-preserving placement, and the guardrail's "costs a dev convenience" was false -- dev already required a session on these routes. A flyby now gets 403 rather than 401 there, by decision. Java-only main -42; 28 controller-level assertions became `config/UserRoutesAuthorizationWebMvcTest`. [Full write-up](2026-08-22-backend-cleanup-history.md#mr-15-2-outcome-2026-08-23).
- [x] #6. `currentUserId` is duplicated. **DONE.** Four copies became `config/CurrentUser.userId()`, joining `ClientIp` and `GalleryAccessCookies` as a static helper next to the security plumbing. The item's "move it onto `AuthPrincipal`" does not work -- that is a Spring-free record and this is a static context read. The null contract was left alone and costed instead: the four admin sites break local dev only, the two read-surface sites 500 a logged-out visitor, so it is two problems and not one. Java-only main -26 lines / +36 words. Two more copies of the same read were found and deliberately not folded in (`SyntheticCollectionResolver.currentPrincipal`, `CollectionService.viewerMaySeeHidden`) -- see rule 14. [Full write-up](2026-08-22-backend-cleanup-history.md#mr-15-6-outcome-2026-08-24).

### The MR 15 #6 follow-up, left open on purpose

- [ ] Fold the last two copies of the same static read into `CurrentUser`: add
  `CurrentUser.principal()`, have `userId()` delegate, and rewrite
  `SyntheticCollectionResolver.currentPrincipal` (identical body, returns the principal) and
  `CollectionService.viewerMaySeeHidden` (the read inlined, plus a `p.userId() != null` check
  because it passes the whole principal to `hasAtLeast`). Mechanical and behavior-preserving. Out
  of MR 15 #6's scope because the item said four.

  Already checked, do not re-flag: grepping the read shape
  (`getContext().getAuthentication()`) across `src/main` returns six sites. `CurrentUser` is the
  consolidated one, these two are the follow-up, and the remaining three are genuinely different
  uses -- `CollaboratorAccessInterceptor` resolves an access level, `FlybySessionFilter` tests
  whether an authentication already exists, and `AuthController` serves `/api/auth/me`. None of
  the three extracts a user id.

## MR 16 — Infrastructure classes

- [ ] #3. One keyed rate limiter instead of two. `config/ContactMessageLimiter.java` and `config/ClientGalleryAccessLimiter.java` are the same Caffeine+Bucket4j class twice, and their TTL policies have already drifted. Consolidate into `KeyedRateLimiter(capacity, window, idleTtl)` instantiated as two named beans. Keep `AuthLoginLimiter` separate — its semantics genuinely differ.
- [ ] #4. One AWS config class. `config/SesConfig.java` duplicates S3Config's credentials plumbing and borrows `aws.s3.region` for a non-S3 client. Merge the SesV2Client bean into S3Config (rename it `AwsClientConfig`), share one `AwsCredentialsProvider` bean across the four clients, and delete the catch-log-rethrow blocks. ~40 lines.
- [ ] #5. One CloudFront invalidation implementation. `services/ImageProcessingService.java:838-863` re-implements what `services/ReadCacheInvalidator.java:79-106` already owns. Give `ReadCacheInvalidator` an `invalidatePaths(List<String>)` and delegate. ~25 lines.

## MR 17 — Controllers

- [ ] #7. Admin image list duplicates the prod image search — same 12 `@RequestParam`s, same service call, different response wrapper (`AdminController.java:255-291` vs `ContentControllerProd.java:45-77`). Bind the filter once with a shared `@ModelAttribute` record, reuse prod's constraints, return one response type. ~40 lines.
- [ ] #8. Role membership is writable from two endpoint pairs backed by the same repository calls (`PUT`/`DELETE /api/admin/users/{id}/roles/{roleId}` in `AdminUserController:333-350` vs `PUT`/`DELETE /api/admin/roles/{roleId}/members/{userId}` in `AdminRoleController:150-167`). Keep the roles-side pair. Confirm which one the frontend uses before deleting. ~34 lines.

## MR 18 — Services

- [ ] #9. The from-disk and ingest background loops are ~70 lines of copy-paste (`ImageUploadPipelineService.java:279-392` vs `405-524`), including a CREATE/UPDATE switch the ingest loop already merged. One shared loop with a `(fileEntry, prepared) -> collectionId` resolver. ~85 lines with the fold.
- [ ] #10. `updateGif` reimplements the tag/people/location merge blocks that `ContentMutationUtil` already owns as `updateImage*Optimized` (`ContentService.java:581-653` vs `ContentMutationUtil.java:199-259`). The helpers only use the content id; retype them and call from both paths. ~40 lines.
- [ ] #11. Four near-identical BFS walks: `RoleGrantPropagationService.java:168-223` (three) plus `CollectionService.java:460-490` (`validateNoLinkCycle` and a byte-identical `parentIdsOf`). One `walk(root, neighborsFn)` helper. ~30 lines.
- [ ] #12. `nextOrderIndex` logic in four places (`ContentService:490-493`, `ContentMutationUtil:186-189`, `CollectionService:394-395` and `1067-1072`). One helper.
- [ ] #13. Entity-to-Record mapping and case-insensitive sort duplicated across four files (`Records.Tag` mapping at `ContentModelConverter:343`, `MetadataService:434-436`, `SyntheticCollectionResolver:153`, `ContentService:1025`; Location mapping/sort twice). Static `from(entity)` factories on the records. ~15 lines.

## MR 19 — Query efficiency and data layer

- [ ] #14. `convertEntityToModel` loads the same content row twice (`ContentModelConverter.java:103-118`) — `findAllByIds` already returns typed subclasses, so drop the second typed fetch. Verify COLLECTION hydration first. Called 3x per GIF/text mutation.
- [ ] #15. `getUpdateCollectionData` fetches the collection row twice and has an always-true null check (`CollectionService.java:822-848`).
- [ ] #16. `findCurrentContentCollections` is an N+1 loop, one query per join entry (`CollectionService.java:1326-1388`). Batch-load and filter in memory.
- [ ] #17. Smaller items: `UserInviteService.validate`/`redeem` duplicate token resolution (85-130, into `findLiveInvite`); pagination normalization re-inlined at `CollectionService:127-130` (call `PaginationUtil`); `toEntity`'s `defaultPageSize` parameter and `applyPaginationDefaults` are redundant with each other (`CollectionProcessingUtil:569-596, 939-947`); `uploadToS3`/`streamFileToS3` duplicate key and URL construction (`ImageProcessingService:697-745`); `ensureDimensions` twins (`ImageMetadataExtractor`: `ensureDimensions` 364, `ensureDimensionsFromPath` 378 -- the doc's `318-340` is stale); the EXIF-versus-ISO format detection duplicated between `parseImageDate` (428) and `parseExifDateToLocalDateTime` (467) -- the doc's `350-371` is stale and points at `recordKeywordFlags`; EmailService HTML skeleton twice (157-243, optional).
- [ ] #18. `EquipmentRepository` repeats each SELECT column list 6+ times while sibling repositories hoist constants (`AppUserRepository`, `ShareLinkRepository`, `WebAuthnCredentialRepository`, `CollectionRepository` all do it right). Hoist per-entity constants. ~25 lines.
- [ ] #19. `model/ImageSearchResponse.java` is a strict subset of `model/PagedResponse.java`. Replace it with `PagedResponse<ContentModels.Image>` unless the wire contract must not grow keys.
- [ ] #20. `Records.FilmFormat` (DTO) shadows the `FilmFormat` enum, forcing a fully-qualified name at `Records.java:23` and duplicating the mapping at `ContentControllerProd:147-149` and `CollectionService:912-914`. Rename the record `FilmFormatOption`, import the enum, one static factory.

---

# Wave 6 — Conventions

## MR 20 — The bare-array decision (breaking; coordinate with the frontend)

- [ ] Decide first. 15 endpoints return top-level JSON arrays against the stated "objects only" rule: `AdminController:85`; `AdminUserController:150, 318, 373, 386`; `AdminRoleController:49`; `CollectionAdminController:43`; `ContentControllerProd:85, 96, 107, 118, 130`; `UserFollowsControllerProd:58`; `UserSavesControllerProd:56, 65`; `UserSelectsControllerProd:59`; `UserRatingOverrideControllerProd:58`. `CollectionAdminController:37` even documents the violation as policy. Either wrap them in one breaking-change MR, or amend `.claude/CLAUDE.md` to bless bare arrays. Today the codebase carries two contradictory conventions.

## MR 21 — Untyped Map bodies and responses

- [ ] Admin write surface: `AdminController:296` (`Map<String, List<Long>>`, no `@Valid`), `:478`/`:487`/`:496` (rename endpoints — a body without `"name"` passes null into the service), `:235`/`:295`/`:352`/`:403`/`:412`/`:421`/`:438` (Map responses, one needing `@SuppressWarnings` to cast its own service's map back); `WebAuthnController:140`; `EditController:97`; `ContentControllerProd:144`; `CollectionControllerProd:173`. Introduce small records (`RenameRequest(@NotBlank String name)`, `DeleteImagesRequest(@NotEmpty List<Long>)`, and so on).

## MR 22 — Remaining convention sweeps

- [ ] `ResponseEntity<?>` twice: `UserSelectsControllerProd:59` (serves two different shapes from one GET — split or wrap) and `MessagesControllerPublic:43` (throw a `RateLimitedException` handled globally, which also unifies the three different 429 body shapes currently in play: empty at `AuthController:65`, Map at `CollectionControllerProd:182-183`, ErrorResponse at `MessagesControllerPublic:48-52`).
- [ ] Try-catch in controllers, three sites: `AdminUserController:402-409, 427-433` (map via `ResourceNotFoundException` plus a new `ConflictException` handler). The third dies with bug #15 in MR 7.
- [ ] `@Value` field injection: `CollectionControllerProd:55-56`, `ShareControllerProd:45-46`, `DownloadUrlService:54-55` — move to constructor parameters, following the `WebAuthnController` pattern. Also `@Autowired` on constructors at `AuthLoginLimiter:17`, `ClientGalleryAccessLimiter:31`, `WebAuthnChallengeStore:25`, `WebAuthnService:70` — remove where Spring can pick the constructor unaided, and document the exception where it cannot (two-constructor classes).
- [ ] Fully qualified names inline: `CollectionService:533` (`jakarta.servlet.http.HttpServletRequest` -- the doc's `542` is stale, corrected 2026-08-24; it is the `isGalleryAccessAuthorized` signature, which MR 15 #6 also touches), `CollectionProcessingUtil:828`, `TagViewResolver:115`, `GalleryAccessCookies:33-34`, `ContactMessageLimiter:40`, `Records.java:23` (root-caused by the name clash, consolidation #20).
- [ ] `Optional.get()` x17, all guarded, all rule violations: `CollectionService:118, 122, 1157`; `ContentModelConverter:111`; `TagViewResolver:55`; `ContentMutationUtil:79, 298, 327, 435, 493`; `UserInviteService:94, 123`; `MetadataService:87, 156, 213, 348, 396`. Rewrite opportunistically when touching these methods.
- [ ] Magic number 2500 at both resize call sites (`ImageProcessingService:192, 292`). Name it.
- [ ] `JobStatus.status` is a stringly-typed field with its states in a trailing comment (`JobTrackingService:27`). Make it an enum. Consider COMPLETED_WITH_ERRORS instead of flipping a 500-file job to FAILED over one error (83-85).
- [ ] Verb-style routes `POST /collections/createCollection` (`AdminController:107`) and `POST /content/content` (`:220`). Alias noun routes, retire after the frontend moves.
- [ ] Route the gallery-access save failure through an exception instead of a `saved()` boolean with a hand-built 400 (`CollectionAdminController:56-63`).

---

# Wave 7 — Structure

## MR 23 — Package moves (rename-only)

- [ ] `controller/user/` is a one-class package (`UserRatingOverrideControllerProd`) that belongs with its five siblings in `controller/prod/`.
- [ ] Request records have two homes: `RoleRequests`/`UserRequests` (`controller/admin/`) and `InviteRequests` (`controller/auth/`) versus `MessageRequests`/`CollectionRequests`/`ContentRequests`/`CollaboratorRequests` (`model/`). Move the three strays into `model/`.
- [ ] Optional: drop the `*Prod` suffix now that no controller carries `@Profile`.

## MR 24 — Service extraction and remaining design items

- [ ] `AdminUserController` is a service wearing a controller's clothes: two repositories and five services injected, entity building, multi-step `@Transactional` orchestration, afterCommit hooks (110-138, 202-237, 270-308, 436-469). Extract an `AdminUserService`.
- [ ] Same shape, smaller: `UserShareControllerProd:124-152` computes grant and candidate sets inline with a repository. Move it into `ShareLinkService`.
- [ ] `Synthetic.blogsOnly` is a constant at its only reachable call site (`SyntheticCollectionResolver:42-49, 86-92`), a transitional shape from the type-keyed catalog. Fold it out.
- [ ] `MessageService` is a pure pass-through with a speculative docblock. Keep it for layering or delete it, but drop the justification.
- [ ] The validator components (`MetadataValidator` is four copies of a 3-line null check as a Spring bean; `ContentValidator` is similar) are the "unnecessary utility classes" CLAUDE.md bans. Replace with bean validation on the DTOs when next touched (~60 lines eventual).
- [ ] Executor handling in `ImageUploadPipelineService` (73-94): `rawUploadExecutor` now runs whole disk and ingest jobs, so it is misnamed and mis-documented, and shutdown awaits one executor but not the other. Rename it, fix it, await both.
- [ ] `AdminHomeService`'s AtomicReference cache has no TTL and is per-instance. Fine single-node; note it for any multi-instance future.
- [ ] Service decomposition, the standing item. The March TODO targeted `CollectionService` at 1,161 lines; it is now 1,749 (`ContentService` 1,045, `ImageProcessingService` 1,365, `CollectionProcessingUtil` 948). Waves 5-7 shrink these, but the trend line is the real problem. Decide the split boundaries before the next feature lands in them.

---

# Wave 8 — Tests

## MR 25 — Shared fixtures and consolidation

- [ ] `new ContentModels.Image(` with 25+ positional nulls appears in 12 test files, each with its own private helper. Same for the 22-arg `CollectionRequests.Update`. One `TestFixtures` class with builders. Every record-shape change currently touches ~12 files. ~300 lines net.
- [ ] `services/CollectionServiceTest.java` (2,412 lines): assert/verify twins where the second test re-runs the first's stubbing and re-checks with `verify` — for example `createCollection_happyPath...` (:128) versus `createCollection_verifiesEntityCreatedViaUtil` (:157); the `deleteCollection` plain-verify test (:188) is a strict subset of the inOrder version (:216). ~250 lines.
- [ ] The four typeless-migration integration tests (V50Backfill 188, V51Prep 282, V52Drop 72, TypelessRead 113) each boot containers to prove transition states that shipped in March and cannot regress. Consolidate into one end-state IT; keep `CollectionTypeAbsentFromWireTest` as the wire guard. ~400 lines.
- [ ] `ImageUploadPipelineServiceTest`'s 1:1 verify ratio suggests some verify-only tests worth a pass.

### Main-dead, test-live constructors (carried from MR 1a)

These have zero `src/main` callers but many in test, so deleting them rewrites working call sites to
pass explicit nulls. Do them in the SAME pass as the `TestFixtures` builders above, which collapse
the same call sites -- done separately, the two changes fight each other.

- [ ] `model/CollectionRequests.java` — 17-arg `Update` constructor, 23 test call sites.
- [ ] `model/DiskUploadRequest.java` — 3-arg `FileEntry` constructor, ~20 test call sites.
- [ ] `model/AuthPrincipal.java` — 4-arg constructor, 28 test call sites plus `SessionService`. Weigh whether appending `, null` at 29 sites is an improvement; it may not be. Leaving it is a legitimate outcome.
- [ ] `services/ContentService.java` — `resolveCollectionDownloadEntries` 2-arg overload, 5 test call sites.
- [ ] `model/DownloadResolution.java` — the `extension` component: 4 construction sites and 6 assertions in test, never read in main. Also rewrite the stale docblock (downloads are presigned redirects, they do not "stream the response").

## MR 26 — Coverage gaps

These are worth more than the bloat they replace.

- [ ] `TokenUtil` — zero direct tests for the CSPRNG/SHA-256 code underlying every invite and share link.
- [ ] `SlugUtil` — zero tests; collisions and normalization are user-facing.
- [ ] `PaginationUtil` — zero tests; an off-by-one corrupts every paged read.
- [ ] `UserFollowsService` — mocked in its controller test, uncovered itself.
- [ ] The validators (`MetadataValidator`, `ContentImageUpdateValidator`) — 1-2 incidental references; they gate admin writes.

Verified good, for the record: `AdminUserControllerTest` is real behavior testing; the auth-table truncation fix landed in `AbstractPostgresIntegrationTest`; no tests mock the deleted `collection.type` shape.

---

## Session log

One line per session. Three entries in a row ending `Next: X` means X is being avoided -- say so and
either make it real work or drop it. The verbose pre-split log is in the
[history file](2026-08-22-backend-cleanup-history.md).

- 2026-08-23 — shipped MR 14 ([#187](https://github.com/themancalledzac/edens.zac.backend/pull/187)) and the tracker/history split ([#186](https://github.com/themancalledzac/edens.zac.backend/pull/186)); merged [#185](https://github.com/themancalledzac/edens.zac.backend/pull/185). Added working rules 11 and 12. Reconciled Waves 1-3 and surfaced 8 live items from "complete" waves. Re-derived MR 15 #2 (17 guards -> 18, one controller in the wrong package). Next: MR 15 #2.
- 2026-08-23 — shipped MR 15 #2 ([#189](https://github.com/themancalledzac/edens.zac.backend/pull/189)); merged [#188](https://github.com/themancalledzac/edens.zac.backend/pull/188). One matcher replaced **17** guards -- yesterday's re-derivation had counted a javadoc line, and its guardrail's dev-convenience premise was false, so the placement decision it framed as a tradeoff had only one behavior-preserving answer. Added working rule 13. Wave 5's first item is closed. Next: MR 15 #6 (`currentUserId` onto `AuthPrincipal`).
- 2026-08-24 — verified [#189](https://github.com/themancalledzac/edens.zac.backend/pull/189) merged (`9d15784`). Re-derived MR 15 #6 and it is **four copies, not three** -- `CollectionService:549` was missed entirely and is not a controller, and the item's stated fix (put it on `AuthPrincipal`) does not work, because that is a Spring-free record and this is a static context read. Wrote its guardrail: both null returns are load-bearing, for two different reasons. Corrected a stale MR 22 ref (`CollectionService:542` -> `533`) found on the way past. Added the working-rule-12 corollary on writing comments, after MR 15 #2 over-wrote one. Next: MR 15 #6.
- 2026-08-24 — shipped MR 15 #6: four `currentUserId` copies became `config/CurrentUser.userId()`
  (main -26 lines / +36 words), and **closed the `PersonRepository.findAccountUserIdsByIds` carry**
  rather than let it reach a fifth. That one was decided, not deferred: zero callers in main *and*
  test, so its "only accounts get grants" rule was unenforced; the method was the wrong shape (a
  `List` query for two single-id call sites), so it was deleted and the rule enforced at
  `RoleRepository.addMember`, the choke point both admin controllers share. Low severity and said
  so -- admin-only routes, and a PERSON row cannot log in; the risk is a dormant grant. The item's
  "move it onto `AuthPrincipal`" was rejected with a reason. The null contract was left alone and
  costed instead, and it turns out to be two separate problems, not one. Added working rule 14
  after finding two more copies of the same read that a name-grep could not see. 1304 tests green.
  Next: MR 16.

---

# Decisions needed from the user

- [ ] Bare-array responses: wrap them (breaking) or amend CLAUDE.md (MR 20).
- [ ] Gallery passwords: accept plaintext-at-rest formally, or redesign the fingerprint feature (MR 10).
- [x] SpotBugs: decided — delete all four artifacts. Done in MR 2. If static analysis is wanted
  later, introduce it fresh at a current version with a filter written from scratch.
- [ ] `admin_home_tile.cover_image_id`: drop in a migration or document as reserved (MR 1, deferred).
- [ ] `role.kind` is written as constant 'SHARED' (`RoleRepository:94`) and read by nothing. Likely droppable, but roles are security-sensitive.
- [ ] Unknown-JSON-key policy: the prior review asked for a recorded decision (C8); none exists.
- [ ] Partial indexes on `is_blog`/`is_client` (C7, "if scale demands") — check request metrics.

---

# Appendix A — Cross-repo verification (highest value)

The BFF verification pass in the frontend repo determines how much the backend findings above actually matter in prod.

- [ ] Does the BFF strip inbound client-supplied `X-Real-IP` before setting its own? Every limiter keys on it, including login brute-force.
- [ ] Does it restrict which paths get the internal secret attached?
- [ ] Does anything cap request body size?

# Appendix B — Prior-review scorecard

Against `ai_docs/reviews/2026-07-25-open-pr-review.md`, backend items: 25 landed, 10 moot (superseded by the typeless phase-2 merge), 2 partial, 3 still open.

- Still open: C7 partial indexes on `is_blog`/`is_client` (explicitly optional); C8 fail-on-unknown-JSON-key policy (a recorded decision was asked for; none exists); `CollectionControllerDevTest` naming drift (folded into MR 4).
- Partial: `CollectionList.fromSibling` exists, but two positional construction sites remain (`CollectionRepository:630`, `CollectionService:873`).
- Everything else verified landed (all-blogs on `is_blog`, no-flags INFO log, `@Valid` on `TagAdminController`, typeFilter deleted, column-list dedup, flag-triplication gone, the 1.6 test adds, the 1.7 comment fixes) or mooted by V51/V52.

# Appendix C — Unverified leads

Worth a targeted check; not asserted as findings.

- [ ] Possibly-dead endpoints: `GET /api/admin/collections/metadata` (`AdminController:152`), one of the two role-membership pairs (consolidation #8), ids-only `GET /api/read/user/saves` (`UserSavesControllerProd:56`). Confirm frontend usage before deleting.
- [ ] `role.kind` written as constant 'SHARED' (`RoleRepository:94`), read by nothing.
- [x] `PersonRepository.findAccountUserIdsByIds` -- **resolved in MR 15 #6.** It had zero callers in main and test, so the only-accounts-get-grants rule was documented and unenforced. The method was deleted and the rule enforced at `RoleRepository.addMember` instead. Low severity: admin-only endpoints, and a PERSON row cannot log in; the risk was a dormant grant surviving an upgrade to an account.
- [ ] `collection.rows_wide` is write-and-echo only. Confirm the frontend reads it.
- [ ] `deleteImages`/`deleteGif` delete from S3 before the DB write inside the transaction (`ContentService:380, 543`). A DB failure orphans the row's URLs; consider afterCommit S3 deletes.
- [ ] `updateImages` builds `imageMap` with `Collectors.toMap`, which throws `IllegalStateException` on a duplicate key. Two updates for the same image id in one request therefore 500 before any work happens, rather than being merged or rejected with a 400. Found while proving the MR 1b guards unreachable; not fixed there because it is a behavior change, not a deletion.
- [ ] `updateImages` reports per-item errors inside one transaction. Confirm a mid-item `DataAccessException` cannot leave an item half-applied (needs a test).
- [ ] `contentDisposition` (`DownloadUrlService:126-127`) does not escape quotes in filenames. Depends on what `sanitizeFilename` strips.
- [ ] `TagService.convertTagToCollection` briefly persists under a temp slug, visible to a concurrent reader, and may burn a `-1` suffix.
- [ ] `WebAuthnController.registerStart`/`loginStart` declare `throws Exception`; serialization failures become generic 500s.
- [ ] ID-list DAO fetches have no ORDER BY. Spot-checked callers re-order, but not all 7+ call sites were traced.
- [ ] `CollectionServiceTest` sections 937-1385 and 1555-2017 were profiled, not read line-by-line.

# Appendix D — Not yet started

- [ ] `ml_image_tagging` (design doc, 0% implemented) is still the largest unstarted feature. No stubs in `src/` to maintain, so it costs nothing until you start.
