package com.travalagent.domain.model.valobj;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public record ImageAttachment(
        String id,
        String name,
        String mediaType,
        String dataUrl,
        int sizeBytes
) {

    public ImageAttachment {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id.trim();
        name = name == null || name.isBlank() ? "uploaded-image" : name.trim();
        mediaType = mediaType == null ? "" : mediaType.trim().toLowerCase(Locale.ROOT);
        dataUrl = dataUrl == null ? null : dataUrl.trim();
        sizeBytes = Math.max(sizeBytes, 0);
    }

    public Map<String, Object> metadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("id", id);
        metadata.put("name", name);
        metadata.put("mediaType", mediaType);
        metadata.put("sizeBytes", sizeBytes);
        return metadata;
    }
}
