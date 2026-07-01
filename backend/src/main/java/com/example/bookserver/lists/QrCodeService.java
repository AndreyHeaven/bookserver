package com.example.bookserver.lists;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/** Renders QR codes as PNG bytes for public share URLs. */
@Service
public class QrCodeService {

    public static final int DEFAULT_SIZE = 400;
    private static final String PNG_FORMAT = "PNG";

    public byte[] pngForUrl(String url, int size) {
        try {
            Map<EncodeHintType, Object> hints = Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 1,
                    EncodeHintType.CHARACTER_SET, "UTF-8");
            BitMatrix matrix = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, size, size, hints);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, PNG_FORMAT, out);
            return out.toByteArray();
        } catch (com.google.zxing.WriterException e) {
            throw new IllegalStateException("Failed to encode QR code for " + url, e);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write QR PNG for " + url, e);
        }
    }

    public byte[] pngForUrl(String url) {
        return pngForUrl(url, DEFAULT_SIZE);
    }
}
