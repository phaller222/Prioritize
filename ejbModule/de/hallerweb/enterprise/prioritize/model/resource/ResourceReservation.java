package de.hallerweb.enterprise.prioritize.model.resource;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToOne;
import javax.persistence.Version;

import com.fasterxml.jackson.annotation.JsonBackReference;

import de.hallerweb.enterprise.prioritize.model.calendar.ITimeSpan;
import de.hallerweb.enterprise.prioritize.model.calendar.TimeSpan;
import de.hallerweb.enterprise.prioritize.model.security.User;

/**
 * JPA entity to represent a {@link ResourceReservation}. Users can reserve Resources for a given timeframe. This entity holds information
 * about the User who reserved a Resource, the TimeFrame (from, until) and the Resource itself.
 * 
 * <p>
 * Copyright: (c) 2014
 * </p>
 * <p>
 * Peter Haller
 * </p>
 * 
 * @author peter
 */
@Entity
@NamedQueries({
		@NamedQuery(name = "findPastResoureReservations", query = "select rr FROM ResourceReservation rr WHERE rr.timespan.dateUntil < :now"),
		@NamedQuery(name = "findAllResourceReservations", query = "select rr FROM ResourceReservation rr"),
		@NamedQuery(name = "findResourceReservationsForResourceGroup", query = "select rr FROM ResourceReservation rr WHERE rr.resource.resourceGroup.id = :resourceGroupId"),
		@NamedQuery(name = "findResourceReservationsForDepartment", query = "select rr FROM ResourceReservation rr WHERE rr.resource.department.id = :departmentId"),
		@NamedQuery(name = "findResourceReservationsForUser", query = "select rr FROM ResourceReservation rr WHERE rr.reservedBy.id = :userId") })
public class ResourceReservation implements ITimeSpan {

	@Id
	@GeneratedValue
	private int id;

	@OneToOne
	@JsonBackReference
	private Resource resource;

	@OneToOne
	private User reservedBy;

	@Version
	private int entityVersion; // For optimistic locks

	public int getSlotNumber() {
		return slotNumber;
	}

	public void setSlotNumber(int slotNumber) {
		this.slotNumber = slotNumber;
	}

	@OneToOne(cascade = CascadeType.ALL)
	private TimeSpan timespan; // TimeSpan indication when the resource has been reserved (from/until).

	/**
	 * The number of the slot which can be used for commands. A default resource without a defined bnumber of slots always has one slot and
	 * one slot available. So in that case always slot nr. 1 is assigned.
	 */
	int slotNumber;

	public int getId() {
		return id;
	}

	public Resource getResource() {
		return resource;
	}

	public void setResource(Resource resource) {
		this.resource = resource;
	}

	public User getReservedBy() {
		return reservedBy;
	}

	public void setReservedBy(User reservedBy) {
		this.reservedBy = reservedBy;
	}

	public TimeSpan getTimeSpan() {
		return timespan;
	}

	public void setTimeSpan(TimeSpan timespan) {
		this.timespan = timespan;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + id;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		ResourceReservation other = (ResourceReservation) obj;
		if (id != other.id)
			return false;
		return true;
	}
}
