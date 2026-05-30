package de.hallerweb.enterprise.prioritize.service.address;

import de.hallerweb.enterprise.prioritize.model.address.Address;
import de.hallerweb.enterprise.prioritize.repository.address.AddressRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor // Erzeugt den Konstruktor für die finale Repository-Variable
public class AddressService {

    private final AddressRepository addressRepository;

    @Transactional(readOnly = true)
    public List<Address> findAll() {
        return addressRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Address findById(Long id) {
        return addressRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Address not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public Collection<Address> findByFilter(Address filter) {
        if (filter == null) {
            return addressRepository.findAll();
        }
        // Dank der @Param Nutzung im Repository ist die Reihenfolge hier nun sicher
        return addressRepository.findByFilter(
                filter.getCity(),
                filter.getZipCode(),
                filter.getStreet(),
                filter.getHousenumber(),
                filter.getFloor(),
                filter.getCountry()
        );
    }

    public Address createAddress(Address address) {
        return addressRepository.save(address);
    }

    public void updateAddress(Long id, Address addressDetails) {
        // Erst laden, dann ändern -> Schützt vor Datenverlust bei Teil-Updates
        Optional<Address> foundAddress = addressRepository.findById(id);
        Address address = foundAddress.orElseThrow(() -> new EntityNotFoundException("Address not found with id: " + id));

        address.setCity(addressDetails.getCity());
        address.setZipCode(addressDetails.getZipCode());
        address.setStreet(addressDetails.getStreet());
        address.setHousenumber(addressDetails.getHousenumber());
        address.setCountry(addressDetails.getCountry());
        address.setFloor(addressDetails.getFloor());
        address.setPhone(addressDetails.getPhone());
        address.setFax(addressDetails.getFax());
        address.setMobile(addressDetails.getMobile());

        // save() ist hier dank @Transactional und Dirty Checking optional
    }

    public Address partialUpdateAddress(Long id, Address patch) {
        Address existing = addressRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Address not found with id: " + id));

        // Nur Felder überschreiben, die im Patch-Request nicht null sind
        if (patch.getStreet() != null)      existing.setStreet(patch.getStreet());
        if (patch.getHousenumber() != null) existing.setHousenumber(patch.getHousenumber());
        if (patch.getFloor() != null)       existing.setFloor(patch.getFloor());
        if (patch.getZipCode() != null)     existing.setZipCode(patch.getZipCode());
        if (patch.getCity() != null)        existing.setCity(patch.getCity());
        if (patch.getCountry() != null)     existing.setCountry(patch.getCountry());
        if (patch.getPhone() != null)       existing.setPhone(patch.getPhone());
        if (patch.getFax() != null)         existing.setFax(patch.getFax());
        if (patch.getMobile() != null)      existing.setMobile(patch.getMobile());

        return addressRepository.save(existing);
    }


    public void deleteAddress(Long id) {
        if (!addressRepository.existsById(id)) {
            throw new EntityNotFoundException("Address not found with id: " + id);
        }
        addressRepository.deleteById(id);
    }
}