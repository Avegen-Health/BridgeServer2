# Creating a Testing Subpopulation (Consent Not Required)

This guide outlines the steps to create and configure a subpopulation for testing purposes where user consent is **not required**.

**Prerequisites:**
*   Authenticated session with `DEVELOPER` or `ADMIN` roles.
*   `Bridge-Session` token.
*   `Content-Type: application/json` header.

## 1. Create Subpopulation
Create a new subpopulation. By default, it will be `required: true`.

```bash
POST /v3/subpopulations
Content-Type: application/json

{
    "name": "Testing Subpopulation",
    "required": true, 
    "criteria": { ... } 
}
```
*Note: You likely cannot set `required: false` immediately if no consent is published.*

## 2. Create Consent Document
Create a consent document for this subpopulation.

```bash
POST /v3/subpopulations/{guid}/consents
Content-Type: application/json

{
    "documentContent": "<p>Test consent content.</p>"
}
```
**Response:** Note the `createdOn` timestamp (e.g., `2025-12-16T05:33:59.408Z`).

## 3. Publish Consent Document
Publish the document created in Step 2. This resolves the `Timestamp is 0` error.

```bash
POST /v3/subpopulations/{guid}/consents/{createdOn}/publish
```
*   Replace `{guid}` with your subpopulation GUID.
*   Replace `{createdOn}` with the timestamp from Step 2.

## 4. Update Subpopulation to "Not Required"
Now that a consent is published, you can update the subpopulation configuration.

```bash
POST /v3/subpopulations/{guid}
Content-Type: application/json

{
    "guid": "{guid}",
    "required": false,  // <--- SET TO FALSE
    "publishedConsentCreatedOn": "{createdOn}", // <--- MUST MATCH PUBLISHED CONSENT
    ... (include other original fields)
}
```

## Troubleshooting
*   **S3 Error (AccessControlListNotSupported):** If publishing fails, ensure the S3 bucket allows ACLs.
    ```bash
    aws s3api put-bucket-ownership-controls --bucket {bucket-name} --ownership-controls="Rules=[{ObjectOwnership=BucketOwnerPreferred}]"
    aws s3api put-public-access-block --bucket {bucket-name} --public-access-block-configuration "BlockPublicAcls=false,IgnorePublicAcls=false,BlockPublicPolicy=false,RestrictPublicBuckets=false"
    ```
