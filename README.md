# Keycloak Spanish e-mail bug: "expirará en 5 minutos minutos"

Reproduces — and demonstrates the fix for — a bug in Keycloak's Spanish community
translations of the base **email** theme.

Five messages append a literal `minutos` right after the link-expiration placeholder,
but that placeholder (`{3}` or `{4}`, depending on the key) is already the **fully
formatted** expiration period produced by `linkExpirationFormatter`
(`5 minutos`, `12 horas`, `1 día`). Real e-mails therefore read:

> Este enlace expirará en **5 minutos minutos**.

and for a 12-hour lifespan the unit is plain wrong:

> Este enlace expirará en **12 horas minutos**.

Affected keys in
`themes/src/main/resources-community/theme/base/email/messages/messages_es.properties`:

* `emailVerificationBody` / `emailVerificationBodyHtml`
* `passwordResetBody` / `passwordResetBodyHtml`
* `executeActionsBody`

The remaining keys of the same file already use the correct form
(e.g. `executeActionsBodyHtml`: *"Este enlace caducará en {4}."*).

* **Upstream issue:** https://github.com/keycloak/keycloak/issues/ISSUE_NUMBER
* **Fix (ready-to-open PR):** https://github.com/enoquefcd/keycloak/pull/9
  (branch [`email-es-duplicate-minutes`](https://github.com/enoquefcd/keycloak/tree/email-es-duplicate-minutes))

## 1. 30-second repro (Java 17+)

```console
$ cd unit && java RenderCheck.java
== messages_es.bug.properties ==
  passwordResetBody    Este enlace expirará en 5 minutos minutos.
  executeActionsBody   Este enlace expirará en 5 minutos minutos.
  passwordResetBody    Este enlace expirará en 1 minuto minutos.
  executeActionsBody   Este enlace expirará en 1 minuto minutos.
  passwordResetBody    Este enlace expirará en 12 horas minutos.
  executeActionsBody   Este enlace expirará en 12 horas minutos.

== messages_es.fixed.properties ==
  passwordResetBody    Este enlace expirará en 5 minutos.
  executeActionsBody   Este enlace expirará en 5 minutos.
  passwordResetBody    Este enlace expirará en 1 minuto.
  executeActionsBody   Este enlace expirará en 1 minuto.
  passwordResetBody    Este enlace expirará en 12 horas.
  executeActionsBody   Este enlace expirará en 12 horas.
```

`RenderCheck.java` renders the bundle values exactly the way Keycloak does
(`MessageFormat` + the verbatim `LinkExpirationFormatterMethod#format` logic).

* `messages_es.bug.properties` — verbatim from Keycloak `26.7.0` (byte-identical to
  current `main` @ `c50bfbdd2a`)
* `messages_es.fixed.properties` — the same file after the fix

## 2. End-to-end repro (real Keycloak, real e-mails)

Requires Docker Compose, `curl`, `python3`.

```console
$ cd e2e && docker compose up -d   # Keycloak 26.7.0 + Mailpit
$ ./send-emails.sh                 # admin-triggered e-mail in both realms
maria@bug-demo.local     -> Este enlace expirará en 5 minutos minutos.
maria@fix-demo.local     -> Este enlace expirará en 5 minutos.

$ LIFESPAN=43200 ./send-emails.sh  # 12-hour lifespan
maria@bug-demo.local     -> Este enlace expirará en 12 horas minutos.
maria@fix-demo.local     -> Este enlace expirará en 12 horas.
```

(Outputs above are actual captured runs against `quay.io/keycloak/keycloak:26.7.0`.)

Two realms are imported at startup:

* **`bug-demo`** — stock Spanish translations, as shipped in the image.
* **`fix-demo`** — identical, plus an e-mail theme (`e2e/themes/es-fixed`) that
  overrides **only the five affected keys** with the fixed values.

`send-emails.sh` calls the Admin REST `execute-actions-email` endpoint for the demo
user of each realm and prints the expiration sentence of the resulting e-mails via the
Mailpit API. Inspect the full e-mails at http://localhost:8025 (Mailpit UI). Use
`KC_PORT` / `MAILPIT_PORT` to remap host ports.

## The fix

Drop the literal unit from the five keys — `…expirará en {3} minutos.` becomes
`…expirará en {3}.` — matching the form the rest of the file already uses. See the
diff in [enoquefcd/keycloak#9](https://github.com/enoquefcd/keycloak/pull/9).

## License

Apache License 2.0. The `messages_es.*.properties` files, the theme override and the
vendored `LinkExpirationFormatterMethod#format` logic originate from
[Keycloak](https://github.com/keycloak/keycloak) (Apache-2.0).
