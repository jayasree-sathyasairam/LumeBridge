package com.sentinel.payload;

import com.lumebridge.payload.PayloadFormatDetector;
import com.lumebridge.payload.PayloadMediaFamily;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PayloadFormatDetectorTest {

    @Test
    void contentTypeJsonOverridesSniff() {
        byte[] xmlLooking = "<root/>".getBytes(StandardCharsets.UTF_8);
        assertEquals(PayloadMediaFamily.JSON, PayloadFormatDetector.detect("application/json", xmlLooking));
    }

    @Test
    void sniffJsonWhenContentTypeMissing() {
        assertEquals(PayloadMediaFamily.JSON, PayloadFormatDetector.detect(null, "{\"a\":1}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void contentTypeMsgpackOverridesSniff() {
        byte[] jsonLooking = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        assertEquals(PayloadMediaFamily.MSGPACK, PayloadFormatDetector.detect("application/msgpack", jsonLooking));
    }
}
