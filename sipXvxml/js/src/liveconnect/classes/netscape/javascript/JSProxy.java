/* -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 *
 * The contents of this file are subject to the Netscape Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/NPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express oqr
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * The Original Code is Mozilla Communicator client code, released
 * March 31, 1998.
 *
 * The Initial Developer of the Original Code is Netscape
 * Communications Corporation.  Portions created by Netscape are
 * Copyright (C) 1998 Netscape Communications Corporation. All
 * Rights Reserved.
 *
 * Contributor(s): 
 *
 * Alternatively, the contents of this file may be used under the
 * terms of the GNU Public License (the "GPL"), in which case the
 * provisions of the GPL are applicable instead of those above.
 * If you wish to allow use of your version of this file only
 * under the terms of the GPL and not to allow others to use your
 * version of this file under the NPL, indicate your decision by
 * deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL.  If you do not delete
 * the provisions above, a recipient may use your version of this
 * file under either the NPL or the GPL.
 */

/**
 * The JSProxy interface allows applets and plugins to
 * share javascript contexts.
 */

package netscape.javascript;
import java.applet.Applet;

public interface JSProxy {
    Object  getMember(JSObject jso, String name);
    Object  getSlot(JSObject jso, int index);
    void    setMember(JSObject jso, String name, Object value);
    void    setSlot(JSObject jso, int index, Object value);
    void    removeMember(JSObject jso, String name);
    Object  call(JSObject jso, String methodName, Object args[]);
    Object  eval(JSObject jso, String s);
    String      toString(JSObject jso);
    JSObject    getWindow(Applet applet);
}
