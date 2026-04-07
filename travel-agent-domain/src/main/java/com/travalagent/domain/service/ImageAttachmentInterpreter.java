package com.travalagent.domain.service;

import com.travalagent.domain.model.valobj.ImageAttachmentInterpretation;
import com.travalagent.domain.model.valobj.ImageAttachment;

import java.util.List;

public interface ImageAttachmentInterpreter {

    ImageAttachmentInterpretation interpretTravelContext(String userMessage, List<ImageAttachment> attachments);
}
