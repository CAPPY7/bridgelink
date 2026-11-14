# Progress

v0.6.0 is tagged on a736cd8 (CI was green). Resume, menu-bar drop, find-Mac, clipboard echo, battery, and notification/OTP hooks are committed after that tag.

## Done in code
- Pairing with commit-then-reveal, emulator test, signed v0.4.0 and v0.6.0.
- Files both ways, resume via file_have, links, share sheet, Quick Settings tile.
- Menu-bar icon drop target and Command-Shift-D.
- Find phone with stop, vibrate, Find my Mac beep.
- Clipboard echo prevention. Notification listener and OTP copy exist but stay off until both sides turn mirroring on.
- Shared tested rules: OTP table, SMS parts, clipboard history, leave-lock, relay seal, stream header.

## Next exact step
Wire the remaining features into both apps, one commit each, in this order: clipboard history screen, media buttons, SMS module, inbox list, scan-to-PDF, automations, shared folder, proximity lock, screen mirror framing, remote control, camera preview, relay client. Then run the full test list in section 3, fix CI, and tag the highest honest version.

## Open problems
- v0.6.0 release assets are the commit before resume. The resume commit still needs a green CI run.
- Notification text is sent only when the user turns mirroring on. It is not logged. Restricted-settings help screen is not written yet.
- Mac global shortcut needs Accessibility on some macOS versions.
- Do not create a new signing key. Do not print secrets.
