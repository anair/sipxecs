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
import java.net.Socket;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.log4j.Logger;

/**
 * An abstraction of the FreeSwitchEventSocket.
 * 
 * Good for Unit Tests to emulate FreeSwitch, and a place to hang some common code.
 * 
 */
public abstract class FreeSwitchEventSocketInterface {

    static final Logger LOG = Logger.getLogger("org.sipfoundry.sipxivr");
    private HashMap<String, String> m_variables;
    private LinkedBlockingQueue<FreeSwitchEvent> m_eventQueue = new LinkedBlockingQueue<FreeSwitchEvent>();
    private LinkedBlockingQueue<String> m_dtmfQueue = new LinkedBlockingQueue<String>();
    private Configuration m_config;
    private boolean m_disconnected;

    public abstract void connect(Socket socket) throws IOException;

    public abstract void cmd(String cmd);

    public abstract FreeSwitchEvent cmdResponse(String cmd);

    public abstract FreeSwitchEvent awaitLiveEvent();

    public abstract void close() throws IOException;

    public String getDtmfQueue() {
        StringBuilder que = new StringBuilder();
        for (Iterator<String> i = m_dtmfQueue.iterator(); i.hasNext();) {
            que.append((String) i.next());
        }
        return que.toString();
    }
    
    /**
     * Remove any initial digits in the dtmfQueue that are not in the digitMask.
     * 
     * @param digitMask
     * @return true if dtmfQueue still contains digits after trimming
     */
    public boolean trimDtmfQueue(String digitMask) {
        String origDtmfQueue = getDtmfQueue();
        boolean hasDigits = false ;
        for (Iterator<String> i = m_dtmfQueue.iterator(); i.hasNext();) {
            String digit = (String) i.next();
            if (digitMask.contains(digit)) {
                hasDigits = true;
                break;
            }
            i.remove(); // Toss any digits not in the digit mask
        }
        LOG.debug(String.format("trimDtmfQueue(%s) dtmfQueue was (%s) is (%s)", 
                digitMask, origDtmfQueue, getDtmfQueue()));
        return hasDigits;
    }

    /**
     * Get the first digit in the dtmfQueue.
     * 
     * @return The digit, or null if none.
     */
    public String getDtmfDigit() {
        LOG.debug(String.format("getDtmfDigit() dtmfQueue is (%s)", 
                getDtmfQueue()));
        return m_dtmfQueue.poll();
    }

    /**
     * Add a digit to the dtmfQueue.
     * 
     * @param digit
     */
    public void appendDtmfQueue(String digit) {
        m_dtmfQueue.add(digit);
        LOG.debug(String.format("appendDtmfQueue(%s) dtmfQueue is (%s)", 
                digit, getDtmfQueue()));
    }

    /**
     * Parse the initial response from "connect" into name/value pairs. Store in the variables
     * list.
     * 
     * @param vars
     */
    public void setVariables(Vector<String> vars) {
        m_variables = FreeSwitchEvent.parseHeaders(vars);
    }

    /**
     * Return a particular variable in the variables list.
     * 
     * @param name
     * @return The value of the variable, or null if it isn't found.
     */
    public String getVariable(String name) {
        return getVariables().get(name.toLowerCase());
    }

    /**
     * Await for an event to occur.
     * 
     * First checks if any are queued, and uses them if so, else blocks waiting for a live event.
     * 
     * @return
     */
    public FreeSwitchEvent awaitEvent() {
        // First check the queue
        if (!getEventQueue().isEmpty()) {
            FreeSwitchEvent event = getEventQueue().poll();
            LOG.debug(String.format("::awaitEvent Queued response (%s)", event.getContentType()));
            return event;
        }

        return awaitLiveEvent();
    }

    /**
     * Invoke (start performing) the FreeSwitch command represented by "handler". Pass all events
     * to that handler until it says it's finished.
     * 
     * "empty" events signify FreeSwitch closed the socket (the caller hungup). Throw a DisconnectException
     * event in this case.
     * 
     * @param handler
     * @throws Throwable
     */
    public void invoke(FreeSwitchEventHandler handler) {
        boolean finished = false;

        LOG.debug("FSESI::invoke handler add " + handler);

        finished = handler.start();

        while (!finished) {
            // Wait for an event to arrive on the socket
            // (or suck 'em off the queue if they arrived already)
            FreeSwitchEvent event = awaitEvent();

            LOG.debug(String.format("FSEII::invoke Send event(%s) to (%s)", event.toString(), handler
                    .toString()));
            finished = handler.handleEvent(event);

            if (!m_disconnected && event.isEmpty()) {
                finished = true;
                m_disconnected = true;
                LOG.info("FSESI::invoke throw DisconnectException");
                throw new DisconnectException();
            }
        }

        LOG.debug("FSESI::invoke handler remove " + handler);
    }

    public void setVariables(HashMap<String, String> variables) {
        m_variables = variables;
    }

    public HashMap<String, String> getVariables() {
        return m_variables;
    }

    public void setEventQueue(LinkedBlockingQueue<FreeSwitchEvent> eventQueue) {
        m_eventQueue = eventQueue;
    }

    public LinkedBlockingQueue<FreeSwitchEvent> getEventQueue() {
        return m_eventQueue;
    }

    public void setConfig(Configuration config) {
        m_config = config;
    }

    public Configuration getConfig() {
        return m_config;
    }

}
