package com.life_insurance_system.service;

import com.life_insurance_system.model.RiskAssessment;
import com.life_insurance_system.repository.RiskAssessmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RiskAssessmentService {

    private final RiskAssessmentRepository riskAssessmentRepository;
    private final ApplicationService applicationService;

    @Autowired
    public RiskAssessmentService(RiskAssessmentRepository riskAssessmentRepository, ApplicationService applicationService) {
        this.riskAssessmentRepository = riskAssessmentRepository;
        this.applicationService = applicationService;
    }

    public List<RiskAssessment> getAllRiskAssessments() {
        return riskAssessmentRepository.findAll();
    }

    public RiskAssessment createRiskAssessment(RiskAssessment riskAssessment) {
        // Ensure assessment date is set
        if (riskAssessment.getAssessmentDate() == null) {
            riskAssessment.setAssessmentDate(new java.sql.Timestamp(System.currentTimeMillis()));
        }
        return riskAssessmentRepository.save(riskAssessment);
    }

    public RiskAssessment getRiskAssessmentById(int id) {
        return riskAssessmentRepository.findById(id).orElse(null);
    }

    public RiskAssessment getRiskAssessmentByApplication(com.life_insurance_system.model.Application application) {
        return riskAssessmentRepository.findAll().stream()
                .filter(ra -> ra.getApplication().getApplicationId() == application.getApplicationId())
                .findFirst()
                .orElse(null);
    }

    public RiskAssessment updateRiskAssessment(RiskAssessment riskAssessment) {
        return riskAssessmentRepository.save(riskAssessment);
    }

    public void deleteRiskAssessment(int id) {
        RiskAssessment riskAssessment = riskAssessmentRepository.findById(id).orElse(null);
        if (riskAssessment != null) {
            com.life_insurance_system.model.Application application = applicationService.getApplicationById(riskAssessment.getApplication().getApplicationId());
            if (application != null && application.getCurrentStatus() != com.life_insurance_system.model.Application.ApplicationStatus.Accepted) {
                riskAssessmentRepository.deleteById(id);
            }
        }
    }
}