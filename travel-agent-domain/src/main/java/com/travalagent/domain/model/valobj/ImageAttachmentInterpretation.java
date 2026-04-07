package com.travalagent.domain.model.valobj;

import com.travalagent.domain.model.entity.ConversationImageFacts;

public record ImageAttachmentInterpretation(
        String summary,
        ConversationImageFacts facts
) {
}
