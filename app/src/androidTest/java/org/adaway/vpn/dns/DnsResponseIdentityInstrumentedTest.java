package org.adaway.vpn.dns;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.Type;

@RunWith(AndroidJUnit4.class)
public class DnsResponseIdentityInstrumentedTest {
    @Test
    public void upstreamResponseMustMatchTransactionAndQuestion() throws Exception {
        Message query = query(Type.A);
        Message response = DnsPacketProxy.createBlockedResponse(query);
        assertTrue(DnsPacketProxy.isMatchingResponse(query, response.toWire()));
        response.getHeader().setID((query.getHeader().getID() + 1) & 0xffff);
        assertFalse(DnsPacketProxy.isMatchingResponse(query, response.toWire()));
        response = DnsPacketProxy.createBlockedResponse(query(Type.AAAA));
        response.getHeader().setID(query.getHeader().getID());
        assertFalse(DnsPacketProxy.isMatchingResponse(query, response.toWire()));
        assertFalse(DnsPacketProxy.isMatchingResponse(query, query.toWire()));
        assertFalse(DnsPacketProxy.isMatchingResponse(query, new byte[]{1, 2}));
    }

    private static Message query(int type) throws Exception {
        return Message.newQuery(Record.newRecord(Name.fromString("test.invalid."), type, DClass.IN));
    }
}
