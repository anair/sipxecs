//
// Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
// Contributors retain copyright to elements licensed under a Contributor Agreement.
// Licensed to the User under the LGPL license.
// 
//
// $$
////////////////////////////////////////////////////////////////////////

#include "cppunit/extensions/HelperMacros.h"
#include "cppunit/TestCase.h"
#include "sipxunit/TestUtilities.h"
#include "utl/PluginHooks.h"
#include "os/OsConfigDb.h"

#include "net/SipMessage.h"
#include "net/SipUserAgent.h"

#include "ForwardRules.h"
#include "SipRouter.h"

#include "testlib/FileTestContext.h"
#include "TransferControl.h"

class TransferControlTest : public CppUnit::TestCase
{
   CPPUNIT_TEST_SUITE(TransferControlTest);

   CPPUNIT_TEST(testConstructor);

   CPPUNIT_TEST(nonInvite);
   CPPUNIT_TEST(nonInviteRefer);
   CPPUNIT_TEST(normalInvite);
   CPPUNIT_TEST(InviteWithReplaces);
   CPPUNIT_TEST(ReferWithReplaces);
   CPPUNIT_TEST(UnAuthenticatedRefer);
   CPPUNIT_TEST(AuthenticatedRefer);
   CPPUNIT_TEST(AuthenticatedForiegnRefer);
   CPPUNIT_TEST(UnAuthenticatedForiegnRefer);
   CPPUNIT_TEST(AuthenticatedForiegnGruuRefer);
   CPPUNIT_TEST(UnAuthenticatedForiegnGruuRefer);
   CPPUNIT_TEST(AuthenticatedRoutedRefer);
   
   CPPUNIT_TEST_SUITE_END();

public:

   static FileTestContext* TransferTestContext;
   static TransferControl* xferctl;
   static SipUserAgent     testUserAgent;
   static SipRouter*       testSipRouter;
   
   void setUp()
      {
         TransferTestContext = new FileTestContext(TEST_DATA_DIR "/transfer-control",
                                                   TEST_WORK_DIR "/transfer-control-context");
         TransferTestContext->inputFile("domain-config");
         TransferTestContext->setSipxDir(SipXecsService::ConfigurationDirType);
      }

   void tearDown()
      {
      }

   void testConstructor()
      {
         /*
          * This test exists to initialize the singleton plugin.
          * Doing it as a static ran into ordering problems.
          */
         CPPUNIT_ASSERT((xferctl = dynamic_cast<TransferControl*>(getAuthPlugin("xfer"))));

         testUserAgent.setIsUserAgent(FALSE);

         //testUserAgent.setDnsSrvTimeout(1 /* seconds */);
         //testUserAgent.setMaxSrvRecords(4);
         testUserAgent.setUserAgentHeaderProperty("sipXecs/testproxy");

         testUserAgent.setForking(FALSE);  // Disable forking

         UtlString hostAliases("sipx.example.edu example.edu");
         
         testUserAgent.setHostAliases(hostAliases);

         OsConfigDb configDb;
         configDb.set("SIPX_PROXY_AUTHENTICATE_ALGORITHM", "MD5");
         configDb.set("SIPX_PROXY_DOMAIN_NAME", "example.edu");
         configDb.set("SIPX_PROXY_AUTHENTICATE_REALM", "example.edu");
         configDb.set("SIPX_PROXY_HOSTPORT", "sipx.example.edu");

         testSipRouter = new SipRouter(testUserAgent, mForwardingRules, configDb);
      }

   // Test that an in-dialog request without a replaces header is not affected
   void nonInvite()
      {
         UtlString identity; // no authenticated identity
         Url requestUri("sip:someone@somewhere");

         const char* message =
            "INFO sip:someone@somewhere SIP/2.0\r\n"
            "Via: SIP/2.0/TCP 10.1.1.3:33855\r\n"
            "To: sip:someone@somewhere;tag=totag\r\n"
            "From: Caller <sip:caller@example.org>; tag=30543f3483e1cb11ecb40866edd3295b\r\n"
            "Call-Id: f88dfabce84b6a2787ef024a7dbe8749\r\n"
            "Cseq: 5 INFO\r\n"
            "Max-Forwards: 20\r\n"
            "Contact: caller@127.0.0.1\r\n"
            "Content-Length: 0\r\n"
            "\r\n";
         SipMessage testMsg(message, strlen(message));

         UtlSList noRemovedRoutes;
         UtlString myRouteName("myhost.example.com");
         RouteState routeState( testMsg, noRemovedRoutes, myRouteName );

         const char unmodifiedRejectReason[] = "unmodified";
         UtlString rejectReason(unmodifiedRejectReason);
         
         UtlString method("INFO");
         AuthPlugin::AuthResult priorResult = AuthPlugin::CONTINUE;
         
         CPPUNIT_ASSERT(AuthPlugin::CONTINUE
                        == xferctl->authorizeAndModify(testSipRouter,
                                                       identity,
                                                       requestUri,
                                                       routeState,
                                                       method,
                                                       priorResult,
                                                       testMsg,
                                                       rejectReason
                                                       ));
         ASSERT_STR_EQUAL(unmodifiedRejectReason, rejectReason.data());

         // verify that the message has not been modified
         UtlString forwardedMsg;
         size_t length;
         testMsg.getBytes(&forwardedMsg,&length);
         ASSERT_STR_EQUAL(message, forwardedMsg.data());
      }

   // Test that an in-dialog REFER request for a method other than INVITE is not affected
   void nonInviteRefer()
      {
         UtlString identity; // no authenticated identity
         Url requestUri("sip:someone@somewhere");

         const char* message =
            "REFER sip:someone@somewhere SIP/2.0\r\n"
            "Refer-To: <sip:else@where;method=INFO>\r\n"
            "Via: SIP/2.0/TCP 10.1.1.3:33855\r\n"
            "To: sip:someone@somewhere;tag=totag\r\n"
            "From: Caller <sip:caller@example.org>; tag=30543f3483e1cb11ecb40866edd3295b\r\n"
            "Call-Id: f88dfabce84b6a2787ef024a7dbe8749\r\n"
            "Cseq: 5 INFO\r\n"
            "Max-Forwards: 20\r\n"
            "Contact: caller@127.0.0.1\r\n"
            "Content-Length: 0\r\n"
            "\r\n";
         SipMessage testMsg(message, strlen(message));

         UtlSList noRemovedRoutes;
         UtlString myRouteName("myhost.example.com");
         RouteState routeState( testMsg, noRemovedRoutes, myRouteName );

         const char unmodifiedRejectReason[] = "unmodified";
         UtlString rejectReason(unmodifiedRejectReason);
         
         UtlString method("REFER");
         AuthPlugin::AuthResult priorResult = AuthPlugin::CONTINUE;
         
         CPPUNIT_ASSERT(AuthPlugin::CONTINUE
                        == xferctl->authorizeAndModify(testSipRouter,
                                                       identity,
                                                       requestUri,
                                                       routeState,
                                                       method,
                                                       priorResult,
                                                       testMsg,
                                                       rejectReason
                                                       ));
         ASSERT_STR_EQUAL(unmodifiedRejectReason, rejectReason.data());

         // verify that the Refer-To has not been modified to route through this proxy
         UtlString forwardedTargetStr;
         CPPUNIT_ASSERT(testMsg.getReferToField(forwardedTargetStr));
         ASSERT_STR_EQUAL("<sip:else@where;method=INFO>",
                          forwardedTargetStr.data());

         // verify that the message has not been modified
         UtlString forwardedMsg;
         size_t length;
         testMsg.getBytes(&forwardedMsg,&length);
         ASSERT_STR_EQUAL(message, forwardedMsg.data());
      }

   // Test that a dialog-forming INVITE without a replaces header is not affected
   void normalInvite()
      {
         UtlString identity; // no authenticated identity
         Url requestUri("sip:someone@somewhere");

         const char* message =
            "INVITE sip:someone@somewhere SIP/2.0\r\n"
            "Via: SIP/2.0/TCP 10.1.1.3:33855\r\n"
            "To: sip:someone@somewhere\r\n"
            "From: Caller <sip:caller@example.org>; tag=30543f3483e1cb11ecb40866edd3295b\r\n"
            "Call-Id: f88dfabce84b6a2787ef024a7dbe8749\r\n"
            "Cseq: 1 INVITE\r\n"
            "Max-Forwards: 20\r\n"
            "Contact: caller@127.0.0.1\r\n"
            "Content-Length: 0\r\n"
            "\r\n";
         SipMessage testMsg(message, strlen(message));

         UtlSList noRemovedRoutes;
         UtlString myRouteName("myhost.example.com");
         RouteState routeState( testMsg, noRemovedRoutes, myRouteName );

         const char unmodifiedRejectReason[] = "unmodified";
         UtlString rejectReason(unmodifiedRejectReason);
         
         UtlString method("INVITE");
         AuthPlugin::AuthResult priorResult = AuthPlugin::CONTINUE;
         
         CPPUNIT_ASSERT(AuthPlugin::CONTINUE
                        == xferctl->authorizeAndModify(testSipRouter,
                                                       identity,
                                                       requestUri,
                                                       routeState,
                                                       method,
                                                       priorResult,
                                                       testMsg,
                                                       rejectReason
                                                       ));
         ASSERT_STR_EQUAL(unmodifiedRejectReason, rejectReason.data());

         // verify that the message has not been modified
         UtlString forwardedMsg;
         size_t length;
         testMsg.getBytes(&forwardedMsg,&length);
         ASSERT_STR_EQUAL(message, forwardedMsg.data());
      }

   // Test that an out-of-dialog INVITE with a Replaces header is allowed
   void InviteWithReplaces()
      {
         UtlString identity; // no authenticated identity
         Url requestUri("sip:someone@somewhere");

         const char* message =
            "INVITE sip:someone@somewhere SIP/2.0\r\n"
            "Replaces: valid@callid;to-tag=totagvalue;from-tag=fromtagvalue\r\n"
            "Via: SIP/2.0/TCP 10.1.1.3:33855\r\n"
            "To: sip:someone@somewhere\r\n"
            "From: Caller <sip:caller@example.org>; tag=30543f3483e1cb11ecb40866edd3295b\r\n"
            "Call-Id: f88dfabce84b6a2787ef024a7dbe8749\r\n"
            "Cseq: 1 INVITE\r\n"
            "Max-Forwards: 20\r\n"
            "Contact: caller@127.0.0.1\r\n"
            "Content-Length: 0\r\n"
            "\r\n";
         SipMessage testMsg(message, strlen(message));

         UtlSList noRemovedRoutes;
         UtlString myRouteName("myhost.example.com");
         RouteState routeState( testMsg, noRemovedRoutes, myRouteName );

         const char unmodifiedRejectReason[] = "unmodified";
         UtlString rejectReason(unmodifiedRejectReason);
         
         UtlString method("INVITE");
         AuthPlugin::AuthResult priorResult = AuthPlugin::CONTINUE;
         
         CPPUNIT_ASSERT(AuthPlugin::ALLOW
                        == xferctl->authorizeAndModify(testSipRouter,
                                                       identity,
                                                       requestUri,
                                                       routeState,
                                                       method,
                                                       priorResult,
                                                       testMsg,
                                                       rejectReason
                                                       ));
         ASSERT_STR_EQUAL(unmodifiedRejectReason, rejectReason.data());

         // verify that the message has not been modified
         UtlString forwardedMsg;
         size_t length;
         testMsg.getBytes(&forwardedMsg,&length);
         ASSERT_STR_EQUAL(message, forwardedMsg.data());
      }

   // Test that a REFER with Replaces is allowed
   void ReferWithReplaces()
      {
         UtlString identity; // no authenticated identity
         Url requestUri("sip:someone@somewhere");

         const char* message =
            "REFER sip:someone@somewhere SIP/2.0\r\n"
            "Refer-To: other@elsewhere?replaces=valid@callid;to-tag=totagvalue;from-tag=fromtagvalue\r\n"
            "Via: SIP/2.0/TCP 10.1.1.3:33855\r\n"
            "To: sip:someone@somewhere\r\n"
            "From: Caller <sip:caller@example.org>; tag=30543f3483e1cb11ecb40866edd3295b\r\n"
            "Call-Id: f88dfabce84b6a2787ef024a7dbe8749\r\n"
            "Cseq: 1 INVITE\r\n"
            "Max-Forwards: 20\r\n"
            "Contact: caller@127.0.0.1\r\n"
            "Content-Length: 0\r\n"
            "\r\n";
         SipMessage testMsg(message, strlen(message));

         UtlSList noRemovedRoutes;
         UtlString myRouteName("myhost.example.com");
         RouteState routeState( testMsg, noRemovedRoutes, myRouteName );

         const char unmodifiedRejectReason[] = "unmodified";
         UtlString rejectReason(unmodifiedRejectReason);
         
         UtlString method("REFER");
         AuthPlugin::AuthResult priorResult = AuthPlugin::CONTINUE;
         
         CPPUNIT_ASSERT(AuthPlugin::ALLOW
                        == xferctl->authorizeAndModify(testSipRouter,
                                                       identity,
                                                       requestUri,
                                                       routeState,
                                                       method,
                                                       priorResult,
                                                       testMsg,
                                                       rejectReason
                                                       ));
         ASSERT_STR_EQUAL(unmodifiedRejectReason, rejectReason.data());

         /*
          * The target of this refer is not local, but the refer-to is not our domain and
          * is not a gruu, so this should be routed through this proxy.
          */
         UtlString modifiedReferToStr;
         CPPUNIT_ASSERT(testMsg.getReferToField(modifiedReferToStr));
         Url modifiedReferTo(modifiedReferToStr);
         CPPUNIT_ASSERT(Url::SipUrlScheme == modifiedReferTo.getScheme());

         UtlString transferIdentityValue; // with replaces: should _not_ get an identity
         CPPUNIT_ASSERT(! modifiedReferTo.getHeaderParameter("X-Sipx-Authidentity",
                                                             transferIdentityValue));

         UtlString proxyRoute;         // but should route through this proxy
         CPPUNIT_ASSERT(modifiedReferTo.getHeaderParameter(SIP_ROUTE_FIELD,proxyRoute));
         ASSERT_STR_EQUAL("<sip:example.edu;lr>", proxyRoute.data());
      }


   // Test that an unauthenticated REFER without Replaces is challenged
   void UnAuthenticatedRefer()
      {
         UtlString identity; // no authenticated identity
         Url requestUri("sip:someone@somewhere");

         const char* message =
            "REFER sip:someone@somewhere SIP/2.0\r\n"
            "Refer-To: other@example.edu\r\n"
            "Via: SIP/2.0/TCP 10.1.1.3:33855\r\n"
            "To: sip:someone@somewhere\r\n"
            "From: Caller <sip:caller@example.org>; tag=30543f3483e1cb11ecb40866edd3295b\r\n"
            "Call-Id: f88dfabce84b6a2787ef024a7dbe8749\r\n"
            "Cseq: 1 INVITE\r\n"
            "Max-Forwards: 20\r\n"
            "Contact: caller@127.0.0.1\r\n"
            "Content-Length: 0\r\n"
            "\r\n";
         SipMessage testMsg(message, strlen(message));

         UtlSList noRemovedRoutes;
         UtlString myRouteName("myhost.example.com");
         RouteState routeState( testMsg, noRemovedRoutes, myRouteName );

         const char unmodifiedRejectReason[] = "unmodified";
         UtlString rejectReason(unmodifiedRejectReason);
         
         UtlString method("REFER");
         AuthPlugin::AuthResult priorResult = AuthPlugin::CONTINUE;
         
         CPPUNIT_ASSERT(AuthPlugin::DENY
                        == xferctl->authorizeAndModify(testSipRouter,
                                                       identity,
                                                       requestUri,
                                                       routeState,
                                                       method,
                                                       priorResult,
                                                       testMsg,
                                                       rejectReason
                                                       ));
         ASSERT_STR_EQUAL(unmodifiedRejectReason, rejectReason.data());
      }

   
   // Test that an authenticated REFER without Replaces is allowed and annotated
   void AuthenticatedRefer()
      {
         UtlString identity("controller@domain"); // an authenticated identity
         Url requestUri("sip:someone@somewhere");

         const char* message =
            "REFER sip:someone@somewhere SIP/2.0\r\n"
            "Refer-To: other@example.edu\r\n"
            "Via: SIP/2.0/TCP 10.1.1.3:33855\r\n"
            "To: sip:someone@somewhere\r\n"
            "From: Caller <sip:caller@example.org>; tag=30543f3483e1cb11ecb40866edd3295b\r\n"
            "Call-Id: f88dfabce84b6a2787ef024a7dbe8749\r\n"
            "Cseq: 1 INVITE\r\n"
            "Max-Forwards: 20\r\n"
            "Contact: caller@127.0.0.1\r\n"
            "Content-Length: 0\r\n"
            "\r\n";
         SipMessage testMsg(message, strlen(message));

         UtlSList noRemovedRoutes;
         UtlString myRouteName("myhost.example.com");
         RouteState routeState( testMsg, noRemovedRoutes, myRouteName );

         const char unmodifiedRejectReason[] = "unmodified";
         UtlString rejectReason(unmodifiedRejectReason);
         
         UtlString method("REFER");
         AuthPlugin::AuthResult priorResult = AuthPlugin::CONTINUE;
         
         CPPUNIT_ASSERT(AuthPlugin::CONTINUE
                        == xferctl->authorizeAndModify(testSipRouter,
                                                       identity,
                                                       requestUri,
                                                       routeState,
                                                       method,
                                                       priorResult,
                                                       testMsg,
                                                       rejectReason
                                                       ));
         ASSERT_STR_EQUAL(unmodifiedRejectReason, rejectReason.data());

         UtlString modifiedReferToStr;
         CPPUNIT_ASSERT(testMsg.getReferToField(modifiedReferToStr));

         Url modifiedReferTo(modifiedReferToStr);
         CPPUNIT_ASSERT(Url::SipUrlScheme == modifiedReferTo.getScheme());

         UtlString transferIdentityValue;
         CPPUNIT_ASSERT(modifiedReferTo.getHeaderParameter("X-Sipx-Authidentity",
                                                           transferIdentityValue));
         Url transferIdentityUrl(transferIdentityValue);
         CPPUNIT_ASSERT(Url::SipUrlScheme == transferIdentityUrl.getScheme());

         UtlString transferIdentity;
         transferIdentityUrl.getIdentity(transferIdentity);
         ASSERT_STR_EQUAL("controller@domain", transferIdentity.data());

         UtlString proxyRoute; // the refer-to was this domain, so no route should be added
         CPPUNIT_ASSERT(! modifiedReferTo.getHeaderParameter(SIP_ROUTE_FIELD,proxyRoute));
      }

   // Test that an authenticated REFER without Replaces is allowed and annotated
   void AuthenticatedForiegnRefer()
      {
         UtlString identity("controller@domain"); // an authenticated identity
         Url requestUri("sip:someone@somewhere");

         const char* message =
            "REFER sip:someone@somewhere SIP/2.0\r\n"
            "Refer-To: other@elsewhere.edu\r\n"
            "Via: SIP/2.0/TCP 10.1.1.3:33855\r\n"
            "To: sip:someone@somewhere\r\n"
            "From: Caller <sip:caller@example.org>; tag=30543f3483e1cb11ecb40866edd3295b\r\n"
            "Call-Id: f88dfabce84b6a2787ef024a7dbe8749\r\n"
            "Cseq: 1 INVITE\r\n"
            "Max-Forwards: 20\r\n"
            "Contact: caller@127.0.0.1\r\n"
            "Content-Length: 0\r\n"
            "\r\n";
         SipMessage testMsg(message, strlen(message));

         UtlSList noRemovedRoutes;
         UtlString myRouteName("myhost.example.com");
         RouteState routeState( testMsg, noRemovedRoutes, myRouteName );

         const char unmodifiedRejectReason[] = "unmodified";
         UtlString rejectReason(unmodifiedRejectReason);
         
         UtlString method("REFER");
         AuthPlugin::AuthResult priorResult = AuthPlugin::CONTINUE;
         
         CPPUNIT_ASSERT(AuthPlugin::CONTINUE
                        == xferctl->authorizeAndModify(testSipRouter,
                                                       identity,
                                                       requestUri,
                                                       routeState,
                                                       method,
                                                       priorResult,
                                                       testMsg,
                                                       rejectReason
                                                       ));
         ASSERT_STR_EQUAL(unmodifiedRejectReason, rejectReason.data());

         UtlString modifiedReferToStr;
         CPPUNIT_ASSERT(testMsg.getReferToField(modifiedReferToStr));

         Url modifiedReferTo(modifiedReferToStr);
         CPPUNIT_ASSERT(Url::SipUrlScheme == modifiedReferTo.getScheme());

         UtlString transferIdentityValue;
         CPPUNIT_ASSERT(modifiedReferTo.getHeaderParameter("X-Sipx-Authidentity",
                                                           transferIdentityValue));
         Url transferIdentityUrl(transferIdentityValue);
         CPPUNIT_ASSERT(Url::SipUrlScheme == transferIdentityUrl.getScheme());

         UtlString transferIdentity;
         transferIdentityUrl.getIdentity(transferIdentity);
         ASSERT_STR_EQUAL("controller@domain", transferIdentity.data());

         UtlString proxyRoute; //
         CPPUNIT_ASSERT(modifiedReferTo.getHeaderParameter(SIP_ROUTE_FIELD,proxyRoute));
         ASSERT_STR_EQUAL("<sip:example.edu;lr>", proxyRoute.data());
      }

   // Test that an unauthenticated REFER without Replaces to a foreign target is not challenged
   void UnAuthenticatedForiegnRefer()
      {
         UtlString identity; // no authenticated identity
         Url requestUri("sip:someone@somewhere");

         const char* message =
            "REFER sip:someone@somewhere SIP/2.0\r\n"
            "Refer-To: other@elsewhere.edu\r\n"
            "Via: SIP/2.0/TCP 10.1.1.3:33855\r\n"
            "To: sip:someone@somewhere\r\n"
            "From: Caller <sip:caller@example.org>; tag=30543f3483e1cb11ecb40866edd3295b\r\n"
            "Call-Id: f88dfabce84b6a2787ef024a7dbe8749\r\n"
            "Cseq: 1 INVITE\r\n"
            "Max-Forwards: 20\r\n"
            "Contact: caller@127.0.0.1\r\n"
            "Content-Length: 0\r\n"
            "\r\n";
         SipMessage testMsg(message, strlen(message));

         UtlSList noRemovedRoutes;
         UtlString myRouteName("myhost.example.com");
         RouteState routeState( testMsg, noRemovedRoutes, myRouteName );

         const char unmodifiedRejectReason[] = "unmodified";
         UtlString rejectReason(unmodifiedRejectReason);
         
         UtlString method("REFER");
         AuthPlugin::AuthResult priorResult = AuthPlugin::CONTINUE;
         
         CPPUNIT_ASSERT(AuthPlugin::ALLOW
                        == xferctl->authorizeAndModify(testSipRouter,
                                                       identity,
                                                       requestUri,
                                                       routeState,
                                                       method,
                                                       priorResult,
                                                       testMsg,
                                                       rejectReason
                                                       ));
         ASSERT_STR_EQUAL(unmodifiedRejectReason, rejectReason.data());

         // verify that the Refer-To has been modified to route through this proxy
         UtlString forwardedTargetStr;
         CPPUNIT_ASSERT(testMsg.getReferToField(forwardedTargetStr));
         ASSERT_STR_EQUAL("<sip:other@elsewhere.edu?ROUTE=%3Csip%3Aexample.edu%3Blr%3E>",
                          forwardedTargetStr.data());
      }
   
   // Test that an authenticated REFER without Replaces to a GRUU is allowed but not annotated
   void AuthenticatedForiegnGruuRefer()
      {
         UtlString identity("controller@domain"); // an authenticated identity
         Url requestUri("sip:someone@somewhere");

         const char* message =
            "REFER sip:someone@somewhere SIP/2.0\r\n"
            "Refer-To: <sip:other@elsewhere.edu;gr>\r\n"
            "Via: SIP/2.0/TCP 10.1.1.3:33855\r\n"
            "To: sip:someone@somewhere\r\n"
            "From: Caller <sip:caller@example.org>; tag=30543f3483e1cb11ecb40866edd3295b\r\n"
            "Call-Id: f88dfabce84b6a2787ef024a7dbe8749\r\n"
            "Cseq: 1 INVITE\r\n"
            "Max-Forwards: 20\r\n"
            "Contact: caller@127.0.0.1\r\n"
            "Content-Length: 0\r\n"
            "\r\n";
         SipMessage testMsg(message, strlen(message));

         UtlSList noRemovedRoutes;
         UtlString myRouteName("myhost.example.com");
         RouteState routeState( testMsg, noRemovedRoutes, myRouteName );

         const char unmodifiedRejectReason[] = "unmodified";
         UtlString rejectReason(unmodifiedRejectReason);
         
         UtlString method("REFER");
         AuthPlugin::AuthResult priorResult = AuthPlugin::CONTINUE;
         
         CPPUNIT_ASSERT(AuthPlugin::CONTINUE
                        == xferctl->authorizeAndModify(testSipRouter,
                                                       identity,
                                                       requestUri,
                                                       routeState,
                                                       method,
                                                       priorResult,
                                                       testMsg,
                                                       rejectReason
                                                       ));
         ASSERT_STR_EQUAL(unmodifiedRejectReason, rejectReason.data());

         UtlString modifiedReferToStr;
         CPPUNIT_ASSERT(testMsg.getReferToField(modifiedReferToStr));

         Url modifiedReferTo(modifiedReferToStr);
         CPPUNIT_ASSERT(Url::SipUrlScheme == modifiedReferTo.getScheme());

         UtlString transferIdentityValue;
         CPPUNIT_ASSERT(modifiedReferTo.getHeaderParameter("X-Sipx-Authidentity",
                                                           transferIdentityValue));
         Url transferIdentityUrl(transferIdentityValue);
         CPPUNIT_ASSERT(Url::SipUrlScheme == transferIdentityUrl.getScheme());

         UtlString transferIdentity;
         transferIdentityUrl.getIdentity(transferIdentity);
         ASSERT_STR_EQUAL("controller@domain", transferIdentity.data());

         UtlString proxyRoute; //
         CPPUNIT_ASSERT(! modifiedReferTo.getHeaderParameter(SIP_ROUTE_FIELD,proxyRoute));
      }

   // Test that an unauthenticated REFER without Replaces to a foreign GRUU target is not challenged
   void UnAuthenticatedForiegnGruuRefer()
      {
         UtlString identity; // no authenticated identity
         Url requestUri("sip:someone@somewhere");

         const char* message =
            "REFER sip:someone@somewhere SIP/2.0\r\n"
            "Refer-To: <sip:other@elsewhere.edu;gr>\r\n"
            "Via: SIP/2.0/TCP 10.1.1.3:33855\r\n"
            "To: sip:someone@somewhere\r\n"
            "From: Caller <sip:caller@example.org>; tag=30543f3483e1cb11ecb40866edd3295b\r\n"
            "Call-Id: f88dfabce84b6a2787ef024a7dbe8749\r\n"
            "Cseq: 1 INVITE\r\n"
            "Max-Forwards: 20\r\n"
            "Contact: caller@127.0.0.1\r\n"
            "Content-Length: 0\r\n"
            "\r\n";
         SipMessage testMsg(message, strlen(message));

         UtlSList noRemovedRoutes;
         UtlString myRouteName("myhost.example.com");
         RouteState routeState( testMsg, noRemovedRoutes, myRouteName );

         const char unmodifiedRejectReason[] = "unmodified";
         UtlString rejectReason(unmodifiedRejectReason);
         
         UtlString method("REFER");
         AuthPlugin::AuthResult priorResult = AuthPlugin::CONTINUE;
         
         CPPUNIT_ASSERT(AuthPlugin::ALLOW
                        == xferctl->authorizeAndModify(testSipRouter,
                                                       identity,
                                                       requestUri,
                                                       routeState,
                                                       method,
                                                       priorResult,
                                                       testMsg,
                                                       rejectReason
                                                       ));
         ASSERT_STR_EQUAL(unmodifiedRejectReason, rejectReason.data());

         // verify that the message has not been modified
         UtlString forwardedMsg;
         size_t length;
         testMsg.getBytes(&forwardedMsg,&length);
         ASSERT_STR_EQUAL(message, forwardedMsg.data());
      }
   
   // Test that an authenticated routed REFER without Replaces is allowed and annotated
   void AuthenticatedRoutedRefer()
      {
         UtlString identity("controller@domain"); // an authenticated identity
         Url requestUri("sip:someone@somewhere");

         const char* message =
            "REFER sip:someone@somewhere SIP/2.0\r\n"
            "Refer-To: <sip:other@example.edu?route=%3Csip%3Aotherproxy.com%3Blr%3E>\r\n"
            "Via: SIP/2.0/TCP 10.1.1.3:33855\r\n"
            "To: sip:someone@somewhere\r\n"
            "From: Caller <sip:caller@example.org>; tag=30543f3483e1cb11ecb40866edd3295b\r\n"
            "Call-Id: f88dfabce84b6a2787ef024a7dbe8749\r\n"
            "Cseq: 1 INVITE\r\n"
            "Max-Forwards: 20\r\n"
            "Contact: caller@127.0.0.1\r\n"
            "Content-Length: 0\r\n"
            "\r\n";
         SipMessage testMsg(message, strlen(message));

         UtlSList noRemovedRoutes;
         UtlString myRouteName("myhost.example.com");
         RouteState routeState( testMsg, noRemovedRoutes, myRouteName );

         const char unmodifiedRejectReason[] = "unmodified";
         UtlString rejectReason(unmodifiedRejectReason);
         
         UtlString method("REFER");
         AuthPlugin::AuthResult priorResult = AuthPlugin::CONTINUE;
         
         CPPUNIT_ASSERT(AuthPlugin::CONTINUE
                        == xferctl->authorizeAndModify(testSipRouter,
                                                       identity,
                                                       requestUri,
                                                       routeState,
                                                       method,
                                                       priorResult,
                                                       testMsg,
                                                       rejectReason
                                                       ));
         ASSERT_STR_EQUAL(unmodifiedRejectReason, rejectReason.data());

         UtlString modifiedReferToStr;
         CPPUNIT_ASSERT(testMsg.getReferToField(modifiedReferToStr));

         Url modifiedReferTo(modifiedReferToStr);
         CPPUNIT_ASSERT(Url::SipUrlScheme == modifiedReferTo.getScheme());

         UtlString transferIdentityValue;
         CPPUNIT_ASSERT(modifiedReferTo.getHeaderParameter("X-Sipx-Authidentity",
                                                           transferIdentityValue));
         Url transferIdentityUrl(transferIdentityValue);
         CPPUNIT_ASSERT(Url::SipUrlScheme == transferIdentityUrl.getScheme());

         UtlString transferIdentity;
         transferIdentityUrl.getIdentity(transferIdentity);
         ASSERT_STR_EQUAL("controller@domain", transferIdentity.data());

         UtlString proxyRoute; // the refer-to was this domain, so no route should be added
         CPPUNIT_ASSERT(modifiedReferTo.getHeaderParameter(SIP_ROUTE_FIELD,proxyRoute));
         ASSERT_STR_EQUAL("<sip:otherproxy.com;lr>", proxyRoute.data());
      }

private:
   ForwardRules  mForwardingRules;
};

CPPUNIT_TEST_SUITE_REGISTRATION(TransferControlTest);

TransferControl* TransferControlTest::xferctl;
FileTestContext* TransferControlTest::TransferTestContext;
SipUserAgent     TransferControlTest::testUserAgent;
SipRouter*       TransferControlTest::testSipRouter;
