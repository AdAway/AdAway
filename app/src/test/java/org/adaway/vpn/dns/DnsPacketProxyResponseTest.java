package org.adaway.vpn.dns;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Opcode;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.Type;

import java.net.InetAddress;

public class DnsPacketProxyResponseTest {
    @Test
    public void redirectAnswersOnlyTheRequestedAddressFamily() throws Exception {
        assertAnswer(Type.A, "192.0.2.50", Type.A);
        assertAnswer(Type.AAAA, "2001:db8::50", Type.AAAA);
        assertAnswer(Type.AAAA, "192.0.2.50", -1);
        assertAnswer(Type.A, "2001:db8::50", -1);
        assertAnswer(Type.MX, "192.0.2.50", -1);
        assertAnswer(Type.ANY, "192.0.2.50", Type.A);
    }

    @Test
    public void invalidRedirectNeverResolvesAHostnameOrDefaultsToLoopback() throws Exception {
        for (String address : new String[]{null, "", "localhost", "redirect.invalid"}) {
            Message response = DnsPacketProxy.createRedirectResponse(query(Type.A), address);
            assertEquals(Rcode.SERVFAIL, response.getRcode());
            assertEquals(0, response.getHeader().getCount(Section.ANSWER));
        }
    }

    @Test
    public void syntheticAnswerDoesNotReflectClientAnswerOrAuthentication() throws Exception {
        Message query = query(Type.A);
        query.getHeader().setFlag(Flags.AD);
        query.addRecord(new ARecord(query.getQuestion().getName(), DClass.IN, 60,
                InetAddress.getByName("192.0.2.99")), Section.ANSWER);

        Message response = DnsPacketProxy.createBlockedResponse(query);

        assertEquals(query.getHeader().getID(), response.getHeader().getID());
        assertEquals(query.getQuestion(), response.getQuestion());
        assertTrue(response.getHeader().getFlag(Flags.QR));
        assertTrue(response.getHeader().getFlag(Flags.RD));
        assertFalse(response.getHeader().getFlag(Flags.AD));
        assertEquals(0, response.getHeader().getCount(Section.ANSWER));
        assertEquals(1, response.getHeader().getCount(Section.AUTHORITY));
    }

    @Test
    public void rejectsResponsePacketsAndMultiQuestionQueries() throws Exception {
        Message query = query(Type.A);
        assertTrue(DnsPacketProxy.isStandardQuery(query));
        query.getHeader().setFlag(Flags.QR);
        assertFalse(DnsPacketProxy.isStandardQuery(query));
        query.getHeader().unsetFlag(Flags.QR);
        query.getHeader().setOpcode(Opcode.UPDATE);
        assertFalse(DnsPacketProxy.isStandardQuery(query));
        query.getHeader().setOpcode(Opcode.QUERY);
        query.addRecord(query(Type.AAAA).getQuestion(), Section.QUESTION);
        assertFalse(DnsPacketProxy.isStandardQuery(query));
    }

    private static Message query(int type) throws Exception {
        return Message.newQuery(Record.newRecord(Name.fromString("test.invalid."), type, DClass.IN));
    }

    private static void assertAnswer(int type, String address, int expectedType) throws Exception {
        Message response = DnsPacketProxy.createRedirectResponse(query(type), address);
        assertEquals(Rcode.NOERROR, response.getRcode());
        Record[] answers = response.getSectionArray(Section.ANSWER);
        assertEquals(expectedType == -1 ? 0 : 1, answers.length);
        if (expectedType != -1) {
            assertEquals(expectedType, answers[0].getType());
        }
    }
}
