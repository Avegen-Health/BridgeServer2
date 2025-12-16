# Admin User Management & Workflow (Bridge UAT)

This document outlines the procedures for key administrative tasks on the Bridge UAT server, specifically focusing on App creation, User management, and troubleshooting permissions.

## 1. App Creation
To create a new app (study) environment:
- **Endpoint**: `POST /v1/apps`
- **Constraint**: `shortName` must not contain spaces (e.g., use `"Biaffect3"` not `"Biaffect 3"`).
- **Authentication**: Requires SuperAdmin credentials.

## 2. Admin User Creation (Developer/Researcher Roles)
Standard participant sign-up (`POST /v3/auth/signUp`) **cannot** assign administrative roles. Attempts to add roles during standard sign-up will fail or be ignored.

### Recommended Method: Direct Admin Account Creation
Use the **Accounts API** to create a user with roles in a single step.

**Endpoint**: `POST /v1/accounts`

**Headers**:
- `Content-Type`: `application/json`
- `Bridge-Session`: (Your Admin Session Token)

**Payload**:
```json
{
  "email": "user@example.com",
  "password": "StrongPassword123!",
  "roles": ["developer", "researcher"],
  "dataGroups": ["test_user"],
  "phone": {
    "number": "9011543105",
    "regionCode": "IN"
  },
  "sharingScope": "all_qualified_researchers",
  "emailVerified": true,
  "status": "enabled"
}
```

## 3. Troubleshooting & Common Issues

### Issue: "You do not appear to be a developer..." (BSM Login Error)
**Cause**: The user authenticated successfully but lacks the `developer` or `researcher` role.
**Fix**: Update the user account to include these roles using the `v1/accounts` endpoint.

### Issue: "Account not found" when updating a user
**Cause**: Context Mismatch. Your Admin session is likely authenticated in the `api` app (default), but the target user is in a different app (e.g., `biaffect-3`). The `api` context cannot see users in other apps via the `v1/accounts` endpoint.
**Fix**: Authenticate specifically into the target app.

**Command to get App-Specific Admin Token**:
```bash
curl -X POST https://bridge-uat.grip-research.org/v3/auth/admin/signIn \
  -H "Content-Type: application/json" \
  -d '{
    "appId": "biaffect-3",
    "email": "your_admin_email@example.com",
    "password": "your_password"
}'
```
Use the returned `sessionToken` to manage users within that specific app.

### Issue: "Email address has already been used" (409 Conflict)
**Cause**: A user with that email already exists, possibly with a different ID or in a "deleted" but lingering state, or simply created previously.
**Fix**:
1. Check the error response for the `entityKeys` or `userId` (e.g., `{"userId": "existing-id"}`).
2. Use that ID to Update or Delete the existing account.
3. If updating, use `POST /v1/accounts/{existing-id}` with the new desired state (roles, password).

## 4. Verification
After configuration, verify access by logging into the Bridge Study Manager.

**URL**: [https://bsm-uat.grip-research.org/](https://bsm-uat.grip-research.org/)
**Credentials (Example)**:
- **Study**: `biaffect-3`
- **User**: `akash.shinde+02@avegenhealth.com`
- **Password**: `Password123!`
