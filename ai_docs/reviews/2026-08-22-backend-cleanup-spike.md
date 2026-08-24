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
| 4 — Comments and docs | MR 12-14 | MR 12 and MR 13 **complete** — [history](2026-08-22-backend-cleanup-history.md#wave-4--mr-12-and-mr-13-complete) (#177, #178, #180, #181, #183, #184). **Wave 4 is a genuine debloat: -975 words across six MRs** (retro below). **Next up: MR 14 -- read working rule 10 first.** 93 in-method comments left in `src/main`, re-derived below. |
| 5 — Consolidations | MR 15-19 | not started |
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
items; these five are the ones that are still real. Everything else that was unticked there was
verified done and ticked off in the history file.

- [ ] **Wave 3 residual — chunked bodies bypass the public body cap.** `RateLimitFilter` reads
  `getContentLengthLong()`, which is -1 for `Transfer-Encoding: chunked`, so a chunked request
  reaches Jackson uncapped. Options: reject chunked on `/api/public/**` outright (complete, small
  risk of breaking a proxy that chunks), or wrap the input stream in a counting guard (complete, no
  client-visible behavior change, more code). Verify first whether anything in front -- CloudFront
  or the BFF -- already normalizes chunked to a fixed length, which would close this for free.
  Decide before adding code.
- [ ] **`PersonRepository.findAccountUserIdsByIds` is still there and its deletion precondition is
  false.** MR 1 deferred it to MR 5 pending "the only-accounts-get-grants rule is confirmed enforced
  elsewhere". It is not enforced anywhere: both `RoleRepository.addMember` call sites
  (`AdminRoleController`, `AdminUserController`) pass a path-variable user id straight through. So
  this is a live gap, not dead code. Belongs with MR 15 or a security follow-up, not a deletion MR.
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

## Ordering note

The original review put bug fixes first so deletions would rebase cleanly. We inverted that and started with deletions, because they are compiler-verified and carry no behavior change. The bug MRs rebase onto the deletions instead. Only one item actually collided, and it was handled: `PersonRepository.deleteById` was listed under both MR 1 and bug #1, and was held until MR 5 because it had a live caller -- dangerous code, not dead code. It shipped with MR 5 and is gone.

---

# Wave 4 — Comments and docs

Rule: no `//` comments inside method bodies in main code. Delete, or promote to a docblock. D = delete, P = promote then delete. Class-level section banners (`// ====`) are outside method bodies and are out of scope.

**Measured 2026-08-23, not estimated.** At the start of the wave `src/main` held 684 lines beginning
with `//`, of which **567 sat at indent >= 4** -- inside method bodies, which is what this rule
targets. The review's "~370 occurrences" premise was low by about 53%. MR 12 and MR 13 cleared 474
of them; the 93 left are MR 14, below. MR 12's and MR 13's worklists, outcomes and guardrails are in
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
| **Wave 4** | **-65** | **-975** | |

So the trend was sound and 13b was the outlier, now corrected. Two things worth carrying forward.
13a shows the divergence without the defect -- **+2 lines but -75 words** -- which is proof the line
count is simply the wrong instrument, not just that 13b was sloppy. And 12a/12b, the two biggest
sweeps, produced the two biggest prose reductions, so scale is not what causes inflation. Care is.
Working rule 10 has the check.

## MR 14 — Comment debloat: controllers, config, data layer, and stale docblocks

### MR 14 worklist — re-derived 2026-08-23 on `6b2dc61`, use this instead of the line refs below

**93 in-method comments across 20 files. No split needed** -- that is close to 12c's 82, which shipped
fine as one MR. If it does start feeling long, the fault line is `SecurityConfig` (24) alone versus
the other nineteen files (69), not a controller/config/dao carve-up.

| File | Comments |
|---|---|
| `config/SecurityConfig.java` | 24 |
| `controller/admin/AdminUserController.java` | 10 |
| `dao/ContentRepository.java` | 7 |
| `services/AdminBootstrap.java` | 6 |
| `controller/prod/CollectionControllerProd.java` | 6 |
| `dao/RoleRepository.java` | 5 |
| `controller/auth/AuthController.java` | 5 |
| `config/JdbcPublicKeyCredentialUserEntityRepository.java` | 5 |
| `controller/prod/ContentDownloadControllerProd.java` | 4 |
| `services/ClientGalleryAuthService.java` | 3 |
| `dao/CollectionRepository.java` | 3 |
| `types/TextFormType.java`, `services/SessionService.java`, `controller/prod/ShareControllerProd.java`, `config/RequestMetricInterceptor.java`, `config/DatabaseInfoLogger.java`, `config/ClientGalleryAccessLimiter.java` | 2 each |
| `services/ContentService.java`, `controller/auth/WebAuthnController.java`, `config/ClientIp.java` | 1 each |

**The line refs below are 68% accurate -- the best in Wave 4, and this inverts working rule 5's
expectation.** MR 12's were 36%, MR 13b's 4%. Do not discount MR 14's refs the way the last three
MRs had to.

The reason matters more than the number: **decay tracks how much a file was edited, not how old the
doc is.** Waves 1-3 and MR 12/13 all landed on `services/`, so the `services/` refs rotted while
`controller/`, `config/` and `dao/` sat untouched. The distribution is bimodal, not uniformly 68% --
eleven files are at 100% and six are at 0%, so check per file rather than trusting the average:

- **100% (trust these):** `AdminUserController`, `ShareControllerProd`, `SecurityConfig`,
  `ClientGalleryAccessLimiter`, `DatabaseInfoLogger`, `JdbcPublicKeyCredentialUserEntityRepository`,
  `RequestMetricInterceptor`, `SessionService`, `AdminBootstrap`, `RoleRepository`, `TextFormType`.
- **Partial:** `CollectionControllerProd` 5/7, `ContentRepository` 4/7,
  `ContentDownloadControllerProd` 2/13.
- **0% (re-derive by name):** `AdminController`, `AuthController`, `WebAuthnController`,
  `TomcatConfig`, `ClientGalleryAuthService`, `CollectionRepository`.

`AdminController` now has **zero** in-method comments, so its `P: 232-233` item is finished by
attrition -- drop it rather than hunting for it.

### Guardrail for MR 14: `ContentService:227` is evidence, not a comment to sweep

One of the 93 is quarantined. `ContentService:227` reads
`// Batch save all successfully updated images for efficiency`, and it is the only remaining evidence
of **bug #16** (`updateImages` claims a batch save it does not do), filed by MR 12b. The Wave 4
guardrail already says to leave a bug-bearing comment until its own MR lands. It reads exactly like
the redundant narration MR 14 is deleting everywhere else, which is precisely why a fresh session
will delete it without noticing.

Leave it. Report instead whether bug #16 is still real against current `updateImages` -- Working rule
8 says a filed finding decays like any other note, and if it was fixed in passing then the comment
can go with the fix rather than silently.

Second, softer guardrail: `SecurityConfig`'s 24 are the densest security writing in the repo and the
item below says keep every word. That instruction predates Working rule 10. Both hold -- keep every
*fact*, but promoting 24 comments into one `filterChain` docblock is the exact shape that produced
13b's +42%. Check the word count on that file specifically before opening the PR.

### Two claims below, both verified 2026-08-23

- `CollectionVisibility.java:12` does still say `password_hash`; the column is `gallery_password`
  since V18. Real, and it is a docblock line rather than one of the 93.
- `.claude/CLAUDE.md:19` does still claim `controller/prod/ - Production endpoints (@Profile("prod"))`.
  Zero controllers carry `@Profile`. Two of them (`MessagesControllerAdmin`,
  `RequestMetricController`) now carry docblocks explicitly saying they run in dev and prod with no
  `@Profile` gating, so the code documents the opposite of the CLAUDE.md line. Real, and worth doing
  in this MR since it is one line.

- [ ] `AdminController.java` — P: 232-233 (with bug #4). `AdminUserController.java` — P: 216-218, 288-292 (invite-invalidation security rationale); D: 425-426. `AuthController.java` — D: 60, 70-71; P: 99-101. `WebAuthnController.java` — D: 142, 197. `ShareControllerProd.java` — P: 84-85. `CollectionControllerProd.java` — P: 104-109 (caching rationale); D: 222. `ContentDownloadControllerProd.java` — P: 72-75, 111-115, 148-149; D: 140-141.
- [ ] `SecurityConfig.java` — P: 30-31, 48-53, 58-64, 67-73, 79-80, all into the `filterChain` docblock. This is the best security documentation in the repo; keep every word. `ClientGalleryAccessLimiter.java` — P: 45-46. `TomcatConfig.java` — D: 18 (wrong), P: 19. `DatabaseInfoLogger.java` — D: 32, 37. `DefaultValues.java` — P: 5. `JdbcPublicKeyCredentialUserEntityRepository.java` — P: 62-65, 73. `RequestMetricInterceptor.java` — D: 71, 77. `SessionService.java` — D: 132-133. `AdminBootstrap.java` — P: 81-86; the "Do not fix this into a single statement" warning must survive. `ClientGalleryAuthService.java` — D: 58, 63, 102.
- [ ] `CollectionRepository.java` — P: 1018-1020 ("ORDER BY is load-bearing", into the `findContentByContentIdsIn` docblock). `ContentRepository.java` — P: 217-220, 774-776. `RoleRepository.java` — P: 271-273 (the `\s` JEP 378 note, into the `insertInheritedGrant` docblock); D: 372, 393 once the docblock carries it. `CollaboratorRequests.java` — D: the ten trailing positional labels at 45-65; the docblock already enumerates denied fields. `TextFormType.java` — D: 56, 59. `UserStatus.java` — P: 7-8. `ContentEntity.java` — D: 33. `Records.java` and `CollectionRequests.java` — D: the two trailing "Prevent instantiation" comments. `CollectionVisibility.java:12` — fix "password_hash" to "gallery_password" (column renamed in V18).
- [ ] Stale docblocks to rewrite, not delete:
  - `filterNonListedChildCollections` (`CollectionService:1536-1547`) describes a context-detection mode that no longer exists.
  - "previously spread across ContentProcessingUtil" rename-history at `ContentModelConverter:36-37` and `ContentMutationUtil:30-31` — that class is gone.
  - "PARENT-shaped" vocabulary at `CollectionService:103-104`, `TagViewResolver:22-23`, `UserPageAssembler:26, 38` — dead since the enum deletion.
  - `CollectionAccessService.effectiveLevel` overclaims: it says `canView`/`isClient`/`hasAtLeast` all resolve through `effectiveLevel`'s GENERAL ceiling. `canView` and `isClient` actually hit the repository directly and are only safe because flyby principals carry a null userId, which nothing documents or asserts. Fix the docblock, or actually route them through `effectiveLevel`, before a future caller trusts it. **VERIFIED 2026-08-23 during MR 12c** -- five affected call sites and the full cost of the fix are written up in the "MR 12c outcome" section. Now a Wave 3 follow-up, not a lead.
- [ ] `.claude/CLAUDE.md` is wrong about the architecture it documents: "controller/prod/ - Production endpoints (@Profile(\"prod\"))". Zero controllers carry `@Profile`; everything serves in all profiles. Fix the doc.

---

# Wave 5 — Consolidations

## MR 15 — Cross-cutting

Consolidation #1 (one client-IP resolver) ships with bug #3 in MR 5.

- [ ] #2. One SecurityConfig matcher instead of 17 copy-pasted guards. The identical 3-line `isRealUser` 401 guard opens every method across the six `/api/read/user/**` controllers (`UserControllerProd:25`; `UserFollowsControllerProd:38,49,59`; `UserSavesControllerProd:36,47,57,67`; `UserSelectsControllerProd:35,46,62`; `UserShareControllerProd:53,70,89,110`; `UserRatingOverrideControllerProd:43,61`). SecurityConfig already does exactly this for `/api/edit/**` with `hasRole("USER")`, which also excludes flyby principals. Add `requestMatchers("/api/read/user/**").hasRole("USER")` and delete all 17. ~51 lines.
- [ ] #6. `currentUserId` exists in three controllers (`AdminUserController:472-475`, `AdminRoleController:170-173`, `ContentDownloadControllerProd:196-199`). Move it onto `AuthPrincipal`.

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
- [ ] Fully qualified names inline: `CollectionService:542` (`jakarta.servlet.http.HttpServletRequest`), `CollectionProcessingUtil:828`, `TagViewResolver:115`, `GalleryAccessCookies:33-34`, `ContactMessageLimiter:40`, `Records.java:23` (root-caused by the name clash, consolidation #20).
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
- [ ] `PersonRepository.findAccountUserIdsByIds` being dead may mean the only-accounts-get-grants rule lost its enforcement point. Confirm before deleting (blocks an MR 5 item).
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
