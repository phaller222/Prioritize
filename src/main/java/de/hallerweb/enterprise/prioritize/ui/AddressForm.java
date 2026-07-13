/*
 * Copyright 2026 Peter Michael Haller and contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.hallerweb.enterprise.prioritize.ui;

import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import de.hallerweb.enterprise.prioritize.model.address.Address;

/**
 * Reusable address sub-form shared by {@link CompanyView}, {@link DepartmentView} and {@link UserView}
 * (every aggregate that carries an {@link Address}). It is a self-contained {@link FormLayout} with its
 * own {@link Binder}, so a host view only has to embed it, push the current address in via
 * {@link #setAddress(Address)} and pull the edited one out via {@link #getAddressOrNull()}.
 * <p>
 * The form is always bound to a plain, detached {@link Address} bean — never a managed entity. Host views
 * load the current address through a transactional service method that returns a detached copy (a Vaadin
 * thread has no open session, so a lazy address proxy cannot be read here), and on save they attach the
 * bean returned here to the entity they pass back into the service.
 * <p>
 * {@link #getAddressOrNull()} returns {@code null} when every field is blank, so creating an entity
 * without an address does not persist an empty {@link Address} row, and the existing services'
 * "null address = leave unchanged" contract keeps an untouched address intact.
 *
 * @author peter haller
 */
public class AddressForm extends FormLayout {

    private final Binder<Address> binder = new Binder<>(Address.class);

    private final TextField street = new TextField("Street");
    private final TextField housenumber = new TextField("House no.");
    private final TextField floor = new TextField("Floor");
    private final TextField zipCode = new TextField("ZIP");
    private final TextField city = new TextField("City");
    private final TextField country = new TextField("Country");
    private final TextField phone = new TextField("Phone");
    private final TextField fax = new TextField("Fax");
    private final TextField mobile = new TextField("Mobile");

    public AddressForm() {
        add(street, housenumber, floor, zipCode, city, country, phone, fax, mobile);
        setResponsiveSteps(
                new ResponsiveStep("0", 1),
                new ResponsiveStep("320px", 2));
        // Street and city get the wider span in the two-column layout.
        setColspan(street, 2);
        setColspan(city, 2);

        binder.forField(street).bind(Address::getStreet, Address::setStreet);
        binder.forField(housenumber).bind(Address::getHousenumber, Address::setHousenumber);
        binder.forField(floor).bind(Address::getFloor, Address::setFloor);
        binder.forField(zipCode).bind(Address::getZipCode, Address::setZipCode);
        binder.forField(city).bind(Address::getCity, Address::setCity);
        binder.forField(country).bind(Address::getCountry, Address::setCountry);
        binder.forField(phone).bind(Address::getPhone, Address::setPhone);
        binder.forField(fax).bind(Address::getFax, Address::setFax);
        binder.forField(mobile).bind(Address::getMobile, Address::setMobile);
    }

    /** Populates the fields from {@code address} (a detached copy), or clears them when {@code null}. */
    public void setAddress(Address address) {
        binder.readBean(address != null ? address : new Address());
    }

    /**
     * @return a fresh {@link Address} carrying the entered values, or {@code null} if every field is blank.
     */
    public Address getAddressOrNull() {
        Address address = new Address();
        try {
            binder.writeBean(address);
        } catch (ValidationException e) {
            return null; // no validators are configured, so this cannot actually happen
        }
        return isBlank(address) ? null : address;
    }

    private boolean isBlank(Address a) {
        return isEmpty(a.getStreet()) && isEmpty(a.getHousenumber()) && isEmpty(a.getFloor())
                && isEmpty(a.getZipCode()) && isEmpty(a.getCity()) && isEmpty(a.getCountry())
                && isEmpty(a.getPhone()) && isEmpty(a.getFax()) && isEmpty(a.getMobile());
    }

    private boolean isEmpty(String s) {
        return s == null || s.isBlank();
    }
}
