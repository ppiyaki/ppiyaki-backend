package com.ppiyaki.prescription;

import com.ppiyaki.common.ocr.ClovaOcrClient.OcrToken;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PiiMaskingService {

    private static final Pattern RESIDENT_NUMBER = Pattern.compile("\\d{6}[-\\s]?\\d{7}");
    private static final Pattern PHONE_NUMBER = Pattern.compile("01[016789]-?\\d{3,4}-?\\d{4}");
    private static final Pattern LANDLINE = Pattern.compile("\\d{2,3}-\\d{3,4}-\\d{4}");
    private static final Pattern EMAIL = Pattern.compile("\\w+@\\w+\\.\\w+");
    private static final Pattern LICENSE_NUMBER = Pattern.compile("면허\\s*\\d+");
    private static final Pattern BIRTH_DATE = Pattern.compile("(생년|생일)\\s*\\d{4}[-.]?\\d{2}[-.]?\\d{2}");

    private static final Set<String> PII_KEYWORDS = Set.of(
            "환자명", "성명", "수진자", "처방의", "의사", "약사",
            "주소", "거소", "건강보험", "피보험자"
    );

    private static final int LINE_Y_TOLERANCE = 10;

    public List<OcrToken> identifyPiiTokens(final List<OcrToken> tokens) {
        final List<OcrToken> piiTokens = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i++) {
            final OcrToken token = tokens.get(i);
            final String text = token.text();

            if (RESIDENT_NUMBER.matcher(text).find()
                    || PHONE_NUMBER.matcher(text).find()
                    || LANDLINE.matcher(text).find()
                    || EMAIL.matcher(text).find()
                    || LICENSE_NUMBER.matcher(text).find()
                    || BIRTH_DATE.matcher(text).find()) {
                piiTokens.add(token);
                continue;
            }

            boolean isKeyword = false;
            for (final String keyword : PII_KEYWORDS) {
                if (text.contains(keyword)) {
                    isKeyword = true;
                    break;
                }
            }

            if (isKeyword) {
                final int keywordLineY = token.y();
                for (int j = i + 1; j < tokens.size(); j++) {
                    final OcrToken next = tokens.get(j);
                    if (isSameLine(keywordLineY, next.y(), token.height())) {
                        piiTokens.add(next);
                    } else {
                        break;
                    }
                }
            }
        }

        return piiTokens;
    }

    private boolean isSameLine(final int baseY, final int tokenY, final int lineHeight) {
        final int tolerance = Math.max(LINE_Y_TOLERANCE, lineHeight / 2);
        return Math.abs(baseY - tokenY) <= tolerance;
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
        final Graphics2D g = masked.createGraphics();
        g.drawImage(original, 0, 0, null);
        g.setColor(Color.BLACK);

        for (final OcrToken token : piiTokens) {
            g.fillRect(token.x(), token.y(), token.width(), token.height());
        }

        g.dispose();
        return masked;
    }
}
