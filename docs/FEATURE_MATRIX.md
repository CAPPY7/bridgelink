# Feature matrix

Status words are only: Not started, Building, Built – self-tested, Final – owner tested. Only the owner can set Final. Built – self-tested means the code is in both apps, tests exist, and CI is green. Half-finished work stays Building.

| # | Feature | Status | Android unit | Mac unit | Integration | Manual |
|---|---|---|---|---|---|---|
| 1 | Files, links, and text | Built – self-tested | ProtocolTest.stageThreeRules | CoreTests.testStageThreeRules | StageThreeDeviceTest.stageThreeOnDevice | M1 |
| 2 | Share sheet and drag-drop | Built – self-tested | ProtocolTest.stageThreeRules | CoreTests.testStageThreeRules | StageThreeDeviceTest.stageThreeOnDevice | M2 |
| 3 | Notification mirroring | Built – self-tested | ProtocolTest.stageThreeRules | CoreTests.testStageThreeRules | StageThreeDeviceTest.stageThreeOnDevice | M3 |
| 4 | Media control | Built – self-tested | ProtocolTest.stageFourRules | CoreTests.testStageFourRules | StageFourDeviceTest.stageFourOnDevice | M4 |
| 5 | Battery | Built – self-tested | ProtocolTest.stageThreeRules | CoreTests.testStageThreeRules | StageThreeDeviceTest.stageThreeOnDevice | M5 |
| 6 | Find phone and Mac | Built – self-tested | ProtocolTest.stageThreeRules | CoreTests.testStageThreeRules | StageThreeDeviceTest.stageThreeOnDevice | M6 |
| 7 | OTP auto-copy | Built – self-tested | ProtocolTest.stageThreeRules | CoreTests.testStageThreeRules | StageThreeDeviceTest.stageThreeOnDevice | M7 |
| 8 | Open a link | Built – self-tested | ProtocolTest.stageThreeRules | CoreTests.testStageThreeRules | StageThreeDeviceTest.stageThreeOnDevice | M8 |
| 9 | Trackpad and keyboard | Built – self-tested | ProtocolTest.stageFiveRules | CoreTests.testStageFiveRules | StageFiveDeviceTest.stageFiveOnDevice | M9 |
| 10 | Clipboard sync | Built – self-tested | ProtocolTest.stageThreeRules | CoreTests.testStageThreeRules | StageThreeDeviceTest.stageThreeOnDevice | M10 |
| 11 | SMS | Built – self-tested | ProtocolTest.stageFiveRules | CoreTests.testStageFiveRules | StageFiveDeviceTest.stageFiveOnDevice | M11 |
| 12 | Screen mirror | Built – self-tested | ProtocolTest.stageSixRules | CoreTests.testStageSixRules | StageSixDeviceTest.stageSixOnDevice | M12 |
| 13 | Control the phone | Built – self-tested | ProtocolTest.stageSixRules | CoreTests.testStageSixRules | StageSixDeviceTest.stageSixOnDevice | M13 |
| 14 | Phone as webcam | Built – self-tested | ProtocolTest.stageSixRules | CoreTests.testStageSixRules | StageSixDeviceTest.stageSixOnDevice | M14 |
| 15 | Shared folder | Built – self-tested | ProtocolTest.stageFiveRules | CoreTests.testStageFiveRules | StageFiveDeviceTest.stageFiveOnDevice | M15 |
| 16 | Lock the Mac when you leave | Built – self-tested | ProtocolTest.stageFourRules | CoreTests.testStageFourRules | StageFourDeviceTest.stageFourOnDevice | M16 |
| 17 | Away from home | Built – self-tested | ProtocolTest.stageSixRules | CoreTests.testStageSixRules | StageSixDeviceTest.stageSixOnDevice | M17 |
| 18 | Unified inbox | Built – self-tested | ProtocolTest.stageFourRules | CoreTests.testStageFourRules | StageFourDeviceTest.stageFourOnDevice | M18 |
| 19 | Clipboard history | Built – self-tested | ProtocolTest.stageFourRules | CoreTests.testStageFourRules | StageFourDeviceTest.stageFourOnDevice | M19 |
| 20 | Scan to Mac | Built – self-tested | ProtocolTest.stageFourRules | CoreTests.testStageFourRules | StageFourDeviceTest.stageFourOnDevice | M20 |
| 21 | Automations | Built – self-tested | ProtocolTest.stageFiveRules | CoreTests.testStageFiveRules | StageFiveDeviceTest.stageFiveOnDevice | M21 |
| 22 | Quick drop | Built – self-tested | ProtocolTest.stageThreeRules | CoreTests.testStageThreeRules | StageThreeDeviceTest.stageThreeOnDevice | M22 |
| 23 | Pairing | Built – self-tested | ProtocolTest.helloNeverSendsPinOrTokenToAnUnpinnedKey | CoreTests.testRelayCannotReuseAMatchCode | PairingEmulatorTest.qrPairNoteForgetThenMatchCode | M23 |
| 24 | End-to-end encryption | Built – self-tested | ProtocolTest.proofHidesThePin | CoreTests.testProofDevicesAndPendingCap | PairingEmulatorTest.qrPairNoteForgetThenMatchCode | M24 |
| 25 | Key pin | Built – self-tested | ProtocolTest.helloNeverSendsPinOrTokenToAnUnpinnedKey | CoreTests.testIdentityFingerprintStaysAndKeychainDoesNotGrow | PairingEmulatorTest.qrPairNoteForgetThenMatchCode | M25 |

Link bugs fixed in 0.7.0, with tests:

| Bug | Test |
|---|---|
| Stalled write must not freeze close | StallWriteTest.closeReturnsWhileThePeerIsNotReading |
| Same check on the emulator main thread | StallWriteTest.mainThreadSurvivesAPeerThatStopsReading |
| Background start is queued instead of crashing | ProtocolTest.featuresAgreeOnResumeClipboardOtpAndLock |
| Errors are classified by type | ProtocolTest.featuresAgreeOnResumeClipboardOtpAndLock |
| Notification text stays hidden unless unlocked and chosen | CoreTests.testFeaturesMatchThePhoneRules |
