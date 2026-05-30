package de.hallerweb.enterprise.prioritize.service.address;

import de.hallerweb.enterprise.prioritize.model.address.Address;
import de.hallerweb.enterprise.prioritize.repository.address.AddressRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("postgres")
@Transactional
class AddressServiceTest {

    @Autowired
    private AddressService addressService;

    @Autowired
    private AddressRepository addressRepository;

    private Address berlinAddress;
    private Address hamburgAddress;

    @BeforeEach
    void setUp() {
        berlinAddress = Address.builder()
                .street("Unter den Linden")
                .housenumber("1")
                .floor("2")
                .zipCode("10117")
                .city("Berlin")
                .country("Deutschland")
                .phone("030-123456")
                .build();
        berlinAddress = addressRepository.save(berlinAddress);

        hamburgAddress = Address.builder()
                .street("Mönckebergstraße")
                .housenumber("7")
                .zipCode("20095")
                .city("Hamburg")
                .country("Deutschland")
                .build();
        hamburgAddress = addressRepository.save(hamburgAddress);
    }

    // ==========================================
    // findAll
    // ==========================================

    @Test
    @DisplayName("findAll: Gibt mindestens die angelegten Testadressen zurück")
    void findAll_ShouldContainTestAddresses() {
        List<Address> all = addressService.findAll();
        assertTrue(all.stream().anyMatch(a -> a.getId().equals(berlinAddress.getId())));
        assertTrue(all.stream().anyMatch(a -> a.getId().equals(hamburgAddress.getId())));
    }

    // ==========================================
    // findById
    // ==========================================

    @Test
    @DisplayName("findById: Existierende Adresse wird korrekt zurückgegeben")
    void findById_ShouldReturnAddress() {
        Address found = addressService.findById(berlinAddress.getId());
        assertNotNull(found);
        assertEquals("Berlin", found.getCity());
        assertEquals("Unter den Linden", found.getStreet());
    }

    @Test
    @DisplayName("findById: Unbekannte ID wirft EntityNotFoundException")
    void findById_UnknownId_ShouldThrow() {
        assertThrows(EntityNotFoundException.class,
                () -> addressService.findById(-999L));
    }

    // ==========================================
    // findByFilter
    // ==========================================

    @Test
    @DisplayName("findByFilter: Findet Adresse anhand von Stadt und PLZ")
    void findByFilter_WithCityAndZip_ShouldReturnMatch() {
        Address filter = Address.builder()
                .city("Berlin")
                .zipCode("10117")
                .build();
        Collection<Address> result = addressService.findByFilter(filter);
        assertTrue(result.stream().anyMatch(a -> a.getId().equals(berlinAddress.getId())));
        assertTrue(result.stream().noneMatch(a -> a.getId().equals(hamburgAddress.getId())));
    }

    @Test
    @DisplayName("findByFilter: Leerer Filter gibt alle Adressen zurück")
    void findByFilter_NullFilter_ShouldReturnAll() {
        Collection<Address> result = addressService.findByFilter(null);
        assertTrue(result.stream().anyMatch(a -> a.getId().equals(berlinAddress.getId())));
        assertTrue(result.stream().anyMatch(a -> a.getId().equals(hamburgAddress.getId())));
    }

    // ==========================================
    // createAddress
    // ==========================================

    @Test
    @DisplayName("createAddress: Neue Adresse wird korrekt persistiert")
    void createAddress_ShouldPersist() {
        Address newAddress = Address.builder()
                .street("Königsallee")
                .housenumber("42")
                .zipCode("40212")
                .city("Düsseldorf")
                .country("Deutschland")
                .build();

        Address created = addressService.createAddress(newAddress);

        assertNotNull(created.getId());
        assertEquals("Düsseldorf", created.getCity());
        assertEquals("Königsallee", created.getStreet());
    }

    // ==========================================
    // updateAddress
    // ==========================================

    @Test
    @DisplayName("updateAddress: Alle Felder werden korrekt überschrieben")
    void updateAddress_ShouldUpdateAllFields() {
        Address update = Address.builder()
                .street("Neue Straße")
                .housenumber("99")
                .floor("3")
                .zipCode("99999")
                .city("München")
                .country("Österreich")
                .phone("089-999999")
                .fax("089-000000")
                .mobile("0170-111111")
                .build();

        addressService.updateAddress(berlinAddress.getId(), update);

        Address updated = addressRepository.findById(berlinAddress.getId()).orElseThrow();
        assertEquals("Neue Straße", updated.getStreet());
        assertEquals("99", updated.getHousenumber());
        assertEquals("3", updated.getFloor());
        assertEquals("99999", updated.getZipCode());
        assertEquals("München", updated.getCity());
        assertEquals("Österreich", updated.getCountry());
        assertEquals("089-999999", updated.getPhone());
        assertEquals("089-000000", updated.getFax());
        assertEquals("0170-111111", updated.getMobile());
    }

    @Test
    @DisplayName("updateAddress: Unbekannte ID wirft EntityNotFoundException")
    void updateAddress_UnknownId_ShouldThrow() {
        Address update = Address.builder().city("Ghost").build();
        assertThrows(EntityNotFoundException.class,
                () -> addressService.updateAddress(-999L, update));
    }

    // ==========================================
    // partialUpdateAddress
    // ==========================================

    @Test
    @DisplayName("partialUpdateAddress: Nur gesetzte Felder werden überschrieben")
    void partialUpdateAddress_ShouldOnlyUpdateNonNullFields() {
        Address patch = Address.builder()
                .city("Berlin-Mitte")
                .phone("030-999999")
                .build();

        Address updated = addressService.partialUpdateAddress(berlinAddress.getId(), patch);

        assertEquals("Berlin-Mitte", updated.getCity());
        assertEquals("030-999999", updated.getPhone());
        // Unverändertes Feld bleibt erhalten
        assertEquals(berlinAddress.getStreet(), updated.getStreet());
        assertEquals(berlinAddress.getZipCode(), updated.getZipCode());
    }

    @Test
    @DisplayName("partialUpdateAddress: Unbekannte ID wirft EntityNotFoundException")
    void partialUpdateAddress_UnknownId_ShouldThrow() {
        Address patch = Address.builder().city("Ghost").build();
        assertThrows(EntityNotFoundException.class,
                () -> addressService.partialUpdateAddress(-999L, patch));
    }

    // ==========================================
    // deleteAddress
    // ==========================================

    @Test
    @DisplayName("deleteAddress: Adresse wird aus der DB entfernt")
    void deleteAddress_ShouldRemoveFromDb() {
        Long id = hamburgAddress.getId();
        addressService.deleteAddress(id);
        assertFalse(addressRepository.existsById(id));
    }

    @Test
    @DisplayName("deleteAddress: Unbekannte ID wirft EntityNotFoundException")
    void deleteAddress_UnknownId_ShouldThrow() {
        assertThrows(EntityNotFoundException.class,
                () -> addressService.deleteAddress(-999L));
    }
}