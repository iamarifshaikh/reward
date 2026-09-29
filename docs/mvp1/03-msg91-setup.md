# MSG91 SMS Setup

How OTP SMS goes from the log adapter to real delivery. Nothing in the application code changes: this is credentials plus one property.

---

## 1. Why Indian SMS needs more than an API key

India requires every commercial SMS to be pre-registered on a **DLT portal** (Distributed Ledger Technology), run by the telecom operators. Three things get registered:

| Thing | What it is | Example |
|---|---|---|
| **Entity** | The business sending the messages | Peppermint Communications Pvt. Ltd. |
| **Header (sender ID)** | The 6 characters the SMS appears from | `INTIQR` |
| **Content template** | The exact text, with variables marked | `Your INTIQ Rewards code is {#var#}. Valid for 5 minutes. Do not share it.` |

You cannot send arbitrary text. The operator matches every message against an approved template and drops anything that doesn't fit. That is why our `SmsRequest` carries a **template code and a variable map** rather than a finished string.

MSG91 can do the DLT registration on your behalf, and their panel mirrors the DLT templates. Registration typically takes a few working days, which is why this was flagged as a critical-path item.

---

## 2. What you need from the MSG91 panel

| Value | Where it is | Goes into |
|---|---|---|
| **Auth key** | Panel → top-right menu → Authkey | `MSG91_AUTH_KEY` |
| **Sender ID / header** | Panel → SMS → Sender IDs (the DLT-approved header) | `MSG91_SENDER_ID` |
| **Template ID** | Panel → SMS → Templates, next to the approved template | `MSG91_TEMPLATE_OTP_LOGIN` |

The template ID is a long string such as `65f1c0a2d6fc0512345678ab`, not the template name.

**When creating the template**, its variable must be named to match what our code sends. `OtpService` sends the map `{"otp": "123456"}`, so the MSG91 template variable should be **`otp`**:

```
Your INTIQ Rewards code is ##otp##. It is valid for 5 minutes. Do not share it with anyone.
```

If your approved template uses a different variable name, say `VAR1`, tell me and I'll change the one line in `OtpService` that builds the map. Don't rename the template to fit the code, since the DLT text is what's approved.

---

## 3. Configure

Put the values in `.env` (already gitignored) and flip the provider:

```bash
SMS_PROVIDER=msg91
MSG91_BASE_URL=https://control.msg91.com
MSG91_AUTH_KEY=<your authkey>
MSG91_SENDER_ID=INTIQR
MSG91_TEMPLATE_OTP_LOGIN=<template id>
```

Restart the app. That is the entire switch: `Msg91SmsSender` becomes the active `SmsSender` bean and `LogSmsSender` is no longer created, so every caller is unchanged.

To go back to console codes for development, set `SMS_PROVIDER=log`.

---

## 4. What we send

```
POST https://control.msg91.com/api/v5/flow
authkey: <auth key>
content-type: application/json

{
  "template_id": "<MSG91_TEMPLATE_OTP_LOGIN>",
  "realTimeResponse": "1",
  "sender": "INTIQR",
  "recipients": [
    { "mobiles": "919876543210", "otp": "123456" }
  ]
}
```

Two details that trip people up:

- **`mobiles` has the country code and no plus**: `919876543210`. We store E.164 (`+919876543210`), so the adapter strips the `+`.
- **MSG91 answers HTTP 200 even when it rejects the message.** The body carries `{"type": "success"}` or `{"type": "error"}`, so the adapter checks the body rather than the status code. Without that, failures would look like successes.

---

## 5. Testing it

1. Set the variables above, restart, and confirm the startup log does **not** mention the log adapter.
2. Create a user with your own mobile number, or temporarily change the seeded admin to have a phone.
3. Request a code:

```bash
curl -i -X POST http://localhost:8080/api/v1/auth/otp \
  -H 'Content-Type: application/json' \
  -d '{"channel":"SMS","destination":"+919876543210"}'
```

4. The SMS should arrive within seconds. Check MSG91 → SMS → Logs for the delivery report, and match it with the request id our log prints as `MSG91 accepted message, requestId=…`.

---

## 6. When it fails

| Symptom | Usual cause |
|---|---|
| `MSG91 rejected the message: ...` | Wrong template id, variable name mismatch, or the template is not approved yet |
| Accepted but never delivered | DLT header not linked to the template, or the number is on DND for promotional routes. OTP traffic must go on the transactional route |
| 401 from MSG91 | Auth key wrong, or taken from a different MSG91 account |
| `No MSG91 template id configured for template code: OTP_LOGIN` | `MSG91_TEMPLATE_OTP_LOGIN` is empty in `.env` |

Costs are per SMS and per route, so keep `SMS_PROVIDER=log` for day-to-day development and switch to `msg91` only when testing delivery.

---

## 7. Adding more message types later

MVP 4 will need scheme alerts and points-credited notifications. Each one is: get a new DLT template approved, add its id under `intiq.messaging.sms.msg91.template-ids`, and call `SmsRequest.of(phone, "<TEMPLATE_CODE>", vars, text)`. No change to the adapter.
