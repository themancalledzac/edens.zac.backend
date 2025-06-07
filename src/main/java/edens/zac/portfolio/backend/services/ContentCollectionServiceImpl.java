package edens.zac.portfolio.backend.services;

public class ContentCollectionServiceImpl {

    // Example for how we will do pagination
//    public ContentCollectionModel getCollectionBySlug(String slug, Integer pageNumber) {
//        // 1. Get collection metadata
//        ContentCollectionEntity collection = contentCollectionRepository.findBySlug(slug)
//                .orElseThrow(() -> new CollectionNotFoundException(slug));
//
//        // 2. Service layer creates the Pageable based on input
//        Pageable pageable;
//        if (pageNumber == null || pageNumber <= 1) {
//            // Default: Get first 50 blocks (page 0, size 50)
//            pageable = PageRequest.of(0, 50);
//        } else {
//            // Pagination: pageNumber 2 = skip 50, take next 50
//            int pageIndex = pageNumber - 1; // Convert to 0-based
//            pageable = PageRequest.of(pageIndex, 50);
//        }
//
//        // 3. Use same repository method for both cases
//        Page<ContentBlockEntity> contentPage = contentBlockRepository
//                .findByCollectionId(collection.getId(), pageable);
//
//        return convertToModel(collection, contentPage);
//    }


}
