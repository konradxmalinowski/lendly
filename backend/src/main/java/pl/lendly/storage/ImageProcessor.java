package pl.lendly.storage;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pl.lendly.common.api.ApiException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Decodes an uploaded JPEG or PNG and re-encodes it as a fresh JPEG. Re-encoding drops all source
 * metadata, including EXIF GPS positions written by phone cameras.
 */
@Component
public class ImageProcessor {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final int MAX_SOURCE_SIDE = 6000;
    private static final long MAX_SOURCE_PIXELS = 24_000_000L;
    // Decoding holds the full bitmap in memory (4 bytes per pixel), so concurrent decodes are capped.
    private final Semaphore decodeSlots = new Semaphore(2);
    private static final int MAX_OUTPUT_SIDE = 1600;
    private static final float JPEG_QUALITY = 0.85f;

    public ProcessedImage process(byte[] source) {
        if (source.length == 0 || source.length > MAX_BYTES) throw invalid("IMAGE_TOO_LARGE", "The image must be at most 5 MB.");
        String format = detectFormat(source);
        acquireSlot();
        try {
            BufferedImage decoded = decode(source, format);
            BufferedImage output = flattenAndScale(decoded);
            byte[] jpeg = encodeJpeg(output);
            return new ProcessedImage(jpeg, output.getWidth(), output.getHeight());
        } finally {
            decodeSlots.release();
        }
    }

    private void acquireSlot() {
        try {
            if (decodeSlots.tryAcquire(10, TimeUnit.SECONDS)) return;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "IMAGE_PROCESSING_BUSY", "Image processing is busy. Try again shortly.");
    }

    private String detectFormat(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) return "jpeg";
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        if (bytes.length >= png.length) {
            boolean match = true;
            for (int index = 0; index < png.length; index++) if (bytes[index] != png[index]) { match = false; break; }
            if (match) return "png";
        }
        throw invalid("UNSUPPORTED_IMAGE_TYPE", "Only JPEG and PNG images are accepted.");
    }

    private BufferedImage decode(byte[] bytes, String format) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format);
            if (input == null || !readers.hasNext()) throw unreadable();
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                // Dimensions come from the header, so oversized images are rejected before pixel allocation.
                if (width < 1 || height < 1 || width > MAX_SOURCE_SIDE || height > MAX_SOURCE_SIDE
                        || (long) width * height > MAX_SOURCE_PIXELS) {
                    throw invalid("IMAGE_DIMENSIONS_INVALID", "The image dimensions are not allowed.");
                }
                BufferedImage image = reader.read(0);
                if (image == null) throw unreadable();
                return image;
            } finally {
                reader.dispose();
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw unreadable();
        }
    }

    private BufferedImage flattenAndScale(BufferedImage source) {
        double scale = Math.min(1.0, (double) MAX_OUTPUT_SIDE / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private byte[] encodeJpeg(BufferedImage image) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IllegalStateException("No JPEG writer is available.");
        ImageWriter writer = writers.next();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(buffer)) {
            writer.setOutput(output);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), params);
        } catch (IOException exception) {
            throw new IllegalStateException("JPEG encoding failed.", exception);
        } finally {
            writer.dispose();
        }
        return buffer.toByteArray();
    }

    private ApiException unreadable() {
        return invalid("IMAGE_UNREADABLE", "The image could not be read.");
    }

    private ApiException invalid(String code, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    public record ProcessedImage(byte[] jpeg, int width, int height) {}
}
