# Issue #7 identity / employee audit and candidate validation

2026-10-09 retry1, issue #7 only. The current mounted worktree was clean and already contained candidate implementation commits ce76fd4 and fff5217. The supplied zero-task-commit description describes earlier pre-coding failure, not this mounted Git state; the existing work is preserved. Native #2 is closed; historical “dependency 6” is specification numbering, not native #6. No original database/cache writes, password resets, push, publication, policy changes, schema changes, external dependency changes or extra coding agents. Current standards and ADR 0001 applied; ADR 0002 has no affected image behavior. Plan: [issue7-plan.md](issue7-plan.md).

## Responsibility chain and compatibility

- `/user/user/login`: UserAuthController validates DTO and wraps ApiResult → UserService.login → unchanged WechatService identity exchange → UserMapper.findByOpenid → standard user insert if missing → original JWT fields/secret/TTL/scope. Existing users keep ID; upstream failure precedes lookup/create. Logout still blacklists through existing TokenBlacklistService. No added transaction or uniqueness/concurrency promise: sequential lookup/create is unchanged, and user.openid is not unique in the supplied schema.
- `/admin/employee/*`: AdminEmployeeController obtains authenticated request identity → EmployeeApplicationService validates existing rules → named EmployeeMapper credential, name/page and username-exclusion queries. Status/password/details remain standard primary-key CRUD. Name trimming, update_time DESC/id DESC, page total/empty page and excluded-current-ID duplicate check are unchanged. Legacy account login/register/save and pagination rules now live in AdminService; protocol/session/captcha and template response remain in Controllers. Legacy AdminMapper owns credential/username/list filters. Registration still uses selectOne (duplicate rows fail); new-record save still checks any username match.
- User counts in ReportApplicationService and WorkspaceApplicationService call named UserService/UserMapper methods. Only user registration-time filters moved, retaining inclusive boundaries; other reporting behavior is outside #7.
- Production Wechat upstream failure now retains internal cause in WechatLoginException and uses a dedicated safe handler. Never log third-party URI/body/cause; outward failure business code remains 0. Dev Wechat implementation/fallback is unchanged. Plaintext/equality/default-password behavior is explicitly excluded from strategy rewrite. JWT cookies, headers, claims and blacklist strategy are unchanged.

No paths/schema/JSON fields/types/business codes changed. ApiResult is success code 1 / all failure codes 0; the JWT interceptor additionally returns HTTP 401. Vue consumers: `yuweiju-web-vue/yuweiju-admin/src/api/modules/employee.ts:13`, `src/stores/user.ts:45`, `src/api/http.ts`, and router guards. Miniapp consumers: `yuweiju-weixin-miniapp/common/vendor.js:20067` (login), `:20362` (authentication header). Both clients need no UI edits because APIs are preserved. Java constructors changed only at internal injection sites.

## Existing permission and state boundaries

File evidence in backend `src/main/java/com/codeying/` (Service implementation in `service/impl/`, Mapper in `mapper/`, interceptor in `interceptor/`):

- EmployeeApplicationServiceImpl:45 login only matches status=1 through EmployeeMapper:12; wrong/disabled accounts share existing error. `:70` editPassword rejects absent actor, different empId, missing employee and wrong old password. `:88` setStatus requires nonnull actor, status 0/1 and existing target; it permits any authenticated employee to change any employee including itself. Create/update similarly require nonnull actor; there is no role field/policy in Employee.
- `JwtAuthInterceptor:49` permits OPTIONS; `:57` selects separate admin/user header and signing key; `:74` parses signature/expiry, `:82` checks Redis blacklist, `:88` requires uid, `:94` publishes identity. It does not check a persisted employee's current status or a scope claim. Tests retain those facts; do not infer a production role policy. Separate keys enforce current user/admin separation.
- Service page/getById do not take an actor: authentication is supplied by AdminEmployeeController and interceptor. Mutation Service rules (self password ownership, old password, actor existence as a parameter, legal status and target existence) remain enforced on reuse. Services do not authenticate an arbitrary nonnull caller-supplied ID or reload actor status. Adding those checks/role restrictions or revoking an already issued JWT on disable would change semantics, so left unchanged and recorded as a follow-up decision.
- Address isolation tested through existing UserAddressBookController:209 id + user_id filter; no address implementation change or claim of repairing all reusable user-domain services.
- TokenBlacklistService's existing Redis-error fail-open handling remains unchanged. No production authentication completion claim.

## Exact enabled legacy coverage

Evidence paths below are backend `src/main/java/com/codeying/`. Both Controllers are ordinary `@Controller` components with no disabling profile (`AdminPortalController:18`, `AdminManagementController:21`), scanned by `App:13`. Source registration is distinct from a usable rendered login/template or working tb_admin database.

| Legacy route | Methods | Source evidence | Existing auth boundary |
| --- | --- | --- | --- |
| `/` | unrestricted @RequestMapping | AdminPortalController:32 | Outside JWT; session user decides login view vs /hello redirect |
| `/hello` | unrestricted | AdminPortalController:44 | Outside JWT; no session/role check |
| `/login` | GET, POST | AdminPortalController:64, :79 | Outside JWT; POST checks session captcha case-insensitively, usertype=admin and legacy credentials; writes session user/role, no JWT |
| `/register` | GET, POST | AdminPortalController:54, :112 | Outside JWT; POST checks empty fields/type/duplicate username, creates tb_admin account; no login/captcha prerequisite |
| `/logout` | unrestricted | AdminPortalController:136 | Outside JWT; removes only session user, does not revoke employee JWT |
| `/admin/list` | unrestricted | AdminManagementController:22, :41 | JWT required; no additional session/role authorization |
| `/admin/edit` | unrestricted | AdminManagementController:59 | JWT required; no additional session/role authorization |
| `/admin/detail` | unrestricted | AdminManagementController:76 | JWT required; no additional session/role authorization |
| `/admin/save` | unrestricted | AdminManagementController:89 | JWT required; updates still dereference legacy session user, so JWT alone may fail after a write; no repair or runtime writes attempted |
| `/admin/delete` | unrestricted | AdminManagementController:106 | JWT required; no additional session/role authorization; legacy primary-key delete retained |

`config/WebMvcConfiguration.java:32` applies JWT to /admin/** except /admin/employee/login; `:38` applies it to /user/** except /user/user/login, /user/shop/status, /user/category/list, /user/dish/list, /user/setmeal/list, /user/setmeal/dish/**. Portal paths are outside those patterns, and `JwtAuthInterceptor:55` also passes non-admin/user paths. No other servlet filter/security/interceptor registration found. OPTIONS is exempt even on protected routes.

`AdminPortalController:83` checks captcha before credential lookup; `:93` writes session. A successful legacy session cannot supply the token header required by old /admin routes. Static template evidence: `src/main/resources/templates/login.html:62` posts /login, `:77` requests /captcha; `register.html:70` posts /register; `layout.html:72` links /admin/list. CaptchaServlet is a component (`servlet/CaptchaServlet:23`) with session captcha write at :47, without explicit /captcha servlet mapping in source. Runtime servlet registration has an empty mapping set (`ISSUE7 CAPTCHA_SERVLET_MAPPINGS []`); it is not proof of a reachable /captcha endpoint. A later curl attempt occurred after normal probe shutdown and returned connection failure (HTTP 000), so it supplies no endpoint evidence. POM lacks a Thymeleaf template-engine dependency despite retained Thymeleaf templates; template rendering is not validated. Legacy entity maps `tb_admin` (`entity/Admin:19`); that table is absent from the provided isolated schema. Enabled handler registration does not prove the old portal is usable.

Authentication gaps: publicly registered legacy account creation, session/JWT mismatch, existing /admin/save session dereference, and permissive employee role/state rules above require a maintainer decision to alter. They are preserved, not silently corrected or grounds for claiming production authentication. No unresolved new authentication choice is needed for the compatible responsibility move.

## Verification and evidence

The following original run results were recorded in the mounted report, but its ignored logs/artifacts were absent in the current container. They are historical results, not current runtime proof. Current rerun artifacts are under `.scratch/issue7/` (ignored, not committed); the current results are recorded separately below. They use synthetic isolated credentials only and never print login bodies/tokens. Build/test outputs are not production authentication evidence.

| Command and directory | Result |
| --- | --- |
| `mvn -B -f yuweiju-backend/pom.xml package` at root, Java/Maven normal installed binaries added to PATH | Exit 0; 34 tests, 0 failures/errors/skips; `.scratch/issue7/maven-final.log`, completed 13:02:57 UTC |
| `npm ci` in yuweiju-web-vue/yuweiju-admin | Exit 0; locked dependencies installed, no dependency changes |
| `npm run lint` in same directory | Exit 0 |
| `npm run typecheck` in same directory | Exit 0 |
| `npm run test` in same directory | Exit 0; 2/2 Vitest tests |
| `npm run build` in same directory | Exit 0; existing large-chunk warning |
| `.sandcastle/environment/issue7-check.sh --browser` at root | Exit 0; real SQL/Spring/HTTP/Redis, legacy handler/JWT coverage and synthetic admin PAGE login passed; `.scratch/issue7/check-result.log` |

Ordinary tests: IdentityEmployeeBehaviorTest checks credentials/new/existing/failure paths, validation, existing ownership/status and username-conflict rules, DTO projections/empty page. WechatBehaviorTest binds an HTTP response stub to the actual RestTemplate, checks successful identity, missing identity/transport/malformed failure and credential-safe failure response, dev fallback. IdentityControllerSecurityTest checks delegation/validation, missing identity, authenticated actor propagation, wrong signing key/expiry/blacklist/OPTIONS, legacy captcha/session behavior. LegacyAdminBehaviorTest checks duplicate/new registration, plaintext assignment and legacy save conflict. Mockito and standalone MVC are not real SQL or deployed HTTP proof.

Real probe: candidate jar libraries extracted locally, real Spring App with standalone application-afk.yml and dev,afk (no personal configs), only mysql:3306/sandcastle_fixture and redis:6379. Uses per-run unique synthetic employees/users/address, verifies bound SQL credential/status/username/name/trim/sort/empty/page/time filters; tests Service actor/ownership/status rules; normal HTTP credential login, cross-key rejection and user-owned address/list isolation; existing JWT after disable, logout with actual Redis blacklist; exact Spring legacy handler registry plus unauthenticated legacy /admin route HTTP 401. Does not create tb_admin or claim its Mapper SQL was executed. Removes only owned synthetic rows/cache. Auto-increment advancement and third-party effects are not “rolled back”; no real Wechat calls occur (dev mock-prefixed codes).

The task-specific browser script fills synthetic credentials into the actual Vue username/password inputs and clicks 登录. This is separate evidence from API login. Screenshot is taken after route navigation, without printing credentials/tokens. The actual screenshot was inspected and shows the Vue workspace/dashboard; `.scratch/issue7/synthetic-page-login.png`. Browser fixture credential file is removed on normal completion. It does not satisfy the requested human/real maintainer password demonstration.

Preserved failed runs: first Maven run failed one assertion because a test initially confused HTTP 401 with business code 0; second failed standalone legacy request injection. Both were test fixture mistakes, fixed without changing product auth semantics. The first real probe failed a narrow wall-clock registration-time assumption after its login/isolation assertions; the rerun references persisted DATETIME and compares named counts with direct bound SQL. The previous report recorded preservation of the original probe log, but that ignored artifact is absent from the current mount; product time semantics are unchanged. They are not supervisor iterations. No Git guard rejection occurred. All named build/client commands exited 0 in their own invocation; no frontend source or lockfile modifications resulted.

## Original limitations and pending human acceptance

Historical `docs/verification-evidence/2026-10-05/miniapp-real-login.json`, `miniapp-authenticated-home.json`, `api-results.json` and original driver are missing in this mounted worktree. Earlier simulator/mock success and earlier test JWT do not prove real Wechat or normal administrator PAGE login. Nothing rerun against original data to recreate missing evidence.

1. Maintainer opens the real management PAGE in the authorized demonstration environment, enters existing normal credentials privately, verifies login reaches the demonstration and employee page, then logout rejects reuse. Do not put credentials/tokens in screenshots, commands, logs or evidence, reset the original password, or use a test JWT. Record that actual login/cache writes occurred only under separate original-data authorization.
2. In an authorized real miniapp environment with real Wechat configuration, perform legitimate wx.login and check existing identity reuse, valid login and user isolation. Record upstream failure handling according to current dev/prod policy. User creation/cache/upstream requests require that environment's authorization. Current isolated/mock and response-stub checks do not prove real Wechat acceptance.
3. Decide separately whether the old publicly registered portal is to remain usable, be disabled or gain a different auth boundary; resolve tb_admin/template/captcha usability and session/JWT behavior before claiming its working authentication. Do not perform legacy registration/save/delete against original data for audit proof.
4. Decide separately any changed employee role/status enforcement or JWT state policy. Current exclusions retain plaintext/password/token strategies and dev mock fallback.

Candidate delivery can be complete as a local reviewed implementation with successful automated checks; issue #7 is not fully accepted while real manual PAGE / real Wechat criteria remain unverified. No host publication or production-auth claim.

## Current mounted retry review

The plan was updated before further code edits. Review found and corrected one pagination compatibility regression: `AdminServiceImpl.pageLegacy` used the MyBatis Plus Page constructor, which normalizes nonpositive current pages, whereas the original controller used setters and exposed the requested value through `PagerFooterVO`. Setter construction is restored. The added ordinary footer behavior test covers default page/size and requested pages -1, 0, 1 and 3. This does not change public auth, schema, clients or password/token/dev fallback behavior. Legacy SQL remains unavailable because fixture tb_admin is absent.

Current verification in progress; final results will be added before local commit. Existing commits and report history are preserved. No supervisor continuation or extra coding agent used.

Host handoff: supervisor exhausted its authorized 30-minute total across 2 iterations, without COMPLETE. Pagination compatibility correction was preserved uncommitted. Host independently ran LegacyAdminBehaviorTest in a no-auth/no-model container: exit 0. This correction is committed for exact-candidate independent verification; full acceptance remains pending. Earlier ignored first-iteration logs did not survive framework worktree recreation; historical results are not substituted for new validation.
