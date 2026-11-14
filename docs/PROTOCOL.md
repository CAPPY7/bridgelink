# Protocol

Version 1. Both installable apps use this. The browser desk does not.

## Framing

Each message is a frame:

1. 1 byte type. `1` means JSON. `2` means binary and is reserved.
2. 4 byte big-endian payload length.
3. Payload. Maximum 1,048,576 bytes. A peer that exceeds this is disconnected.

## JSON envelope

```json
{"v":1,"id":"<uuid>","type":"text","from":"android","ts":1000,"payload":{"body":"hello"}}
```

| Field | Meaning |
|---|---|
| v | Must be 1 |
| id | Unique id. Receivers drop duplicates |
| type | Message name |
| from | `android`, `mac`, or `relay` |
| ts | Unix milliseconds |
| payload | String values only |

Golden fixture: `docs/fixtures/text.json`.

## Types in 0.3.0

| type | payload | Who sends it |
|---|---|---|
| hello | `name`, `pin` and/or `token` | Phone, first message |
| paired | `token` | Mac, after a good PIN or a known token |
| error | `body` | Either side |
| text | `body` | Either side |
| clipboard | `body` | Either side |
| ping | empty | Phone, about every 20 seconds |
| pong | empty | Mac |
| ring | empty | Mac |

## Pairing

The Mac shows a QR code and a 6-digit PIN. The link inside the QR is:

```text
bridgelink://pair?v=1&host=192.168.1.20&port=8787&fp=<sha256>&pin=123456
```

The PIN comes from a secure random generator. It expires in 2 minutes, works once, and locks for 60 seconds after 5 wrong guesses. Comparison is constant-time.

The phone can paste that link, which stores the host, port, and certificate fingerprint before connecting. It can also find the Mac by browsing `_bridgelink._tcp` and type the PIN. There is no in-app camera scanner in 0.3.0. A normal camera app can read the QR, then the link is pasted into the phone.

A correct PIN returns a long `token`. The phone stores that token in encrypted preferences (Android Keystore) and stores the Mac certificate fingerprint. Later connections send the token and must present the same certificate. A changed certificate is rejected with the message "This Mac key changed". Forget the Mac, then pair again, to trust a new key.

The Mac does not drop an already paired phone when a new connection fails the PIN.

The Mac keeps the TLS identity in the login keychain so the fingerprint does not change on restart.

## Transport

The Mac listens on TCP port 8787 with TLS. The phone is the client. It pins the Mac certificate's SHA-256 fingerprint after the first successful pair.

This is not mutual TLS. The phone does not present its own certificate in 0.3.0.
