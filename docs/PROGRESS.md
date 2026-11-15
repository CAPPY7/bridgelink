# Progress

Stage 1 code is on the way to v0.8.0. Do not tag until CI is green.

## Done in code
- Bug list is in docs/BUGS.md. The install docs, disk image, icon, history, battery, several addresses, diagnostics, and release signing check are in the tree.
- Android unit tests passed locally.
- The Mac signing certificate could not be stored. GitHub returned 403 for secret writes.

## Next exact step
Push, wait for CI, fix whatever is red, then tag v0.8.0.

## Open problems
- LinkService.java is still longer than 400 lines.
- Mac signing secrets are not in GitHub yet, so the signature is still ad-hoc.
- Sparkle, the first-run wizard, and the in-app updater are after this tag.
- Do not create a new Android signing key. Do not print secrets.
