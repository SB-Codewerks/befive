# Governance: classification, access requests, Okta app creation

**Relevance:** **1.0: context.** Not built in 0.x. **Sources:** 01 §2.6, decisions 18, 29, 32. 02 §24 (classification and access workflow), §15.6. 03 §5.14.6, §5.25, §5.29, §5.39, §6.9, mockup `access-request-approval.png`. 04 SEC-003, SEC-004, GOV-3, GOV-4.

## Classification (02 §24.1)

- Levels are a settings document (`:classification`), editable by Administrators only and promoted with configuration.
- **Default levels:** Public, Internal, Confidential, Restricted. Customers can define their own.
- Each level has an ordinal. That is what makes "raise" and "lower" meaningful.
- Each level can carry **default policy attachments**, optionally locked, injected as hierarchy level 2a. Example: Restricted forces cache off and fail-closed.
- Each level also carries an **approval routing** chain.
- API Owners may propose a level for their own APIs; an Operator or Administrator confirms it.
- **0.x:** the `:classification` field exists on API, version and operation documents. It is a free tag used for facets and badges. The default policies and routing are not applied yet. This is handoff default H-13.

## Access requests (02 §24.2–§24.7)

- **What is requested:** application × API version (× plan) × scopes, with a justification of 20 to 1,000 characters.
- **Sources:** the portal, or the Admin API (for Consumer Managers and integrations).
- **State machine:** submitted → in review → approved or rejected → provisioning → provisioned or failed. Cancellation is possible while pending.
- **Frozen at submission:** the classification and the route.
- **Separation of duties:** configurable. Requesters cannot approve their own requests.
- **Built-in approvals:**
  - Steps run sequentially and are materialized as `approval_step` rows.
  - `:api-owner` resolves to the API's owners.
  - A group step is satisfied by any member of the group.
- **ServiceNow:**
  - Uses the existing integration plus a `:requests` section: a request record, a webhook back to BeFive, and polling as a fallback.
  - Ships with a Flow Designer script.
  - Supported releases: Washington DC and later.
- **Generic webhook adapter** (`:workflow-webhook`): signed request event out, signed decision callback in. Jira Service Management is the shipped example.
- **Routing:** `{:default :builtin :by-classification {"restricted" "snow-prod"}}`.
- **On approval:**
  - The subscription is provisioned with its plan.
  - Keys are issued, or the Okta client is bound or created.
  - The outcome is written back to the workflow system, audited, and emailed (Angus Mail).

## Optional Okta OAuth app creation (02 §24.8). Includes the Oct 1 key-custody decisions

- **Off by default.** It uses the customer's least-privilege credential: an API token for an admin with a custom role, or an OAuth service app with `okta.apps.manage`.
- Every call is audited. **BeFive never deletes Okta apps.**
- Rate rail: at most 20 creations per hour.
- Apps use the `befive-` name prefix.
- Restricted APIs are excluded from automatic creation by default.
- **Client authentication is `private_key_jwt` only**, with no secrets.
- **Default key path:** the developer uploads a public JWK or PEM.
  - RSA keys must be 2048 bits or more; EC keys P-256.
  - Pasted private material is refused, both in the browser and on the server.
  - An optional in-browser WebCrypto generator exists for convenience. The private key never leaves the browser.
- **Generated keys:**
  - `:allow-generated true` is an Administrator opt-in and writes a critical audit event.
  - **Never available for Restricted APIs.** Can be disabled per API.
  - The pair is generated with RSA 3072 or P-256.
  - The private key is stored only in the secret store, and is **downloadable once** after a fresh sign-in (`max_age=300`).
  - **It is deleted from the secrets store immediately after the download, or after 24 h if never downloaded** (owner decision, Oct 1, 2026). A sweeper runs every 10 min.
  - An expired, undownloaded key is deactivated in Okta.
  - Audit event: `client_key.private_deleted` (`downloaded` | `expired`).
- **Rotation:** upload a second key, then deactivate the old one.
- **"Report key compromised"** deactivates the key in Okta immediately.
- A rotation reminder is sent after 180 d by default.

## Roles (02 §15.6)

- The access-request steps for API Owners cover their own APIs only.
- Consumer Managers handle the Consumer Manager steps.
- Administrators can decide any step, and the decision is recorded as an override.
