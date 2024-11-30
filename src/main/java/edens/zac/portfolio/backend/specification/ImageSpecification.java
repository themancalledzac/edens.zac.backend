package edens.zac.portfolio.backend.specification;

import edens.zac.portfolio.backend.entity.ImageEntity;
import edens.zac.portfolio.backend.model.ImageSearchModel;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ImageSpecification {

    public static Specification<ImageEntity> buildImageSpecification(ImageSearchModel searchModel) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // rating
            if (searchModel.getRating() != null) {
                predicates.add(criteriaBuilder.equal(root.get("rating"), searchModel.getRating()));
            }

            // ISO Range
            if (searchModel.getIsoMin() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("iso"), searchModel.getIsoMin()));
            }

            if (searchModel.getIsoMax() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("iso"), searchModel.getIsoMax()));
            }

            // F-stop range  TODO: unsure if we need to convert to a number here or not.
            if (searchModel.getFStopMin() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("fStop"), searchModel.getFStopMin()));
            }

            if (searchModel.getFStopMax() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("fStop"), searchModel.getFStopMax()));
            }

            // Shutter Speed Range
            if (searchModel.getShutterSpeedMin() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("shutterSpeed"), searchModel.getShutterSpeedMin()));
            }

            if (searchModel.getShutterSpeedMax() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("shutterSpeed"), searchModel.getShutterSpeedMax()));
            }

            // Focal Length Range
            if (searchModel.getFocalLengthMin() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("focalLength"), searchModel.getFocalLengthMin()));
            }

            if (searchModel.getFocalLengthMax() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("focalLength"), searchModel.getFocalLengthMax()));
            }

            // Camera TODO: look into this 'like' and why we have a "%" here, and in other spots
            if (searchModel.getCamera() != null) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("camera")), "%" + searchModel.getCamera().toLowerCase() + "%"));
            }

            // Lens
            if (searchModel.getLens() != null) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("lens")), "%" + searchModel.getLens().toLowerCase() + "%"));
            }

            // Black And White
            if (searchModel.getBlackAndWhite() != null) {
                predicates.add(criteriaBuilder.equal(root.get("blackAndWhite"), searchModel.getBlackAndWhite()));
            }

            // Location
            if (searchModel.getLocation() != null) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("location")), "%" + searchModel.getLocation().toLowerCase() + "%"));
            }

            // Catalog TODO: explain more fully the JOIN here
            if (searchModel.getCatalog() != null) {
                Join<Object, Object> catalogJoin = root.join("catalogs");
                predicates.add(criteriaBuilder.equal(criteriaBuilder.lower(catalogJoin.get("name")), searchModel.getCatalog().toLowerCase()));
            }

            // Date Range
            DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
            if (searchModel.getStartDate() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createDate"), searchModel.getStartDate()));
            }

            if (searchModel.getEndDate() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createDate"), searchModel.getEndDate()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
};