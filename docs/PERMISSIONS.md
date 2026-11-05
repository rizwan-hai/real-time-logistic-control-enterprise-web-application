# Roles and Permissions

**Status:** Phase 0C implements the identity tables, permission evaluator, tenant-aware permission resolution, role assignments, user/role/permission administration, and customer/driver/warehouse object-scope checks. The broad business permission matrix below remains proposed until each business module lands.

Phase 0C identity management permissions are `identity.users.read`, `identity.users.manage`, `identity.roles.read`, `identity.roles.manage`, `identity.permissions.read`, `identity.permissions.manage`, and `identity.sessions.manage`. These are seeded only under the `dev` profile. Production provisioning of initial identity roles and grants remains an operational bootstrap decision.

| Role | Typical scope | Example capability set |
|---|---|---|
| Platform Administrator | Platform configuration; tightly audited | Manage organizations, security configuration, global integrations. No implicit business-data access. |
| Organization Administrator | One organization | Manage users/roles and organization settings. |
| Operations Manager | Assigned organization/sites | Read and coordinate orders, shipments, exceptions, and operational dashboards. |
| Order Clerk | Assigned organization | Create/edit drafts, submit orders, read permitted shipments. |
| Warehouse Operator | Assigned warehouse(s) | Execute receipts, picks, packs, stock counts; no financial authority. |
| Transport Dispatcher | Assigned organization/regions | Plan trips, assign drivers/vehicles, monitor shipments. |
| Driver | Own active assignments | Read own tasks; post locations/status/POD only for assigned work. |
| Finance Clerk | Assigned organization | Prepare charges/invoices and record permitted payment reconciliation. |
| Finance Approver | Assigned organization | Approve/issue/credit invoices within configured thresholds. |
| Customer User | Own customer account | View own orders/shipments/invoices and submit permitted service requests. |
| Auditor | Explicitly granted organization(s) | Read audit and selected business records; no mutation. |

## Permission keys (initial catalog)

Use namespaced keys, e.g. `orders.read`, `orders.create`, `orders.submit`, `orders.cancel`, `shipments.read`, `shipments.dispatch`, `exceptions.resolve`, `warehouse.receive`, `warehouse.pick`, `inventory.adjust`, `transport.assign`, `tracking.read`, `tracking.write_own`, `pod.upload_own`, `pod.read`, `billing.read`, `billing.prepare`, `billing.approve`, `billing.issue`, `users.manage`, `roles.manage`, `audit.read`, `integrations.manage`.

| Role | Core grants (all additionally scope-checked) |
|---|---|
| Platform Administrator | Platform configuration and integration administration; business access requires explicit scoped grant. |
| Organization Administrator | `users.manage`, `roles.manage`, broad org master-data read/write; no automatic invoice approval. |
| Operations Manager | Broad scoped order/shipment/exception read; order confirmation, dispatch coordination, exception resolution. |
| Order Clerk | `orders.read/create/submit`; limited shipment read. |
| Warehouse Operator | `warehouse.receive/pick`, stock read; `inventory.adjust` only if specifically granted. |
| Transport Dispatcher | `transport.assign`, shipment/tracking read, exception read/acknowledge. |
| Driver | `tracking.write_own`, `pod.upload_own`, own assignment/shipment/task read and allowed status updates. |
| Finance Clerk | `billing.read/prepare`, payment record entry as allowed. |
| Finance Approver | `billing.read/approve/issue`; thresholds and separation-of-duties rules apply. |
| Customer User | Own account order/shipment/invoice read and allowed order/request submission. |
| Auditor | `audit.read` plus configured read-only business visibility. |

## Enforcement rules

Enforce authorization in Spring Security and application services, including background jobs and WebSocket subscriptions. Scope every query/mutation to organization, assigned warehouse/region, own driver assignment, or customer account. Deny by default. Prevent self-escalation and separation-of-duties violations; log grants/revocations. Avoid trusting roles or object IDs from request payload. Permission catalog, role matrix, customer data visibility, and emergency access rules require approval in Phase 0C.

## Role-permission matrix (proposed)

Legend: **M** manage/create, **R** scoped read, **A** approve/issue, **X** execute own assigned task, **—** no grant. Every grant is additionally constrained by tenant and object scope.

| Role | Users / roles | Orders | Warehouse / stock | Transport / tracking | Exceptions / POD | Billing | Audit / config |
|---|---|---|---|---|---|---|---|
| Platform Administrator | M platform | — | — | — | — | — | M platform config; scoped audit only |
| Organization Administrator | M org users/roles | R | R | R | R | R | R org audit |
| Operations Manager | — | M/R | R | M/R | M/R | R | R operational audit |
| Order Clerk | — | M/R | — | R | R | — | — |
| Warehouse Operator | — | R | X/R; adjust only explicit grant | — | — | — | — |
| Transport Dispatcher | — | R | R availability | M/R | R/acknowledge | — | — |
| Driver | — | assigned R | assigned tasks X | own assigned X/location write | assigned exception/POD X | — | — |
| Finance Clerk | — | R | — | — | — | M prepare/payment record | — |
| Finance Approver | — | R | — | — | — | A within policy | R finance audit |
| Customer User | — | own account R/create allowed | — | own shipment R | own requests/POD view by policy | own invoice R | — |
| Auditor | — | scoped R | scoped R | scoped R | scoped R | scoped R | R audit; no mutation |

This matrix is a role template, not a hard-coded authorization shortcut. Fine-grained permission keys are assigned explicitly; high-risk actions require re-authentication or dual control if policy mandates it. Platform administration does not grant implicit access to customer business records.
