/*
 *
 *
 * Copyright (C) 2008 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 */
package org.sipfoundry.sipxivr;

import java.net.URLDecoder;
import java.util.ListIterator;

public class Play extends CallCommand {
    private PromptList m_prompts;
    private String m_digitMask = "0123456789#*";
    private boolean m_stopped;
    private ListIterator<String> m_iter;
    private Break m_breaker;

    public Play(FreeSwitchEventSocketInterface fses) {
        super(fses);
    }

    public Play(FreeSwitchEventSocketInterface fses, PromptList prompts) {
        super(fses);
        setPromptList(prompts);
    }

    public Play(FreeSwitchEventSocketInterface fses, String prompts) {
        super(fses);
        PromptList p = new PromptList();
        p.addPrompts(prompts);
        setPromptList(p);
    }

    public void setPromptList(PromptList prompts) {
        this.m_prompts = prompts;
    }

    public void setDigitMask(String digitMask) {
        this.m_digitMask = digitMask;
    }

    public PromptList getPromptList() {
        return m_prompts;
    }

    @Override
    public boolean start() throws Throwable {
        m_finished = false;
        m_stopped = false;

        // dummy loop for break
        for (;;) { 
            // Found a bargable digit in the DTMF queue
            // Or no prompts to play
            if (m_fses.trimDtmfQueue(m_digitMask) || m_prompts == null) {
                m_finished = true;
                break;
            }

            // Start playing the first prompt
            m_iter = m_prompts.getPrompts().listIterator();
            nextPrompt();
            break;
        }
        return m_finished;
    }

    void nextPrompt() throws Throwable {
        if (m_iter.hasNext()) {
            m_finished = false;
            String prompt = m_iter.next();
            m_command = "playback\nexecute-app-arg: " + prompt;
            super.start();
        } else {
            m_finished = true;
        }
    }

    @Override
    public boolean handleEvent(FreeSwitchEvent event) throws Throwable {
        if (m_breaker != null) {
            // Feed event to breaker first, to see if it wants to handle it
            if (m_breaker.handleEvent(event)) {
                // Breaker is done.
                m_breaker = null;
                return m_finished;
            }
        }
        if (event.getEventValue("Event-Name", "").contentEquals("DTMF")) {
            String encodedDigit = event.getEventValue("DTMF-Digit");
            assert (encodedDigit != null);
            String digit = URLDecoder.decode(encodedDigit, "UTF-8");
            String duration = event.getEventValue("DTMF-Duration", "(Unknown)");
            LOG.debug(String.format("DTMF event %s %s", digit, duration));
            if (m_digitMask.contains(digit)) {
                // Add digit to the DTMF queue
                m_fses.appendDtmfQueue(digit);

                // No more prompts to play
                m_stopped = true;

                // Stop the currently playing prompt.
                m_breaker = new Break(m_fses);
                m_breaker.start();
                return m_finished;
            }
        }
        m_finished = super.handleEvent(event);
        if (m_finished) {
            if (!m_stopped) {
                // On to the next prompt (if there is one)
                nextPrompt();
            }
        }
        return m_finished;
    }
}
