package com.life_insurance_system.controller;

import com.life_insurance_system.model.Application;
import com.life_insurance_system.model.Payment;
import com.life_insurance_system.model.Policy;
import com.life_insurance_system.model.User;
import com.life_insurance_system.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/fo")
public class FoController {

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private PremiumService premiumService;

    @Autowired
    private PolicyService policyService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ClaimService claimService;

    @GetMapping("/dashboard")
    public String foDashboard() {
        return "fo/dashboard";
    }

    @GetMapping("/applications")
    public String viewFoApplications(Model model) {
        java.util.List<Application> applications = applicationService.getAllApplications();
        java.util.Map<Integer, java.math.BigDecimal> premiums = new java.util.HashMap<>();
        for (Application app : applications) {
            premiums.put(app.getApplicationId(), premiumService.calculatePremium(app));
        }
        model.addAttribute("applications", applications);
        model.addAttribute("premiums", premiums);
        return "fo/view_applications";
    }


    @PostMapping("/finalize-application")
    public String finalizeApplication(@RequestParam("applicationId") int applicationId, @RequestParam("annualPremium") java.math.BigDecimal annualPremium, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        Application application = applicationService.getApplicationById(applicationId);
        if (application != null) {
            application.setCurrentStatus(Application.ApplicationStatus.PendingCustomer);
            applicationService.updateApplication(application);
            redirectAttributes.addFlashAttribute("success", "Premium calculated and application finalized!");
        } else {
            redirectAttributes.addFlashAttribute("error", "Application not found!");
        }

        return "redirect:/fo/applications";
    }

    @GetMapping("/payments")
    public String processPayments(Model model) {
        model.addAttribute("policies", policyService.getAllPolicies());
        return "fo/process_payments";
    }

    @GetMapping("/payouts")
    public String processPayouts(Model model) {
        model.addAttribute("claims", claimService.getAllClaims().stream().filter(c -> c.getClaimStatus() == com.life_insurance_system.model.Claim.ClaimStatus.Approved).collect(java.util.stream.Collectors.toList()));
        return "fo/process_payouts";
    }

    @PostMapping("/record-payment")
    public String recordPayment(@RequestParam("policyId") int policyId, @RequestParam("amount") java.math.BigDecimal amount, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        Policy policy = policyService.getPolicyById(policyId);
        if (policy != null) {
            Payment payment = new Payment();
            payment.setPolicy(policy);
            payment.setFinanceOfficer(user);
            payment.setAmount(amount);
            payment.setPaymentDate(new java.sql.Date(System.currentTimeMillis()));
            payment.setType(Payment.PaymentType.Received);
            payment.setStatus(Payment.PaymentStatus.Paid);
            paymentService.createPayment(payment);
            redirectAttributes.addFlashAttribute("success", "Payment recorded successfully!");
        } else {
            redirectAttributes.addFlashAttribute("error", "Policy not found!");
        }
        return "redirect:/fo/payments";
    }

    @PostMapping("/process-payout")
    public String processPayout(@RequestParam("claimId") int claimId, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        com.life_insurance_system.model.Claim claim = claimService.getClaimById(claimId);
        if (claim != null) {
            claim.setClaimStatus(com.life_insurance_system.model.Claim.ClaimStatus.Paid);
            claimService.updateClaim(claim);

            Payment payment = new Payment();
            payment.setPolicy(claim.getPolicy());
            payment.setFinanceOfficer(user);
            payment.setAmount(claim.getPayoutAmount().negate());
            payment.setPaymentDate(new java.sql.Date(System.currentTimeMillis()));
            payment.setType(Payment.PaymentType.Received);
            payment.setStatus(Payment.PaymentStatus.Paid);
            paymentService.createPayment(payment);
            redirectAttributes.addFlashAttribute("success", "Payout processed successfully!");
        } else {
            redirectAttributes.addFlashAttribute("error", "Claim not found!");
        }
        return "redirect:/fo/payouts";
    }

    @GetMapping("/payment-history")
    public String viewPaymentHistory(Model model) {
        model.addAttribute("payments", paymentService.getAllPayments());
        return "fo/view_payment_history";
    }

    @PostMapping("/generate-schedule")
    public String generatePaymentSchedule(@RequestParam("policyId") int policyId, 
                                          @RequestParam("scheduleType") String scheduleType,
                                          HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        Policy policy = policyService.getPolicyById(policyId);
        if (policy != null) {
            try {
                // Generate payment schedule based on type
                int numberOfPayments = scheduleType.equals("monthly") ? 12 : 
                                      (scheduleType.equals("quarterly") ? 4 : 
                                      (scheduleType.equals("semi-annual") ? 2 : 1));
                
                java.math.BigDecimal paymentAmount = policy.getAnnualPremium().divide(
                    new java.math.BigDecimal(numberOfPayments), 2, java.math.RoundingMode.HALF_UP);
                
                java.util.Calendar calendar = java.util.Calendar.getInstance();
                calendar.setTime(policy.getStartDate());
                
                for (int i = 0; i < numberOfPayments; i++) {
                    Payment payment = new Payment();
                    payment.setPolicy(policy);
                    payment.setFinanceOfficer(user);
                    payment.setAmount(paymentAmount);
                    payment.setPaymentDate(new java.sql.Date(calendar.getTimeInMillis()));
                    payment.setType(Payment.PaymentType.Schedule);
                    payment.setStatus(Payment.PaymentStatus.Due);
                    paymentService.createPayment(payment);
                    
                    // Add time based on schedule type
                    if (scheduleType.equals("monthly")) {
                        calendar.add(java.util.Calendar.MONTH, 1);
                    } else if (scheduleType.equals("quarterly")) {
                        calendar.add(java.util.Calendar.MONTH, 3);
                    } else if (scheduleType.equals("semi-annual")) {
                        calendar.add(java.util.Calendar.MONTH, 6);
                    } else {
                        calendar.add(java.util.Calendar.YEAR, 1);
                    }
                }
                
                redirectAttributes.addFlashAttribute("success", 
                    "Payment schedule (" + scheduleType + ") generated successfully!");
            } catch (Exception e) {
                redirectAttributes.addFlashAttribute("error", 
                    "Failed to generate payment schedule: " + e.getMessage());
            }
        } else {
            redirectAttributes.addFlashAttribute("error", "Policy not found!");
        }
        return "redirect:/fo/payments";
    }

    @PostMapping("/update-payment-status")
    public String updatePaymentStatus(@RequestParam("paymentId") int paymentId,
                                      @RequestParam("status") String status,
                                      RedirectAttributes redirectAttributes) {
        try {
            Payment payment = paymentService.getPaymentById(paymentId);
            if (payment != null) {
                payment.setStatus(Payment.PaymentStatus.valueOf(status));
                paymentService.updatePayment(payment);
                redirectAttributes.addFlashAttribute("success", "Payment status updated successfully!");
            } else {
                redirectAttributes.addFlashAttribute("error", "Payment not found!");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to update payment: " + e.getMessage());
        }
        return "redirect:/fo/payment-history";
    }

    @GetMapping("/payment/delete/{id}")
    public String deletePayment(@PathVariable int id, RedirectAttributes redirectAttributes) {
        try {
            Payment payment = paymentService.getPaymentById(id);
            if (payment != null && (payment.getStatus() == Payment.PaymentStatus.Due || 
                                    payment.getStatus() == Payment.PaymentStatus.Unused)) {
                paymentService.deletePayment(id);
                redirectAttributes.addFlashAttribute("success", "Payment record deleted successfully!");
            } else {
                redirectAttributes.addFlashAttribute("error", 
                    "Cannot delete payment. Only Due or Unused payments can be deleted.");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete payment: " + e.getMessage());
        }
        return "redirect:/fo/payment-history";
    }

    @GetMapping("/remove-unused-schedules/{id}")
    public String removeUnusedSchedules(@PathVariable int id, RedirectAttributes redirectAttributes) {
        paymentService.removeUnusedSchedules(id);
        redirectAttributes.addFlashAttribute("success", "Unused schedules removed successfully!");
        return "redirect:/fo/payments";
    }
}
