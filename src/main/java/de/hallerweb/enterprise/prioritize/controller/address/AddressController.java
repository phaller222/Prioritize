package de.hallerweb.enterprise.prioritize.controller.address;

import de.hallerweb.enterprise.prioritize.model.company.Address;
import de.hallerweb.enterprise.prioritize.service.address.AddressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;

@RestController
public class AddressController {

    @Autowired
    AddressService addressService;

    @GetMapping("/api/v1/addresses")
    public List<Address> getAllAdresses() {
        return addressService.findAll();
    }

    @GetMapping("/api/v1/addresses/{id}")
    public Address getAdresse(@PathVariable Integer id) {
        return addressService.findById(id);
    }

    @GetMapping("/api/v1/addresses/{city}/")
    public List<Address> getAdressesByCity(@PathVariable String city) {
        return addressService.findByCity(city);
    }

    @GetMapping("/api/v1/addresses/filter")
    public Collection<Address> findByAddress(@RequestBody Address address) {
       return addressService.findByFilter(address);
    }

    @PostMapping("/api/v1/addresses")
    public void createAddress(@RequestBody Address address) {
        addressService.createAddress(address);
    }

    @PutMapping("/api/v1/addresses/{id}")
    public void updateAddress(@RequestBody Address address, @PathVariable Integer id) {
        addressService.updateAddress(id, address);
        Address testAddress = new Address();
        testAddress.setCity("Stuttgart"); // Erkennt die IDE das?
        System.out.println(testAddress.getCity());
    }
    @DeleteMapping("/api/v1/addresses/{id}")
    public void deleteAddress(@PathVariable Integer id) {
        addressService.deleteAddress(id);
    }
}
