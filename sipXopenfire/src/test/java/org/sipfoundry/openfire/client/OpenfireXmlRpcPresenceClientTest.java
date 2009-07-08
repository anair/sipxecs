package org.sipfoundry.openfire.client;

import java.util.Map;

import junit.framework.TestCase;

import org.sipfoundry.openfire.config.ConfigurationParser;
import org.sipfoundry.openfire.config.WatcherConfig;
import org.sipfoundry.openfire.plugin.presence.PresenceState;
import org.sipfoundry.openfire.plugin.presence.XmlRpcPresenceProvider;

public class OpenfireXmlRpcPresenceClientTest extends TestCase {
    String domain = "sipxpbx.example.local";
    String sipDomain = "sipxpbx.example.local";
    private OpenfireXmlRpcPresenceClient presenceClient;
    private WatcherConfig watcherConfig;
    // HARD CODED -- please change as needed.
    private String configDir = "/usr/local/sipx/etc/sipxpbx";
    
    // NOTE - you MUST BE LOGGED IN AS ADMIN from your XMPP CLIENT
    
    public void setUp() throws Exception {
        ConfigurationParser configParser = new ConfigurationParser();
        watcherConfig = configParser.parse("file://" + configDir + "/sipxopenfire.xml");
        this.domain = watcherConfig.getOpenfireHost();
        this.sipDomain = watcherConfig.getProxyDomain();
        this.presenceClient = new OpenfireXmlRpcPresenceClient(domain,watcherConfig.getOpenfireXmlRpcPort());
    }
    
    /*
     * You must sign in to the presence server as admin to run these tests.
     */
    public void testPresence() throws Exception {
        String presenceInfo = presenceClient.getXmppPresenceState("admin");
        System.out.println("presenceInfo = " + presenceInfo);
        presenceClient.setXmppPresenceState("admin", PresenceState.ONLINE);    
        String presenceState = presenceClient.getXmppPresenceState("admin");
        assertEquals( PresenceState.ONLINE, presenceState);
        presenceClient.setXmppPresenceState("admin", PresenceState.BUSY);
        presenceState = presenceClient.getXmppPresenceState("admin");
        assertEquals(presenceState,PresenceState.BUSY);
        presenceClient.setXmppCustomPresenceMessage("admin", "Hacking");
        String status = presenceClient.getXmppCustomPresenceMessage("admin");
        System.out.println("Server returned " + status);
        assertEquals(status,"Hacking");
        Map unifiedPresence = presenceClient.getUnifiedPresenceInfo("user1@" + this.domain);
        System.out.println("Unified Presence = " + unifiedPresence);
        
    }
}
