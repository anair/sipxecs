/*
 *
 *
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 */
package org.sipfoundry.sipxconfig.rest;

import java.util.List;

import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.FileRepresentation;
import org.restlet.resource.Representation;
import org.restlet.resource.ResourceException;
import org.restlet.resource.Variant;
import org.sipfoundry.sipxconfig.vm.Mailbox;
import org.sipfoundry.sipxconfig.vm.Voicemail;

public class VoicemailResourceDownload extends VoicemailResource {

    private String m_messsageId;

    @Override
    public void init(Context context, Request request, Response response) {
        super.init(context, request, response);
        m_messsageId = (String) getRequest().getAttributes().get("messageId");
    }

    @Override
    public Representation represent(Variant variant) throws ResourceException {
        Mailbox mailbox = getMailboxManager().getMailbox(getUser().getUserName());
        List<Voicemail> voicemails = getMailboxManager().getVoicemail(mailbox, getFolder());

        Representation representation = null;
        if (m_messsageId != null && m_messsageId != "") {
            for (Voicemail voicemail : voicemails) {
                if (voicemail.getMessageId().equals(m_messsageId)) {
                    representation = new FileRepresentation(voicemail.getMediaFile(), MediaType.AUDIO_WAV);

                }
            }
        }
        return representation;
    }

}
