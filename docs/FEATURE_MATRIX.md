# Feature matrix

Status words are only: Not started, Building, Built – self-tested, Final – owner tested. Only the owner can set Final. Built – self-tested means the code is in both apps, tests exist, and CI is green. Half-finished work stays Building.

| # | Feature | Status | Android unit | Mac unit | Integration | Manual |
|---|---|---|---|---|---|---|
| 1 | Files, links, and text | Building | ProtocolTest.fileNamesAndLinksRejectTricks | CoreTests.testFileNamesAndLinksRejectTricks | none | M1 |
| 2 | Share sheet and drag-drop | Building | ProtocolTest.fileNamesAndLinksRejectTricks | CoreTests.testFileNamesAndLinksRejectTricks | none | M2 |
| 3 | Notification mirroring | Building | ProtocolTest.featuresAgreeOnResumeClipboardOtpAndLock | CoreTests.testFeaturesMatchThePhoneRules | none | M3 |
| 4 | Media control | Not started | none | none | none | M4 |
| 5 | Battery | Building | ProtocolTest.featuresAgreeOnResumeClipboardOtpAndLock | CoreTests.testFeaturesMatchThePhoneRules | none | M5 |
| 6 | Find phone and Mac | Building | ProtocolTest.featuresAgreeOnResumeClipboardOtpAndLock | CoreTests.testFeaturesMatchThePhoneRules | none | M6 |
| 7 | OTP auto-copy | Building | ProtocolTest.otpTableRejectsPricesDatesAndLongNumbers | CoreTests.testFeaturesMatchThePhoneRules | none | M7 |
| 8 | Open a link | Building | ProtocolTest.fileNamesAndLinksRejectTricks | CoreTests.testFileNamesAndLinksRejectTricks | none | M8 |
| 9 | Trackpad and keyboard | Not started | none | none | none | M9 |
| 10 | Clipboard sync | Building | ProtocolTest.featuresAgreeOnResumeClipboardOtpAndLock | CoreTests.testSensitiveClipboard | none | M10 |
| 11 | SMS | Not started | none | none | none | M11 |
| 12 | Screen mirror | Not started | none | none | none | M12 |
| 13 | Control the phone | Not started | none | none | none | M13 |
| 14 | Phone as webcam | Not started | none | none | none | M14 |
| 15 | Shared folder | Not started | none | none | none | M15 |
| 16 | Lock the Mac when you leave | Not started | none | none | none | M16 |
| 17 | Away from home | Not started | none | none | none | M17 |
| 18 | Unified inbox | Not started | none | none | none | M18 |
| 19 | Clipboard history | Not started | none | none | none | M19 |
| 20 | Scan to Mac | Not started | none | none | none | M20 |
| 21 | Automations | Not started | none | none | none | M21 |
| 22 | Quick drop | Building | ProtocolTest.featuresAgreeOnResumeClipboardOtpAndLock | CoreTests.testFeaturesMatchThePhoneRules | none | M22 |
| 23 | Pairing | Building | ProtocolTest.helloNeverSendsPinOrTokenToAnUnpinnedKey | CoreTests.testRelayCannotReuseAMatchCode | PairingEmulatorTest.qrPairNoteForgetThenMatchCode | M23 |
| 24 | End-to-end encryption | Building | ProtocolTest.proofHidesThePin | CoreTests.testProofDevicesAndPendingCap | PairingEmulatorTest.qrPairNoteForgetThenMatchCode | M24 |
| 25 | Key pin | Building | ProtocolTest.helloNeverSendsPinOrTokenToAnUnpinnedKey | CoreTests.testIdentityFingerprintStaysAndKeychainDoesNotGrow | PairingEmulatorTest.qrPairNoteForgetThenMatchCode | M25 |

Link bugs fixed in 0.7.0, with tests:

| Bug | Test |
|---|---|
| Stalled write must not freeze close | StallWriteTest.closeReturnsWhileThePeerIsNotReading |
| Same check on the emulator main thread | StallWriteTest.mainThreadSurvivesAPeerThatStopsReading |
| Background start is queued instead of crashing | ProtocolTest.featuresAgreeOnResumeClipboardOtpAndLock |
| Errors are classified by type | ProtocolTest.featuresAgreeOnResumeClipboardOtpAndLock |
| Notification text stays hidden unless unlocked and chosen | CoreTests.testFeaturesMatchThePhoneRules |
