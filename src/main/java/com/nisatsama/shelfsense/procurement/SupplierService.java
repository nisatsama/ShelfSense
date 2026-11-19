package com.nisatsama.shelfsense.procurement;

import com.nisatsama.shelfsense.common.ConflictException;
import com.nisatsama.shelfsense.common.ResourceNotFoundException;
import com.nisatsama.shelfsense.procurement.dto.SupplierRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class SupplierService {

    private final SupplierRepository supplierRepository;

    public SupplierService(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    public Supplier create(SupplierRequest request) {
        String code = request.code().trim();
        if (supplierRepository.existsByCode(code)) {
            throw new ConflictException("Supplier with code already exists: " + code);
        }
        Supplier supplier = new Supplier();
        supplier.setCode(code);
        supplier.setName(request.name().trim());
        supplier.setContactEmail(request.contactEmail());
        supplier.setPhone(request.phone());
        supplier.setAddress(request.address());
        supplier.setLeadTimeDays(request.leadTimeDays() != null ? request.leadTimeDays() : 7);
        supplier.setActive(request.active() == null || request.active());
        return supplierRepository.save(supplier);
    }

    public Supplier update(Long id, SupplierRequest request) {
        Supplier supplier = findById(id);
        supplier.setName(request.name().trim());
        supplier.setContactEmail(request.contactEmail());
        supplier.setPhone(request.phone());
        supplier.setAddress(request.address());
        if (request.leadTimeDays() != null) {
            supplier.setLeadTimeDays(request.leadTimeDays());
        }
        if (request.active() != null) {
            supplier.setActive(request.active());
        }
        return supplierRepository.save(supplier);
    }

    @Transactional(readOnly = true)
    public List<Supplier> findAll() {
        return supplierRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    @Transactional(readOnly = true)
    public Supplier findById(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + id));
    }
}
