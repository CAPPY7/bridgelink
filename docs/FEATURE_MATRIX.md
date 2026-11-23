# Feature matrix

Status words are only: Not started, Building, Built – NOT TESTED on a device, Final – owner tested. Only the owner can set Final. Built – NOT TESTED on a device means the code is in both apps and tests exist. Half-finished work stays Building. Removed is used when a feature is taken out.

| # | Feature | Status | Android unit | Mac unit | Integration | Manual |
|---|---|---|---|---|---|---|
| 1 | Files, links, and text | Built – NOT TESTED on a device | ProtocolTest.stageThreeRules | CoreTests.testStageThreeRules | StageThreeDeviceTest.stageThreeOnDevice | M1 |
| 2 | Share sheet and drag-drop | Built – NOT TESTED on a device | ProtocolTest.stageThreeRules | CoreTests.testStageThreeRules | StageThreeDeviceTest.stageThreeOnDevice | M2 |
| 3 | Notification mirroring | Built – NOT TESTED on a device | ProtocolTest.stageThreeRules | CoreTests.testStageThreeRules | StageThreeDeviceTest.stageThreeOnDevice | M3 |
| 4 | Media control | Built – NOT TESTED on a device | ProtocolTest.stageFourRules | CoreTests.testStageFourRules | StageFourDeviceTest.stageFourOnDevice | M4 |
| 5 | Battery | Built – NOT TESTED on a device | ProtocolTest.stageThreeRules | CoreTests.testStageThreeRules | StageThreeDeviceTest.stageThreeOnDevice | M5 |
| 6 | Find phone and Mac | Built – NOT TESTED on a device | ProtocolTest.stageThreeRules | CoreTests.testStageThreeRules | StageThreeDeviceTest.stageThreeOnDevice | M6 |
| 7 | OTP auto-copy | Built – NOT TESTED on a device | ProtocolTest.stageThreeRules | CoreTests.testStageThreeRules | StageThreeDeviceTest.stageThreeOnDevice | M7 |
| 8 | Open a link | Built – NOT TESTED on a device | ProtocolTest.stageThreeRules | CoreTests.testStageThreeRules | StageThreeDeviceTest.stageThreeOnDevice | M8 |
| 9 | Trackpad and keyboard | Built – NOT TESTED on a device | ProtocolTest.stageFiveRules | CoreTests.testStageFiveRules | StageFiveDeviceTest.stageFiveOnDevice | M9 |
| 10 | Clipboard sync | Built – NOT TESTED on a device | ProtocolTest.stageThreeRules | CoreTests.testStageThreeRules | StageThreeDeviceTest.stageThreeOnDevice | M10 |
| 11 | SMS | Built – NOT TESTED on a device | ProtocolTest.stageFiveRules | CoreTests.testStageFiveRules | StageFiveDeviceTest.stageFiveOnDevice | M11 |
| 12 | Screen mirror | Built – NOT TESTED on a device | ProtocolTest.stageSixRules | CoreTests.testStageSixRules | StageSixDeviceTest.stageSixOnDevice | M12 |
| 13 | Control the phone | Built – NOT TESTED on a device | ProtocolTest.stageSixRules | CoreTests.testStageSixRules | StageSixDeviceTest.stageSixOnDevice | M13 |
| 14 | Phone as webcam | Built – NOT TESTED on a device | ProtocolTest.stageSixRules | CoreTests.testStageSixRules | StageSixDeviceTest.stageSixOnDevice | M14 |
| 15 | Shared folder | Built – NOT TESTED on a device | ProtocolTest.stageFiveRules | CoreTests.testStageFiveRules | StageFiveDeviceTest.stageFiveOnDevice | M15 |
| 16 | Lock the Mac when you leave | Built – NOT TESTED on a device | ProtocolTest.stageFourRules | CoreTests.testStageFourRules | StageFourDeviceTest.stageFourOnDevice | M16 |
| 17 | Remote link | Removed | — | — | — | — |
| 18 | Combined list | Removed | — | — | — | — |
| 19 | Clipboard history | Built – NOT TESTED on a device | ProtocolTest.stageFourRules | CoreTests.testStageFourRules | StageFourDeviceTest.stageFourOnDevice | M19 |
| 20 | Scan to Mac | Built – NOT TESTED on a device | ProtocolTest.stageFourRules | CoreTests.testStageFourRules | StageFourDeviceTest.stageFourOnDevice | M20 |
| 21 | When-this-then-that rules | Removed | — | — | — | — |
| 22 | Quick drop | Built – NOT TESTED on a device | ProtocolTest.stageThreeRules | CoreTests.testStageThreeRules | StageThreeDeviceTest.stageThreeOnDevice | M22 |
| 23 | Pairing | Built – NOT TESTED on a device | ProtocolTest.helloNeverSendsPinOrTokenToAnUnpinnedKey | CoreTests.testMiddlemanCannotReuseAMatchCode | PairingEmulatorTest.qrPairNoteForgetThenMatchCode | M23 |
| 24 | Encrypted local link (TLS, pinned key) | Built – NOT TESTED on a device | ProtocolTest.proofHidesThePin | CoreTests.testProofDevicesAndPendingCap | PairingEmulatorTest.qrPairNoteForgetThenMatchCode | M24 |
| 25 | Key pin | Built – NOT TESTED on a device | ProtocolTest.helloNeverSendsPinOrTokenToAnUnpinnedKey | CoreTests.testIdentityFingerprintStaysAndKeychainDoesNotGrow | PairingEmulatorTest.qrPairNoteForgetThenMatchCode | M25 |

Link bugs fixed in 0.7.0, with tests:

| Bug | Test |
|---|---|
| Stalled write must not freeze close | StallWriteTest.closeReturnsWhileThePeerIsNotReading |
| Same check on the emulator main thread | StallWriteTest.mainThreadSurvivesAPeerThatStopsReading |
| Background start is queued instead of crashing | ProtocolTest.featuresAgreeOnResumeClipboardOtpAndLock |
| Errors are classified by type | ProtocolTest.featuresAgreeOnResumeClipboardOtpAndLock |
| Notification text stays hidden unless unlocked and chosen | CoreTests.testFeaturesMatchThePhoneRules |
