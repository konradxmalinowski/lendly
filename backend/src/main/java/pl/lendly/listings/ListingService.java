package pl.lendly.listings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import pl.lendly.auth.AuthPrincipal;
import pl.lendly.common.api.ApiException;
import pl.lendly.listings.ListingDtos.ImageResponse;
import pl.lendly.listings.ListingDtos.ListingRequest;
import pl.lendly.listings.ListingDtos.OwnerListing;
import pl.lendly.listings.ListingDtos.PageResponse;
import pl.lendly.listings.ListingRepository.ImageRow;
import pl.lendly.listings.ListingRepository.ListingFields;
import pl.lendly.listings.ListingRepository.ListingRow;
import pl.lendly.reservations.ReservationService;
import pl.lendly.storage.ImageProcessor;
import pl.lendly.storage.ImageProcessor.ProcessedImage;
import pl.lendly.storage.ImageStorage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ListingService {
    static final int MAX_IMAGES = 8;
    private static final Logger log = LoggerFactory.getLogger(ListingService.class);
    private static final double METERS_PER_DEGREE_LAT = 111_320.0;
    private static final double MIN_OFFSET_METERS = 300.0;
    private static final double MAX_OFFSET_METERS = 600.0;
    private final ListingRepository repository;
    private final ImageProcessor processor;
    private final ImageStorage storage;
    private final TransactionTemplate transactions;
    private final ReservationService reservations;
    private final SecureRandom random = new SecureRandom();

    public ListingService(ListingRepository repository, ImageProcessor processor, ImageStorage storage,
                          TransactionTemplate transactions, ReservationService reservations) {
        this.repository = repository;
        this.reservations = reservations;
        this.processor = processor;
        this.storage = storage;
        this.transactions = transactions;
    }

    public OwnerListing create(AuthPrincipal principal, ListingRequest request) {
        ListingFields fields = fields(request, null);
        ListingRow row = repository.insert(principal.user().id(), fields);
        log.info("Listing created, listingId={}", row.id());
        return owner(row, List.of());
    }

    public OwnerListing get(AuthPrincipal principal, String id) {
        ListingRow row = owned(principal, repository.findById(parse(id)).orElse(null));
        return owner(row, repository.images(row.id()));
    }

    public PageResponse<OwnerListing> mine(AuthPrincipal principal, int page, int size) {
        if (page < 0 || page > 10_000 || size < 1 || size > 50) throw invalidPaging();
        UUID ownerId = principal.user().id();
        List<ListingRow> rows = repository.findByOwner(ownerId, size, (long) page * size);
        Map<UUID, List<ImageRow>> images = repository.images(rows.stream().map(ListingRow::id).toList()).stream()
                .collect(Collectors.groupingBy(ImageRow::listingId));
        long total = repository.countByOwner(ownerId);
        List<OwnerListing> items = rows.stream().map(row -> owner(row, images.getOrDefault(row.id(), List.of()))).toList();
        return new PageResponse<>(items, page, size, total, (int) ((total + size - 1) / size));
    }

    public OwnerListing update(AuthPrincipal principal, String id, ListingRequest request) {
        UUID listingId = parse(id);
        return transactions.execute(status -> {
            ListingRow current = owned(principal, repository.lockById(listingId).orElse(null));
            ListingFields fields = fields(request, current);
            if ("ACTIVE".equals(current.status()) && fields.handoverLat() == null) {
                throw publishRule(Map.of("handoverLat", List.of("is required")));
            }
            ListingRow row = repository.update(listingId, fields);
            return owner(row, repository.images(listingId));
        });
    }

    public OwnerListing publish(AuthPrincipal principal, String id) {
        UUID listingId = parse(id);
        return transactions.execute(status -> {
            ListingRow current = owned(principal, repository.lockById(listingId).orElse(null));
            List<ImageRow> images = repository.images(listingId);
            if ("ACTIVE".equals(current.status())) return owner(current, images);
            Map<String, List<String>> missing = new LinkedHashMap<>();
            if (current.handoverLat() == null) missing.put("handoverLat", List.of("is required"));
            if (images.isEmpty()) missing.put("images", List.of("is required"));
            if (!repository.activeCategoryExists(current.categoryKey())) missing.put("categoryKey", List.of("is invalid"));
            if (!missing.isEmpty()) throw publishRule(missing);
            ListingRow row = repository.updateStatus(listingId, "ACTIVE");
            log.info("Listing published, listingId={}", listingId);
            return owner(row, images);
        });
    }

    public void archive(AuthPrincipal principal, String id) {
        UUID listingId = parse(id);
        transactions.executeWithoutResult(status -> {
            ListingRow current = owned(principal, repository.lockById(listingId).orElse(null));
            if (!"ARCHIVED".equals(current.status())) {
                reservations.prepareArchive(listingId);
                repository.updateStatus(listingId, "ARCHIVED");
                log.info("Listing archived, listingId={}", listingId);
            }
        });
    }

    public ImageResponse addImage(AuthPrincipal principal, String id, byte[] upload) {
        UUID listingId = parse(id);
        // Cheap ownership and capacity check before spending CPU on decoding a file from a non-owner.
        ListingRow precheck = owned(principal, repository.findById(listingId).orElse(null));
        if (repository.images(precheck.id()).size() >= MAX_IMAGES) throw tooManyImages();
        ProcessedImage image = processor.process(upload);
        String key = storage.store(image.jpeg());
        try {
            return transactions.execute(status -> {
                owned(principal, repository.lockById(listingId).orElse(null));
                List<ImageRow> images = repository.images(listingId);
                if (images.size() >= MAX_IMAGES) throw tooManyImages();
                ImageRow row = repository.insertImage(listingId, key, image.jpeg().length, image.width(), image.height(),
                        freeSlot(images));
                repository.touch(listingId);
                return image(row);
            });
        } catch (RuntimeException exception) {
            storage.delete(key);
            throw exception;
        }
    }

    public void deleteImage(AuthPrincipal principal, String id, String imageIdValue) {
        UUID listingId = parse(id);
        UUID imageId = parse(imageIdValue);
        String key = transactions.execute(status -> {
            ListingRow current = owned(principal, repository.lockById(listingId).orElse(null));
            List<ImageRow> images = repository.images(listingId);
            ImageRow target = images.stream().filter(image -> image.id().equals(imageId)).findFirst()
                    .orElseThrow(this::notFound);
            if ("ACTIVE".equals(current.status()) && images.size() == 1) {
                throw publishRule(Map.of("images", List.of("is required")));
            }
            repository.deleteImage(imageId);
            repository.touch(listingId);
            return target.storageKey();
        });
        storage.delete(key);
    }

    private ListingFields fields(ListingRequest request, ListingRow current) {
        String title = request.title().trim();
        String description = request.description().trim();
        Map<String, List<String>> errors = new LinkedHashMap<>();
        if (title.length() < 3) errors.put("title", List.of("has an invalid length"));
        if (description.length() < 10) errors.put("description", List.of("has an invalid length"));
        if (!repository.activeCategoryExists(request.categoryKey())) errors.put("categoryKey", List.of("is invalid"));
        BigDecimal price = null;
        if ("PAID".equals(request.pricingType())) {
            if (request.dailyPrice() == null) errors.put("dailyPrice", List.of("must not be null"));
            else price = request.dailyPrice().setScale(2, RoundingMode.UNNECESSARY);
        }
        if ((request.handoverLat() == null) != (request.handoverLng() == null)) {
            errors.put("handoverLat", List.of("is invalid"));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "The request contains invalid fields.", errors);
        }
        BigDecimal lat = scaled(request.handoverLat());
        BigDecimal lng = scaled(request.handoverLng());
        BigDecimal publicLat = null;
        BigDecimal publicLng = null;
        if (lat != null) {
            if (current != null && lat.equals(current.handoverLat()) && lng.equals(current.handoverLng())
                    && current.publicLat() != null) {
                publicLat = current.publicLat();
                publicLng = current.publicLng();
            } else {
                BigDecimal[] point = displace(lat, lng);
                publicLat = point[0];
                publicLng = point[1];
            }
        }
        return new ListingFields(title, description, request.categoryKey(), request.condition(), request.pricingType(),
                price, lat, lng, publicLat, publicLng);
    }

    /** Moves the private handover point by a random 300–600 m so the public pin cannot reveal it. */
    private BigDecimal[] displace(BigDecimal lat, BigDecimal lng) {
        double distance = MIN_OFFSET_METERS + random.nextDouble() * (MAX_OFFSET_METERS - MIN_OFFSET_METERS);
        double bearing = random.nextDouble() * 2 * Math.PI;
        double latitude = lat.doubleValue();
        double cosLat = Math.max(0.01, Math.cos(Math.toRadians(latitude)));
        double newLat = Math.max(-90, Math.min(90, latitude + distance * Math.cos(bearing) / METERS_PER_DEGREE_LAT));
        double newLng = lng.doubleValue() + distance * Math.sin(bearing) / (METERS_PER_DEGREE_LAT * cosLat);
        if (newLng > 180) newLng -= 360;
        if (newLng < -180) newLng += 360;
        return new BigDecimal[] {scaled(BigDecimal.valueOf(newLat)), scaled(BigDecimal.valueOf(newLng))};
    }

    private BigDecimal scaled(BigDecimal value) { return value == null ? null : value.setScale(6, RoundingMode.HALF_UP); }

    private int freeSlot(List<ImageRow> images) {
        for (int slot = 0; slot < MAX_IMAGES; slot++) {
            final int candidate = slot;
            if (images.stream().noneMatch(image -> image.displayOrder() == candidate)) return slot;
        }
        throw tooManyImages();
    }

    private ListingRow owned(AuthPrincipal principal, ListingRow row) {
        // Other users' listings are reported as missing so drafts and archived items stay undiscoverable.
        if (row == null || !Objects.equals(row.ownerId(), principal.user().id()) || "HIDDEN".equals(row.status())) {
            throw notFound();
        }
        return row;
    }

    static OwnerListing owner(ListingRow row, List<ImageRow> images) {
        return new OwnerListing(row.id(), row.title(), row.description(), row.categoryKey(), row.condition(),
                row.pricingType(), row.dailyPrice(), row.currency(), row.handoverLat(), row.handoverLng(),
                row.publicLat(), row.publicLng(), row.status(), images.stream().map(ListingService::image).toList(),
                row.createdAt(), row.updatedAt(), row.publishedAt(), row.moderationReason());
    }

    static ImageResponse image(ImageRow row) {
        return new ImageResponse(row.id(), ListingDtos.imageUrl(row.id()), row.width(), row.height());
    }

    static UUID parse(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "LISTING_NOT_FOUND", "The listing was not found.");
        }
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "LISTING_NOT_FOUND", "The listing was not found.");
    }

    private ApiException publishRule(Map<String, List<String>> errors) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "LISTING_INCOMPLETE",
                "The listing is missing information required for publishing.", errors);
    }

    private ApiException tooManyImages() {
        return new ApiException(HttpStatus.CONFLICT, "TOO_MANY_IMAGES", "A listing can have at most 8 images.");
    }

    private ApiException invalidPaging() {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "The request contains invalid fields.",
                Map.of("page", List.of("is invalid")));
    }
}
