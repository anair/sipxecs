package org.sipfoundry.openfire.plugin.presence;

import java.util.Collection;
import java.util.Map;

import org.apache.log4j.Logger;

public class XmlRpcChatRoomManagementProvider extends XmlRpcProvider {
    
    private static Logger log = Logger.getLogger(XmlRpcChatRoomManagementProvider.class);
    public final static String SERVICE = "chatroom";
    public final static String SERVER = "chatRoomManagementServer";
   
    
    /**
     * Get the chat room members.
     * 
     * @param subdomain -- server subdomain.
     * @param roomName -- room name.
     * @param userName -- userName
     *
     */
    public Map getMembers(String subdomain, String roomName) {
        try {
            Collection<String> members = getPlugin().getMembers(subdomain,roomName);
            Map retval = createSuccessMap();
            retval.put(ROOM_MEMBERS, members.toArray());
            return retval;
        } catch ( Exception ex) {
         
            return createErrorMap(ErrorCode.GET_CHAT_ROOM_MEMBERS,ex.getMessage());
             
        }
    }
    
  
  
   
    
    public Map kickMember(String subdomain, String roomName, String password, String member,
            String reason) {
        try {
            Map retval = createSuccessMap();
            String memberJid = appendDomain(member);
            getPlugin().kickOccupant(subdomain, roomName, password, memberJid, reason);
            return retval;
        } catch (Exception ex) {
            return createErrorMap(ErrorCode.GET_CHAT_ROOM_ATTRIBUTES, ex.getMessage());
        }
    }
    
    public Map inviteOccupant(String subdomain, String roomName,
            String member, String password, String reason ) {
        try {
            Map retval = createSuccessMap();
            String memberJid = appendDomain(member);
            getPlugin().inviteOccupant(subdomain, roomName, memberJid, password, reason);
            return retval;
        }catch (Exception ex) {
            log.error("Exception occured during processing", ex);
            return createErrorMap(ErrorCode.GET_CHAT_ROOM_ATTRIBUTES, ex.getMessage());
        }
    }
    
    
    
}
