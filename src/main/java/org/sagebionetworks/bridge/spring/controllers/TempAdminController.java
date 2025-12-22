package org.sagebionetworks.bridge.spring.controllers;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.services.AdminAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.collect.Sets;

@RestController
public class TempAdminController {

    @Autowired
    private AdminAccountService adminAccountService;

    @PostMapping("/v1/temp/createAdmin")
    public String createAdmin(@RequestParam String secret) {
        if (!"MySuperSecretTempKey123!".equals(secret)) {
            return "Unauthorized";
        }

        try {
            String appId = "biaffect-3";
            String email = "akash.shinde@avegenhealth.com";

            if (adminAccountService.getAccount(appId, "email:" + email).isPresent()) {
                return "Account already exists";
            }

            Account akash = Account.create();
            akash.setEmail(email);
            akash.setRoles(Sets.newHashSet(Roles.ADMIN));
            akash.setPassword("Password123!");

            akash = adminAccountService.createAccount(appId, akash);

            akash.setStatus(AccountStatus.ENABLED);
            adminAccountService.updateAccount(appId, akash);

            return "Created admin user: " + email;
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }
}
