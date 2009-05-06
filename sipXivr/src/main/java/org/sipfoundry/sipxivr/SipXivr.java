/*
 *
 *
 * Copyright (C) 2008-2009 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 */
package org.sipfoundry.sipxivr;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Hashtable;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.sipfoundry.attendant.Attendant;
import org.sipfoundry.commons.log4j.SipFoundryLayout;
import org.sipfoundry.voicemail.Mwistatus;
import org.sipfoundry.voicemail.VoiceMail;


public class SipXivr implements Runnable {
    static final Logger LOG = Logger.getLogger("org.sipfoundry.sipxivr");
    private static Configuration s_config;

    private Socket m_clientSocket;
    private FreeSwitchEventSocketInterface m_fses;

    public SipXivr(Socket client) {
        m_clientSocket = client;
    }

    /**
     * Determine what to do based on the SIP request.
     * 
     * Run the autoattendant if action=autoattendant
     * Otherwise, hang up.
     */
    public void run() {
        LOG.debug("SipXivr::run Starting SipXivr thread with client " + m_clientSocket);

        try {
            m_fses = new FreeSwitchEventSocket(s_config);
            m_fses.connect(m_clientSocket);


            String sipReqParams = m_fses.getVariable("variable_sip_req_params");
            // Create a table of parameters to pass in
            Hashtable<String, String> parameters = new Hashtable<String, String>();

            if (sipReqParams != null) {
                // Split parameter fields (separated by semicolons)
                String[] params = sipReqParams.split(";");
                for (String param : params) {
                    // Split key value pairs (separated by optional equal sign)
                    String[] kvs = param.split("=", 2);
                    if (kvs.length == 2) {
                        parameters.put(kvs[0], kvs[1]);
                    } else {
                        parameters.put(kvs[0], "");
                    }
                }
            }
            
            LOG.info(String.format("SipXivr::run Accepting call-id %s from %s to %s", 
                    m_fses.getVariable("variable_sip_call_id"),
                    m_fses.getVariable("variable_sip_from_uri"),
                    m_fses.getVariable("variable_sip_req_uri")));
            
            m_fses.invoke(new Answer(m_fses));

            String action = parameters.get("action");
            if (action == null) {
                LOG.warn("Cannot determine which application to run as the action parameter is missing.");
            } else if (action.contentEquals("autoattendant")) {
                // Run the Attendant.
                Attendant app = new Attendant(s_config, m_fses, parameters);
                app.run();
            } else if (action.equals("deposit") || action.equals("retrieve")) {
            	// Run VoiceMail
            	VoiceMail app = new VoiceMail(s_config, m_fses, parameters);
            	app.run();
            } else {
                // Nothing else to run...
                LOG.warn("Cannot determine which application to run from action="+ action);
            }

            m_fses.invoke(new Hangup(m_fses));

        } catch (DisconnectException e) {
            LOG.info("SipXivr::run Far end hungup.");
        } catch (Throwable t) {
            LOG.error("SipXivr::run", t);
        } finally {
            try {
                m_fses.close();
            } catch (IOException e) {
                // Nothing to do, no where to go home...
            }
        }

        LOG.debug("SipXivr::run Ending SipXivr thread with client " + m_clientSocket);
    }

    
    /**
     * Load the configuration from the sipxivr.properties file.
     * Wait for FreeSWITCH to make a TCP connection to s_eventSocketPort.
     * Spawn off a thread to handle each connection.
     * 
     * @throws Throwable
     */
    static void init() throws Throwable {
        int eventSocketPort;
        
        // Load the configuration
        s_config = Configuration.get();

        // Configure log4j
        Properties props = new Properties();
        props.setProperty("log4j.rootLogger", "warn, file");
        props.setProperty("log4j.logger.org.sipfoundry.sipxivr", SipFoundryLayout
                .mapSipFoundry2log4j(s_config.getLogLevel()).toString());
        props.setProperty("log4j.appender.file", "org.sipfoundry.commons.log4j.SipFoundryAppender");
        props.setProperty("log4j.appender.file.File", s_config.getLogFile());
        props.setProperty("log4j.appender.file.layout", "org.sipfoundry.commons.log4j.SipFoundryLayout");
        props.setProperty("log4j.appender.file.layout.facility", "sipXivr");
        PropertyConfigurator.configure(props);
        
        // start MWI servlet on /mwi
        Mwistatus.StartMWIServlet(s_config, "/mwi");
 
        eventSocketPort = s_config.getEventSocketPort();
        LOG.info("Starting SipXivr listening on port " + eventSocketPort);
        ServerSocket serverSocket = new ServerSocket(eventSocketPort);
        for (;;) {
            Socket client = serverSocket.accept();
            SipXivr ivr = new SipXivr(client);
            Thread thread = new Thread(ivr);
            thread.start();
        }
    }

    /**
     * Main entry point for sipXivr
     * @param args
     */
    public static void main(String[] args) {
        try {
            init();
        } catch (Exception e) {
            LOG.fatal(e,e);
            e.printStackTrace();
            System.exit(1);
        } catch (Throwable t) {
            LOG.fatal(t,t);
            t.printStackTrace();
            System.exit(1);
        }
    }
}
