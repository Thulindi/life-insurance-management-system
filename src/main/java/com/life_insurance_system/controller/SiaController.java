package com.life_insurance_system.controller;

import com.life_insurance_system.model.Application;
import com.life_insurance_system.model.RiskAssessment;
import com.life_insurance_system.model.User;
import com.life_insurance_system.service.ApplicationService;
import com.life_insurance_system.service.ClaimService;
import com.life_insurance_system.service.RiskAssessmentService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/sia")
public class SiaController {

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private RiskAssessmentService riskAssessmentService;

    @Autowired
    private ClaimService claimService;

    @GetMapping("/dashboard")
    public String siaDashboard() {
        return "sia/dashboard";
    }

    @GetMapping("/applications")
    public String viewSiaApplications(Model model) {
        model.addAttribute("applications", applicationService.getAllApplications());
        return "sia/view_applications";
    }

    @GetMapping("/assess/{id}")
    public String assessApplication(@PathVariable int id, Model model) {
        Application application = applicationService.getApplicationById(id);
        RiskAssessment riskAssessment = new RiskAssessment();
        riskAssessment.setApplication(application);
        model.addAttribute("riskAssessment", riskAssessment);
        return "sia/assess_application";
    }

    @PostMapping("/assess")
    public String submitAssessment(@ModelAttribute RiskAssessment riskAssessment, @RequestParam("applicationId") int applicationId, @RequestParam("status") String status, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        Application application = applicationService.getApplicationById(applicationId);
        if (application == null) {
            redirectAttributes.addFlashAttribute("error", "Application not found!");
            return "redirect:/sia/applications";
        }

        // Check if assessment already exists for this application
        RiskAssessment existingAssessment = riskAssessmentService.getRiskAssessmentByApplication(application);
        if (existingAssessment != null) {
            redirectAttributes.addFlashAttribute("error", "Assessment already exists for this application. Please edit the existing assessment instead.");
            return "redirect:/sia/reports";
        }

        try {
            // Set required fields
            riskAssessment.setApplication(application);
            riskAssessment.setAdvisor(user);
            riskAssessment.setAssessmentDate(new java.sql.Timestamp(System.currentTimeMillis()));
            
            // Create the assessment
            riskAssessmentService.createRiskAssessment(riskAssessment);

            // Update application status
            application.setCurrentStatus(Application.ApplicationStatus.valueOf(status));
            applicationService.updateApplication(application);
            
            redirectAttributes.addFlashAttribute("success", "Assessment submitted and application status updated!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to create assessment: " + e.getMessage());
            return "redirect:/sia/applications";
        }

        return "redirect:/sia/applications";
    }

    @GetMapping("/claims")
    public String viewSiaClaims(Model model) {
        model.addAttribute("claims", claimService.getAllClaims());
        return "sia/view_claims";
    }

    @GetMapping("/reports")
    public String viewSiaReports(Model model) {
        model.addAttribute("assessments", riskAssessmentService.getAllRiskAssessments());
        return "sia/view_reports";
    }

    @GetMapping("/claim/assess/{id}")
    public String assessClaimPage(@PathVariable int id, Model model) {
        model.addAttribute("claim", claimService.getClaimById(id));
        return "sia/assess_claim";
    }

    @PostMapping("/claim/assess")
    public String assessClaim(@ModelAttribute com.life_insurance_system.model.Claim claim, @RequestParam("claimId") int claimId, @RequestParam("claimStatus") String status, RedirectAttributes redirectAttributes) {
        com.life_insurance_system.model.Claim existingClaim = claimService.getClaimById(claimId);
        if (existingClaim != null) {
            existingClaim.setClaimStatus(com.life_insurance_system.model.Claim.ClaimStatus.valueOf(status));
            existingClaim.setPayoutAmount(claim.getPayoutAmount());
            claimService.updateClaim(existingClaim);
            redirectAttributes.addFlashAttribute("success", "Claim assessed successfully!");
        } else {
            redirectAttributes.addFlashAttribute("error", "Claim not found!");
        }
        return "redirect:/sia/claims";
    }

    @GetMapping("/assessment/edit/{id}")
    public String editAssessmentPage(@PathVariable int id, Model model, RedirectAttributes redirectAttributes) {
        RiskAssessment assessment = riskAssessmentService.getRiskAssessmentById(id);
        if (assessment == null) {
            redirectAttributes.addFlashAttribute("error", "Assessment not found.");
            return "redirect:/sia/reports";
        }
        model.addAttribute("riskAssessment", assessment);
        return "sia/edit_assessment";
    }

    @PostMapping("/assessment/update")
    public String updateAssessment(@RequestParam("assessmentId") int assessmentId,
                                   @RequestParam("riskScore") int riskScore,
                                   @RequestParam("recommendation") String recommendation,
                                   @RequestParam(value = "coverageAdjustment", required = false) Double coverageAdjustment,
                                   @RequestParam(value = "updateApplicationStatus", required = false) String updateStatus,
                                   RedirectAttributes redirectAttributes) {
        RiskAssessment assessment = riskAssessmentService.getRiskAssessmentById(assessmentId);
        if (assessment != null) {
            assessment.setRiskScore(riskScore);
            assessment.setRecommendation(recommendation);
            assessment.setAssessmentDate(new java.sql.Timestamp(System.currentTimeMillis()));
            riskAssessmentService.updateRiskAssessment(assessment);

            // Adjust coverage if provided
            if (coverageAdjustment != null && coverageAdjustment > 0) {
                Application application = assessment.getApplication();
                application.setDesiredCoverage(java.math.BigDecimal.valueOf(coverageAdjustment));
                applicationService.updateApplication(application);
            }

            // Update application status if provided
            if (updateStatus != null && !updateStatus.isEmpty()) {
                Application application = assessment.getApplication();
                application.setCurrentStatus(Application.ApplicationStatus.valueOf(updateStatus));
                applicationService.updateApplication(application);
            }

            redirectAttributes.addFlashAttribute("success", "Assessment updated successfully!");
        } else {
            redirectAttributes.addFlashAttribute("error", "Assessment not found!");
        }
        return "redirect:/sia/reports";
    }

    @GetMapping("/assessment/delete/{id}")
    public String deleteAssessment(@PathVariable int id, RedirectAttributes redirectAttributes) {
        riskAssessmentService.deleteRiskAssessment(id);
        redirectAttributes.addFlashAttribute("success", "Assessment deleted successfully!");
        return "redirect:/sia/reports";
    }
}