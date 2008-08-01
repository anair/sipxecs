/*
 *
 *
 * Copyright (C) 2008 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 *
 */
package org.sipfoundry.sipxconfig.conference;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sipfoundry.sipxconfig.xmlrpc.ApiProvider;
import org.sipfoundry.sipxconfig.xmlrpc.XmlRpcRemoteException;
import org.springframework.beans.factory.annotation.Required;

public class ActiveConferenceContextImpl implements ActiveConferenceContext {

    private static final String COMMAND_LIST = "list";
    private static final Log LOG = LogFactory.getLog(ActiveConferenceContextImpl.class);
    
    private ApiProvider<FreeswitchApi> m_freeswitchApiProvider;
    private ConferenceBridgeContext m_conferenceBridgeContext;
    private FreeswitchApiResultParser m_freeswitchApiParser = new FreeswitchApiResultParserImpl();

    @Required
    public void setFreeswitchApiProvider(ApiProvider<FreeswitchApi> freeswitchApiProvider) {
        m_freeswitchApiProvider = freeswitchApiProvider;
    }

    @Required
    public void setConferenceBridgeContext(ConferenceBridgeContext conferenceBridgeContext) {
        m_conferenceBridgeContext = conferenceBridgeContext;
    }

    public int getActiveConferenceCount(Bridge bridge) {
        LOG.debug("Requesting count of active conferences from bridge " + bridge.getName());
        FreeswitchApi api = m_freeswitchApiProvider.getApi(bridge.getServiceUri());
        String result = null;
        try {
            result = api.conference(COMMAND_LIST);
        } catch (XmlRpcRemoteException xrre) {
            throw new FreeswitchApiConnectException(bridge, xrre);
        }

        int conferenceCount = m_freeswitchApiParser.getActiveConferenceCount(result);
        LOG.debug(String.format("Bridge \"%s\" reports %d active conferences.", bridge.getName(), conferenceCount));
        return conferenceCount;
    }

    public List<ActiveConference> getActiveConferences(Bridge bridge) {
        LOG.debug("Requesting list of active conferences from bridge " + bridge.getName());
        FreeswitchApi api = m_freeswitchApiProvider.getApi(bridge.getServiceUri());
        String result = null;
        try {
            result = api.conference(COMMAND_LIST);
        } catch (XmlRpcRemoteException xrre) {
            throw new FreeswitchApiConnectException(bridge, xrre);
        }
        List<ActiveConference> conferences = m_freeswitchApiParser.getActiveConferences(result);

        for (ActiveConference activeConference : conferences) {
            Conference conference = m_conferenceBridgeContext.findConferenceByName(activeConference.getName());
            if (conference != null) {
                activeConference.setConference(conference);
            }
        }

        LOG.debug(String.format("Bridge \"%s\" reports the following active conferences: %s", 
                bridge.getName(), conferences));
        return conferences;
    }

    public List<ActiveConferenceMember> getConferenceMembers(Conference conference) {
        LOG.debug("Requesting list of members for conference \"" + conference.getName() + "\"");
        Bridge bridge = conference.getBridge();
        String conferenceName = conference.getName();
        FreeswitchApi api = m_freeswitchApiProvider.getApi(bridge.getServiceUri());
        String result = null;
        List<ActiveConferenceMember> members = new ArrayList<ActiveConferenceMember>();

        try {
            result = api.conference(conferenceName + " " + COMMAND_LIST);
            if (m_freeswitchApiParser.verifyConferenceAction(result, conference)) {
                members = m_freeswitchApiParser.getConferenceMembers(result);
            }
        } catch (XmlRpcRemoteException xrre) {
            throw new FreeswitchApiConnectException(bridge, xrre);
        }

        LOG.debug(String.format("Bridge \"%s\" reports the following members for conference \"%s\": %s", 
                bridge.getName(), conference.getName(), members));
        return members;
    }

    public boolean lockConference(Conference conference) {
        return conferenceAction(conference, "lock");
    }

    public boolean unlockConference(Conference conference) {
        return conferenceAction(conference, "unlock");
    }

    public boolean deafUser(Conference conference, ActiveConferenceMember member) {
        return memberAction(conference, member, "deaf");
    }

    public boolean muteUser(Conference conference, ActiveConferenceMember member) {
        return memberAction(conference, member, "mute");
    }

    public boolean undeafUser(Conference conference, ActiveConferenceMember member) {
        return memberAction(conference, member, "undeaf");
    }

    public boolean unmuteUser(Conference conference, ActiveConferenceMember member) {
        return memberAction(conference, member, "unmute");
    }

    public boolean kickUser(Conference conference, ActiveConferenceMember member) {
        return memberAction(conference, member, "kick");
    }

    private boolean conferenceAction(Conference conference, String command) {
        LOG.debug(String.format("Executing command \"%s\" on conference \"%s\"", 
                command, conference.getName()));        
        Bridge bridge = conference.getBridge();
        String conferenceName = conference.getName();
        FreeswitchApi api = m_freeswitchApiProvider.getApi(bridge.getServiceUri());

        try {
            String result = api.conference(String.format("%s %s", conferenceName, command));
            return m_freeswitchApiParser.verifyConferenceAction(result, conference);
        } catch (XmlRpcRemoteException xrre) {
            throw new FreeswitchApiConnectException(bridge, xrre);
        }
    }

    private boolean memberAction(Conference conference, ActiveConferenceMember member, String command) {
        LOG.debug(String.format("Executing command '%s' (conference='%s', member='%s')...", 
                command, conference.getName(), member.getName()));
        Bridge bridge = conference.getBridge();
        String conferenceName = conference.getName();
        FreeswitchApi api = m_freeswitchApiProvider.getApi(bridge.getServiceUri());

        String result = "";
        try {
            result = api.conference(String.format("%s %s %d", conferenceName, command, member.getId()));
            return m_freeswitchApiParser.verifyMemberAction(result, member);
        } catch (XmlRpcRemoteException xrre) {
            throw new FreeswitchApiConnectException(bridge, xrre);
        } finally {
            LOG.debug(String.format("Command '%s' (conference='%s', member='%s') completed with result: '%s'", 
                    command, conference.getName(), member.getName(), result));
//            LOG.debug("Pausing for 1.5 seconds to let FreeSWITCH catch up...");
//            try { Thread.sleep(1000); }
//            catch (InterruptedException ie) { LOG.debug("Interrupted"); }
        }
    }
}
