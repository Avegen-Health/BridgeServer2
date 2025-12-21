package org.sagebionetworks.bridge;

import static org.sagebionetworks.bridge.BridgeConstants.API_2_APP_ID;
import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.sagebionetworks.bridge.BridgeConstants.SAGE_ID;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_APP_ID;
import static org.sagebionetworks.bridge.BridgeConstants.TEST_USER_GROUP;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.config.Environment.PROD;
import static org.sagebionetworks.bridge.models.accounts.SharingScope.NO_SHARING;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dynamodb.AnnotationBasedTableCreator;
import org.sagebionetworks.bridge.dynamodb.DynamoInitializer;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.PasswordPolicy;
import org.sagebionetworks.bridge.models.organizations.Organization;
import org.sagebionetworks.bridge.services.AdminAccountService;
import org.sagebionetworks.bridge.services.AppService;
import org.sagebionetworks.bridge.services.OrganizationService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

/**
 * This bootstrapper creates DynamoDB and S3 buckets that are needed by Bridge,
 * as well as
 * two initial apps and administrative accounts. Bootstrapping occurs on startup
 * by default
 * unless you start the Spring Boot application with the "nonit" profile enabled
 * (mvn spring-boot:run -Dspring.profiles.active=noinit). The "noinit" profile
 * will also
 * disable the database migrations that we run through Liquibase.
 */
@Component
@Profile("default")
public class DefaultAppBootstrapper implements ApplicationListener<ContextRefreshedEvent> {

    /**
     * The data group set in the test (api) app. This includes groups that are
     * required for the SDK integration tests.
     */
    static final Set<String> TEST_DATA_GROUPS = ImmutableSet.of("sdk-int-1", "sdk-int-2", "group1");

    /**
     * The task identifiers set in the test (api) app. This includes task
     * identifiers that are required for the SDK
     * integration tests.
     */
    static final Set<String> TEST_TASK_IDENTIFIERS = ImmutableSet.of("task:AAA", "task:BBB", "task:CCC", "CCC",
            "task1");

    private final BridgeConfig bridgeConfig;
    private final AdminAccountService adminAccountService;
    private final AppService appService;
    private final OrganizationService orgService;
    private final DynamoInitializer dynamoInitializer;
    private final AnnotationBasedTableCreator annotationBasedTableCreator;
    private final S3Initializer s3Initializer;
    private final SqsInitializer sqsInitializer;
    private final SnsInitializer snsInitializer;

    @Autowired
    public DefaultAppBootstrapper(BridgeConfig bridgeConfig, AdminAccountService adminAccountService,
            AppService appService, OrganizationService orgService,
            AnnotationBasedTableCreator annotationBasedTableCreator, DynamoInitializer dynamoInitializer,
            S3Initializer s3Initializer, SqsInitializer sqsInitializer, SnsInitializer snsInitializer) {
        this.bridgeConfig = bridgeConfig;
        this.adminAccountService = adminAccountService;
        this.appService = appService;
        this.orgService = orgService;
        this.dynamoInitializer = dynamoInitializer;
        this.annotationBasedTableCreator = annotationBasedTableCreator;
        this.s3Initializer = s3Initializer;
        this.sqsInitializer = sqsInitializer;
        this.snsInitializer = snsInitializer;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        List<TableDescription> tables = annotationBasedTableCreator.getTables("org.sagebionetworks.bridge.dynamodb");
        dynamoInitializer.init(tables);

        // Order matters. S3 depends on SNS which depends on SQS.
        // sqsInitializer.initQueues();
        // snsInitializer.initTopics();
        // s3Initializer.initBuckets();

        RequestContext.set(new RequestContext.Builder().withCallerAppId(API_APP_ID)
                .withCallerRoles(ImmutableSet.of(SUPERADMIN))
                .withCallerUserId("DefaultStudyBootstrapper").build());

        // The integration bootstrap account should be ADMIN in production, and
        // SUPERADMIN in other environments.
        // It should have the Synapse user ID of the synapse.user set for the
        // integration tests.
        String adminEmail = bridgeConfig.get("admin.email");
        String adminPassword = bridgeConfig.get("admin.password");
        String adminSynUserId = bridgeConfig.get("admin.synapse.user.id");
        if (adminSynUserId == null) {
            adminSynUserId = "3565008"; // Fallback for Akash
        }
        boolean bootstrapUserConfigured = (adminEmail != null);

        Roles adminRole = (bridgeConfig.getEnvironment() == PROD) ? ADMIN : SUPERADMIN;

        Account admin = Account.create();
        admin.setEmail(adminEmail);
        admin.setSynapseUserId(adminSynUserId);
        admin.setDataGroups(Sets.newHashSet(TEST_USER_GROUP));
        admin.setSharingScope(NO_SHARING);
        admin.setRoles(Sets.newHashSet(adminRole));
        if (adminPassword != null) {
            admin.setPassword(adminPassword);
        }

        App app = createApp(API_APP_ID, "Test App", provided -> {
            provided.setMinAgeOfConsent(18);
            provided.setDataGroups(Sets.newHashSet(TEST_DATA_GROUPS));
            provided.setTaskIdentifiers(Sets.newHashSet(TEST_TASK_IDENTIFIERS));
            provided.setUserProfileAttributes(Sets.newHashSet("can_be_recontacted"));
        });
        if (bootstrapUserConfigured) {
            createAccount(app, admin);
        }
        App app2 = createApp(API_2_APP_ID, "Test App 2", null);
        if (bootstrapUserConfigured) {
            createAccount(app2, admin);
        }
        App shared = createApp(SHARED_APP_ID, "Shared App", null);
        if (bootstrapUserConfigured && bridgeConfig.getEnvironment() != Environment.PROD) {
            createAccount(shared, admin);
        }

        // Create biaffect-3 app and admin
        App biaffect3 = createApp("biaffect-3", "BiAffect 3", null);
        if (bootstrapUserConfigured) {
            createAccount(biaffect3, admin);
        }

        try {
            // Create admin user for biaffect-3
            biaffect3 = appService.getApp("biaffect-3");
            String email = "farazh@uic.edu";
            if (!adminAccountService.getAccount("biaffect-3", "email:" + email).isPresent()) {
                Account faraz = Account.create();
                faraz.setEmail(email);
                faraz.setRoles(Sets.newHashSet(Roles.ADMIN));
                if (adminPassword != null) {
                    faraz.setPassword(adminPassword);
                }
                // Synapse User ID is not provided, so account will be UNVERIFIED by default.
                // Update to ENABLED.
                faraz = adminAccountService.createAccount("biaffect-3", faraz);

                faraz.setStatus(org.sagebionetworks.bridge.models.accounts.AccountStatus.ENABLED);
                adminAccountService.updateAccount("biaffect-3", faraz);
            }

            // Create admin user for biaffect-3 (Akash)
            String akashEmail = "akash.shinde@avegenhealth.com";
            if (!adminAccountService.getAccount("biaffect-3", "email:" + akashEmail).isPresent()) {
                Account akash = Account.create();
                akash.setEmail(akashEmail);
                akash.setRoles(Sets.newHashSet(Roles.ADMIN));
                akash.setPassword("Password123!");

                akash = adminAccountService.createAccount("biaffect-3", akash);

                akash.setStatus(org.sagebionetworks.bridge.models.accounts.AccountStatus.ENABLED);
                adminAccountService.updateAccount("biaffect-3", akash);
            }
        } catch (EntityNotFoundException e) {
            // biaffect-3 app likely does not exist in this environment
        } catch (Exception e) {
            // Catch all to avoid breaking startup
            e.printStackTrace();
        }
    }

    private App createApp(String appId, String name, Consumer<App> consumer) {
        App app;
        try {
            app = appService.getApp(appId);
        } catch (EntityNotFoundException e) {
            app = createApp();
            app.setName(appId);
            app.setShortName(appId);
            app.setIdentifier(appId);
            if (consumer != null) {
                consumer.accept(app);
            }
            app = appService.createApp(app);
        }
        if (!orgService.getOrganizationOpt(appId, SAGE_ID).isPresent()) {
            Organization org = Organization.create();
            org.setAppId(appId);
            org.setIdentifier(SAGE_ID);
            org.setName("Sage Bionetworks");
            orgService.createOrganization(org);
        }
        return app;
    }

    private void createAccount(App app, Account admin) {
        String syn = "synapseuserid:" + admin.getSynapseUserId();
        if (!adminAccountService.getAccount(app.getIdentifier(), syn).isPresent()) {
            adminAccountService.createAccount(app.getIdentifier(), admin);
        }
    }

    private App createApp() {
        App app = App.create();
        app.setReauthenticationEnabled(false);
        app.setSponsorName("Sage Bionetworks");
        app.setConsentNotificationEmail("akash.shinde@avegenhealth.com");
        app.setTechnicalEmail("akash.shinde@avegenhealth.com");
        app.setSupportEmail("akash.shinde@avegenhealth.com");
        app.setPasswordPolicy(new PasswordPolicy(2, false, false, false, false));
        app.setEmailVerificationEnabled(true);
        app.setVerifyChannelOnSignInEnabled(true);
        return app;
    }
}
