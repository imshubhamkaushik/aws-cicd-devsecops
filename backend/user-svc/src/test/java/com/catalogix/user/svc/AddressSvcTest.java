package com.catalogix.user.svc;

import com.catalogix.user.dto.AddressRequest;
import com.catalogix.user.dto.AddressResponse;
import com.catalogix.user.exception.AddressNotFoundException;
import com.catalogix.user.model.Address;
import com.catalogix.user.repository.AddressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
class AddressSvcTest {

    @Mock private AddressRepository repo;

    private AddressSvc svc;

    private static final Long USER_ID = 42L;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        svc = new AddressSvc(repo);
    }

    private AddressRequest request(boolean makeDefault) {
        AddressRequest req = new AddressRequest();
        req.setLabel("Home");
        req.setLine1("221B Baker Street");
        req.setCity("Chandigarh");
        req.setState("Punjab");
        req.setPincode("160001");
        req.setPhone("+919812345678");
        req.setMakeDefault(makeDefault);
        return req;
    }

    private Address existing(Long id, boolean isDefault) {
        Address a = new Address();
        a.setId(id);
        a.setUserId(USER_ID);
        a.setLabel("Home");
        a.setDefault(isDefault);
        return a;
    }

    // ---- create ----

    @Test
    void createMakesTheFirstAddressDefaultAutomatically() {
        when(repo.countByUserId(USER_ID)).thenReturn(0L);
        when(repo.findByUserIdOrderByIsDefaultDescCreatedAtDesc(USER_ID)).thenReturn(List.of());
        when(repo.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        AddressResponse resp = svc.create(USER_ID, request(false));

        assertTrue(resp.isDefault());
    }

    @Test
    void createDoesNotDefaultTheSecondAddressUnlessAsked() {
        when(repo.countByUserId(USER_ID)).thenReturn(1L);
        when(repo.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        AddressResponse resp = svc.create(USER_ID, request(false));

        assertFalse(resp.isDefault());
        verify(repo, never()).findByUserIdOrderByIsDefaultDescCreatedAtDesc(any());
    }

    @Test
    void createClearsThePreviousDefaultWhenMakeDefaultIsRequested() {
        Address previousDefault = existing(1L, true);
        when(repo.countByUserId(USER_ID)).thenReturn(1L);
        when(repo.findByUserIdOrderByIsDefaultDescCreatedAtDesc(USER_ID))
                .thenReturn(List.of(previousDefault));
        when(repo.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        AddressResponse resp = svc.create(USER_ID, request(true));

        assertTrue(resp.isDefault());
        assertFalse(previousDefault.isDefault());
    }

    // ---- ownership scoping ----

    @Test
    void updateThrowsForAnAddressBelongingToAnotherUser() {
        when(repo.findByIdAndUserId(1L, USER_ID))
                .thenReturn(Optional.empty());

        AddressRequest updateRequest = request(false);

        assertThrows(
                AddressNotFoundException.class,
                () -> svc.update(USER_ID, 1L, updateRequest));
    }

    @Test
    void deleteThrowsForAnAddressBelongingToAnotherUser() {
        when(repo.findByIdAndUserId(1L, USER_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                AddressNotFoundException.class,
                () -> svc.delete(USER_ID, 1L));

        verify(repo, never()).delete(any());
    }

    // ---- delete promotes a new default ----

    @Test
    void deletingTheDefaultPromotesTheNextMostRecentAddress() {
        Address toDelete = existing(1L, true);
        Address remaining = existing(2L, false);
        when(repo.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(toDelete));
        when(repo.findByUserIdOrderByIsDefaultDescCreatedAtDesc(USER_ID)).thenReturn(List.of(remaining));

        svc.delete(USER_ID, 1L);

        ArgumentCaptor<Address> saved = ArgumentCaptor.forClass(Address.class);
        verify(repo).save(saved.capture());
        assertEquals(2L, saved.getValue().getId());
        assertTrue(saved.getValue().isDefault());
    }

    @Test
    void deletingANonDefaultAddressPromotesNothing() {
        Address toDelete = existing(1L, false);
        when(repo.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(toDelete));

        svc.delete(USER_ID, 1L);

        verify(repo, never()).save(any());
    }

    // ---- setDefault ----

    @Test
    void setDefaultClearsTheOldOneAndSetsTheNew() {
        Address oldDefault = existing(1L, true);
        Address target = existing(2L, false);
        when(repo.findByIdAndUserId(2L, USER_ID)).thenReturn(Optional.of(target));
        when(repo.findByUserIdOrderByIsDefaultDescCreatedAtDesc(USER_ID))
                .thenReturn(List.of(oldDefault, target));
        when(repo.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        AddressResponse resp = svc.setDefault(USER_ID, 2L);

        assertTrue(resp.isDefault());
        assertFalse(oldDefault.isDefault());
    }
}
