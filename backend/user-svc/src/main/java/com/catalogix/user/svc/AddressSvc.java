package com.catalogix.user.svc;

import com.catalogix.user.dto.AddressRequest;
import com.catalogix.user.dto.AddressResponse;
import com.catalogix.user.exception.AddressNotFoundException;
import com.catalogix.user.model.Address;
import com.catalogix.user.repository.AddressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Address book CRUD for the current user. Every access goes through findByIdAndUserId, so
// ownership is enforced by the query itself.
@Service
public class AddressSvc {

    private final AddressRepository repo;

    public AddressSvc(AddressRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> listForUser(Long userId) {
        return repo.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId).stream()
                .map(AddressResponse::from)
                .toList();
    }

    @Transactional
    public AddressResponse create(Long userId, AddressRequest req) {
        Address address = new Address();
        address.setUserId(userId);
        applyFields(address, req);

        // The first address is always the default regardless of makeDefault, so checkout always
        // has something to preselect.
        boolean shouldBeDefault = req.isMakeDefault() || repo.countByUserId(userId) == 0;
        if (shouldBeDefault) {
            clearExistingDefault(userId);
            address.setDefault(true);
        }

        return AddressResponse.from(repo.save(address));
    }

    @Transactional
    public AddressResponse update(Long userId, Long addressId, AddressRequest req) {
        Address address = repo.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new AddressNotFoundException(addressId));
        applyFields(address, req);

        if (req.isMakeDefault() && !address.isDefault()) {
            clearExistingDefault(userId);
            address.setDefault(true);
        }

        return AddressResponse.from(repo.save(address));
    }

    @Transactional
    public void delete(Long userId, Long addressId) {
        Address address = repo.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new AddressNotFoundException(addressId));
        boolean wasDefault = address.isDefault();
        repo.delete(address);

        // If the default was deleted, promote the most recently added remaining address.
        if (wasDefault) {
            repo.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId).stream()
                    .findFirst()
                    .ifPresent(next -> {
                        next.setDefault(true);
                        repo.save(next);
                    });
        }
    }

    @Transactional
    public AddressResponse setDefault(Long userId, Long addressId) {
        Address address = repo.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new AddressNotFoundException(addressId));
        clearExistingDefault(userId);
        address.setDefault(true);
        return AddressResponse.from(repo.save(address));
    }

    @Transactional(readOnly = true)
    public AddressResponse getOne(Long userId, Long addressId) {
        return repo.findByIdAndUserId(addressId, userId)
                .map(AddressResponse::from)
                .orElseThrow(() -> new AddressNotFoundException(addressId));
    }

    private void clearExistingDefault(Long userId) {
        repo.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId).stream()
                .filter(Address::isDefault)
                .forEach(a -> {
                    a.setDefault(false);
                    repo.save(a);
                });
    }

    private void applyFields(Address address, AddressRequest req) {
        address.setLabel(req.getLabel());
        address.setLine1(req.getLine1());
        address.setLine2(req.getLine2());
        address.setCity(req.getCity());
        address.setState(req.getState());
        address.setPincode(req.getPincode());
        address.setPhone(req.getPhone());
    }
}
