<div align="center">

<h1>argent-http-core</h1>

<p>A production Ktor <code>HttpClient</code> factory built around the one hard part of
mobile networking: <strong>token refresh</strong>.</p>

<img src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white"/>
<img src="https://img.shields.io/badge/Ktor-3.2-087CFA?style=flat-square"/>
<img src="https://img.shields.io/badge/JVM-17-437291?style=flat-square"/>
<img src="https://img.shields.io/badge/license-Apache_2.0-8b949e?style=flat-square"/>

</div>

---

## What it is

Three client builders over one shared base config, and a **refresh policy** you can
unit-test without a server:

| Builder | When | Auth behaviour |
|---|---|---|
| `createAuthenticated` | logged-in API calls | bearer on every request; refresh-on-401 with careful failure classification |
| `createOptionallyAuthenticated` | endpoints hit pre- and post-login | bearer attached only when present, read fresh each request |
| `createUnauthenticated` | login / OTP / public | no auth, no retry |

## The part interviewers probe

Attaching a bearer header is trivial. What separates a good implementation is
**what you do when the refresh itself fails** — and most teams get this wrong by
logging the user out too eagerly.

`decideRefresh` is a pure function, so the whole policy is a table you can read:

| Refresh outcome | Verdict | Why |
|---|---|---|
| `401` | **expired** | the refresh token itself is dead → force logout |
| `403` | **expired** | account suspended / forbidden → force logout |
| `5xx` | **unavailable** | the *server* is sick, not the session → **keep the user logged in** |
| network error | **unavailable** | no connectivity → keep the session |
| `200`, blank/malformed body | **expired** | a malformed success is indistinguishable from a broken session |

The `5xx` row is the one that matters: logging out on a backend blip turns a
transient outage into a support-ticket storm. The client deliberately returns
`null` (no retry, session intact) rather than firing the logout path.

## Usage

```kotlin
val factory = HttpClientFactory(
    serverConfig = ServerConfig(authBaseUrl = "https://api.example.com/"),
    tokens = myTokenStore,          // DataStore / Keychain / SQLDelight
    authEvents = myAuthEvents,      // funnels into your single logout path
)

val api = factory.createAuthenticated("https://api.example.com/")
```

Porting to iOS / KMP is a one-line change: swap the `CIO` engine for Darwin/OkHttp
and make `TokenStore` an `expect`/`actual` — the refresh logic is platform-agnostic.

## Why the seams exist

- **`TokenStore`** — tokens are read *fresh on every request and every refresh*, so
  a logout that clears the store is reflected immediately (no memoised-token cache to
  invalidate).
- **`AuthEvents`** — every client that hits an unrecoverable 401/403 funnels into one
  sign-out path instead of each reinventing it.
- **`decideRefresh`** — pure, so the policy is `9` unit tests, not a prayer.

## License

[Apache-2.0](LICENSE) · Chinmay Tayade · [LinkedIn](https://www.linkedin.com/in/chinmaytayade)
