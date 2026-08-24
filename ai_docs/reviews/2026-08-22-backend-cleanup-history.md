# Backend cleanup — completed work

Closed-out detail split out of [`2026-08-22-backend-cleanup-spike.md`](2026-08-22-backend-cleanup-spike.md)
on 2026-08-23. Nothing here is open. The tracker links into these sections when a working rule
cites the MR that taught it; read them for evidence, not for a worklist.

Waves 1-3 and MR 12-13 are complete. Every item in this file is either shipped or was reconciled
into the tracker as carried-forward work -- see "Carried forward" in the tracker for the five items
that were still live when the split happened.

Line numbers throughout are from the `8c28cf3` baseline and have drifted. Working rules 4 and 5 in
the tracker explain how much.

---

# Wave 1 — Deletions

Zero behavior change. The build is the proof.

## MR 1a — Dead code, compiler-verified (862 lines) — DONE ([PR #159](https://github.com/themancalledzac/edens.zac.backend/pull/159))

Everything checked off here was re-verified by caller-grep across main AND test before deletion.

### Corrections to the original review

The review counted callers in `src/main` only. Six items it listed as dead are live in the test
suite, so deleting them is test churn, not dead-code removal. They moved to MR 25, where the
`TestFixtures` work collapses the same call sites, rather than inflating a pure-deletion MR:

- `AuthPrincipal` 4-arg constructor — 1 main caller (`SessionService`) and 28 test call sites. Not
  dead. Its javadoc claimed "30 existing call sites", which was roughly right; the stale count was
  removed and the constructor kept.
- `CollectionRequests.Update` 17-arg — 0 main callers (`CollaboratorRequests.toUpdate` uses the
  22-arg canonical form), 23 test call sites.
- `DiskUploadRequest.FileEntry` 3-arg — 0 main callers, ~20 test call sites.
- `ContentService.resolveCollectionDownloadEntries` 2-arg — 0 main callers, 5 test call sites.
- `DownloadResolution.extension` — never read in main, but 4 construction and 6 assertion sites in
  test.
- `CollectionRepository.hasChildCollections` — the review did not mention that 10 test references
  existed. Deleted anyway, with the two integration tests re-pointed at the live derivation.

One item was found dead that the review missed: `CollectionRepository.findAllByOrderByCollectionDateDesc()`
(the no-arg overload) became unreachable once `CollectionService.getAllCollectionsOrderedByDate` went.

### Dead DAO methods (~400 lines)

- [x] `dao/EquipmentRepository.java` — 16 methods: `findCameraByName`, `findCamerasByNameContainingIgnoreCase`, `existsByCameraName`, `existsByCameraNameIgnoreCase`, `findCamerasOrderByUsageCount`, `findLensByName`, `findLensesByNameContainingIgnoreCase`, `existsByLensName`, `findLensesOrderByUsageCount`, `findFilmTypeByName`, `findFilmTypeByDisplayName`, `findFilmTypesBySearchTerm`, `findAllFilmTypesOrderByDefaultIso`, `existsByFilmTypeName`, `existsByFilmTypeDisplayName`, `findFilmTypesOrderByUsageCount` (lines 110-438). JPA query-method names mechanically ported to JDBC, superseded by the `*IgnoreCase` and `findAll*OrderBy*` variants. ~134 lines.
- [x] `dao/CollectionRepository.java` — 6 methods: `hasChildCollections` (256-281; the service derives this from `findAllReferencedCollectionIdsByParentId` now), `findContentByCollectionIdAndContentType` (869-886), `shiftContentOrderIndices` (948-964), `findContentByCollectionIdAndOrderIndex` (973-984), `updateContentOrderIndexForContent` (1027-1042), `deleteContentById` (1120-1125). ~95 lines.
  - [x] Fix `updateContentVisibleForContent`'s "mirror" javadoc, which cites a deleted method.
  - [x] Re-point the `hasChildCollections` tests at the live derivation.
- [x] `dao/TagRepository.java` — 8 methods: `findByTagName`, `findByTagNameContainingIgnoreCase`, `existsByTagName`, `addCollectionTag`, `removeCollectionTag`, `deleteContentTags`, `addContentTag`, `removeContentTag` (47-481). ~59 lines.
  - [x] Fix `saveCollectionTags`' javadoc, which still cites `addCollectionTag`.
- [x] `dao/PersonRepository.java` — 4 methods: `findByPersonName`, `findByPersonNameContainingIgnoreCase`, `existsByPersonName`, `findAllOrderByImageCountDesc` (41-114). ~45 lines.
- [x] `dao/PersonRepository.java` — `findAccountUserIdsByIds`. DEFERRED to MR 5. Its being dead may mean the only-accounts-get-grants rule lost its enforcement point; confirm before deleting (Appendix C).
- [x] `dao/PersonRepository.java` — `deleteById`. DEFERRED to MR 5. Not dead: `MetadataService.deletePerson` calls it. Deleting it is part of bug #1's fix.
- [x] `dao/CollectionRepository.java` — `findAllByOrderByCollectionDateDesc()` (no-arg overload). Not in the original review; became unreachable when `getAllCollectionsOrderedByDate` was deleted. The 2-arg overload stays.
- [x] `dao/ContentRepository.java` — 4 methods: `findByOriginalFilenames` (233-241), `findMostRecentImageIdByPersonId` (322-344), `deleteTextById` (972-980), `deleteCollectionContentById` (1196-1204). ~50 lines.
- [x] `dao/CollectionPeopleRepository.java` — `findPeopleForCollection` (32-44). The batch variant is the live one. ~13 lines.

### Ghost JPA leftovers on entities (~170 lines)

- [x] `entity/ContentCameraEntity.java`, `ContentLensEntity.java`, `ContentFilmTypeEntity.java`, `ContentPersonEntity.java` — `Set<ContentImageEntity> contentImages`, `Set<CollectionEntity> collections`, `getImageCount()`, `getCollectionCount()`, `getTotalUsageCount()`. No row mapper populates them, zero callers. Pre-JDBC-migration leftovers; ContentPersonEntity's are doubly stale since V35 dropped `content_people`. ~85 lines.
- [x] `entity/ContentEntity.java:36-44` — `setContentTypeFromSubclass()`, a JPA lifecycle idea with no lifecycle.
- [x] `types/FilmType.java` — the whole 73-line enum is dead. `ContentFilmTypeEntity`'s javadoc names itself as the replacement. Delete the file.
- [x] `model/FilmTypeDTO.java` — zero references (`ContentFilmTypeModel` is live). Delete the file.
- [x] `model/Records.java:60-64` — `CollectionSummary`, zero references.
- [x] `types/ContentType.java:11-21` — `contentName` field duplicates `name()`, zero callers. Also delete the commented-out `CODE("Code")` at line 14.

### Dead service code (~130 lines)

- [x] `services/CollectionService.java:805-814` — `getAllCollectionsOrderedByDate()`. No callers; the like-named endpoint calls `getAllCollections`.
- [x] `services/CollectionService.java:492-505` — `findById(Long)`. Test-only.
- [x] `services/validator/ContentImageUpdateValidator.java:48-56` — `validateFilmFormatRequired`. Zero callers.
- [x] `services/MetadataService.java:420-430` — `findOrCreateLocation`, `findLocationById`. Zero callers.
- [ ] `services/ContentService.java:825-829` — `resolveCollectionDownloadEntries` 2-arg overload. MOVED TO MR 25 (5 test call sites).
- [x] `services/ContentService.java:165-168, 171-173, 241-243` — three unreachable guard branches in `updateImages`. MOVED TO MR 1b: proving unreachability is a control-flow judgment, not a mechanical deletion.
- [x] `services/ContentService.java:587, 604, 625` — `updateGif`'s `setTags`/`setPeople`/`setLocations` dead writes. MOVED TO MR 1b.
- [x] `services/ContentService.java:98-104` — `createTag`/`createPerson` pass-throughs. Have `AdminController` call `MetadataService` directly.
- [x] `services/ContentMutationUtil.java:112-119, 177-183` — back-compat entity-typed overloads. MOVED TO MR 1b (requires changing two call sites, not just deleting).
- [x] `types/CollectionVisibility.java:37-39` — `requiresLocalEnv()`. Only its own test calls it.
- [x] `services/ImageProcessingService.java:143-149` — `prepareImageForUpload` single-arg overload. Zero callers.
- [ ] `model/DownloadResolution.java:13-14` — `extension` component written, never read; docblock also stale. MOVED TO MR 25 (10 test sites).
- [x] `services/VideoVariantPlanner.java:27-46` — `VideoVariantPlan`'s target-side fields are always the constants. MOVED TO MR 1b (a record reshape, not a deletion).

### Dead constructors and config (~120 lines)

- [x] `model/CollectionRequests.java:303-305` — the 2-arg `GalleryAccessRequest` constructor. Zero callers anywhere; every site passes the propagation flag.
- [x] `model/AuthPrincipal.java` — removed the stale "30 existing call sites" claim from the 4-arg constructor's javadoc. The constructor itself stays (see corrections above).
- [ ] `model/CollectionRequests.java:119-160` — the 17-arg `Update` constructor. MOVED TO MR 25.
- [ ] `model/DiskUploadRequest.java:44-46` — the 3-arg `FileEntry` constructor. MOVED TO MR 25.
- [x] `config/WebConfigProd.java` — `addCorsMappings` registers nothing. The class's only runtime effect is a log line. Delete the file.
- [x] `config/ProdSecretGuard.java:15-19` — `@RequiredArgsConstructor` with no final fields generates nothing. Delete the annotation and move the `@Value` to a constructor parameter.
- [x] `config/ImageIoConfig.java:15-42` — `registerPlugins` registers nothing, it only logs SPI discovery. Rename to say what it does; keep the diagnostics.
- [ ] V19's `admin_home_tile.cover_image_id` column: written by nothing, read by nothing (`AdminHomeService` resolves covers by strategy). DEFERRED — a schema change does not belong in a pure-deletion MR. Drop it in a migration or document it as reserved.

## MR 1b — Dead code, behavior-adjacent (~60 lines) — DONE

Split out of MR 1a because each item needs a correctness judgment rather than a caller-grep.
All four claims were verified against the control flow across `src/main` AND `src/test`, and all
four held. Build green: 1365 tests, 0 failures, 0 checkstyle violations.

- [x] `services/ContentService.updateImages` — all three guard branches confirmed unreachable, deleted.
  - `imageId == null`: `ContentImageUpdateValidator.validate` runs over every update in a loop
    *before* the per-item loop and throws on a null id. Nothing reaches the guard.
  - `image == null`: every non-null id is in `imageIds`, and the pre-loop containment check throws
    `ResourceNotFoundException` for any id missing from `imageMap`. `Collectors.toMap` rejects null
    values, so a present key cannot map to null. Unreachable *because* of the first guard's
    invariant, so the two had to be judged together rather than independently.
  - `catch (ClassCastException)`: `findImagesByIds` joins `content_image`, and
    `CONTENT_IMAGE_ROW_MAPPER` hardcodes `.contentType(ContentType.IMAGE)` rather than reading the
    column. So `convertRegularContentEntityToModel` always takes the `case IMAGE` arm, whose return
    type is `ContentModels.Image`, and the outer cast cannot fail. No other statement in the try
    block casts. A hypothetical CCE now lands in the general `catch (Exception)` arm, which records
    the same per-item error.
  - The two invariants the guards stood for are now recorded in the `updateImages` docblock. Without
    that, the absence of a null check reads as an oversight.
- [x] `services/ContentService.updateGif` — `setTags`/`setPeople`/`setLocations` confirmed dead, deleted.
  `saveGif`'s UPDATE branch writes `content_gif` scalars only. The response is rebuilt by
  `convertEntityToModel`, which reloads the row through `findGifById` into a *new* instance, and
  `convertGifToModel` re-queries tags/people/locations itself. The `gif` instance the setters wrote
  to is discarded. The merge results are still used to persist the join rows -- only the setter
  calls went.
- [x] `services/ContentMutationUtil` — both entity-typed overloads deleted, call sites pass ids.
  The review predicted two call sites; there were two in main plus seven in
  `ContentMutationUtilTest`. Unlike the MR 25 constructors, this test rewrite was a strict
  improvement (`image` to `1L`) and let nine now-pointless `ContentImageEntity.builder()` lines go
  with it, so it did not need to wait on `TestFixtures`. `RuleBMixedContentIntegrationTest` already
  used the id form.
- [x] `services/VideoVariantPlanner` — `VideoVariantPlan` shrunk to the two booleans.
  Both construction sites passed the constants: `compute` and the probe-failure fallback in
  `ImageProcessingService`. The two read sites now reference `FULL_MAX_LONGEST_SIDE` /
  `WEB_MAX_LONGEST_SIDE` directly. `VideoVariantPlannerTest`'s two target-side assertions became
  tautologies and went.

Test-coupled constructor removals that were also split out of MR 1a moved to MR 25, not here --
see "Main-dead, test-live constructors" there. They rewrite the same call sites that MR 25's
`TestFixtures` builders collapse, so doing them separately is wasted work.

## MR 2 — Build and config rot (~180 lines) — DONE

Build green: 1365 tests, 0 failures, 0 checkstyle violations. Test count is unchanged from MR 1b.

- [x] `pom.xml` — `spring-boot-starter-thymeleaf`: zero references, no templates directory. Deleted.
- [x] SpotBugs: deleted all four artifacts (user decision). The plugin block in `pom.xml`,
  `spotbugs-exclude.xml`, the Dockerfile COPY, and the commented-out CI step.
  - A fifth artifact the review did not count also went: the CI "Upload SpotBugs results" step,
    which uploaded `target/spotbugsXml.xml` — a file nothing produces. Its `if: always()` kept it
    from failing the job, so it had been silently no-opping.
  - Re-enabling was rejected as out of scope for a config-rot MR: it surfaces an unknown pile of
    findings needing triage. The inherited exclude filter was also very broad — `<Class name="~.*\$.*"/>`
    excluded every nested class, which in this codebase means every record in `Records.java`,
    `CollectionRequests.java`, and `ContentModels.java`. Any future reintroduction should write a
    filter from scratch rather than restore that one.
- [x] H2 fossil in test resources. Confirmed inert: no H2 dependency and no JPA starter in `pom.xml`,
  so every `spring.jpa.*` and H2 property was dead.
  - The review's "delete both halves" was wrong about the second half. `application-test.properties`
    is mostly live config — Flyway settings and the AWS, email, and WebAuthn stubs the context needs
    to start. Only four lines existed to neutralize H2. Those four went; the rest stayed.
  - The base `src/test/resources/application.properties` likewise carries live settings
    (`app.access-token.secret`, the two contact rate limits). Only the H2/JPA block went.
  - Three stale comments referencing "the H2 test props" were rewritten.
- [x] A fifth H2 fossil the review missed: `src/test/java/.../ApplicationTests.java`. `@Disabled`
  since the PostgreSQL migration, body is an empty `contextLoads()`, and it carried its own H2
  `@TestPropertySource`. It never ran at all — surefire includes `**/*Test.java` and the file is
  `*Tests.java`, so it was not even being collected as a skip. Deleted.
- [x] `pom.xml` — both spring-milestones repository blocks. Everything resolves from Central at GA
  versions; build is green without them.
- [x] `application.properties` — `spring.codec.max-in-memory-size`, a WebFlux property in a servlet
  app. Deleted as rot. Whether to add a real body cap stays open in MR 11.
- [x] `application.properties` lines 1, 11-13 — the `:-` defaults. Deferred to MR 9 and fixed there
  as bug #9. The paired decision about shipping a default DB password at all is still open; it is
  recorded under MR 9.
- [x] `testRequests/` — deleted the directory. All three files were stale Postman fixtures with zero
  references anywhere in the repo. `updateCollection.json` carried `visible`/`priority`/`coverImageUrl`
  (now `visibility`/`rating`/`coverImageId`); `createCollection.json` also carried `type`, dead since
  the typeless migration.
- [x] `.idea/` — untracked with `git rm --cached`, files preserved on disk so the IDE keeps working.
  The review named only the `.iml`; eight files were tracked. `.gitignore` already lists `.idea` and
  `*.iml` — the commits predate those rules.
  - Not done: `.claude/settings.local.json` still allowlists `Bash(mvn spotbugs:check:*)`. It is a
    local permission entry, not build config, and harmless.

Checkstyle config is healthy (all 49 suppressions are commented with reasons) and the CI pipeline is sound. `grep TODO|FIXME|XXX|HACK` across `src/main`: zero hits.

## MR 3 — Dead tests, wholesale (1,510 lines) — DONE

Build green: 1298 tests, 0 failures, 0 checkstyle violations. 67 tests removed (1365 -> 1298). The estimate of ~1,800 lines counted the baseline before MR 1a-2 trimmed imports; the seven files are 1,510 lines.

- [x] `model/CollectionBaseModelTest.java` (504 lines) — response-DTO "validation" that never runs in prod (`CollectionModel` is never a `@RequestBody`), Lombok round-trips, plus visible rot: `PriorityValidationTests` loops 1..4 but never sets the field, so four assertions are empty.
- [x] `model/CollectionModelTest.java` (541 lines) — same category, including toString substring assertions.
- [x] `entity/ContentEntityTest.java` (65), `ContentImageEntityTest.java` (139), `ContentGifEntityTest.java` (137) — builder/Lombok templates. `ContentEntityTest` opens with `// TODO: Verify tomorrow if this is a valid test file` (it is not) and contains `testTimestampsAreNotTestedDirectly`.
- [x] `services/ImageMetadataExtractionTest.java` (93) — a `@Disabled` diagnostic dump that never runs in CI.
- [x] `model/RecordsTest.java` (31) — asserts that record accessors hold constructor args.

## MR 4 — Test trims (1,057 lines net) — DONE

Build green: 1249 tests, 0 failures, 0 checkstyle violations. 49 tests removed (1298 -> 1249).

- [x] `model/CollectionUpdateRequestTest.java`: 1,004 -> 301 lines. Kept the four Jackson
  wire-contract guards, the four validation tests, and the two `contentPerPage` bound checks.
  `CollectionRequests.Update` is `@RequestBody @Valid` in `AdminController:117`, so unlike
  `CollectionModel` its constraints do execute in prod and the validation tests are worth keeping.
  Deleted the builder, display-mode, cover-image, tag, person, and collection blocks — all
  positional-constructor round-trips. Also inlined the fully-qualified `JavaTimeModule` as an import.
- [x] `entity/ContentTextEntityTest.java`: deleted whole (133 lines). The condition on
  `testFormatTypeValues` failed — it builds three entities and asserts `getFormatType()` returns the
  string it just set. `formatType` is a plain `String` field with a Lombok getter, so there is no
  real behavior there.
- [x] `entity/CollectionEntityTest.java`: 240 -> 41 lines, keeping only `testGetTotalPages`, which
  covers the entity's one hand-written method including its null and zero handling.
  Also deleted `testPasswordProtectionStateIsDerivedFromGalleryPassword`, which the review did not
  call out: despite the name it only asserts `setGalleryPassword`/`getGalleryPassword` round-trips.
  The derivation it claims to cover lives in `CollectionProcessingUtil`, not the entity.
- [x] `types/ContentTypeTest.java`: 59 -> 38 lines, keeping the two `forValue` blocks. Dropped
  `enum_ShouldHaveCorrectValues` (declaration order) and `getValue_ShouldReturnEnumName`
  (`getValue()` is `return this.name()`). `forValue_WithInvalidValue_ShouldReturnText` still pins the
  current lenient behavior and carries a comment pointing at bug #13, which will rewrite it.
- [x] Renamed the three `*DevTest` classes. All three inject `AdminController`, so they now follow
  the package's existing `AdminController<Area>Test` pattern:
  `CacheControllerDevTest` -> `AdminControllerCacheTest`,
  `AdminHomeControllerDevTest` -> `AdminControllerHomeTilesTest`,
  `CollectionControllerDevTest` -> `AdminControllerCollectionsTest`.
  `ai_docs/ai_cicd.md:274` referenced the old name plus two files that no longer exist
  (`ContentControllerDevTest`, `ContentProcessingUtilTest`); corrected.

---

# Wave 2 — Bugs

Ordered by severity. All are small diffs.

## MR 5 — Security bugs — DONE

Build green: 1303 tests, 0 failures, 0 checkstyle violations (1298 -> 1303; the X-Forwarded-For
test was replaced 1:1, and five tests were added).

- [x] Bug #1 (high). Admin "delete person" can delete a real user account. `dao/PersonRepository.java:190-195` (`deleteById`) runs `DELETE FROM users WHERE id = :id` with no status guard, and `MetadataService.deletePerson` (`services/MetadataService.java:175`) calls it unconditionally. Since V35 merged people into `users`, passing an account user's id to the admin delete-person endpoint destroys the account — sessions, passkeys, invites, saves, follows, and share links all cascade. The safe primitive already exists: `deletePersonById` (PersonRepository:265-271) guards with `AND status = 'PERSON'`. Point `deletePerson` at it, 404 on 0 rows, and delete the unguarded `deleteById`.
  - Done as described. `deletePersonById` now returns the row count. `deletePerson` keeps its
    existing `findById` 404 check -- that check cannot tell a person tag from an account, since
    `findById` selects from `users` with no status filter, which is precisely why the guard is
    needed. The association deletes run first, so the throw on 0 rows rolls them back with the
    transaction (`ResourceNotFoundException extends RuntimeException`).
  - Three regression tests added to `MetadataServiceTest`: the guarded-delete happy path, the
    account id that must 404, and the unknown id that must not touch associations.
- [x] Bug #3 (high). `RateLimitFilter` still trusts client-supplied X-Forwarded-For as a fallback key. `config/RateLimitFilter.java:128-138` reads X-Real-IP first (correct), then falls back to the first hop of client-supplied XFF (133-136). Whenever X-Real-IP is absent, an attacker sends a fresh fake XFF per request and the per-IP 500/hour limit on `/api/public/messages` stops existing, while churning the 10k Caffeine cache to evict legitimate buckets. `CollectionControllerProd.java:207-224` documents and implements the correct policy. Delete the XFF branch.
  - `RateLimitFilterTest.xForwardedForFirstHopIsUsedAsIp` asserted the vulnerable behavior. It is
    replaced by `spoofedXForwardedForCannotMintFreshBuckets`, which sends three requests from one
    `remoteAddr` with a different spoofed X-Forwarded-For each time and requires the third to 429.
    Under the old code each spoofed value drew its own bucket and none of them ever 429'd.
- [x] Extract `ClientIp.resolve(HttpServletRequest)` into `config/` while fixing bug #3 (consolidation #1, pulled forward because bug #3 motivates it). Replaces four private copies with two contradictory trust policies: `RateLimitFilter:128-138`, `AuthController:127-133`, `WebAuthnController:210-216`, `CollectionControllerProd:217-224`. Implement the `CollectionControllerProd` policy; carry over its javadoc, the only one explaining the trust model.
  - Done. All four private copies are gone. Only `RateLimitFilter` actually had the XFF branch; the
    other three already implemented the correct policy, so this was a true consolidation for them
    and the bug fix for the filter.
- [x] Bug #7 (medium). `app.admin.enforce-authz=false` in prod silently opens `/api/admin` and `/api/edit`. `config/SecurityConfig.java:65-76` falls through to `permitAll`, `EditAccessWebConfig.java:38-40` skips the interceptor, and `ProdSecretGuard.java:21-27` does not check the flag. One wrong env var proxies unauthenticated visitors into the whole write surface. Extend `ProdSecretGuard` to refuse prod + enforce-authz=false.
  - Done. The guard is already `@Profile("prod")`, so the new check costs nothing outside prod. Two
    tests added, one of which asserts the failure message names the toggle rather than the secret --
    the two checks must stay independent.
- [ ] Delete `PersonRepository.findAccountUserIdsByIds` once the only-accounts-get-grants rule is confirmed enforced elsewhere (carried from MR 1). NOT DONE -- the precondition is false.
  - The rule is not enforced anywhere. Both `RoleRepository.addMember` call sites
    (`AdminRoleController:152`, `AdminUserController:335`) pass a path-variable user id straight
    through, and `addMember` inserts into `role_member` with no status filter. `findAccountUserIdsByIds`
    is the only primitive that expresses the rule and it has zero callers.
  - Not exploitable today: a `status='PERSON'` row has no password hash and no WebAuthn handle, so
    nobody can authenticate as it and the stray `role_member` row grants nothing to anyone. It is a
    data-integrity gap, not an access-control hole -- which is why this was left alone rather than
    given a new validation rule inside a bug-fix MR.
  - Decision needed: either enforce the rule at the two `addMember` call sites (making the method
    live) or delete the method and drop the rule. Deleting it while the rule is unenforced would
    remove the only tool for a gap nobody is tracking. Carried to MR 10.

## MR 6 — Upload pipeline bugs — DONE

Build green: 1262 tests, 0 failures, 0 checkstyle violations (net +8: 17 tests added, 9 removed
with `BooleanExtractor`). Each new test was run against the unfixed code first and fails there.

- [x] Bug #2 (high). The upload OOM guard covers only the multipart path; disk and ingest jobs run unbounded. `services/ImageUploadPipelineService.java:74-79, 144, 162, 197-204`. `uploadSemaphore` (permits=1) is acquired only in `createImagesParallel`. `processFilesFromDisk` and `ingestFilesGroupedByDay` each submit a background loop to an unbounded virtual-thread executor with no semaphore and no cap on concurrent jobs; each loop does full `ImageIO.read` decodes (~130-180 MB heap per 45MP JPEG). This is the same shape as the historical 20+15 concurrent-Lightroom-request OOM — that fix only landed on the multipart endpoint. A disk job can also stack with a semaphore-holding multipart upload. Gate the per-file prepare step in both loops on the same semaphore.
  - Done. Both loops now call `prepareFromDiskGuarded`, which holds the permit across the decode
    and S3 phase only and releases before the DB work, so concurrent jobs interleave instead of one
    job holding the permit for its whole run. `createImagesParallel` still holds it for its whole
    batch -- that path decodes `PARALLEL_BATCH_SIZE` files at once and has to bound the batch.
  - The two acquire sites are now one `acquireUploadPermit()` helper, throwing
    `IllegalStateException` rather than the bare `RuntimeException` the multipart path used (same
    concern as the `validateAndEnsureUniqueSlug` item in MR 9).
  - `processFilesFromDisk_concurrentJobs_serializeThePrepareStep` counts peak concurrent entries
    into the mocked prepare step across two overlapping jobs and requires 1. Without the permit it
    observes 2.
- [x] Bug #10 (medium). Upload dedupe SKIP is unreachable across export sessions. `services/ImageProcessingService.java:229-230, 308, 359-369` — `lastExportDate` is set to `now()` at prepare time (the comment claims file-mtime), so a re-export is always "newer" and always takes the UPDATE branch. The skip feature only fires for duplicates within one batch. Also, on SKIP the image is never linked to the target collection. Use the file's mtime (disk path) or a plugin-sent export date (multipart), and link on SKIP.
  - Split outcome. The from-disk path is fixed; the multipart path is not, and cannot be here.
  - Fixed: `prepareImageFromDisk` now takes `lastExportDate` from the exported JPEG's mtime
    (`exportDateFromFile`), so a re-export is newer and a re-send is not. Falls back to `now()`
    when mtime is unreadable, which keeps the image eligible for update rather than skipping a
    real re-export.
  - Fixed: SKIP now links the existing image to the target collection in both loops, via a shared
    `linkIfNotLinked`. SKIP still does no keyword rewrite and no RAW re-upload -- the stored image
    is unchanged, only its collection membership is added.
  - NOT fixed: the multipart path still stamps `now()`. A `MultipartFile` carries no mtime and the
    Lightroom plugin sends no export date, so there is nothing to read. Fixing it needs a wire
    change in the plugin repo; adding an unused optional request field here would be dead until
    that ships. The misleading comment claiming mtime is replaced with the real reason. Carried:
    add an export-date field to the multipart upload contract when the plugin can send one.
  - `exportDateFromFile` is package-private for tests. The surrounding prepare step converts to
    WebP through `libwebp-imageio`, whose native binary is x86_64-only and will not load on an
    arm64 Mac, so `prepareImageFromDisk` cannot run end-to-end in this test suite.
- [x] Bug #11 (medium). Job cleanup expires running jobs. `services/JobTrackingService.java:127-141` removes any job older than 1 hour with no status check, so a long ingest job vanishes from polling mid-flight. Skip PENDING and PROCESSING.
  - Done. Cleanup now removes only COMPLETED and FAILED jobs. A job stuck in PROCESSING is never
    reclaimed, which is the deliberate trade: a leaked `JobStatus` is a UUID and five counters,
    while dropping a live job makes it 404 on the next poll and the client sees it vanish rather
    than finish.
  - `cleanupExpiredJobs` delegates to a package-private `removeFinishedJobsStartedBefore(cutoff)`
    so tests supply a cutoff instead of aging a job by an hour.
- [x] Bug #12 (medium). `blackAndWhite`/`isFilm` XMP extraction very likely never fires. `services/ImageMetadata.java:120-131` configures both as `xmp:subject` — keywords live in `dc:subject`, and `subject` is an array property that `getProperty` will not flatten. Meanwhile `FILTERED_KEYWORDS` (`ImageMetadataExtractor:44`) strips "monochrome"/"blackandwhite"/"film" from tags because they are "already handled", so the signal is dropped entirely. Extract the flags from the parsed keyword list before filtering. Verify against one real Lightroom export.
  - Done. `recordKeywordFlags` sets both flags off the parsed keyword list before
    `FILTERED_KEYWORDS` strips those keywords from the tags, in both the hierarchical and the flat
    `dc:subject` branch. `FILTERED_KEYWORDS` is now derived from the two flag keyword sets, so a
    keyword cannot set a flag and also survive as a tag.
  - Matching is exact against the keyword sets, not the old substring test. "Film Noir" stays a
    tag; only "film" becomes the flag. The substring version would have set the flag while leaving
    the keyword in the tag list.
  - The dead `BLACK_AND_WHITE` / `IS_FILM` entries are gone from the `ImageMetadata` field table
    along with `BooleanExtractor`, which had no other user. Nine tests pinning that never-fired
    behavior went with it.
  - Verified against synthetic XMP, not a real Lightroom export: `ImageMetadataExtractorKeywordFlagTest`
    builds real JPEGs and splices an XMP APP1 segment after SOI, covering both the hierarchical and
    flat keyword layouts. Confirming against one real export is still worth doing.
  - Flagged for the MR 15-19 consolidation wave: `ImageMetadata.ExifTags.none()` now has no caller
    in main (only `ImageMetadataTest`). Left in place as the symmetric counterpart of the live
    `XmpProperty.none()`.

## MR 7 — Validation and wire contracts — DONE

Build green: 1272 tests, 0 failures, 0 checkstyle violations (+6). Every new test was run against
the unfixed code first; five of the six fail there, and the sixth is called out below.

One finding in this MR was wrong as written. See bug #4.

- [x] Bug #4 (high). Admin image-patch endpoint never validates list elements. `controller/admin/AdminController.java:236` uses `@RequestBody @Valid List<ContentImageUpdateRequest>` — `@Valid` on a List validates the list object, never its elements. The repo's own `GlobalExceptionHandler` documents this trap (`config/GlobalExceptionHandler.java:93-99`), and the collaborator route already uses the working form (`List<CollaboratorRequests.@Valid CollaboratorImageUpdate>`, `EditController.java:99`). Also `ContentImageUpdateRequest.rating` has no `@Min`/`@Max` while the collaborator DTO constrains 0-5. Fix both.
  - PARTLY WRONG AS FILED. The premise -- "`@Valid` on a List validates the list object, never its
    elements" -- does not hold on Spring Boot 3.5.16. Spring 6.1's `HandlerMethodValidator`
    cascades into the elements for either placement. Probed directly with a two-endpoint controller
    under standalone MockMvc: `@Valid List<T>` and `List<@Valid T>` both returned 400 with
    `HandlerMethodValidationException` for an element violating `@NotNull`. The `@NotNull` on
    `ContentImageUpdateRequest.id` was already enforced on this route.
  - The `GlobalExceptionHandler` docblock this finding was derived from asserted the old rule. It
    is corrected in this MR, so the next reader does not inherit the same false premise.
  - Genuinely fixed: `ContentImageUpdateRequest.rating` had no bound while the collaborator DTO
    constrained 0-5 and `content_image` carries `CHECK (rating >= 0 AND rating <= 5)`. Now
    `@Min(0) @Max(5)`, and the javadoc that said "(1-5)" says 0-5.
  - The parameter still moved to `List<@Valid ContentImageUpdateRequest>`. That is a readability
    and consistency change matching `EditController.patchImages`, not a behavior fix.
  - `updateImages_elementMissingId_is400` passes against the unfixed code. It is kept as coverage
    and its comment says so.
- [x] Bug #13 (medium). `ContentType.forValue` silently coerces bad values to TEXT. `types/ContentType.java:28-41` is the Jackson `@JsonCreator` for every request carrying a contentType, so a typo'd discriminator becomes a valid TEXT block instead of a 400. Throw, like `CollectionVisibility.forValue` does. `ContentTypeTest.forValue_WithInvalidValue_ShouldReturnText` pins the current wrong behavior — update it with the fix.
  - Done. `forValue` throws `IllegalArgumentException` naming the value and the valid options, and
    tolerates lowercase input the way `CollectionVisibility.forValue` does -- the serialized form
    is uppercase, so case variance is a client quirk while an unknown name is a real error.
    `GlobalExceptionHandler` maps `IllegalArgumentException` to 400.
  - `ContentTypeTest.forValue_WithInvalidValue_ShouldReturnText` pinned the coercion and is
    replaced by a throwing test, a case-insensitivity test, and one asserting the message names the
    valid options. The now-unused `@Slf4j` came off the enum.
- [x] Bug #14 (medium). `TextFormType` wire value and stored value disagree. `types/TextFormType.java:37-40` serializes lowercase ("markdown"); `services/ContentService.java:462` stores `name()` (uppercase "MARKDOWN"); `entity/ContentTextEntity.java:25-29` documents lowercase. Store `getValue()` and normalize existing rows if any are uppercase.
  - Done. `createTextContent` stores `getValue()` ("markdown") and defaults to "plain" instead of
    "PLAIN". Nothing in Java compares the column -- every read path hands it straight to the
    client -- so the only symptom was two different spellings on the wire depending on which path
    created the block.
  - `V57__lowercase_text_format_type.sql` lowercases existing `content_text.format_type` rows.
    Idempotent; rows already lowercase are untouched. Table and column verified against
    `ContentRepository`'s INSERT before writing the migration.
- [x] Bug #15 (medium). `UserRatingOverrideService` throws `java.lang.SecurityException`, which `GlobalExceptionHandler` does not map. `services/UserRatingOverrideService.java:36-39` — the controller compensates with the repo's only try-catch (`UserRatingOverrideControllerProd.java:46-52`). Throw `AccessDeniedException` (mapped to 403, and what sibling `UserSelectsService:78` uses) and delete the try-catch.
  - Done. `UserRatingOverrideService.upsert` throws `AccessDeniedException` (mapped to 403 by
    `GlobalExceptionHandler`, and what sibling `UserSelectsService` uses) instead of
    `java.lang.SecurityException`, which nothing mapped. The repo's only controller try-catch is
    gone, along with the `@Slf4j` that existed only to log inside it.
- [x] Bug #16 (medium). Selects add endpoint accepts a fully-null body. `controller/prod/UserSelectsControllerProd.java:29, 34` — no `@NotNull`, no `@Valid`, so `{}` reaches `userSelectsService.add(userId, null, null)`. Both sibling controllers validate. Match them.
  - Done. `AddSelectRequest` constrains both fields `@NotNull` and the parameter takes `@Valid`,
    matching `UserFollowsControllerProd`. `{}` now 400s instead of reaching
    `userSelectsService.add(userId, null, null)`.

## MR 8 — Data correctness

- [x] Bug #5 (high). `updateCollectionPeople` rebuilds the exact id-only-entity pattern the tags docblock documents as data-corrupting. `services/CollectionService.java:972-994` fabricates `ContentPersonEntity` instances carrying only an id; `equals`/`hashCode` key on `personName`, so a RETAINED person can survive twice in the set with the same id. The 15-line docblock on the adjacent `updateCollectionTags` (926-940) describes this exact mechanism as the historical tag bug that rolled back whole saves. It currently avoids the PK collision only because the DAO happens to `.distinct()` — any DAO change re-arms it. Load full entities like the tag path does, and add `.distinct()`.

  **Done.** `updateCollectionPeople` now loads full entities via the new
  `CollectionPeopleRepository.findPeopleForCollection`, mirroring `TagRepository.findCollectionTags`
  on the tag side, and `.distinct()` was added to the id mapping. This orphaned
  `CollectionRepository.findCollectionPersonIds` (the broken line was its only caller), so it was
  deleted. Three regression tests in `CollectionServiceTest.UpdateCollectionPeople`; all three fail
  on the pre-fix code.

  `CollectionPeopleRepository.setPeopleForCollection`'s `.distinct()` was left in place per
  instruction. What removing it would change, given the fix: nothing observable today. The service
  now hands it a de-duplicated list, and its other two callers
  (`CollectionService.setCollectionPeople` from the admin manage page, and
  `regeneratePeopleFromContents`, whose ids come from a `SELECT DISTINCT`) cannot produce repeats
  either. Before the fix it was load-bearing: it was the only reason the duplicate id never hit the
  `collection_people` primary key, which is what made this bug #5 (silent) rather than the
  rollback-the-whole-save failure the identical tag defect caused. Keeping it costs one stream op
  and keeps the DAO safe against a future caller that passes a raw list.
- [x] Bug #6 (medium). `getLocationPage` builds a wrong orphan-exclusion list when `collectionPage > 0`. `services/CollectionService.java:226-231` — the shortcut condition ignores `collectionPage`; on page 1 with few collections, `allCollectionIds` is `[]` and the orphan queries either return wrong "orphans" or 500 on an empty `NOT IN`. Public endpoint, client-supplied params. Fix: `collectionPage == 0 && totalCollections <= collectionSize`.

  **Done** (shipped separately from bug #5, as its own MR). Applied the prescribed condition.
  One correction to the finding above: it does not 500. Both
  `ContentRepository.findOrphanImagesByLocationName` and `countOrphanImagesByLocationName` guard
  `excludeCollectionIds != null && !isEmpty()` and just omit their `NOT EXISTS` clause on an empty
  list, so the failure is silent rather than loud -- on any `collectionPage > 0` where
  `totalCollections <= collectionSize`, the endpoint returned every image at the location as an
  "orphan", including images in the collections listed on the same response. Regression test:
  `CollectionServiceTest.GetLocationPage.getLocationPage_pastFirstPage_stillExcludesCollectionImages`.

## MR 9 — Config bugs and low-priority fixes

Scope note. The heading undersells what is in here: 14 items, of which only bug #8 and bug #9 are
config. The other 12 are unrelated low-priority fixes spread across controllers, image processing,
S3 streaming, and two DAOs. Ship them as two MRs -- bugs #8 and #9 first, the remaining 12 after --
rather than one 14-item MR nobody can review.

The split happened. Bugs #8 and #9 shipped as MR 9a
([#172](https://github.com/themancalledzac/edens.zac.backend/pull/172)); the remaining 12 items
shipped as MR 9b ([#173](https://github.com/themancalledzac/edens.zac.backend/pull/173), 22 files,
+707/-145). **MR 9 is complete, and with it Wave 2.**

Bug #9 is the only item here carrying a decision that is not an implementer's to make. The `:-`
placeholder fix is mechanical, but it arrives bundled with the question of whether to keep shipping
a default DB password, and that is a judgment call for the repo owner. MR 9a fixed the separators
and deliberately left that question open -- see the decision item below.

- [x] Bug #8 (medium). Tomcat maxSwallowSize integer overflow. `config/TomcatConfig.java:18` — `2 * 1024 * 1024 * 1024` overflows to negative, which Tomcat treats as unlimited swallow. Use `Integer.MAX_VALUE` or a deliberate constant.
  Confirmed by a failing test before the fix: the connector came up with
  `maxSwallowSize=-2147483648`. Every place Tomcat reads the setting guards it with
  `maxSwallowSize > -1` (`Http11Processor.checkMaxSwallowSize`, `IdentityInputFilter`,
  `ChunkedInputFilter`, `BufferedInputFilter`), so the overflow did not raise the 2GB cap, it
  removed the cap entirely and left Tomcat willing to swallow an unbounded aborted body. Now
  `Integer.MAX_VALUE`, with both connector values named as constants and the stale `// 2GB` comment
  gone. Regression test: `TomcatConfigTest`, which applies the customizer to a real connector and
  asserts the value stays positive.
- [x] Bug #9 (medium). Bash-style `:-` defaults in `application.properties` lines 1, 11-13 — `${POSTGRES_HOST:-localhost}` resolves to the literal `-localhost` when unset, because Spring's separator is `:`. Six placeholders. Also reconsider shipping a default DB password at all. Deferred here from MR 2, which
  deleted the rot in this file but left this one alone because it is a behavior change: today an
  unset `POSTGRES_HOST` silently yields the literal `-localhost`, and fixing the separator makes it
  actually fall back to `localhost`. Anything currently depending on the broken value changes
  behavior. The paired DB-password decision is flagged in the scope note above.

  All six fixed to Spring's `:` separator. Confirmed by failing tests first: with no environment set,
  `spring.datasource.url` resolved to `jdbc:postgresql://-localhost:-5432/-edens_zac`,
  `spring.datasource.username` to `-zedens`, and `spring.profiles.active` to `-default`. Regression
  test: `ApplicationPropertiesPlaceholderTest`, which also scans both shipped property files for the
  `${VAR:-` spelling. It reads `src/main/resources` directly, because
  `src/test/resources/application.properties` shadows the shipped file on the test classpath and a
  `ClassPathResource` lookup would pass vacuously. `application-dev.properties` was already clean.

  Blast radius turned out smaller than the deferral note assumed: in every deployed path these six
  placeholders are shadowed and never consulted. `docker-compose.yml` injects
  `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`
  unconditionally, and sets `SPRING_PROFILES_ACTIVE` with `:?` so an unset value aborts the compose
  run outright; CI sets the same four. The defaults are reachable only when Spring runs directly
  (`mvn spring-boot:run`, IDE, bare `java -jar`) without those variables, and nothing could have
  depended on the old values there -- `//-localhost:-5432` is not a connectable URL. The `:-`
  spelling elsewhere in the repo (`docker-compose.yml`, `deploy.sh`, `scripts/*.sh`) is correct and
  was left alone; bash is what interprets those.
- [ ] Decision, still open: whether to ship a default DB password at all. MR 9a fixed the separator
  and preserved the existing default, so `spring.datasource.password` now falls back to `password`
  instead of `-password`. That is the one line where the fix makes a default more usable rather than
  less. Options: (a) keep `${POSTGRES_PASSWORD:password}` as-is; (b) drop the default entirely with
  `${POSTGRES_PASSWORD}`, which fails the context at startup when unset, matching how
  `ACCESS_TOKEN_SECRET` and the AWS keys already behave in this file; (c) `${POSTGRES_PASSWORD:}`,
  which defers the failure to the first connection attempt. Prod is unaffected either way -- compose
  shadows this property on every deploy -- so the real question is what a local run should do when
  the variable is missing.
- [x] `downloadCollection` bypasses the response machinery with raw `sendError`/`setStatus` (`ContentDownloadControllerProd.java:94-158`). Convert to `ResponseEntity<Void>` like its sibling `downloadImage`.
  Now `ResponseEntity<Void>`, each exit copied from `downloadImage`: 401 via
  `ResponseEntity.status(UNAUTHORIZED)`, 404 via `notFound()`, and the redirect via
  `status(FOUND).location(url)`. The method keeps `throws IOException` -- `zipToS3AndPresign`
  declares it, and GlobalExceptionHandler's catch-all maps it to a 500. The four in-body comment
  blocks were promoted into the method docblock. No new test: `ContentDownloadControllerProdTest`
  and `ContentDownloadAuthTest` already assert 401, 404 and 302-with-Location here, and all of
  those still pass unchanged.
- [x] Admin message delete returns 204 for a nonexistent id (`MessagesControllerAdmin.java:49-54`). Return 404 on 0 rows, like `deleteRole`.
  Copied `AdminRoleController.deleteRole` verbatim. The endpoint had no test at all before; the new
  `DeleteMessage` tests cover both 204 and 404, and adding them exposed that the test class was
  handing the controller a null `MessageService`.
- [x] Text-content creation maps a service null to 400 instead of 500 (`AdminController.java:224-226`).
  Now a bare `RuntimeException`, which is the only route to a 500 here: GlobalExceptionHandler maps
  `IllegalArgumentException` and `IllegalStateException` both to 400, and only the catch-all
  `Exception` handler produces 500. Note this moves in the opposite direction to the
  `validateAndEnsureUniqueSlug` item below, on purpose -- a duplicate slug is the caller's fault
  (400), a service returning null is not (500). Worth recording: the null branch is unreachable
  today. `ContentService.createTextContent` either throws or returns a non-null model, so this
  corrects the status a defensive guard would report rather than a 400 any caller has seen.
- [x] `convertToWebP` leaks the ImageWriter on exception (`ImageProcessingService.java:990-994`). Dispose in a finally block.
  `writer.dispose()` was the last statement in the try-with-resources body, so a throw from
  `writer.write` skipped it. Now in a `finally`. Checked the rest of the method as well: the
  `ImageOutputStream` was already in try-with-resources and there is no `ImageReader` here, so this
  was a single-resource fix. Not meaningfully testable -- the method is private and the failure
  path needs `writer.write` to throw, which means injecting a broken ImageIO SPI.
- [x] `S3MultipartOutputStream.close()` after `abort()` tries to complete a dead upload (`S3MultipartOutputStream.java:109-133`). Early-return when aborted.
  The class already tracked `aborted`; `close()` only checked `closed`. Added the early return.
  The one live caller (`DownloadUrlService.zipToS3AndPresign`) was not hitting this, because
  `ZipOutputStream.close()` had not yet delegated at that point. The bug is real for any caller
  doing abort-then-close directly or through try-with-resources, which the `OutputStream` contract
  invites. Regression test: `S3MultipartOutputStreamTest.close_afterAbort_doesNotCompleteUpload`.
- [x] Full-image decode just to read dimensions (`ImageMetadataExtractor.java:318-340`). Use `ImageReader.getWidth(0)`/`getHeight(0)` header reads; the pipeline decodes the same file again right after.
  Both fallbacks (`ensureDimensions` for `MultipartFile`, `ensureDimensionsFromPath` for `Path`)
  now share one `putDimensionsFromHeader` that opens an `ImageInputStream` and reads
  `getWidth(0)`/`getHeight(0)`. Stream in try-with-resources, reader disposed in a finally. Both
  `ensureDimensions` methods went package-private so tests can drive them, matching
  `ImageProcessingService.recordRenditionDimensions`. New `ImageMetadataExtractorTest` asserts the
  header read returns exactly what `ImageIO.read` returned for the same file.
- [x] `EditController.patchImages` runs a two-phase write with no spanning transaction (`EditController.java:96-126`). Push it into one transactional `CollectionService` method. The `updates == null` check there is dead.
  New `CollectionService.applyCollaboratorImageEdits` holds the guard, the canonical writes and the
  scoped visibility writes in one `@Transactional` method, so the batch is all-or-nothing. The
  controller is now a delegate. The dead `updates == null` check is gone: `@RequestBody` is
  required by default, so Spring rejects an absent or null payload before the handler runs. The
  `updates.isEmpty()` check stays, since an empty array does reach the method. Removing the
  two-phase write also left `EditController` with no use for `ContentService`, so that field and
  its import went too. Tests moved with the logic: `EditControllerTest` now asserts delegation, and
  a new `ApplyCollaboratorImageEdits` block in `CollectionServiceTest` covers the canonical/visible
  split, the guard running before any write, a failed visibility write propagating, and that the
  method still carries `@Transactional`.
- [x] `parseBooleanOrDefault` docblock and logic disagree (`ImageMetadataExtractor.java:458-463`).
  The logic was correct and the docblock was wrong, so the docblock was rewritten. It claimed a
  default "if parsing fails", but `Boolean.parseBoolean` has no failure case -- an unrecognized
  non-blank value returns false, not the default, and only null or blank returns the default. That
  is what both callers want: they read a flag whose only written value is the literal "true" and
  both pass false as the default. One small behavior change came with it -- the value is now
  trimmed before matching, so `" true "` returns true where it used to return false.
- [x] Locale-less `toLowerCase()` on emails in `AuthController.java:61` and `WebAuthnController.java:143`, while the limiters use `Locale.ROOT` for the same key.
  Both now use `Locale.ROOT`, matching `AuthLoginLimiter.key()` and `ContactMessageLimiter`. Before
  this, a Turkish default locale would have lowercased the controller's email differently from the
  limiter key for the same address. Tests set the default locale to tr-TR and assert both the
  lookup and the limiter see the same lowercased address.
- [x] `validateAndEnsureUniqueSlug` throws a bare `RuntimeException`, producing a 500 (`CollectionProcessingUtil:813`). `IllegalStateException` matches the codebase.
  Now `IllegalStateException`, which GlobalExceptionHandler maps to 400. The bare `RuntimeException`
  had no handler and fell through to the catch-all 500 with the generic "An unexpected error
  occurred" body. Regression test in `CollectionProcessingUtilTest`.
- [x] `updateCameraFilmMetadata` is the only DAO mutation without `@Transactional` (`EquipmentRepository:213-224`).
  Added, matching `saveCamera`/`saveLens`/`saveFilmType`. Confirmed it was the only mutation in the
  class missing it. Not unit-testable here -- the annotation is only observable through a Spring
  context with a real transaction manager, and these repository tests are plain Mockito.
- [x] `updateRating` returns an always-true boolean (`CollectionService.java:673-680`). Make it void.

---

## Session log

- 2026-08-22 — shipped MR 5-8 and bug #6 (#165, #166, #168, #169, #170). Wave 1 already complete.
- 2026-08-22 — recorded MR 9's real scope and split it in two (#171).
- 2026-08-22 — shipped MR 9a, bugs #8 and #9 (#172). Decided: keep the default DB password. Corrected
  the doc's premise that bug #9 was a risky behavior change. Next: MR 9b.
- 2026-08-22 — shipped MR 9b, the remaining 12 items (#173). **Wave 2 complete.** Corrected MR 1a's
  stale IN REVIEW heading and MR 11's already-deleted codec item. Added the Working rules section.
  Next: MR 10.
- 2026-08-23 — merged the doc-only scope update (#174), then opened both Wave 3 MRs. They landed
  out of order: MR 11 merged first (#176), MR 10 second (#175). No harm done -- the two branches
  were cut from the same base and touch disjoint code, and MR 11 was deliberately kept off the
  Progress table and session log so the pair could merge in either order without a conflict. That
  precaution is the only reason the inversion cost nothing; the next multi-MR wave should keep it.
  **Wave 3 complete.**
- 2026-08-23 — MR 10 (#175): decided to keep gallery passwords in plaintext, with the cost of moving
  off it written up in the MR 10 section. Found that the email and propagation flows use the
  submitted password rather than the stored one, so they were never part of the plaintext
  requirement -- the tracker had implied a wider dependency than exists.
- 2026-08-23 — MR 11 (#176): split `IllegalStateException` into client-actionable 400s and bare
  `RuntimeException` 500s rather than genericising every message. Reverted one reclassification when
  its test failed: MR 9b had deliberately made `validateAndEnsureUniqueSlug` a 400 a commit earlier.
  Recorded the convention in the `handleIllegalState` javadoc.
- 2026-08-23 — reconciled the board before handing off Wave 4. Found MR 12's line lists 64% stale
  (only 55 of 152 sampled refs still land on a comment) and the Wave 4 premise low by 53% (567
  in-method comments measured, not ~370). Rewrote MR 12 as a mechanical re-derivation with a
  three-way split, added Working rule 5, filed the Wave 3 chunked-body residual. MR 10 (#175) was
  still open at the time of writing; this doc update ships inside it, so the "Wave 3 complete" row
  becomes true exactly when it merges. Next: MR 12a, `CollectionService` only.
- 2026-08-23 — shipped MR 12a (#177) and MR 12b (#178). **Filed bug #16**: `updateImages` comments
  and logs a "batch save" that is a per-image loop, on a method otherwise meticulous about N+1 on
  every read path. Corrected the doc's per-file counts three times running (144 not 148, 58 not 61,
  51 not 58, and 12c is 68 not 82) -- the note's totals include class-member-level comments the Wave
  4 header excludes. Measured MR 13 at 154, where the over-count pattern inverts, and split it 13a/13b
  in the doc before anyone starts it. Added Working rules 6 and 7. Next: MR 12c.
- 2026-08-23 — shipped MR 12c (#180), completing MR 12. 68 comments across twelve files, promoted
  into 11 javadocs, diff comment-only with zero blank-line churn. **No bugs found** -- every
  checkable claim held, which Working rule 7 says to report as the outcome it is. Corrected the
  note's "12 identical CDN comments" to 11 (it listed 11). Left
  `CollectionAccessService.effectiveLevel` alone per the 12c guardrail and reported instead:
  `canView`/`isClient` take a raw `Long userId`, never touch `effectiveLevel`, and are safe only
  because a flyby principal's null userId cannot match `rm.user_id = :userId` -- an accident nothing
  asserts. Filed as a Wave 3 follow-up. Next: MR 13a.
- 2026-08-23 — shipped MR 13a (#181). 117 comments across the two media-pipeline services, into 14
  javadocs. **The sizing note's 117 was exact** -- the first Wave 4 estimate needing no correction,
  breaking a four-in-a-row over-count streak. Found a stale docblock by a comment/comment
  contradiction rather than a comment/code one: `saveProcessedImages` said "in a single transaction"
  while the PHASE 2 comment four methods above it (and the code) said per-image transactions. The
  comment being deleted was the accurate one, which is how it surfaced -- a sweep is unusually good
  at this because it puts both halves in view at once. Also found two `P:` instructions already
  stale in the OTHER direction ("fix wrongness with bug #10" -- bug #10 is fixed and the comment is
  now correct; and the RAW-scheduling staleness note -- already accurate), so Working rule 5's decay
  applies to the `P:` judgment notes too, not just the `D:` line numbers. Next: MR 13b.
- 2026-08-24 — reconciled the board after #180 and #181 merged. Followed up on the `git add -A`
  sweep and found the swept file is cited by Appendix B, from before the sweep -- so it belongs
  tracked and the accident fixed a real fragility. Rule 9 amended to say so, because as first
  written it invited a future session to delete a doc the tracker cites. Java diffs were +69/-70 (12c) and
  +131/-131 (13a). Re-derived MR 13b at **37, exactly the estimate** -- second exact call running,
  so the Wave 4 over-count pattern is done; stop discounting the remaining numbers. Measured 13b's
  line refs as the most decayed in the doc: `ImageMetadataExtractor` **1 of 24 (4%)**, worse than
  `ContentService`'s 8%, and `ImageMetadata` 0 of 5. Replaced them with a re-derived worklist.
  Corrected consolidation #17's refs, which were stale in the same file (`ensureDimensions` is 364
  not 318-340; the repeated date parse is 428/467, not 350-371 which is `recordKeywordFlags`).
  Added Working rule 8 (`P:` notes decay like `D:` refs) and Working rule 9 (never `git add -A`;
  12c's commit swept an untracked 321-line review doc into #180). Next: MR 13b.
- 2026-08-23 — shipped MR 13b (#183), which completes MR 13 and leaves 93 in-method comments in
  `src/main`, all MR 14's. 37 comments into 13 javadocs, no bugs found. The re-derived 37 was exact,
  making three exact Wave 4 calls in a row -- the over-count era is over and the doc's remaining
  numbers should be read straight. Two comments the worklist filed as redundant narration turned out
  load-bearing: the XMP first-wins rule across multiple directories, and the EXIF-over-XMP precedence
  contract. That is the inverse of MR 13a's lesson -- 13a found `P:` notes telling it to fix things
  already fixed, 13b found `D:` notes telling it to delete things worth keeping. **Working rule 8
  generalizes: a `D:`/`P:` classification decays like a line number does, in both directions. Re-read
  the comment before trusting either verdict.**
  Costed the date-parsing fold the guardrail deferred and found the guardrail's own premise wrong:
  only `parseExifDateToLocalDateTime` detects format, `parseImageDate` sidesteps detection with a
  permissive split, so the two share knowledge and not code. Probed the built class -- they diverge
  only on malformed input, where `parseImageDate` returns month **13** and builds an S3 path from it.
  Folding is therefore a behavior change with an S3-pathing consequence, not a refactor; re-scoped
  that half of consolidation #17 accordingly. Next: MR 14.
- 2026-08-23 — shipped MR 13c after #183 merged, correcting 13b rather than carrying the defect into
  MR 14. Prompted by the right question: the PRs kept growing while being called "debloat". Splitting
  13b's `+161/-53` showed 88 lines of it was this tracker and only +20 was Java -- but measuring the
  Java **in words** rather than lines showed +178 (+42%), because javadoc's `/**`, ` * ` and `<p>`
  overhead makes real prose growth read as a near-neutral line count. **Line count is the wrong
  metric for a comment MR.** Three causes, now Working rule 10: the EXIF-over-XMP precedence rule
  written into three separate docblocks (the same three-copies failure MR 12a caught with the CDN
  invalidation comment); a dense 3-line docblock deleted and re-expanded to 11 saying the same thing;
  and a fact promoted into a private method that its public caller already documented. 13c cut -24
  lines / -192 words with nothing lost, leaving 13b+13c at -4 lines / -14 words with all 37 comments
  gone. Next: MR 14, which has 93 comments and would have repeated this at three times the scale.
- 2026-08-23 — reconciled after #183 and #184 both merged, and closed out MR 13. Ran the word-count
  retro across all six Wave 4 MRs: **-975 words total**, with 13b the only inflation, so the trend was
  sound and the defect was localized. 13a is the instructive one -- **+2 lines, -75 words** -- proving
  line count is the wrong instrument rather than just that 13b was careless. Re-derived MR 14 at **93
  comments across 20 files**, no split needed (12c shipped 82 as one). Measured its refs at **68%,
  the best in Wave 4**, which inverts the discount working rule 5 taught: **decay tracks edit churn,
  not doc age** -- Waves 1-3 and MR 12/13 all landed on `services/`, leaving `controller/`, `config/`
  and `dao/` refs pristine. Distribution is bimodal (eleven files 100%, six 0%), so the average lies;
  per-file table is in the MR 14 section. Found `AdminController` now has zero in-method comments, so
  its item is done by attrition. Verified both of MR 14's checkable claims (the `password_hash`
  docblock and the CLAUDE.md `@Profile` line) still hold. Flagged `ContentService:227` as MR 14's
  guardrail -- it is bug #16's only evidence and reads exactly like the narration MR 14 deletes.
  Next: MR 14.

---

# Wave 3 — Security hardening

## Posture summary

Strong for a solo-operated backend, and clearly designed rather than accreted. CloudFront in front, a BFF-only shared-secret perimeter in prod (constant-time compared, rotation-aware, startup-guarded against the dev default), role-gated security chain, a fail-closed per-collection collaborator interceptor, and strictly self-scoped user endpoints. Token and session hygiene is genuinely good: 256-bit SecureRandom everywhere, SHA-256 at rest for sessions/invites/share links, constant-time compares, bcrypt with a dummy-hash timing equalizer, single-use WebAuthn challenges, HttpOnly/Secure/SameSite=Strict cookies with sliding 60-day and absolute 90-day windows. The flyby share principal is carefully contained (no authorities, excluded by `hasRole("USER")`, GENERAL ceiling pinned in code, share-before-admin ordering) — no escape was found.

The weaknesses cluster in two places: the client-IP trust seam (bug #3 plus four duplicated resolvers, both in MR 5) and the gallery-password subsystem (MR 10). The rest is config rot.

Verified clean, for the record: no SQL injection anywhere in scope (named parameters throughout, no string concatenation into queries); no hardcoded secrets beyond the dev default that `ProdSecretGuard` refuses in prod; `app.access-token.secret` fails to start without a value; limiter maps are all Caffeine-bounded with TTLs; filter ordering and double-registration guards are correct; the collaborator gate covers every `/api/edit/**` mapping; no IDOR on the user-scoped surface.

On the standing X-Forwarded-For question: mostly fixed, one remnant. Every consumer now keys off X-Real-IP first (`RateLimitFilter:129-132`, `AuthController:128-131`, `WebAuthnController:211-214`, `CollectionControllerProd:218-221`), matching the agreed BFF fix. The remnant is bug #3. Delete it and the April finding is closed on the backend side. Whether the BFF strips inbound client-supplied X-Real-IP cannot be proven from this repo — see Appendix C.

## MR 10 — Gallery password subsystem — DONE ([PR #175](https://github.com/themancalledzac/edens.zac.backend/pull/175))

It followed MR 9a's shape: a mechanical fix bundled with a decision that is not an implementer's to
make. The decision is recorded below with the numbers behind it rather than settled silently.

Why this one next: Wave 3 has only two MRs, and MR 10 is the one with a real design question in it.
MR 11 is mostly small and one of its four items already turned out half-done. Also, the tracker's
own posture summary names the gallery-password subsystem as one of only two weak clusters in an
otherwise strong security posture, and the other one (the client-IP trust seam) already closed in
MR 5.

**Guardrail: do not hash the stored gallery password.** This is the obvious-looking fix and it is
wrong here. `passwordFingerprint` HMACs the plaintext password so that two galleries sharing a
password produce the same fingerprint, which is what lets one unlock cookie open a whole password
group without storing any group identifier. bcrypt is salted, so hashing the stored value destroys
that property and takes the shared-unlock feature with it. Leave the plaintext storage alone and
report what changing it would cost — if the fingerprint feature is worth less than the plaintext
risk, that is the owner's call to make with the numbers in front of them, not a change to slip into
a cleanup MR.

Verified 2026-08-22, line refs below still resolve: the plaintext compare is
`ClientGalleryAuthService.java:68`, `generateAccessToken` is at 77, `validateAccessToken` at 92,
`passwordFingerprint` at 133, `generatePasswordAccessToken` at 147, `validatePasswordAccessToken`
at 164.

Note on the second item: the file already uses `MessageDigest.isEqual` for both HMAC comparisons
(lines 117 and 182). Only the password equality at line 68 is a plain `.equals()`, so that item is a
one-line change, not a sweep.

- [x] Decide and record: gallery passwords are stored and compared in plaintext (`services/ClientGalleryAuthService.java:59-68` and flows). **Decision: keep plaintext.** Recorded in the class javadoc on `ClientGalleryAuthService` and costed out below.
- [x] At minimum, switch the compare to `MessageDigest.isEqual` for consistency with the rest of the codebase. One-line change at the former line 68; behavior identical, timing no longer leaks the matching prefix length.
- [x] Changing a gallery password does not revoke issued per-slug cookies. Fixed: the signed payload is now `slug|fingerprint|expiry` via a private `slugTokenPayload` helper shared by `generateAccessToken` and `validateAccessToken`, so the two cannot drift. `generateAccessToken` took a second `password` parameter; its only main caller is `buildAccessCookies`, which already had the value in hand.

### Why the fingerprint and not a version counter

The tracker offered both. The fingerprint needs no schema change and is already computed on every
read path; a version counter needs a new column, a migration, and an increment wired into all four
password write sites. Same guarantee, less surface.

### Deploy note

Binding the fingerprint changes the signed payload, so every per-slug gallery cookie already in a
visitor's browser stops validating at deploy. Visitors inside an active 24h window re-enter the
password once. The password-fingerprint cookie is unaffected -- it always bound the fingerprint by
construction, which is why only the per-slug token was broken.

### The plaintext decision, costed

Two live consumers read the *stored* password and would break under a one-way hash:

1. `passwordFingerprint(entity.getGalleryPassword())` -- the shared-unlock cookie group, reached
   from `CollectionService:545` and `ContentDownloadControllerProd:196` via
   `GalleryAccessCookies.hasValidAccess`.
2. `CollectionModel.galleryPassword` (`CollectionService:890`) -- the admin manage page shows the
   current password so the owner can tell a client what it is.

Two that look like they need plaintext but do not. Both use `request.password()`, the value the
admin just submitted, never a re-read of storage:

- `sendGalleryPasswordEmail` (`CollectionService:1727`).
- `propagatePasswordToChildrenIfRequested` (`CollectionService:1767`).

So emailing the password to a client and propagating it to children both survive a hash. Only the
shared unlock and the admin display do not.

**bcrypt alone** costs the shared-unlock feature outright. bcrypt is salted, so two galleries with
the same password no longer produce a common key, and a PARENT password stops opening its
propagated children without a prompt per gallery. The admin display goes too.

**The one design that keeps both** is storing bcrypt for verification *and* persisting the HMAC
fingerprint as its own column, so the fingerprint is derivable from storage without plaintext. What
that costs:

- One Flyway migration adding `gallery_password_fingerprint`, backfilled by HMAC-ing the existing
  plaintext column -- feasible precisely because it is plaintext today -- then clearing the
  plaintext in the same migration.
- **Secret rotation becomes destructive.** The fingerprint is keyed by `app.access-token.secret`.
  Today rotating that secret costs one round of invalidated cookies, because fingerprints are
  recomputed from plaintext on every read. With the plaintext gone, stored fingerprints computed
  under the old secret can never be recomputed, so rotation permanently breaks shared unlock for
  existing galleries unless the old secret is retained forever. This is the non-obvious cost and
  the main reason to hesitate.
- The admin page loses "show me the password" and becomes "set a new one", which changes how the
  owner hands passwords to clients.
- Touches: 1 migration, `ClientGalleryAuthService`, `CollectionRepository` (row mapper, insert,
  `updateGalleryPassword`, `saveGalleryAccess`), `CollectionService:890`, `CollectionModel`, plus
  the frontend manage page. Roughly a day plus a migration against live data.

**Recommendation: not worth it at current exposure.** These passwords gate photo galleries only,
the gallery owner picks them rather than the client, they carry no account privileges, and the
database is not internet-reachable. The usual argument for hashing -- users reuse passwords across
services -- does not apply while the owner is the one choosing them. Revisit if gallery passwords
ever become client-chosen, or if the database's exposure changes.

## MR 11 — Public surface hardening — DONE ([PR #176](https://github.com/themancalledzac/edens.zac.backend/pull/176))

- [x] ~~`spring.codec.max-in-memory-size=64KB` (`application.properties:72-73`) is a WebFlux property in a servlet app.~~ **The deletion already happened in MR 2** ([#161](https://github.com/themancalledzac/edens.zac.backend/pull/161)); verified absent from `application.properties` on 2026-08-22. **Decision: the cap is worth adding, and it is in.** Folded into `RateLimitFilter` rather than a second filter, since that filter already owns the `/api/public/**` predicate. See "Why the cap earned its place" below.
- [x] `IllegalStateException` handler echoes internal messages to clients as 400s. Split "bad request" from "broken invariant" rather than genericising the message, which would have thrown away genuinely useful client errors. See "How the split was drawn" below.
- [x] Contact-message table has no global growth cap. Added a global daily bucket to `ContactMessageLimiter` (`app.contact.rate-limit-global-per-day`, default 1000), checked before the per-email bucket. It is the only one of the three limits whose key an attacker cannot choose.
- [x] Share and invite raw tokens travel as URL path segments and will sit in access logs (`ShareControllerProd.java:55-66`, `InviteController.java:51-52, 75-78`). Re-confirmed 2026-08-23: accepted design for shareability, keep log retention short. No code change, as the item anticipated.

### Why the cap earned its place

Bean Validation already bounds the contact payload to 5320 characters, so the cap looked redundant.
It is not: `@Valid` runs after Jackson has materialised the whole body, so the 20MB string default
is reachable on every request, and `RateLimitFilter` allows 500/h per IP. That is 10GB through the
parser per hour per address, on a box that has already been OOM'd once by concurrent large
requests. 16KB clears the real payload by 3x.

The check runs after the rate-limit bucket is consumed, so oversized requests still count against
the sender's hourly budget instead of being rejected for free.

Known gap, deliberate: the cap reads `Content-Length`, so chunked requests arrive without one and
still reach Jackson, bounded only by the container's own post limit. Rejecting chunked outright
would be the complete fix but risks breaking a legitimate proxy, and the tracker asked for the
Content-Length filter specifically. Filed as the one open Wave 3 residual:

- [ ] **Wave 3 residual — chunked bodies bypass the public body cap.** `RateLimitFilter` reads
  `getContentLengthLong()`, which is -1 for `Transfer-Encoding: chunked`, so a chunked request
  reaches Jackson uncapped. Options: reject chunked on `/api/public/**` outright (complete, small
  risk of breaking a proxy that chunks), or wrap the input stream in a counting guard (complete, no
  client-visible behavior change, more code). Verify first whether anything in front -- CloudFront
  or the BFF -- already normalizes chunked to a fixed length, which would close this for free.
  Decide before adding code.

### How the split was drawn

`IllegalStateException` keeps its 400 and keeps echoing the thrower's message, because most
throwers are genuinely client-actionable and their messages are worth reading: "a collection
already owns slug 'x'", "cannot merge an identity into itself", "no in-flight login challenge".
Genericising all of them to protect a handful of leaky ones would have been a net loss.

What moved to a bare `RuntimeException` (500, generic body, stack trace logged) is the set that
were server faults wearing a 400:

- `WebAuthnService` -- "Authenticated handle has no app_user: <handle>", "Principal has no app_user:
  <userId>". These leaked internal identifiers to unauthenticated callers and are the sharp end of
  this item.
- `JdbcUserCredentialRepository` -- three throwers leaking app_user ids and WebAuthn handles.
- `TokenUtil`, `ImageProcessingService` -- "SHA-256 unavailable". A JVM-level impossibility, never
  the caller's fault.
- `ImageUploadPipelineService` -- "Upload interrupted while waiting for semaphore".
- `CollectionProcessingUtil` -- "Failed to find or create location with name: X". A data-layer
  failure, not bad input.
- `ContentService.castContentModel` -- a type mismatch is a programming error.

**Not changed, deliberately:** `validateAndEnsureUniqueSlug`'s "Could not generate a unique slug
after 100 attempts". MR 9b moved this one from `RuntimeException` to `IllegalStateException` on
purpose, one commit before this MR. Its test caught the reversal. Re-deciding a call that shipped
last week is not this MR's business; if 500 is the right status there it should be argued on its
own.

The convention is now recorded in the `handleIllegalState` javadoc so the next throw site picks the
right type without rediscovering this.

Two things noticed while in here and deliberately not fixed. `MessagesControllerPublic:43` returns
`ResponseEntity<?>` against the typed-return rule -- already tracked in MR 22, which also has the
better fix (a `RateLimitedException` handled globally, unifying the three different 429 body shapes
in play). Not duplicated here. And `JdbcUserCredentialRepository` lives in `config/`, not `dao/` as
this doc claimed; the path is corrected above.

---

---

# Wave 4 — MR 12 and MR 13 (complete)

## MR 12 — Comment debloat: core services

### Prep note (2026-08-23) — read before touching the lists below

The line lists in this section are from the `8c28cf3` baseline and eleven MRs have landed on those
files since. Measured drift: of 152 sampled `D:` refs, **only 55 still land on a `//` comment --
36%**. Per file: `ContentService` 8%, `CollectionService` 15%, `MetadataService` 55%,
`CollectionProcessingUtil` 81%, and `ContentModelConverter` / `PaginationUtil` / `TagViewResolver`
100%. Following these numbers would delete live code.

Keep the lists as the record of intent -- they still say which files matter, roughly how dense each
one is, and which specific comments were judged worth promoting rather than deleting. That judgment
is the part worth preserving. Do not use them as coordinates.

Re-derive the worklist instead:

```bash
grep -rn --include="*.java" -E "^\s{4,}//" src/main/java/edens/zac/portfolio/backend/services/
```

Current inline counts for this MR's files (indent >= 4, banners excluded): CollectionService 148,
ContentService 61, CollectionProcessingUtil 58, MetadataService 16, ContentModelConverter 13,
SyntheticCollectionResolver 14, UserPageAssembler 10, TagViewResolver 8, ContentMutationUtil 6,
PaginationUtil 4, TagService 3, UserMergeService 3, CollectionAccessService 3, CollectionFlags 1,
ContentImageUpdateValidator 1. Total 349.

At 349 comments across 15 files this is too big for one MR. Split it: `CollectionService` alone
(148) is one MR, `ContentService` + `CollectionProcessingUtil` (119) a second, the remaining twelve
small files (82) a third. That matches how MR 9 had to be split once its real size was known.

The `P:` (promote) entries are the ones to recover by hand from the lists below, since they encode a
judgment a grep cannot reproduce -- find each by reading the named docblock, not by line number.

### Guardrail for MR 12: the diff contains comment lines and nothing else

The tempting wrong move here is not deleting too many comments. It is touching the code underneath
them. Deleting a comment puts your eyes on a badly named variable, a method that wants extracting,
or a branch the comment was apologising for -- and a "while I'm here" fix feels free because the MR
is already zero-risk.

It is not free. A comment MR is the only kind whose correctness the compiler and the test suite can
fully confirm: if the diff is comments only, a green build **is** the proof. Add one non-comment
line and that guarantee is gone for the whole MR, across hundreds of hunks nobody will review
closely because they are "just comments". Wave 2 spent five MRs on bugs precisely because behavior
changes need their own scrutiny; smuggling one in here gets it none.

So: delete and promote comments only. No renames, no extractions, no simplifications, no reordering.
`git diff` on the MR should show only removed `//` lines and added javadoc.

The report half, which matters more than the prohibition: **some of these comments are describing
real bugs.** The review already caught one (`ImageProcessingService:229`, "fix wrongness with bug
#10" in MR 13). Expect more -- a comment that contradicts the code it sits above is a bug report
someone wrote and forgot. When you find one, file it as a numbered item in this doc and say so in
the PR. Do not fix it inline, and do not delete the comment either; leave it until its own MR lands,
because the stale comment is the only remaining evidence of the problem.

Same rule for the `// ====` section banners: out of scope per the Wave 4 header, leave them, except
the stale "TYPE-SPECIFIC PROCESSING" one at `CollectionProcessingUtil:927` which the header already
condemns.

- [x] `CollectionService.java` — **MR 12a, done.** All 144 in-method comments removed; the
      load-bearing ones promoted into 13 javadocs. See "MR 12a outcome" below. The original list is
      kept underneath as the record of intent, not as coordinates.
  - Original (stale, `8c28cf3`) — D: 127, 141, 146, 213, 233, 244, 290, 303, 306, 309, 317, 330, 336, 344, 347, 384, 393, 397, 496, 503, 567, 582, 585, 590, 595, 601, 606, 610, 651, 744, 765, 774, 777, 782, 809, 812, 820, 899, 907, 911, 917, 970, 989, 1011, 1044, 1047, 1062, 1074, 1082, 1112, 1137, 1140, 1334, 1399, 1411, 1446, 1456, 1473, 1485, 1520, 1553. P: 103/109/113 (resolution order, into the `getCollectionWithPagination` docblock), 135 (spec D1 invariant), 149, 152, 223 (keep with bug #6), 299/325/740 (CDN invalidation, three copies, into one class-level sentence), 357 (checkstyle-load-bearing final), 415, 574, 613, 620, 628/635, 828, 840, 865, 1016, 1028, 1056, 1100, 1107, 1121, 1164. Delete as redundant with the docblock: 124/280, 1344/1348, 1567 (stale), 1659.

### MR 12a outcome (2026-08-23) — shipped as [#177](https://github.com/themancalledzac/edens.zac.backend/pull/177)

Diff: 127 insertions, 152 deletions, one file (plus this doc). Every changed line is a comment line -- verified
mechanically, not by eye. `mvn clean install` green (1315 tests, 0 failures), `mvn checkstyle:check`
0 violations, `mvn spotless:apply` produced no reformatting.

Count correction to the prep note: `CollectionService` held **144** comments at indent >= 4, not 148.
148 is the count of all lines starting with `//`, which includes the four class-member-level lines
that the Wave 4 header puts out of scope. Worth applying the same correction when sizing 12b and 12c.

Every promoted `P:` entry survived re-derivation and landed in a javadoc, including the ones the
original list singled out: the resolution order and spec-D1 invariant (`getCollectionWithPagination`,
which had no docblock at all), the bug #6 first-page shortcut (`getLocationPage`, also undocumented),
the three CDN-invalidation copies collapsed into one class-level sentence, the checkstyle-load-bearing
`final` on `parentEntity`, and the `selfProvider` cache-proxy rationale.

Two deliberate exceptions, both worth carrying into 12b and 12c:

1. **Two trailing comments kept** (`updateCollectionTags`, `updateCollectionPeople`: `null // No
   tracking needed for collection updates`). The odd line break in those calls exists only because
   of the trailing comment. Removing it lets google-java-format collapse the call onto one line --
   a non-comment diff line, which would break the guardrail's whole guarantee. Not worth it for two
   comments; they belong to whichever MR touches that code for real.
2. **The class-member comment on `selfProvider` left in place** (out of scope per the Wave 4
   header). Its content is now also in the `getUpdateCollectionData` docblock, where the
   self-invocation actually happens. Deliberate duplication -- each is load-bearing at its own site.

**No comment in this file turned out to describe an undiscovered bug.** The guardrail said to expect
some; in `CollectionService` there were none. Every comment that made a checkable claim
(`populateSiblings(model, true)` being LISTED-only, the `collectionPage == 0` shortcut, D8 clearing
running first and unconditionally, both join-row writers running the cycle check and the password
propagation) was verified against the code and held. One staleness found, already filed:

- Confirms the MR 14 item for the `filterNonListedChildCollections` docblock. It is still stale, and
  the deleted inline comment above `collectionRepository.findByIds(referencedIds)` -- "used for both
  context detection and visibility filter" -- shows why: the batch-loaded children no longer feed any
  context detection, since `parentIsProtected` now reads `model.getIsPasswordProtected()` directly.
  The docblock still describes the removed derivation. The docblock itself was **not** touched here;
  it is MR 14's to rewrite. Its line reference has moved off `1536-1547` -- re-derive it by name.
- [x] `CollectionProcessingUtil.java` — **MR 12b, done.** 51 in-method comments removed plus the stale `TYPE-SPECIFIC PROCESSING` banner. Original (stale) list — D: 88, 93, 98, 111, 120, 160, 168, 226, 233, 244, 273, 291, 305, 316, 328, 336, 393, 400, 414, 430, 486, 616, 645, 693, 696, 699, 793, 804, 875, 883, 891, 904, trailing 801, 810. P: 188, 587, 593. Delete-redundant: 515, 626.
- [x] `ContentService.java` — **MR 12b, done.** 57 of 58 in-method comments removed; one kept deliberately as bug #16's evidence. Original (stale) list — D: 114, 123, 134, 139, 144, 151, 159, 175, 181, 189, 198, 206, 210, 219, 224, 230, 233, 250, 278, 297, 305, 315, 322, 331, 379, 382, 450, 458, 465, 468, 477, 504, 507, 510, 638, 934, 940. P: 417 (batch-vs-N+1, into the `searchImages` docblock), 977 (Rule B, into the `linkContentToCollection` docblock). Delete-redundant: 347, 579, 597, 614, 854.

### MR 12b outcome (2026-08-23) — shipped as [#178](https://github.com/themancalledzac/edens.zac.backend/pull/178)

Diff: 82 insertions, 121 deletions, two files (plus this doc). Build green (1315 tests, 0 failures), checkstyle 0
violations, spotless produced no reformatting.

Re-derived counts: `ContentService` 58 and `CollectionProcessingUtil` 51, so 109 in scope rather than
the prep note's 119 -- the same over-count as 12a, and for the same reason. `CollectionProcessingUtil`
also carries 21 comments at indent < 4, of which 18 are the six legitimate section banners (left in
place per the Wave 4 header) and 3 were the stale `TYPE-SPECIFIC PROCESSING` banner, removed as the
prep note explicitly authorizes. That banner now heads a section containing exactly one method,
`applyPaginationDefaults`, which is not type-specific at all.

Guardrail note, so 12c inherits the rule: the diff is comment lines plus **two blank lines**, and
nothing else. Both blanks were forced. Each sat immediately before a comment block that ended a
method body, so removing the comment left a dangling blank line before the closing brace that
spotless drops. Not a code change, but worth naming rather than claiming a perfectly pure diff.

Two trailing comments in `validateAndEnsureUniqueSlug` (`// Slug is unique`, `// Limit to prevent
infinite loop`) were removed and then deliberately restored. Unlike 12a's pair there was no reflow
risk here -- the code text stayed byte-identical -- but stripping a trailing comment still shows up
as a modified code line, which defeats the cheap "grep the diff for non-comment lines" audit that
makes this MR class worth running. Same rule as 12a: trailing comments belong to whichever MR
touches that code for real.

One promoted comment corrected a docblock as a side effect, which is worth flagging since it edges
on MR 14's territory. `populateCollectionsOnContent`'s docblock said "on image content items"; the
method has handled GIFs for some time, and the inline comment being promoted said exactly that. The
docblock now reads "image AND GIF content items". This is the promote operation, not a separate fix
-- but if 12c or 13 hits the same shape, prefer promoting over rewriting and say so.

### Bug #16 (medium, found by MR 12b) — `updateImages` claims a batch save it does not do

`services/ContentService.java` — the comment reading `// Batch save all successfully updated images
for efficiency` sits above a loop that calls `contentRepository.saveImage(image)` once per image, and
the log line underneath reports `"Batch saved {} updated images"`. `ContentRepository.saveImage` is a
single-row INSERT/UPDATE and the repository has no batch variant, so a batch of N image edits issues
N statements.

What makes this a real defect rather than a wording slip: `updateImages` is meticulous about N+1 on
every read path -- it batch-fetches the images, then their current tags, people and locations in one
query each, with two comments explicitly labelled OPTIMIZED -- and then does exactly N+1 on the write
path. The comment and the log are the only evidence anyone intended otherwise.

Not fixed here, and **the comment is deliberately left in place** per the Wave 4 guardrail: it is the
only remaining marker of the problem until its own MR lands. That MR should add a batch save to
`ContentRepository` and fix the log line with it. Both `updateImages` and the collaborator edit path
(`CollectionService.applyCollaboratorImageEdits`, which routes through `updateImages`) benefit.

Not to be confused with consolidation item #15, which separately notes that `getUpdateCollectionData`
fetches the collection row twice. That one is already filed and untouched.
### MR 12c worklist — re-derived 2026-08-23, use this instead of the line refs below

Measured on `3111e00` (after 12a and 12b merged), `grep -cE '^[[:space:]]{4,}//'` with banners
excluded. **68 total, not the 82 the prep note estimated** -- the third consecutive over-count, and
for the same reason every time: the note's per-file totals include class-member-level comments that
the Wave 4 header puts out of scope.

| File | In-method comments |
|---|---|
| `MetadataService.java` | 16 |
| `SyntheticCollectionResolver.java` | 10 |
| `UserPageAssembler.java` | 10 |
| `TagViewResolver.java` | 8 |
| `ContentModelConverter.java` | 6 |
| `PaginationUtil.java` | 4 |
| `ContentMutationUtil.java` | 3 |
| `TagService.java` | 3 |
| `UserMergeService.java` | 3 |
| `CollectionAccessService.java` | 3 |
| `CollectionFlags.java` | 1 |
| `validator/ContentImageUpdateValidator.java` | 1 |

Three of these -- `ContentModelConverter`, `PaginationUtil`, `TagViewResolver` -- are the files the
prep note measured at 100% line-reference accuracy, so their `D:`/`P:` lists below are actually
usable rather than needing full re-derivation. The rest are not.

`MetadataService` is the one with real shape to it: 12 identical "Metadata mutation: drop the CDN
copy" comments collapse into a single class-docblock sentence, exactly as 12a did for
`CollectionService`'s three CDN copies. That pattern is now established -- follow it.

**Guardrail for 12c: leave `CollectionAccessService.effectiveLevel` alone.** Its item below says the
comment should go "into the `effectiveLevel` docblock being rewritten anyway" -- that phrase is a
leftover from when MR 12 was scoped alongside a consolidation, and the rewrite it refers to is not
part of Wave 4. Twelve small files with 68 comments between them is a low-risk mechanical MR right
up until someone decides one of them wants restructuring, and `effectiveLevel` is the access-control
resolution function, which is the worst possible place in this set to take an unreviewed detour.
Promote the comment into the docblock as it stands, and if the method genuinely needs reworking,
report what changing it would do rather than doing it here.

The same instinct will come up on `CollectionFlags` and `PaginationUtil`, both of which are small
enough to feel rewritable in one sitting. Same answer: comments only.

The original per-file lists follow, kept as the record of intent (which comments were judged worth
promoting) rather than as coordinates. **All twelve done in [#180](https://github.com/themancalledzac/edens.zac.backend/pull/180)** -- see "MR 12c outcome" below.

- [x] `ContentModelConverter.java` — D: 379, 386, 473, 480, trailing 170. P: 654.
- [x] `ContentMutationUtil.java` — D: 384. P: 132.
- [x] `SyntheticCollectionResolver.java` — P: 38 (trimmed), 78, 95.
- [x] `TagService.java` — P: 65. D: 116.
- [x] `TagViewResolver.java` — D: 57, 65, 84. P: 71 (ordering-restoration rationale).
- [x] `MetadataService.java` — D: the 12 identical "Metadata mutation: drop the CDN copy..." comments at 55, 76, 101, 123, 144, 169, 192, 260, 313, 384, 410. Replace with one class-docblock sentence. P: 208.
- [x] `PaginationUtil.java` — D: 24, 27, 42, 45.
- [x] `UserPageAssembler.java` — P: 95 (V35 identity-merge, load-bearing), 117. D: 127. Trailing 206 is acceptable as a switch-arm note.
- [x] `UserMergeService.java` — P: 87. `CollectionAccessService.java` — P: 68, into the `effectiveLevel` docblock being rewritten anyway. `CollectionFlags.java` — D: 79, trailing 24.
- [x] `validator/ContentImageUpdateValidator.java` — D: 31. (51 died with the dead method in MR 1.)

### MR 12c outcome (2026-08-23) — shipped as [#180](https://github.com/themancalledzac/edens.zac.backend/pull/180)

All 68 in-method comments removed across the twelve files; every file now measures 0. The
load-bearing ones were promoted into 11 javadocs, 6 of them newly added.

**The diff is comment lines and nothing else, with zero blank-line churn.** `git diff -U0` filtered
for anything that is not a `//`, `/**`, `*` or `*/` line returns empty, and the blank-line count is
0 -- so Working rule 6's "name the dangling blank line in the PR" caveat did not apply here. Build
green, 1315 tests, 0 failures. For a comments-only diff that is the whole proof.

**No bugs found.** Per Working rule 7 this is the correct outcome to report, not a shallow pass.
Every checkable claim in the 68 held under verification: the 11 CDN-invalidation comments matched
their `markChanged()` calls, `deletePerson`'s guarded-delete reasoning matched the zero-row check,
`TagViewResolver`'s ordering-restoration comment matched the re-key-and-re-stream code, and
`UserMergeService`'s gross-versus-net counts matched `countImageTags`/`countCollectionTags`.

Count correction, the fourth in a row but the first in the other direction: the note said "the 12
identical CDN comments" and then listed 11 line numbers. There are 11. The file's total of 16 is
unchanged -- 11 CDN + 3 guarded-delete + 2 upsert.

Two comments the original lists marked `P:` were deleted instead, because the docblock above them
already said the same thing: `ContentMutationUtil`'s "merge with existing locations" (the
`associateLocationsByName` docblock already says "Additive: merges with the content's existing
locations rather than replacing them") and `PaginationUtil`'s four normalization notes.

#### The `effectiveLevel` report the guardrail asked for

The guardrail said to promote `CollectionAccessService:68` as it stands and report what changing the
method would do rather than doing it. Promoted as instructed; the method body is untouched. The
finding, which confirms the unverified lead already filed at the bottom of this doc:

**`canView` and `isClient` do not resolve through `effectiveLevel`, and the `effectiveLevel` docblock
says they do.** They are two-line passthroughs to `RoleRepository.canView`/`isClient`, and -- unlike
`hasAtLeast`, which really does call `effectiveLevel` -- they take a raw `Long userId` rather than an
`AuthPrincipal`. A principal is never in scope, so the GENERAL share-link ceiling cannot apply to
them.

Five call sites are affected: `UserShareControllerProd:92`, `ContentDownloadControllerProd:192`,
`UserRatingOverrideService:37`, `UserSelectsService:77`, `CollectionService:539`.

They are safe today, by two independent accidents. A flyby principal carries a null `userId`, and
`rm.user_id = :userId` never matches on NULL under SQL comparison semantics, so both queries count 0
and return false. On top of that, three of the five sites guard first anyway -- `UserShareControllerProd`
with `AuthPrincipal.isRealUser`, and `ContentDownloadControllerProd` and `CollectionService` with an
explicit `userId != null`.

Neither accident is asserted anywhere. No test pins the null-userId behavior of
`RoleRepository.canView`, and nothing stops a future caller from resolving a share principal to a
real user id before calling `canView` -- at which point a link holder gets view access the GENERAL
ceiling was written to deny, and the docblock will still claim the ceiling covers them.

What changing it would cost: routing both through `effectiveLevel` means changing their signatures
from `Long userId` to `AuthPrincipal`, updating those five call sites, and accepting that `canView`
becomes `hasAtLeast(principal, id, GENERAL)` -- which changes its result for a share principal from
false to true inside the share's scope. That is a real behavior change on the download and selects
paths, and it needs its own MR with its own tests. Filed as a Wave 3 follow-up rather than done here.
The cheap half -- correcting the docblock to say that only `hasAtLeast` and
`CollaboratorAccessInterceptor` resolve through `effectiveLevel`, and that `canView`/`isClient` are
safe only via the null-userId accident -- is also deliberately not done here, because a docblock that
describes access-control behavior should change in the MR that verifies that behavior, not in a
comment sweep.

## MR 13 — Comment debloat: media pipeline

### Sizing, measured 2026-08-23 on `3111e00` — split this before starting

**154 in-method comments, and the over-count pattern INVERTS here.** Every Wave 4 file measured so
far came in smaller than the doc implied; MR 13 comes in larger. Do not carry the "the estimates are
high" lesson into this MR.

| File | In-method comments |
|---|---|
| `ImageProcessingService.java` | 62 |
| `ImageUploadPipelineService.java` | 55 |
| `ImageMetadataExtractor.java` | 22 |
| `ImageMetadata.java` | 5 |
| `DownloadUrlService.java` | 4 |
| `S3MultipartOutputStream.java` | 2 |
| `EmailService.java` | 2 |
| `ReadCacheInvalidator.java` | 2 |
| `JobTrackingService.java` | 0 |

At 154 this is larger than `CollectionService` alone was (144), and MR 12 had to be split at 349.
Decide the split up front rather than discovering it mid-MR, which is what happened to both MR 9 and
MR 12. The natural fault line is obvious: `ImageProcessingService` + `ImageUploadPipelineService` are
117 of the 154, so 13a is those two and 13b is the remaining seven (37).

Path correction: `ImageMetadata.java` is in `services/`, not `model/` as the list below implies.
`JobTrackingService` has zero in-method comments -- its item below is a class-docblock fix only.

- [x] **MR 13a, done ([#181](https://github.com/themancalledzac/edens.zac.backend/pull/181)).** `ImageProcessingService.java` — original (stale) list, D: 165, 170, 177, 184, 195, 208, 268, 273, 280, 287, 295, 348, 371, 377, 1278, 1287, 1295, 1303, 1330, 1339, 1347, 1355. P: 218-219 (fix staleness: RAW scheduling moved to `ImageUploadPipelineService`), 224, 229 (fix wrongness with bug #10), 343-344, 357-358, 382-383 (fix staleness: it is `ContentMutationUtil` now), 385, 388, 394-396, 408-410, 446-447, 461-463, 469, 591-592, 603-604, 611-612, 622, 630-631, 656-657, 762, 764, 798-799. Also fix the `deleteImageFromS3` docblock (769-772): web keys are content-hashed now.
- [x] **MR 13a, done ([#181](https://github.com/themancalledzac/edens.zac.backend/pull/181)).** `ImageUploadPipelineService.java` — original (stale) list, D: 143, 161, 191, 229, 332, 336, 473, 656, 664, 698, 725, 757. P: 115, 130-131, 136, 192, 197-198, 207-209, 255-257, 288, 295, 298-299, 304, 318, 324, 381, 410, 417, 420-421, 431-432, 438-439, 459, 465, 512, 612-614, 710-711, 782. The 410-471 duplicates disappear with consolidation #9 (MR 18).
### MR 13a outcome (2026-08-23) — merged as [#181](https://github.com/themancalledzac/edens.zac.backend/pull/181)

Java diff: 2 files, +131/-131. (MR 12c, [#180](https://github.com/themancalledzac/edens.zac.backend/pull/180), was 12 files, +69/-70.)

All 117 in-method comments removed from `ImageProcessingService` (62) and `ImageUploadPipelineService`
(55); both files now measure 0. The sizing note's 117 was exact -- the first Wave 4 estimate that
needed no correction. Promoted into 14 javadocs, 3 of them newly added on private methods
(`processFilesFromDiskLoop`, `ingestFilesGroupedByDayLoop`, and the `contentHash` expansion).

Diff is comment lines and nothing else. Two blank-line removals, both the separating blank that
belonged to a removed comment block rather than the Working-rule-6 dangling-blank case: one in
`prepareImageForUpload` after the RAW-deferral note, one in the dedupe UPDATE branch. No code
reflow. Build green, 1315 tests, 0 failures.

**One stale doc found and fixed** (a doc fix, in Wave 4's scope -- not a filed code bug).
`saveProcessedImages`'s docblock opened "Save prepared images to database in a single transaction",
which contradicts both the code and the PHASE 2 comment four methods above it: each image saves in
its own transaction via the `@Transactional` repository methods, precisely so one failure cannot
cascade. The comment being deleted was the accurate one, which is how the contradiction surfaced.
Corrected the docblock.

**Two instructions in the list below were already stale.** "P: 229 (fix wrongness with bug #10)" --
bug #10 is fixed and checked off; the comment at that site now correctly describes multipart having
no mtime and always taking the UPDATE branch, and `exportDateFromFile` carries the mtime rationale
in a docblock already. Nothing to fix. "P: 218-219 (fix staleness: RAW scheduling moved to
`ImageUploadPipelineService`)" -- that comment already said the scheduling is deferred and carried
through `PreparedImageData`, so it was accurate too, and was promoted as-is.

The `deleteImageFromS3` docblock fix the list asked for was real and is done: it claimed re-uploads
land on identical S3 keys "which is the norm -- keys are deterministic from filename/year/month".
True for the original and RAW keys, but the web key is content-hashed via `hashedWebFilename`, so a
changed image gets a new key and only a byte-identical re-export reuses the old one. Docblock now
says which is which.

The `410-471` duplicate block the list flags for consolidation #9 (MR 18) is untouched, as are the
two loops it refers to. Their new docblocks are deliberately written so the ingest one points at the
from-disk one for the shared behavior, which should make that consolidation easier to read, not
harder.

### MR 13b worklist — re-derived 2026-08-24 on `57a5506`, use this instead of the line refs below

**37 total, exactly what the sizing note predicted** -- the second Wave 4 estimate in a row needing
no correction. The over-count pattern that ran through MR 12 is over; take the remaining numbers at
face value.

**The line refs below are the most decayed in the whole doc.** Measured against the current tree:

| File | Comments | Doc's line refs still landing on a `//` |
|---|---|---|
| `ImageMetadataExtractor.java` | 22 | **1 of 24 (4%)** |
| `ImageMetadata.java` | 5 | **0 of 5** |
| `DownloadUrlService.java` | 4 | 2 of 4 |
| `S3MultipartOutputStream.java` | 2 | 0 of 1 |
| `EmailService.java` | 2 | 1 of 1 |
| `ReadCacheInvalidator.java` | 2 | 1 of 1 |
| `JobTrackingService.java` | 0 | class-doc fix only |

4% is the worst yet measured -- worse than `ContentService`'s 8%, which was the previous record.
Do not read the lists below as coordinates. The real comments are:

- [x] `ImageMetadataExtractor.java` (22) — extraction narration at 128, 135, 139, 180, 183, 203,
      211, 217 is redundant with the method names and goes. The load-bearing set is the XMP keyword
      logic at 274-318: hierarchical subjects first because Lightroom writes category parents,
      `"People|Jane Doe"` to a person, `"Weather|sunset"` to the leaf tag, the person-name filter
      (Lightroom emits people BOTH as `People|Name` and as standalone keywords), and the flat
      `dc:subject` fallback with no people distinction. That belongs in the
      `extractTagsAndPeopleFromXmp` docblock. Date-format notes at 431, 474-498 explain EXIF
      `YYYY:` versus ISO `YYYY-` detection -- promote, see the guardrail.
- [x] `ImageMetadata.java` (5) — 231, 256, 261, 268, 274, all describing the shutter-speed
      formatting ladder (pass through an existing fraction, `>= 1 sec` displays as-is, `< 1 sec`
      becomes `1/N`). One docblock on the formatter.
- [x] `DownloadUrlService.java` (4) — both blocks are load-bearing and promote: 104-105 (S3 keys are
      unique but `original_filename` is not, so entries are sequence-prefixed or `ZipOutputStream`
      throws on duplicate names) and 113-114 (a per-image failure writes a placeholder rather than
      tearing down the whole ZIP).
- [x] `S3MultipartOutputStream.java` (2) — 129-130, why the empty-`completedParts` guard exists
      despite a well-formed ZIP always having an end-of-central-directory record.
- [x] `EmailService.java` (2) — 140-141, the `SesV2Exception` versus `SdkClientException` split.
- [x] `ReadCacheInvalidator.java` (2) — 81-82, why the failure logs at debug rather than warn
      (expected until the CloudFront API origin is enabled).
- [x] `JobTrackingService.java` (0) — no in-method comments. The class docblock (now at 14-18, not
      14-15) says "background disk upload processing"; it also tracks ingest jobs, since
      `ingestFilesGroupedByDay` calls `createJob`. One-line fix.

### Guardrail for 13b: leave the date-parsing duplication in `ImageMetadataExtractor` alone

Promoting the comments at 431 and 474-498 puts both date parsers on screen at once, and the
duplication is obvious: `parseImageDate` (428) and `parseExifDateToLocalDateTime` (467) each do
their own EXIF-versus-ISO format detection. Folding them together will feel like the natural
finish to the promotion, because the docblock you are writing has to describe the same rule twice.

It is not in scope. That duplication is already filed as part of consolidation #17, which owns it
along with the `ensureDimensions` twins in the same file. Taking it here would move a filed
consolidation into a comments-only MR, and 13b's green build proves nothing about a refactor.

Promote the format-detection rule into both docblocks as it stands, and report what folding them
would cost -- specifically whether `parseImageDate`'s `int[]` return and
`parseExifDateToLocalDateTime`'s `LocalDateTime` return can share a parse step without one of them
growing a conversion that costs more than the duplication does.

The original per-file lists follow, kept as the record of intent rather than as coordinates.

- [x] `ImageMetadata.java` — D: 245, 270, 275, 282, 288.
- [x] `ImageMetadataExtractor.java` — D: 114, 121, 166, 169, 189, 197, 203, 208, 259, 299, 353, 396, 401, 409, 411, 415, 420, 443. P: 75, 98, 125, 269, 275, 286-287.
- [x] `S3MultipartOutputStream.java` — P: 119-120. `DownloadUrlService.java` — P: 91, 93, 104-105, 113-114. `EmailService.java` — P: 140-141. `ReadCacheInvalidator.java` — P: 81-82. `JobTrackingService.java` — the class doc at 14-15 also tracks ingest; update it.

### MR 13b outcome (2026-08-23) — shipped as [#183](https://github.com/themancalledzac/edens.zac.backend/pull/183)

Java diff: 7 files, +65/-45. All 37 in-method comments removed; all seven files now measure 0.
The re-derived 37 was exact -- third Wave 4 estimate in a row needing no correction. 93 in-method
comments left in `src/main`, all of them MR 14's.

Promoted into 13 javadocs, 2 of them newly added on private methods (`DownloadUrlService.writeZipEntries`,
`ImageMetadataExtractor.extractFromStream`). Diff is comment lines and nothing else -- verified by
`git diff -U0` filtered for anything that is not a `//`, `*`, `/**`, `*/` or blank line, which came
back empty both before and after `spotless:apply`. No blank-line removals: none of the 37 sat at the
end of a method body, so Working rule 6's dangling-blank case did not arise. Build green, 1315 tests,
0 failures -- the same count as 13a, as a comments-only MR should be.

**No bugs found.** Per Working rule 7 that is the reportable outcome, not a shallow pass. Every
checkable claim in the 37 held against its code, including the two the worklist called out as
load-bearing (the XMP person-name filter and the ZIP sequence prefix), both verified by reading the
branch they describe.

**Two comments were doing more than the worklist credited them with**, and both became docblock
sentences rather than deletions. `extractFromStream`'s "stop after first non-empty result" is a
first-wins rule across multiple XMP directories -- a file with several gets one keyword set, not a
merge, and nothing else in the file said so. The "Only set if not already extracted from EXIF" pair
at the two extractor methods is the EXIF-over-XMP precedence contract; the worklist listed both as
redundant narration to delete.

`JobTrackingService`'s class-doc fix was real and is done. Verified before editing per Working rule 8:
`createJob` has exactly two callers, `processFilesFromDisk` and `ingestFilesGroupedByDay`, so
"background disk upload processing" did understate it. Doc now names both pipelines.

### The date-parsing fold, costed (guardrail follow-up)

The guardrail's premise is off, and that is the main finding. It says the two parsers "each do their
own EXIF-versus-ISO format detection." Only one does. `parseExifDateToLocalDateTime` detects on
`charAt(4)` (`:` is EXIF, `-` is ISO). `parseImageDate` detects nothing -- it splits on `[: T-]` and
reads the first two numeric runs, which lands identically on both formats. What the two share is the
knowledge that EXIF uses `:` where ISO uses `-`, not a line of code. There is no common parse step to
extract, so the duplication being folded is about two lines of *documentation*, which 13b just wrote
into both docblocks.

Measured on the built class, the two disagree wherever the input is malformed:

| Input | `parseImageDate` | `parseExifDateToLocalDateTime` |
|---|---|---|
| `2024:05:15 14:30:00` | 2024/5 | `2024-05-15T14:30` |
| `2024-05-15T14:30:00` | 2024/5 | `2024-05-15T14:30` |
| `2024:05:15` | 2024/5 | `2024-05-15T00:00` |
| `2024:05` | 2024/5 | `null` |
| `2024:13:45 99:99:99` | **2024/13** | `null` |

So the fold is a behavior change in both directions, which is why it does not belong in a
comments-only MR:

- **Routing `parseImageDate` through the strict parser tightens it.** Truncated-but-usable dates that
  yield a year and month today would return `null`, fall through to the modify-date branch, and end
  at `LocalDate.now()`. That silently moves which `year/month` prefix an image lands under in S3, for
  exactly the malformed inputs least likely to be covered by a test.
- **It also fixes something.** `parseImageDate` currently returns month 13 for a nonsense date and
  builds an S3 path out of it. Not filed as a bug -- no comment contradicted the code, and it needs
  malformed EXIF to reach -- but it is a real robustness gap and the fold would close it.

On the specific question the guardrail asks: the `int[]` and `LocalDateTime` returns cannot share a
parse step cheaply. A shared `Optional<LocalDateTime>` core makes `parseImageDate` strict, which is
the regression above; keeping today's tolerance means retaining the permissive split inside the core
as a fallback, so both paths survive and an abstraction is added over them. A shared
"normalize EXIF to ISO" helper is worse: `parseExifDateToLocalDateTime` already does that inline, and
`parseImageDate` needs two integers, not normalized text, so the helper would add a call and a string
allocation while removing nothing.

Recommendation for consolidation #17: keep the `ensureDimensions` twins in scope -- that is the real
duplication in this file -- and re-scope the date pair from "fold the two parsers" to "decide whether
`parseImageDate` should stay permissive." That is a behavior decision with an S3-pathing consequence,
not a refactor, and it wants its own MR and a test for the month-13 case.

### MR 13c outcome (2026-08-23) — density pass over 13b's own docblocks

Java diff: 4 files, +25/-49, **net -24 lines and -192 words of prose.** No comments removed and none
added -- this MR only tightens the javadoc 13b wrote. Build green, 1315 tests, 0 failures.

Filed because the question "we keep saying debloat, but the diffs keep growing" turned out to be
correct once the numbers were separated. 13b's headline `+161/-53` was 88 lines of this tracker and
only +20 of Java, so the doc was not the problem -- but measured in words the Java was **+178 (+42%)**,
which is the opposite of a debloat. Working rule 10 records the three causes and the check that
catches them.

Combined, 13b+13c is what the MR should have been the first time: all 37 inline comments gone, 13
docblocks carrying the load-bearing rules, and **-4 lines / -14 words** overall.

Nothing was lost in the tightening. Every rule 13b identified as load-bearing is still documented --
the XMP first-wins rule, EXIF-over-XMP precedence, the person-name filter, the ZIP sequence prefix,
both date-format rules -- each now stated once, in the method that enforces it, instead of two or
three times across the file. Two docblocks (`S3MultipartOutputStream.close`,
`ImageMetadata.ShutterSpeedExtractor`) were left alone: they were already dense, and cutting them
further would have cost information rather than restatement.

---

## MR 14 outcome (2026-08-23)

Re-derived on `51fede9`: 93 in-method comments across 20 files, matching the count the tracker
predicted exactly -- the first Wave 4 worklist that did not need re-deriving. Disposition:

| | count | |
|---|---|---|
| deleted | 7 | restated the code, or the docblock already said it |
| promoted | 19 | a fact about the whole method |
| kept inline | 66 | a warning attached to one specific line |
| quarantined | 1 | `ContentService:227`, bug #16's only evidence |

Java-only **+4 lines / -51 words**. Twenty files became thirteen; seven went to zero
(`ClientGalleryAuthService`, `TextFormType`, `DatabaseInfoLogger`, `ClientIp`,
`JdbcPublicKeyCredentialUserEntityRepository`, `RequestMetricInterceptor`, `CollectionRepository`).

### The seven deletes

Three in `ClientGalleryAuthService` (`// Not password-protected -- allow access` above
`if (getGalleryPassword() == null) return true;`, and two like it), two in `TextFormType`
(`// Try to match by enum name first` above `valueOf(value.toUpperCase())`), one in
`DatabaseInfoLogger` (`// Log environment-configured values` above three `log.info` calls), and
`ClientIp:34` -- whose class javadoc already stated the same fact almost verbatim, which is
precisely the check working rule 10 asks for.

### The nineteen promotes

`JdbcPublicKeyCredentialUserEntityRepository.save`/`delete` (5 lines) -- both are whole-method
no-ops, and an `@Override` no-op is exactly what a docblock is for. `DatabaseInfoLogger` (1),
`RequestMetricInterceptor.afterCompletion` (2), `ContentRepository.searchImages` (3, a
caller-visible ordering contract), `CollectionRepository.findContentByContentIdsIn` (3, likewise),
and `AdminUserController.upgradeUser` (3) and `.merge` (2), both folded into docblocks that already
existed -- so each was compressed against what the docblock already said rather than appended.

### Why 66 stayed

The measurement is in working rule 12. `SecurityConfig`'s 24 were the test case the guardrail asked
for: a careful promoted draft that keeps every fact and states each rule once still came to 289
words against the inline 265, **+24 words (+9%)**, plus about ten lines of javadoc scaffolding. All
of the overhead is anchor-naming. A docblock must write "on `/api/auth/me`, `/api/auth/logout`,
`/api/auth/webauthn/register/**` and `/api/edit/**`" to say what an inline comment says by sitting
on the line.

The other durable cases: `RoleRepository`'s three `\s` notes sit against the text block whose
trailing `\s` they protect, and an editor deleting that `\s` would never read a docblock;
`AdminBootstrap`'s "Do not fix this into a single statement without preserving that property" has to
be next to the two statements; `CollectionControllerProd`'s six lines explain a `Cache-Control` call
that is deliberately *absent*, which no docblock can point at.

### Bug #16, re-verified

Still real. `ContentRepository.saveImage` is a single-row INSERT/UPDATE; the loop in `updateImages`
calls it once per image; the log line still reads "Batch saved {} updated images". The only
`batchUpdate` in `ContentRepository` is in `saveContentPeople`, a different table. N image edits
issue N statements. The comment stays put.

---

## MR 15 #2 outcome, 2026-08-23

One `requestMatchers("/api/read/user/**").hasRole("USER")` replacing **17** per-method `isRealUser`
guards across six controllers. Java-only **main +21/-63 (net -42)**, **test +193/-191**, total -40.
The item's "~51 lines" was exact for the guards themselves: 17 x 3 = 51, and the rest of the 63 is
two now-unused `HttpStatus` imports plus three docblocks this change made false.

### The guardrail's premise was false

The guardrail costed three placements and called "outside the toggle" the honest default that
"takes away a dev convenience". Verified against `143f471`: **there is no such convenience.** The 17
guards were unconditional -- plain `if (!AuthPrincipal.isRealUser(principal))` with no profile check
-- so an anonymous `GET /api/read/user/me/page` already 401'd in dev. Placing the matcher outside
the toggle changes nothing in any profile.

It also picked the wrong precedent. `SecurityConfig` already carries an **unconditional**
`hasRole("USER")` matcher outside the toggle, for `/api/auth/me` and `/api/auth/logout`.
`/api/edit/**` sits inside the toggle because it is a *write* surface dev wants login-free. An
unconditional session-required read surface matches `/api/auth/me`, so the matcher went next to it.
"Outside + dev-only permitAll" was rejected on the same finding: it would have *added* a dev
convenience these routes never had.

This is working rule 8 at the scale of a whole guardrail, not one `P:` note. Verified before acting;
the note was as old as the line numbers next to it.

### The count was 17, not 18

The 2026-08-23 re-derivation raised 17 to 18 and said the doc "missed the one at 34" in
`UserShareControllerProd`. Line 34 is a **javadoc line**, which a grep for `isRealUser` picks up:

```
 * identity is enforced here with {@code AuthPrincipal.isRealUser} rather than by a matcher. That
```

Its other two corrections hold and were worth having: `UserSelectsControllerProd` really had drifted
35/46/62 -> 37/48/64, and `UserRatingOverrideControllerProd` really is in `controller/user/` at
41/54. Removing the guards mechanically confirmed the total: 1 + 3 + 4 + 3 + 4 + 2 = 17.

That same docblock had to be rewritten, along with two on `AuthPrincipal` -- all three asserted that
identity is enforced in the controller "rather than by a matcher", which this MR inverts.

### Two costs the item did not price

**28 assertions across 6 test classes pinned the 401 at the controller.** All five MockMvc tests
used `MockMvcBuilders.standaloneSetup`, which builds no security chain, and
`UserShareControllerProdTest` called controller methods directly -- so none of them could survive
the move. `FlybyWriteLockoutTest` (90 lines) existed solely to pin these guards and was deleted
outright. Replaced by `config/UserRoutesAuthorizationWebMvcTest`: four tests on the real chain
against the **real** controllers as beans, not stubs, because the risk this MR introduces is a route
sitting outside the matcher's path pattern and only real `@RequestMapping` values catch that. It
keeps the `verifyNoInteractions` property the deleted class was protecting.

**A flyby's status changes 401 -> 403** on these 17 endpoints, accepted by decision. A share-link
holder is authenticated but holds no authorities, so `hasRole` denies it as
authenticated-but-unauthorized. `FlybyAccessWebMvcTest:83-84` already documents exactly this split
for `/api/admin/**`. Anonymous stays 401. Both are now pinned.

Verified equivalent otherwise: `SessionAuthenticationFilter:39` and `WebAuthnService:221` both grant
`ROLE_USER`, and `SessionService.resolve:146` always builds a non-null `userId`, so
`hasRole("USER")` and `isRealUser` accept exactly the same callers.

### Verification

Mutation-checked rather than assumed: with the matcher stripped, `UserRoutesAuthorizationWebMvcTest`
fails; restored, the full suite is 1,302 green with 0 checkstyle violations.

`UserRatingOverrideControllerProd` still has **no controller test at all** (only a service test) --
a coverage gap for MR 26, not introduced here.

---

## MR 15 #6 outcome, 2026-08-24

Four `currentUserId` copies became `config/CurrentUser.userId()`. Java-only main **-26 lines,
**+36 words**. Shipped with a second commit closing the `PersonRepository` carry.

The re-derived table was right this time -- four declarations, seven call sites, all verified
against `abcf549` before the edit, and the count came out even when the change was made (rule 13's
test). `CollectionService:549` was the copy the original three-copy item missed, and it is the one
that matters most, because it is not a controller and it is the caller whose null is hardest to
tighten.

### Where it went, and why not `AuthPrincipal`

The item's stated fix was "move it onto `AuthPrincipal`". That does not survive contact with the
code and was not done. `AuthPrincipal` is a Spring-free record in `model/`; this helper is a static
`SecurityContextHolder` read, not a property of a principal instance. Putting it there drags Spring
Security's context into a model type, and every test that constructs an `AuthPrincipal` would be
constructing something that can read ambient global state.

`config/` was the answer: `ClientIp`, `GalleryAccessCookies` and `FlybyCookies` are already static
final helper classes there, next to the security plumbing. No new package, no new pattern.

### The null-contract report the guardrail asked for

The contract was left alone, as instructed. Tightening it to `orElseThrow` would cost, concretely:

| Call site | What null means today | Cost of throwing |
|---|---|---|
| `AdminUserController:230` (audit log) | dev, gate open | log line loses its actor; harmless |
| `AdminUserController:330` `addMember` | dev, gate open | breaks local role assignment |
| `AdminRoleController:65` `createRole` | dev, gate open | breaks local role creation |
| `AdminRoleController:120` `setGrant` | dev, gate open | breaks local grant editing |
| `AdminRoleController:148` `addMember` | dev, gate open | breaks local role assignment |
| `ContentDownloadControllerProd:190` | anonymous visitor | 500 on every anonymous download |
| `CollectionService:538` | anonymous visitor | 500 on every anonymous gallery visit |

So the four admin sites break local development only, and the two read-surface sites break
production for logged-out visitors. **These are not one contract with one fix.** The admin four
would be closed properly by making `app.admin.enforce-authz=true` unconditional -- a Wave 3-shaped
decision about dev ergonomics, not a consolidation -- and the read-surface two are correct as they
stand and should never throw. Anyone revisiting this should split it in two before touching either.

### Two more copies of the same read, not folded in

Scope was the four named `currentUserId` helpers, so these were left alone, but they are the same
static read and belong to whoever picks up the follow-up:

- `SyntheticCollectionResolver:146` `currentPrincipal()` -- identical body, returns the principal
  instead of `.userId()`. `CurrentUser.userId()` is exactly this plus a field read.
- `CollectionService:1531` `viewerMaySeeHidden` -- the same read inlined, plus a `p.userId() != null`
  check, because it passes the whole principal to `hasAtLeast`.

Folding both in means adding `CurrentUser.principal()` and having `userId()` delegate to it. It is
mechanical and behavior-preserving. It was not done here because the item said four.

**This is also what the re-derivation missed, twice over.** It found four copies by grepping the
helper *name*; these two are the same code under different names. That is working rule 14.

### The `PersonRepository.findAccountUserIdsByIds` decision

Decided, not carried a fifth time. **It is a real gap and a low-severity one, and the method was
the wrong shape to fix it.** Verified: zero callers in `src/main` *and* `src/test` -- the only hit
in the tree was its own declaration.

Its docblock claimed it preserved the "only account-backed persons receive a role membership" rule.
Nothing called it, so the rule was documented and unenforced -- which is what the tracker correctly
spotted, and why "delete it as dead code" was the wrong disposition three times running.

Severity, stated honestly: both endpoints are `/api/admin/**` behind `hasRole("ADMIN")`, so the
actor is already an admin, and a tag-only `PERSON` row has a null email and password_hash so it
cannot log in. Admitting one grants nobody anything today. The risk is a **dormant grant** -- if
that person is later upgraded to an account, it inherits the role silently.

The fix went to `RoleRepository.addMember`, the single choke point both controllers already share,
rather than into two controllers (which would add the duplication MR 15 exists to remove). The
deleted method took a `List` and both call sites pass one id, which is why wiring it in was always
awkward and why it kept being deferred instead of used. Existing integration tests seed `ACTIVE`
users and were unaffected; two new tests cover a `PERSON` row and an unknown id.

### Verification

`mvn clean install` green at each commit. 1302 tests before, **1304 after**, 0 failures.

## S-1 outcome, 2026-08-24 — `UserStatus` enforced in the auth path

Two guards, both mutation-verified. `AuthController.login` and `SessionService.resolve` now require
`UserStatus.ACTIVE`. 1304 tests before, **1308 after**.

### The allowlist was the free choice, and it is the one that fails closed

The item said allowlist versus `<> 'DISABLED'` was free, having already disproved the
onboarding objection. Shipped the allowlist (`!= ACTIVE`), for a reason the item did not give:
`UserStatus` has four values, and only one of them should be able to act. A denylist admits `PERSON`
-- a tag-only identity with no login account -- and admits `INVITED`, which is reachable with a
password hash still attached because an admin can PATCH an ACTIVE user back to INVITED without
clearing it. It also means the next status added to the enum is admitted by default. This is the
same fail-closed reasoning S-1 used to settle the MR 15 #6 allowlist question, applied to itself.

### Where the login guard went, and why the position matters

Into the existing `maybeUser.isEmpty() || passwordHash == null` branch as a third disjunct, not as a
new branch after the password check. That branch already performs a dummy BCrypt against a constant
hash to equalize response time between unknown-email and wrong-password. Folding the status test in
means a non-ACTIVE account pays the identical cost and returns the identical 401, so the fix does
not open the user-enumeration timing oracle the surrounding code exists to close. A separate guard
placed after `passwordEncoder.matches` would have returned fast on a correct password for a disabled
account, which is a worse oracle than the one being defended against -- it distinguishes "disabled
with the right password" from every other failure.

The test pins this: `verify(passwordEncoder, never()).matches("correct", "{bcrypt}$2a$10$hash")`.
The real hash is never consulted, so the guard is provably ahead of the password check.

### Mutation results (working rule 15)

Both guards were stripped and the suite re-run, per rule 15's standard that a guard test only counts
if it reddens.

| Mutation | Result |
|---|---|
| Remove the status test from `SessionService.resolve` | **2 failures** -- `resolveRejectsSessionWhoseAccountWasDisabled`, `resolveRejectsSessionWhoseAccountWasReturnedToInvited` |
| Remove `getStatus() != ACTIVE` from `AuthController.login` | **2 failures** -- both `loginForNonActiveAccountReturns401AndCreatesNoSession` cases, `Status expected:<401> but was:<204>` |

The 204 is the finding stated as a test result: without the guard, a DISABLED account with a correct
password logs in successfully.

Two details that decide whether these tests can fail at all. The login test stubs the real hash to
return true and marks the stub `lenient()` -- without that stub the mutated code would consult an
unstubbed matcher, get `false`, and 401 anyway, producing a test that passes either way. And the
resolve tests must be integration tests: `AppUserRepository` is mocked in every unit test that
touches this path, and a mock returns whatever entity the test built regardless of the column, so
the guard would be invisible. Rule 15's "testing through a mock of the thing under test" again.

The resolve test also asserts the session row is still unrevoked and unexpired after the rejection,
which is what makes it a status test rather than an accidental pass through the revocation branch.

### What was NOT done, stated rather than left ambiguous

**Session revocation on status change is not in this MR.** The item scoped it as defense in depth
and asked for an explicit statement of which half shipped. `AppUserRepository.updateStatus` is still
a bare `UPDATE`. The hole it would close is already closed for access: `resolve` reads status fresh
on every request, so a disabled account's live sessions stop resolving on their next request without
anyone touching `user_session`. What revocation would add is tidying the rows and cutting the window
to zero rather than to one request. Still open, now S-8.

### The `AuthPrincipal` cost, reported instead of implemented

The guardrail asked for the price of putting status on the principal rather than paying it. It is
higher than the call-site count suggests, and the field would be inert.

Construction sites: **3 in main** (two of them are `AuthPrincipal`'s own `client` and `flyby`
factories; the third is `SessionService`) and **30 across 21 test files**, all through the 4-arg
convenience constructor the board already decided to keep. So the mechanical cost is 33 edits, or
zero real edits and one hidden defect: give the 4-arg constructor a hardcoded `ACTIVE` default and
all 30 test principals silently become ACTIVE, which is a field no test can ever exercise.

The reason not to do it is stronger than the count. **The field could only ever hold `ACTIVE`.**
`AuthPrincipal` is built once per request, inside `resolve`, immediately after the guard that
rejects every non-ACTIVE account. Any code reading `principal.status()` runs strictly after that
check has passed, so the field is a constant with a getter. A test asserting on it cannot fail --
rule 15's "reports coverage" defect, built in by construction.

And the fan-out is the shape MR 15 #2 just deleted: **21 `@AuthenticationPrincipal` parameters
across 9 files** plus **6 `SecurityContextHolder` reads**, each a place someone could add a status
check, none of which would be reachable. That is 17 copy-pasted guards growing back into 27, one
quarter after one matcher replaced them.

### Verification

`mvn clean install` green -- spotless and checkstyle included. **1308 tests, 0 failures** (1304
before; the 4 new are 2 parameterized login cases and 2 resolve cases).



---

# Moved from the tracker 2026-08-24 (working rule 11)

These were closed-out write-ups still sitting in the tracker. Content unchanged.

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

