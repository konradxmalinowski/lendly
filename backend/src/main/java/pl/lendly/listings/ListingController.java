package pl.lendly.listings;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pl.lendly.auth.AuthPrincipal;
import pl.lendly.auth.RateLimiter;
import pl.lendly.common.api.ApiException;
import pl.lendly.listings.ListingDtos.ImageResponse;
import pl.lendly.listings.ListingDtos.ListingRequest;
import pl.lendly.listings.ListingDtos.OwnerListing;
import pl.lendly.listings.ListingDtos.PageResponse;
import pl.lendly.listings.ListingRepository.ImageAccess;
import pl.lendly.storage.ImageProcessor;
import pl.lendly.storage.ImageStorage;

import java.io.IOException;
import java.net.URI;
import java.nio.file.NoSuchFileException;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ListingController {
    private final ListingService listings;
    private final ListingRepository repository;
    private final ImageStorage storage;
    private final RateLimiter rateLimiter;

    public ListingController(ListingService listings, ListingRepository repository, ImageStorage storage,
                             RateLimiter rateLimiter) {
        this.listings = listings;
        this.repository = repository;
        this.storage = storage;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/items")
    ResponseEntity<OwnerListing> create(@AuthenticationPrincipal AuthPrincipal principal,
                                        @Valid @RequestBody ListingRequest request) {
        rateLimiter.listingCreate(principal.user().id());
        OwnerListing created = listings.create(principal, request);
        return ResponseEntity.created(URI.create("/api/me/items/" + created.id())).cacheControl(CacheControl.noStore())
                .body(created);
    }

    @PutMapping("/items/{id}")
    ResponseEntity<OwnerListing> update(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id,
                                        @Valid @RequestBody ListingRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(listings.update(principal, id, request));
    }

    @PostMapping("/items/{id}/publish")
    ResponseEntity<OwnerListing> publish(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(listings.publish(principal, id));
    }

    @DeleteMapping("/items/{id}")
    ResponseEntity<Void> archive(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        listings.archive(principal, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/items")
    ResponseEntity<PageResponse<OwnerListing>> mine(@AuthenticationPrincipal AuthPrincipal principal,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(listings.mine(principal, page, size));
    }

    @GetMapping("/me/items/{id}")
    ResponseEntity<OwnerListing> mineDetail(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(listings.get(principal, id));
    }

    @PostMapping(path = "/items/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ImageResponse> upload(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id,
                                         @RequestPart("file") MultipartFile file) throws IOException {
        rateLimiter.imageUpload(principal.user().id());
        if (file.isEmpty() || file.getSize() > ImageProcessor.MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "IMAGE_TOO_LARGE", "The image must be at most 5 MB.");
        }
        ImageResponse image = listings.addImage(principal, id, file.getBytes());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(image);
    }

    @DeleteMapping("/items/{id}/images/{imageId}")
    ResponseEntity<Void> deleteImage(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id,
                                     @PathVariable String imageId) {
        listings.deleteImage(principal, id, imageId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/images/{imageId}")
    ResponseEntity<byte[]> image(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String imageId)
            throws IOException {
        UUID id;
        try { id = UUID.fromString(imageId); }
        catch (RuntimeException exception) { throw imageNotFound(); }
        ImageAccess access = repository.imageAccess(id).orElseThrow(this::imageNotFound);
        boolean owner = principal != null && Objects.equals(principal.user().id(), access.ownerId());
        if (!access.publiclyVisible() && !owner) throw imageNotFound();
        CacheControl cache = access.publiclyVisible() ? CacheControl.maxAge(Duration.ofHours(1)).cachePublic()
                : CacheControl.noStore().cachePrivate();
        byte[] bytes;
        try { bytes = storage.read(access.storageKey()); }
        catch (NoSuchFileException exception) { throw imageNotFound(); }
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(cache)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(bytes);
    }

    private ApiException imageNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "IMAGE_NOT_FOUND", "The image was not found.");
    }
}
