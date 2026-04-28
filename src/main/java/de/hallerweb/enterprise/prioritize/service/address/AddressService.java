package de.hallerweb.enterprise.prioritize.service.address;

import de.hallerweb.enterprise.prioritize.model.company.Address;
import de.hallerweb.enterprise.prioritize.repository.address.AddressRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

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
    public Address findById(Integer id) {
        return addressRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Address not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<Address> findByCity(String city) {
        return addressRepository.findByCityEqualsIgnoreCase(city);
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
                filter.getCountry()
        );
    }

    public Address createAddress(Address address) {
        return addressRepository.save(address);
    }

    public void updateAddress(Integer id, Address addressDetails) {
        // Erst laden, dann ändern -> Schützt vor Datenverlust bei Teil-Updates
        Address address = findById(id);

        address.setCity(addressDetails.getCity());
        address.setZipCode(addressDetails.getZipCode());
        address.setStreet(addressDetails.getStreet());
        address.setHousenumber(addressDetails.getHousenumber());
        address.setCountry(addressDetails.getCountry());

        // save() ist hier dank @Transactional und Dirty Checking optional
    }

    public void deleteAddress(Integer id) {
        if (!addressRepository.existsById(id)) {
            throw new EntityNotFoundException("Address not found with id: " + id);
        }
        addressRepository.deleteById(id);
    }
}