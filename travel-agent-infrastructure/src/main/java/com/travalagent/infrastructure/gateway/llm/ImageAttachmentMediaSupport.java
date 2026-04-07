package com.travalagent.infrastructure.gateway.llm;

import com.travalagent.domain.model.valobj.ImageAttachment;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ImageAttachmentMediaSupport {

    private static final Pattern DATA_URL = Pattern.compile("^data:([^;]+);base64,(.+)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private ImageAttachmentMediaSupport() {
    }

    static Media[] toMediaArray(List<ImageAttachment> attachments) {
        return attachments.stream()
                .map(ImageAttachmentMediaSupport::toMedia)
                .toArray(Media[]::new);
    }

    private static Media toMedia(ImageAttachment attachment) {
        ParsedDataUrl parsed = parse(attachment);
        Resource resource = new ByteArrayResource(parsed.bytes()) {
            @Override
            public String getFilename() {
                return attachment.name();
            }
        };
        return Media.builder()
                .id(attachment.id())
                .name(attachment.name())
                .mimeType(parsed.mimeType())
                .data(resource)
                .build();
    }

    private static ParsedDataUrl parse(ImageAttachment attachment) {
        Matcher matcher = DATA_URL.matcher(attachment.dataUrl());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Attachment must be a base64 data URL");
        }
        MimeType mimeType = MimeTypeUtils.parseMimeType(matcher.group(1).trim());
        byte[] bytes = Base64.getDecoder().decode(matcher.group(2).trim());
        return new ParsedDataUrl(mimeType, bytes);
    }

    private record ParsedDataUrl(
            MimeType mimeType,
            byte[] bytes
    ) {
    }
}
