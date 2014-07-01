package com.subcherry.trac;

import java.util.Date;
import java.util.Map;
import java.util.Vector;

import org.lustin.trac.xmlprc.Ticket;

/**
 * @version   $Revision$  $Author$  $Date$
 */
public class TracTicket {
    
	private static final int FETCH_TICKET_RETRY = 20;
    
    public static final String TICKET_ATT_SUMMARY     = "summary";
    public static final String TICKET_ATT_COMPONENT   = "component";
    public static final String TICKET_ATT_TYPE        = "type";
    public static final String TICKET_ATT_CC          = "cc";
    public static final String TICKET_ATT_OWNER       = "owner";
    public static final String TICKET_ATT_KEYWORD     = "keywords";
    public static final String TICKET_ATT_STATUS      = "status";
    public static final String TICKET_ATT_RESOLUTION  = "resolution";
    public static final String TICKET_ATT_VERSION     = "version";
    public static final String TICKET_ATT_MILESTONE   = "milestone";
    public static final String TICKET_ATT_DESCRIPTION = "description";
    public static final String TICKET_ATT_REPORTER    = "reporter";
    public static final String TICKET_ATT_PRIORITY    = "priority";
    public static final String TICKET_ATT_IMPLEMENTED_IN = "implementedon";

    public static final String TICKET_ATT__NUMBER        = "_number";
    public static final String TICKET_ATT__DATE_CREATED  = "_dateCreated";
    public static final String TICKET_ATT__DATE_MODIFIED = "_dateModified";
    
    private static final String[] TICKET_ATTS = new String[] {
        TICKET_ATT_SUMMARY,
        TICKET_ATT_COMPONENT,
        TICKET_ATT_TYPE,
        TICKET_ATT_CC,
        TICKET_ATT_OWNER,
        TICKET_ATT_KEYWORD,
        TICKET_ATT_STATUS,
        TICKET_ATT_RESOLUTION,
        TICKET_ATT_VERSION,
        TICKET_ATT_MILESTONE,
        TICKET_ATT_DESCRIPTION,
        TICKET_ATT_REPORTER,
        TICKET_ATT_PRIORITY,
        TICKET_ATT__NUMBER,
        TICKET_ATT__DATE_CREATED,
        TICKET_ATT__DATE_MODIFIED,
        TICKET_ATT_IMPLEMENTED_IN
    };

    private Map     attributes;
    private Date    created;
    private Date    modified;
    private Integer number;

    // Constructors

    /** 
     * This constructor creates a new TracTicket.
     * 
     */
    public TracTicket(Integer aNumber, Date aCreated, Date aMod, Map someAttr) {
        this.number = aNumber;
        this.created = aCreated;
        this.modified = aMod;
        this.attributes = someAttr;
    }
    
	public Integer getNumber() {
		return number;
	}

	public Date getCreated() {
		return created;
	}

	public Date getModified() {
		return modified;
	}

	public String getCc() {
		return (String) attributes.get(TICKET_ATT_CC);
	}

	public String getComponent() {
		return (String) attributes.get(TICKET_ATT_COMPONENT);
	}

	public String getDescription() {
		return (String) attributes.get(TICKET_ATT_DESCRIPTION);
	}

	public String getImplementedIn() {
		return (String) attributes.get(TICKET_ATT_IMPLEMENTED_IN);
	}

	public String getKeyword() {
		return (String) attributes.get(TICKET_ATT_KEYWORD);
	}

	public String getMilestone() {
		return (String) attributes.get(TICKET_ATT_MILESTONE);
	}

	public String getOwner() {
		return (String) attributes.get(TICKET_ATT_OWNER);
	}

	public String getPriority() {
		return (String) attributes.get(TICKET_ATT_PRIORITY);
	}

	public String getReporter() {
		return (String) attributes.get(TICKET_ATT_REPORTER);
	}

	public String getResolution() {
		return (String) attributes.get(TICKET_ATT_RESOLUTION);
	}

	public String getStatus() {
		return (String) attributes.get(TICKET_ATT_STATUS);
	}

	public String getSummary() {
		return (String) attributes.get(TICKET_ATT_SUMMARY);
	}

	public String getType() {
		return (String) attributes.get(TICKET_ATT_TYPE);
	}

	public String getVersion() {
		return (String) attributes.get(TICKET_ATT_VERSION);
	}

    public String[] getAttributeNames() {
        return TICKET_ATTS;
    }

    public Object getAttributeValue(String aAttrName) {
        if (TICKET_ATT__NUMBER.equals(aAttrName)) {
            return this.number;
        }
        if (TICKET_ATT__DATE_CREATED.equals(aAttrName)) {
            return this.created;
        }
        if (TICKET_ATT__DATE_MODIFIED.equals(aAttrName)) {
            return this.modified;
        }
        return this.attributes.get(aAttrName);
    }

    public Object setAttributeValue(String aAttrName, Object aValue) {
        if (TICKET_ATT__NUMBER.equals(aAttrName)) {
            Integer theNumber = this.number;
            this.number = (Integer) aValue;
            return theNumber;
        }
        if (TICKET_ATT__DATE_CREATED.equals(aAttrName)) {
            Date theDate = this.created;
            this.created = (Date) aValue;
            return theDate;
        }
        if (TICKET_ATT__DATE_MODIFIED.equals(aAttrName)) {
            Date theDate = this.modified;
            this.modified = (Date) aValue;
            return theDate;
        }
        return this.attributes.put(aAttrName, aValue);
    }
    
    public static TracTicket getTicket(TracConnection trac, Integer aTicketNumber) {
        Ticket ticket = trac.getTicket();
		int i = 0;
		while (i < FETCH_TICKET_RETRY) {
			try {
				Vector theVector = ticket.get(aTicketNumber);
				if (theVector == null) {
					return null;
				}
				return new TracTicket(
						(Integer) theVector.get(0), 
						(Date)    theVector.get(1), 
						(Date)    theVector.get(2), 
						(Map)     theVector.get(3));
			} catch (RuntimeException ex) {
				/*
				 * Under unclear circumstances, sometimes contacting XmlRPC fails. So we just try again.
				 */
				i++;
				if (i < FETCH_TICKET_RETRY) {
					System.err.println("Unable to fetch Ticket for number " + aTicketNumber + ". Retry " + (FETCH_TICKET_RETRY - i) + " times.");
					try {
						Thread.sleep(2000);
					} catch (InterruptedException ex1) {
						// ignore
					}
				} else {
					throw ex;
				}
			}
		}
		throw new RuntimeException("Unable to fetch Ticket " + aTicketNumber);

    }
    
}

