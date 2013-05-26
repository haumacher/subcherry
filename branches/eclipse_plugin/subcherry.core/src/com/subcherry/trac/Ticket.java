package com.subcherry.trac;

/**
 * @version   $Revision$  $Author$  $Date$
 */
public abstract class Ticket {

	public abstract String id();

	public abstract String title();

	public abstract String component();

	public abstract String type();

	public abstract String status();

	public abstract String resolution();

	public abstract String milestone();

	public abstract String implementedIn();

	@Override
	public String toString() {
		return " Ticket " + id() + "[milestone:" + milestone() + ",status:" + status() + ",resolution:" + resolution() + "]";
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null || obj.getClass() != Ticket.class) {
			return false;
		}
		return id() == null ? ((Ticket) obj).id() == null : id().equals(((Ticket) obj).id());
	}

	@Override
	public int hashCode() {
		if (id() == null) {
			return 4711;
		}
		return id().hashCode() + 17;
	}

}
