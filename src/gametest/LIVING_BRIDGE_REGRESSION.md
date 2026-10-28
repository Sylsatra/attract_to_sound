# Saved-world Living Bridge regression

Run the lifecycle fixture in its own server invocation:

```powershell
.\gradlew.bat --offline --no-configuration-cache --project-prop gameTestOnlyEntrypoint=com.example.soundattract.ai.LivingBridgeWallContactGameTests runGameTest
```

Use Java 25. The fixture must be isolated from the other automatic-admission fixture because both exercise shared per-level admission scheduling, budgets and cooldown state. It is selected explicitly rather than added to the default concurrent entrypoints.

The NBT contains terrain copied read-only from the reported world on 2026-10-27: original origin `(-8,-62,-60)`, size `19x16x21`. It includes the cobblestone wall, projecting upper lip and inside landing. The test uses a tracked Survival player visibly standing on the lip, and a real AI-enabled zombie cohort. The standard helper's mock player reports Creative mode and is ineligible as this scenario's threat.

Run the opposite approach separately by selecting `com.example.soundattract.ai.LivingBridgeEastWallGameTests`. Its updated terrain snapshot has origin `(-8,-62,-62)` and size `25x16x25`, including the east approach and the lava ring. Every worker spawn is checked for safe footing and clear collision volume before normal AI starts. The west rig's outermost column was moved clear of the lava ring as part of this prerequisite check.

Acceptance: normal admission; a complete upright pillar with overlapping horizontal body footprints and the existing 1.90-block source spacing; actual member AABBs outside block collision shapes during every rotation/anchor/traversal observation; the older live member-contact distances; and 50 anchored observation ticks including at least 30 in TRAVERSE. Source alignment allows native crowd movement while rejecting a tilted source tower. The test retains its original 200-tick admission deadline and 1500-tick completion deadline.

The upright check also covers the full settling window. This caught intermittent foundation drift when assembly holding stopped for those 10 ticks; production now continues the same source-pillar hold until rotation begins.

Run `LivingWallAdmissionGameTests` separately for the nine existing admission, articulation, support, obstacle and cohort regressions. The support-contact test checks resolved feet and rejects obstructing ceilings/lava.

`BridgeHazardRegressionGameTests` is also included in the default suite. It reproduces the fractional-foot support lip and checks safe→lava→safe terrain changes through the classifier, safe staging, the actual prepared native `WalkNodeEvaluator`, and `NodeRouter`. Neighbor changes must invalidate 3x3 staging without changing unrelated center-cell classification.

Its native crowd-push regression exercises both vector overloads and an actual entity collision pair. A lava-directed impulse must be rejected while escape, safe-ground and fire-immune mob impulses remain effective. This covers the hazard push hook present in Forge 1.20.1 but missing from both Fabric copies.

Select `com.example.soundattract.ai.LivingBridgeDiagonalWallGameTests` separately for an initially northeast approach to the saved terrain. Normal AI may regroup before admission, so this fixture does not require the final bridge plan itself to remain diagonal.

Select `com.example.soundattract.ai.LivingBridgeOpenSkyWallGameTests` for the northeast approach with a `25x32x25` snapshot, origin `(-8,-62,-62)`. The extra vertical volume is actual saved-world air copied read-only. It moves the framework's artificial barrier roof above taller source pillars; the original 16-high fixtures can reject a nine-body candidate at that roof and select a shorter bridge instead. The normal coordinator still chooses the plan, and the same upright/collision/contact/traversal assertions apply.
