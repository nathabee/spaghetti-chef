0.3.0 was done
the 0.3.1 will be postponed for the future (so we keep this TODO until done)

### 0.3.0 — Local Security, Roles, and Dangerous Action Guards

status: planned

Purpose:

Introduce a local authorization and safety model before PrinterHub grows into
central VPS / multi-farm operation.

This step does not yet implement full enterprise identity management. It creates
the local permission model, role profiles, backend guards, dashboard visibility,
dangerous-action confirmation, and audit behavior needed for safe real-printer
operation.

Goals:

* distinguish read-only monitoring from state-changing printer operations
* introduce local role profiles such as `VIEWER`, `OPERATOR`, and `ADMIN`
* map roles to explicit backend permissions
* enforce permissions in the API, not only in the dashboard
* protect dangerous actions behind explicit confirmation
* add audit entries for operator-triggered state-changing actions
* prepare the security boundary needed before central VPS integration

---

#### 0.3.0.A — Local role and permission model

status: planned

Goals:

* define built-in local roles:

```text
VIEWER
OPERATOR
ADMIN
```

* define explicit permissions for printer viewing, printer configuration, monitoring configuration, job control, SD-card operations, command execution, and security management
* keep roles human-readable while enforcing permission checks internally
* keep the first implementation local and lightweight

Initial role intent:

```text
VIEWER   -> read-only monitoring and diagnostics
OPERATOR -> normal printer operation and prepared job control
ADMIN    -> runtime configuration, printer administration, and security settings
```

Expected result:

* PrinterHub has a clear local access model
* dangerous and administrative actions are no longer treated the same as read-only dashboard viewing

Likely impacted files:

```text
src/main/java/printerhub/security/LocalRole.java
src/main/java/printerhub/security/Permission.java
src/main/java/printerhub/security/RoleProfile.java
src/main/java/printerhub/security/AuthorizationService.java
src/main/java/printerhub/OperationMessages.java
```

---

#### 0.3.0.B — Persist local security settings and role profiles

status: planned

Goals:

* persist local security settings in SQLite
* initialize built-in role profiles with default permissions
* expose local security configuration through API
* avoid user-account complexity in the first version

Suggested persisted model:

```text
security_settings
├── security_enabled
├── default_role
├── require_dangerous_action_confirmation
├── created_at
└── updated_at
```

```text
role_profiles
├── role_name
├── permissions_json
├── built_in
├── created_at
└── updated_at
```

Suggested API:

```text
GET /security/profile
GET /security/roles
PUT /security/roles
GET /settings/security
PUT /settings/security
```

Expected result:

* role behavior is no longer hardcoded only in Java
* local security defaults survive restart
* later authentication can reuse the same role/permission model

Likely impacted files:

```text
src/main/java/printerhub/persistence/SecuritySettingsStore.java
src/main/java/printerhub/persistence/RoleProfileStore.java
src/main/java/printerhub/persistence/DatabaseInitializer.java
src/main/java/printerhub/api/RemoteApiServer.java
src/main/resources/dashboard/api.js
src/main/resources/dashboard/state.js
src/main/resources/dashboard/views/settings.js
```

---

#### 0.3.0.C — Backend authorization guard for API endpoints

status: planned

Goals:

* enforce authorization in backend API handlers
* keep dashboard button visibility as UX only, not as the security boundary
* reject forbidden actions with clear API errors
* classify endpoint permissions consistently

Permission examples:

```text
PRINTER_VIEW
PRINTER_CONFIGURE
MONITORING_VIEW
MONITORING_CONFIGURE
JOB_VIEW
JOB_CREATE
JOB_START
JOB_PAUSE
JOB_RESUME
JOB_CANCEL
JOB_RESTART
JOB_DELETE
SD_VIEW
SD_REFRESH
SD_UPLOAD
SD_DELETE
SD_RECOVERY_CLOSE_UPLOAD
COMMAND_READ
COMMAND_SAFE_CONTROL
COMMAND_DANGEROUS_CONTROL
COMMAND_RAW
SETTINGS_VIEW
SETTINGS_UPDATE
SECURITY_VIEW
SECURITY_MANAGE
```

Example endpoint mapping:

```text
GET /printers                         -> PRINTER_VIEW
POST /printers                        -> PRINTER_CONFIGURE
PUT /printers/{id}                    -> PRINTER_CONFIGURE
DELETE /printers/{id}                 -> PRINTER_CONFIGURE
PUT /settings/monitoring              -> MONITORING_CONFIGURE
POST /jobs/{id}/start                 -> JOB_START
POST /jobs/{id}/pause                 -> JOB_PAUSE
POST /jobs/{id}/resume                -> JOB_RESUME
POST /jobs/{id}/cancel                -> JOB_CANCEL
POST /printers/{id}/sd-card/uploads   -> SD_UPLOAD
DELETE /printer-sd-files/{id}         -> SD_DELETE
POST /printers/{id}/commands          -> command-specific permission
```

Expected result:

* unauthorized state-changing requests are blocked even if called directly through curl
* API responses clearly explain forbidden actions
* central VPS integration later can reuse the same authorization boundary

Likely impacted files:

```text
src/main/java/printerhub/api/RemoteApiServer.java
src/main/java/printerhub/security/AuthorizationService.java
src/main/java/printerhub/security/ActionPermissionResolver.java
src/test/java/printerhub/api/RemoteApiServerTest.java
```

---

#### 0.3.0.D — Dangerous action confirmation model

status: planned

Goals:

* require explicit confirmation for risky printer actions
* avoid accidental heating, movement, file deletion, print start, cancel, recovery close, raw command, and future streamed execution
* return a clear confirmation-required API error when confirmation is missing
* make the dashboard wording explicit about physical printer effects

Risky action groups:

```text
HEATING
MOVEMENT
HOMING
SD_DELETE
FILE_UPLOAD_OVERWRITE
PRINT_START
PRINT_CANCEL
RECOVERY_CLOSE_UPLOAD
RAW_COMMAND
STREAMED_GCODE_EXECUTION
```

Suggested request behavior:

```json
{
  "confirmed": true,
  "confirmationReason": "Operator confirmed nozzle heating"
}
```

Suggested rejection behavior:

```json
{
  "error": "confirmation_required",
  "requiredConfirmation": "HEATING"
}
```

Expected result:

* dangerous operations require intentional operator acknowledgement
* safety behavior is enforced by backend, not only by frontend wording

Likely impacted files:

```text
src/main/java/printerhub/security/DangerousAction.java
src/main/java/printerhub/security/DangerousActionGuard.java
src/main/java/printerhub/api/RemoteApiServer.java
src/main/resources/dashboard/dashboard.js
src/main/resources/dashboard/views/printer-prepare.js
src/main/resources/dashboard/views/printer-control.js
src/main/resources/dashboard/views/printer-sd-card.js
src/main/resources/dashboard/components/job-card.js
```

---

#### 0.3.0.E — Dashboard role-aware controls

status: planned

Goals:

* show current local role/security mode in the dashboard
* hide or disable actions the current role cannot execute
* show clear reason text for disabled controls
* keep dangerous actions visually distinct
* add local security settings to Settings

Dashboard behavior:

```text
VIEWER:
  dashboard remains useful for monitoring, but action buttons are disabled or hidden

OPERATOR:
  normal job and SD-card operation buttons remain available

ADMIN:
  configuration, settings, and security management controls are available
```

Expected result:

* the UI communicates what the current operator can do
* fewer accidental action attempts happen before API rejection
* local role behavior is understandable without reading backend code

Likely impacted files:

```text
src/main/resources/dashboard/views/settings.js
src/main/resources/dashboard/views/monitoring.js
src/main/resources/dashboard/views/printer-print.js
src/main/resources/dashboard/views/printer-sd-card.js
src/main/resources/dashboard/views/printer-prepare.js
src/main/resources/dashboard/views/printer-control.js
src/main/resources/dashboard/components/job-card.js
src/main/resources/dashboard/components/nav.js
src/main/resources/dashboard/dashboard.css
```

---

#### 0.3.0.F — Audit events for authorized and rejected state-changing actions

status: planned

Goals:

* persist audit entries for operator-triggered state-changing actions
* record whether the action was accepted or rejected by authorization/confirmation guards
* include role, permission, action type, printer/job/file target, result, and failure reason
* keep audit useful even before real user accounts exist

Initial actor model:

```text
actor = local-dashboard
role = current/default local role
```

Later actor model:

```text
actor = authenticated user
role = resolved user role
```

Expected result:

* local printer operations become traceable
* rejected dangerous or unauthorized actions are visible
* later authentication can enrich the same audit trail instead of replacing it

Likely impacted files:

```text
src/main/java/printerhub/persistence/PrinterEventStore.java
src/main/java/printerhub/persistence/OperatorAuditStore.java
src/main/java/printerhub/security/AuthorizationService.java
src/main/java/printerhub/api/RemoteApiServer.java
src/main/resources/dashboard/views/printer-history.js
src/main/resources/dashboard/views/monitoring.js
```

---

## Expected result for 0.3.0

After this step, PrinterHub has a real local safety and authorization boundary.

Expected improvements:

* read-only monitoring is separated from printer control
* state-changing actions require suitable permissions
* dangerous physical actions require explicit confirmation
* dashboard controls reflect the current local role
* API endpoints reject unauthorized direct calls
* operator-triggered actions are auditable
* the local runtime is better prepared for central VPS authentication later

Non-goals:

* no central user database yet
* no OAuth/OIDC yet
* no multi-farm identity model yet
* no internet-facing authentication design yet
* no per-tenant enterprise access model yet
 

## My recommendation for implementation order

Do it in this order:

```text
1. Permission enum + built-in role profiles
2. SecuritySettingsStore + RoleProfileStore
3. AuthorizationService
4. Apply guards in RemoteApiServer
5. Add confirmation-required model for dangerous actions
6. Add dashboard role-aware controls
7. Add audit entries
``` 


---



 

 
## `0.3.1 — Local User Accounts, Login, and Dashboard Administration`

This is where you add real user management.

Purpose:

```text
PrinterHub gets local users, password login, sessions, and dashboard-based account administration.
```

This is the step you are asking about.

It should include:

```text
local user database
password hash storage
login/logout API
session handling
current user endpoint
dashboard login screen
admin page for user CRUD
role assignment
enable/disable users
audit identity attached to actions
```

 

## `0.3.1 — Local User Accounts, Login, and Dashboard Administration`

status: planned

Purpose:

Add local authentication to PrinterHub so real users can log in, receive a role/profile, and administer accounts through the dashboard before central VPS integration begins.

Goals:

* add local user accounts stored in the PrinterHub runtime database
* add password-based login for the embedded dashboard
* store only password hashes, never plain passwords
* attach one role/profile to each user
* support user create/edit/disable operations through REST API and dashboard
* protect admin-only user management actions
* expose the currently logged-in user and permissions to the dashboard
* attach authenticated user identity to operator-triggered audit events
* keep local authentication separate from later central VPS authentication

Expected result:

* PrinterHub has a real local login screen
* dashboard actions are executed as a known user, not as anonymous browser activity
* local admins can manage users and roles without editing the database manually
* later central authentication can build on a clear existing permission model

---

## Roles / profiles

Start simple. Do not create too many roles.

Recommended baseline:

```text
ADMIN
OPERATOR
VIEWER
```

### `ADMIN`

Can administer the local runtime.

Allowed examples:

```text
view dashboard
manage printers
manage monitoring settings
manage serial transfer settings
manage users
assign roles
enable/disable users
start/pause/resume/cancel jobs
upload files
delete SD files
execute dangerous actions after confirmation
view history/audit
```

### `OPERATOR`

Can operate printers, but cannot administer the runtime.

Allowed examples:

```text
view dashboard
view monitoring
view jobs
create/start/pause/resume/cancel jobs
upload prepared files if allowed
refresh SD-card files
use recovery actions if allowed
view job history
```

Not allowed:

```text
manage users
change roles
change runtime settings
delete printers
change serial transfer defaults
```

### `VIEWER`

Read-only role.

Allowed examples:

```text
view farm home
view monitoring
view printer status
view jobs
view history
```

Not allowed:

```text
start jobs
cancel jobs
upload files
delete SD files
change settings
manage printers
manage users
execute commands
```

---

## Permission model

Internally, roles should map to permissions.

Do not hardcode role checks everywhere like:

```java
if (role == ADMIN)
```

Better:

```java
authorizationService.require(user, Permission.MANAGE_USERS);
authorizationService.require(user, Permission.START_JOB);
authorizationService.require(user, Permission.DELETE_SD_FILE);
```

Suggested permissions:

```text
DASHBOARD_VIEW
MONITORING_VIEW
PRINTER_VIEW
PRINTER_MANAGE
SETTINGS_VIEW
SETTINGS_UPDATE
USER_VIEW
USER_MANAGE
JOB_VIEW
JOB_CREATE
JOB_START
JOB_PAUSE
JOB_RESUME
JOB_CANCEL
JOB_DELETE
PRINT_FILE_UPLOAD
SD_FILE_VIEW
SD_FILE_MANAGE
SD_FILE_DELETE
COMMAND_EXECUTE_SAFE
COMMAND_EXECUTE_DANGEROUS
RECOVERY_ACTION_EXECUTE
AUDIT_VIEW
```

Then role profiles become simple mappings:

```text
ADMIN    -> all permissions
OPERATOR -> operational permissions
VIEWER   -> read-only permissions
```

This is important because later the central VPS may have more refined profiles without rewriting every guard.

---

## Database model

### `local_users`

```text
id
username
display_name
password_hash
role_name
enabled
created_at
updated_at
last_login_at
```

Important notes:

* `username` should be unique.
* `password_hash` must contain a proper password hash, not plain text.
* `enabled=false` blocks login without deleting the user.
* deleting users should probably be avoided at first; disable is safer for audit history.

### `login_sessions`

```text
id
session_token_hash
user_id
created_at
expires_at
revoked_at
last_seen_at
```

The browser receives a session cookie. The database stores only a hash of the session token.

Do not store raw session tokens.

---

## REST API

### Authentication API

```text
POST /auth/login
POST /auth/logout
GET  /auth/me
```

### User management API

```text
GET  /users
POST /users
GET  /users/{id}
PUT  /users/{id}
POST /users/{id}/enable
POST /users/{id}/disable
POST /users/{id}/password
```

Possible `GET /auth/me` response:

```json
{
  "authenticated": true,
  "user": {
    "id": "u-1",
    "username": "nathabee",
    "displayName": "Nathabee",
    "role": "ADMIN",
    "enabled": true
  },
  "permissions": [
    "DASHBOARD_VIEW",
    "MONITORING_VIEW",
    "PRINTER_MANAGE",
    "USER_MANAGE",
    "JOB_START"
  ]
}
```

Possible login request:

```json
{
  "username": "nathabee",
  "password": "..."
}
```

Possible user creation request:

```json
{
  "username": "atelier-user",
  "displayName": "Atelier User",
  "password": "...",
  "role": "OPERATOR",
  "enabled": true
}
```

---

## Dashboard behavior

Add a login screen before the dashboard loads.

Flow:

```text
open /dashboard
if no valid session:
  show login screen
else:
  load /auth/me
  load dashboard data
```

Dashboard should show:

```text
current user
current role
logout button
```

Example:

```text
Logged in as Nathabee
Role: ADMIN
```

Add a local admin area under Settings or a new Admin page:

```text
Settings
├── Monitoring rules
├── Serial transfer settings
├── Printer administration
└── User administration
```

Or, cleaner after 0.3.1:

```text
PrinterHub
├── Farm Home
├── Monitoring
├── Printers
├── Jobs
├── History
├── Settings
└── Admin
```

I would prefer **Admin** as a separate global menu once users exist.

Admin page:

```text
Admin
├── Users
├── Roles / Profiles
└── Audit visibility
```

For `0.3.1`, roles can be fixed enum values. No need for editable custom roles yet.

---

## Where should role checks apply?

Backend first.

At minimum:

```text
printer create/update/delete
printer enable/disable
settings update
serial transfer settings update
user management
job start/pause/resume/cancel/delete
SD upload
SD delete
SD recovery close
manual commands
dangerous commands
```

The dashboard should also hide/disable controls, but only as UX.

The API must enforce the rule.

Example:

```text
VIEWER clicks Start Job by manipulating browser
-> backend returns 403 Forbidden
```

---

## Should central admin later manage local users?

Later, yes, but not immediately.

The long-term model can be:

```text
local users = local fallback/admin users
central users = VPS-managed users
farm identity = local runtime identity
```

In the central version, you will probably have:

```text
central_user
central_role
farm_access
farm_runtime_identity
```

But the local runtime still needs a local admin for standalone/emergency use.

That is why `0.3.1` is useful even if central auth comes later.

---

# Should unfinished `0.*` become `2.*` later?

Yes, that idea is reasonable, but do it deliberately.

I would think like this:

```text
0.x = local runtime development / prototype-to-product hardening
1.x = central multi-farm platform foundation
2.x = mature local runtime refinement after central architecture exists
```

So after `0.3.x`, you can decide:

```text
1.0.x = central platform baseline
2.0.x = local runtime professionalization / final polish / production-grade local runtime
```

That would make sense because once central architecture exists, local runtime work changes meaning. It is no longer just a standalone project; it becomes an edge runtime inside a central platform.

But I would not rename everything now.

For now:

```text
0.3.0 = local authorization baseline
0.3.1 = local login and user administration
then decide whether 0.4/0.5 remain local roadmap or move to 2.x
```

My recommendation:

```text
finish 0.3.0 and 0.3.1 first
then move to 1.0.0
then move remaining local perfection topics to 2.x if they are no longer central prerequisites
```

---

 