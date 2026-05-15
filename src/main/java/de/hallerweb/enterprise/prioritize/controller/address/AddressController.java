package de.hallerweb.enterprise.prioritize.controller.address;

import de.hallerweb.enterprise.prioritize.model.company.Address;
import de.hallerweb.enterprise.prioritize.service.address.AddressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping("/api/v1/addresses")
public class AddressController {

    @Autowired
    AddressService addressService;

    @GetMapping
    public List<Address> getAllAdresses() {
        return addressService.findAll();
    }

    @GetMapping("/{id}")
    public Address get(@PathVariable Integer id) {
        return addressService.findById(id);
    }

    @GetMapping("/{city}/")
    public List<Address> getByCity(@PathVariable String city) {
        return addressService.findByCity(city);
    }

    @GetMapping("/filter")
    public Collection<Address> findByAddress(@RequestBody Address address) {
       return addressService.findByFilter(address);
    }

    @PostMapping("/")
    public void create(@RequestBody Address address) {
        addressService.createAddress(address);
    }

    @PutMapping("/{id}")
    public void update(@RequestBody Address address, @PathVariable Integer id) {
        addressService.updateAddress(id, address);
        Address testAddress = new Address();
        testAddress.setCity("Stuttgart"); // Erkennt die IDE das?
        System.out.println(testAddress.getCity());
    }
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Integer id) {
        addressService.deleteAddress(id);
    }
}
