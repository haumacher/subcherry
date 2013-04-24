package test.com.subcherry.utils;

import junit.framework.TestCase;

import com.subcherry.utils.Utils;
import com.subcherry.utils.Utils.TicketMessage;

/**
 * Test case for {@link Utils}.
 * 
 * @version   $Revision$  $Author$  $Date$
 */
public class TestUtils extends TestCase {

	public void testPortMessage() {
		TicketMessage message = new Utils.TicketMessage("Ticket #9438: Ported to CWS_TL_5_7_3_Patch_11_2 from TL_trunk: Follow-up for [148277]: Resolved merge conflict in Unimplementable: Added missing documentation.");
		assertEquals("9438", message.ticketNumber);
		assertNull(message.apiChange);
		assertEquals(" Follow-up for [148277]: Resolved merge conflict in Unimplementable: Added missing documentation.", message.originalMessage);
	}
	
	public void testAPIChangePortMessage() {
		TicketMessage message = new Utils.TicketMessage("Ticket #9438: Ported to CWS_TL_5_7_3_Patch_11_2 from TL_trunk: API change: Added missing documentation.");
		assertEquals("9438", message.ticketNumber);
		assertNotNull(message.apiChange);
		assertEquals(" Added missing documentation.", message.originalMessage);
	}
	
	public void testMessage() {
		TicketMessage message = new Utils.TicketMessage("Ticket #9438: Added missing documentation.");
		assertEquals("9438", message.ticketNumber);
		assertNull(message.apiChange);
		assertEquals(" Added missing documentation.", message.originalMessage);
	}
	
	public void testAPIChangeMessage() {
		TicketMessage message = new Utils.TicketMessage("Ticket #9438: API change: Added missing documentation.");
		assertEquals("9438", message.ticketNumber);
		assertNotNull(message.apiChange);
		assertEquals(" Added missing documentation.", message.originalMessage);
	}
	
}

