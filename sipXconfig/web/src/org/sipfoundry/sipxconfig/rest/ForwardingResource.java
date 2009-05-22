/*
 *
 *
 * Copyright (C) 2009 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 *
 */

package org.sipfoundry.sipxconfig.rest;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.SingleValueConverter;

import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Representation;
import org.restlet.resource.ResourceException;
import org.restlet.resource.Variant;
import org.sipfoundry.sipxconfig.admin.callgroup.AbstractRing;
import org.sipfoundry.sipxconfig.admin.callgroup.AbstractRing.Type;
import org.sipfoundry.sipxconfig.admin.forwarding.CallSequence;
import org.sipfoundry.sipxconfig.admin.forwarding.ForwardingContext;
import org.sipfoundry.sipxconfig.admin.forwarding.Ring;
import org.sipfoundry.sipxconfig.common.BeanWithId;
import org.springframework.beans.factory.annotation.Required;

import static org.restlet.data.MediaType.APPLICATION_JSON;
import static org.restlet.data.MediaType.TEXT_XML;

public class ForwardingResource extends UserResource {
    private ForwardingContext m_forwardingContext;

    @Override
    public void init(Context context, Request request, Response response) {
        super.init(context, request, response);
        getVariants().add(new Variant(TEXT_XML));
        getVariants().add(new Variant(APPLICATION_JSON));
    }

    @Override
    public Representation represent(Variant variant) throws ResourceException {
        CallSequence callSequence = m_forwardingContext.getCallSequenceForUser(getUser());
        return new CallSequenceRepresentation(variant.getMediaType(), callSequence);
    }

    @Override
    public void storeRepresentation(Representation entity) throws ResourceException {
        CallSequenceRepresentation representation = new CallSequenceRepresentation(entity);
        CallSequence newCallSequence = representation.getObject();
        CallSequence callSequence = m_forwardingContext.getCallSequenceForUser(getUser());
        final List<AbstractRing> rings = newCallSequence.getRings();
        // need to resort the rings - since positions are lost when marshalling
        Comparator<AbstractRing> comparePositions = new Comparator<AbstractRing>() {
            public int compare(AbstractRing ring1, AbstractRing ring2) {
                return ring1.getPosition() - ring2.getPosition();
            }
        };
        Collections.sort(rings, comparePositions);
        callSequence.replaceRings(rings);
        callSequence.setWithVoicemail(newCallSequence.isWithVoicemail());
        m_forwardingContext.saveCallSequence(callSequence);
    }

    @Required
    public void setForwardingContext(ForwardingContext forwardingContext) {
        m_forwardingContext = forwardingContext;
    }

    private static class RingTypeConverter implements SingleValueConverter {
        public boolean canConvert(Class type) {
            return type.equals(Type.class);
        }

        public Object fromString(String str) {
            return Type.getEnum(str);
        }

        public String toString(Object obj) {
            Type type = (Type) obj;
            return type.getName();
        }
    }

    static class CallSequenceRepresentation extends XStreamRepresentation<CallSequence> {

        public CallSequenceRepresentation(MediaType mediaType, CallSequence object) {
            super(mediaType, object);
        }

        public CallSequenceRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            super.configureXStream(xstream);
            xstream.omitField(BeanWithId.class, "m_id");
            xstream.alias("call-sequence", CallSequence.class);
            xstream.omitField(CallSequence.class, "m_user");
            xstream.alias("ring", Ring.class);
            xstream.omitField(Ring.class, "m_schedule");
            xstream.omitField(Ring.class, "m_callSequence");
            xstream.addImmutableType(Type.class);
            xstream.registerConverter(new RingTypeConverter());
        }
    }
}
