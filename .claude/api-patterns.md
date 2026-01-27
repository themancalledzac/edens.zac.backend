# API Patterns

## Endpoint Structure
```
/api/read/...   - Public GET endpoints (cached)
/api/write/...  - Authenticated mutation endpoints
/api/dev/...    - Development-only (@Profile("dev"))
```

## Controller Pattern
```java
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/read/collections")
public class CollectionControllerProd {

    private final CollectionService collectionService;

    @GetMapping("/{slug}")
    public ResponseEntity<?> getCollectionBySlug(@PathVariable String slug) {
        try {
            CollectionModel collection = collectionService.getBySlug(slug);
            return ResponseEntity.ok(collection);
        } catch (IllegalArgumentException e) {
            log.warn("Collection not found: {}", slug);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("Collection not found: " + slug);
        } catch (Exception e) {
            log.error("Error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to retrieve collection: " + e.getMessage());
        }
    }
}
```

## Response Patterns

### Success
```java
return ResponseEntity.ok(model);           // 200 with body
return ResponseEntity.ok(page);            // 200 with Page<T>
return ResponseEntity.status(HttpStatus.CREATED).body(model);  // 201
```

### Errors
```java
return ResponseEntity.status(HttpStatus.NOT_FOUND)
    .body("Resource not found: " + id);

return ResponseEntity.status(HttpStatus.BAD_REQUEST)
    .body("Invalid parameter: " + param);

return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
    .body("Failed to process: " + e.getMessage());
```

## Pagination
```java
@GetMapping
public ResponseEntity<?> getAll(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "50") int size) {

    Pageable pageable = PaginationUtil.normalizeCollectionPageable(page, size);
    Page<Model> results = service.getAll(pageable);
    return ResponseEntity.ok(results);
}
```

## Request Bodies
```java
@PostMapping
public ResponseEntity<?> create(@RequestBody CreateRequest request) {
    // Validate, process, return
}

@PostMapping("/{slug}/access")
public ResponseEntity<?> validateAccess(
    @PathVariable String slug,
    @RequestBody Map<String, String> passwordRequest) {
    String password = passwordRequest.get("password");
    // ...
}
```

## Dev vs Prod Controllers
- `*ControllerDev`: Additional debug endpoints, less validation
- `*ControllerProd`: Production-ready, proper error handling
- Use `@Profile("dev")` and `@Profile("prod")` annotations
- Never expose dev endpoints in production

## Error Handling Hierarchy
1. `IllegalArgumentException` -> 404 NOT_FOUND (resource not found)
2. Validation errors -> 400 BAD_REQUEST
3. All other exceptions -> 500 INTERNAL_SERVER_ERROR + log.error()
