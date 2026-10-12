package com.life_insurance_system.controller;

import com.life_insurance_system.model.Application;
import com.life_insurance_system.model.CustomerDetail;
import com.life_insurance_system.model.Policy;
import com.life_insurance_system.model.Claim;
import com.life_insurance_system.model.User;
import com.life_insurance_system.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/cse")
public class CseController {

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private UserService userService;

    @Autowired
    private CustomerDetailService customerDetailService;

    @Autowired
    private PolicyService policyService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PremiumService premiumService;

    @Autowired
    private ClaimService claimService;

    @GetMapping("/dashboard")
    public String cseDashboard() {
        return "cse/dashboard";
    }

    @GetMapping("/applications")
    public String viewApplications(Model model) {
        model.addAttribute("applications", applicationService.getAllApplications());
        return "cse/view_applications";
    }

    @GetMapping("/create-customer")
    public String createCustomerPage(Model model) {
        model.addAttribute("user", new User());
        return "cse/create_customer";
    }

    @PostMapping("/create-customer")
    public String createCustomer(User user,
                                 @RequestParam("dateOfBirth") String dateOfBirth,
                                 @RequestParam("phoneNumber") String phoneNumber,
                                 @RequestParam("address") String address,
                                 RedirectAttributes redirectAttributes) {

        CustomerDetail customerDetail = new CustomerDetail();
        customerDetail.setDateOfBirth(java.sql.Date.valueOf(dateOfBirth));
        customerDetail.setPhoneNumber(phoneNumber);
        customerDetail.setAddress(address);

        userService.registerUser(user, customerDetail);

        redirectAttributes.addFlashAttribute("success", "Customer account created successfully!");
        return "redirect:/cse/dashboard";
    }

    @GetMapping("/application/{id}")
    public String viewApplication(@PathVariable int id, Model model, RedirectAttributes redirectAttributes) {
        Application application = applicationService.getApplicationById(id);
        if (application == null) {
            redirectAttributes.addFlashAttribute("error", "Application not found.");
            return "redirect:/cse/applications";
        }
        model.addAttribute("application", application);
        return "cse/view_application_detail";
    }

    @PostMapping("/application/updateStatus")
    public String updateApplicationStatus(@RequestParam("applicationId") int applicationId, @RequestParam("status") String status, RedirectAttributes redirectAttributes) {
        Application application = applicationService.getApplicationById(applicationId);
        if (application != null) {
            application.setCurrentStatus(Application.ApplicationStatus.valueOf(status));
            applicationService.updateApplication(application);
            redirectAttributes.addFlashAttribute("success", "Application status updated successfully!");
        } else {
            redirectAttributes.addFlashAttribute("error", "Application not found!");
        }
        return "redirect:/cse/applications";
    }

    @PostMapping("/application/accept")
    public String acceptApplication(@RequestParam("applicationId") int applicationId, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        Application application = applicationService.getApplicationById(applicationId);
        if (application != null && application.getCurrentStatus() == Application.ApplicationStatus.PendingCustomer) {
            application.setCurrentStatus(Application.ApplicationStatus.Accepted);
            applicationService.updateApplication(application);

            Policy policy = policyService.createPolicyFromApplication(application);

            com.life_insurance_system.model.Payment payment = new com.life_insurance_system.model.Payment();
            payment.setPolicy(policy);
            payment.setFinanceOfficer(user);
            payment.setAmount(policy.getAnnualPremium());
            payment.setPaymentDate(new java.sql.Date(System.currentTimeMillis()));
            payment.setType(com.life_insurance_system.model.Payment.PaymentType.Schedule);
            payment.setStatus(com.life_insurance_system.model.Payment.PaymentStatus.Due);
            paymentService.createPayment(payment);

            redirectAttributes.addFlashAttribute("success", "Policy created successfully!");
        } else {
            redirectAttributes.addFlashAttribute("error", "Application cannot be accepted at this time.");
        }

        return "redirect:/cse/applications";
    }

    @GetMapping("/review-updates")
    public String reviewCustomerUpdates(Model model) {
        model.addAttribute("customerDetails", customerDetailService.getPendingCustomerDetails());
        return "cse/review_updates";
    }

    @GetMapping("/review-updates/approve/{id}")
    public String approveCustomerUpdate(@PathVariable int id, RedirectAttributes redirectAttributes) {
        customerDetailService.approveCustomerUpdate(id);
        redirectAttributes.addFlashAttribute("success", "Customer update approved successfully!");
        return "redirect:/cse/review-updates";
    }

    @GetMapping("/claims")
    public String viewClaims(Model model) {
        model.addAttribute("claims", claimService.getAllClaims());
        return "cse/view_claims";
    }

    @PostMapping("/claim/forward")
    public String forwardClaimToSia(@RequestParam("claimId") int claimId, RedirectAttributes redirectAttributes) {
        Claim claim = claimService.getClaimById(claimId);
        if (claim != null && claim.getClaimStatus() == Claim.ClaimStatus.Filed) {
            claim.setClaimStatus(Claim.ClaimStatus.PendingSIA);
            claimService.updateClaim(claim);
            redirectAttributes.addFlashAttribute("success", "Claim forwarded to SIA successfully!");
        } else {
            redirectAttributes.addFlashAttribute("error", "Claim not found or cannot be forwarded.");
        }
        return "redirect:/cse/claims";
    }

    @GetMapping("/claim/archive/{id}")
    public String archiveClaim(@PathVariable int id, RedirectAttributes redirectAttributes) {
        claimService.archiveClaim(id);
        redirectAttributes.addFlashAttribute("success", "Claim archived successfully!");
        return "redirect:/cse/claims";
    }

    // Enhanced: View all customers
    @GetMapping("/customers")
    public String viewCustomers(Model model) {
        model.addAttribute("customers", userService.getAllUsers().stream()
                .filter(u -> u.getRole().getRoleName().equals("Customer"))
                .collect(java.util.stream.Collectors.toList()));
        return "cse/view_customers";
    }

    // Enhanced: View specific customer details
    @GetMapping("/customer/{id}")
    public String viewCustomerDetails(@PathVariable int id, Model model, RedirectAttributes redirectAttributes) {
        User customer = userService.getUserById(id);
        if (customer == null || !customer.getRole().getRoleName().equals("Customer")) {
            redirectAttributes.addFlashAttribute("error", "Customer not found.");
            return "redirect:/cse/customers";
        }
        model.addAttribute("customer", customer);
        model.addAttribute("customerDetail", customerDetailService.getCustomerDetailByUser(customer));
        model.addAttribute("policies", policyService.getPoliciesByUser(customer));
        model.addAttribute("applications", applicationService.getAllApplications().stream()
                .filter(a -> a.getUser().getUserId() == id)
                .collect(java.util.stream.Collectors.toList()));
        return "cse/view_customer_detail";
    }

    // Enhanced: Update customer information directly
    @PostMapping("/customer/update")
    public String updateCustomerInfo(@RequestParam("customerId") int customerId,
                                     @RequestParam("firstName") String firstName,
                                     @RequestParam("lastName") String lastName,
                                     @RequestParam("email") String email,
                                     @RequestParam("phoneNumber") String phoneNumber,
                                     @RequestParam("address") String address,
                                     RedirectAttributes redirectAttributes) {
        User customer = userService.getUserById(customerId);
        if (customer != null && customer.getRole().getRoleName().equals("Customer")) {
            customer.setFirstName(firstName);
            customer.setLastName(lastName);
            customer.setEmail(email);
            userService.updateUser(customer, null);

            CustomerDetail customerDetail = customerDetailService.getCustomerDetailByUser(customer);
            if (customerDetail != null) {
                customerDetail.setPhoneNumber(phoneNumber);
                customerDetail.setAddress(address);
                customerDetail.setPendingReview(false);
                customerDetailService.updateCustomerDetail(customerDetail);
            }

            redirectAttributes.addFlashAttribute("success", "Customer information updated successfully!");
            return "redirect:/cse/customer/" + customerId;
        } else {
            redirectAttributes.addFlashAttribute("error", "Customer not found!");
            return "redirect:/cse/customers";
        }
    }

    // Enhanced: Reject customer update request
    @GetMapping("/review-updates/reject/{id}")
    public String rejectCustomerUpdate(@PathVariable int id, RedirectAttributes redirectAttributes) {
        customerDetailService.rejectCustomerUpdate(id);
        redirectAttributes.addFlashAttribute("success", "Customer update rejected!");
        return "redirect:/cse/review-updates";
    }

    // Enhanced: View policy details
    @GetMapping("/policy/{id}")
    public String viewPolicyDetails(@PathVariable int id, Model model, RedirectAttributes redirectAttributes) {
        Policy policy = policyService.getPolicyById(id);
        if (policy == null) {
            redirectAttributes.addFlashAttribute("error", "Policy not found.");
            return "redirect:/cse/applications";
        }
        model.addAttribute("policy", policy);
        model.addAttribute("payments", paymentService.getPaymentsByPolicy(policy));
        return "cse/view_policy_detail";
    }
}