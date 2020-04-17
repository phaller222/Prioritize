package de.hallerweb.enterprise.prioritize.controller.nfc;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

import de.hallerweb.enterprise.prioritize.controller.CompanyController;
import de.hallerweb.enterprise.prioritize.controller.resource.ResourceController;
import de.hallerweb.enterprise.prioritize.controller.security.SessionController;
import de.hallerweb.enterprise.prioritize.controller.security.UserRoleController;
import de.hallerweb.enterprise.prioritize.model.nfc.NFCCounter;
import de.hallerweb.enterprise.prioritize.model.nfc.NFCUnit;
import de.hallerweb.enterprise.prioritize.model.nfc.NFCUnit.NFCUnitType;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;

@Stateless
public class NFCUnitController implements Serializable {

	@PersistenceContext(unitName = "Prioritze")
	EntityManager em;

	@EJB
	ResourceController resourceController;
	@EJB
	CompanyController companyController;
	@EJB
	UserRoleController userRoleController;
	@EJB
	SessionController sessionController;

	// TODO: DEMOCODE, REMOVE! -----------------------------------------------------------
	public void createNFCUnit() {
		NFCUnit vd = new NFCUnit();

		Resource tempResource = new Resource();
		tempResource.setName("TEST");
		tempResource.setDescription("TEST");
		tempResource.setMaxSlots(1);
		tempResource.setStationary(false);
		tempResource.setRemote(true);
		tempResource.setAgent(false);
		tempResource.setIp("10.0.0.1");

		Resource resource = resourceController.createResource(tempResource, resourceController
				.findResourceGroupByNameAndDepartment("default", 6, userRoleController.findUserById(18, sessionController.getUser()))
				.getId(), sessionController.getUser());

		vd.setWrappedResource(resource);
		em.persist(vd);
		System.out.println("------------- VD CREATED. ------------");
	}
	// ---------------------------------------------------------------------------------

	public NFCUnit findNFCUnitByUUID(String uuid) {
		Query query = em.createNamedQuery("findNFCUnitByUUID");
		query.setParameter("uuid", uuid);
		return (NFCUnit) query.getSingleResult();
	}

	public List<NFCUnit> findNFCUnitsByType(NFCUnitType type) {
		Query query = em.createNamedQuery("findNFCUnitsByType");
		query.setParameter("unitType", type);
		return query.getResultList();
	}

	public NFCUnit createNFCUnit(String name, String description, NFCUnitType unitType, Resource device, String payload, String uuid) {
		NFCUnit unit = createNFCUnit(name, description, unitType, device, payload);
		unit.setUuid(uuid);
		return unit;
	}

	public NFCUnit createNFCUnit(String name, String description, NFCUnitType unitType, Resource device, String payload) {
		NFCUnit unit = new NFCUnit();
		unit.setName(name);
		unit.setDescription(description);
		unit.setUnitType(unitType);
		unit.setSequenceNumber(0);
		unit.setUuid(UUID.randomUUID().toString());
		unit.setLastConnectedDevice(device);
		unit.setLastConnectedTime(new Date());
		unit.setPayload(payload);
		if (payload != null) {
			unit.setPayloadSize(payload.length());
		} else {
			unit.setPayloadSize(0);
		}
		em.persist(unit);
		return unit;
	}

	public NFCUnit updateNFCUnit(String uuid, NFCUnit unit, Resource device) {
		NFCUnit managedUnit = findNFCUnitByUUID(uuid);
		managedUnit.setName(unit.getName());
		managedUnit.setDescription(unit.getDescription());
		managedUnit.setLastConnectedDevice(device);
		managedUnit.setLastConnectedTime(new Date());
		managedUnit.setLatitude(unit.getLatitude());
		managedUnit.setLongitude(unit.getLongitude());
		managedUnit.setPayload(unit.getPayload());
		managedUnit.setPayloadSize(unit.getPayloadSize());
		managedUnit.setSequenceNumber(unit.getSequenceNumber() + 1);
		managedUnit.setUnitType(unit.getUnitType());
		return managedUnit;
	}

	public String readPayload(String uuid, long sequenceNumber) {
		NFCUnit unit = (NFCUnit) findNFCUnitByUUID(uuid);
		if (unit != null) {
			if (sequenceNumber == unit.getSequenceNumber()) {
				return unit.getPayload();
			} else {
				// If sequence number does not match (tampered with?) do NOT return payload data!
				return "";
			}
		} else
			return "";
	}

	public void writePayload(String uuid, String payload, Resource device) {
		NFCUnit unit = (NFCUnit) findNFCUnitByUUID(uuid);
		if (unit != null) {
			unit.setSequenceNumber(unit.getSequenceNumber() + 1);
			unit.setPayload(payload);
			unit.setPayloadSize(payload.length());
			unit.setLastConnectedTime(new Date());
			Resource managedResource = resourceController.getResource(device.getId(), sessionController.getUser());
			unit.setLastConnectedDevice(managedResource);
			unit.setLatitude(managedResource.getLatitude());
			unit.setLongitude(managedResource.getLongitude());
		}
	}

	public NFCCounter createNFCCounter() {
		NFCCounter counter = new NFCCounter();
		counter.setNfcUnit(createNFCUnit("Counter", "NFC Counter", NFCUnitType.COUNTER, null, ""));
		em.persist(counter);
		return counter;
	}

	public NFCCounter createNFCCounterWithUUID(String uuid) {
		NFCCounter counter = new NFCCounter();
		NFCUnit unit = createNFCUnit("Counter", "NFC counter", NFCUnitType.COUNTER, null, "0", uuid);
		counter.setNfcUnit(unit);
		em.persist(counter);
		return counter;
	}

	public NFCCounter createNFCCounter(String uuid) {
		NFCCounter counter = new NFCCounter();
		counter.setNfcUnit(createNFCUnit("Counter", "NFC Counter", NFCUnitType.COUNTER, null, "0"));
		em.persist(counter);
		return counter;
	}

}
