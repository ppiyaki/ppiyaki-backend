package com.ppiyaki.prescription;

import com.ppiyaki.common.ocr.ClovaOcrClient.OcrToken;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PiiMaskingService {

    private static final Pattern RESIDENT_NUMBER = Pattern.compile("\\d{6}[-\\s]?\\d{7}");
    private static final Pattern PHONE_NUMBER = Pattern.compile("01[016789]-?\\d{3,4}-?\\d{4}");

    public List<OcrToken> identifyPiiTokens(final List<OcrToken> tokens) {
        final List<OcrToken> piiTokens = new ArrayList<>();

        for (final OcrToken token : tokens) {
            final String text = token.text();
            if (RESIDENT_NUMBER.matcher(text).find()
                    || PHONE_NUMBER.matcher(text).find()) {
                piiTokens.add(token);
            }
        }

        return piiTokens;
    }

    public String maskText(final String fullText, final List<OcrToken> piiTokens) {
        String masked = fullText;
        for (final OcrToken token : piiTokens) {
            masked = masked.replace(token.text(), "[MASKED]");
        }
        return masked;
    }

    public BufferedImage maskImage(final BufferedImage original, final List<OcrToken> piiTokens) {
        final BufferedImage masked = new BufferedImage(
                original.getWidth(), original.getHeight(), original.getType());
        final Graphics2D graphics = masked.createGraphics();
        graphics.drawImage(original, 0, 0, null);
        graphics.setColor(Color.BLACK);

        for (final OcrToken token : piiTokens) {
            graphics.fillRect(token.x(), token.y(), token.width(), token.height());
        }

        graphics.dispose();
        return masked;
    }
}
