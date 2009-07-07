// 
// 
// Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
// Contributors retain copyright to elements licensed under a Contributor Agreement.
// Licensed to the User under the LGPL license.
// 
// $$
//////////////////////////////////////////////////////////////////////////////

#include <cppunit/extensions/HelperMacros.h>
#include <cppunit/TestCase.h>
#include <sipxunit/TestUtilities.h>

#include <os/OsDefs.h>
#include <net/SipDialogEvent.h>


/**
 * Unit test for SipDialogEvent
 */
class SipDialogEventTest : public CppUnit::TestCase
{
   CPPUNIT_TEST_SUITE(SipDialogEventTest);
   CPPUNIT_TEST(testDialogPackageParser);
   CPPUNIT_TEST(testDialogPackageFromPolycom);
   CPPUNIT_TEST(testDialogPackageWithParams);
   CPPUNIT_TEST(testCopyAndSearchDialog);
   CPPUNIT_TEST_SUITE_END();

public:

   void testDialogPackageParser()
      {
         const char *package = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            "<dialog-info xmlns=\"urn:ietf:params:xml:ns:dialog-info\" version=\"0\" state=\"full\" entity=\"sip:moh@panther.pingtel.com:5120\">\n"
            "<dialog id=\"2\" call-id=\"call-1116603513-890@10.1.1.153\" local-tag=\"264460498\" remote-tag=\"1c10982\" direction=\"recipient\">\n"
            "<state>confirmed</state>\n"
            "<local>\n"
            "<identity>moh@panther.pingtel.com:5120</identity>\n"
            "<target uri=\"sip:moh@10.1.1.26:5120\">\n"
            "</target>\n"
            "</local>\n"
            "<remote>\n"
            "<identity>4444@10.1.1.153</identity>\n"
            "</remote>\n"
            "</dialog>\n"
            "</dialog-info>\n"
            ;
       
         // Construct a SipDialogEvent from the XML.
         SipDialogEvent body(package);

         // Convert it back to XML, and see if it is the same.
         UtlString bodyString;
         ssize_t bodyLength;
       
         body.getBytes(&bodyString, &bodyLength);
       
         CPPUNIT_ASSERT(strcmp(bodyString.data(), package) == 0);

         // Extract the Dialog, and see if its call-Id matches.
         UtlSListIterator* itor = body.getDialogIterator();
         Dialog* pDialog = dynamic_cast <Dialog*> ((*itor)());
         delete itor;

         UtlString callId;
         pDialog->getCallId(callId);

         UtlString refCallId = "call-1116603513-890@10.1.1.153";
         CPPUNIT_ASSERT(strcmp(callId.data(), refCallId.data()) == 0);

         // See that the reported event body length is right.
         ssize_t otherLength = body.getLength();
         CPPUNIT_ASSERT_EQUAL_MESSAGE("content length is not equal",
                                      bodyLength, otherLength);
      }

   void testDialogPackageFromPolycom()
      {
         const char *package = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            "<dialog-info xmlns=\"urn:ietf:params:xml:ns:dialog-info\" version=\"4\" state=\"partial\" entity=\"sip:222@panther.pingtel.com\">\n"
            "<dialog id=\"ida648720b\" call-id=\"62c3a00e-2f2662f8-53cfd2f7@10.1.20.231\" local-tag=\"B7691142-47C44851\" remote-tag=\"1900354342\" direction=\"initiator\">\n"
            "<state>terminated</state>\n"
            "<local>\n"
            "<target uri=\"sip:222@panther.pingtel.com\">\n"
            "</target>\n"
            "</local>\n"
            "<remote>\n"
            "<identity>sip:100@panther.pingtel.com</identity>\n"
            "<target uri=\"sip:10.1.1.26:5100\"/>\n"
            "</remote>\n"
            "</dialog>\n"
            "</dialog-info>\n"
            ;
       
         // Construct a SipDialogEvent from the XML.
         SipDialogEvent body(package);

         // Convert it back to XML, and see if it is the same.
         UtlString bodyString;
         ssize_t bodyLength;
       
         body.getBytes(&bodyString, &bodyLength);
       
         CPPUNIT_ASSERT(strcmp(bodyString.data(), package) == 0);

         // Extract the Dialog, and see if its call-Id matches.
         UtlSListIterator* itor = body.getDialogIterator();
         Dialog* pDialog = dynamic_cast <Dialog*> ((*itor)());
         delete itor;

         UtlString callId;
         pDialog->getCallId(callId);

         UtlString refCallId = "62c3a00e-2f2662f8-53cfd2f7@10.1.20.231";
         CPPUNIT_ASSERT(strcmp(callId.data(), refCallId.data()) == 0);

         // See that the reported event body length is right.
         ssize_t otherLength = body.getLength();
         CPPUNIT_ASSERT_EQUAL_MESSAGE("content length is not equal",
                                      bodyLength, otherLength);
      }

   void testDialogPackageWithParams()
      {
         const char *package =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            "<dialog-info xmlns=\"urn:ietf:params:xml:ns:dialog-info\" version=\"0\" state=\"full\" entity=\"sip:moh@panther.pingtel.com:5120\">\n"
            "<dialog id=\"2\" call-id=\"call-1116603513-890@10.1.1.153\" local-tag=\"264460498\" remote-tag=\"1c10982\" direction=\"recipient\">\n"
            "<state>confirmed</state>\n"
            "<local>\n"
            "<identity>moh@panther.pingtel.com:5120</identity>\n"
            "<target uri=\"sip:moh@10.1.1.26:5120\">\n"
            "<param pname=\"param1\" pval=\"0\"/>\n"
            "<param pname=\"param2\" pval=\"something\"/>\n"
            "</target>\n"
            "</local>\n"
            "<remote>\n"
            "<identity>4444@10.1.1.153</identity>\n"
            "</remote>\n"
            "</dialog>\n"
            "</dialog-info>\n"
            ;

         // Construct a SipDialogEvent from the XML.
         SipDialogEvent body(package);

         // Convert it back to XML, and see if it is the same.
         UtlString bodyString;
         ssize_t bodyLength;

         body.getBytes(&bodyString, &bodyLength);

         CPPUNIT_ASSERT(strcmp(bodyString.data(), package) == 0);

         // Extract the Dialog, and see if its parameter matches.
         UtlSListIterator* itor = body.getDialogIterator();
         Dialog* pDialog = dynamic_cast <Dialog*> ((*itor)());
         delete itor;

         UtlString pvalue;
         pDialog->getLocalParameter("param1", pvalue);
         UtlString refPvalue = "0";
         ASSERT_STR_EQUAL(refPvalue.data(), pvalue.data());

         pDialog->getLocalParameter("param2", pvalue);
         refPvalue = "something";
         ASSERT_STR_EQUAL(refPvalue.data(), pvalue.data());

         pDialog->getLocalParameter("param3", pvalue);
         refPvalue = "";
         ASSERT_STR_EQUAL(refPvalue.data(), pvalue.data());

         // See that the reported event body length is right.
         ssize_t otherLength = body.getLength();
         CPPUNIT_ASSERT_EQUAL_MESSAGE("content length is not equal",
                                      bodyLength, otherLength);
      }

   void testCopyAndSearchDialog()
      {
         const char *package =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            "<dialog-info xmlns=\"urn:ietf:params:xml:ns:dialog-info\" version=\"0\" state=\"full\" entity=\"sip:moh@panther.pingtel.com:5120\">\n"
            "<dialog id=\"1\" call-id=\"call-1116603513-321@10.1.1.154\" local-tag=\"263210321\" remote-tag=\"1c10321\" direction=\"initiator\">\n"
            "<state>trying</state>\n"
            "<local>\n"
            "<identity>moh@panther.pingtel.com:5120</identity>\n"
            "<target uri=\"sip:moh@10.1.1.26:5120\">\n"
            "<param pname=\"param1\" pval=\"3\"/>\n"
            "<param pname=\"param2\" pval=\"something else\"/>\n"
            "</target>\n"
            "</local>\n"
            "<remote>\n"
            "<identity>4444@10.1.1.153</identity>\n"
            "</remote>\n"
            "</dialog>\n"
            "<dialog id=\"2\" call-id=\"call-1116603513-890@10.1.1.153\" local-tag=\"264460498\" remote-tag=\"1c10982\" direction=\"recipient\">\n"
            "<state>confirmed</state>\n"
            "<local>\n"
            "<identity>moh@panther.pingtel.com:5120</identity>\n"
            "<target uri=\"sip:moh@10.1.1.26:5120\">\n"
            "<param pname=\"param1\" pval=\"0\"/>\n"
            "<param pname=\"param2\" pval=\"something\"/>\n"
            "</target>\n"
            "</local>\n"
            "<remote>\n"
            "<identity>4444@10.1.1.153</identity>\n"
            "</remote>\n"
            "</dialog>\n"
            "</dialog-info>\n"
            ;
         const char *modifiedPackage =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            "<dialog-info xmlns=\"urn:ietf:params:xml:ns:dialog-info\" version=\"1\" state=\"full\" entity=\"sip:moh@panther.pingtel.com:5120\">\n"
            "<dialog id=\"2\" call-id=\"call-1116603513-890@10.1.1.153\" local-tag=\"264460498\" remote-tag=\"1c10982\" direction=\"recipient\">\n"
            "<state>confirmed</state>\n"
            "<local>\n"
            "<identity>moh@panther.pingtel.com:5120</identity>\n"
            "<target uri=\"sip:moh@10.1.1.26:5120\">\n"
            "<param pname=\"param1\" pval=\"0\"/>\n"
            "<param pname=\"param2\" pval=\"something\"/>\n"
            "</target>\n"
            "</local>\n"
            "<remote>\n"
            "<identity>4444@10.1.1.153</identity>\n"
            "</remote>\n"
            "</dialog>\n"
            "<dialog id=\"2a\" call-id=\"call-1116603513-890@10.1.1.153\" local-tag=\"264460498\" remote-tag=\"1c10982\" direction=\"recipient\">\n"
            "<state>confirmed</state>\n"
            "<local>\n"
            "<identity>moh@panther.pingtel.com:5120</identity>\n"
            "<target uri=\"sip:moh@10.1.1.26:5120\">\n"
            "<param pname=\"param1\" pval=\"0\"/>\n"
            "<param pname=\"param2\" pval=\"something new\"/>\n"
            "</target>\n"
            "</local>\n"
            "<remote>\n"
            "<identity>4444@10.1.1.153</identity>\n"
            "</remote>\n"
            "</dialog>\n"
            "</dialog-info>\n"
            ;

         // Construct a SipDialogEvent from the XML.
         SipDialogEvent body(package);

         Dialog* pDialog;
         UtlString dialogId = "2";
         pDialog = body.getDialogByDialogId(dialogId);

         UtlString pvalue;
         pDialog->getLocalParameter("param2", pvalue);
         UtlString refPvalue = "something";
         ASSERT_STR_EQUAL(refPvalue.data(), pvalue.data());

         // make a copy of the input dialog event
         SipDialogEvent copiedDialogEvent(body);

         dialogId = "3";
         pDialog = copiedDialogEvent.getDialogByDialogId(dialogId);
         CPPUNIT_ASSERT(!pDialog);

         dialogId = "1";
         pDialog = copiedDialogEvent.getDialogByDialogId(dialogId);
         CPPUNIT_ASSERT(pDialog);
         int version;
         copiedDialogEvent.buildBody(version);
         pDialog->getLocalParameter("param2", pvalue);
         refPvalue = "something else";
         ASSERT_STR_EQUAL(refPvalue.data(), pvalue.data());

         // remove one of the dialogs
         copiedDialogEvent.removeDialog(pDialog);
         pDialog = copiedDialogEvent.getDialogByDialogId(dialogId);
         CPPUNIT_ASSERT(!pDialog);

         dialogId = "2";
         UtlString uniqueDialogId = "2a";
         pDialog = copiedDialogEvent.getDialogByDialogId(dialogId);
         CPPUNIT_ASSERT(pDialog);

         // make and insert a copy, which will be owned by copiedDialogEvent
         Dialog* pNewDialog = new Dialog(*pDialog);
         pNewDialog->setDialogId(uniqueDialogId);
         pNewDialog->setLocalParameter("param2", "something new");

         copiedDialogEvent.insertDialog(pNewDialog);
         pDialog = copiedDialogEvent.getDialogByDialogId(dialogId);
         CPPUNIT_ASSERT(pDialog);
         pDialog = copiedDialogEvent.getDialogByDialogId(uniqueDialogId);
         CPPUNIT_ASSERT(pDialog);

         UtlString bodyString;
         ssize_t bodyLength;
         copiedDialogEvent.getBytes(&bodyString, &bodyLength);
         CPPUNIT_ASSERT(strcmp(bodyString.data(), modifiedPackage) == 0);
      }
};

CPPUNIT_TEST_SUITE_REGISTRATION(SipDialogEventTest);
