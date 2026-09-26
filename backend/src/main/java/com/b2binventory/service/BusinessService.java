package com.b2binventory.service;

import com.b2binventory.domain.Business;
import com.b2binventory.repository.BusinessRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BusinessService {

    private final BusinessRepository businessRepository;

    public BusinessService(BusinessRepository businessRepository) {
        this.businessRepository = businessRepository;
    }

    public Business getBusinessById(Long id) {
        return businessRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Business not found with id: " + id));
    }

    public List<Business> getAllBusinesses() {
        return businessRepository.findAll();
    }

    public Business updateBusiness(Long id, Business businessData) {
        Business business = getBusinessById(id);
        
        if (businessData.getName() != null) {
            business.setName(businessData.getName());
        }
        if (businessData.getType() != null) {
            business.setType(businessData.getType());
        }
        if (businessData.getMobile() != null) {
            business.setMobile(businessData.getMobile());
        }
        if (businessData.getEmail() != null) {
            business.setEmail(businessData.getEmail());
        }
        if (businessData.getAddress() != null) {
            business.setAddress(businessData.getAddress());
        }
        if (businessData.getCity() != null) {
            business.setCity(businessData.getCity());
        }
        if (businessData.getState() != null) {
            business.setState(businessData.getState());
        }
        if (businessData.getCountry() != null) {
            business.setCountry(businessData.getCountry());
        }
        if (businessData.getGstNumber() != null) {
            business.setGstNumber(businessData.getGstNumber());
        }

        return businessRepository.save(business);
    }
}
