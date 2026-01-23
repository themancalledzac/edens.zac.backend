package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.model.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Service interface for managing content blocks, tags, and people.
 * Provides methods for CRUD operations on tags, people, and image content blocks.
 */
public interface ContentService {

    /**
     * Create a new tag
     *
     * @param tagName The name of the tag to create
     * @return Map containing created tag data
     */
    Map<String, Object> createTag(String tagName);

    /**
     * Create a new person
     *
     * @param personName The name of the person to create
     * @return Map containing created person data
     */
    Map<String, Object> createPerson(String personName);

    /**
     * Create a new camera
     *
     * @param cameraName The name of the camera to create
     * @return Map containing created camera data
     */
    Map<String, Object> createCamera(String cameraName);

    /**
     * Update one or more images
     *
     * @param updates List of image update requests
     * @return Map containing update results (updatedIds, updatedCount, errors)
     */
    Map<String, Object> updateImages(List<ContentImageUpdateRequest> updates);

    /**
     * Delete one or more images
     *
     * @param imageIds List of image IDs to delete
     * @return Map containing deletion results (deletedIds, deletedCount, errors)
     */
    Map<String, Object> deleteImages(List<Long> imageIds);

    /**
     * Get all tags ordered alphabetically
     *
     * @return List of all tags
     */
    List<ContentTagModel> getAllTags();

    /**
     * Get all people ordered alphabetically
     *
     * @return List of all people
     */
    List<ContentPersonModel> getAllPeople();

    /**
     * Get all cameras ordered alphabetically
     *
     * @return List of all cameras
     */
    List<ContentCameraModel> getAllCameras();

    /**
     * Get all film types ordered alphabetically by display name
     *
     * @return List of all film types
     */
    List<edens.zac.portfolio.backend.model.ContentFilmTypeModel> getAllFilmTypes();

    /**
     * Get all lenses ordered alphabetically
     *
     * @return List of all lenses
     */
    List<edens.zac.portfolio.backend.model.ContentLensModel> getAllLenses();

    /**
     * Get all locations ordered alphabetically
     *
     * @return List of all locations
     */
    List<edens.zac.portfolio.backend.model.LocationModel> getAllLocations();

    /**
     * Create a new film type
     *
     * @param filmTypeName The technical name of the film type (e.g., "KODAK_PORTRA_400")
     * @param displayName The human-readable display name (e.g., "Kodak Portra 400")
     * @param defaultIso The default ISO value for this film stock
     * @return Map containing created film type data
     */
    Map<String, Object> createFilmType(String filmTypeName, String displayName, Integer defaultIso);

    /**
     * Get all images ordered by createDate descending
     *
     * @return List of all images
     */
    List<ContentImageModel> getAllImages();

    /**
     * Create one or more images and associate them with a collection
     *
     * @param collectionId ID of the collection to associate images with
     * @param files List of image files to upload
     * @return List of created image models
     */
    List<ContentImageModel> createImages(Long collectionId, List<MultipartFile> files);

//    /**
//     * Create text content
//     *
//     * @param request Text content creation request
//     * @return Created text content model
//     */
////    ContentTextModel createTextContent(oldCreateTextContentRequest request);
//
//    /**
//     * Create code content
//     *
//     * @param request Code content creation request
//     * @return Created code content model
//     */
//    ContentCodeModel createCodeContent(CreateCodeContentRequest request);

    /**
     * Create code content
     *
     * @param request content creation request
     * @return Created content model of any 'content' type
     */
    ContentModel createTextContent(CreateTextContentRequest request);


}
