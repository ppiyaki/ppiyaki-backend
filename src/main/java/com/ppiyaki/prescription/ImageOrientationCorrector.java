package com.ppiyaki.prescription;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ImageOrientationCorrector {

    public byte[] correctOrientation(final byte[] imageBytes, final String format) {
        try {
            final int orientation = readExifOrientation(imageBytes);
            if (orientation <= 1) {
                return imageBytes;
            }

            log.info("EXIF orientation={}, correcting image", orientation);
            final BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));
            final BufferedImage corrected = applyOrientation(original, orientation);

            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(corrected, format.equalsIgnoreCase("png") ? "png" : "jpg", outputStream);
            return outputStream.toByteArray();

        } catch (final Exception e) {
            log.warn("EXIF orientation correction failed, using original: {}", e.getMessage());
            return imageBytes;
        }
    }

    private int readExifOrientation(final byte[] imageBytes) {
        try {
            final Metadata metadata = ImageMetadataReader.readMetadata(
                    new ByteArrayInputStream(imageBytes));
            final ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (directory != null && directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (final Exception e) {
            log.debug("No EXIF orientation found: {}", e.getMessage());
        }
        return 1;
    }

    private BufferedImage applyOrientation(final BufferedImage image, final int orientation) {
        final int width = image.getWidth();
        final int height = image.getHeight();

        final AffineTransform transform = new AffineTransform();
        int resultWidth = width;
        int resultHeight = height;

        switch (orientation) {
            case 2 -> transform.scale(-1, 1);
            case 3 -> {
                transform.translate(width, height);
                transform.rotate(Math.PI);
            }
            case 4 -> transform.scale(1, -1);
            case 5 -> {
                resultWidth = height;
                resultHeight = width;
                transform.scale(-1, 1);
                transform.rotate(Math.PI / 2);
            }
            case 6 -> {
                resultWidth = height;
                resultHeight = width;
                transform.translate(height, 0);
                transform.rotate(Math.PI / 2);
            }
            case 7 -> {
                resultWidth = height;
                resultHeight = width;
                transform.scale(-1, 1);
                transform.translate(0, -width);
                transform.rotate(-Math.PI / 2);
            }
            case 8 -> {
                resultWidth = height;
                resultHeight = width;
                transform.translate(0, width);
                transform.rotate(-Math.PI / 2);
            }
            default -> {
                return image;
            }
        }

        final BufferedImage result = new BufferedImage(resultWidth, resultHeight, image.getType());
        final Graphics2D graphics = result.createGraphics();
        graphics.drawImage(image, transform, null);
        graphics.dispose();
        return result;
    }
}
